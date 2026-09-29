package com.lhzkml.jasmine.core.data.model

/**
 * 出厂内置供应商的种子。
 *
 * 内容由 Rust 核心给出（`rust/model-provider-info/src/presets`）—— 供应商清单的真源在那边，界面这边
 * 不再写死一份；往后加一家供应商，只在 Rust 那边加一个预设。实现在 `core:agent`（只有它挂着 Rust 的
 * 绑定），由 Hilt 注入到这里。
 */
fun interface BuiltInProviders {

    /** 首次启动、或存的那份 JSON 坏了时，用它当种子。 */
    fun list(): List<ProviderConfig>
}
