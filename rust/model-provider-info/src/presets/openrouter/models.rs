//! OpenRouter 的模型目录：**空的**。
//!
//! 它是个聚合网关（同一个端点后面挂着几百个模型，各家的都有），我们手上没有可信的"哪个模型支持哪些档"
//! 的数据 —— 硬编一份只会骗人。所以这里什么都不声明：界面按"目录里没有这个模型"处理，档位用现有两家
//! 并集里的那些线上档（见 [`crate::presets::reasoning_levels`]），起点档也是"未设置"。

use crate::ModelConfig;
use crate::presets::ModelCatalogEntry;

/// 空的目录。
pub const CATALOG: &[ModelCatalogEntry] = &[];

/// 目录当模型配置用（空的那份）。
pub fn preset_models() -> Vec<ModelConfig> {
    Vec::new()
}
