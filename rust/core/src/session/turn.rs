//! One turn: sample the model, run whatever tools it asks for, and sample again until it
//! stops asking or stops for the user.

use crate::client::ModelClient;
use crate::client::SamplingRequest;
use crate::client::tool_declaration;
use crate::context_manager::normalize;
use crate::session::SessionError;
use crate::thread::ChatThread;
use crate::tools::ToolCallRuntime;
use crate::tools::ToolRegistry;
use futures::StreamExt;
use jasmine_api::ApiError;
use jasmine_api::ResponseEvent;
use jasmine_api::ResponseStream;
use jasmine_client::HttpTransport;
use jasmine_protocol::ChatEvent;
use jasmine_protocol::models::ContentItem;
use jasmine_protocol::models::FunctionCallOutputPayload;
use jasmine_protocol::models::ResponseItem;
use jasmine_protocol::openai_models::InputModality;
use jasmine_protocol::protocol::ContextUsageBreakdownItem;
use jasmine_protocol::protocol::ContextUsageSource;
use jasmine_tools::ResponsesApiTool;
use jasmine_utils_string::approx_token_count;
use tokio_util::sync::CancellationToken;

/// What one turn runs with: where to sample, what to offer, and the conversation so far.
///
/// The turn appends the assistant's own output and every tool result to `history` as it goes,
/// so the next round sees a coherent transcript.
pub struct Turn<'a, T: HttpTransport> {
    pub client: &'a ModelClient<T>,
    pub thread: &'a mut ChatThread,
    pub registry: &'a ToolRegistry,
    pub runtime: &'a ToolCallRuntime,
    pub history: &'a mut Vec<ResponseItem>,
    pub instruction: Option<String>,
    pub input_modalities: &'a [InputModality],
    pub cancellation: &'a CancellationToken,
}

/// What one sampling round produced.
#[derive(Debug, Default)]
struct SamplingRound {
    text: String,
    reasoning: Vec<ResponseItem>,
    /// Thinking that only ever arrived as deltas. A settled item takes it over; a round cut off in the
    /// middle keeps it, so the platform can show how far that thinking had got.
    streamed_reasoning: String,
    tool_calls: Vec<ResponseItem>,
}

/// Runs a turn to completion, reporting through `emit`.
pub async fn run_turn<T: HttpTransport>(
    turn: Turn<'_, T>,
    emit: &mut impl FnMut(ChatEvent),
) -> Result<(), SessionError> {
    loop {
        turn.thread.begin_turn();
        let mut request_input = turn.history.clone();
        normalize(&mut request_input, turn.input_modalities);
        let tools = turn.registry.specs();
        // What the request is about to send, by part. Read from the request itself rather than
        // from the history, because this is exactly what goes on the wire.
        let breakdown = context_breakdown(turn.instruction.as_deref(), &tools, &request_input);
        turn.thread.note_request_breakdown(breakdown);
        let stream = turn
            .client
            .stream(SamplingRequest {
                instruction: turn.instruction.clone(),
                input: request_input,
                tools,
                stream: true,
            })
            .await
            .map_err(SessionError::from)?;

        // Stopping is noticed inside the stream read, which ends it there.
        let round = match drain_stream(stream, &mut *turn.thread, emit, turn.cancellation).await {
            Ok(round) => round,
            Err(error) => {
                emit(ChatEvent::Failed(error.to_string()));
                return Err(error.into());
            }
        };

        // A stopped round contributes nothing to the conversation. Only what the provider marked
        // done belongs to it, and a round cut off in the middle never got that far. What it did
        // write is handed over instead, for the record the platform shows it from.
        if turn.cancellation.is_cancelled() {
            turn.thread
                .note_interrupted_reasoning(&round.streamed_reasoning);
            turn.thread.note_interrupted_reply(&round.text);
            return Err(SessionError::TurnAborted);
        }

        // A settled item belongs to the round that produced it: a thinking model's reasoning is
        // part of what the next request has to see.
        turn.history.extend(round.reasoning);

        // What this round cost, once the model has said. The window and the request it was
        // measured against come along, so the platform can show both without keeping its own tally.
        if let Some(event) = turn.thread.usage_event() {
            emit(event);
        }

        if !round.text.is_empty() {
            turn.history.push(ResponseItem::Message {
                id: None,
                role: "assistant".to_string(),
                content: vec![ContentItem::OutputText { text: round.text }],
            });
        }

        // A prompt stops the turn: the interactive call has no result yet, and asking the
        // model again would send it an assistant message whose calls are not all answered.
        if turn.thread.is_paused_for_prompt() {
            break;
        }

        if round.tool_calls.is_empty() {
            break;
        }

        // Every call of one answer goes in before any of its results.
        for call in &round.tool_calls {
            turn.history.push(call.clone());
        }

        // The batch runs to its end before a stop is honoured: a call whose result never reached
        // the history is a request the provider refuses, so every call gets its output first.
        for outcome in turn.runtime.run(&round.tool_calls, emit).await {
            turn.history.push(ResponseItem::FunctionCallOutput {
                id: None,
                call_id: Some(outcome.call_id),
                name: None,
                namespace: None,
                output: FunctionCallOutputPayload::from_text(outcome.output),
            });
        }

        if turn.cancellation.is_cancelled() {
            return Err(SessionError::TurnAborted);
        }
    }

    emit(ChatEvent::Completed);
    Ok(())
}

/// Reads one streaming answer, forwarding events and collecting what the turn needs.
async fn drain_stream(
    mut stream: ResponseStream,
    thread: &mut ChatThread,
    emit: &mut impl FnMut(ChatEvent),
    cancellation: &CancellationToken,
) -> Result<SamplingRound, ApiError> {
    let mut round = SamplingRound::default();
    loop {
        let event = tokio::select! {
            event = stream.next() => event,
            // Stopping ends the read here; the caller reports the turn as aborted.
            _ = cancellation.cancelled() => break,
        };
        let Some(event) = event else { break };
        let event = event?;
        match &event {
            ResponseEvent::Created { response_id } => {
                thread.note_invocation(response_id.clone());
            }
            ResponseEvent::OutputTextDelta(text) => round.text.push_str(text),
            ResponseEvent::ReasoningContentDelta { delta, .. } => {
                round.streamed_reasoning.push_str(delta);
            }
            ResponseEvent::OutputItemDone(item @ ResponseItem::Reasoning { .. }) => {
                round.reasoning.push(item.clone());
                // 整块到了，增量这份就没有留下的必要（免得重启后看到两遍）。
                round.streamed_reasoning.clear();
            }
            ResponseEvent::OutputItemDone(item @ ResponseItem::FunctionCall { .. }) => {
                round.tool_calls.push(item.clone());
            }
            _ => {}
        }
        for chat_event in thread.on_response_event(event) {
            // One sampling round ends here; the turn announces the end that matters.
            if matches!(chat_event, ChatEvent::Completed) {
                continue;
            }
            emit(chat_event);
        }
    }
    Ok(round)
}

/// Counts one request by part: the instruction, the tool schemas, the conversation.
///
/// Bytes over the core's usual bytes-per-token constant — the same estimate the truncation code
/// uses, because no tokenizer is available. The shares it gives are what the platform shows; the
/// total is replaced by the model's own count once it answers, since a real tokenizer only lives on
/// the provider's side (and how a tool declaration is serialized is the provider's business too).
///
/// MCP tools are told apart by the `mcp__<server>__` namespace upstream puts them in. Skills have
/// no source in the core yet, so that bucket is reported empty.
fn context_breakdown(
    instruction: Option<&str>,
    tools: &[ResponsesApiTool],
    input: &[ResponseItem],
) -> Vec<ContextUsageBreakdownItem> {
    let tokens = |text: &str| i64::try_from(approx_token_count(text)).unwrap_or(i64::MAX);

    let system_prompt = instruction.map_or(0, tokens);
    let mut system_tools = 0;
    let mut mcp_tools = 0;
    for tool in tools {
        // The declaration as it goes on the wire, not the struct it was built from.
        let encoded = tool_declaration(tool)
            .and_then(|declaration| {
                serde_json::to_string(&declaration)
                    .map_err(|error| ApiError::Stream(format!("failed to encode tool: {error}")))
            })
            .unwrap_or_default();
        if is_mcp_tool(&tool.name) {
            mcp_tools += tokens(&encoded);
        } else {
            system_tools += tokens(&encoded);
        }
    }
    let messages: i64 = input
        .iter()
        .map(|item| tokens(&serde_json::to_string(item).unwrap_or_default()))
        .sum();

    vec![
        ContextUsageBreakdownItem {
            source: ContextUsageSource::SystemPrompt,
            tokens: system_prompt,
        },
        ContextUsageBreakdownItem {
            source: ContextUsageSource::SystemToolSchemas,
            tokens: system_tools,
        },
        ContextUsageBreakdownItem {
            source: ContextUsageSource::Skills,
            tokens: 0,
        },
        ContextUsageBreakdownItem {
            source: ContextUsageSource::McpToolSchemas,
            tokens: mcp_tools,
        },
        ContextUsageBreakdownItem {
            source: ContextUsageSource::Messages,
            tokens: messages,
        },
    ]
}

/// Whether a tool came from an MCP server: upstream namespaces those `mcp__<server>__<tool>`.
fn is_mcp_tool(name: &str) -> bool {
    name.starts_with("mcp__")
}
