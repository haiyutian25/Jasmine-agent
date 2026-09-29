//! DeepSeek 预设（OpenAI 兼容的 Chat Completions）。
//!
//! 这一家的"专门适配"都收在这个文件夹里：预设本身在这里，它家端点的响应形状在 [`models`]。

pub mod models;

use crate::ModelProviderInfo;
use crate::WireApi;

/// 供应商 id。
pub const PROVIDER_ID: &str = "deepseek";

/// 官方端点。
pub const BASE_URL: &str = "https://api.deepseek.com";

/// 出厂那份配置：模型来自目录（见 [`models`]），界面不用再手工录一遍。
pub fn provider() -> ModelProviderInfo {
    ModelProviderInfo {
        id: PROVIDER_ID.to_string(),
        name: "DeepSeek".to_string(),
        base_url: BASE_URL.to_string(),
        wire_api: WireApi::Chat,
        is_built_in: true,
        models: models::preset_models(),
        request_max_retries: None,
        stream_idle_timeout_ms: None,
    }
}
