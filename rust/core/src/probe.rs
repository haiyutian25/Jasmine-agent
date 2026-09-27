//! Asking a provider whether it can answer at all.
//!
//! The whole stream is drained and the settled text is read: a stream's first frame can be a
//! role-only delta carrying no text, which is indistinguishable from a silent model.

use crate::client::ModelClient;
use crate::client::SamplingRequest;
use futures::StreamExt;
use jasmine_api::ResponseEvent;
use jasmine_client::ReqwestTransport;
use jasmine_http_client::HttpClientBuilder;
use jasmine_model_provider::ApiClient;
use jasmine_model_provider::ResolvedProvider;
use jasmine_model_provider::create_api_client;
use jasmine_protocol::ProbeResult;
use jasmine_protocol::models::ContentItem;
use jasmine_protocol::models::ResponseItem;
use jasmine_protocol::probe::EMPTY_REPLY_DETAIL;
use jasmine_protocol::probe::PROBE_PROMPT;

/// Sends one minimal request and reports whether a usable answer came back.
///
/// Transport and provider failures come back as [`ProbeResult::Failure`] rather than as an
/// error: the caller shows the reason verbatim and has nothing to do with a typed error.
pub fn probe(provider: &ResolvedProvider, model_id: &str) -> ProbeResult {
    let runtime = match tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
    {
        Ok(runtime) => runtime,
        Err(error) => {
            return ProbeResult::Failure {
                detail: error.to_string(),
            };
        }
    };

    match runtime.block_on(ask(provider, model_id)) {
        Ok(reply) if reply.trim().is_empty() => ProbeResult::Failure {
            detail: EMPTY_REPLY_DETAIL.to_string(),
        },
        Ok(reply) => ProbeResult::Success { reply },
        Err(detail) => ProbeResult::Failure { detail },
    }
}

/// One minimal round trip; the error is already the string the caller shows.
async fn ask(provider: &ResolvedProvider, model_id: &str) -> Result<String, String> {
    let http = HttpClientBuilder::new()
        .without_request_logging()
        .build()
        .map_err(|error| error.to_string())?;
    let request = SamplingRequest {
        instruction: None,
        input: vec![ResponseItem::Message {
            id: None,
            role: "user".to_string(),
            content: vec![ContentItem::InputText {
                text: PROBE_PROMPT.to_string(),
            }],
        }],
        tools: Vec::new(),
        stream: true,
    };

    let transport = ReqwestTransport::from_http_client(http);
    let client = create_api_client(provider, transport);
    let model_client = match client {
        ApiClient::Chat(client) => ModelClient::chat_completions(*client, model_id),
        ApiClient::Responses(client) => ModelClient::responses(*client, model_id),
    };
    let stream = model_client
        .stream(request)
        .await
        .map_err(|error| error.to_string())?;

    let mut reply = String::new();
    let mut stream = stream;
    while let Some(event) = stream.next().await {
        match event {
            Ok(ResponseEvent::OutputTextDelta(text)) => reply.push_str(&text),
            Ok(_) => {}
            Err(error) => return Err(error.to_string()),
        }
    }
    Ok(reply)
}
