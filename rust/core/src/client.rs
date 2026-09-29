//! Streaming model client: turns one sampling request into a stream of events, over
//! whichever wire protocol the provider speaks.

use jasmine_api::ApiError;
use jasmine_api::ChatCompletionsClient;
use jasmine_api::ChatCompletionsOptions;
use jasmine_api::ChatMessage;
use jasmine_api::ChatRequest;
use jasmine_api::ChatTool;
use jasmine_api::ChatToolCallFunction;
use jasmine_api::ChatToolCallRequest;
use jasmine_api::ChatToolFunction;
use jasmine_api::Reasoning;
use jasmine_api::ResponseStream;
use jasmine_api::ResponsesApiRequest;
use jasmine_api::ResponsesApiTools;
use jasmine_api::ResponsesClient;
use jasmine_api::ResponsesOptions;
use jasmine_client::HttpTransport;
use jasmine_protocol::config_types::ReasoningSummary;
use jasmine_protocol::models::ContentItem;
use jasmine_protocol::models::ReasoningItemContent;
use jasmine_protocol::models::ResponseItem;
use jasmine_protocol::openai_models::ReasoningEffort;
use jasmine_tools::ResponsesApiTool;
use serde_json::Value;
use serde_json::value::RawValue;
use std::sync::Arc;

/// Which request/response shape the provider answers.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WireProtocol {
    ChatCompletions,
    Responses,
}

/// One sampling request: the conversation so far, the instruction, and the tools on offer.
#[derive(Debug, Clone)]
pub struct SamplingRequest {
    /// System instruction; `None` leaves the provider's default in place.
    pub instruction: Option<String>,
    pub input: Vec<ResponseItem>,
    pub tools: Vec<ResponsesApiTool>,
    pub stream: bool,
}

/// Streams one request over the configured protocol.
pub struct ModelClient<T: HttpTransport> {
    model_id: String,
    /// The most the model may write in one response, as the platform configured it. `None` leaves
    /// the provider's own default in place, which is what a platform that never set a length sends.
    max_output_tokens: Option<u32>,
    /// How hard the model should think before it answers, as the platform configured it. `None`
    /// sends no reasoning field at all — the provider's own default, and what a model the platform
    /// never gave an effort to gets.
    reasoning_effort: Option<ReasoningEffort>,
    backend: Backend<T>,
}

enum Backend<T: HttpTransport> {
    Responses(ResponsesClient<T>),
    ChatCompletions(ChatCompletionsClient<T>),
}

impl<T: HttpTransport> ModelClient<T> {
    pub fn responses(client: ResponsesClient<T>, model_id: impl Into<String>) -> Self {
        Self {
            model_id: model_id.into(),
            max_output_tokens: None,
            reasoning_effort: None,
            backend: Backend::Responses(client),
        }
    }

    pub fn chat_completions(client: ChatCompletionsClient<T>, model_id: impl Into<String>) -> Self {
        Self {
            model_id: model_id.into(),
            max_output_tokens: None,
            reasoning_effort: None,
            backend: Backend::ChatCompletions(client),
        }
    }

    /// Caps how much the model may write per response, as the platform configured it.
    pub fn with_max_output_tokens(mut self, max_output_tokens: Option<u32>) -> Self {
        self.max_output_tokens = max_output_tokens;
        self
    }

    /// Sets how hard the model should think before answering, as the platform configured it.
    ///
    /// It rides on both wires under each protocol's own name, and on the Responses wire it also asks
    /// for a summary of that thinking: without one the provider hands back nothing readable.
    pub fn with_reasoning_effort(mut self, reasoning_effort: Option<ReasoningEffort>) -> Self {
        self.reasoning_effort = reasoning_effort;
        self
    }

    pub fn model_id(&self) -> &str {
        &self.model_id
    }

    pub fn protocol(&self) -> WireProtocol {
        match self.backend {
            Backend::Responses(_) => WireProtocol::Responses,
            Backend::ChatCompletions(_) => WireProtocol::ChatCompletions,
        }
    }

    pub async fn stream(&self, request: SamplingRequest) -> Result<ResponseStream, ApiError> {
        match &self.backend {
            Backend::Responses(client) => {
                client
                    .stream_request(
                        responses_request(
                            &self.model_id,
                            &request,
                            self.max_output_tokens,
                            self.reasoning_effort.clone(),
                        )?,
                        ResponsesOptions::default(),
                    )
                    .await
            }
            Backend::ChatCompletions(client) => {
                client
                    .stream_request(
                        chat_request(
                            &self.model_id,
                            &request,
                            self.max_output_tokens,
                            self.reasoning_effort.clone(),
                        )?,
                        ChatCompletionsOptions::default(),
                    )
                    .await
            }
        }
    }
}

/// The Responses API takes the item list as-is; function tools are flattened alongside it.
///
/// The output cap goes on the wire as `max_output_tokens` — the name that protocol uses.
///
/// The reasoning effort goes on the wire inside the protocol's own `reasoning` object, and it asks
/// for a summary of the thinking alongside it: that protocol hands back nothing readable otherwise.
fn responses_request(
    model_id: &str,
    request: &SamplingRequest,
    max_output_tokens: Option<u32>,
    reasoning_effort: Option<ReasoningEffort>,
) -> Result<ResponsesApiRequest, ApiError> {
    let tools = if request.tools.is_empty() {
        None
    } else {
        let encoded = serde_json::to_string(&tool_payload(request.tools.as_slice())?)
            .map_err(|e| ApiError::Stream(format!("failed to encode tools: {e}")))?;
        let raw = RawValue::from_string(encoded)
            .map_err(|e| ApiError::Stream(format!("failed to encode tools: {e}")))?;
        Some(ResponsesApiTools::from(Arc::<RawValue>::from(raw)))
    };

    Ok(ResponsesApiRequest {
        model: model_id.to_string(),
        instructions: request.instruction.clone().unwrap_or_default(),
        input: request.input.clone(),
        tools,
        tool_choice: "auto".to_string(),
        parallel_tool_calls: false,
        reasoning: reasoning_effort.map(|effort| Reasoning {
            effort: Some(effort),
            summary: Some(ReasoningSummary::Auto),
            context: None,
        }),
        store: false,
        stream: request.stream,
        stream_options: None,
        include: vec!["reasoning.encrypted_content".to_string()],
        service_tier: None,
        prompt_cache_key: None,
        text: None,
        max_output_tokens,
    })
}

fn tool_payload(tools: &[ResponsesApiTool]) -> Result<Vec<Value>, ApiError> {
    tools.iter().map(tool_declaration).collect()
}

/// One tool as it goes on the wire.
///
/// Shared with the context-usage accounting, so what that counts is the declaration that is
/// actually sent rather than a lookalike.
pub(crate) fn tool_declaration(tool: &ResponsesApiTool) -> Result<Value, ApiError> {
    Ok(serde_json::json!({
        "type": "function",
        "name": tool.name,
        "description": tool.description,
        "parameters": tool_parameters(tool)?,
    }))
}

fn tool_parameters(tool: &ResponsesApiTool) -> Result<Value, ApiError> {
    serde_json::to_value(&tool.parameters)
        .map_err(|e| ApiError::Stream(format!("failed to encode tool parameters: {e}")))
}

/// Chat Completions needs the item list translated into role-tagged messages: an assistant
/// turn's tool calls ride on its own message, and every tool result is a separate
/// `role: "tool"` message keyed by the call id that asked for it.
///
/// The output cap goes on the wire as `max_tokens` — the name this protocol's OpenAI-compatible
/// providers take.
fn chat_request(
    model_id: &str,
    request: &SamplingRequest,
    max_output_tokens: Option<u32>,
    reasoning_effort: Option<ReasoningEffort>,
) -> Result<ChatRequest, ApiError> {
    let mut messages = Vec::new();
    let instruction = request
        .instruction
        .as_deref()
        .map(str::trim)
        .filter(|text| !text.is_empty());
    if let Some(instruction) = instruction {
        messages.push(ChatMessage {
            role: "system".to_string(),
            content: Some(instruction.to_string()),
            reasoning_content: None,
            tool_calls: None,
            tool_call_id: None,
            name: None,
        });
    }
    // The items of one answer are translated in the order they were written, folding back the two
    // things this protocol wants together and the Responses protocol keeps apart: every call of an
    // answer rides on **one** assistant message, with that answer's thinking on it. Left split — one
    // message per call, thinking dropped — the provider refuses the request ("an assistant message
    // with 'tool_calls' must be followed by tool messages responding to each 'tool_call_id'").
    let mut reasoning: Option<String> = None;
    for item in &request.input {
        match item {
            ResponseItem::Reasoning { content, .. } => reasoning = reasoning_text(content),
            _ => {
                for message in chat_messages(item) {
                    push_chat_message(&mut messages, message, &mut reasoning);
                }
            }
        }
    }

    let tools: Vec<ChatTool> = request
        .tools
        .iter()
        .map(|tool| {
            Ok(ChatTool {
                r#type: "function".to_string(),
                function: ChatToolFunction {
                    name: tool.name.clone(),
                    description: tool.description.clone(),
                    parameters: tool_parameters(tool)?,
                },
            })
        })
        .collect::<Result<Vec<_>, ApiError>>()?;
    let tools = (!tools.is_empty()).then_some(tools);
    // Only declared when tools are on offer: it is meaningless otherwise, and some gateways
    // reject the parameter outright.
    let parallel_tool_calls = tools.as_ref().map(|_| false);

    Ok(ChatRequest {
        model: model_id.to_string(),
        messages,
        tools,
        temperature: None,
        parallel_tool_calls,
        top_p: None,
        max_tokens: max_output_tokens,
        // OpenAI-compatible endpoints spell the effort just like this.
        reasoning_effort: reasoning_effort.map(|effort| effort.as_str().to_string()),
        stop: None,
        stream: request.stream,
    })
}

/// Appends one translated message, folding an answer's parts back together.
///
/// The Responses API keeps every call of an answer in an item of its own and its thinking in one
/// more; chat completions wants the calls gathered onto **one** assistant message, with the thinking
/// of that answer on it. Only a call message that directly follows another call message is folded —
/// the text the model wrote before calling, or a new turn, starts a message of its own.
fn push_chat_message(
    messages: &mut Vec<ChatMessage>,
    mut message: ChatMessage,
    reasoning: &mut Option<String>,
) {
    // The thinking of an answer rides back on the **first** assistant message that follows it —
    // whether that is the text it wrote or the calls it made. Every assistant turn of a thinking
    // model has to carry its own reasoning back: leaving one without it is refused with "the
    // `reasoning_content` in the thinking mode must be passed back to the API".
    if message.role == "assistant" {
        message.reasoning_content = reasoning.take();
    }

    let Some(calls) = message.tool_calls.clone() else {
        messages.push(message);
        return;
    };
    if let Some(previous) = messages.last_mut()
        && previous.role == "assistant"
        && previous.content.is_none()
        && let Some(previous_calls) = previous.tool_calls.as_mut()
    {
        previous_calls.extend(calls);
        if previous.reasoning_content.is_none() {
            previous.reasoning_content = message.reasoning_content;
        }
        return;
    }
    messages.push(message);
}

/// The text of a reasoning item, when it says anything.
fn reasoning_text(content: &Option<Vec<ReasoningItemContent>>) -> Option<String> {
    let text = content
        .iter()
        .flatten()
        .map(|item| match item {
            ReasoningItemContent::ReasoningText { text } | ReasoningItemContent::Text { text } => {
                text.as_str()
            }
        })
        .collect::<String>();
    (!text.trim().is_empty()).then_some(text)
}

/// Maps one item to the messages it becomes. A tool result is its own message, so an item
/// that carries only a result yields exactly one.
fn chat_messages(item: &ResponseItem) -> Vec<ChatMessage> {
    match item {
        ResponseItem::Message { role, content, .. } => {
            let text = content
                .iter()
                .filter_map(|content| match content {
                    ContentItem::InputText { text } | ContentItem::OutputText { text } => {
                        Some(text.as_str())
                    }
                    ContentItem::InputImage { .. } | ContentItem::InputAudio { .. } => None,
                })
                .collect::<String>();
            if text.is_empty() {
                return Vec::new();
            }
            vec![ChatMessage {
                role: role.clone(),
                content: Some(text),
                reasoning_content: None,
                tool_calls: None,
                tool_call_id: None,
                name: None,
            }]
        }
        ResponseItem::FunctionCall {
            name,
            arguments,
            call_id,
            ..
        } => vec![ChatMessage {
            role: "assistant".to_string(),
            content: None,
            reasoning_content: None,
            tool_calls: Some(vec![ChatToolCallRequest {
                id: call_id.clone(),
                r#type: "function".to_string(),
                function: ChatToolCallFunction {
                    name: name.clone(),
                    arguments: arguments.clone(),
                },
            }]),
            tool_call_id: None,
            name: None,
        }],
        ResponseItem::FunctionCallOutput {
            call_id, output, ..
        } => vec![ChatMessage {
            role: "tool".to_string(),
            content: Some(
                output
                    .text_content()
                    .map(str::to_string)
                    .unwrap_or_default(),
            ),
            reasoning_content: None,
            tool_calls: None,
            tool_call_id: call_id.clone(),
            name: None,
        }],
        ResponseItem::Reasoning { .. } | ResponseItem::Other => Vec::new(),
    }
}

#[cfg(test)]
mod tests {
    use super::SamplingRequest;
    use super::chat_request;
    use super::responses_request;
    use jasmine_protocol::models::ContentItem;
    use jasmine_protocol::models::FunctionCallOutputPayload;
    use jasmine_protocol::models::ReasoningItemContent;
    use jasmine_protocol::models::ResponseItem;

    fn request(input: Vec<ResponseItem>) -> SamplingRequest {
        SamplingRequest {
            instruction: None,
            input,
            tools: Vec::new(),
            stream: true,
        }
    }

    fn message(role: &str, text: &str) -> ResponseItem {
        ResponseItem::Message {
            id: None,
            role: role.to_string(),
            content: vec![ContentItem::InputText {
                text: text.to_string(),
            }],
        }
    }

    fn thinking(text: &str) -> ResponseItem {
        ResponseItem::Reasoning {
            id: None,
            summary: Vec::new(),
            content: Some(vec![ReasoningItemContent::ReasoningText {
                text: text.to_string(),
            }]),
            encrypted_content: None,
        }
    }

    fn call(name: &str, call_id: &str) -> ResponseItem {
        ResponseItem::FunctionCall {
            id: None,
            name: name.to_string(),
            namespace: None,
            arguments: "{}".to_string(),
            encrypted_function_args: None,
            call_id: call_id.to_string(),
        }
    }

    fn output(call_id: &str, text: &str) -> ResponseItem {
        ResponseItem::FunctionCallOutput {
            id: None,
            call_id: Some(call_id.to_string()),
            name: None,
            namespace: None,
            output: FunctionCallOutputPayload::from_text(text.to_string()),
        }
    }

    /// The shape a thinking model's turn has to take on this wire.
    ///
    /// Every failure this guards against was measured against the provider: one assistant message
    /// per call is refused with "an assistant message with 'tool_calls' must be followed by tool
    /// messages responding to each 'tool_call_id'", and a turn whose thinking is missing is refused
    /// with "the `reasoning_content` in the thinking mode must be passed back to the API" — the
    /// latter bites the **plain** answers too, not only the turn that called tools.
    #[test]
    fn every_assistant_turn_carries_its_own_thinking() {
        let request = request(vec![
            // A first turn that only answered: its thinking has to come back as well.
            message("user", "你有哪些工具？"),
            thinking("The user asks what tools I have. I should list them."),
            message("assistant", "我有两个工具。"),
            message("user", "测试你全部的工具"),
            thinking("The user wants every tool, so I call both of them."),
            message("assistant", "我来实际调用这两个工具。"),
            call("current_time", "call-1"),
            call("list_past_conversations", "call-2"),
            output("call-1", "2026-09-28 17:30:15 GMT+08:00"),
            output("call-2", "no earlier conversations"),
        ]);

        let chat = chat_request("m", &request, None, None).expect("a chat request");
        let roles: Vec<&str> = chat
            .messages
            .iter()
            .map(|message| message.role.as_str())
            .collect();
        assert_eq!(
            roles,
            vec![
                "user",
                "assistant",
                "user",
                "assistant",
                "assistant",
                "tool",
                "tool"
            ]
        );

        // Each assistant message carries the thinking of the turn that produced it.
        assert_eq!(
            chat.messages[1].reasoning_content.as_deref(),
            Some("The user asks what tools I have. I should list them.")
        );
        assert_eq!(
            chat.messages[3].reasoning_content.as_deref(),
            Some("The user wants every tool, so I call both of them.")
        );

        // Both calls of that answer ride on one message together.
        let calls = chat.messages[4]
            .tool_calls
            .as_ref()
            .expect("the calls ride together");
        assert_eq!(calls.len(), 2);
        assert_eq!(calls[0].id, "call-1");
        assert_eq!(calls[1].id, "call-2");

        // Each call is answered by its own tool message.
        assert_eq!(chat.messages[5].tool_call_id.as_deref(), Some("call-1"));
        assert_eq!(chat.messages[6].tool_call_id.as_deref(), Some("call-2"));
    }

    /// The platform's output cap rides on every request, under each protocol's own name.
    #[test]
    fn the_output_cap_rides_on_the_request() {
        let request = request(vec![message("user", "在吗")]);

        let chat = chat_request("m", &request, Some(4096), None).expect("a chat request");
        assert_eq!(chat.max_tokens, Some(4096));

        let responses =
            responses_request("m", &request, Some(4096), None).expect("a responses request");
        assert_eq!(responses.max_output_tokens, Some(4096));
    }

    /// A model the platform never gave a cap sends none, leaving the provider's default in place.
    #[test]
    fn no_output_cap_leaves_the_provider_its_default() {
        let request = request(vec![message("user", "在吗")]);

        let chat = chat_request("m", &request, None, None).expect("a chat request");
        assert_eq!(chat.max_tokens, None);

        let responses = responses_request("m", &request, None, None).expect("a responses request");
        assert_eq!(responses.max_output_tokens, None);
    }

    /// 平台给模型配的推理强度，按两种协议各自的字段名上线；没配就两边都不带。
    #[test]
    fn the_reasoning_effort_rides_on_the_request() {
        let request = request(vec![message("user", "在吗")]);

        let chat = chat_request("m", &request, None, Some(super::ReasoningEffort::High))
            .expect("a chat request");
        assert_eq!(chat.reasoning_effort.as_deref(), Some("high"));

        let responses =
            responses_request("m", &request, None, Some(super::ReasoningEffort::Minimal))
                .expect("a responses request");
        let reasoning = responses.reasoning.expect("the reasoning object");
        assert_eq!(reasoning.effort, Some(super::ReasoningEffort::Minimal));
        // 明文思考要先要到摘要，供应商才给。
        assert_eq!(reasoning.summary, Some(super::ReasoningSummary::Auto));

        let chat = chat_request("m", &request, None, None).expect("a chat request");
        assert_eq!(chat.reasoning_effort, None);
        let responses = responses_request("m", &request, None, None).expect("a responses request");
        assert!(responses.reasoning.is_none());
    }
}
