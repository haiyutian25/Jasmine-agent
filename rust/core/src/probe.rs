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
use jasmine_protocol::models::ContentItem;
use jasmine_protocol::models::ResponseItem;
use jasmine_protocol::probe::EMPTY_REPLY_DETAIL;
use jasmine_protocol::probe::PROBE_PROMPT;

/// Sends one minimal request and returns the reply the endpoint gave.
///
/// 与 [`crate::models::list_models`] 同一种形状：失败只回一句能显示的原因（HTTP 状态、传输错误、
/// 供应商原话），由边界把它归到"这一次往返"那一类。**答了但没带内容**（[`EMPTY_REPLY_DETAIL`]）
/// 也算失败 —— 那同样值得重试一次。
pub fn probe(provider: &ResolvedProvider, model_id: &str) -> Result<String, String> {
    let reply = crate::runtime::shared()?.block_on(ask(provider, model_id))?;
    if reply.trim().is_empty() {
        return Err(EMPTY_REPLY_DETAIL.to_string());
    }
    Ok(reply)
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
