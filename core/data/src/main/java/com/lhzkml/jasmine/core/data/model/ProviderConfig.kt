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
    val contextLength: Int = 0,
    val maxOutputLength: Int = 0,
    /**
     * 这个模型该用多强的推理 —— 新建会话时的**默认档**：空串 = 未设置（请求里一个推理字段都不发）、
     * `none` = 关闭、其余取 `minimal` / `low` / `medium` / `high` / `xhigh` / `max`（与 codex 的
     * `ReasoningEffort` 同一个取值表）。
     */
    val reasoningEffort: String = "",
    /**
     * 这个模型**支持哪些档**（对应 codex 的 `ModelInfo.supported_reasoning_levels`）：会话里那张档位
     * 面板只列这些。
     *
     * 空 = **不限制**（所有档都可用），也是没配过的模型的默认。它只管"可选项"，不碰请求 —— 请求里发的
     * 是**会话**当前那一档（见 `ModelConfig.reasoningEffort` 与 core 的首次附着拷贝）。
     */
    val reasoningEfforts: List<String> = emptyList(),
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
