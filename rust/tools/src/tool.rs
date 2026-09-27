use crate::ResponsesApiTool;
use std::future::Future;
use std::pin::Pin;

#[derive(Debug, thiserror::Error)]
pub enum ToolError {
    #[error("unknown tool: {0}")]
    Unknown(String),
    #[error("{0}")]
    Failed(String),
}

pub type ToolFuture<'a> = Pin<Box<dyn Future<Output = Result<String, ToolError>> + Send + 'a>>;

/// One callable tool.
///
/// `arguments` is the raw JSON the model produced; a tool that takes none ignores it.
pub trait Tool: Send + Sync {
    fn spec(&self) -> ResponsesApiTool;

    fn execute<'a>(&'a self, arguments: &'a str) -> ToolFuture<'a>;

    /// Whether this tool can run alongside another call of the same answer.
    ///
    /// A tool that only reads may share the conversation; anything that touches state the
    /// others depend on (a shell, a session lock) declares itself exclusive, which queues it
    /// behind the calls it could disturb. Conservative by default: a tool that says nothing
    /// runs alone.
    fn supports_parallel(&self) -> bool {
        false
    }
}
