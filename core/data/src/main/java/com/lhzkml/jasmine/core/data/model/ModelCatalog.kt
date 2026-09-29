package com.lhzkml.jasmine.core.data.model

/**
 * 拉取一家端点提供的模型名（`GET {base_url}/v1/models`）。
 *
 * 实现在 `core:agent`（走 Rust 核心）：这次请求由核心自己发，各家响应形状的适配也收在核心的预设里
 * （见 `rust/model-provider-info/src/presets`）—— 界面这边只剩"调一次、把失败报出来"。
 */
fun interface ModelCatalog {

    /** 失败时抛异常（调用方把原因当一次失败提示）；结果是一次性的界面数据，不落盘。 */
    suspend fun list(provider: ProviderConfig): List<String>
}
