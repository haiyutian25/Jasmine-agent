//! The conversation facade the platform drives.
//!
//! It owns what a conversation needs between calls: the client for the provider's wire protocol,
//! the context so far, and the live thread state. Attaching is explicit — switching provider or
//! model means starting a new conversation.
//!
//! The core underneath is async while the boundary is synchronous, so each call drives its
//! operation on a runtime of its own. The service is stateful and holds the conversation while a
//! call runs: one instance per conversation owner, one call at a time.

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
    ) -> Result<(), AgentError> {
        let http = HttpClientBuilder::new()
            .build()
            .map_err(|error| AgentError::Transport(error.to_string()))?;
        let client = match create_api_client(&provider, ReqwestTransport::from_http_client(http)) {
            ApiClient::Chat(client) => {
                ModelClient::chat_completions(*client, model.model_id.clone())
            }
            ApiClient::Responses(client) => ModelClient::responses(*client, model.model_id.clone()),
        };

        let (rollout, history, unfinished) =
            match find_session_path(&self.sessions_dir, session_id.as_str())
                .map_err(|error| AgentError::Transcript(error.to_string()))?
            {
                Some(path) => {
                    let history = read_response_items(&path)
                        .map_err(|error| AgentError::Transcript(error.to_string()))?;
                    // What the file says about its last turn: one it never closed is the part the
                    // platform may ask to have continued.
                    let unfinished = jasmine_rollout::interrupted_turn(&path);
                    let rollout = RolloutRecorder::open(path)
                        .map_err(|error| AgentError::Transcript(error.to_string()))?;
                    (rollout, history, unfinished)
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
                    (rollout, Vec::new(), None)
                }
            };

        let mut thread = ChatThread::new();
        thread.start_session(session_id.as_str());

        *self.lock()? = Some(Attached {
            client,
            model: model.clone(),
            instruction: instruction.to_string(),
            history,
            thread,
            rollout,
            cancellation: CancellationToken::new(),
            turn_id: unfinished.clone().unwrap_or_default(),
            interrupted: unfinished.is_some(),
        });
        Ok(())
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
        items
            .iter()
            .filter_map(|(timestamp, item)| {
                let at = millis(timestamp);
                let entry = match item {
                    RolloutItem::TurnStarted { model_id, .. } => {
                        model_label = Some(model_id.clone());
                        None
                    }
                    RolloutItem::ResponseItem(recorded) => transcript_entry(recorded),
                    // A turn that stopped is a line of its own on the platform, between the two
                    // messages it sits between. A file from before the length was recorded has no
                    // seconds to show, so it gets no line.
                    RolloutItem::TurnAborted { duration_ms, .. } if *duration_ms > 0 => {
                        Some(HistoryEntry {
                            role: Role::Model,
                            text: String::new(),
                            tool_call_id: None,
                            stopped_after_ms: Some(*duration_ms),
                            recorded_at: at,
                            model_label: None,
                        })
                    }
                    RolloutItem::TurnAborted { .. } => None,
                    RolloutItem::SessionMeta(_) | RolloutItem::TurnComplete { .. } => None,
                };
                entry.map(|mut entry| {
                    entry.recorded_at = at;
                    entry.model_label = model_label.clone();
                    entry
                })
            })
            .collect()
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
    turn_id: &mut String,
    interrupted: &mut bool,
    duration_ms: u64,
    outcome: Result<(), SessionError>,
) -> Result<(), AgentError> {
    match outcome {
        Ok(()) => record_boundary(
            rollout,
            RolloutItem::TurnComplete {
                turn_id: turn_id.clone(),
            },
        ),
        Err(SessionError::TurnAborted) => {
            *interrupted = true;
            record_boundary(
                rollout,
                RolloutItem::TurnAborted {
                    turn_id: turn_id.clone(),
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
