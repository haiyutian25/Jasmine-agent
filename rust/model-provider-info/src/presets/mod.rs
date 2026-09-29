//! 出厂内置的供应商预设，以及各家的"专门适配"。
//!
//! DeepSeek 只是**第一个**：这一层是照 codex 抄来的通用骨架，往后每加一家就在这里加一个文件夹并登记到
//! [`built_in`]。一家的东西（预设、它家端点的响应形状）都收在它自己的文件夹里，界面那边不再重复一份。

pub mod deepseek;

use crate::ModelProviderInfo;

/// 出厂内置的供应商，顺序就是界面里显示的顺序。
pub fn built_in() -> Vec<ModelProviderInfo> {
    vec![deepseek::provider()]
}

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
