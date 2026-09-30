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
    /// The platform stopped the turn before it finished.
    #[error("the turn was interrupted")]
    TurnAborted,
    #[error(transparent)]
    Api(#[from] ApiError),
}
