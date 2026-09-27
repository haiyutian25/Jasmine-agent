//! A provider with its credential attached, and the client its wire protocol needs.

use crate::bearer_auth_provider::BearerAuthProvider;
use jasmine_api::ChatCompletionsClient;
use jasmine_api::ResponsesClient;
use jasmine_client::HttpTransport;
use jasmine_client::Provider;
use jasmine_model_provider_info::ModelProviderInfo;
use jasmine_model_provider_info::WireApi;
use std::sync::Arc;

/// Provider metadata plus the credential to use it with.
///
/// The metadata can be passed around freely — the interface reads and edits it — while the key
/// only appears where a request is about to be built.
#[derive(Debug, Clone)]
pub struct ResolvedProvider {
    info: ModelProviderInfo,
    api_key: String,
}

impl ResolvedProvider {
    pub fn from_config(info: ModelProviderInfo, api_key: impl Into<String>) -> Self {
        Self {
            info,
            api_key: api_key.into(),
        }
    }

    pub fn info(&self) -> &ModelProviderInfo {
        &self.info
    }

    pub fn api_key(&self) -> &str {
        self.api_key.as_str()
    }

    pub fn wire_api(&self) -> WireApi {
        self.info.wire_api
    }

    /// The transport configuration requests to this provider run on.
    pub fn to_provider(&self) -> Provider {
        self.info.to_provider()
    }
}

/// The client for whichever protocol the provider speaks.
pub enum ApiClient<T: HttpTransport> {
    Chat(Box<ChatCompletionsClient<T>>),
    Responses(Box<ResponsesClient<T>>),
}

impl<T: HttpTransport> ApiClient<T> {
    pub fn wire_api(&self) -> WireApi {
        match self {
            Self::Chat(_) => WireApi::Chat,
            Self::Responses(_) => WireApi::Responses,
        }
    }
}

pub fn create_api_client<T: HttpTransport>(
    provider: &ResolvedProvider,
    transport: T,
) -> ApiClient<T> {
    let auth: Arc<dyn jasmine_api::AuthProvider> =
        Arc::new(BearerAuthProvider::new(provider.api_key().to_string()));
    match provider.wire_api() {
        WireApi::Chat => ApiClient::Chat(Box::new(ChatCompletionsClient::new(
            transport,
            provider.to_provider(),
            auth,
        ))),
        WireApi::Responses => ApiClient::Responses(Box::new(ResponsesClient::new(
            transport,
            provider.to_provider(),
            auth,
        ))),
    }
}
