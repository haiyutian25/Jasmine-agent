package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
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
 * The model's context comes entirely from the session store, so these tests
 * assert what the runner reads back out of it by inspecting the `LlmRequest` it
 * builds — no network, no API key.
 *
 * Sessions are exercised through a real [InMemorySessionService]; the app injects
 * ADK's Room-backed one, and the resume path is identical either way (the service
 * interface is the seam).
 */
class AdkAgentChatTest {

    @Test
    fun `a new session starts with no context`() = runTest {
        val model = RecordingModel(reply = "hi")
        val chat = AdkAgentChat(
            sessionService = InMemorySessionService(),
            modelFactory = { _, _ -> model },
        )

        chat.startConversation(
            sessionId = CONVERSATION_ID,
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )
        chat.send("only message").toList()

        assertEquals(listOf("only message"), model.requests.single().texts())
    }

    @Test
    fun `a stored conversation is resumed with its own context`() = runTest {
        val sessionService = InMemorySessionService()
        val first = RecordingModel(reply = "first answer")
        val chat = AdkAgentChat(sessionService = sessionService, modelFactory = { _, _ -> first })

        chat.startConversation(
            sessionId = CONVERSATION_ID,
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )
        chat.send("first question").toList()
        chat.endConversation()

        // A fresh facade over the same store models an app restart: the earlier
        // turns must come back from the session, not from anywhere else.
        val second = RecordingModel(reply = "second answer")
        val resumed = AdkAgentChat(sessionService = sessionService, modelFactory = { _, _ -> second })
        resumed.startConversation(
            sessionId = CONVERSATION_ID,
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )
        resumed.send("second question").toList()

        val texts = second.requests.single().texts()
        assertTrue("stored context was not restored: $texts", texts.contains("first question"))
        assertTrue("stored context was not restored: $texts", texts.contains("first answer"))
        assertTrue("new turn missing: $texts", texts.contains("second question"))
    }

    @Test
    fun `a different conversation does not inherit another one's context`() = runTest {
        val sessionService = InMemorySessionService()
        val first = RecordingModel(reply = "first answer")
        val chat = AdkAgentChat(sessionService = sessionService, modelFactory = { _, _ -> first })

        chat.startConversation(
            sessionId = "conversation-1",
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )
        chat.send("secret").toList()
        chat.endConversation()

        val second = RecordingModel(reply = "clean answer")
        val other = AdkAgentChat(sessionService = sessionService, modelFactory = { _, _ -> second })
        other.startConversation(
            sessionId = "conversation-2",
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )
        other.send("fresh question").toList()

        val texts = second.requests.single().texts()
        assertFalse("context leaked between conversations: $texts", texts.contains("secret"))
    }

    @Test
    fun `ending a conversation does not erase the stored session`() = runTest {
        val sessionService = InMemorySessionService()
        val model = RecordingModel(reply = "ok")
        val chat = AdkAgentChat(sessionService = sessionService, modelFactory = { _, _ -> model })

        chat.startConversation(
            sessionId = CONVERSATION_ID,
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )
        chat.send("remember me").toList()
        chat.endConversation()

        val events = sessionService.listEvents(sessionKey()).events
        assertTrue("the session was dropped on endConversation", events.isNotEmpty())
    }

    @Test
    fun `assistant text is streamed back to the caller`() = runTest {
        val model = RecordingModel(reply = "pong")
        val chat = AdkAgentChat(
            sessionService = InMemorySessionService(),
            modelFactory = { _, _ -> model },
        )
        chat.startConversation(
            sessionId = CONVERSATION_ID,
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )

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
        val chat = AdkAgentChat(
            sessionService = InMemorySessionService(),
            modelFactory = { _, _ -> model },
        )
        chat.startConversation(
            sessionId = CONVERSATION_ID,
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )

        val events = chat.send("ping").toList()

        assertEquals(
            listOf("HTTP 401: bad key"),
            events.filterIsInstance<ChatEvent.Failed>().map { it.detail },
        )
    }

    @Test
    fun `sending before starting a conversation is rejected`() = runTest {
        val chat = AdkAgentChat(
            sessionService = InMemorySessionService(),
            modelFactory = { _, _ -> RecordingModel(reply = "x") },
        )

        val failure = runCatching { chat.send("hi").toList() }.exceptionOrNull()

        assertTrue("expected IllegalStateException, got $failure", failure is IllegalStateException)
    }

    private fun sessionKey() = SessionKey(
        appName = "jasmine",
        userId = "local_user",
        id = CONVERSATION_ID,
    )

    private companion object {
        const val CONVERSATION_ID = "conversation-1"

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