use crate::common::ResponseEvent;
use crate::common::ResponseStream;
use crate::endpoint::chat_completions::ChatResponse;
use crate::endpoint::chat_completions::ChatToolCall;
use crate::endpoint::chat_completions::ChatUsage;
use crate::error::ApiError;
use crate::telemetry::SseTelemetry;
use eventsource_stream::Eventsource;
use futures::StreamExt;
use jasmine_client::ByteStream;
use jasmine_client::StreamResponse;
use jasmine_protocol::models::ResponseItem;
use jasmine_protocol::protocol::TokenUsage;
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::mpsc;
use tokio::time::Instant;
use tokio::time::timeout;
use tracing::debug;
use tracing::trace;

const REQUEST_ID_HEADER: &str = "x-request-id";

pub fn spawn_chat_stream(
    stream_response: StreamResponse,
    idle_timeout: Duration,
    telemetry: Option<Arc<dyn SseTelemetry>>,
) -> ResponseStream {
    let upstream_request_id = stream_response
        .headers
        .get(REQUEST_ID_HEADER)
        .and_then(|value| value.to_str().ok())
        .map(str::to_string);

    let (tx_event, rx_event) = mpsc::channel::<Result<ResponseEvent, ApiError>>(1600);
    tokio::spawn(async move {
        process_sse(stream_response.bytes, tx_event, idle_timeout, telemetry).await;
    });

    ResponseStream {
        rx_event,
        upstream_request_id,
        interrupt: None,
    }
}

/// Reassembles tool calls that the Chat Completions stream splits across frames.
///
/// A single call arrives in pieces: the first frame carries `id` and the function name,
/// later frames append fragments of the `arguments` JSON text. Merging is keyed on the
/// `index` OpenAI stamps on each entry; an entry without one is treated as a complete
/// call, which is the shape gateways that ignore streaming return.
#[derive(Debug, Default)]
struct ToolCallFragments {
    fragments: Vec<Fragment>,
}

#[derive(Debug, Default)]
struct Fragment {
    index: Option<usize>,
    name: Option<String>,
    id: Option<String>,
    arguments: String,
}

impl ToolCallFragments {
    fn add(&mut self, call: ChatToolCall) {
        let position = match call.index {
            Some(index) => self
                .fragments
                .iter()
                .position(|fragment| fragment.index == Some(index))
                .unwrap_or_else(|| {
                    self.fragments.push(Fragment {
                        index: Some(index),
                        ..Fragment::default()
                    });
                    self.fragments.len() - 1
                }),
            None => {
                self.fragments.push(Fragment::default());
                self.fragments.len() - 1
            }
        };
        let fragment = &mut self.fragments[position];
        if let Some(id) = call.id.filter(|id| !id.is_empty()) {
            fragment.id = Some(id);
        }
        if let Some(function) = call.function {
            if let Some(name) = function.name.filter(|name| !name.is_empty()) {
                fragment.name = Some(name);
            }
            if let Some(arguments) = function.arguments {
                fragment.arguments.push_str(&arguments);
            }
        }
    }

    /// Returns complete calls in the order they started; nameless entries are invalid frames.
    fn complete(self) -> Vec<ResponseItem> {
        self.fragments
            .into_iter()
            .filter_map(|fragment| {
                let name = fragment.name?;
                Some(ResponseItem::FunctionCall {
                    id: None,
                    name,
                    namespace: None,
                    arguments: fragment.arguments,
                    encrypted_function_args: None,
                    call_id: fragment.id.unwrap_or_default(),
                })
            })
            .collect()
    }
}

fn to_token_usage(usage: ChatUsage) -> TokenUsage {
    TokenUsage {
        input_tokens: usage.prompt_tokens.unwrap_or_default(),
        cached_input_tokens: 0,
        cache_write_input_tokens: 0,
        output_tokens: usage.completion_tokens.unwrap_or_default(),
        reasoning_output_tokens: 0,
        total_tokens: usage.total_tokens.unwrap_or_default(),
    }
}

async fn process_sse(
    stream: ByteStream,
    tx_event: mpsc::Sender<Result<ResponseEvent, ApiError>>,
    idle_timeout: Duration,
    telemetry: Option<Arc<dyn SseTelemetry>>,
) {
    let mut stream = stream.eventsource();
    let mut tool_calls = ToolCallFragments::default();
    let mut response_error: Option<ApiError> = None;

    loop {
        let start = Instant::now();
        let response = tokio::select! {
            biased;
            _ = tx_event.closed() => return,
            response = timeout(idle_timeout, stream.next()) => response,
        };
        if let Some(t) = telemetry.as_ref() {
            t.on_sse_poll(&response, start.elapsed());
        }
        let sse = match response {
            Ok(Some(Ok(sse))) => sse,
            Ok(Some(Err(e))) => {
                debug!("SSE Error: {e:#}");
                let _ = tx_event.send(Err(ApiError::Stream(e.to_string()))).await;
                return;
            }
            Ok(None) => {
                let error = response_error.unwrap_or(ApiError::Stream(
                    "stream closed before a finish reason was reported".into(),
                ));
                let _ = tx_event.send(Err(error)).await;
                return;
            }
            Err(_) => {
                let _ = tx_event
                    .send(Err(ApiError::Stream("idle timeout waiting for SSE".into())))
                    .await;
                return;
            }
        };

        let payload = sse.data;
        if payload.trim() == "[DONE]" {
            break;
        }
        trace!("SSE event: {payload}");

        let chunk: ChatResponse = match serde_json::from_str(&payload) {
            Ok(chunk) => chunk,
            Err(e) => {
                debug!(
                    error_category = ?e.classify(),
                    error_line = e.line(),
                    error_column = e.column(),
                    payload_bytes = payload.len(),
                    "Failed to parse SSE event"
                );
                continue;
            }
        };

        if let Some(message) = chunk.error.and_then(|error| error.message) {
            response_error = Some(ApiError::Stream(message));
            continue;
        }

        let Some(choice) = chunk.choices.into_iter().next() else {
            continue;
        };

        if let Some(delta) = choice.delta.as_ref().or(choice.message.as_ref())
            && let Some(calls) = delta.tool_calls.as_ref()
        {
            for call in calls {
                tool_calls.add(call.clone());
            }
        }

        if let Some(text) = choice
            .delta
            .as_ref()
            .or(choice.message.as_ref())
            .and_then(|delta| delta.content.clone())
            && !text.is_empty()
            && tx_event
                .send(Ok(ResponseEvent::OutputTextDelta(text)))
                .await
                .is_err()
        {
            return;
        }

        if choice.finish_reason.is_none() {
            continue;
        }

        let end_turn = choice.finish_reason.as_deref() == Some("stop");
        let response_id = chunk.id.unwrap_or_default();
        let token_usage = chunk.usage.map(to_token_usage);
        for item in std::mem::take(&mut tool_calls).complete() {
            if tx_event
                .send(Ok(ResponseEvent::OutputItemDone(item)))
                .await
                .is_err()
            {
                return;
            }
        }
        let _ = tx_event
            .send(Ok(ResponseEvent::Completed {
                response_id,
                token_usage,
                usage_metadata: None,
                end_turn: Some(end_turn),
            }))
            .await;
        return;
    }

    // `[DONE]` arrived without a finish reason: report what was reassembled and close.
    for item in tool_calls.complete() {
        if tx_event
            .send(Ok(ResponseEvent::OutputItemDone(item)))
            .await
            .is_err()
        {
            return;
        }
    }
    let _ = tx_event
        .send(Ok(ResponseEvent::Completed {
            response_id: String::new(),
            token_usage: None,
            usage_metadata: None,
            end_turn: None,
        }))
        .await;
}

#[cfg(test)]
mod tests {
    use super::to_token_usage;
    use crate::endpoint::chat_completions::ChatUsage;

    #[test]
    fn maps_the_reported_counts_onto_the_shared_usage_type() {
        let usage = to_token_usage(ChatUsage {
            prompt_tokens: Some(10),
            completion_tokens: Some(4),
            total_tokens: Some(14),
        });

        assert_eq!(usage.input_tokens, 10);
        assert_eq!(usage.output_tokens, 4);
        assert_eq!(usage.total_tokens, 14);
    }

    #[test]
    fn treats_a_missing_count_as_zero() {
        let usage = to_token_usage(ChatUsage {
            prompt_tokens: None,
            completion_tokens: None,
            total_tokens: Some(14),
        });

        assert_eq!(usage.input_tokens, 0);
        assert_eq!(usage.output_tokens, 0);
        assert_eq!(usage.total_tokens, 14);
    }
}
