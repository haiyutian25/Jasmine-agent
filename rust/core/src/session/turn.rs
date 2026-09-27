//! One turn: sample the model, run whatever tools it asks for, and sample again until it
//! stops asking or stops for the user.

use crate::client::ModelClient;
use crate::client::SamplingRequest;
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
        let stream = turn
            .client
            .stream(SamplingRequest {
                instruction: turn.instruction.clone(),
                input: request_input,
                tools: turn.registry.specs(),
                stream: true,
            })
            .await
            .map_err(SessionError::from)?;

        // Stopping is noticed inside the stream read, which keeps the part that had already
        // arrived: what is on screen is what the file will hold.
        let round = match drain_stream(stream, &mut *turn.thread, emit, turn.cancellation).await {
            Ok(round) => round,
            Err(error) => {
                emit(ChatEvent::Failed(error.to_string()));
                return Err(error.into());
            }
        };

        // A settled item belongs to the round that produced it: a thinking model's reasoning is
        // part of what the next request has to see.
        turn.history.extend(round.reasoning);

        if !round.text.is_empty() {
            turn.history.push(ResponseItem::Message {
                id: None,
                role: "assistant".to_string(),
                content: vec![ContentItem::OutputText { text: round.text }],
            });
        }

        // The part that arrived is in the history, so the file keeps what the screen showed.
        if turn.cancellation.is_cancelled() {
            return Err(SessionError::TurnAborted);
        }

        // A prompt stops the turn: the interactive call has no result yet, and asking the
        // model again would send it an assistant message whose calls are not all answered.
        if turn.thread.is_paused_for_prompt() {
            break;
        }

        if round.tool_calls.is_empty() {
            break;
        }

        // Every call of one answer goes in before any of its results: a provider in thinking mode
        // reads a call that follows a result as one whose reasoning was dropped, and refuses the
        // whole request.
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
            // Stopping keeps whatever had already arrived, so the caller can put it in the history.
            _ = cancellation.cancelled() => break,
        };
        let Some(event) = event else { break };
        let event = event?;
        match &event {
            ResponseEvent::Created { response_id } => {
                thread.note_invocation(response_id.clone());
            }
            ResponseEvent::OutputTextDelta(text) => round.text.push_str(text),
            ResponseEvent::OutputItemDone(item @ ResponseItem::Reasoning { .. }) => {
                round.reasoning.push(item.clone());
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
