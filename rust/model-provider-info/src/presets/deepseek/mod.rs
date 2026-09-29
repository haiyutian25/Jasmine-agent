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

/// 出厂那份配置。模型列表留空 —— 由界面从端点拉取或手工添加。
pub fn provider() -> ModelProviderInfo {
    ModelProviderInfo {
        id: PROVIDER_ID.to_string(),
        name: "DeepSeek".to_string(),
        base_url: BASE_URL.to_string(),
        wire_api: WireApi::Chat,
        is_built_in: true,
        models: Vec::new(),
        request_max_retries: None,
        stream_idle_timeout_ms: None,
    }
}
