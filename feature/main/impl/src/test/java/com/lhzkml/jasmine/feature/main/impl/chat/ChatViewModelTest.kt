package com.lhzkml.jasmine.feature.main.impl.chat

import com.lhzkml.jasmine.core.agent.AgentChat
import com.lhzkml.jasmine.core.agent.ChatEvent
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.ModelConfig
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import com.lhzkml.jasmine.core.data.model.UserPreferences
import com.lhzkml.jasmine.core.data.repository.ChatHistoryRepository
import com.lhzkml.jasmine.core.data.repository.ProviderRepository
import com.lhzkml.jasmine.core.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Chat state-machine tests. The network is replaced by [FakeAgentChat], the
 * repositories by in-memory fakes, so these cover the ViewModel's own logic:
 * message assembly, streaming appends, failure handling, transcript persistence,
 * restore and conversation switching.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var providerRepository: FakeProviderRepository
    private lateinit var preferencesRepository: FakeUserPreferencesRepository
    private lateinit var historyRepository: FakeChatHistoryRepository
    private lateinit var agentChat: FakeAgentChat

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        providerRepository = FakeProviderRepository(listOf(PROVIDER))
        preferencesRepository =
            FakeUserPreferencesRepository(
                UserPreferences.DEFAULT.copy(
                    activeProviderId = PROVIDER.id,
                    activeModelId = MODEL_ID,
                )
            )
        historyRepository = FakeChatHistoryRepository()
        agentChat = FakeAgentChat()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Selection & basic flow ─────────────────────────────────────────

    @Test
    fun `state mirrors the selected provider and model`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        assertEquals(PROVIDER.id, state.activeProviderId)
        assertEquals(MODEL_ID, state.activeModelId)
        assertEquals("deepseek-chat", state.activeModel?.modelId)
        assertTrue(state.isReady)
    }

    @Test
    fun `sending assembles the transcript and appends streamed chunks`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            agentChat.nextEvents = listOf(
                ChatEvent.Text("Hel"),
                ChatEvent.Text("lo"),
                ChatEvent.Completed,
            )

            viewModel.trySendAction(ChatAction.InputChanged("hi"))
            viewModel.trySendAction(ChatAction.SendClicked)
            advanceUntilIdle()

            val messages = viewModel.stateFlow.value.messages
            assertEquals(2, messages.size)
            assertEquals(ChatRole.USER, messages[0].role)
            assertEquals("hi", messages[0].text)
            assertEquals(ChatRole.ASSISTANT, messages[1].role)
            assertEquals("Hello", messages[1].text)
            assertFalse(messages[1].isStreaming)
            assertFalse(messages[1].isError)

            assertEquals(listOf("hi"), agentChat.sent)
            assertEquals(1, agentChat.conversationsStarted)
            assertEquals("", viewModel.stateFlow.value.input)
            assertFalse(viewModel.stateFlow.value.isSending)
        }

    @Test
    fun `the turn ends even when the agent never reports completion`() =
        runTest(testDispatcher) {
            // ADK does not reliably set turnComplete on the final event, so the
            // ViewModel must unblock the composer when the flow ends regardless.
            val viewModel = createViewModel()
            advanceUntilIdle()
            agentChat.nextEvents = listOf(ChatEvent.Text("done"))

            viewModel.trySendAction(ChatAction.InputChanged("hi"))
            viewModel.trySendAction(ChatAction.SendClicked)
            advanceUntilIdle()

            val state = viewModel.stateFlow.value
            assertFalse(state.isSending)
            assertFalse(state.messages.last().isStreaming)
            assertEquals("done", state.messages.last().text)
        }

    @Test
    fun `a failed turn is surfaced on the assistant message`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        agentChat.nextEvents = listOf(ChatEvent.Text("partial"), ChatEvent.Failed("HTTP 401"))

        viewModel.trySendAction(ChatAction.InputChanged("hi"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        val reply = viewModel.stateFlow.value.messages.last()
        assertTrue(reply.isError)
        assertTrue(reply.text.startsWith("partial"))
        assertTrue(reply.text.endsWith("HTTP 401"))
        assertFalse(viewModel.stateFlow.value.isSending)
    }

    @Test
    fun `consecutive sends reuse the same conversation`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.trySendAction(ChatAction.InputChanged("one"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()
        viewModel.trySendAction(ChatAction.InputChanged("two"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        assertEquals(1, agentChat.conversationsStarted)
        assertEquals(listOf("one", "two"), agentChat.sent)
        assertEquals(4, viewModel.stateFlow.value.messages.size)
        assertEquals(1, historyRepository.created.size)
    }

    @Test
    fun `selecting another model starts over and is persisted`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.trySendAction(ChatAction.InputChanged("hi"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        viewModel.trySendAction(ChatAction.ModelSelected(PROVIDER.id, "model-2"))
        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        assertEquals("model-2", state.activeModelId)
        // The transcript survives a model switch — it is replayed into the new session.
        assertEquals(2, state.messages.size)
        assertFalse(state.isModelPickerOpen)
        assertEquals(listOf(PROVIDER.id to "model-2"), preferencesRepository.activeModelUpdates)
        assertEquals(1, agentChat.conversationsEnded)
    }

    @Test
    fun `reselecting the active model only closes the picker`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.trySendAction(ChatAction.InputChanged("hi"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        viewModel.trySendAction(ChatAction.ModelSelected(PROVIDER.id, MODEL_ID))
        advanceUntilIdle()

        assertEquals(2, viewModel.stateFlow.value.messages.size)
        assertTrue(preferencesRepository.activeModelUpdates.isEmpty())
        assertEquals(0, agentChat.conversationsEnded)
    }

    @Test
    fun `blank input and missing credentials block sending`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.trySendAction(ChatAction.InputChanged("   "))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()
        assertTrue(agentChat.sent.isEmpty())

        // Same provider, but without a key the chat is not ready and must stay put.
        providerRepository.providersStateFlow.value = listOf(PROVIDER.copy(apiKey = ""))
        advanceUntilIdle()
        assertFalse(viewModel.stateFlow.value.isReady)

        viewModel.trySendAction(ChatAction.InputChanged("hi"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()
        assertTrue(agentChat.sent.isEmpty())
        assertNull(viewModel.stateFlow.value.messages.firstOrNull())
    }

    // ── Transcript persistence ─────────────────────────────────────────

    @Test
    fun `the first send creates the conversation and persists both messages`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            agentChat.nextEvents = listOf(ChatEvent.Text("Hello"), ChatEvent.Completed)

            viewModel.trySendAction(ChatAction.InputChanged("hi"))
            viewModel.trySendAction(ChatAction.SendClicked)
            advanceUntilIdle()

            val created = historyRepository.created.single()
            assertEquals("hi", created.title)
            assertEquals(PROVIDER.id, created.providerId)
            assertEquals(MODEL_ID, created.modelId)
            assertEquals(created.id, viewModel.stateFlow.value.activeConversationId)
            assertEquals(
                listOf(
                    Triple(created.id, ChatRole.USER, "hi"),
                    Triple(created.id, ChatRole.ASSISTANT, "Hello"),
                ),
                historyRepository.appended,
            )
        }

    @Test
    fun `a restored conversation is loaded from the transcript`() =
        runTest(testDispatcher) {
            historyRepository.seedConversation(
                conversationId = "conv-old",
                title = "earlier",
                transcript = listOf(
                    TranscriptMessage(ChatRole.USER, "first question"),
                    TranscriptMessage(ChatRole.ASSISTANT, "first answer"),
                ),
            )
            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.stateFlow.value
            assertEquals("conv-old", state.activeConversationId)
            assertEquals(
                listOf("first question", "first answer"),
                state.messages.map { it.text },
            )

            // Sending only hands over the new turn: the model's earlier context
            // comes from the stored ADK session, not from the transcript.
            viewModel.trySendAction(ChatAction.InputChanged("second question"))
            viewModel.trySendAction(ChatAction.SendClicked)
            advanceUntilIdle()

            assertEquals(listOf("second question"), agentChat.sent)
        }

    @Test
    fun `the agent session is keyed by the persisted conversation id`() = runTest(testDispatcher) {
        // This is what lets a stored ADK session be *resumed* instead of rebuilt:
        // both sides of the conversation agree on one identity, so the model's
        // context and the transcript cannot drift apart.
        historyRepository.seedConversation(
            conversationId = "conv-old",
            title = "earlier",
            transcript = listOf(TranscriptMessage(ChatRole.USER, "first question")),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.trySendAction(ChatAction.InputChanged("follow up"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        assertEquals("conv-old", agentChat.startedWithSessionId)
    }

    @Test
    fun `a brand new conversation starts a session under its own id`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.trySendAction(ChatAction.InputChanged("hello"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        val conversationId = viewModel.stateFlow.value.activeConversationId
        assertTrue("no conversation was persisted", conversationId != null)
        assertEquals(conversationId, agentChat.startedWithSessionId)
    }

    @Test
    fun `a failed reply stays visible in the transcript`() = runTest(testDispatcher) {
        historyRepository.seedConversation(
            conversationId = "conv-old",
            title = "earlier",
            transcript = listOf(
                TranscriptMessage(ChatRole.USER, "question"),
                TranscriptMessage(ChatRole.ASSISTANT, "HTTP 500", isError = true),
            ),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        val messages = viewModel.stateFlow.value.messages
        assertEquals(listOf("question", "HTTP 500"), messages.map { it.text })
        assertTrue("the failed reply lost its error flag", messages.last().isError)
    }

    @Test
    fun `selecting a conversation from history loads its transcript`() =
        runTest(testDispatcher) {
            historyRepository.seedConversation("conv-a", "A", listOf(TranscriptMessage(ChatRole.USER, "from A")))
            historyRepository.seedConversation("conv-b", "B", listOf(TranscriptMessage(ChatRole.USER, "from B")))
            historyRepository.markLatest("conv-a")

            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(listOf("from A"), viewModel.stateFlow.value.messages.map { it.text })

            viewModel.trySendAction(ChatAction.ConversationSelected("conv-b"))
            advanceUntilIdle()

            val state = viewModel.stateFlow.value
            assertEquals("conv-b", state.activeConversationId)
            assertEquals(listOf("from B"), state.messages.map { it.text })
            assertFalse(state.isHistoryOpen)
            // Switching conversations must rebuild the session for the new history.
            assertEquals(1, agentChat.conversationsEnded)
        }

    @Test
    fun `deleting the current conversation clears the transcript`() =
        runTest(testDispatcher) {
            historyRepository.seedConversation("conv-a", "A", listOf(TranscriptMessage(ChatRole.USER, "hi")))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.trySendAction(ChatAction.ConversationDeleted("conv-a"))
            advanceUntilIdle()

            val state = viewModel.stateFlow.value
            assertNull(state.activeConversationId)
            assertTrue(state.messages.isEmpty())
            assertEquals(listOf("conv-a"), historyRepository.deleted)
        }

    @Test
    fun `new conversation keeps the previous transcript in history`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.trySendAction(ChatAction.InputChanged("hi"))
            viewModel.trySendAction(ChatAction.SendClicked)
            advanceUntilIdle()

            viewModel.trySendAction(ChatAction.NewConversationClicked)
            advanceUntilIdle()

            assertTrue(viewModel.stateFlow.value.messages.isEmpty())
            assertNull(viewModel.stateFlow.value.activeConversationId)
            assertEquals(1, historyRepository.created.size)
            assertEquals(1, agentChat.conversationsEnded)
        }

    private fun createViewModel() = ChatViewModel(
        providerRepository = providerRepository,
        userPreferencesRepository = preferencesRepository,
        chatHistoryRepository = historyRepository,
        agentChat = agentChat,
    )

    private companion object {
        const val MODEL_ID = "model-1"

        val PROVIDER = ProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            apiKey = "sk-test",
            apiType = ProviderApiType.CHAT_COMPLETIONS,
            models = listOf(
                ModelConfig(id = MODEL_ID, modelId = "deepseek-chat"),
                ModelConfig(id = "model-2", modelId = "deepseek-reasoner"),
            ),
        )
    }
}

private class FakeProviderRepository(initial: List<ProviderConfig>) : ProviderRepository {
    override val providersStateFlow = MutableStateFlow(initial)
    override suspend fun upsertProvider(provider: ProviderConfig) = Unit
    override suspend fun deleteProvider(id: String) = Unit
    override suspend fun fetchModels(provider: ProviderConfig): List<String> = emptyList()
}

private class FakeUserPreferencesRepository(initial: UserPreferences) : UserPreferencesRepository {
    override val preferencesStateFlow = MutableStateFlow(initial)
    val activeModelUpdates = mutableListOf<Pair<String, String>>()

    override suspend fun updateTheme(themeId: String) = Unit
    override suspend fun updateTypography(typographyChoice: String) = Unit
    override suspend fun updateColorMode(colorMode: String) = Unit
    override suspend fun updateFontScale(fontScale: Float) = Unit
    override suspend fun updateActiveCustomFont(fontId: String) = Unit
    override suspend fun updateActiveModel(providerId: String, modelId: String) {
        activeModelUpdates += providerId to modelId
    }
}

private class FakeChatHistoryRepository : ChatHistoryRepository {
    override val conversationsStateFlow = MutableStateFlow<List<Conversation>>(emptyList())
    val created = mutableListOf<Conversation>()
    val appended = mutableListOf<Triple<String, ChatRole, String>>()
    val deleted = mutableListOf<String>()

    private val transcripts = mutableMapOf<String, MutableList<TranscriptMessage>>()
    private var latest: Conversation? = null

    /** Seeds a stored conversation and makes it the most recent one. */
    fun seedConversation(
        conversationId: String,
        title: String,
        transcript: List<TranscriptMessage>,
    ) {
        val conversation = Conversation(
            id = conversationId,
            title = title,
            providerId = "deepseek",
            modelId = "model-1",
            createdAt = 0,
            updatedAt = 0,
        )
        transcripts[conversationId] = transcript.toMutableList()
        conversationsStateFlow.value = conversationsStateFlow.value + conversation
        latest = conversation
    }

    fun markLatest(conversationId: String) {
        latest = conversationsStateFlow.value.firstOrNull { it.id == conversationId }
    }

    override suspend fun latestConversation(): Conversation? = latest

    override suspend fun messagesOf(conversationId: String): List<TranscriptMessage> =
        transcripts[conversationId].orEmpty().toList()

    override suspend fun createConversation(
        providerId: String,
        modelId: String,
        title: String,
    ): Conversation {
        val conversation = Conversation(
            id = "conv-${created.size + 1}",
            title = title,
            providerId = providerId,
            modelId = modelId,
            createdAt = 0,
            updatedAt = 0,
        )
        created += conversation
        transcripts[conversation.id] = mutableListOf()
        conversationsStateFlow.value = listOf(conversation) + conversationsStateFlow.value
        return conversation
    }

    override suspend fun appendMessage(
        conversationId: String,
        role: ChatRole,
        text: String,
        isError: Boolean,
    ) {
        appended += Triple(conversationId, role, text)
        transcripts.getOrPut(conversationId) { mutableListOf() } +=
            TranscriptMessage(role = role, text = text, isError = isError)
    }

    override suspend fun deleteConversation(id: String) {
        deleted += id
        transcripts.remove(id)
        conversationsStateFlow.value = conversationsStateFlow.value.filterNot { it.id == id }
    }
}

private class FakeAgentChat : AgentChat {
    var nextEvents: List<ChatEvent> = emptyList()
    val sent = mutableListOf<String>()
    var startedWithSessionId: String? = null
    var conversationsStarted = 0
    var conversationsEnded = 0

    override suspend fun startConversation(
        sessionId: String,
        provider: ProviderConfig,
        modelId: String,
        instruction: String,
    ) {
        conversationsStarted++
        startedWithSessionId = sessionId
    }

    override fun send(text: String): Flow<ChatEvent> {
        sent += text
        return nextEvents.asFlow()
    }

    override fun endConversation() {
        conversationsEnded++
    }
}
