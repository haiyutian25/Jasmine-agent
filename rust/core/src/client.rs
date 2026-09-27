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
use jasmine_api::ResponseStream;
use jasmine_api::ResponsesApiRequest;
use jasmine_api::ResponsesApiTools;
use jasmine_api::ResponsesClient;
use jasmine_api::ResponsesOptions;
use jasmine_client::HttpTransport;
use jasmine_protocol::models::ContentItem;
use jasmine_protocol::models::ResponseItem;
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
            backend: Backend::Responses(client),
        }
    }

    pub fn chat_completions(client: ChatCompletionsClient<T>, model_id: impl Into<String>) -> Self {
        Self {
            model_id: model_id.into(),
            backend: Backend::ChatCompletions(client),
        }
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
                        responses_request(&self.model_id, &request)?,
                        ResponsesOptions::default(),
                    )
                    .await
            }
            Backend::ChatCompletions(client) => {
                client
                    .stream_request(
                        chat_request(&self.model_id, &request)?,
                        ChatCompletionsOptions::default(),
                    )
                    .await
            }
        }
    }
}

/// The Responses API takes the item list as-is; function tools are flattened alongside it.
fn responses_request(
    model_id: &str,
    request: &SamplingRequest,
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
        reasoning: None,
        store: false,
        stream: request.stream,
        stream_options: None,
        // 让服务端把思考以加密形态交回来：下一轮要原样带回它，这轮才被认作连续。
        include: vec!["reasoning.encrypted_content".to_string()],
        service_tier: None,
        prompt_cache_key: None,
        text: None,
    })
}

fn tool_payload(tools: &[ResponsesApiTool]) -> Result<Vec<Value>, ApiError> {
    tools
        .iter()
        .map(|tool| {
            Ok(serde_json::json!({
                "type": "function",
                "name": tool.name,
                "description": tool.description,
                "parameters": tool_parameters(tool)?,
            }))
        })
        .collect()
}

fn tool_parameters(tool: &ResponsesApiTool) -> Result<Value, ApiError> {
    serde_json::to_value(&tool.parameters)
        .map_err(|e| ApiError::Stream(format!("failed to encode tool parameters: {e}")))
}

/// Chat Completions needs the item list translated into role-tagged messages: an assistant
/// turn's tool calls ride on its own message, and every tool result is a separate
/// `role: "tool"` message keyed by the call id that asked for it.
fn chat_request(model_id: &str, request: &SamplingRequest) -> Result<ChatRequest, ApiError> {
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
            tool_calls: None,
            tool_call_id: None,
            name: None,
        });
    }
    messages.extend(request.input.iter().flat_map(chat_messages));

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
        max_tokens: None,
        stop: None,
        stream: request.stream,
    })
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
            tool_calls: None,
            tool_call_id: call_id.clone(),
            name: None,
        }],
        ResponseItem::Reasoning { .. } | ResponseItem::Other => Vec::new(),
    }
}
