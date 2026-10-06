//! One turn: sample the model, run whatever tools it asks for, and sample again until it
//! stops asking or stops for the user.

use crate::client::ModelClient;
use crate::client::SamplingRequest;
use crate::client::tool_declaration;
use crate::context_manager::compact;
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

/// 模型配置没给 `effective_context_window_percent` 时用的百分比（上游默认 95）。
const DEFAULT_AUTO_COMPACT_WINDOW_PERCENT: i64 = 95;

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
    /// 自动压缩的绝对触发线（token）；`0` = 未设置，用默认口径。
    pub auto_compact_token_limit: u32,
    /// 压缩触发线占窗口的百分比；`0` = 未设置，用默认的 95。
    pub effective_context_window_percent: u32,
    pub cancellation: &'a CancellationToken,
    /// 本轮若发生上下文压缩，结果写到这里，由调用侧落盘（`RolloutItem::Compacted`）。
    ///
    /// 走"出参"而不是让 turn 自己去写 rollout：turn 只拿得到 `history`，看不见 recorder
    /// （那是 `session::service` 的职责）。压缩是**每轮都可能发生**的事，所以放在这个每轮都持有的
    /// 结构里最省事 —— 调用侧给它一个 `&mut Option<_>`，turn 压了就填上，没压就原样留着。
    pub compaction_out: &'a mut Option<compact::CompactRecord>,
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
        // 上下文压缩：与 codex 一样在**每个采样轮开始前**判定。
        //
        // 上游的判定点有两处 —— PreTurn（`session/turn.rs:183` 的 `run_pre_sampling_compact`）
        // 与 MidTurn（`:611` 的 `should_roll_over`）—— 这里落在同一点：本函数是全仓唯一把
        // `history` 物化成请求的地方，而它在 agentic loop 里**每轮都会重跑**，所以"工具轮越滚越大"
        // 的那种增长也能被压到。
        //
        // 压缩本身不该把 turn 打断：上游是返回 `Err` 由调用侧处理，这里更保守 —— 失败只记 warning，
        // 让本轮照常带着原历史跑下去。
        if let Err(error) = maybe_compact(
            turn.client,
            turn.history,
            turn.thread,
            turn.instruction.clone(),
            &mut *turn.compaction_out,
            turn.auto_compact_token_limit,
            turn.effective_context_window_percent,
            emit,
        )
        .await
        {
            tracing::warn!(%error, "context compaction failed; continuing with the uncompacted history");
        }
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

/// 判定并执行一次上下文压缩。
///
/// 判定与重建的全部逻辑都在 [`compact`] 里（逐项移植自 codex，见该模块头部对照表）；这里只做三件事：
/// 算活跃 token、发一次**同模型**的摘要请求、把历史换成「最近若干用户消息 + 摘要」。
///
/// 与 codex 的两处载体差异，都写在这里：
/// 1. **活跃 token 的算法**：上游是「服务端 usage 的 total_tokens + 本地估算的『最后一次模型输出
///    之后追加的项』」（`context_manager/history.rs:850-867`）。我们索引不到那个切点（history 是裸
///    `Vec<ResponseItem>`，没有"哪些项在最后一次模型输出之后"的记账），所以取
///    `max(服务端报的输入 token, 整份历史的估算值)` —— 宁可早压，也不要撑爆窗口。
/// 2. **压缩请求的形状**：与上游一致 —— 完整历史 + 末尾追加一条 `SUMMARIZATION_PROMPT`
///    （`compact.rs:257-261`）、**同一模型**、**不带 tools**（上游手搓 `Prompt`，`tools` 为空）、
///    沿用本轮 system prompt（上游带 `base_instructions`，`compact.rs:279`）。
async fn maybe_compact<T: HttpTransport>(
    client: &ModelClient<T>,
    history: &mut Vec<ResponseItem>,
    thread: &mut ChatThread,
    instruction: Option<String>,
    compaction_out: &mut Option<compact::CompactRecord>,
    auto_compact_token_limit: u32,
    effective_context_window_percent: u32,
    emit: &mut impl FnMut(ChatEvent),
) -> Result<(), SessionError> {
    let context_window = thread.context_window();
    if context_window <= 0 {
        return Ok(());
    }

    let reported = thread
        .token_usage_info()
        .map(|info| info.last_token_usage.input_tokens)
        .unwrap_or(0);
    let estimated: i64 = history
        .iter()
        .map(|item| compact::approx_item_token_count(item) as i64)
        .sum();
    let active_context_tokens = reported.max(estimated);

    // 判定用上游那套口径：绝对线（配置里的 `auto_compact_token_limit`）与窗口百分比线
    // （`effective_context_window_percent`）取「或」—— 哪个先到算哪个。
    // 两项都来自模型配置，`0` = 未设置；百分比未设置时用上游默认的 95。
    let configured_limit =
        (auto_compact_token_limit > 0).then_some(i64::from(auto_compact_token_limit));
    let percent = if effective_context_window_percent > 0 {
        i64::from(effective_context_window_percent)
    } else {
        DEFAULT_AUTO_COMPACT_WINDOW_PERCENT
    };
    if !compact::token_limit_reached(active_context_tokens, context_window, configured_limit, percent, 0)
    {
        return Ok(());
    }

    // 压缩要额外打一次模型请求，用户会实打实看到一段停顿，所以先说一声。
    // 它不是回合的一步（不结束回合）：压完这一轮照常往下跑。
    emit(ChatEvent::Compacting {
        tokens: active_context_tokens,
        context_window,
    });

    let mut prompt_input = history.clone();
    prompt_input.push(ResponseItem::Message {
        id: None,
        role: "user".to_string(),
        content: vec![ContentItem::InputText {
            text: compact::SUMMARIZATION_PROMPT.to_string(),
        }],
    });

    let mut stream = client
        .stream(SamplingRequest {
            instruction,
            input: prompt_input,
            tools: Vec::new(),
            stream: true,
        })
        .await
        .map_err(SessionError::from)?;

    let mut summary = String::new();
    while let Some(event) = stream.next().await {
        if let ResponseEvent::OutputTextDelta(text) = event.map_err(SessionError::from)? {
            summary.push_str(&text);
        }
    }

    if summary.trim().is_empty() {
        // 上游在拿不到摘要时会把兜底文案塞进去（`compact.rs:730`）；这里选择**不动原历史** ——
        // 用一句"没有摘要"替换掉整段真实对话，风险比收益大。
        return Ok(());
    }

    let summary_text = compact::summary_with_prefix(&summary);
    let user_messages = compact::collect_user_messages(history);
    let compacted = compact::build_compacted_history(
        &user_messages,
        &summary_text,
        compact::COMPACT_USER_MESSAGE_MAX_TOKENS,
    );
    let items_before = history.len();
    let items_after = compacted.len();
    // 出参给调用侧落盘。文件里因此留下"压成了什么"，重启恢复时以最后一条为准；
    // 而压缩前的原始条目在 rollout 里仍然躺着（追加式写入，一条没删），随时能对账。
    *compaction_out = Some(compact::CompactRecord {
        replacement_history: compacted.clone(),
        summary,
        active_context_tokens,
        context_window,
        items_before,
        items_after,
    });
    *history = compacted;
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
