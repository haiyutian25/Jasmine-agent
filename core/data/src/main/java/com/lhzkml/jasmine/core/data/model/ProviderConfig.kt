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
    companion object {
        /** Factory preset: DeepSeek (OpenAI-compatible Chat Completions). */
        val DEEPSEEK = ProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            apiKey = "",
            apiType = ProviderApiType.CHAT_COMPLETIONS,
            isBuiltIn = true,
        )

        /** Seed list persisted on first launch. */
        val DEFAULTS: List<ProviderConfig> = listOf(DEEPSEEK)
    }
}
