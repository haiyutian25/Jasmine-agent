//! DeepSeek 的 `GET /v1/models` 响应形状。
//!
//! 它回的是 `{ "models": [ { "id" | "model_name": ... } ] }`，而 OpenAI 那套是
//! `{ "data": [ { "id": ... } ] }`。形状表放在这里，通用代码只负责逐个试，不认识任何一家。

use crate::presets::ModelListShape;

/// 这家端点的形状。
pub const SHAPE: ModelListShape = ModelListShape {
    list_keys: &["models"],
    id_keys: &["id", "model_name"],
};
