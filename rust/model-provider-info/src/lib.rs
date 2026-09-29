#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]
//! Provider metadata: what a provider is configured as, and which wire protocol it speaks.

#[cfg(feature = "uniffi")]
uniffi::setup_scaffolding!("jasmine_model_provider_info");

pub mod presets;

use http::HeaderMap;
use jasmine_client::Provider;
use jasmine_client::RetryConfig;
use jasmine_protocol::openai_models::InputModality;
use jasmine_protocol::openai_models::default_input_modalities;
use serde::Deserialize;
use serde::Serialize;
use std::fmt;
use std::time::Duration;

const DEFAULT_REQUEST_MAX_RETRIES: u64 = 4;
const DEFAULT_STREAM_IDLE_TIMEOUT_MS: u64 = 300_000;
const MAX_REQUEST_MAX_RETRIES: u64 = 100;

/// Backoff floor for request retries.
const RETRY_BASE_DELAY_MS: u64 = 200;

/// Wire protocol of a model provider. Both are OpenAI-protocol flavours.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Enum))]
#[serde(rename_all = "lowercase")]
pub enum WireApi {
    /// The classic `POST /v1/chat/completions` API.
    Chat,
    /// The newer Responses API (`POST /v1/responses`).
    #[default]
    Responses,
}

impl fmt::Display for WireApi {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(match self {
            Self::Chat => "chat",
            Self::Responses => "responses",
        })
    }
}

/// One model configured under a provider.
///
/// `model_id` is the wire identifier, picked from the fetched model list or typed in as a
/// custom id — no model id is ever hardcoded. The two lengths are token budgets; `0` means
/// "not set", and the caller decides a default at request time.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ModelConfig {
    pub id: String,
    pub model_id: String,
    /// 选择器里显示的名字（出厂目录给的，例如 `GPT-5.5`）；空 = 就显示 [Self::model_id]。
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub context_length: u32,
    #[serde(default)]
    pub max_output_length: u32,
    /// What this model can read. Content of a modality missing here is replaced with a note
    /// before the request is sent, because a model that cannot read it rejects the whole request.
    #[serde(default = "default_input_modalities")]
    pub input_modalities: Vec<InputModality>,
    /// 这个模型在**配置里**声明的默认推理档（线上取值）；空 = 未设置。
    ///
    /// 只对"核心目录里没有的模型"有意义：目录里有这个 id 时，会话的起点档由目录的起点档说了算
    /// （见 [`crate::presets::default_level`]），配置里这份不参与 —— 界面也不给目录模型显示这一栏。
    #[serde(default)]
    pub reasoning_effort: String,
}

impl Default for ModelConfig {
    /// A model nothing is known about is assumed to read text and images, which is also what a
    /// stored entry without the field means.
    fn default() -> Self {
        Self {
            id: String::new(),
            model_id: String::new(),
            name: String::new(),
            context_length: 0,
            max_output_length: 0,
            input_modalities: default_input_modalities(),
            reasoning_effort: String::new(),
        }
    }
}

/// A model-provider entry (OpenAI-protocol compatible).
///
/// The credential is deliberately not part of this: metadata can be passed around freely,
/// while the key is attached only where a request is built.
///
/// `is_built_in` marks factory presets: they can be edited but never deleted, and the stored
/// list is always seeded with them on first launch. `models` are configured per provider —
/// entries come from the fetched model list or from a custom model id.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ModelProviderInfo {
    pub id: String,
    pub name: String,
    pub base_url: String,
    pub wire_api: WireApi,
    #[serde(default)]
    pub is_built_in: bool,
    #[serde(default)]
    pub models: Vec<ModelConfig>,
    /// Maximum number of times to retry a failed request to this provider.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub request_max_retries: Option<u64>,
    /// Idle timeout (in milliseconds) to wait for activity on a streaming response before
    /// treating the connection as lost.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub stream_idle_timeout_ms: Option<u64>,
}

impl ModelProviderInfo {
    /// Effective maximum number of request retries for this provider.
    pub fn request_max_retries(&self) -> u64 {
        self.request_max_retries
            .unwrap_or(DEFAULT_REQUEST_MAX_RETRIES)
            .min(MAX_REQUEST_MAX_RETRIES)
    }

    /// Effective idle timeout for streaming responses.
    pub fn stream_idle_timeout(&self) -> Duration {
        self.stream_idle_timeout_ms
            .map(Duration::from_millis)
            .unwrap_or(Duration::from_millis(DEFAULT_STREAM_IDLE_TIMEOUT_MS))
    }

    pub fn retry_config(&self) -> RetryConfig {
        RetryConfig {
            max_attempts: self.request_max_retries(),
            base_delay: Duration::from_millis(RETRY_BASE_DELAY_MS),
            retry_429: false,
            retry_5xx: true,
            retry_transport: true,
        }
    }

    /// The transport configuration the API clients run on.
    pub fn to_provider(&self) -> Provider {
        Provider {
            name: self.name.clone(),
            base_url: self.base_url.clone(),
            query_params: None,
            headers: HeaderMap::new(),
            retry: self.retry_config(),
            stream_idle_timeout: self.stream_idle_timeout(),
        }
    }
}
