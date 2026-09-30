package com.lhzkml.jasmine.feature.main.impl.chat

import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.core.agent.AgentChat
import com.lhzkml.jasmine.core.agent.AppUsage
import com.lhzkml.jasmine.core.agent.ChatEvent
import com.lhzkml.jasmine.core.agent.ContextUsage
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.AgentOutputLanguage
import com.lhzkml.jasmine.core.data.model.CatalogModel
import com.lhzkml.jasmine.core.data.model.ModelConfig
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import com.lhzkml.jasmine.core.data.model.AgentSettings
import com.lhzkml.jasmine.core.data.model.UserPreferences
import com.lhzkml.jasmine.core.agent.ConversationStore
import com.lhzkml.jasmine.core.data.repository.ProviderRepository
import com.lhzkml.jasmine.core.data.repository.UserPreferencesRepository
import com.lhzkml.jasmine.core.markdown.MarkdownParser
import com.lhzkml.jasmine.core.markdown.MarkdownParserFactory
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlock
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlockType
import com.lhzkml.jasmine.core.markdown.model.MarkdownInline
import com.lhzkml.jasmine.core.markdown.model.MarkdownInlineType
import com.lhzkml.jasmine.core.markdown.model.MarkdownUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.After
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
    private lateinit var conversationStore: FakeConversationStore
    private lateinit var agentChat: FakeAgentChat
    private lateinit var markdownParserFactory: FakeMarkdownParserFactory

    /**
     * 最近一次 [createViewModel] 造出来的实例。
     *
     * [tearDown] 必须先取消它的作用域：EffectRunner 与回合协程都活在 `viewModelScope` 里，
     * 不先结束它们就 `resetMain()`，就会重演本文件记录过的那类 bug
     * （`Dispatchers.Main is used concurrently with setting it`）。
     */
    private var viewModel: ChatViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        markdownParserFactory = FakeMarkdownParserFactory()
        providerRepository = FakeProviderRepository(listOf(PROVIDER))
        preferencesRepository =
            FakeUserPreferencesRepository(
                UserPreferences.DEFAULT.copy(
                    activeProviderId = PROVIDER.id,
                    activeModelId = MODEL_ID,
                )
            )
        conversationStore = FakeConversationStore()
        agentChat = FakeAgentChat()
    }

    /**
     * 每个用例跑完把 Main 还原 —— 现在可以这么做了。
     *
     * 以前不行：流式解析的 worker 是随 ViewModel 起的长命协程，`withContext(Dispatchers.Default)`
     * 那一步可能在本用例结束后才回到 Main，于是这批续体在**下一个**用例的 `setUp` 上撞车
     * （`Dispatchers.Main is used concurrently with setting it`）。现在 worker 属于**这一轮**
     * （见 `ChatViewModel.launchTurn`：回合结束关通道、排空、join），不会有一条续体活过这一轮。
     */
    @After
    fun tearDown() {
        // 先结束 EffectRunner / 回合协程，**并等它们真的结束**，再换 Main。
        //
        // 只 cancel 不够：解析 worker 里那段 `withContext(Dispatchers.Default)` 跑在真线程上，取消不会
        // 打断它；它回到 Main 的那一刻如果本用例已经 `resetMain()`，就会报
        // `Dispatchers.Main was accessed when the platform dispatcher was absent…`，并污染下一个用例。
        viewModel?.viewModelScope?.cancel()
        viewModel?.viewModelScope?.coroutineContext?.get(Job)?.let { job ->
            runBlocking { withTimeoutOrNull(5_000) { job.join() } }
        }
        viewModel = null
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

    // ── 推理档位：面板能列哪几档由核心目录说了算 ─────────────────────────

    @Test
    fun `the effort menu keeps only the levels the catalog declares`() =
        runTest(testDispatcher) {
            // 目录给这个模型声明了两档 —— 面板就只列这两档（+「未设置」，在界面那边补）。
            providerRepository = FakeProviderRepository(
                initial = listOf(PROVIDER),
                catalogModels = listOf(
                    CatalogModel(
                        modelId = "deepseek-chat",
                        name = "DeepSeek-Chat",
                        contextLength = 1_000_000,
                        levels = listOf("low", "high"),
                    ),
                ),
            )
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(listOf("low", "high"), viewModel.stateFlow.value.allowedEfforts)
        }

    @Test
    fun `a model the catalog does not know keeps every level`() = runTest(testDispatcher) {
        // 目录里没有这个模型（这里是空目录）→ 不限制：界面按全部档列。
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.stateFlow.value.allowedEfforts.isEmpty())
    }

    @Test
    fun `a gateway id still finds its catalog entry`() = runTest(testDispatcher) {
        // 聚合网关的 id 带 `厂商/` 前缀、免费档还带 `:free` 后缀 —— 归一化之后照样认得目录里那条
        // （付费档没有后缀，走的是同一个前缀规则）。
        val gatewayProvider = PROVIDER.copy(
            models = listOf(
                ModelConfig(id = MODEL_ID, modelId = "deepseek/deepseek-chat:free", reasoningEffort = ""),
            ),
        )
        providerRepository = FakeProviderRepository(
            initial = listOf(gatewayProvider),
            catalogModels = listOf(
                CatalogModel(
                    modelId = "deepseek-chat",
                    name = "DeepSeek-Chat",
                    contextLength = 1_000_000,
                    levels = listOf("low", "high"),
                ),
            ),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("low", "high"), viewModel.stateFlow.value.allowedEfforts)
    }

    // ── 模型回复语言（Agent 设置）：界面只传值，规则在核心 ─────────────────

    @Test
    fun `the reply language reaches the core as a value`() = runTest(testDispatcher) {
        preferencesRepository = FakeUserPreferencesRepository(
            UserPreferences.DEFAULT.copy(
                activeProviderId = PROVIDER.id,
                activeModelId = MODEL_ID,
                agentOutputLanguage = AgentOutputLanguage.TRADITIONAL_CHINESE,
            )
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        agentChat.nextEvents = listOf(ChatEvent.Text("ok"), ChatEvent.Completed)

        viewModel.trySendAction(ChatAction.InputChanged("hi"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        // 界面上只把**值**交出去：语言那条规则由核心按这个值拼（见 Rust 的 agent_settings）。
        val settings = agentChat.settings
        assertTrue(settings != null)
        assertEquals(AgentOutputLanguage.TRADITIONAL_CHINESE, settings?.outputLanguage)
        assertTrue(settings?.appLanguage?.isNotEmpty() == true)
        // 给核心的仍然只有人格，没有语言规则。
        assertFalse(agentChat.instruction.orEmpty().contains("Output language preference"))
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
    fun `tool activity is interleaved with the model's text in order`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        agentChat.nextEvents = listOf(
            ChatEvent.Text("Let me check."),
            ChatEvent.ToolCall("current_time", "—"),
            ChatEvent.ToolResult("current_time", "2026-09-24 09:00"),
            ChatEvent.Text("It is 09:00."),
            ChatEvent.Completed,
        )

        viewModel.trySendAction(ChatAction.InputChanged("what time"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        val messages = viewModel.stateFlow.value.messages
        // user, preamble, call(+result), answer — the order things happened in.
        // 返回并进调用那张卡片，不再单独成条。
        assertEquals(4, messages.size)
        assertEquals("what time", messages[0].text)
        assertEquals("Let me check.", messages[1].text)
        assertEquals("current_time", messages[2].tool?.name)
        assertFalse(messages[2].tool!!.isResultOnly)
        assertEquals("2026-09-24 09:00", messages[2].tool!!.result)
        assertEquals("It is 09:00.", messages[3].text)
        assertFalse(messages[3].isStreaming)
    }

    @Test
    fun `a turn that opens with a tool call leaves no empty bubble`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        // 参数是 `—` 而不是空串：核心对无参调用渲染的就是 `—`，而界面把**空** detail
        // 定义为「只有返回、没有配对调用」，喂空串会让调用卡被当成返回卡、合并不上。
        agentChat.nextEvents = listOf(
            ChatEvent.ToolCall("current_time", "—"),
            ChatEvent.ToolResult("current_time", "09:00"),
            ChatEvent.Text("It is 09:00."),
            ChatEvent.Completed,
        )

        viewModel.trySendAction(ChatAction.InputChanged("what time"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        // The placeholder created on send is dropped rather than sealed, so the trace
        // does not start with a blank assistant bubble.
        val messages = viewModel.stateFlow.value.messages
        assertEquals(3, messages.size)
        assertTrue(messages.none { it.tool == null && it.text.isEmpty() })
    }

    @Test
    fun `the persisted reply joins the segments and leaves the trace out`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            agentChat.nextEvents = listOf(
                ChatEvent.Text("Let me check."),
                ChatEvent.ToolCall("current_time", ""),
                ChatEvent.ToolResult("current_time", "2026-09-24 09:00"),
                ChatEvent.Text("It is 09:00."),
                ChatEvent.Completed,
            )

            viewModel.trySendAction(ChatAction.InputChanged("what time"))
            viewModel.trySendAction(ChatAction.SendClicked)
            advanceUntilIdle()

            // Nothing is written from here: the runner appends the user message and the
            // model's reply to the session as the turn runs. All this layer does is open
            // the conversation.
            assertEquals(1, conversationStore.created.size)
        }

    @Test
    fun `a question is surfaced and leaves no empty bubble behind`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        agentChat.nextEvents = listOf(
            ChatEvent.Text("I need one thing."),
            ChatEvent.UserPromptRequested("Which city?", emptyList()),
            // ADK reports a pause as this turn's final response, and that is what
            // releases the composer while the question is on screen.
            ChatEvent.Completed,
        )

        viewModel.trySendAction(ChatAction.InputChanged("book a flight"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        assertEquals("Which city?", state.pendingPrompt?.prompt)
        assertTrue(state.pendingPrompt!!.options.isEmpty())
        // The turn is paused, not running, so the composer is released.
        assertFalse(state.isSending)
        assertTrue(state.messages.none { it.tool == null && it.text.isEmpty() })
    }

    @Test
    fun `answering resumes the turn and streams the rest`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        agentChat.nextEvents = listOf(
            ChatEvent.UserPromptRequested("Pick one", listOf("Beijing", "Shanghai")),
            ChatEvent.Completed,
        )

        viewModel.trySendAction(ChatAction.InputChanged("book a flight"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()
        assertEquals(
            listOf("Beijing", "Shanghai"),
            viewModel.stateFlow.value.pendingPrompt?.options,
        )

        agentChat.nextPromptEvents = listOf(ChatEvent.Text("Booked."), ChatEvent.Completed)
        viewModel.trySendAction(ChatAction.PromptAnswered("Beijing"))
        advanceUntilIdle()

        assertEquals(listOf("Beijing"), agentChat.answered)
        val state = viewModel.stateFlow.value
        assertNull(state.pendingPrompt)
        assertFalse(state.isSending)
        assertEquals("Booked.", state.messages.last().text)
    }

    @Test
    fun `answering with nothing pending does nothing`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.trySendAction(ChatAction.PromptAnswered("hello"))
        advanceUntilIdle()

        assertTrue(agentChat.answered.isEmpty())
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
        // Each turn ends on the agent's own completion marker.
        agentChat.nextEvents = listOf(ChatEvent.Completed)

        viewModel.trySendAction(ChatAction.InputChanged("one"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()
        viewModel.trySendAction(ChatAction.InputChanged("two"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        assertEquals(1, agentChat.conversationsStarted)
        assertEquals(listOf("one", "two"), agentChat.sent)
        assertEquals(4, viewModel.stateFlow.value.messages.size)
        assertEquals(1, conversationStore.created.size)
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
    fun `the first send creates the conversation`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            agentChat.nextEvents = listOf(ChatEvent.Text("Hello"), ChatEvent.Completed)

            viewModel.trySendAction(ChatAction.InputChanged("hi"))
            viewModel.trySendAction(ChatAction.SendClicked)
            advanceUntilIdle()

            val created = conversationStore.created.single()
            assertEquals("hi", created.title)
            assertEquals(PROVIDER.id, created.providerId)
            // 会话文件里存的是**线上模型名**（与 attach 时交给核心的一致），不是模型条目的 id。
            assertEquals(PROVIDER.models.first().modelId, created.modelId)
            assertEquals(created.id, viewModel.stateFlow.value.activeConversationId)
        }

    @Test
    fun `a restored conversation is loaded from the transcript`() =
        runTest(testDispatcher) {
            conversationStore.seedConversation(
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
        conversationStore.seedConversation(
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
        conversationStore.seedConversation(
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
            conversationStore.seedConversation("conv-a", "A", listOf(TranscriptMessage(ChatRole.USER, "from A")))
            conversationStore.seedConversation("conv-b", "B", listOf(TranscriptMessage(ChatRole.USER, "from B")))
            conversationStore.markLatest("conv-a")

            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(listOf("from A"), viewModel.stateFlow.value.messages.map { it.text })

            viewModel.trySendAction(ChatAction.ConversationSelected("conv-b"))
            advanceUntilIdle()

            val state = viewModel.stateFlow.value
            assertEquals("conv-b", state.activeConversationId)
            assertEquals(listOf("from B"), state.messages.map { it.text })
            // Switching conversations must rebuild the session for the new history.
            assertEquals(1, agentChat.conversationsEnded)
        }

    /**
     * 抽屉那一片是 ChatViewModel **派生**出来的（P1-7）。
     *
     * 钉住两件事：(a) 投影按仓库给的顺序映射出标题与当前高亮；(b) 流式输出**不移动它** ——
     * 分片只改当前会话的消息，不改 `conversations` / `activeConversationId`，派生值相等，
     * 抽屉不该跟着分片重组（界面以前是自己 map + `distinctUntilChanged` 自救的）。
     */
    @Test
    fun `the sidebar projection follows the conversation list, not the streaming text`() =
        runTest(testDispatcher) {
            conversationStore.seedConversation(
                "conv-a",
                "A",
                listOf(TranscriptMessage(ChatRole.USER, "from A")),
            )
            conversationStore.seedConversation(
                "conv-b",
                "B",
                listOf(TranscriptMessage(ChatRole.USER, "from B")),
            )
            conversationStore.markLatest("conv-a")

            val viewModel = createViewModel()
            advanceUntilIdle()

            val before = viewModel.sidebarState.value
            assertEquals(listOf("conv-a", "conv-b"), before.conversations.map { it.id })
            assertEquals(listOf("A", "B"), before.conversations.map { it.title })
            assertEquals("conv-a", before.activeConversationId)

            agentChat.nextEvents = listOf(ChatEvent.Text("一"), ChatEvent.Text("二"), ChatEvent.Completed)
            viewModel.trySendAction(ChatAction.InputChanged("hi"))
            viewModel.trySendAction(ChatAction.SendClicked)
            advanceUntilIdle()

            assertTrue("这一轮应当已经把正文写进消息里", viewModel.stateFlow.value.messages.isNotEmpty())
            assertEquals("流式输出不该移动抽屉那一片", before, viewModel.sidebarState.value)
        }

    /**
     * 中途换会话：旧回合**照旧跑完**（它写的是自己那条会话的文件），但它的事件不能再往界面上落 ——
     * 否则切过去之后，新会话里会冒出别人的工具卡和正文（工具卡 / 思考段找不到目标时会现造一条消息）。
     */
    @Test
    fun `a turn left behind writes nothing into the conversation opened next`() =
        runTest(testDispatcher) {
            conversationStore.seedConversation(
                "conv-a",
                "A",
                listOf(TranscriptMessage(ChatRole.USER, "from A")),
            )
            conversationStore.seedConversation(
                "conv-b",
                "B",
                listOf(TranscriptMessage(ChatRole.USER, "from B")),
            )
            // 启动时恢复的是 A：这一轮要发在 A 上，然后才切到 B。
            conversationStore.markLatest("conv-a")

            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals("conv-a", viewModel.stateFlow.value.activeConversationId)

            agentChat.nextEvents = listOf(ChatEvent.Text("先说一句"))
            agentChat.hangAfterEvents = true
            agentChat.eventsAfterInterrupt = listOf(
                ChatEvent.ToolCall("current_time", "—"),
                ChatEvent.ToolResult("current_time", "2026-09-30 09:00"),
                ChatEvent.Text("再说一句"),
                ChatEvent.Reasoning("想想"),
            )
            viewModel.trySendAction(ChatAction.InputChanged("几点"))
            viewModel.trySendAction(ChatAction.SendClicked)
            advanceUntilIdle()
            assertTrue(
                "旧回合的正文应当落在自己那条会话上",
                viewModel.stateFlow.value.messages.any { it.text.contains("先说一句") },
            )

            // 换到另一条会话，然后让旧回合把剩下的事件吐完。
            viewModel.trySendAction(ChatAction.ConversationSelected("conv-b"))
            advanceUntilIdle()
            viewModel.trySendAction(ChatAction.StopClicked)
            advanceUntilIdle()

            val messages = viewModel.stateFlow.value.messages
            assertEquals(listOf("from B"), messages.map { it.text })
            assertTrue(
                "新会话里不该出现旧回合的工具卡：" + messages.map { it.tool?.name },
                messages.none { it.tool != null },
            )
            assertTrue(
                "新会话里不该出现旧回合的思考：" + messages.map { it.thinking },
                messages.none { it.thinking.isNotEmpty() },
            )
        }

    /**
     * 回复还在跑的时候新建对话、又马上切回原来那条：这条会话的转写必须照常显示（以前是整屏空白，
     * 而且要离开再回来才恢复）。
     */
    @Test
    fun `switching back to a conversation whose answer is still running shows its transcript`() =
        runTest(testDispatcher) {
            conversationStore.seedConversation(
                "conv-a",
                "A",
                listOf(TranscriptMessage(ChatRole.USER, "from A")),
            )
            conversationStore.seedConversation(
                "conv-b",
                "B",
                listOf(TranscriptMessage(ChatRole.USER, "from B")),
            )
            conversationStore.markLatest("conv-a")

            val viewModel = createViewModel()
            advanceUntilIdle()

            agentChat.nextEvents = listOf(ChatEvent.Text("先说一句"))
            agentChat.hangAfterEvents = true
            viewModel.trySendAction(ChatAction.InputChanged("几点"))
            viewModel.trySendAction(ChatAction.SendClicked)
            advanceUntilIdle()

            // 新建对话 → 立刻切回原来那条（它的回合还在跑）。
            viewModel.trySendAction(ChatAction.NewConversationClicked)
            advanceUntilIdle()
            viewModel.trySendAction(ChatAction.ConversationSelected("conv-a"))
            advanceUntilIdle()

            val state = viewModel.stateFlow.value
            assertEquals("conv-a", state.activeConversationId)
            assertTrue(
                "切回来应当看到这条会话的转写，实际是：" + state.messages.map { it.text },
                state.messages.any { it.text == "from A" },
            )
        }

    /**
     * 两条会话**同时各跑各的**（照 ZCode 的多 topic 运行时）。
     *
     * A 回复到一半时切到 B、在 B 里发一条：B 不用等 A 那一轮跑完（核心那边一条会话一份附着），
     * 两句回答各进各的会话；A 那一轮也不被顶掉、不被掐断（它的游标与解析 worker 跟着它自己）。
     */
    @Test
    fun `two conversations run their own turns at the same time`() = runTest(testDispatcher) {
        conversationStore.seedConversation(
            "conv-a",
            "A",
            listOf(TranscriptMessage(ChatRole.USER, "from A")),
        )
        conversationStore.seedConversation(
            "conv-b",
            "B",
            listOf(TranscriptMessage(ChatRole.USER, "from B")),
        )
        conversationStore.markLatest("conv-a")

        val viewModel = createViewModel()
        advanceUntilIdle()

        // A：发一条，正文来一段之后挂住 —— 这一轮还在跑。
        agentChat.nextEvents = listOf(ChatEvent.Text("A 讲到一半"))
        agentChat.hangAfterEvents = true
        viewModel.trySendAction(ChatAction.InputChanged("第一题"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()
        assertTrue("A 那一轮应该在跑", viewModel.stateFlow.value.isSending)

        // 切到 B，在 B 里发一条：它不等 A 那一轮。
        viewModel.trySendAction(ChatAction.ConversationSelected("conv-b"))
        advanceUntilIdle()
        agentChat.nextEvents = listOf(ChatEvent.Text("B 的回答"), ChatEvent.Completed)
        agentChat.hangAfterEvents = false
        viewModel.trySendAction(ChatAction.InputChanged("第二题"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        // 各附着各的、各发各的：两条消息没有挤到同一条会话上。
        assertEquals(listOf("conv-a", "conv-b"), agentChat.sentSessions)
        assertEquals(listOf("第一题", "第二题"), agentChat.sent)
        assertEquals("两条会话各附着一次", 2, agentChat.conversationsStarted)

        // B 上只有 B 那一轮，而且已经答完。
        val b = viewModel.stateFlow.value
        assertEquals(listOf("from B", "第二题", "B 的回答"), b.messages.map { it.text })
        assertFalse("B 这一轮已经答完", b.isSending)

        // A 那一轮还在跑，切回去看它的那一半。
        viewModel.trySendAction(ChatAction.ConversationSelected("conv-a"))
        advanceUntilIdle()
        val a = viewModel.stateFlow.value
        assertEquals(listOf("from A", "第一题", "A 讲到一半"), a.messages.map { it.text })
        assertTrue("A 那一轮还在跑", a.isSending)
    }

    /**
     * 切走之后这一轮**继续输出**的那一段，也要落进它自己那条会话（核心一直在正常输出、文件里也是
     * 完整的，丢的只是显示）。
     *
     * 以前回合级的每一步读的都是"界面正显示的那条会话"的消息列表：切走之后读到的成了**别人的**，
     * 于是正文只累到最新一个分片、解析结果整批被丢掉 —— 切回来永远停在切换那一刻，重启读文件才对上。
     */
    @Test
    fun `a reply that keeps arriving after a switch stays in its own conversation`() =
        runTest(testDispatcher) {
            conversationStore.seedConversation(
                "conv-a",
                "A",
                listOf(TranscriptMessage(ChatRole.USER, "from A")),
            )
            conversationStore.seedConversation(
                "conv-b",
                "B",
                listOf(TranscriptMessage(ChatRole.USER, "from B")),
            )
            conversationStore.markLatest("conv-a")

            val viewModel = createViewModel()
            advanceUntilIdle()

            agentChat.nextEvents = listOf(ChatEvent.Text("切换前"))
            agentChat.hangAfterEvents = true
            viewModel.trySendAction(ChatAction.InputChanged("写一段"))
            viewModel.trySendAction(ChatAction.SendClicked)
            advanceUntilIdle()

            // 走开到另一条会话，原来那一轮继续在后台输出（没有中断、也没有结束）。
            viewModel.trySendAction(ChatAction.ConversationSelected("conv-b"))
            advanceUntilIdle()
            agentChat.emitWhileHanging(ChatEvent.Text("，切换后"))
            advanceUntilIdle()
            agentChat.emitWhileHanging(ChatEvent.Reasoning("切换后的思考"))
            advanceUntilIdle()

            val shown = viewModel.stateFlow.value
            assertTrue(
                "界面上这条会话不该混进别人那一轮的输出：" + shown.messages.map { it.text },
                shown.messages.none { it.text.contains("切换后") },
            )
            assertTrue(
                "界面上这条会话不该混进别人那一轮的思考：" + shown.messages.map { it.thinking },
                shown.messages.none { it.thinking.contains("切换后") },
            )

            // 切回来：切换之后才到的那一段必须在，正文是接上的。
            viewModel.trySendAction(ChatAction.ConversationSelected("conv-a"))
            advanceUntilIdle()
            val reply = viewModel.stateFlow.value.messages.lastOrNull { it.role == ChatRole.ASSISTANT }
            assertNotNull("切回来应当看到这条会话自己的回复", reply)
            assertEquals("切换前，切换后", reply!!.text)
            assertEquals("切换后的思考", reply.thinking)
        }

    @Test
    fun `reselecting the current conversation rebuilds nothing`() = runTest(testDispatcher) {
        // 侧边栏里点当前这条路：抽屉由 UI 关，ViewModel 不该重建 session、
        // 也不该重读转写。
        conversationStore.seedConversation("conv-a", "A", listOf(TranscriptMessage(ChatRole.USER, "hi")))
        val viewModel = createViewModel()
        advanceUntilIdle()

        val current = viewModel.stateFlow.value.activeConversationId
        assertNotNull(current)

        viewModel.trySendAction(ChatAction.ConversationSelected(current!!))
        advanceUntilIdle()

        assertEquals(current, viewModel.stateFlow.value.activeConversationId)
        assertEquals(listOf("hi"), viewModel.stateFlow.value.messages.map { it.text })
        assertEquals(0, agentChat.conversationsEnded)
    }

    @Test
    fun `stopping mid-reply keeps the partial text and offers to continue`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // 流式来一段之后挂住：回合仍在进行中，不会发 Completed。
            agentChat.hangAfterEvents = true
            agentChat.nextEvents = listOf(ChatEvent.Text("half a reply"))
            viewModel.trySendAction(ChatAction.InputChanged("hi"))
            viewModel.trySendAction(ChatAction.SendClicked)
            advanceUntilIdle()
            assertTrue("the turn should still be running", viewModel.stateFlow.value.isSending)

            viewModel.trySendAction(ChatAction.StopClicked)
            advanceUntilIdle()

            val state = viewModel.stateFlow.value
            assertEquals("中断命令应该送到核心一次", 1, agentChat.interruptCalls)
            // 半段留在界面上；它后面跟一行「你在 4秒 后停止了」的状态（那行没有正文）。
            assertEquals(
                "消息快照（text / stoppedAfterMs）= " +
                    state.messages.map { it.text to it.stoppedAfterMs },
                listOf("hi", "half a reply", ""),
                state.messages.map { it.text },
            )
            assertEquals(4_000L, state.messages.last().stoppedAfterMs)
            // composer 释放，不卡在停止态
            assertFalse(state.isSending)
            // 中断送到了核心：半段由核心按条目写进会话文件，平台不再补写
            assertTrue(agentChat.interrupted)
            // 发送键换成「继续」
            assertTrue(state.canContinue)
        }

    @Test
    fun `deleting the current conversation clears the transcript`() =
        runTest(testDispatcher) {
            conversationStore.seedConversation("conv-a", "A", listOf(TranscriptMessage(ChatRole.USER, "hi")))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.trySendAction(ChatAction.ConversationDeleted("conv-a"))
            advanceUntilIdle()

            val state = viewModel.stateFlow.value
            assertNull(state.activeConversationId)
            assertTrue(state.messages.isEmpty())
            assertEquals(listOf("conv-a"), conversationStore.deleted)
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
            assertEquals(1, conversationStore.created.size)
            assertEquals(1, agentChat.conversationsEnded)
        }

    // ── 严格 UDF（修复方案 v2）：异步结果只经 action 回流；过期结果按身份键丢弃 ──────────

    /**
     * 收集一次性事件（单消费者）。
     *
     * 必须挂在 [TestScope.backgroundScope] 上：`eventFlow` 的收集是**长命**的，挂在测试体自己的作用域里
     * 会让 `runTest` 等它结束（`UncompletedCoroutinesError`）。
     */
    private fun TestScope.eventsOf(viewModel: ChatViewModel): MutableList<ChatUiEvent> {
        val events = mutableListOf<ChatUiEvent>()
        backgroundScope.launch { viewModel.eventFlow.collect { events += it } }
        return events
    }

    @Test
    fun `stale conversation facts are dropped`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val before = viewModel.stateFlow.value

        viewModel.trySendAction(
            ChatAction.Internal.ConversationFactsLoaded(
                ConversationFacts(
                    conversationId = "conv-elsewhere",
                    window = 4096L,
                    usage = null,
                    effort = "high",
                )
            )
        )
        advanceUntilIdle()

        // 这条会话不是当前打开的那条 → 一个字段都不许改（守卫在 handler 里，同步）。
        val after = viewModel.stateFlow.value
        assertEquals(before.contextWindow, after.contextWindow)
        assertEquals(before.reasoningEffort, after.reasoningEffort)
    }

    @Test
    fun `selecting a conversation loads its facts and transcript`() = runTest(testDispatcher) {
        // 生产路径：打开另一条会话 → 消息、窗口、档位都从它的文件里读回来，再经 action 落状态。
        conversationStore.seedConversation("conv-1", "older", emptyList())
        conversationStore.seedConversation("conv-2", "newer", emptyList()) // latest
        agentChat.conversationContextWindows["conv-2"] = 200_000L
        agentChat.conversationContextWindows["conv-1"] = 777L
        agentChat.sessionEffort = null
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals("conv-2", viewModel.stateFlow.value.activeConversationId)

        viewModel.trySendAction(ChatAction.ConversationSelected("conv-1"))
        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        assertEquals("conv-1", state.activeConversationId)
        assertEquals(777L, state.contextWindow)
    }

    @Test
    fun `allowed efforts from a model that is no longer active are dropped`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.trySendAction(
                ChatAction.Internal.AllowedEffortsLoaded(
                    providerId = "someone-else",
                    modelId = "other-model",
                    levels = listOf("low"),
                )
            )
            advanceUntilIdle()

            assertTrue(viewModel.stateFlow.value.allowedEfforts.isEmpty())
        }

    @Test
    fun `a rejected context window falls back to the core value and reports`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            val events = eventsOf(viewModel)
            advanceUntilIdle()

            viewModel.trySendAction(
                ChatAction.Internal.ContextWindowRejected(
                    ConversationChats.NEW_CONVERSATION,
                    1234L,
                    "boom",
                )
            )
            advanceUntilIdle()

            assertEquals(1234L, viewModel.stateFlow.value.contextWindow)
            assertTrue(events.single() is ChatUiEvent.ShowError)
        }

    @Test
    fun `stream parsed always acks even when the target message is gone`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            val ack = CompletableDeferred<Unit>()

            // 轮次也是"查无此轮"：一条已经收尾（或根本不存在）的回合的贴块请求，ack 同样要放行。
            viewModel.trySendAction(
                ChatAction.Internal.StreamParsed("no-such-turn", "no-such-message", null, null, ack)
            )
            advanceUntilIdle()

            // 不放行的话 worker 会永远挂在 ack 上、回合 join 不回来（见 ChatViewModel.deliverParsed）。
            assertTrue(ack.isCompleted)
        }

    @Test
    fun `conversation created from an older epoch is dropped`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 先新建会话（代次 +1），再补一条"上一代"的创建结果 → 必须被丢弃。
        viewModel.trySendAction(ChatAction.NewConversationClicked)
        viewModel.trySendAction(ChatAction.Internal.ConversationCreated(epoch = 0L, id = "conv-stale"))
        advanceUntilIdle()

        assertNull(viewModel.stateFlow.value.activeConversationId)
    }

    @Test
    fun `choosing a context window goes through an effect`() = runTest(testDispatcher) {
        // 生产路径：会话已附着时改窗口 → 命令经 Effect 落到核心，回执经 action 落状态。
        val viewModel = createViewModel()
        advanceUntilIdle()
        agentChat.nextEvents = listOf(ChatEvent.Text("hi"), ChatEvent.Completed)
        viewModel.trySendAction(ChatAction.InputChanged("hi"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        viewModel.trySendAction(ChatAction.ContextWindowSelected(4096L))
        advanceUntilIdle()

        assertEquals(listOf(4096L), agentChat.contextWindowsSet)
        assertEquals(4096L, viewModel.stateFlow.value.contextWindow)
    }

    @Test
    fun `deleting a conversation goes through an effect`() = runTest(testDispatcher) {
        conversationStore.seedConversation("conv-1", "older", emptyList())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.trySendAction(ChatAction.ConversationDeleted("conv-1"))
        advanceUntilIdle()

        assertEquals(listOf("conv-1"), conversationStore.deleted)
    }

    @Test
    fun `a failed delete reports through the event channel`() = runTest(testDispatcher) {
        conversationStore.deleteFailure = IllegalStateException("no")
        val viewModel = createViewModel()
        val events = eventsOf(viewModel)
        advanceUntilIdle()

        viewModel.trySendAction(ChatAction.ConversationDeleted("conv-x"))
        advanceUntilIdle()

        assertTrue(events.single() is ChatUiEvent.ShowError)
    }

    @Test
    fun `stop flags the turn until it actually finishes`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        agentChat.nextEvents = listOf(ChatEvent.Text("hi"))
        agentChat.hangAfterEvents = true
        viewModel.trySendAction(ChatAction.InputChanged("hi"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()
        assertTrue(viewModel.stateFlow.value.isSending)

        viewModel.trySendAction(ChatAction.StopClicked)
        advanceUntilIdle()

        // 核心收手（Aborted）→ 回合收尾：发送态与"正在停止"都落下，且能继续。
        val state = viewModel.stateFlow.value
        assertEquals("中断命令应该被送到核心一次", 1, agentChat.interruptCalls)
        assertTrue(
            "应该出现「你在 N 秒后停止了」那行（messages=${state.messages.map { it.text }}）",
            state.messages.any { it.stoppedAfterMs != null },
        )
        assertTrue("核心应该记下这次中断", agentChat.interrupted)
        assertFalse("回合结束就不该还在发送", state.isSending)
        assertFalse("回合结束就该落下停止位", state.isInterruptRequested)
        assertTrue("中断过的回合应该能继续", state.canContinue)
        }

    @Test
    fun `the turn interrupted action settles the turn on its own`() = runTest(testDispatcher) {
        // 探针：不经过核心事件，直接喂 TurnInterrupted —— 用来把"handler 的问题"和"事件路径的问题"分开。
        val viewModel = createViewModel()
        advanceUntilIdle()
        agentChat.nextEvents = listOf(ChatEvent.Text("hi"))
        agentChat.hangAfterEvents = true
        viewModel.trySendAction(ChatAction.InputChanged("hi"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()
        assertTrue("回合应该在跑", viewModel.stateFlow.value.isSending)

        viewModel.trySendAction(
            ChatAction.Internal.TurnInterrupted(viewModel.runningTurnIdForTest()!!, 4_000L)
        )
        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        assertFalse("TurnInterrupted 应该落下发送态", state.isSending)
        assertTrue("TurnInterrupted 应该给「继续」", state.canContinue)
    }

    @Test
    fun `an interrupt failure action clears the stopping flag and reports`() = runTest(testDispatcher) {
        // 探针：真起一轮 → 停（命令会被拒）→ 再手工喂一条 EffectFailed(tag = "Interrupt")。
        // 事件数告诉我们是"自动那条 EffectFailed 到了"（2）还是只到了手工这条（1）。
        val viewModel = createViewModel()
        val events = eventsOf(viewModel)
        advanceUntilIdle()
        agentChat.nextEvents = listOf(ChatEvent.Text("hi"))
        agentChat.hangAfterEvents = true
        agentChat.interruptFailure = IllegalStateException("auto")
        viewModel.trySendAction(ChatAction.InputChanged("hi"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        viewModel.trySendAction(ChatAction.StopClicked)
        advanceUntilIdle()
        val afterAuto = events.size
        val flagAfterAuto = viewModel.stateFlow.value.isInterruptRequested

        viewModel.trySendAction(ChatAction.Internal.EffectFailed("Interrupt", "manual"))
        advanceUntilIdle()

        assertEquals("自动的 EffectFailed 应该到了（1 条）", 1, afterAuto)
        assertTrue("自动失败后状态位就该落下", !flagAfterAuto)
        assertEquals("手工那条也应该到（共 2 条）", 2, events.size)
        assertFalse("状态位不许粘住", viewModel.stateFlow.value.isInterruptRequested)
    }


    @Test
    fun `a failed interrupt clears the stopping flag and reports`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val events = eventsOf(viewModel)
        advanceUntilIdle()
        agentChat.nextEvents = listOf(ChatEvent.Text("hi"))
        agentChat.hangAfterEvents = true
        agentChat.interruptFailure = IllegalStateException("no")
        viewModel.trySendAction(ChatAction.InputChanged("hi"))
        viewModel.trySendAction(ChatAction.SendClicked)
        advanceUntilIdle()

        viewModel.trySendAction(ChatAction.StopClicked)
        advanceUntilIdle()

        // 顺序：先看事件与调用次数，最后看状态位 —— 失败时报出来的信息更精确。
        assertEquals("中断命令应该被送到核心一次", 1, agentChat.interruptCalls)
        assertEquals("应该正好报一次错", 1, events.size)
        assertTrue("报的应该是错误事件", events.single() is ChatUiEvent.ShowError)
        assertFalse(
            "中断失败后状态位不许粘住",
            viewModel.stateFlow.value.isInterruptRequested,
        )
    }

    /**
     * 转写里那行 `tool_status` → 状态的映射表（E1）：核心只会写 `"completed"` / `"stopped"`，
     * 其余取值是兼容读法。表错了，恢复出来的历史就会显示成错的状态。
     */
    @Test
    fun `stored tool status strings map onto the four live states`() {
        assertEquals(ChatToolStatus.COMPLETED, toolStatusOf("completed"))
        assertEquals(ChatToolStatus.STOPPED, toolStatusOf("stopped"))
        // 旧数据里的审批语义：没有审批流程了，读法保留但不再产生。
        assertEquals(ChatToolStatus.RUNNING, toolStatusOf("pending"))
        assertEquals(ChatToolStatus.FAILED, toolStatusOf("denied"))
        // 认不出的取值按"已完成"——一行工具记录落了盘，它当时就是跑完了的。
        assertEquals(ChatToolStatus.COMPLETED, toolStatusOf(""))
        assertEquals(ChatToolStatus.COMPLETED, toolStatusOf("whatever"))
    }

    /**
     * 载荷形状认不出来时必须被判成"降级"（E2）：判漏的后果是展开区一片空白、卡片上还不给标记，
     * 那正是这条链上要消除的**静默**。
     */
    @Test
    fun `payload shapes the renderer cannot read are flagged as fallbacks`() {
        // 补丁：参数里没有 patch/diff/new_string，结果也没有正文 → 画不出补丁。
        assertTrue(toolCallFallsBackToRaw("edit_file", """{"file_path":"a.kt"}""", null))
        assertFalse(toolCallFallsBackToRaw("edit_file", """{"patch":"@@ -1 +1 @@"}""", null))

        // 清单：既没有 `- [ ]` 行、也没有 status/content 对 → 一项都认不出来。
        assertTrue(toolCallFallsBackToRaw("todo_write", """{"items":[]}""", "no idea"))
        assertFalse(toolCallFallsBackToRaw("todo_write", "{}", "- [x] done"))

        // 命令：没有 command 字段、也没有输出 → 终端渲染器画不出任何东西。
        assertTrue(toolCallFallsBackToRaw("bash", """{"cwd":"/tmp"}""", null))
        assertFalse(toolCallFallsBackToRaw("bash", """{"command":"ls"}""", null))

        // 兜底渲染器画的就是原文，不存在"降级"。
        assertFalse(toolCallFallsBackToRaw("some_mcp_tool", "whatever", null))
    }

    private fun createViewModel() = ChatViewModel(
        providerRepository = providerRepository,
        userPreferencesRepository = preferencesRepository,
        conversationStore = conversationStore,
        agentChat = agentChat,
        markdownParserFactory = markdownParserFactory,
    ).also {
        // 解析别在真线程上跑：`withContext(Dispatchers.Default)` 会越过 `advanceUntilIdle()` 的栅栏，
        // 把"贴块"拖到用例之后（本文件历史上正是被这类续体撞过 Main）。
        it.parseDispatcher = testDispatcher
        viewModel = it
    }

    private companion object {
        const val MODEL_ID = "model-1"

        val PROVIDER = ProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            apiKey = "sk-test",
            apiType = ProviderApiType.CHAT_COMPLETIONS,
            models = listOf(
                ModelConfig(id = MODEL_ID, modelId = "deepseek-chat", reasoningEffort = ""),
                ModelConfig(id = "model-2", modelId = "deepseek-reasoner", reasoningEffort = ""),
            ),
        )
    }
}

private class FakeProviderRepository(
    initial: List<ProviderConfig>,
    private val catalogModels: List<CatalogModel> = emptyList(),
) : ProviderRepository {
    override val providersStateFlow = MutableStateFlow(initial)
    override suspend fun upsertProvider(provider: ProviderConfig) = Unit
    override suspend fun deleteProvider(id: String) = Unit
    override suspend fun fetchModels(provider: ProviderConfig): List<String> = emptyList()
    override suspend fun catalog(providerId: String): List<CatalogModel> = catalogModels
}



/**
 * 纯 Kotlin 解析器工厂。
 *
 * 生产的解析器是 JNI 的（`NativeBridge` 在 `init` 里就 `System.loadLibrary`），
 * 纯 JVM 单测加载不了 .so；换成这个替身，被测的 ViewModel 逻辑一行都不用改。
 *
 * 块内容不是这些用例的断言对象，所以实现刻意最简：**整段文本 = 一个段落块**，
 * 且每次 `append` 都当作 FULL 重解析（与 ViewModel 的调用方式一致）。
 */
private class FakeMarkdownParserFactory : MarkdownParserFactory {
    val created = mutableListOf<FakeMarkdownParser>()

    override fun create(): MarkdownParser = FakeMarkdownParser().also { created += it }
}

private class FakeMarkdownParser : MarkdownParser {

    private val text = StringBuilder()

    override fun append(chunk: String): MarkdownUpdate {
        text.append(chunk)
        return MarkdownUpdate(
            index = 0,
            advanced = false,
            newlyCompletedCount = 0,
            blocks = blocks(),
        )
    }

    override fun reset() {
        text.clear()
    }

    /** 一次流只产生一个块，收尾没有额外内容，故为空增量。 */
    override fun finalizeStream(): MarkdownUpdate = MarkdownUpdate.EMPTY

    override fun close() = Unit

    private fun blocks(): List<MarkdownBlock> =
        if (text.isEmpty()) {
            emptyList()
        } else {
            listOf(
                MarkdownBlock(
                    id = "block-0",
                    type = MarkdownBlockType.PARAGRAPH,
                    content = listOf(
                        MarkdownInline(MarkdownInlineType.TEXT, literal = text.toString())
                    ),
                )
            )
        }
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

    override suspend fun updateAgentOutputLanguage(value: String) = Unit
}

private class FakeConversationStore : ConversationStore {
    override val conversationsStateFlow = MutableStateFlow<List<Conversation>>(emptyList())
    /** 用例里没有读失败。 */
    override val readFailures: Flow<String> = emptyFlow()
    val created = mutableListOf<Conversation>()
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

    /** ADK's session store is not observable; the ViewModel re-reads it explicitly. */
    /** 让用例把它设成真，模拟"这条会话有一个没写完的回合"。 */
    var hasUnfinishedTurn: String? = null

    override suspend fun interruptedTurn(conversationId: String): String? = hasUnfinishedTurn

    /** 用量统计跟这条会话无关，用例里一律报"什么都没有"。 */
    override suspend fun usageStats(): AppUsage = AppUsage.Empty

    override suspend fun refresh() = Unit

    // 真实的 store 是 Room 挂起读：ViewModel 构造期那次恢复本来就落在构造返回**之后**。这里也挂起
    // 一拍，用例才有机会先换掉 `ChatViewModel.parseDispatcher` —— 否则恢复会在真线程上解析，越过
    // `advanceUntilIdle()` 的栅栏，把状态拖到用例之后（本文件记录过的那类续体撞 Main）。
    override suspend fun latestConversation(): Conversation? {
        delay(1)
        return latest
    }

    override suspend fun messagesOf(conversationId: String): List<TranscriptMessage> {
        delay(1)
        return transcripts[conversationId].orEmpty().toList()
    }

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
        latest = conversation
        return conversation
    }

    /** 让用例模拟"删除失败"（验证失败回流：EffectFailed → ChatUiEvent.ShowError）。 */
    var deleteFailure: Exception? = null

    override suspend fun deleteConversation(id: String) {
        deleteFailure?.let { throw it }
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

    /** 附着时收到的人格（系统指令的语言那部分由核心拼，不在这里）。 */
    var instruction: String? = null

    /** 附着时收到的 Agent 行为设置（回复语言那类值）。 */
    var settings: AgentSettings? = null

    /** 这个假核心的会话档位：测试直接给值。 */
    var sessionEffort: String? = null

    override suspend fun reasoningEffort(sessionId: String): String? = sessionEffort

    override suspend fun conversationReasoningEffort(sessionId: String): String? = sessionEffort

    override suspend fun setReasoningEffort(sessionId: String, value: String) {
        sessionEffort = value
    }

    /** 发完 [nextEvents] 后挂住，不发 Completed —— 模拟「回复还在进行中」。 */
    var hangAfterEvents = false

    /**
     * 挂住期间还要继续吐的事件：等中断信号到了先发这些，再发 Aborted。
     *
     * 用来模拟"用户切走了会话，核心那边这一轮还在跑"：切走之后才到的事件（工具调用 / 正文）以前会
     * 落到**新**会话的界面上。
     */
    var eventsAfterInterrupt: List<ChatEvent> = emptyList()

    /**
     * 挂住期间推一条事件（推一条、吐一条）。
     *
     * 模拟"切走了会话，核心那边这一轮**还在正常输出**"：这一轮没有结束，也没有被中断，只是界面
     * 在看别的会话 —— 那一段必须落进它自己那条会话。
     */
    fun emitWhileHanging(event: ChatEvent) {
        hangWakeups.trySend(event)
    }

    /** 挂住期间的唤醒：一条要吐的事件，或一个"中断到了"的 null（只为把挂住的那一轮叫醒）。 */
    private val hangWakeups = Channel<ChatEvent?>(Channel.UNLIMITED)

    override suspend fun startConversation(
        sessionId: String,
        provider: ProviderConfig,
        modelId: String,
        instruction: String,
        settings: AgentSettings,
    ) {
        conversationsStarted++
        startedWithSessionId = sessionId
        this.instruction = instruction
        this.settings = settings
    }

    override fun send(sessionId: String, text: String): Flow<ChatEvent> {
        sent += text
        sentSessions += sessionId
        return flow {
            nextEvents.forEach { emit(it) }
            // 挂住不回 Completed；核心被中断时以 Aborted 收尾（照 Rust 那边的事件契约）。
            if (hangAfterEvents) {
                // 挂住期间被推来的事件照吐（见 [emitWhileHanging]），中断信号到了才进收尾。
                while (!interruptSignal.isCompleted) {
                    val next = hangWakeups.receive() ?: continue
                    emit(next)
                }
                eventsAfterInterrupt.forEach { emit(it) }
                emit(ChatEvent.Aborted(durationMs = 4_000))
            }
        }
    }

    override fun endConversation(sessionId: String) {
        conversationsEnded++
        ended += sessionId
    }

    /** 每一轮发到哪条会话上（按顺序）；用来验"事件落进它自己那条"。 */
    val sentSessions = mutableListOf<String>()

    /** 释放过哪几条会话（按顺序）。 */
    val ended = mutableListOf<String>()

    /** 核心那边会话的窗口；测试默认给核心的默认值。 */
    var contextWindow: Long = 200_000

    /** 界面设过的窗口，按顺序记下来。 */
    val contextWindowsSet = mutableListOf<Long>()

    override suspend fun contextWindow(sessionId: String): Long = contextWindow

    /** 各会话自己记录的窗口；没有记录（或没设过）时为 null。 */
    val conversationContextWindows = mutableMapOf<String, Long>()

    override suspend fun conversationContextWindow(sessionId: String): Long? =
        conversationContextWindows[sessionId]

    /** 各会话文件里记着的用量；测试按需塞。 */
    val conversationUsages = mutableMapOf<String, ContextUsage>()

    override suspend fun conversationUsage(sessionId: String): ContextUsage? =
        conversationUsages[sessionId]

    override fun setContextWindow(sessionId: String, tokens: Long): Flow<ChatEvent> {
        contextWindowsSet += tokens
        contextWindow = tokens
        return kotlinx.coroutines.flow.emptyFlow()
    }

    /** 中断是否送到了核心（现在是核心自己收手，平台不再补写半段）。 */
    var interrupted = false

    /** 让用例模拟"中断命令失败"（验证失败回流 + 状态位不粘住）。 */
    var interruptFailure: Exception? = null

    /** 中断被调用了几次（幂等性用例看它）。 */
    var interruptCalls = 0

    private val interruptSignal = kotlinx.coroutines.CompletableDeferred<Unit>()

    override suspend fun interrupt(sessionId: String) {
        interruptCalls++
        interruptedSessions += sessionId
        interruptFailure?.let { throw it }
        interrupted = true
        interruptSignal.complete(Unit)
        // 把挂住的那一轮叫醒，让它走收尾（见 send 里那个循环）。
        hangWakeups.trySend(null)
    }

    /** 中断是冲着哪条会话去的（按顺序）。 */
    val interruptedSessions = mutableListOf<String>()

    override fun continueTurn(sessionId: String): Flow<ChatEvent> =
        kotlinx.coroutines.flow.emptyFlow()

    override suspend fun persistInterruptedReply(sessionId: String, text: String) {}

    /** Events the resumed turn streams, once the user answers. */
    var nextPromptEvents: List<ChatEvent> = emptyList()

    val answered = mutableListOf<String>()

    override fun respondToPrompts(sessionId: String, answers: List<String>): Flow<ChatEvent> {
        answered += answers
        sentSessions += sessionId
        return nextPromptEvents.asFlow()
    }
}
