//! DeepSeek 的模型目录，以及它家 `GET /v1/models` 的响应形状。
//!
//! 目录照 deepseek-harness（`dsh`）的 `packages/llm/llm-deepseek/src/models.ts` 抄：同一个 id、同一个
//! 名字、同一个上下文容量。**档位也照它抄**：它那边整条路由一套 `off`/`low`/`high`/`max`（默认 `high`），
//! 起点档就是那个默认。
//!
//! 端点在实测里其实还接受 `minimal`/`medium`/`xhigh`（`none` 当场关掉思考，返回里不再有
//! `reasoning_content`），但**上游不声明它们** —— 界面上多给几档只会让人猜"该选哪个"，所以不列。

use crate::ModelConfig;
use crate::presets::ModelCatalogEntry;
use crate::presets::ModelListShape;
use crate::presets::preset_model;
use jasmine_protocol::openai_models::ReasoningEffort;

/// 这家端点的形状：`{ "models": [ { "id" | "model_name": ... } ] }`（OpenAI 那套是 `data`/`id`）。
pub const SHAPE: ModelListShape = ModelListShape {
    list_keys: &["models"],
    id_keys: &["id", "model_name"],
};

/// 这家路由的档位，照 `dsh` 抄：`off` / `low` / `high` / `max`（它那边整条路由一套，不分模型）。
const LEVELS: &[ReasoningEffort] = &[
    ReasoningEffort::None,
    ReasoningEffort::Low,
    ReasoningEffort::High,
    ReasoningEffort::Max,
];

/// 这家端点的目录。上下文容量取 `dsh` 的默认（1,000,000）。
pub const CATALOG: &[ModelCatalogEntry] = &[
    ModelCatalogEntry {
        model_id: "deepseek-flash",
        name: "DeepSeek-V41-Flash",
        description: "",
        context_window: 1_000_000,
        default_level: Some(ReasoningEffort::High),
        levels: LEVELS,
    },
    ModelCatalogEntry {
        model_id: "deepseek-v4-pro",
        name: "DeepSeek-V4-Pro",
        description: "Stronger agentic coding, knowledge, and difficult reasoning; suited to \
                      complex or quality-critical tasks at higher cost.",
        context_window: 1_000_000,
        default_level: Some(ReasoningEffort::High),
        levels: LEVELS,
    },
];

/// 目录当模型配置用（出厂那份）。
pub fn preset_models() -> Vec<ModelConfig> {
    CATALOG.iter().map(preset_model).collect()
}


