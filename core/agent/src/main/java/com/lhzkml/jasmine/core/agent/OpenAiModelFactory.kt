package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.models.Model
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient

/**
 * Builds the ADK [Model] that speaks [provider]'s wire protocol.
 *
 * The single construction site of the two protocol adapters, shared by
 * [AdkProviderProbe] and [AdkAgentChat] — adding a third protocol means adding
 * one branch here rather than hunting for call sites.
 */
internal fun openAiModelFor(
    provider: ProviderConfig,
    modelId: String,
    httpClient: OkHttpClient,
    ioDispatcher: CoroutineDispatcher,
): Model =
    when (provider.apiType) {
        ProviderApiType.CHAT_COMPLETIONS -> OpenAiChatCompletionsModel(
            config = provider,
            modelId = modelId,
            httpClient = httpClient,
            ioDispatcher = ioDispatcher,
        )
        ProviderApiType.RESPONSES -> OpenAiResponsesModel(
            config = provider,
            modelId = modelId,
            httpClient = httpClient,
            ioDispatcher = ioDispatcher,
        )
    }
