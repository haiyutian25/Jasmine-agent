package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.memory.InMemoryMemoryService
import com.google.adk.kt.models.Model
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.RequestInputTool
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.lhzkml.jasmine.core.agent.tools.JasmineTools
import com.lhzkml.jasmine.core.agent.tools.generatedTools
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        // ADK marks the end of the turn itself: the closing text event is its
        // `isFinalResponse`, and that is what carries ChatEvent.Completed. The caller
        // no longer posts an end-of-turn action of its own.
        assertEquals(1, events.count { it is ChatEvent.Completed })
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

    @Test
    fun `declared tools reach the model`() = runTest {
        val model = RecordingModel(reply = "hi")
        val chat = AdkAgentChat(
            sessionService = InMemorySessionService(),
            modelFactory = { _, _ -> model },
            tools = listOf(currentTimeTool()),
        )

        chat.startConversation(
            sessionId = CONVERSATION_ID,
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )
        chat.send("what time is it").toList()

        // Without this the model has no way to know the tool exists.
        val declared = model.requests.single().config.tools
            .orEmpty()
            .flatMap { it.functionDeclarations.orEmpty() }
        assertEquals(listOf("current_time"), declared.map { it.name })
    }

    @Test
    fun `a tool call runs the tool and feeds its result back to the model`() = runTest {
        val model = ToolCallingModel(toolName = "current_time")
        val chat = AdkAgentChat(
            sessionService = InMemorySessionService(),
            modelFactory = { _, _ -> model },
            tools = listOf(currentTimeTool()),
        )
        chat.startConversation(
            sessionId = CONVERSATION_ID,
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )

        val events = chat.send("what time is it").toList()

        // The call and its result are surfaced so the UI can show the agent working.
        assertEquals(
            listOf("current_time"),
            events.filterIsInstance<ChatEvent.ToolCall>().map { it.name },
        )
        val results = events.filterIsInstance<ChatEvent.ToolResult>()
        assertEquals(listOf("current_time"), results.map { it.name })
        assertTrue("the tool produced no output: $results", results.single().result.isNotBlank())

        // The loop closed: ADK re-called the model with the tool's answer appended.
        assertEquals(2, model.requests.size)
        val fedBack = model.requests.last().contents
            .flatMap { it.parts }
            .mapNotNull { it.functionResponse }
        assertEquals(listOf("current_time"), fedBack.map { it.name })

        // …and the model's closing text still reaches the caller.
        assertTrue(
            events.filterIsInstance<ChatEvent.Text>().any { it.text == "done" },
        )
    }

    @Test
    fun `a long-running tool pauses the turn and the answer resumes it`() = runTest {
        val model = RequestInputModel(toolName = "adk_request_input", question = "Which city?")
        val chat = AdkAgentChat(
            sessionService = InMemorySessionService(),
            modelFactory = { _, _ -> model },
            tools = listOf(RequestInputTool()),
        )
        chat.startConversation(
            sessionId = CONVERSATION_ID,
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )

        val paused = chat.send("book me a flight").toList()

        // The turn stops on the prompt: the tool deferred, so ADK ended the turn
        // instead of re-calling the model with an empty result.
        val prompt = paused.filterIsInstance<ChatEvent.UserPromptRequested>().single()
        assertEquals("Which city?", prompt.prompt)
        assertTrue("no options expected for a free-form question", prompt.options.isEmpty())
        // ADK marks the pause as this turn's final response (`isFinalResponse` is true
        // for an event carrying a long-running call), which is how the caller learns
        // the turn ended and can release the composer for the answer.
        assertTrue("the pause was not reported as the end of the turn", paused.any { it is ChatEvent.Completed })
        assertEquals(1, model.requests.size)

        val resumed = chat.respondToPrompts(listOf("Beijing")).toList()

        // The answer went back as the tool's result, correlated by call id.
        assertEquals(2, model.requests.size)
        val fedBack = model.requests.last().contents
            .flatMap { it.parts }
            .mapNotNull { it.functionResponse }
        assertEquals(listOf("adk_request_input"), fedBack.map { it.name })
        assertEquals("Beijing", fedBack.single().response["result"])

        // …and the rest of the turn streamed normally.
        assertTrue(resumed.filterIsInstance<ChatEvent.Text>().any { it.text == "done" })
    }

    @Test
    fun `answering with no prompt pending is rejected`() = runTest {
        val chat = AdkAgentChat(
            sessionService = InMemorySessionService(),
            modelFactory = { _, _ -> RecordingModel(reply = "x") },
        )
        chat.startConversation(
            sessionId = CONVERSATION_ID,
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )

        val failure =
            runCatching { chat.respondToPrompts(listOf("hello")).toList() }.exceptionOrNull()

        assertTrue("expected IllegalStateException, got $failure", failure is IllegalStateException)
    }

    @Test
    fun `a finished turn is handed to long-term memory`() = runTest {
        val memory = InMemoryMemoryService()
        val chat = AdkAgentChat(
            sessionService = InMemorySessionService(),
            modelFactory = { _, _ -> RecordingModel(reply = "Paris is the capital of France") },
            memoryService = memory,
        )
        chat.startConversation(
            sessionId = CONVERSATION_ID,
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )

        chat.send("what is the capital of France").toList()

        // Without ingestion `load_memory` would search an empty store forever.
        val found = memory.searchMemory(appName = "jasmine", userId = "local_user", query = "capital")
        assertTrue("nothing was remembered", found.memories.isNotEmpty())
    }

    @Test
    fun `without a memory service a turn still completes`() = runTest {
        // Memory is optional: the runner defaults it, and a null must not break a turn.
        val model = RecordingModel(reply = "ok")
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

        val events = chat.send("hi").toList()

        assertTrue(events.filterIsInstance<ChatEvent.Text>().any { it.text == "ok" })
    }

    @Test
    fun `a streamed answer is not repeated by its settled event`() = runTest {
        // ADK's SSE mode has the model stream deltas and then the aggregator repeat the
        // whole answer on one settled event. Taking text from both would print it twice.
        val chat = AdkAgentChat(
            sessionService = InMemorySessionService(),
            modelFactory = { _, _ -> StreamingModel() },
        )
        chat.startConversation(
            sessionId = CONVERSATION_ID,
            provider = PROVIDER,
            modelId = "test-model",
            instruction = "be nice",
        )

        val events = chat.send("ping").toList()

        assertEquals(
            listOf("Hel", "lo"),
            events.filterIsInstance<ChatEvent.Text>().map { it.text },
        )
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
        // An error is not streamed; a normal answer is, in the shape ADK's SSE mode
        // produces.
        return if (errorMessage != null) {
            flowOf(LlmResponse(errorMessage = errorMessage))
        } else {
            streamedAnswer(reply)
        }
    }
}

/**
 * One streamed answer in the shape ADK's SSE mode produces: a delta carrying the text,
 * then the settled event the aggregator emits with the whole answer.
 */
private fun streamedAnswer(text: String): Flow<LlmResponse> =
    flowOf(
        LlmResponse(
            content = Content(role = Role.MODEL, parts = listOf(Part(text = text))),
            partial = true,
        ),
        LlmResponse(
            content = Content(role = Role.MODEL, parts = listOf(Part(text = text))),
            partial = false,
        ),
    )

/**
 * Calls [toolName] on the first turn and answers in text once the tool has run —
 * which is what makes the runner's tool loop observable.
 */
private class ToolCallingModel(private val toolName: String) : Model {

    override val name: String = "tool-calling-model"

    val requests = mutableListOf<LlmRequest>()

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> {
        requests += request
        val toolAlreadyRan = request.contents
            .flatMap { it.parts }
            .any { it.functionResponse != null }
        return if (toolAlreadyRan) {
            streamedAnswer("done")
        } else {
            // A tool call arrives on a settled event, not as a delta.
            flowOf(
                LlmResponse(
                    content = Content(
                        role = Role.MODEL,
                        parts = listOf(Part(functionCall = FunctionCall(name = toolName, args = emptyMap()))),
                    )
                )
            )
        }
    }
}

/**
 * Asks [question] through a long-running tool, then answers in text once the tool
 * has a response — the shape a paused-and-resumed turn has in practice.
 *
 * The call carries an explicit id because that is what ADK correlates the injected
 * answer against, and what a real provider supplies.
 */
private class RequestInputModel(
    private val toolName: String,
    private val question: String,
) : Model {

    override val name: String = "request-input-model"

    val requests = mutableListOf<LlmRequest>()

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> {
        requests += request
        val answered = request.contents
            .flatMap { it.parts }
            .any { it.functionResponse != null }
        return if (answered) {
            streamedAnswer("done")
        } else {
            flowOf(
                LlmResponse(
                    content = Content(
                        role = Role.MODEL,
                        parts = listOf(
                            Part(
                                functionCall = FunctionCall(
                                    name = toolName,
                                    args = mapOf("message" to question),
                                    id = CALL_ID,
                                )
                            )
                        ),
                    )
                )
            )
        }
    }

    private companion object {
        const val CALL_ID = "call-1"
    }
}

/**
 * Streams two deltas and then the settled aggregate — the shape ADK's SSE mode produces
 * for a model that streams.
 */
private class StreamingModel : Model {
    override val name = "streaming-model"

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> =
        flowOf(
            LlmResponse(
                content = Content(role = Role.MODEL, parts = listOf(Part(text = "Hel"))),
                partial = true,
            ),
            LlmResponse(
                content = Content(role = Role.MODEL, parts = listOf(Part(text = "lo"))),
                partial = true,
            ),
            LlmResponse(
                content = Content(role = Role.MODEL, parts = listOf(Part(text = "Hello"))),
                partial = false,
            ),
        )
}

private fun LlmRequest.texts(): List<String> =
    contents.flatMap { content -> content.parts.mapNotNull { it.text } }

/**
 * The app's own current-time tool, taken from the set ADK's KSP processor generated —
 * the same way `AgentModule` hands tools to the agent.
 */
private fun currentTimeTool(): BaseTool =
    JasmineTools(NoConversationsStore()).generatedTools().first { it.name == "current_time" }

/**
 * A store holding nothing. These tests exercise the runner, not the listing tool, and
 * the current-time tool never reads it.
 */
private class NoConversationsStore : ConversationStore {
    override val conversationsStateFlow = MutableStateFlow<List<Conversation>>(emptyList())

    override suspend fun refresh() = Unit

    override suspend fun latestConversation(): Conversation? = null

    override suspend fun messagesOf(conversationId: String): List<TranscriptMessage> = emptyList()

    override suspend fun createConversation(
        providerId: String,
        modelId: String,
        title: String,
    ): Conversation = error("not used by these tests")

    override suspend fun deleteConversation(id: String) = Unit
}