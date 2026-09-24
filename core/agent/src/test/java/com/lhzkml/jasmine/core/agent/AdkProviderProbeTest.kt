package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The probe reads the *settled* response, which is what makes it usable against the
 * real adapters.
 *
 * Both OpenAI adapters stream unconditionally, and the opening frame of an OpenAI
 * stream is a role-only delta carrying no text. A probe that read the first response
 * would report "the model returned an empty reply" for a provider that answered
 * perfectly — which is exactly what it did before this was covered.
 */
class AdkProviderProbeTest {

    @Test
    fun `a streaming provider is reported as reachable`() = runTest {
        val probe = AdkProviderProbe { _, _ -> ProbeReplyModel(reply = "pong") }

        val result = probe.probe(PROVIDER, "test-model")

        assertEquals(ProbeResult.Success("pong"), result)
    }

    @Test
    fun `a stream that carries no text is reported as an empty reply`() = runTest {
        val probe = AdkProviderProbe { _, _ -> ProbeReplyModel(reply = "") }

        val result = probe.probe(PROVIDER, "test-model")

        assertTrue(
            "expected an empty-reply failure, got $result",
            result is ProbeResult.Failure && result.detail.contains("empty reply"),
        )
    }

    @Test
    fun `a transport error is reported verbatim`() = runTest {
        val probe = AdkProviderProbe { _, _ -> ProbeErrorModel() }

        val result = probe.probe(PROVIDER, "test-model")

        assertEquals(ProbeResult.Failure("HTTP 401 Unauthorized"), result)
    }

    private companion object {
        val PROVIDER = ProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            apiKey = "sk-test",
            apiType = ProviderApiType.CHAT_COMPLETIONS,
        )
    }
}

/**
 * The shape the real adapters produce for [reply]: a role-only opening delta, a text
 * delta, then the settled aggregate — so no text is available on the first response.
 */
private class ProbeReplyModel(private val reply: String) : Model {

    override val name = "streaming-model"

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> =
        flowOf(
            LlmResponse(partial = true),
            LlmResponse(content = reply.content(), partial = true),
            LlmResponse(content = reply.content(), partial = false),
        )

    private fun String.content(): Content? =
        takeIf { it.isNotEmpty() }?.let {
            Content(role = Role.MODEL, parts = listOf(Part(text = it)))
        }
}

private class ProbeErrorModel : Model {
    override val name = "failing-model"

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> =
        throw IOException("HTTP 401 Unauthorized")
}
