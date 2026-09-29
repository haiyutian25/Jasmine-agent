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
     */
    val reasoningEffort: String = "",
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
)
