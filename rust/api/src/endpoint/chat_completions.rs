use crate::auth::SharedAuthProvider;
use crate::common::ResponseStream;
use crate::endpoint::session::EndpointSession;
use crate::error::ApiError;
use crate::provider::Provider;
use crate::requests::headers::build_session_headers;
use crate::requests::headers::insert_header;
use crate::sse::spawn_chat_stream;
use crate::telemetry::SseTelemetry;
use http::HeaderMap;
use http::HeaderValue;
use http::Method;
use jasmine_client::EncodedJsonBody;
use jasmine_client::HttpTransport;
use jasmine_client::RequestTelemetry;
use serde::Deserialize;
use serde::Serialize;
use serde_json::Value;
use std::sync::Arc;
use tracing::instrument;

// ── request side ──────────────────────────────────────────────────────────

#[derive(Debug, Clone, Serialize)]
pub struct ChatRequest {
    pub model: String,
    pub messages: Vec<ChatMessage>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub tools: Option<Vec<ChatTool>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub temperature: Option<f32>,
    /// Declines parallel tool calls in a single turn: this app asks the user one question at a
    /// time, so a model that batches two interactive calls leaves one of them unanswerable.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub parallel_tool_calls: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub top_p: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub max_tokens: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub stop: Option<Vec<String>>,
    pub stream: bool,
}

/// Request-side message: `role` is required because this shape is only ever sent.
///
/// Responses deliberately do not reuse it — see [`ChatDelta`].
#[derive(Debug, Clone, Serialize)]
pub struct ChatMessage {
    pub role: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub content: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub tool_calls: Option<Vec<ChatToolCallRequest>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub tool_call_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
}

/// `type` carries no default on purpose: it is always serialized, and OpenAI rejects a
/// tool declaration that omits it.
#[derive(Debug, Clone, Serialize)]
pub struct ChatTool {
    pub r#type: String,
    pub function: ChatToolFunction,
}

#[derive(Debug, Clone, Serialize)]
pub struct ChatToolFunction {
    pub name: String,
    pub description: String,
    pub parameters: Value,
}

#[derive(Debug, Clone, Serialize)]
pub struct ChatToolCallRequest {
    pub id: String,
    pub r#type: String,
    pub function: ChatToolCallFunction,
}

#[derive(Debug, Clone, Serialize)]
pub struct ChatToolCallFunction {
    pub name: String,
    pub arguments: String,
}

// ── response side ─────────────────────────────────────────────────────────

#[derive(Debug, Deserialize)]
pub struct ChatResponse {
    pub id: Option<String>,
    #[serde(default)]
    pub choices: Vec<ChatChoice>,
    pub usage: Option<ChatUsage>,
    pub error: Option<ChatError>,
}

#[derive(Debug, Deserialize)]
pub struct ChatChoice {
    pub message: Option<ChatDelta>,
    pub delta: Option<ChatDelta>,
    #[serde(rename = "finish_reason")]
    pub finish_reason: Option<String>,
}

/// Response-side message or streaming frame: `role` must be optional here.
///
/// It is a distinct type rather than a reuse of [`ChatMessage`] because a stream sends
/// `role` only on the first frame; requiring it would make every later frame fail to parse.
#[derive(Debug, Clone, Deserialize)]
pub struct ChatDelta {
    pub role: Option<String>,
    pub content: Option<String>,
    #[serde(default)]
    pub tool_calls: Option<Vec<ChatToolCall>>,
}

/// Streaming side of a tool call.
///
/// A stream repeats `index` on every frame of the same call — the only thing that makes
/// reassembly possible, since the frames carry no other clue that they belong together.
#[derive(Debug, Clone, Deserialize)]
pub struct ChatToolCall {
    pub index: Option<usize>,
    pub id: Option<String>,
    #[serde(rename = "type")]
    pub call_type: Option<String>,
    pub function: Option<ChatToolCallFunctionDelta>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ChatToolCallFunctionDelta {
    pub name: Option<String>,
    pub arguments: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct ChatUsage {
    #[serde(rename = "prompt_tokens")]
    pub prompt_tokens: Option<i64>,
    #[serde(rename = "completion_tokens")]
    pub completion_tokens: Option<i64>,
    #[serde(rename = "total_tokens")]
    pub total_tokens: Option<i64>,
}

#[derive(Debug, Deserialize)]
pub struct ChatError {
    pub message: Option<String>,
}

// ── client ────────────────────────────────────────────────────────────────

pub struct ChatCompletionsClient<T: HttpTransport> {
    session: EndpointSession<T>,
    sse_telemetry: Option<Arc<dyn SseTelemetry>>,
}

#[derive(Default)]
pub struct ChatCompletionsOptions {
    pub session_id: Option<String>,
    pub thread_id: Option<String>,
    pub extra_headers: HeaderMap,
}

impl<T: HttpTransport> ChatCompletionsClient<T> {
    pub fn new(transport: T, provider: Provider, auth: SharedAuthProvider) -> Self {
        Self {
            session: EndpointSession::new(transport, provider, auth),
            sse_telemetry: None,
        }
    }

    pub fn with_telemetry(
        self,
        request: Option<Arc<dyn RequestTelemetry>>,
        sse: Option<Arc<dyn SseTelemetry>>,
    ) -> Self {
        Self {
            session: self.session.with_request_telemetry(request),
            sse_telemetry: sse,
        }
    }

    #[instrument(
        name = "chat_completions.stream_request",
        level = "info",
        skip_all,
        fields(
            transport = "chat_completions_http",
            http.method = "POST",
            api.path = "chat/completions"
        )
    )]
    pub async fn stream_request(
        &self,
        request: ChatRequest,
        options: ChatCompletionsOptions,
    ) -> Result<ResponseStream, ApiError> {
        let ChatCompletionsOptions {
            session_id,
            thread_id,
            extra_headers,
        } = options;
        let body = EncodedJsonBody::encode(&request)
            .map_err(|e| ApiError::Stream(format!("failed to encode chat request: {e}")))?;

        let mut headers = extra_headers;
        if let Some(ref thread_id) = thread_id {
            insert_header(&mut headers, "x-client-request-id", thread_id);
        }
        headers.extend(build_session_headers(session_id, thread_id));

        let stream_response = self
            .session
            .stream_encoded_json_with(
                Method::POST,
                "chat/completions",
                headers,
                Some(body),
                |req| {
                    req.headers.insert(
                        http::header::ACCEPT,
                        HeaderValue::from_static("text/event-stream"),
                    );
                },
            )
            .await?;

        Ok(spawn_chat_stream(
            stream_response,
            self.session.provider().stream_idle_timeout,
            self.sse_telemetry.clone(),
        ))
    }
}
