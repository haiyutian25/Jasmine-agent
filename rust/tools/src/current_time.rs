use crate::JsonSchema;
use crate::ResponsesApiTool;
use crate::Tool;
use crate::ToolFuture;
use std::collections::BTreeMap;
use std::sync::Arc;

/// Host integration boundary for reading the device's clock.
///
/// The time zone is a platform fact, so the host hands back the formatted answer.
pub trait Clock: Send + Sync {
    fn now_formatted(&self) -> String;
}

/// Reports the device's current date and time.
///
/// A model has no clock: without this it answers date questions from its training cut-off
/// and gets them wrong. The zone is part of the answer because "today" is zone-dependent.
pub struct CurrentTimeTool {
    clock: Arc<dyn Clock>,
}

impl CurrentTimeTool {
    pub fn new(clock: Arc<dyn Clock>) -> Self {
        Self { clock }
    }
}

impl Tool for CurrentTimeTool {
    fn spec(&self) -> ResponsesApiTool {
        ResponsesApiTool {
            name: "current_time".to_string(),
            description: "Get the current date, time and time zone on the user's device. \
                          Use this whenever the answer depends on what \"now\" is."
                .to_string(),
            strict: false,
            defer_loading: None,
            parameters: JsonSchema::object(
                BTreeMap::new(),
                /*required*/ None,
                /*additional_properties*/ None,
            ),
            output_schema: None,
        }
    }

    fn execute<'a>(&'a self, _arguments: &'a str) -> ToolFuture<'a> {
        Box::pin(async move { Ok(self.clock.now_formatted()) })
    }
}

#[cfg(test)]
mod tests {
    #![allow(clippy::unwrap_used, clippy::expect_used)]
    use super::Clock;
    use super::CurrentTimeTool;
    use crate::Tool;
    use std::sync::Arc;

    struct FixedClock;

    impl Clock for FixedClock {
        fn now_formatted(&self) -> String {
            "2026-09-27 10:31:05 GMT+08:00".to_string()
        }
    }

    #[test]
    fn answers_with_what_the_host_reports() {
        let tool = CurrentTimeTool::new(Arc::new(FixedClock));
        let answer = futures::executor::block_on(tool.execute("{}")).expect("execute");
        assert_eq!(answer, "2026-09-27 10:31:05 GMT+08:00");
    }
}
