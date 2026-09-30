pub mod service;
pub mod turn;
pub mod turn_input;

pub use service::AgentChatService;
pub use service::AgentError;
pub use service::ChatSink;
pub use service::StoreListener;
pub use turn::run_turn;
pub use turn_input::respond_to_prompts;
pub use turn_input::send_text;

use jasmine_api::ApiError;

/// Why a session operation could not run.
#[derive(Debug, thiserror::Error)]
pub enum SessionError {
    /// The answers arrived when no prompt was waiting for one.
    #[error("no prompt is waiting for an answer")]
    NoPromptWaiting,
    /// 交上来的答案条数与待答的提问条数对不上。
    ///
    /// 与 [Self::NoPromptWaiting] 分开：那一个是"根本没有待答"，这一个是"有，但数量不对"。
    /// 两者都必须**一个都不消费**（见 `ChatThread::take_prompt_answers`）。
    #[error("expected {expected} answers for the prompts waiting, got {got}")]
    AnswerCountMismatch { expected: usize, got: usize },
    /// The platform stopped the turn before it finished.
    #[error("the turn was interrupted")]
    TurnAborted,
    #[error(transparent)]
    Api(#[from] ApiError),
}
