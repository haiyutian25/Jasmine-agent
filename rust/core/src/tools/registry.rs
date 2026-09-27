use jasmine_tools::ResponsesApiTool;
use jasmine_tools::Tool;
use jasmine_tools::ToolError;
use jasmine_tools::ToolFuture;
use std::sync::Arc;

/// The tools on offer for one conversation.
#[derive(Default)]
pub struct ToolRegistry {
    tools: Vec<Arc<dyn Tool>>,
}

impl ToolRegistry {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn add<T: Tool + 'static>(&mut self, tool: T) -> &mut Self {
        self.tools.push(Arc::new(tool));
        self
    }

    pub fn is_empty(&self) -> bool {
        self.tools.is_empty()
    }

    /// Declarations to put in a sampling request.
    pub fn specs(&self) -> Vec<ResponsesApiTool> {
        self.tools.iter().map(|tool| tool.spec()).collect()
    }

    pub fn contains(&self, name: &str) -> bool {
        self.tools.iter().any(|tool| tool.spec().name == name)
    }

    pub fn execute<'a>(&'a self, name: &'a str, arguments: &'a str) -> ToolFuture<'a> {
        match self.tools.iter().find(|tool| tool.spec().name == name) {
            Some(tool) => tool.execute(arguments),
            None => Box::pin(async move { Err(ToolError::Unknown(name.to_string())) }),
        }
    }
}
