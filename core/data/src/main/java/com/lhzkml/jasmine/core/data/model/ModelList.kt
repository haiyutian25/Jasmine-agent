package com.lhzkml.jasmine.core.data.model

/**
 * 目录里的一个模型（值来自核心的模型目录，不是界面自己定义的一份）。
 *
 * [name] 是选择器里显示的名字，[contextLength] 是它的上下文容量 —— 界面据此把表单自动填好。
 */
data class CatalogModel(
    val modelId: String,
    val name: String,
    val contextLength: Int,
    /**
     * 目录给这个模型声明的档（线上取值）；空 = 目录没声明（聊天页就按"不限制"列出全部）。
     *
     * 聊天输入行那张档位面板按它来列 —— 目录里有这个模型就不用在界面上自己填了。
     */
    val levels: List<String> = emptyList(),
)

/**
 * 目录里用的键：线上 id 去掉 `厂商/` 前缀与 `:变体` 后缀。
 *
 * 聚合网关（OpenRouter 那样）的 id 是 `厂商/模型`，免费档还带 `:free` 这样一截（**付费档通常没有**），
 * 而我们目录里的键是裸 id —— 归一化之后才比得上。
 *
 * **规则与核心同一条**（`rust/model-provider-info/src/presets/mod.rs` 的 `catalog_key`）：核心那边管
 * 起点档/矫正/档位表，这边管界面上的填空与判定，两边要改一起改。
 */
fun catalogKey(modelId: String): String {
    val withoutPrefix = modelId.substringAfterLast('/')
    val bare = withoutPrefix.substringBefore(':')
    return bare.ifEmpty { modelId }
}

/** 目录里认得 [modelId] 吗（先按原样，再按 [catalogKey] 归一化后的键找一遍）。 */
fun List<CatalogModel>.findInCatalog(modelId: String): CatalogModel? =
    firstOrNull { it.modelId == modelId } ?: firstOrNull { it.modelId == catalogKey(modelId) }

/**
 * 这一层只说两件与"模型"有关的事：
 *
 * - [fetch]：端点自己报出来的模型（`GET {base_url}/v1/models`）；
 * - [catalog]：**核心认得的**模型（名字、上下文容量），用来把表单自动填好。
 *
 * 模型目录本身只在 Rust 里（`rust/model-provider-info/src/presets`），这里出去的是它的值。
 * 实现在 `core:agent`。
 */
interface ModelList {

    /** 失败时抛异常（调用方把原因当一次失败提示）；结果是一次性的界面数据，不落盘。 */
    suspend fun fetch(provider: ProviderConfig): List<String>

    /**
     * 核心目录里认得的模型；**只有出厂那几家供应商有目录**（目录按供应商 id 认），认不出就是空的。
     * 这是纯本地查询（不碰网络、不涉及凭据），所以只要 id。
     */
    suspend fun catalog(providerId: String): List<CatalogModel>
}
