//! OpenAI 的模型目录：照 codex 的 `codex-rs/models-manager/models.json` 抄。
//!
//! 只要 `visibility = "list"` 的那七个 —— 另外三个（`gpt-daybreak-*-latest`、`codex-auto-review`）
//! codex 自己也不在选择器里列，是内部/灰度用的。
//!
//! **档位逐模型照抄**：`low`…`max`，其中四个（`gpt-6-astra`、`gpt-6-sol`、`gpt-5.6-sol`、
//! `gpt-5.6-terra`）还声明了 `ultra`；`gpt-5.5` 只有四档（没有 `max`，也没有 `ultra`）。起点档同样是
//! 照抄它每个模型的 `default_reasoning_level`。
//!
//! `ultra` 是**界面档**、不是线上取值：codex 发请求前会把它换成该模型支持的最强档（见它的
//! `resolve_reasoning_effort`），我们照做 —— 解析在 [`crate::presets::wire_level`]。
//! `none`/`minimal` 它没有，我们也不替它加。

use crate::ModelConfig;
use crate::presets::ModelCatalogEntry;
use crate::presets::preset_model;
use jasmine_protocol::openai_models::ReasoningEffort;

/// 五个档：`low`…`max` —— 没声明 `ultra` 的那两个模型用这一份。
const LEVELS_WITHOUT_ULTRA: &[ReasoningEffort] = &[
    ReasoningEffort::Low,
    ReasoningEffort::Medium,
    ReasoningEffort::High,
    ReasoningEffort::XHigh,
    ReasoningEffort::Max,
];

/// 六个档：上面那份再加 `ultra`（界面档，发请求前换成最强档）。
const LEVELS_WITH_ULTRA: &[ReasoningEffort] = &[
    ReasoningEffort::Low,
    ReasoningEffort::Medium,
    ReasoningEffort::High,
    ReasoningEffort::XHigh,
    ReasoningEffort::Max,
    ReasoningEffort::Ultra,
];

/// `gpt-5.5` 的四档：它没有 `max`、也没有 `ultra`。
const LEVELS_GPT_5_5: &[ReasoningEffort] = &[
    ReasoningEffort::Low,
    ReasoningEffort::Medium,
    ReasoningEffort::High,
    ReasoningEffort::XHigh,
];

/// 这家端点的目录。
pub const CATALOG: &[ModelCatalogEntry] = &[
    ModelCatalogEntry {
        model_id: "gpt-6-astra",
        name: "GPT-6-Astra",
        description: "Frontier intelligence for the most demanding work.",
        context_window: 272_000,
        default_level: Some(ReasoningEffort::Low),
        levels: LEVELS_WITH_ULTRA,
    },
    ModelCatalogEntry {
        model_id: "gpt-6-sol",
        name: "GPT-6-Sol",
        description: "Workhorse model for coding and everyday work.",
        context_window: 272_000,
        default_level: Some(ReasoningEffort::Medium),
        levels: LEVELS_WITH_ULTRA,
    },
    ModelCatalogEntry {
        model_id: "gpt-6-luna",
        name: "GPT-6-Luna",
        description: "Fast and affordable model for easier tasks.",
        context_window: 272_000,
        default_level: Some(ReasoningEffort::Medium),
        levels: LEVELS_WITHOUT_ULTRA,
    },
    ModelCatalogEntry {
        model_id: "gpt-5.6-sol",
        name: "GPT-5.6-Sol",
        description: "Older coding model for complex work.",
        context_window: 272_000,
        default_level: Some(ReasoningEffort::Low),
        levels: LEVELS_WITH_ULTRA,
    },
    ModelCatalogEntry {
        model_id: "gpt-5.6-terra",
        name: "GPT-5.6-Terra",
        description: "Older balanced model for straightforward work.",
        context_window: 272_000,
        default_level: Some(ReasoningEffort::Medium),
        levels: LEVELS_WITH_ULTRA,
    },
    ModelCatalogEntry {
        model_id: "gpt-5.6-luna",
        name: "GPT-5.6-Luna",
        description: "Older fast and efficient model.",
        context_window: 272_000,
        default_level: Some(ReasoningEffort::Medium),
        levels: LEVELS_WITHOUT_ULTRA,
    },
    ModelCatalogEntry {
        model_id: "gpt-5.5",
        name: "GPT-5.5",
        description: "Legacy coding model.",
        context_window: 272_000,
        default_level: Some(ReasoningEffort::Medium),
        levels: LEVELS_GPT_5_5,
    },
];

/// 目录当模型配置用（出厂那份）。
pub fn preset_models() -> Vec<ModelConfig> {
    CATALOG.iter().map(preset_model).collect()
}


