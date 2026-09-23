package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the real ADK runner with a recording [Model].
 *
 * This is what makes the transcript restore trustworthy: a restored conversation
 * is only useful if the replayed turns actually reach the model, and that is
 * asserted here by inspecting the `LlmRequest` the runner builds — no network,
 * no API key.
 */
class AdkAgentChatTest {

    @Test
    fun `replayed history reaches the model`() = runTest {
        val model = RecordingModel(reply = "second answer")
        val chat = AdkAgentChat(modelFactory = { _, _ -> model })

        chat.startConversation(
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
            history = listOf(
                ChatTurn(ChatRole.USER, "first question"),
                ChatTurn(ChatRole.ASSISTANT, "first answer"),
            ),
        )
        chat.send("second question").toList()

        val texts = model.requests.single().texts()
        assertTrue("replayed user turn missing: $texts", texts.contains("first question"))
        assertTrue("replayed assistant turn missing: $texts", texts.contains("first answer"))
        assertTrue("new turn missing: $texts", texts.contains("second question"))
        // Replayed turns keep their role so the model reads them as a dialogue.
        assertEquals(1, model.requests.single().contents.count { it.role == Role.MODEL })
    }

    @Test
    fun `without history the model only sees the new message`() = runTest {
        val model = RecordingModel(reply = "hi")
        val chat = AdkAgentChat(modelFactory = { _, _ -> model })

        chat.startConversation(provider = PROVIDER, modelId = "test-model", instruction = "be nice")
        chat.send("only message").toList()

        assertEquals(listOf("only message"), model.requests.single().texts())
    }

    @Test
    fun `assistant text is streamed back to the caller`() = runTest {
        val model = RecordingModel(reply = "pong")
        val chat = AdkAgentChat(modelFactory = { _, _ -> model })
        chat.startConversation(provider = PROVIDER, modelId = "test-model", instruction = "be nice")

        val events = chat.send("ping").toList()

        val texts = events.filterIsInstance<ChatEvent.Text>().map { it.text }
        assertEquals(listOf("pong"), texts)
        // The caller's own message is not echoed back: it is already rendered locally.
        assertFalse(texts.contains("ping"))
        // Note: ChatEvent.Completed is NOT asserted. ADK does not reliably mark the
        // final event as turnComplete for a plain single-agent turn, which is why
        // ChatViewModel posts its own TurnCompleted once the flow ends.
    }

    @Test
    fun `a model error becomes a failed event`() = runTest {
        val model = RecordingModel(reply = "", errorMessage = "HTTP 401: bad key")
        val chat = AdkAgentChat(modelFactory = { _, _ -> model })
        chat.startConversation(provider = PROVIDER, modelId = "test-model", instruction = "be nice")

        val events = chat.send("ping").toList()

        assertEquals(
            listOf("HTTP 401: bad key"),
            events.filterIsInstance<ChatEvent.Failed>().map { it.detail },
        )
    }

    @Test
    fun `sending before starting a conversation is rejected`() = runTest {
        val chat = AdkAgentChat(modelFactory = { _, _ -> RecordingModel(reply = "x") })

        val failure = runCatching { chat.send("hi").toList() }.exceptionOrNull()

        assertTrue("expected IllegalStateException, got $failure", failure is IllegalStateException)
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

/** Records every request it is asked to serve and replies with a canned answer. */
private class RecordingModel(
    private val reply: String,
    private val errorMessage: String? = null,
) : Model {

    override val name: String = "recording-model"

    val requests = mutableListOf<LlmRequest>()

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> {
        requests += request
        return flowOf(
            LlmResponse(
                content = Content(role = Role.MODEL, parts = listOf(Part(text = reply))),
                errorMessage = errorMessage,
            )
        )
    }
}

private fun LlmRequest.texts(): List<String> =
    contents.flatMap { content -> content.parts.mapNotNull { it.text } }
