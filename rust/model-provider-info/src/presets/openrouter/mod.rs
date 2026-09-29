//! OpenRouter 预设：一个 OpenAI 兼容的**聚合网关**。
//!
//! 它没有模型目录（见 [`models`]）—— 模型靠"获取模型列表"拉，或者自己填 id，两者都按**目录外**的模型
//! 处理：档位用现有两家并集里的那些线上档，起点档不设置。

pub mod models;

use crate::ModelProviderInfo;
use crate::WireApi;

/// 供应商 id。
pub const PROVIDER_ID: &str = "openrouter";

/// 官方端点（OpenAI 兼容那一套）。
pub const BASE_URL: &str = "https://openrouter.ai/api/v1";

/// 出厂那份配置。模型列表空着：它家的模型由用户拉取/自己填。
pub fn provider() -> ModelProviderInfo {
    ModelProviderInfo {
        id: PROVIDER_ID.to_string(),
        name: "OpenRouter".to_string(),
        base_url: BASE_URL.to_string(),
        wire_api: WireApi::Chat,
        is_built_in: true,
        models: models::preset_models(),
        request_max_retries: None,
        stream_idle_timeout_ms: None,
    }
}
