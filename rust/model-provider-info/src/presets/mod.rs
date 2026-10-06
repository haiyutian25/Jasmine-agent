//! 出厂内置的供应商预设，以及各家的"专门适配"。
//!
//! 这一层是照 codex 抄来的通用骨架，往后每加一家就在这里加一个文件夹并登记到 [`built_in`]。一家的东西
//! （预设、模型目录、它家端点的响应形状）都收在它自己的文件夹里，界面那边不再重复一份。

pub mod deepseek;
pub mod openai;
pub mod openrouter;

use crate::ModelConfig;
use crate::ModelProviderInfo;
use jasmine_protocol::openai_models::ReasoningEffort;
use jasmine_protocol::openai_models::default_input_modalities;

/// 出厂内置的供应商，顺序就是界面里显示的顺序。
pub fn built_in() -> Vec<ModelProviderInfo> {
    vec![
        deepseek::provider(),
        openai::provider(),
        openrouter::provider(),
    ]
}

// ── 模型目录 ───────────────────────────────────────────────────────────

/// 目录里的一条：一个模型，以及它的能力 —— 相当于 codex 后端目录里的一个条目。
///
/// 目录是"后端给的"那一半知识（codex 放在 `codex-rs/models-manager/models.json`），界面上不由人填。
pub struct ModelCatalogEntry {
    /// 线上模型 id。
    pub model_id: &'static str,
    /// 选择器里的名字。
    pub name: &'static str,
    pub description: &'static str,
    /// 上下文容量（token）。
    pub context_window: u32,
    /// 新建会话时的起点档；`None` = 不设置（请求里一个推理字段都不发）。
    pub default_level: Option<ReasoningEffort>,
    /// 这个模型支持的档（线上取值，按面板里的顺序）。
    pub levels: &'static [ReasoningEffort],
}

/// 这条目录当模型配置用（出厂那份）：id 就用线上 id，名字与上下文容量来自目录。
///
/// **起点档不在模型配置里** —— 它在目录里（[`default_level`]），核心附着会话时直接问目录。
pub fn preset_model(entry: &ModelCatalogEntry) -> ModelConfig {
    ModelConfig {
        id: entry.model_id.to_string(),
        model_id: entry.model_id.to_string(),
        name: entry.name.to_string(),
        context_length: entry.context_window,
        max_output_length: 0,
        input_modalities: default_input_modalities(),
        // 目录模型的起点档不写在配置里 —— 它在目录里（`default_level`），核心附着会话时直接问目录。
        reasoning_effort: String::new(),
        // 压缩口径目录里也没有：`0` = 用核心的默认（全窗口 95% 那条线）。
        // 想按模型收紧（比如小窗口模型提前压），在配置里给这两个字段即可。
        auto_compact_token_limit: 0,
        effective_context_window_percent: 0,
    }
}

/// 这个供应商的**模型目录**：核心认得哪些模型。
///
/// 界面"获取模型列表 / 填自定义 ID"时拿它把表单填好（名字、上下文容量）—— 目录只在这里，出去的是值。
pub fn catalog(provider_id: &str) -> Vec<&'static ModelCatalogEntry> {
    match provider_id {
        deepseek::PROVIDER_ID => deepseek::models::CATALOG.iter().collect(),
        openai::PROVIDER_ID => openai::models::CATALOG.iter().collect(),
        _ => Vec::new(),
    }
}

/// 界面要用的**目录知识**：一家有自己目录就用它自己的；**这家没有目录**（OpenRouter 那种聚合网关、
/// 或者用户自己加的端点）就把现有那几家的目录**并起来**给它 —— 按模型 id 认，认得的模型一样能填名字、
/// 上下文容量、档位表。
///
/// 也就是说：聚合网关上的 `deepseek-v4-pro` 走的是 DeepSeek 那份目录知识，`gpt-5.5` 走 OpenAI 那份。
pub fn catalog_for(provider_id: &str) -> Vec<&'static ModelCatalogEntry> {
    let own = catalog(provider_id);
    if !own.is_empty() {
        return own;
    }
    catalog_all()
}

/// 全部预设的目录条目；同一个模型 id 只留第一份（先出现的优先）。
pub fn catalog_all() -> Vec<&'static ModelCatalogEntry> {
    let mut entries: Vec<&'static ModelCatalogEntry> = Vec::new();
    for provider in built_in() {
        for entry in catalog(&provider.id) {
            if !entries.iter().any(|known| known.model_id == entry.model_id) {
                entries.push(entry);
            }
        }
    }
    entries
}

/// 目录里用的键：线上 id 去掉 `厂商/` 前缀与 `:变体` 后缀。
///
/// 聚合网关（OpenRouter 那样）的 id 是 `厂商/模型`：`deepseek/deepseek-v4-pro`；免费档还带 `:free`
/// 这样一截后缀（`qwen/qwen3.8-27b:free`），**付费档通常没有后缀** —— 所以前后两种都得能对上。
/// 本来就是裸 id 的（我们目录里的那些）原样返回；归一化后空了（比如 id 就是 `/`）也原样返回。
pub fn catalog_key(model_id: &str) -> &str {
    let without_prefix = model_id.rsplit('/').next().unwrap_or(model_id);
    let bare = without_prefix.split(':').next().unwrap_or(without_prefix);
    if bare.is_empty() { model_id } else { bare }
}

/// 目录里的这一条（按 [`catalog_for`] 那份知识去找）；没有就是 `None`。
///
/// 先按原样找（绝大多数情况），找不到再按 [`catalog_key`] 归一化后的键找一遍 —— 这样网关上的
/// `deepseek/deepseek-v4-pro`、`qwen/qwen3.8-27b:free` 也能认出对应的目录知识。
fn entry_in(provider_id: &str, model_id: &str) -> Option<&'static ModelCatalogEntry> {
    let knowledge = catalog_for(provider_id);
    knowledge
        .iter()
        .find(|entry| entry.model_id == model_id)
        .or_else(|| {
            let key = catalog_key(model_id);
            knowledge.iter().find(|entry| entry.model_id == key)
        })
        .copied()
}

/// 这个模型声明支持的档（线上取值）；目录里没有它就是空的，界面按"不限制"处理。
pub fn reasoning_levels(provider_id: &str, model_id: &str) -> Vec<&'static str> {
    entry_in(provider_id, model_id)
        .map(|entry| {
            entry
                .levels
                .iter()
                .map(ReasoningEffort::as_str)
                .collect::<Vec<_>>()
        })
        .unwrap_or_default()
}

/// 这个模型支不支持这一档（界面里的取值）。
///
/// 目录里没有这个模型、或者它不声明支持表 → 按"不限制"处理（`true`）—— 与界面那条规则一致。
/// 换模型时用它把"会话里存过、但新模型不支持"的档矫正掉（codex 在它 `turn_context` 里同样这么做）。
pub fn supports_level(provider_id: &str, model_id: &str, level: &str) -> bool {
    let levels = reasoning_levels(provider_id, model_id);
    levels.is_empty() || levels.iter().any(|candidate| *candidate == level)
}

/// 请求**真正发出去**的取值：界面档 `ultra` 换成这个模型支持的最强档。
///
/// 照 codex 的 `resolve_reasoning_effort`：优先 `max`，没有就取支持表里最强的那一档；目录里没有这个
/// 模型、没声明支持表时用 `max`（通用写法里最强的一档）。其余档位原样返回 —— 上游不声明的档进不了
/// 支持表，所以"按支持表过滤"不在这里，那一步发生在换模型时（见 [`supports_level`]）。
pub fn wire_level(provider_id: &str, model_id: &str, level: &str) -> String {
    if level != ReasoningEffort::Ultra.as_str() {
        return level.to_string();
    }
    let levels = reasoning_levels(provider_id, model_id);
    levels
        .iter()
        .rev()
        .find(|candidate| **candidate != ReasoningEffort::Ultra.as_str())
        .map(|candidate| (*candidate).to_string())
        .unwrap_or_else(|| ReasoningEffort::Max.as_str().to_string())
}

/// 目录给这个模型的**起点档**（新建会话时的档）；`None` = 目录里没有它、或者它不声明起点档，
/// 那就是"未设置"（请求里一个推理字段都不发）。
pub fn default_level(provider_id: &str, model_id: &str) -> Option<&'static str> {
    entry_in(provider_id, model_id)?
        .default_level
        .as_ref()
        .map(ReasoningEffort::as_str)
}

// ── 模型列表形状 ───────────────────────────────────────────────────────

/// 一家端点的模型列表形状：顶层键名与每条里 id 的键名，都是"按顺序试"。
///
/// 通用代码只遍历这张表，不认识任何一家；每家的形状由它自己的模块给出（见 [`deepseek::models::SHAPE`]）。
#[derive(Debug, Clone, Copy)]
pub struct ModelListShape {
    pub list_keys: &'static [&'static str],
    pub id_keys: &'static [&'static str],
}

/// OpenAI 那套形状：手工录入（非预设）的端点只有它可用。
pub const OPENAI_MODEL_LIST: ModelListShape = ModelListShape {
    list_keys: &["data"],
    id_keys: &["id"],
};

/// 已知形状，顺序就是解析时试的顺序：先通用的，再各预设自己的。
pub fn model_list_shapes() -> Vec<ModelListShape> {
    vec![OPENAI_MODEL_LIST, deepseek::models::SHAPE]
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 档位表照上游：DeepSeek 是它那条路由的四档，OpenAI 逐模型（含 `ultra`、`gpt-5.5` 没有 `max`）。
    #[test]
    fn the_catalog_levels_follow_the_upstream_projects() {
        assert_eq!(
            reasoning_levels("deepseek", "deepseek-flash"),
            ["none", "low", "high", "max"]
        );
        assert_eq!(
            reasoning_levels("openai", "gpt-5.5"),
            ["low", "medium", "high", "xhigh"]
        );
        assert!(reasoning_levels("openai", "gpt-6-astra").contains(&"ultra"));
        assert!(!reasoning_levels("openai", "gpt-6-luna").contains(&"ultra"));
    }

    /// OpenRouter 是个聚合网关、**没有自己的目录**：它按"现有两家的目录知识"认模型 —— 认得的照样
    /// 有档位表与起点档，不认得的就是"目录外"（不限制 + 没有起点档）。
    #[test]
    fn the_aggregator_borrows_the_other_catalogs() {
        assert!(catalog("openrouter").is_empty());

        // 认得的：走 DeepSeek 那份知识。
        assert_eq!(
            reasoning_levels("openrouter", "deepseek-v4-pro"),
            ["none", "low", "high", "max"]
        );
        assert_eq!(default_level("openrouter", "deepseek-v4-pro"), Some("high"));
        // 认得的：走 OpenAI 那份知识（`gpt-5.5` 没有 `max`）。
        assert_eq!(
            reasoning_levels("openrouter", "gpt-5.5"),
            ["low", "medium", "high", "xhigh"]
        );

        // 不认得的：不限制（并集里的每一档都算支持）、没有起点档。
        assert!(reasoning_levels("openrouter", "some-other/model").is_empty());
        assert_eq!(default_level("openrouter", "some-other/model"), None);
        assert!(supports_level("openrouter", "some-other/model", "max"));
        assert!(supports_level("openrouter", "some-other/model", "none"));

        // 网关的 id 带 `厂商/` 前缀、免费档还带 `:变体` 后缀 —— 归一化之后照样认得（付费档没有后缀 ✓）。
        assert_eq!(
            reasoning_levels("openrouter", "deepseek/deepseek-v4-pro"),
            ["none", "low", "high", "max"]
        );
        assert_eq!(
            default_level("openrouter", "deepseek/deepseek-v4-pro"),
            Some("high")
        );
        assert_eq!(
            reasoning_levels("openrouter", "deepseek/deepseek-v4-pro:free"),
            ["none", "low", "high", "max"]
        );
        assert_eq!(
            reasoning_levels("openrouter", "openai/gpt-5.5:free"),
            ["low", "medium", "high", "xhigh"]
        );
        // 归一化之后仍然不认得的：还是"目录外"。
        assert!(reasoning_levels("openrouter", "qwen/qwen3.8-27b:free").is_empty());
    }

    /// 目录键的归一化：去掉 `厂商/` 前缀与 `:变体` 后缀；裸 id 原样；归一化后为空也原样。
    #[test]
    fn the_catalog_key_strips_the_gateway_affixes() {
        assert_eq!(catalog_key("deepseek/deepseek-v4-pro"), "deepseek-v4-pro");
        assert_eq!(catalog_key("qwen/qwen3.8-27b:free"), "qwen3.8-27b");
        assert_eq!(catalog_key("deepseek-v4-pro"), "deepseek-v4-pro");
        assert_eq!(catalog_key("gpt-5.5"), "gpt-5.5");
        // 只剩后缀、或者什么都没有：原样返回，别把 id 弄没了。
        assert_eq!(catalog_key(":free"), ":free");
        assert_eq!(catalog_key(""), "");
    }

    /// "支不支持"照支持表；目录里没有这一家/这个模型 = 不限制（什么都算支持）。
    #[test]
    fn a_level_is_supported_unless_the_catalog_says_otherwise() {
        assert!(supports_level("openai", "gpt-6-astra", "ultra"));
        assert!(!supports_level("openai", "gpt-5.5", "max"));
        // 上游不声明 `minimal`，所以它不算支持 —— 这就是"多的删掉"那条规矩。
        assert!(!supports_level("deepseek", "deepseek-flash", "minimal"));
        // 目录里没有这个模型：不限制。
        assert!(supports_level("openai", "nope", "ultra"));
    }

    /// 界面档 `ultra` 不会原样发出去：换成这个模型支持的最强档（照 codex 的 `resolve_reasoning_effort`）。
    #[test]
    fn only_the_interface_level_ultra_is_resolved_for_the_wire() {
        // 声明了 `max` 就用 `max`。
        assert_eq!(wire_level("openai", "gpt-6-astra", "ultra"), "max");
        // `gpt-5.5` 只到 `xhigh`，就用 `xhigh`。
        assert_eq!(wire_level("openai", "gpt-5.5", "ultra"), "xhigh");
        // 目录里没有这个模型：用 `max`。
        assert_eq!(wire_level("openai", "nope", "ultra"), "max");
        // 其余档位原样。
        assert_eq!(wire_level("deepseek", "deepseek-flash", "low"), "low");
        assert_eq!(wire_level("deepseek", "deepseek-flash", "none"), "none");
    }
}
