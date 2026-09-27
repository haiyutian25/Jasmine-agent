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
}
