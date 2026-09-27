use jasmine_api::ResponseEvent;
use jasmine_protocol::ChatEvent;
use jasmine_protocol::models::ResponseItem;
use jasmine_protocol::protocol::TokenUsage;
use serde_json::Value;

/// ADK's two long-running tools: they stop the turn and wait for the user.
pub const REQUEST_INPUT_TOOL: &str = "adk_request_input";
pub const GET_USER_CHOICE_TOOL: &str = "get_user_choice";

const MESSAGE_ARG: &str = "message";
const OPTIONS_ARG: &str = "options";

/// One call that stops the turn, remembered until the user answers it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PendingPrompt {
    pub name: String,
    pub id: String,
}

/// What one model event produced: events for the UI, plus the prompt it asked for.
#[derive(Debug, Default)]
pub struct MappedEvent {
    pub events: Vec<ChatEvent>,
    pub prompt: Option<PendingPrompt>,
    /// What the response cost, when it reported that. Usage is metadata about the response
    /// rather than something the UI is told, so it rides here instead of on an event.
    pub token_usage: Option<TokenUsage>,
}

/// Turns a long-running call into the prompt it represents, or `None` for an ordinary tool.
///
/// Both shapes are fixed by the tool contract, so the argument names are read here rather
/// than modelled in the public event.
fn as_user_prompt(name: &str, arguments: &str) -> Option<ChatEvent> {
    let args: Value = serde_json::from_str(arguments).ok()?;
    match name {
        REQUEST_INPUT_TOOL => Some(ChatEvent::UserPromptRequested {
            prompt: args
                .get(MESSAGE_ARG)
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string(),
            options: Vec::new(),
        }),
        GET_USER_CHOICE_TOOL => Some(ChatEvent::UserPromptRequested {
            prompt: String::new(),
            options: args
                .get(OPTIONS_ARG)
                .and_then(Value::as_array)
                .map(|options| {
                    options
                        .iter()
                        .filter_map(|option| option.as_str().map(str::to_string))
                        .collect()
                })
                .unwrap_or_default(),
        }),
        _ => None,
    }
}

/// Maps one model event to what the UI sees.
///
/// Text deltas are forwarded as they arrive. Tool activity is only reported from settled
/// items: a streamed function call carries incomplete arguments, and acting on those would
/// surface half-built calls.
pub fn map_response_event(event: ResponseEvent) -> MappedEvent {
    match event {
        ResponseEvent::OutputTextDelta(text) => {
            if text.is_empty() {
                return MappedEvent::default();
            }
            MappedEvent {
                events: vec![ChatEvent::Text(text)],
                prompt: None,
                token_usage: None,
            }
        }
        ResponseEvent::OutputItemDone(ResponseItem::FunctionCall {
            name,
            arguments,
            call_id,
            ..
        }) => {
            let call = ChatEvent::tool_call(name.clone(), &arguments);
            match as_user_prompt(&name, &arguments) {
                Some(prompt) => MappedEvent {
                    events: vec![call, prompt],
                    prompt: Some(PendingPrompt { name, id: call_id }),
                    token_usage: None,
                },
                None => MappedEvent {
                    events: vec![call],
                    prompt: None,
                    token_usage: None,
                },
            }
        }
        ResponseEvent::Completed { token_usage, .. } => MappedEvent {
            events: vec![ChatEvent::Completed],
            prompt: None,
            token_usage,
        },
        ResponseEvent::Created { .. }
        | ResponseEvent::OutputItemAdded(_)
        | ResponseEvent::OutputItemDone(_)
        | ResponseEvent::ServerModel(_)
        | ResponseEvent::ToolCallInputDelta { .. }
        | ResponseEvent::ReasoningSummaryDelta { .. }
        | ResponseEvent::ReasoningSummaryDone { .. }
        | ResponseEvent::ReasoningContentDelta { .. } => MappedEvent::default(),
    }
}
