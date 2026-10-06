package com.lhzkml.jasmine.core.data.model

import kotlinx.serialization.Serializable

/**
 * Wire protocol of a model provider. Both are OpenAI-protocol flavours:
 * - [CHAT_COMPLETIONS]: the classic `POST /v1/chat/completions` API.
 * - [RESPONSES]: the newer OpenAI Responses API (`POST /v1/responses`).
 */
@Serializable
enum class ProviderApiType {
    CHAT_COMPLETIONS,
    RESPONSES,
}

/**
 * One model configured under a provider. [modelId] is the wire identifier,
 * picked from the fetched model list or typed in as a custom id — no model
 * id is ever hardcoded. [contextLength] / [maxOutputLength] are token
 * budgets; 0 means "not set" (the caller decides a default at request time).
 */
@Serializable
data class ModelConfig(
    val id: String,
    val modelId: String,
    /** 出厂目录给的名字（例如 `GPT-5.5`）；空 = 就显示 [modelId]。 */
    val name: String = "",
    val contextLength: Int = 0,
    val maxOutputLength: Int = 0,
    /**
     * 这个模型的默认档（新建会话的起点）；空串 = 未设置。
     *
     * 只有**核心目录里没有的模型**才需要它 —— 目录里有这个 id 时，起点档由目录说了算（核心附着会话时
     * 直接问目录），供应商页也就不显示这一栏。
     *
     * **它有默认值（空串）是这个字段的既有形状**：这一层配置整体是"少给哪个键就取默认值"，
     * 空串同时也是"未设置"的表示法。所以请不要把这个默认值当成"给老数据兜底"的兼容分支删掉 ——
     * 删了它就变成必填字段，少一个键的配置直接解不出来，而这不是这个字段要表达的意思。
     */
    val reasoningEffort: String = "",
    /**
     * 自动压缩的触发线（token 数）；`0` = 未设置。
     *
     * 与核心的 `ModelConfig::auto_compact_token_limit` 一一对应。`0` 表示"这个模型没填" ——
     * 这时落回**全局默认**（`UserPreferences.autoCompactTokenLimit`，在设置页的「行为与权限」里），
     * `0` 的语义与 [contextLength] / [maxOutputLength] 完全一致。
     */
    val autoCompactTokenLimit: Int = 0,
    /** 自动压缩的窗口百分比；`0` = 未设置（落回全局默认，再落回核心的 95）。 */
    val effectiveContextWindowPercent: Int = 0,
    )

/**
 * A persisted model-provider entry (OpenAI-protocol compatible).
 *
 * [isBuiltIn] marks factory presets (DeepSeek): they can be edited but never
 * deleted, and the list is always seeded with them on first launch.
 * [models] are configured per provider — nothing is hardcoded: entries come
 * from the fetched `/v1/models` list or from a custom model id.
 */
@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val apiType: ProviderApiType,
    val isBuiltIn: Boolean = false,
    val models: List<ModelConfig> = emptyList(),
) {
    /**
     * 日志与调试输出里**不打印密钥**。
     *
     * 这是 data class，生成的 `toString()` 会把主构造参数全部打出来 —— 只要有任何一处
     * 把整个对象写进日志（例如 `Log.w(TAG, "effect $effect failed", error)`，而 effect 里
     * 就装着这个对象），用户的 API key 就落进 logcat 了。这里覆写成脱敏版本；
     * 其余字段照常显示，排障仍然够用。
     */
    override fun toString(): String =
        "ProviderConfig(id=$id, name=$name, baseUrl=$baseUrl, " +
            "apiKey=${if (apiKey.isEmpty()) "<empty>" else "<redacted>"}, " +
            "apiType=$apiType, isBuiltIn=$isBuiltIn, models=$models)"
}
