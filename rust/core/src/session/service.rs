//! The conversation facade the platform drives.
//!
//! It owns what a conversation needs between calls: the client for the provider's wire protocol,
//! the context so far, and the live thread state. Attaching is explicit — switching provider or
//! model means starting a new conversation.
//!
//! The core underneath is async while the boundary is synchronous, so each call drives its
//! operation on a runtime of its own. The service is stateful and holds the conversation while a
//! call runs: one instance per conversation owner, one call at a time.

use crate::agent_settings::AgentSettings;
use crate::client::ModelClient;
use crate::host::Clock;
use crate::session::SessionError;
use crate::session::respond_to_prompts as continue_turn;
use crate::session::send_text;
use crate::session::turn::Turn;
use crate::thread::ChatThread;
use crate::tools::ToolCallRuntime;
use crate::tools::ToolRegistry;
use jasmine_client::ReqwestTransport;
use jasmine_http_client::HttpClientBuilder;
use jasmine_model_provider::ApiClient;
use jasmine_model_provider::ResolvedProvider;
use jasmine_model_provider::create_api_client;
use jasmine_model_provider_info::ModelConfig;
use jasmine_protocol::ChatEvent;
use jasmine_protocol::Role;
use jasmine_protocol::SessionId;
use jasmine_protocol::models::ContentItem;
use jasmine_protocol::models::ResponseItem;
use jasmine_protocol::protocol::AppUsageStats;
use jasmine_protocol::protocol::ContextUsageBreakdownItem;
use jasmine_protocol::protocol::TokenUsageInfo;
use jasmine_rollout::RolloutItem;
use jasmine_rollout::RolloutRecorder;
use jasmine_rollout::SessionMeta;
use jasmine_rollout::delete_session;
use jasmine_rollout::find_session_path;
use jasmine_rollout::list_sessions;
use jasmine_rollout::read_response_items;
use jasmine_rollout::read_timed_items;
use jasmine_rollout::timestamp_now;
use jasmine_tools::Clock as ToolClock;
use jasmine_tools::ConversationSummary as ToolConversationSummary;
use jasmine_tools::ConversationTitles;
use jasmine_tools::CurrentTimeTool;
use jasmine_tools::ListPastConversationsTool;
use std::future::Future;
use std::path::PathBuf;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::MutexGuard;
use tokio_util::sync::CancellationToken;

/// Where the core pushes events on their way back to the platform.
pub trait ChatSink {
    fn emit(&mut self, event: ChatEvent);
}

/// Why a session operation could not run.
#[derive(Debug, thiserror::Error)]
pub enum AgentError {
    /// A call arrived with no conversation attached.
    #[error("尚未附着会话")]
    NoSession,
    /// The runtime the call is driven on could not be created.
    #[error("failed to start the async runtime: {0}")]
    Runtime(String),
    /// The client for the provider could not be built.
    #[error("failed to build the HTTP client: {0}")]
    Transport(String),
    /// A call panicked while holding the conversation.
    #[error("the conversation was left inconsistent by a failed call")]
    Poisoned,
    /// The conversation's own file could not be read or written.
    #[error("会话文件不可用：{0}")]
    Transcript(String),
    #[error(transparent)]
    Session(#[from] SessionError),
}

impl AgentError {
    /// The one line the platform shows.
    pub fn detail(&self) -> String {
        self.to_string()
    }
}

/// One conversation as the platform's list shows it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ConversationSummary {
    pub session_id: String,
    pub title: String,
    pub provider_id: String,
    pub model_id: String,
    /// Last write, in milliseconds since the epoch — the same unit the list orders by.
    pub updated_at: i64,
}

/// One line of a conversation as the platform's transcript shows it.
///
/// A tool result travels with the id of the call it answers: without it the line cannot be
/// placed next to the call that produced it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HistoryEntry {
    pub role: Role,
    pub text: String,
    pub tool_call_id: Option<String>,
    /// Set when the line stands for a turn that stopped: how long it had been running.
    pub stopped_after_ms: Option<u64>,
    /// When the line was recorded, in milliseconds since the epoch (0 when the file has none).
    pub recorded_at: i64,
    /// The model the turn this line belongs to ran on — the model the user's message went to.
    pub model_label: Option<String>,
    /// Set when the line stands for a tool call: the call and its result are one line, so the card
    /// the platform draws carries both.
    pub tool_name: Option<String>,
    pub tool_detail: Option<String>,
    pub tool_result: Option<String>,
    /// 这次工具调用走到哪一步了，取值与 ZCode 的 `chat.toolCall.status.*` 一一对应：
    /// `pending` / `running` / `completed` / `failed` / `denied` / `stopped`。
    ///
    /// 非工具行给空串。**状态是行自己的数据**：界面读它，不从"有没有结果"反推。
    pub tool_status: String,
    /// 这一轮回答之前模型「想过」的内容（深度思考），空串表示没有。
    ///
    /// 思考在会话文件里是独立条目，不属于任何一条消息；这里把它挂在**同一轮里那条回复**上，重建
    /// 出来的对话才和现场看到的一样（思考块排在正文上方）。
    pub thinking: String,
}

/// Everything one attached conversation owns.
struct Attached {
    client: ModelClient<ReqwestTransport>,
    model: ModelConfig,
    instruction: String,
    history: Vec<ResponseItem>,
    thread: ChatThread,
    rollout: RolloutRecorder,
    /// Cancelled when the platform stops the turn that is running.
    cancellation: CancellationToken,
    /// The turn the file currently holds open, and whether it stopped before finishing.
    turn_id: String,
    interrupted: bool,
    /// The window this conversation runs against, in tokens. Always set: it is decided when the
    /// conversation is first attached, and a model switch inside the conversation does not move it.
    context_window_tokens: u64,
    /// 这个会话当前的推理档位（空串 = 未设置）。第一次附着时从模型配置抄一次，之后会话自己说了算。
    reasoning_effort: String,
}

/// One conversation, as the platform drives it.
pub struct AgentChatService {
    sessions_dir: PathBuf,
    clock: Arc<dyn Clock>,
    runtime: ToolCallRuntime,
    attached: Mutex<Option<Attached>>,
    /// The running turn's own token, kept outside [Self::attached] so [Self::interrupt] can reach
    /// it while a turn holds that lock for its whole run.
    cancellation: Mutex<CancellationToken>,
}

impl AgentChatService {
    /// Keeps the platform's clock and its conversations directory, and offers the built-in tools.
    pub fn new(sessions_dir: PathBuf, clock: Arc<dyn Clock>) -> Self {
        let mut registry = ToolRegistry::new();
        registry.add(CurrentTimeTool::new(Arc::new(ClockBridge {
            host: Arc::clone(&clock),
        })));
        registry.add(ListPastConversationsTool::new(Arc::new(
            ConversationsBridge {
                sessions_dir: sessions_dir.clone(),
                clock: Arc::clone(&clock),
            },
        )));

        Self {
            sessions_dir,
            clock,
            runtime: ToolCallRuntime::new(Arc::new(registry)),
            attached: Mutex::new(None),
            cancellation: Mutex::new(CancellationToken::new()),
        }
    }

    /// Attaches to a conversation and loads whatever its own file already holds.
    ///
    /// The provider's metadata is read here and the credential attached only for the requests
    /// this conversation makes.
    pub fn start_conversation(
        &self,
        session_id: &SessionId,
        provider: ResolvedProvider,
        model: &ModelConfig,
        instruction: &str,
        settings: &AgentSettings,
    ) -> Result<(), AgentError> {
        let http = HttpClientBuilder::new()
            .build()
            .map_err(|error| AgentError::Transport(error.to_string()))?;
        let client = match create_api_client(&provider, ReqwestTransport::from_http_client(http)) {
            ApiClient::Chat(client) => {
                ModelClient::chat_completions(*client, model.model_id.clone())
            }
            ApiClient::Responses(client) => ModelClient::responses(*client, model.model_id.clone()),
        }
        // How much this model may write per response. It belongs to the model rather than to the
        // conversation, so a model switch inside a conversation does move it, unlike the window.
        .with_max_output_tokens(output_token_cap(model));

        let (mut rollout, history, unfinished, stored_context_window, stored_usage, stored_effort) =
            match find_session_path(&self.sessions_dir, session_id.as_str())
                .map_err(|error| AgentError::Transcript(error.to_string()))?
            {
                Some(path) => {
                    let history = read_response_items(&path)
                        .map_err(|error| AgentError::Transcript(error.to_string()))?;
                    // What the file says about its last turn: one it never closed is the part the
                    // platform may ask to have continued.
                    let unfinished = jasmine_rollout::interrupted_turn(&path);
                    // The window the platform picked for this conversation, if it ever did.
                    let window = jasmine_rollout::context_window_tokens(&path);
                    // What it last reported costing — the platform shows this after a restart.
                    let usage = jasmine_rollout::token_usage(&path);
                    // 这个会话当前的推理档位（最后一次改动的值）；一条都没有 = 还没记过。
                    let effort = jasmine_rollout::reasoning_effort_value(&path);
                    let rollout = RolloutRecorder::open(path)
                        .map_err(|error| AgentError::Transcript(error.to_string()))?;
                    (rollout, history, unfinished, window, usage, effort)
                }
                None => {
                    let meta = SessionMeta {
                        session_id: session_id.as_str().to_string(),
                        timestamp: timestamp_now(),
                        title: String::new(),
                        provider_id: provider.info().id.clone(),
                        model_id: model.model_id.clone(),
                    };
                    let rollout = RolloutRecorder::create(&self.sessions_dir, &meta)
                        .map_err(|error| AgentError::Transcript(error.to_string()))?;
                    (rollout, Vec::new(), None, None, None, None)
                }
            };

        // What window this conversation runs against. It is decided once — the first time the
        // conversation is attached, from the model the platform had picked then — and written
        // down, so a model switch inside the conversation leaves the window where it was.
        let context_window_tokens = match stored_context_window {
            Some(tokens) => tokens,
            None => {
                let tokens = starting_context_window(model);
                record_boundary(&mut rollout, RolloutItem::ContextWindow { tokens })?;
                tokens
            }
        };

        let provider_id = provider.info().id.as_str();
        // 这个会话的推理档位：第一次附着时定一条起点档（新建对话读一次），之后只认会话自己的记录。
        // 起点档按这个次序找：**目录**（后端给的 —— 目录里有这个模型就听它的）→ 模型配置里那份默认档
        // （只可能是目录外的模型，界面也只给它们显示那一栏）→ 都没有就是"未设置"（一个字段都不发）。
        //
        // 但存过的那一档**新模型不一定支持**（同一会话里换模型之后尤其如此）。codex 换模型时也做同一
        // 件事（它 `turn_context` 里按 `supported_reasoning_levels` 换档）：不支持就落到这个模型自己的
        // 起点档，绝不把不支持的值发出去。矫正的结果会**追加**一条记录（历史不删），界面附着后回读到
        // 的就是真正在用的值。
        let stored_effort = stored_effort.filter(|value| {
            jasmine_model_provider_info::presets::supports_level(
                provider_id,
                &model.model_id,
                value,
            )
        });
        let reasoning_effort = match stored_effort {
            Some(value) => value,
            None => {
                let value = jasmine_model_provider_info::presets::default_level(
                    provider_id,
                    &model.model_id,
                )
                .map(str::to_string)
                .unwrap_or_else(|| model.reasoning_effort.clone());
                record_boundary(
                    &mut rollout,
                    RolloutItem::ReasoningEffort {
                        value: value.clone(),
                    },
                )?;
                value
            }
        };
        // 请求侧发的是**解析后的取值**：`ultra` 这种界面档在这里换成这个模型支持的最强档。
        let wire_effort =
            jasmine_model_provider_info::presets::wire_level(provider_id, &model.model_id, &reasoning_effort);
        let client = client.with_reasoning_effort(parse_reasoning_effort(&wire_effort));

        let mut thread = ChatThread::new();
        thread.start_session(session_id.as_str());
        // Noted after the attach, which clears whatever the previous conversation had.
        thread.note_context_window(to_tokens(context_window_tokens));
        // And what it had cost, so the window the platform shows has its figure from the start.
        if let Some((info, breakdown)) = stored_usage {
            thread.note_usage_record(info, breakdown);
        }

        *self.lock()? = Some(Attached {
            client,
            model: model.clone(),
            // 语言那部分由核心拼（界面只传值）：系统指令 = 平台给的人格 + 核心的输出语言规则。
            instruction: settings.system_instruction(instruction),
            history,
            thread,
            rollout,
            cancellation: CancellationToken::new(),
            turn_id: unfinished.clone().unwrap_or_default(),
            interrupted: unfinished.is_some(),
            context_window_tokens,
            reasoning_effort,
        });
        Ok(())
    }

    /// The window the attached conversation runs against, in tokens.
    ///
    /// Nothing is attached before the platform has attached a conversation, and nothing to report
    /// either — the platform's own default applies until then.
    pub fn context_window(&self) -> Option<i64> {
        self.lock().ok().and_then(|attached| {
            attached
                .as_ref()
                .map(|attached| attached.thread.context_window())
        })
    }

    /// Sets the window the attached conversation runs against and records it in its file.
    ///
    /// The platform owns the choice: it is written down so the same conversation is resumed with it,
    /// and reported back straight away so the platform can redraw without waiting for a reply.
    pub fn set_context_window(
        &self,
        tokens: u64,
        sink: &mut dyn ChatSink,
    ) -> Result<(), AgentError> {
        let mut guard = self.lock()?;
        let attached = guard.as_mut().ok_or(AgentError::NoSession)?;
        let Attached {
            thread,
            rollout,
            context_window_tokens,
            ..
        } = attached;
        record_boundary(rollout, RolloutItem::ContextWindow { tokens })?;
        *context_window_tokens = tokens;
        thread.note_context_window(to_tokens(tokens));
        if let Some(event) = thread.usage_event() {
            sink.emit(event);
        }
        Ok(())
    }

    /// 这个会话当前的推理档位；没有附着会话时 `None`。
    pub fn reasoning_effort(&self) -> Option<String> {
        self.lock().ok().and_then(|attached| {
            attached
                .as_ref()
                .map(|attached| attached.reasoning_effort.clone())
        })
    }

    /// 改这个会话的推理档位，并把这次改动**追加**进它的文件。
    ///
    /// 追加而不是覆盖：每改一次多一条，所以「未设置 → 高 → 低」这样的历史完整留在文件里，一条都不删。
    /// 改完立刻生效 —— 请求侧从这一刻起用会话自己的值（不再看模型配置）。
    pub fn set_reasoning_effort(&self, value: &str) -> Result<(), AgentError> {
        let mut guard = self.lock()?;
        let attached = guard.as_mut().ok_or(AgentError::NoSession)?;
        let Attached {
            client,
            rollout,
            reasoning_effort,
            ..
        } = attached;
        record_boundary(
            rollout,
            RolloutItem::ReasoningEffort {
                value: value.to_string(),
            },
        )?;
        *reasoning_effort = value.to_string();
        client.set_reasoning_effort(parse_reasoning_effort(value));
        Ok(())
    }

    /// The window one conversation recorded, read straight from its file.
    ///
    /// The platform needs this before attaching — it is what the window picker shows for a
    /// conversation it has just opened. `None` means the conversation never got one, which is what
    /// the platform's own default fills in.
    pub fn conversation_context_window(
        &self,
        session_id: &SessionId,
    ) -> Result<Option<u64>, AgentError> {
        let Some(path) = find_session_path(&self.sessions_dir, session_id.as_str())
            .map_err(|error| AgentError::Transcript(error.to_string()))?
        else {
            return Ok(None);
        };
        Ok(jasmine_rollout::context_window_tokens(&path))
    }

    /// 某条会话自己记录的推理档位，直接从它的文件里读（最后一条为准）；没记录过就是 `None`。
    ///
    /// 平台打开一条会话、还没发消息时用它把输入框那边的档位显示成这条会话自己的值（不是界面默认，
    /// 也不是模型的默认）。
    pub fn conversation_reasoning_effort(
        &self,
        session_id: &SessionId,
    ) -> Result<Option<String>, AgentError> {
        let Some(path) = find_session_path(&self.sessions_dir, session_id.as_str())
            .map_err(|error| AgentError::Transcript(error.to_string()))?
        else {
            return Ok(None);
        };
        Ok(jasmine_rollout::reasoning_effort_value(&path))
    }

    /// What one conversation last reported costing, read straight from its file.
    ///
    /// The platform shows this when a conversation is opened, before anything new is sent — without
    /// it, a restarted app would have nothing to put next to the window it shows.
    pub fn conversation_usage(
        &self,
        session_id: &SessionId,
    ) -> Result<Option<(TokenUsageInfo, Vec<ContextUsageBreakdownItem>)>, AgentError> {
        let Some(path) = find_session_path(&self.sessions_dir, session_id.as_str())
            .map_err(|error| AgentError::Transcript(error.to_string()))?
        else {
            return Ok(None);
        };
        Ok(jasmine_rollout::token_usage(&path))
    }

    /// What the app has spent **this month**, read back out of every conversation's own file.
    ///
    /// Only the current month is counted — nothing older than the first of the month reaches the
    /// figures, so last month's numbers fall away on their own.
    pub fn usage_stats(&self) -> Result<AppUsageStats, AgentError> {
        jasmine_rollout::usage_stats(&self.sessions_dir)
            .map_err(|error| AgentError::Transcript(error.to_string()))
    }

    /// How many messages the conversation's context holds.
    pub fn context_len(&self) -> usize {
        self.lock()
            .ok()
            .and_then(|attached| attached.as_ref().map(|attached| attached.history.len()))
            .unwrap_or(0)
    }

    /// Appends the user's message and streams the reply.
    pub fn send(&self, text: &str, sink: &mut dyn ChatSink) -> Result<(), AgentError> {
        let mut guard = self.lock()?;
        let attached = guard.as_mut().ok_or(AgentError::NoSession)?;
        let Attached {
            client,
            model,
            instruction,
            history,
            thread,
            rollout,
            cancellation,
            turn_id,
            interrupted,
            reasoning_effort,
            ..
        } = attached;
        let instruction = instruction_option(instruction);
        let registry = self.runtime.registry();
        let runtime = &self.runtime;
        let mut emit = |event: ChatEvent| sink.emit(event);
        let recorded = history.len();
        *cancellation = self.fresh_cancellation()?;
        let started = std::time::Instant::now();
        *turn_id = uuid::Uuid::new_v4().to_string();
        *interrupted = false;
        record_boundary(
            rollout,
            RolloutItem::TurnStarted {
                turn_id: turn_id.clone(),
                model_id: model.model_id.clone(),
                // 记的是**这个会话当前**的档位（模型级只是新建会话时抄一次的起点），所以往回翻看到的
                // 就是"这一轮实际用的档位"。
                reasoning_effort: reasoning_effort.clone(),
            },
        )?;

        let outcome = block_on(send_text(
            Turn {
                client,
                thread,
                registry,
                runtime,
                history,
                instruction,
                input_modalities: &model.input_modalities,
                cancellation,
            },
            text.to_string(),
            &mut emit,
        ));
        if matches!(outcome, Ok(Err(SessionError::TurnAborted))) {
            // Recorded before the abort event, as upstream does: a client that re-reads the
            // rollout on that event must already see why the turn ended.
            history.push(interrupted_turn_marker());
        }
        record_turn(rollout, history, recorded)?;
        record_usage(rollout, thread)?;
        finish_turn(
            rollout,
            &mut emit,
            turn_id,
            interrupted,
            started.elapsed().as_millis() as u64,
            outcome?,
        )
    }

    /// Answers the prompts a turn stopped on and continues that turn.
    pub fn respond_to_prompts(
        &self,
        answers: &[String],
        sink: &mut dyn ChatSink,
    ) -> Result<(), AgentError> {
        let mut guard = self.lock()?;
        let attached = guard.as_mut().ok_or(AgentError::NoSession)?;
        let Attached {
            client,
            model,
            instruction,
            history,
            thread,
            rollout,
            cancellation,
            turn_id,
            interrupted,
            reasoning_effort,
            ..
        } = attached;
        let instruction = instruction_option(instruction);
        let registry = self.runtime.registry();
        let runtime = &self.runtime;
        let mut emit = |event: ChatEvent| sink.emit(event);
        let recorded = history.len();
        *cancellation = self.fresh_cancellation()?;
        let started = std::time::Instant::now();
        *turn_id = uuid::Uuid::new_v4().to_string();
        *interrupted = false;
        record_boundary(
            rollout,
            RolloutItem::TurnStarted {
                turn_id: turn_id.clone(),
                model_id: model.model_id.clone(),
                // 记的是**这个会话当前**的档位（模型级只是新建会话时抄一次的起点），所以往回翻看到的
                // 就是"这一轮实际用的档位"。
                reasoning_effort: reasoning_effort.clone(),
            },
        )?;

        let outcome = block_on(continue_turn(
            Turn {
                client,
                thread,
                registry,
                runtime,
                history,
                instruction,
                input_modalities: &model.input_modalities,
                cancellation,
            },
            answers,
            &mut emit,
        ));
        if matches!(outcome, Ok(Err(SessionError::TurnAborted))) {
            // Recorded before the abort event, as upstream does: a client that re-reads the
            // rollout on that event must already see why the turn ended.
            history.push(interrupted_turn_marker());
        }
        record_turn(rollout, history, recorded)?;
        record_usage(rollout, thread)?;
        finish_turn(
            rollout,
            &mut emit,
            turn_id,
            interrupted,
            started.elapsed().as_millis() as u64,
            outcome?,
        )
    }

    /// One conversation's unfinished turn, if it has one.
    ///
    /// It is a fact about the conversation's own file, so it answers whether or not the core is
    /// attached to anything — a turn interrupted before the app restarted still counts, which is
    /// what lets the platform keep offering to continue it.
    pub fn interrupted_turn(&self, session_id: &str) -> Result<Option<String>, AgentError> {
        let path = find_session_path(&self.sessions_dir, session_id)
            .map_err(|error| AgentError::Transcript(error.to_string()))?;
        Ok(path.and_then(|path| jasmine_rollout::interrupted_turn(&path)))
    }

    /// Continues the answer the last turn stopped on, as its own turn.
    ///
    /// Nothing is added to the conversation: sampling restarts under the turn id the file already
    /// carries, which is what the platform's continue affordance asks for. What the model then does
    /// with the interrupted transcript — pick the answer up or write it again — is the model's own
    /// call. A turn that finished is not continued: the call does nothing.
    pub fn recover_turn(&self, sink: &mut dyn ChatSink) -> Result<(), AgentError> {
        let mut guard = self.lock()?;
        let attached = guard.as_mut().ok_or(AgentError::NoSession)?;
        if !attached.interrupted {
            return Ok(());
        }

        let Attached {
            client,
            model,
            instruction,
            history,
            thread,
            rollout,
            cancellation,
            turn_id,
            interrupted,
            reasoning_effort,
            ..
        } = attached;
        let instruction = instruction_option(instruction);
        let registry = self.runtime.registry();
        let runtime = &self.runtime;
        let mut emit = |event: ChatEvent| sink.emit(event);
        let recorded = history.len();
        *cancellation = self.fresh_cancellation()?;
        let started = std::time::Instant::now();
        *interrupted = false;
        // The id stays as it is: sampling restarts under the id the file already recorded for that
        // turn, which is what upstream keeps (`RecoverTurnRequest.turn_id` has to be an id that was
        // already recorded) and what makes the resume read as the same turn.
        record_boundary(
            rollout,
            RolloutItem::TurnStarted {
                turn_id: turn_id.clone(),
                model_id: model.model_id.clone(),
                // 记的是**这个会话当前**的档位（模型级只是新建会话时抄一次的起点），所以往回翻看到的
                // 就是"这一轮实际用的档位"。
                reasoning_effort: reasoning_effort.clone(),
            },
        )?;

        let outcome = block_on(crate::session::run_turn(
            Turn {
                client,
                thread,
                registry,
                runtime,
                history,
                instruction,
                input_modalities: &model.input_modalities,
                cancellation,
            },
            &mut emit,
        ));
        if matches!(outcome, Ok(Err(SessionError::TurnAborted))) {
            // Recorded before the abort event, as upstream does: a client that re-reads the
            // rollout on that event must already see why the turn ended.
            history.push(interrupted_turn_marker());
        }
        record_turn(rollout, history, recorded)?;
        record_usage(rollout, thread)?;
        finish_turn(
            rollout,
            &mut emit,
            turn_id,
            interrupted,
            started.elapsed().as_millis() as u64,
            outcome?,
        )
    }

    /// Stops the turn that is running.
    ///
    /// Nothing is cut off mid-flight: the turn notices at its next await point, keeps whatever it
    /// had already produced, and reports itself as aborted.
    pub fn interrupt(&self) -> Result<(), AgentError> {
        let token = self
            .cancellation
            .lock()
            .map_err(|_| AgentError::Poisoned)?
            .clone();
        token.cancel();
        Ok(())
    }

    /// A token for the turn about to run, shared with [Self::interrupt].
    ///
    /// The clone and the turn's own handle share one state, so cancelling through the service
    /// reaches the turn that is running.
    fn fresh_cancellation(&self) -> Result<CancellationToken, AgentError> {
        let token = CancellationToken::new();
        *self.cancellation.lock().map_err(|_| AgentError::Poisoned)? = token.clone();
        Ok(token)
    }

    /// Keeps the half-written reply a cancelled turn left behind.
    ///
    /// The platform owns the durable transcript; this puts the same text into the model's
    /// context so both sides agree on what was said.
    pub fn persist_interrupted_reply(&self, text: &str) -> Result<(), AgentError> {
        if text.trim().is_empty() {
            return Ok(());
        }

        let mut guard = self.lock()?;
        let attached = guard.as_mut().ok_or(AgentError::NoSession)?;
        let item = ResponseItem::Message {
            id: None,
            role: Role::Model.as_str().to_string(),
            content: vec![ContentItem::OutputText {
                text: text.to_string(),
            }],
        };
        attached.history.push(item.clone());
        attached
            .rollout
            .record_items(&[RolloutItem::ResponseItem(item)])
            .map_err(|error| AgentError::Transcript(error.to_string()))?;
        Ok(())
    }

    /// Creates a conversation's file, so a new conversation exists before it is attached.
    ///
    /// The title is the platform's: it is what the list shows, and the core only stores it.
    pub fn create_conversation(
        &self,
        session_id: &SessionId,
        provider_id: &str,
        model_id: &str,
        title: &str,
    ) -> Result<(), AgentError> {
        if find_session_path(&self.sessions_dir, session_id.as_str())
            .map_err(|error| AgentError::Transcript(error.to_string()))?
            .is_some()
        {
            return Ok(());
        }

        let meta = SessionMeta {
            session_id: session_id.as_str().to_string(),
            timestamp: timestamp_now(),
            title: title.to_string(),
            provider_id: provider_id.to_string(),
            model_id: model_id.to_string(),
        };
        RolloutRecorder::create(&self.sessions_dir, &meta)
            .map(|_| ())
            .map_err(|error| AgentError::Transcript(error.to_string()))
    }

    /// Removes a conversation and its file.
    pub fn delete_conversation(&self, session_id: &SessionId) -> Result<(), AgentError> {
        delete_session(&self.sessions_dir, session_id.as_str())
            .map_err(|error| AgentError::Transcript(error.to_string()))
    }

    /// The conversations this app has, the most recently written first.
    pub fn conversations(&self) -> Vec<ConversationSummary> {
        match list_sessions(&self.sessions_dir) {
            Ok(entries) => entries
                .into_iter()
                .map(|entry| ConversationSummary {
                    session_id: entry.meta.session_id,
                    title: entry.meta.title,
                    provider_id: entry.meta.provider_id,
                    model_id: entry.meta.model_id,
                    updated_at: millis(&entry.updated_at),
                })
                .collect(),
            Err(_) => Vec::new(),
        }
    }

    /// One conversation's transcript, in the order it happened.
    pub fn transcript(&self, session_id: &SessionId) -> Vec<HistoryEntry> {
        let Ok(Some(path)) = find_session_path(&self.sessions_dir, session_id.as_str()) else {
            return Vec::new();
        };
        let Ok(items) = read_timed_items(&path) else {
            return Vec::new();
        };

        // A turn's model is written at its start, so every line it produced can say which model
        // the user's message went to — a conversation that switched models keeps them apart.
        let mut model_label: Option<String> = None;
        // The file keeps a call and its result on separate lines; the platform shows one card, so
        // they are paired here by the id the call was given (call id, name, arguments, when).
        let mut pending: Vec<(String, String, String, i64)> = Vec::new();
        let mut lines: Vec<HistoryEntry> = Vec::new();
        for (timestamp, item) in items.iter() {
            let at = millis(timestamp);
            match item {
                RolloutItem::TurnStarted { model_id, .. } => model_label = Some(model_id.clone()),
                RolloutItem::ResponseItem(ResponseItem::FunctionCall {
                    name,
                    arguments,
                    call_id,
                    ..
                }) => pending.push((call_id.clone(), name.clone(), arguments.clone(), at)),
                RolloutItem::ResponseItem(ResponseItem::FunctionCallOutput {
                    call_id,
                    output,
                    ..
                }) => {
                    let answered = call_id.as_deref();
                    match pending
                        .iter()
                        .position(|(id, ..)| Some(id.as_str()) == answered)
                    {
                        Some(index) => {
                            let (_, name, arguments, called_at) = pending.remove(index);
                            lines.push(tool_line(
                                name,
                                arguments,
                                Some(output.text_content().unwrap_or_default().to_string()),
                                called_at,
                                model_label.clone(),
                            ));
                        }
                        // A result whose call is gone still says something happened.
                        None => {
                            let text = output.text_content().unwrap_or_default().to_string();
                            if !text.is_empty() {
                                lines.push(HistoryEntry {
                                    role: Role::Model,
                                    text,
                                    tool_call_id: call_id.clone(),
                                    stopped_after_ms: None,
                                    recorded_at: at,
                                    model_label: model_label.clone(),
                                    tool_name: None,
                                    tool_detail: None,
                                    tool_result: None,
                                    thinking: String::new(),
                                    tool_status: String::new(),
                                });
                            }
                        }
                    }
                }
                // 一段推理就是转写里的一行：它在会话文件里的位置，就是它在界面上的位置。
                // 不攒、也不挂到别的行上 —— 那样只对"工具前 / 回复前"这些特定位置成立，段数再多一段、
                // 或者回合中途就结束，都会漏掉或错位。
                RolloutItem::ResponseItem(ResponseItem::Reasoning {
                    content, summary, ..
                }) => {
                    let mut parts: Vec<&str> = content
                        .iter()
                        .flatten()
                        .map(|part| match part {
                            jasmine_protocol::models::ReasoningItemContent::ReasoningText {
                                text,
                            }
                            | jasmine_protocol::models::ReasoningItemContent::Text { text } => {
                                text.as_str()
                            }
                        })
                        .collect();
                    parts.extend(summary.iter().map(|part| match part {
                        jasmine_protocol::models::ReasoningItemReasoningSummary::SummaryText {
                            text,
                        } => text.as_str(),
                    }));
                    let text = parts.join("\n");
                    if !text.trim().is_empty() {
                        lines.push(HistoryEntry {
                            role: Role::Model,
                            text: String::new(),
                            tool_call_id: None,
                            stopped_after_ms: None,
                            recorded_at: at,
                            model_label: model_label.clone(),
                            tool_name: None,
                            tool_detail: None,
                            tool_result: None,
                            thinking: text.trim().to_string(),
                            tool_status: String::new(),
                        });
                    }
                }
                RolloutItem::ResponseItem(recorded) => {
                    if let Some(mut entry) = transcript_entry(recorded) {
                        // 上一轮里没等到返回的调用，以"没有结果"的模样收口成一行（界面上显示成
                        // 「已停止」）—— 否则那些卡会凭空消失，重启前后对不上。
                        if entry.role == Role::User {
                            for (id, name, arguments, called_at) in pending.drain(..) {
                                let mut line = tool_line(
                                    name,
                                    arguments,
                                    None,
                                    called_at,
                                    model_label.clone(),
                                );
                                line.tool_call_id = Some(id);
                                line.tool_status = "stopped".to_string();
                                line.tool_status = "stopped".to_string();
                                lines.push(line);
                            }
                        }
                        entry.recorded_at = at;
                        entry.model_label = model_label.clone();
                        lines.push(entry);
                    }
                }
                // A turn that stopped is a line of its own on the platform, between the two
                // messages it sits between.
                RolloutItem::TurnAborted { duration_ms, .. } => {
                    // 被打断的那一轮里没等到返回的调用，同样收口成一行（「已停止」）。
                    for (id, name, arguments, called_at) in pending.drain(..) {
                        let mut line =
                            tool_line(name, arguments, None, called_at, model_label.clone());
                        line.tool_call_id = Some(id);
                        line.tool_status = "stopped".to_string();
                        lines.push(line);
                    }
                    lines.push(HistoryEntry {
                        role: Role::Model,
                        text: String::new(),
                        tool_call_id: None,
                        stopped_after_ms: Some(*duration_ms),
                        recorded_at: at,
                        model_label: None,
                        tool_name: None,
                        tool_detail: None,
                        tool_result: None,
                        thinking: String::new(),
                        tool_status: String::new(),
                    });
                }
                // The window, the usage and the reasoning effort are properties of the conversation,
                // not lines of the transcript.
                RolloutItem::SessionMeta(_)
                | RolloutItem::TurnComplete { .. }
                | RolloutItem::ContextWindow { .. }
                | RolloutItem::ReasoningEffort { .. }
                | RolloutItem::TokenUsageRecord { .. } => {}
            }
        }
        // A call that never got a result — a batch the platform stopped — still shows its card.
        for (_, name, arguments, called_at) in pending {
            lines.push(tool_line(
                name,
                arguments,
                None,
                called_at,
                model_label.clone(),
            ));
        }
        lines
    }

    /// Releases the conversation. Its stored history is left untouched.
    pub fn end_conversation(&self) {
        if let Ok(mut attached) = self.lock() {
            *attached = None;
        }
    }

    /// The clock the tools ask for, for callers that build their own tools.
    pub fn clock(&self) -> Arc<dyn Clock> {
        Arc::clone(&self.clock)
    }

    fn lock(&self) -> Result<MutexGuard<'_, Option<Attached>>, AgentError> {
        self.attached.lock().map_err(|_| AgentError::Poisoned)
    }
}

/// A recorded moment in the unit the platform's list orders by.
///
/// An unparsable stamp (a hand-edited file) is 0, which the interface reads as "no time".
fn millis(timestamp: &str) -> i64 {
    chrono::DateTime::parse_from_rfc3339(timestamp)
        .map(|moment| moment.timestamp_millis())
        .unwrap_or(0)
}

/// One tool call as the platform shows it: the call and its result on a single line.
///
/// The detail and the result are abbreviated the same way the live turn abbreviates them, so a
/// reloaded conversation reads exactly like the one that was just streamed.
fn tool_line(
    name: String,
    arguments: String,
    result: Option<String>,
    recorded_at: i64,
    model_label: Option<String>,
) -> HistoryEntry {
    HistoryEntry {
        role: Role::Model,
        text: String::new(),
        tool_call_id: None,
        stopped_after_ms: None,
        recorded_at,
        model_label,
        tool_name: Some(name),
        tool_detail: Some(jasmine_protocol::chat_event::abbreviate(&arguments)),
        tool_result: result.map(|result| jasmine_protocol::chat_event::abbreviate(&result)),
        thinking: String::new(),
        // 成对落下的工具行就是"执行完成"。
        tool_status: "completed".to_string(),
    }
}

/// What the platform's transcript shows for one recorded item.
///
/// A tool result is shown as the model's own line, keyed by the call it answers.
fn transcript_entry(item: &ResponseItem) -> Option<HistoryEntry> {
    match item {
        ResponseItem::Message { role, content, .. } => {
            let text = content
                .iter()
                .filter_map(|part| match part {
                    ContentItem::InputText { text } | ContentItem::OutputText { text } => {
                        Some(text.as_str())
                    }
                    ContentItem::InputImage { .. } | ContentItem::InputAudio { .. } => None,
                })
                .collect::<String>();
            // A developer item is context the core injected, not something the conversation said.
            if text.is_empty() || role == "developer" {
                return None;
            }
            Some(HistoryEntry {
                role: if role == Role::User.as_str() {
                    Role::User
                } else {
                    Role::Model
                },
                text,
                tool_call_id: None,
                stopped_after_ms: None,
                recorded_at: 0,
                model_label: None,
                tool_name: None,
                tool_detail: None,
                tool_result: None,
                thinking: String::new(),
                tool_status: String::new(),
            })
        }
        ResponseItem::FunctionCallOutput {
            call_id, output, ..
        } => Some(HistoryEntry {
            role: Role::Model,
            text: output
                .text_content()
                .map(str::to_string)
                .unwrap_or_default(),
            tool_call_id: call_id.clone(),
            stopped_after_ms: None,
            recorded_at: 0,
            model_label: None,
            tool_name: None,
            tool_detail: None,
            tool_result: None,
            // 有结果的工具行就是"执行完成"。
            tool_status: "completed".to_string(),
            thinking: String::new(),
        }),
        ResponseItem::Reasoning { .. }
        | ResponseItem::FunctionCall { .. }
        | ResponseItem::Other => None,
    }
}

/// The instruction is only sent when there is one: an empty one would replace the provider's own.
fn instruction_option(instruction: &str) -> Option<String> {
    let trimmed = instruction.trim();
    (!trimmed.is_empty()).then(|| trimmed.to_string())
}

/// The model's context window, as the platform configured it. Zero means it never was.
fn model_context_window(model: &ModelConfig) -> Option<u64> {
    (model.context_length > 0).then(|| u64::from(model.context_length))
}

/// The most the model may write in one response, as the platform configured it. Zero means it
/// never was — every request then leaves the cap to the provider, which is the default.
fn output_token_cap(model: &ModelConfig) -> Option<u32> {
    (model.max_output_length > 0).then_some(model.max_output_length)
}

/// 把这个会话的推理档位解析成请求要的形状。空串 —— 或者这个 client 不认识的词 —— 就是"没设置"：
/// 请求里一个推理字段都不发。
fn parse_reasoning_effort(value: &str) -> Option<jasmine_protocol::openai_models::ReasoningEffort> {
    value.parse().ok()
}

/// How much context a conversation runs against when neither it nor its model says.
///
/// A conversation with no window at all would have no denominator to show, so one is assumed; the
/// platform can pick another per conversation.
const DEFAULT_CONTEXT_WINDOW: u64 = 200_000;

/// The window a conversation starts with: the model's own setting, or the default.
fn starting_context_window(model: &ModelConfig) -> u64 {
    model_context_window(model).unwrap_or(DEFAULT_CONTEXT_WINDOW)
}

/// A window the thread can carry: token counts stay well inside `i64`.
fn to_tokens(context_window: u64) -> i64 {
    i64::try_from(context_window).unwrap_or(i64::MAX)
}

/// Writes down what the turn cost, so a conversation reopened later still has something to show.
///
/// A turn that reported nothing leaves the file as it was: the previous figure is still the last
/// thing that was known.
fn record_usage(rollout: &mut RolloutRecorder, thread: &ChatThread) -> Result<(), AgentError> {
    let Some((info, breakdown)) = thread.usage_record() else {
        return Ok(());
    };
    record_boundary(rollout, RolloutItem::TokenUsageRecord { info, breakdown })
}

/// The marker the core leaves in the conversation when a turn is interrupted on purpose.
///
/// It is the shape upstream records on its interrupt path (`reason == Interrupted`): a developer
/// item carrying the interrupted-turn guidance, written before the abort event so a client that
/// re-reads the rollout on that event already sees it. The platform does not show it — see
/// [transcript_entry] — and the next request sends it to the model.
fn interrupted_turn_marker() -> ResponseItem {
    ResponseItem::Message {
        id: None,
        role: "developer".to_string(),
        content: vec![ContentItem::InputText {
            text: "<turn_aborted>\n\
                   The previous turn was interrupted on purpose. Any running unified exec \
                   processes may still be running in the background. If any tools/commands were \
                   aborted, they may have partially executed.\n\
                   </turn_aborted>"
                .to_string(),
        }],
    }
}

/// Appends one turn boundary, so the file says which turn is still open.
fn record_boundary(rollout: &mut RolloutRecorder, item: RolloutItem) -> Result<(), AgentError> {
    rollout
        .record_items(&[item])
        .map_err(|error| AgentError::Transcript(error.to_string()))
}

/// Closes a turn in the file and tells the platform how it ended.
///
/// A turn that stopped before finishing is recorded as stopped, which is what lets the platform
/// offer to continue it.
fn finish_turn(
    rollout: &mut RolloutRecorder,
    emit: &mut dyn FnMut(ChatEvent),
    turn_id: &str,
    interrupted: &mut bool,
    duration_ms: u64,
    outcome: Result<(), SessionError>,
) -> Result<(), AgentError> {
    match outcome {
        Ok(()) => record_boundary(
            rollout,
            RolloutItem::TurnComplete {
                turn_id: turn_id.to_string(),
            },
        ),
        Err(SessionError::TurnAborted) => {
            *interrupted = true;
            record_boundary(
                rollout,
                RolloutItem::TurnAborted {
                    turn_id: turn_id.to_string(),
                    reason: jasmine_rollout::TurnAbortReason::Interrupted,
                    duration_ms,
                },
            )?;
            emit(ChatEvent::Aborted { duration_ms });
            Ok(())
        }
        Err(error) => Err(error.into()),
    }
}

/// Writes what one turn added into the conversation's own file.
///
/// The file is the transcript: the platform's list and the next request's context both read
/// it, so an item in the history is an item in the file.
fn record_turn(
    rollout: &mut RolloutRecorder,
    history: &[ResponseItem],
    recorded: usize,
) -> Result<(), AgentError> {
    let items = history[recorded..]
        .iter()
        .cloned()
        .map(RolloutItem::ResponseItem)
        .collect::<Vec<_>>();
    rollout
        .record_items(&items)
        .map_err(|error| AgentError::Transcript(error.to_string()))
}

/// Drives one operation of the async core to completion.
fn block_on<F: Future>(operation: F) -> Result<F::Output, AgentError> {
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .map_err(|error| AgentError::Runtime(error.to_string()))?;
    Ok(runtime.block_on(operation))
}

/// Hands the session's clock to the tool that asks for one.
struct ClockBridge {
    host: Arc<dyn Clock>,
}

impl ToolClock for ClockBridge {
    fn now_formatted(&self) -> String {
        self.host.now_formatted()
    }
}

/// Hands the session's conversation list to the tool that asks for one.
struct ConversationsBridge {
    sessions_dir: PathBuf,
    clock: Arc<dyn Clock>,
}

impl ConversationTitles for ConversationsBridge {
    fn conversations(&self) -> Vec<ToolConversationSummary> {
        match list_sessions(&self.sessions_dir) {
            Ok(entries) => entries
                .into_iter()
                .map(|entry| ToolConversationSummary {
                    title: entry.meta.title,
                    updated_at: self.clock.format(&entry.updated_at),
                })
                .collect(),
            Err(_) => Vec::new(),
        }
    }
}

#[cfg(test)]
#[path = "service_tests.rs"]
mod tests;
