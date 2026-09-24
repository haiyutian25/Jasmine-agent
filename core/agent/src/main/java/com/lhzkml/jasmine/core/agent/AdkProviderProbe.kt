package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.toList

/**
 * ADK-backed [ProviderProbe]: builds the [Model] matching the provider's wire
 * protocol and runs a single turn through it.
 *
 * The model is injected as a factory, so this class holds no transport wiring.
 *
 * The whole flow is consumed and the *settled* response is read, because the
 * adapters stream unconditionally: one call yields the deltas followed by the
 * aggregate ADK's `StreamingResponseAggregator` emits. Reading only the first
 * response would read the opening delta of an OpenAI stream, which is a
 * role-only frame carrying no text — indistinguishable from a silent model.
 */
class AdkProviderProbe(
    private val modelFactory: (ProviderConfig, String) -> Model,
) : ProviderProbe {

    override suspend fun probe(provider: ProviderConfig, modelId: String): ProbeResult {
        val model = modelFactory(provider, modelId)
        val request = LlmRequest(
            model = model,
            contents = listOf(
                Content(
                    role = Role.USER,
                    parts = listOf(Part(text = PROBE_PROMPT)),
                )
            ),
        )

        return try {
            val responses = model.generateContent(request, stream = false).toList()
            val settled = responses.lastOrNull { !it.partial } ?: responses.lastOrNull()
            val reply = settled
                ?.content
                ?.parts
                .orEmpty()
                .mapNotNull { it.text }
                .joinToString("")
                .trim()
            if (reply.isEmpty()) {
                ProbeResult.Failure(EMPTY_REPLY_DETAIL)
            } else {
                ProbeResult.Success(reply)
            }
        } catch (cancellation: CancellationException) {
            // Never swallow cancellation: the caller's scope is going away.
            throw cancellation
        } catch (error: Exception) {
            ProbeResult.Failure(error.message ?: error::class.simpleName.orEmpty())
        }
    }

    private companion object {
        /** Cheapest prompt that still proves the model answers with content. */
        const val PROBE_PROMPT = "Reply with the single word: pong"

        const val EMPTY_REPLY_DETAIL = "the model returned an empty reply"
    }
}
