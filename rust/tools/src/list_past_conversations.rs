use crate::JsonSchema;
use crate::ResponsesApiTool;
use crate::Tool;
use crate::ToolFuture;
use std::collections::BTreeMap;
use std::sync::Arc;

/// Most recent conversations worth listing before the answer stops being useful.
const MAX_CONVERSATIONS: usize = 20;

/// One earlier conversation, as the list needs it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ConversationSummary {
    pub title: String,
    /// Last update, already formatted in the device's zone.
    pub updated_at: String,
}

/// Host integration boundary for reading the conversation list.
pub trait ConversationTitles: Send + Sync {
    fn conversations(&self) -> Vec<ConversationSummary>;
}

/// Lists the user's earlier conversations in this app, newest first.
///
/// Titles only — never transcripts: the model needs to know *that* something was discussed
/// and when, and dumping old messages would blow up the context for no gain. The host
/// returns a point-in-time snapshot rather than a live collection, because a tool answer is
/// a fact about one moment.
pub struct ListPastConversationsTool {
    titles: Arc<dyn ConversationTitles>,
}

impl ListPastConversationsTool {
    pub fn new(titles: Arc<dyn ConversationTitles>) -> Self {
        Self { titles }
    }
}

impl Tool for ListPastConversationsTool {
    fn spec(&self) -> ResponsesApiTool {
        ResponsesApiTool {
            name: "list_past_conversations".to_string(),
            description: "List the user's earlier conversations in this app, newest first, \
                          with their titles and last-updated times. Use it when the user \
                          refers to something discussed before, or asks what they have \
                          talked about."
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
        Box::pin(async move {
            let conversations = self.titles.conversations();
            if conversations.is_empty() {
                return Ok("The user has no earlier conversations.".to_string());
            }
            Ok(conversations
                .iter()
                .take(MAX_CONVERSATIONS)
                .map(|conversation| {
                    format!("- {} — {}", conversation.updated_at, conversation.title)
                })
                .collect::<Vec<_>>()
                .join("\n"))
        })
    }
}

#[cfg(test)]
mod tests {
    #![allow(clippy::unwrap_used, clippy::expect_used)]
    use super::ConversationSummary;
    use super::ConversationTitles;
    use super::ListPastConversationsTool;
    use crate::Tool;
    use std::sync::Arc;

    struct Titles(Vec<ConversationSummary>);

    impl ConversationTitles for Titles {
        fn conversations(&self) -> Vec<ConversationSummary> {
            self.0.clone()
        }
    }

    fn tool(summaries: Vec<ConversationSummary>) -> ListPastConversationsTool {
        ListPastConversationsTool::new(Arc::new(Titles(summaries)))
    }

    #[test]
    fn lists_what_the_host_reports_with_its_own_timestamps() {
        let answer = futures::executor::block_on(
            tool(vec![ConversationSummary {
                title: "yesterday".to_string(),
                updated_at: "2026-09-26 21:04".to_string(),
            }])
            .execute("{}"),
        )
        .expect("execute");

        assert_eq!(answer, "- 2026-09-26 21:04 — yesterday");
    }

    #[test]
    fn says_so_when_there_is_nothing_to_list() {
        let answer = futures::executor::block_on(tool(Vec::new()).execute("{}")).expect("execute");
        assert_eq!(answer, "The user has no earlier conversations.");
    }
}
