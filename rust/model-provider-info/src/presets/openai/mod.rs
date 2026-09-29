//! OpenAI 预设（Responses API）。模型目录照 codex 那份抄，见 [`models`]。

pub mod models;

use crate::ModelProviderInfo;
use crate::WireApi;

/// 供应商 id。
pub const PROVIDER_ID: &str = "openai";

/// 官方端点（codex 内置的那条）。
pub const BASE_URL: &str = "https://api.openai.com/v1";

/// 出厂那份配置。
pub fn provider() -> ModelProviderInfo {
    ModelProviderInfo {
        id: PROVIDER_ID.to_string(),
        name: "OpenAI".to_string(),
        base_url: BASE_URL.to_string(),
        wire_api: WireApi::Responses,
        is_built_in: true,
        models: models::preset_models(),
        request_max_retries: None,
        stream_idle_timeout_ms: None,
    }
}
