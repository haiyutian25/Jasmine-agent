package com.lhzkml.jasmine.feature.main.impl.chat

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.core.agent.AgentChat
import com.lhzkml.jasmine.core.agent.ChatEvent
import com.lhzkml.jasmine.core.agent.ChatFailureKind
import com.lhzkml.jasmine.core.agent.ConversationStore
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.ModelConfig
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.data.model.AgentOutputLanguage
import com.lhzkml.jasmine.core.data.model.AgentSettings
import com.lhzkml.jasmine.core.data.model.UserPreferences
import com.lhzkml.jasmine.core.data.model.findInCatalog
import com.lhzkml.jasmine.core.data.repository.ProviderRepository
import com.lhzkml.jasmine.core.data.repository.UserPreferencesRepository
import com.lhzkml.jasmine.core.markdown.IncrementalMarkdownDocument
import com.lhzkml.jasmine.core.markdown.MarkdownParser
import com.lhzkml.jasmine.core.markdown.MarkdownParserFactory
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlock
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlockType
import com.lhzkml.jasmine.core.markdown.model.MarkdownInline
import com.lhzkml.jasmine.core.markdown.model.MarkdownInlineType
import com.lhzkml.jasmine.core.markdown.model.MarkdownUpdate
import com.lhzkml.jasmine.core.ui.base.BaseViewModel
import com.lhzkml.jasmine.core.ui.base.EffectRunner
import com.lhzkml.jasmine.core.ui.components.SidebarConversation
import com.lhzkml.jasmine.feature.main.impl.R
import com.lhzkml.jasmine.feature.main.impl.relativeTimeText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel backing the chat surface (MVVM + unidirectional data flow).
 *
 * It owns the conversation: the active provider/model selection is mirrored from
 * (and persisted to) preferences, and both the live turn ([AgentChat]) and the
 * history ([ConversationStore]) are ADK sessions — one conversation, one record.
 * The runner appends every user message and model reply to the session as it runs,
 * so this class writes no transcript of its own.
 *
 * This ViewModel is scoped to the `Main` navigation entry, so leaving the main
 * screen releases the runner; both the transcript and the session survive.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val conversationStore: ConversationStore,
    private val agentChat: AgentChat,
    private val markdownParserFactory: MarkdownParserFactory,
) : BaseViewModel<ChatState, ChatUiEvent, ChatAction>(initialState = ChatState()) {

    /**
     * 每一条已附着会话的附着身份（会话 id → `会话|provider|model`）。
     *
     * 照 ZCode：**一条会话一份**。所以切到别的会话不再"换掉"谁、也不用重挂；只有同一条会话里换了
     * provider / model 时才需要重挂（值变了，见 [runTurn]）。
     *
     * 写纪律（修复方案 D2，门禁 R8 守护）：**只由同步 handler 写** —— 附着成功的异步结果经
     * [ChatAction.Internal.ConversationAttached] 回流到这里；协程自己不动这张表。
     */
    private val attachedKeys = mutableMapOf<String, String>()

    /**
     * 界面在**还没附着会话**时先选好的上下文窗口。
     *
     * 新会话的第一条消息发出去之前没有会话文件，写不进去，所以先记在这里；附着时再写进那条
     * 会话自己的文件（核心负责落盘与回报）。
     */
    private var pendingContextWindow: Long? = null

    /**
     * 新会话的第一条消息发出去之前还没有会话文件，档位写不进去，所以先记在这里；附着时再交给核心
     * （核心负责把它追加进那条会话自己的文件）。与 [pendingContextWindow] 同一个道理 —— 所以**新建
     * 对话之后、还没发第一条消息时也能切档位**。
     */
    private var pendingReasoningEffort: String? = null

    /**
     * 正在落盘的那次模型选择（乐观值）。null = 没有在途的落盘。
     *
     * 偏好那条流是**整份** [UserPreferences]：改主题、改字体、改字号都会让它重发一次，带上尚未落盘的
     * 旧 `activeModelId`。没有这个守卫的话，那条重发会把刚选的模型盖回旧值，等落盘完成再翻回来 ——
     * 界面上就是"选完闪一下"。
     */
    private var pendingActiveModel: Pair<String, String>? = null

    /**
     * 偏好流里的"模型回复语言"已经读到过了。
     *
     * **模型回复语言本身不在这里存**：真源只有偏好仓库那条流，用的时候现取（见 [agentSettings]）——
     * 两个 ViewModel 各存一份同源值，迟早会分叉。这里只留一个"读到过没有"的标志，因为那条流的
     * 第一帧是 `stateIn` 的初值 [UserPreferences.DEFAULT]、第二帧才是磁盘上真存的那份：
     * 初值那帧不算"用户改了语言"，否则每次启动都会白重挂一次当前会话。
     */
    private var languagePreferenceSeen = false

    /** 正在跑的每一轮，按这一轮自己的身份证存。 */
    private val turns = mutableMapOf<String, Turn>()

    /**
     * 一条会话正在跑的那一轮的一切。
     *
     * 流式游标、写过的段、解析命令通道和它自己的后台 worker 都在这里。以前这些是 ViewModel 上的
     * 单份字段，所以同时只可能有一轮 —— 第二条消息一开，就把第一条的游标与路由全抢走了。
     * 一条会话同时仍然只有一轮（同一会话里的两条消息排队），但**几条会话可以同时各跑各的**：
     * 照 ZCode，生成属于它自己的会话，界面在看哪一条与之无关。
     *
     * [id] 是这一轮的身份证，动作按它找回自己那一轮。**不能拿会话 id 当身份证**：新建对话要到
     * 第一条消息发出去之后才有 id，而这一轮从那一刻就已经在跑了（见 [rekeyTurns]）。
     */
    private class Turn(val id: String, chatKey: String) {
        /** 这一轮写进哪条会话；新建对话拿到 id 时就地改掉（见 [rekeyTurns]）。 */
        var chatKey: String = chatKey

        /**
         * **这一轮**的流式解析命令通道。
         *
         * 解析**不在** `handleAction` 里做了：那里跑在 `viewModelScope`（Main）上，而 FULL 模式
         * 每来一个分片都要重解析整篇回复（O(n)）。长回复时主线程会被持续打满 —— 真机实测
         * 主线程 100% CPU 持续 15 秒、`Skipped 438 frames!  Davey! duration=7738ms`。
         *
         * 现在改成投递给这条通道，由**这一轮的**后台工作协程处理（见 [launchTurn]）：解析在 Default 上做，
         * 主线程只负责把结果贴进状态；回合结束（或这一轮被取消）通道就关掉，**不留"忘了收的解析"**。
         * 这一轮已经收尾之后再到的命令直接丢弃 —— 那时没有落点了。
         */
        val commands = Channel<StreamCommand>(Channel.UNLIMITED)

        /** Id of the assistant message currently being streamed, for chunk appends. */
        var streamingMessageId: String? = null

        /**
         * Assistant segments written during this turn. A turn can hold several —
         * text before a tool call and text after its result are separate bubbles — and
         * they are joined into the single row the transcript stores.
         */
        val assistantIds = mutableListOf<String>()

        /**
         * 待解析的最新文本。
         *
         * 关键在**合并**：模型一秒能吐几十个分片，而每次都是全量重解析，逐条排队会让解析
         * 永远追不上、回复越来越滞后。所以只保留最新一份，工作协程取走时用 `getAndSet(null)`，
         * 中间那些分片自然被跳过 —— 结果与逐条解析一致（FULL 模式本来就只认最终文本）。
         */
        val pendingParse = AtomicReference<StreamParseRequest?>(null)

        /** 已经排了一次解析，不必再排（省掉每个分片一次入队）。 */
        val parseQueued = AtomicBoolean(false)

        /**
         * Length of the text the parser was last fed.
         *
         * Mirrors `gt.h0`'s `l` field: the FULL path in ima skips the re-parse when the
         * incoming text has the same length as the one it last parsed, and it *assigns*
         * here rather than accumulating (the DELTA path is the one that accumulates).
         */
        var parsedLength: Int = 0

        /** 跑这一轮的协程。 */
        var job: Job? = null
    }

    /** 给 [turn] 的解析 worker 投一条命令；这一轮已经收尾了就丢弃。 */
    private fun sendStreamCommand(turn: Turn, command: StreamCommand) {
        turn.commands.trySend(command)
    }

    /** 一次流式解析请求：解析成 [text] 之后，块要贴到 [targetId] 这条消息上。 */
    private class StreamParseRequest(val targetId: String, val text: String)

    /** 交给后台工作协程的活。 */
    private sealed interface StreamCommand {
        /** 有新文本待解析（文本本身在 [Turn.pendingParse] 里）。 */
        data object Parse : StreamCommand

        /**
         * 这一段的正文写完了：收尾（闭合未结束的块），然后释放解析器。
         *
         * [trailingBlock] 是收尾之后要追加的块（失败原因那一块）。它**必须**走这条路而不是
         * 直接往状态里加：`applied()` 会按 `update.index` 截断块列表，同步追加的块会被
         * 收尾结果吃掉。
         */
        data class Finalize(
            val targetId: String?,
            val trailingBlock: MarkdownBlock? = null,
        ) : StreamCommand

        /** 直接释放解析器（段是空的 / 换会话 / 停止）。 */
        data object Close : StreamCommand
    }

    /**
     * "用户换掉了当前会话"的代次。
     *
     * [ensureConversation] 是异步的，创建期间用户可能已经点了新建会话或切了另一条 —— 那时这条
     * [ChatAction.Internal.ConversationCreated] 必须被丢弃，否则一条已经被放弃的会话会"复活"成
     * 当前会话。与 [attachedKeys] 同类：实现细节，不进 State。
     */
    private var conversationEpoch: Long = 0L

    /**
     * 每会话一份的聊天状态（照 ZCode 的 conversation projection，见 [ConversationChats]）。
     *
     * 每一轮的事件**只往它自己那条会话里写**（见 [Turn.chatKey]），与当前显示哪条无关；切走再切
     * 回来直接用内存里这份接着渲染（keep-warm 到期才回落到读转写）。
     *
     * 修复方案 D1：它是界面投影的**输入**，界面看到的 `ChatState.conversation` 由 [stateFlow]
     * 用 combine 派生 —— 这里只管存真身，不存在"忘了投影"。
     */
    private val chats = ConversationChats()

    /**
     * 界面看到的状态 = 基座状态 ⊕ 当前会话投影（修复方案 D1）。
     *
     * 投影由 combine **派生**：每会话状态的真身是 [chats]，这里只负责"当前显示哪条"的选择
     * （`activeConversationId`）。写会话状态只写真身（[updateChat]），投影自动跟上。
     */
    override val stateFlow: StateFlow<ChatState> =
        combine(mutableStateFlow, chats.flow) { global, table -> project(global, table) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                project(mutableStateFlow.value, chats.flow.value),
            )

    /**
     * handler 的读-判-写在同一帧：同步重建，不经过 combine 的调度间隙。
     * 与 [stateFlow] 共用同一个 [project]，保证"handler 读到的"与"界面看到的"是同一份。
     */
    override val state: ChatState get() = project(mutableStateFlow.value, chats.flow.value)

    /**
     * 唯一投影函数（D1）：全局状态 + 会话表 → 界面状态。`conversation` 字段只能在这里出现
     * （门禁 R9）——任何手动的 `copy(conversation = …)` 都是手动投影的回退。
     */
    private fun project(global: ChatState, table: Map<String, ConversationChatState>): ChatState =
        global.copy(
            conversation = table[global.activeConversationId ?: ConversationChats.NEW_CONVERSATION]
                ?: ConversationChatState(),
        )

    /** 当前显示的那条会话的键；还没发出第一条消息的新会话用 [ConversationChats.NEW_CONVERSATION]。 */
    private fun displayKey(): String = state.activeConversationId ?: ConversationChats.NEW_CONVERSATION

    /**
     * 抽屉要的那一片（P1-7）：会话列表 → 抽屉形状（标题 + 时间小字 + 当前高亮）。
     *
     * 投影原本写在 `MainScreen` 的组合函数里（还叠了个 `distinctUntilChanged` 自救）——
     * 那是 UI 层持有业务投影，且同一份 `stateFlow` 被订阅两次。现在投影在 ViewModel 内算，
     * 界面只收集。`distinctUntilChanged` 保留：流式分片只改 `conversation.messages`，
     * 不碰 `conversations` / `activeConversationId`，派生值相等就被挡掉，抽屉不会跟着分片重组。
     */
    val sidebarState: StateFlow<ChatSidebarState> =
        stateFlow
            .map { chat ->
                ChatSidebarState(
                    conversations = chat.conversations.map { conversation ->
                        SidebarConversation(
                            id = conversation.id,
                            title = conversation.title,
                            subtitle = relativeTimeText(conversation.updatedAt).orEmpty(),
                        )
                    },
                    activeConversationId = chat.activeConversationId,
                )
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, ChatSidebarState())

    /** 这条会话正在跑的那一轮；没在跑就是 null。 */
    private fun turnOfChat(key: String): Turn? =
        turns.values.firstOrNull { turn -> turn.chatKey == key }

    /** 这一轮的身份证找回它自己那一轮（动作回流时用）。 */
    private fun turnOf(turnId: String): Turn? = turns[turnId]

    /**
     * 只给测试与调试看：现在跑着的那一轮的身份证。
     *
     * 回合级动作（正文 / 收尾 / 贴块…）都按它指路，用例要直接喂一条动作时得先拿到它。
     */
    internal fun runningTurnIdForTest(): String? = turns.values.firstOrNull()?.id

    /**
     * [turn]**自己那条会话**的消息列表。
     *
     * 这一轮的每一步都要看**它自己**那份，而不是界面正显示的那一份 —— 照 ZCode：生成写进它自己的
     * topic，切会话换的只是"在看哪一份"。读错就是那个 bug：切走之后读到的是别人的消息列表，
     * "找不到目标消息"于是正文只累到最新一个分片、解析结果被整批丢掉，切回来永远停在切换那一刻，
     * 直到重启从文件读回来才补上（核心其实一直在正常输出、文件里也是完整的）。
     */
    private fun turnMessages(turn: Turn): List<ChatMessage> = chats.stateOf(turn.chatKey).messages

    /**
     * 改一条会话的状态（真身那张表）。
     *
     * 与 [updateState] 同一条规矩：**只由 handler 同步调用**。修复方案 D1 之后这里不再投影 ——
     * 界面那份由 [stateFlow] 的 combine 派生，写真身就够了。
     */
    private fun updateChat(
        key: String,
        transform: ConversationChatState.() -> ConversationChatState,
    ) {
        chats.update(key, transform)
    }

    /** 界面意图（输入、拨工具卡、切窗口…）：改当前显示那条会话。 */
    private fun updateConversation(transform: ConversationChatState.() -> ConversationChatState) {
        updateChat(displayKey(), transform)
    }

    /** 回合输出（正文 / 思考 / 工具 / 收尾…）：改 [turn] **自己那条**会话。 */
    private fun updateTurn(turn: Turn, transform: ConversationChatState.() -> ConversationChatState) {
        updateChat(turn.chatKey, transform)
    }

    /** 新建对话拿到真 id：把那一轮改到新键上（它的状态由 [ConversationChats.rekey] 一起搬）。 */
    private fun rekeyTurns(from: String, to: String) {
        turns.values.forEach { turn ->
            if (turn.chatKey == from) turn.chatKey = to
        }
    }

    /**
     * 出站命令执行器（core:ui 通用件，修复方案 D3）：单消费者、FIFO；每条命令的结果/失败都以
     * Internal action 回流（成功 = [performEffect] 的返回值，失败兜底 = [ChatAction.Internal.EffectFailed]）。
     */
    private val effects = EffectRunner<ChatEffect, ChatAction.Internal>(
        scope = viewModelScope,
        sendAction = ::sendAction,
        perform = ::performEffect,
        onFailure = { effect, error ->
            ChatAction.Internal.EffectFailed(
                tag = effect.tag(),
                message = error.message.orEmpty(),
                conversationId = effect.conversationId,
            )
        },
    )

    /**
     * 解析用的调度器。
     *
     * 生产是 [Dispatchers.Default]：FULL 模式每个分片都重解析整篇，放主线程会把主线程打满（见
     * [Turn.commands] 的注释）。单测把它换成测试调度器 —— 真线程会越过 `advanceUntilIdle()` 的栅栏，
     * 让"贴块"落到用例之后，进而让同一套流程时快时慢。
     */
    internal var parseDispatcher: CoroutineDispatcher = Dispatchers.Default

    init {
        providerRepository
            .providersStateFlow
            .map { ChatAction.Internal.ProvidersReceived(it) }
            .onEach(::sendAction)
            .launchIn(viewModelScope)

        userPreferencesRepository
            .preferencesStateFlow
            .onEach { preferences ->
                sendAction(
                    ChatAction.Internal.ActiveModelReceived(
                        preferences.activeProviderId,
                        preferences.activeModelId,
                    )
                )
            }
            .launchIn(viewModelScope)

        // 回复语言单独收一条：偏好那条流是**整份**的，改主题 / 字体 / 字号都会重发一次，
        // 而只有语言真的变了才需要重挂会话 —— 这里先折叠掉重复，handler 就不必再拿一份旧值来比对
        // （那份旧值正是"同一个偏好存两份"的来源）。
        userPreferencesRepository
            .preferencesStateFlow
            .map { preferences -> preferences.agentOutputLanguage }
            .distinctUntilChanged()
            .onEach { sendAction(ChatAction.Internal.LanguagePreferenceChanged) }
            .launchIn(viewModelScope)

        conversationStore
            .conversationsStateFlow
            .map { ChatAction.Internal.ConversationsReceived(it) }
            .onEach(::sendAction)
            .launchIn(viewModelScope)

        // 存储读失败（D5）：列表已保留上一次成功的快照，这里只把"这次没读到"提示一次。
        conversationStore.readFailures
            .onEach { sendEvent(ChatUiEvent.ShowToast(R.string.chat_store_read_failed)) }
            .launchIn(viewModelScope)

        // The session store is not observable, so the list has to be read once here.
        // 这是一条**出站命令**：走 Effect（结果由 conversationsStateFlow 回灌，失败回流见 §3 第 13 条）。
        effects.send(ChatEffect.RefreshConversations)
        viewModelScope.launch { restoreLatestConversation() }
        // 流式解析的 worker 不在这里起：它属于**每一轮**，见 launchTurn。
    }

    override fun handleAction(action: ChatAction) {
        when (action) {
            is ChatAction.InputChanged -> updateConversation { copy(input = action.value) }
            ChatAction.SendClicked -> handleSendClicked()
            ChatAction.StopClicked -> handleStopClicked()
            ChatAction.ContinueClicked -> handleContinueClicked()
            ChatAction.NewConversationClicked -> handleNewConversation()
            ChatAction.ModelPickerOpened -> updateState { copy(isModelPickerOpen = true) }
            ChatAction.ModelPickerDismissed -> updateState { copy(isModelPickerOpen = false) }
            ChatAction.ContextPanelOpened -> updateState { copy(isContextPanelOpen = true) }
            ChatAction.ContextPanelDismissed -> updateState { copy(isContextPanelOpen = false) }
            is ChatAction.ModelSelected -> handleModelSelected(action)
            is ChatAction.ContextWindowSelected -> handleContextWindowSelected(action.tokens)
            is ChatAction.ToolRowToggled -> handleToolRowToggled(action)
            is ChatAction.ThoughtLevelSelected -> handleThoughtLevelSelected(action)
            is ChatAction.ConversationSelected -> handleConversationSelected(action)
            is ChatAction.ConversationDeleted -> handleConversationDeleted(action)
            is ChatAction.PromptAnswered -> handlePromptAnswered(action)

            is ChatAction.Internal.ProvidersReceived ->
                updateState { copy(providers = action.providers) }
            is ChatAction.Internal.ConversationsReceived ->
                updateState { copy(conversations = action.conversations) }
            is ChatAction.Internal.ActiveModelReceived -> {
                val incoming = action.providerId to action.modelId
                val inFlight = pendingActiveModel
                // 身份守卫（F8）：在途的落盘还没回来时，**忽略与它不同的旧值** —— 偏好那条流是整份的，
                // 改主题/字体/字号都会重发一次，带上尚未落盘的旧模型；照收就会把刚选的盖回去。
                if (inFlight != null && incoming != inFlight) return
                if (inFlight != null) pendingActiveModel = null
                updateState {
                    copy(activeProviderId = action.providerId, activeModelId = action.modelId)
                }
                // 当前模型要等偏好读回来才知道，所以新会话的窗口、模型允许的档位都在这里再对一次。
                if (state.activeConversationId == null) {
                    updateConversation {
                        copy(
                            contextWindow = newConversationContextWindow(),
                            // 还没有会话：先照模型配置里的默认档显示 —— 第一次附着时核心会定下这条会话
                            // 自己的起点档（目录里有这个模型就用目录的），附着后
                            // [syncReasoningEffortAfterAttach] 再把核心那边的值读回来。
                            reasoningEffort = state.activeModel?.reasoningEffort.orEmpty(),
                        )
                    }
                }
                // 允许的档位由核心目录决定：取数与发 action 在协程里，落状态在 handler（守卫按
                // provider/model 身份比对，换过模型就丢弃）。
                val provider = state.activeProvider
                val modelId = state.activeModel?.modelId
                if (provider != null && modelId != null) {
                    viewModelScope.launch { refreshAllowedEfforts(provider.id, modelId) }
                }
            }
            is ChatAction.Internal.LanguagePreferenceChanged ->
                handleLanguagePreference()
            is ChatAction.Internal.TranscriptRestored -> {
                handleTranscriptRestored(action)
                // 这条会话是不是有一个还没写完的回合，决定发送键要不要是「继续」。
                // 它是会话文件里的事实，所以重启之后打开这条会话同样拿得到。
                viewModelScope.launch {
                    val unfinished = runCatching {
                        conversationStore.interruptedTurn(action.conversationId)
                    }
                    // 读失败**不下结论**（F7）：以前 getOrNull() 会把失败变成 canContinue = false，
                    // 发送键上的「继续」无声消失，用户以为这条回复是完整的。
                    if (unfinished.isFailure) {
                        reportReadFailure(
                            R.string.chat_interrupted_turn_failed,
                            unfinished.exceptionOrNull(),
                        )
                    } else {
                        // 只取数与发 action —— 状态由 handler 同步落（守卫也搬到了那里：过期会话的结论
                        // 不会再写进状态，这是这一处原有的隐患）。
                        sendAction(
                            ChatAction.Internal.CanContinueResolved(
                                conversationId = action.conversationId,
                                canContinue = unfinished.getOrNull() != null,
                            )
                        )
                    }
                    // 窗口/用量/档位同样从它的文件里读回来 —— 重启之后要显示的是这条会话自己的值。
                    refreshConversationFacts(action.conversationId)
                }
            }
            is ChatAction.Internal.ReplyChunk ->
                turnOf(action.turnId)?.let { turn -> appendReplyChunk(turn, action.text) }
            is ChatAction.Internal.ReasoningChunk ->
                turnOf(action.turnId)?.let { turn -> appendReasoningChunk(turn, action.text) }
            is ChatAction.Internal.ToolCalled ->
                turnOf(action.turnId)?.let { turn -> appendToolCall(turn, action) }
            is ChatAction.Internal.ToolReturned ->
                turnOf(action.turnId)?.let { turn -> appendToolResult(turn, action) }
            is ChatAction.Internal.PromptRequested ->
                turnOf(action.turnId)?.let { turn -> handlePromptRequested(turn, action) }
            is ChatAction.Internal.TurnFailed ->
                turnOf(action.turnId)?.let { turn ->
                    // 这一轮失败时还开着的工具卡标成「执行失败」（照 ZCode 的 failed 态）。
                    markOpenTools(turn, ChatToolStatus.FAILED)
                    failTurn(turn, action.detail, action.kind)
                }
            is ChatAction.Internal.TurnCompleted ->
                turnOf(action.turnId)?.let { turn -> finishTurn(turn) }
            is ChatAction.Internal.TurnRetired -> turns.remove(action.turnId)
            is ChatAction.Internal.TurnInterrupted ->
                turnOf(action.turnId)?.let { turn ->
                    // 用户停止时还开着的工具卡标成「已停止」。
                    markOpenTools(turn, ChatToolStatus.STOPPED)
                    handleTurnInterrupted(turn, action.durationMs)
                }
            // 用量只是这一轮的附带信息：面板开着就刷新，消息不动。
            is ChatAction.Internal.UsageReceived ->
                turnOf(action.turnId)?.let { turn ->
                    updateTurn(turn) { copy(contextUsage = action.usage) }
                }

            // ── 异步结果的落点：读-判-写**全在这一帧里**（守卫按身份键丢弃过期结果）──

            is ChatAction.Internal.CanContinueResolved -> {
                if (state.activeConversationId != action.conversationId) return
                updateConversation { copy(canContinue = action.canContinue) }
            }
            is ChatAction.Internal.ConversationFactsLoaded -> {
                val facts = action.facts
                if (state.activeConversationId != facts.conversationId) return
                updateConversation {
                    copy(
                        contextWindow = facts.window ?: newConversationContextWindow(),
                        contextUsage = facts.usage,
                        reasoningEffort = facts.effort.orEmpty(),
                    )
                }
            }
            is ChatAction.Internal.TranscriptLoaded -> {
                if (state.activeConversationId != action.conversationId) return
                updateConversation { copy(messages = action.messages) }
            }
            is ChatAction.Internal.ContextWindowApplied ->
                updateChat(action.conversationId) { copy(contextWindow = action.tokens) }
            is ChatAction.Internal.ContextWindowRejected -> {
                action.coreValue?.let { value ->
                    updateChat(action.conversationId) { copy(contextWindow = value) }
                }
                sendEvent(ChatUiEvent.ShowError(R.string.chat_context_window_failed, action.message))
            }
            is ChatAction.Internal.ReasoningEffortApplied ->
                updateChat(action.conversationId) { copy(reasoningEffort = action.value) }
            is ChatAction.Internal.ReasoningEffortRejected -> {
                action.coreValue?.let { value ->
                    updateChat(action.conversationId) { copy(reasoningEffort = value) }
                }
                sendEvent(ChatUiEvent.ShowError(R.string.chat_reasoning_effort_failed, action.message))
            }
            is ChatAction.Internal.AllowedEffortsLoaded -> {
                val unchanged = state.activeProviderId == action.providerId &&
                    state.activeModel?.modelId == action.modelId
                if (!unchanged) return
                updateState { copy(allowedEfforts = action.levels) }
            }
            is ChatAction.Internal.ReasoningEffortSynced ->
                updateChat(action.conversationId) { copy(reasoningEffort = action.value) }
            is ChatAction.Internal.ContextWindowSynced -> {
                // 核心没记过（null）就不写：保持今天 `?.let { … }` 的语义。
                action.value?.let { value ->
                    updateChat(action.conversationId) { copy(contextWindow = value) }
                }
            }
            is ChatAction.Internal.ConversationCreated -> {
                if (action.epoch != conversationEpoch) return
                // 还没有 id 的那份状态与那一轮一起搬到真实 id 上；投影随 activeConversationId 自动跟上（D1）。
                chats.rekey(ConversationChats.NEW_CONVERSATION, action.id)
                rekeyTurns(ConversationChats.NEW_CONVERSATION, action.id)
                updateState { copy(activeConversationId = action.id) }
            }
            // 附着注册表的唯一写入口：协程只发 action，写在 handler 里落（修复方案 D2）。
            is ChatAction.Internal.ConversationAttached ->
                attachedKeys[action.conversationId] = action.key
            is ChatAction.Internal.PendingContextWindowConsumed -> {
                // 等值守卫：补交在飞期间用户又选了新值，迟到的回流不能把新值抹掉。
                if (pendingContextWindow == action.value) pendingContextWindow = null
            }
            is ChatAction.Internal.PendingReasoningEffortConsumed -> {
                if (pendingReasoningEffort == action.value) pendingReasoningEffort = null
            }
            is ChatAction.Internal.KeepWarmExpired -> {
                // 读-判-写同帧：又跑了新一轮、或者正显示着它，就不丢。
                if (turnOfChat(action.key) == null && displayKey() != action.key) {
                    chats.drop(action.key)
                }
            }
            is ChatAction.Internal.StreamParsed -> {
                // ⚠️ 无论贴没贴都要放行：worker 正挂在 ack 上，漏掉这一行回合就 join 不回来。
                val turn = turnOf(action.turnId)
                // 贴的是**这一轮那条会话**的消息，不是界面正显示的那条：切走之后这一段照样要收下。
                if (turn != null && turnMessages(turn).any { it.id == action.targetId }) {
                    val startedAt = System.currentTimeMillis()
                    action.update?.let { applyStreamBlocks(turn, action.targetId, it) }
                    action.trailingBlock?.let { appendBlock(turn, action.targetId, it) }
                    Log.d(CHAT_PARSE_TAG, "贴块 ${System.currentTimeMillis() - startedAt}ms")
                }
                action.ack.complete(Unit)
            }
            is ChatAction.Internal.EffectFailed -> {
                if (action.tag == ChatEffect.Interrupt::class.java.simpleName) {
                    // 落回**发起中断的那条**会话（用户可能已经切走了）。
                    updateChat(action.conversationId ?: displayKey()) {
                        copy(isInterruptRequested = false)
                    }
                }
                sendEvent(ChatUiEvent.ShowError(R.string.chat_action_failed, action.message))
            }
            is ChatAction.Internal.ActiveModelPersistRejected -> {
                // 这次落盘结束了（失败了）：守卫解除，之后照收偏好流送来的权威值。
                pendingActiveModel = null
                // 身份守卫回滚（D4）：当前选择仍等于那条乐观值才回退。
                if (state.activeProviderId == action.optimisticProviderId &&
                    state.activeModelId == action.optimisticModelId
                ) {
                    updateState {
                        copy(
                            activeProviderId = action.fallbackProviderId,
                            activeModelId = action.fallbackModelId,
                        )
                    }
                }
                sendEvent(ChatUiEvent.ShowError(R.string.chat_action_failed, action.message))
            }
        }
    }

    override fun onCleared() {
        // 正在跑的那几轮由作用域取消收尾：取消会通报核心让它们**各自**收手（见 [RustAgentChat]）。
        // 附着着的会话一并放掉 —— 历史不动，别的什么都不动。
        // 拆毁路径（D3）：action/event 通道已随作用域关闭，失败只能进日志。
        attachedKeys.keys.toList().forEach { id ->
            runCatching { agentChat.endConversation(id) }
                .onFailure { Log.w(TAG, "endConversation($id) failed during onCleared", it) }
        }
        attachedKeys.clear()
        super.onCleared()
    }

    // region Restore

    /**
     * Brings back the most recently updated conversation, transcript and all.
     * The model selection is left to preferences: they are the single source for
     * "which model am I using", while the conversation only records what produced
     * it (shown in the history list).
     *
     * 正文的解析是 native 的，几百条消息就是上千次 JNI 往返 —— 全在 [parseDispatcher] 上做完再
     * 回流，主线程（这一屏正等着显示）一次都不碰 native。
     */
    private suspend fun restoreLatestConversation() {
        val conversation = runCatching { conversationStore.latestConversation() }
            .getOrNull() ?: return
        val messages = runCatching { conversationStore.messagesOf(conversation.id) }
            .getOrDefault(emptyList())
        // 兜底模型名在主线程上读（state 只在这儿动），解析带着它一起下后台。
        val fallbackModelLabel = modelLabelOf(conversation.id)
        val restored = withContext(parseDispatcher) {
            messages.map {
                it.toChatMessage(
                    fallbackModelLabel = fallbackModelLabel,
                    parserFactory = markdownParserFactory,
                )
            }
        }
        sendAction(ChatAction.Internal.TranscriptRestored(conversation.id, restored))
    }

    private fun handleTranscriptRestored(action: ChatAction.Internal.TranscriptRestored) {
        // The user may have started a new conversation while the load was in flight.
        if (state.activeConversationId != null || state.messages.isNotEmpty()) return
        updateState { copy(activeConversationId = action.conversationId) }
        // 转写落到**它自己那条会话**上（然后把这条投影给界面）。
        updateChat(action.conversationId) { copy(messages = action.messages) }
    }

    /**
     * 会话记录里的模型 → 界面上显示的模型名。
     *
     * `Conversation.modelId` 存的是 `ModelConfig.id`（不是模型名），所以要按 id 找回配置、
     * 取它的 `modelId` 来显示；找不到就用原值兜底。规则与侧边栏会话列表同一套。
     */
    private fun modelLabelOf(conversationId: String?): String? {
        val conversation = state.conversations.firstOrNull { it.id == conversationId } ?: return null
        return state.providers
            .firstOrNull { it.id == conversation.providerId }
            ?.models
            ?.firstOrNull { it.id == conversation.modelId }
            ?.modelId
            ?: conversation.modelId
    }

    // endregion

    // region Effects（出站命令：执行 + 回流）

    /**
     * 执行一条出站命令。**只做边界调用，不碰 state** —— 成功/失败都以 Internal action 返回，
     * 再由 [handleAction] 同步落状态（这就是 R2：异步必回流）。
     *
     * 没有乐观写入的命令（中断 / 删除 / 刷新）故意让异常冒出去：交给 EffectRunner 兜底成
     * [ChatAction.Internal.EffectFailed]。
     */
    private suspend fun performEffect(effect: ChatEffect): ChatAction.Internal? = when (effect) {
        is ChatEffect.SetContextWindow -> performSetContextWindow(effect)
        is ChatEffect.SetReasoningEffort -> performSetReasoningEffort(effect)
        is ChatEffect.Interrupt -> {
            agentChat.interrupt(effect.conversationId)
            null
        }
        is ChatEffect.DeleteConversation -> {
            conversationStore.deleteConversation(effect.id)
            null
        }
        ChatEffect.RefreshConversations -> {
            conversationStore.refresh()
            null
        }
        is ChatEffect.PersistActiveModel -> performPersistActiveModel(effect)
    }

    /**
     * 写会话的上下文窗口。
     *
     * 成功 → [ChatAction.Internal.ContextWindowApplied]（核心随后还会经事件流报一次用量）；
     * 失败 → [ChatAction.Internal.ContextWindowRejected]，带上核心的当前值供界面**回退**
     * —— 界面上那个值是乐观写入的，不能让它停在没落地的数上。
     */
    private suspend fun performSetContextWindow(effect: ChatEffect.SetContextWindow): ChatAction.Internal =
        try {
            // 这个调用只会报一条用量事件；面板要的是那条会话自己的最新数字，直接从它的文件读回来
            // 更稳（用户可能已经切走，这条结论仍然落到它自己那条会话上）。
            agentChat.setContextWindow(effect.conversationId, effect.tokens).collect { }
            refreshConversationFacts(effect.conversationId)
            ChatAction.Internal.ContextWindowApplied(effect.conversationId, effect.tokens)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ChatAction.Internal.ContextWindowRejected(
                conversationId = effect.conversationId,
                coreValue = runCatching { agentChat.contextWindow(effect.conversationId) }.getOrNull(),
                message = error.message ?: error::class.simpleName.orEmpty(),
            )
        }

    /**
     * 写会话的推理档位。
     *
     * 成功 → [ChatAction.Internal.ReasoningEffortApplied]，值以**核心读回来的**为准（写失败时
     * 界面不会显示一个没落地的档）；失败 → [ChatAction.Internal.ReasoningEffortRejected] 回退。
     */
    private suspend fun performSetReasoningEffort(effect: ChatEffect.SetReasoningEffort): ChatAction.Internal =
        try {
            agentChat.setReasoningEffort(effect.conversationId, effect.value)
            ChatAction.Internal.ReasoningEffortApplied(
                conversationId = effect.conversationId,
                value = runCatching { agentChat.reasoningEffort(effect.conversationId) }.getOrNull()
                    ?: effect.value,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ChatAction.Internal.ReasoningEffortRejected(
                conversationId = effect.conversationId,
                coreValue = runCatching { agentChat.reasoningEffort(effect.conversationId) }.getOrNull(),
                message = error.message ?: error::class.simpleName.orEmpty(),
            )
        }

    /**
     * 把模型选择写进偏好（修复方案 D4）。
     *
     * 成功 → null（不需要回执：`preferencesStateFlow` 回灌即确认）；失败 →
     * [ChatAction.Internal.ActiveModelPersistRejected]，带回乐观值与回退值。
     */
    private suspend fun performPersistActiveModel(effect: ChatEffect.PersistActiveModel): ChatAction.Internal? =
        try {
            userPreferencesRepository.updateActiveModel(effect.providerId, effect.modelId)
            null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ChatAction.Internal.ActiveModelPersistRejected(
                optimisticProviderId = effect.providerId,
                optimisticModelId = effect.modelId,
                fallbackProviderId = effect.fallbackProviderId,
                fallbackModelId = effect.fallbackModelId,
                message = error.message ?: error::class.simpleName.orEmpty(),
            )
        }

    // endregion

    // region Action handlers

    private fun handleSendClicked() {
        val text = state.input.trim()
        if (text.isEmpty() || state.isSending) return
        val provider = state.activeProvider ?: return
        val model = state.activeModel ?: return
        if (provider.apiKey.isBlank()) return

        // The placeholder bubble is what the UI shows while the model is silent. It
        // is dropped again if the turn opens with a tool call instead of text.
        val turn = beginTurn(displayKey())
        val assistantId = UUID.randomUUID().toString()
        turn.streamingMessageId = assistantId
        turn.assistantIds += assistantId
        // 用户消息和它的回复占同一个时间点（这一轮是同时开始的）。
        val now = System.currentTimeMillis()
        updateConversation {
            copy(
                input = "",
                canContinue = false,
                isSending = true,
                messages = messages +
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = ChatRole.USER,
                        text = text,
                        timestamp = now,
                    ) +
                    ChatMessage(
                        id = assistantId,
                        role = ChatRole.ASSISTANT,
                        text = "",
                        isStreaming = true,
                        timestamp = now,
                        modelLabel = model.modelId,
                    ),
            )
        }

        launchTurn(turn) { active -> runTurn(active, provider, model, text) }
    }

    /**
     * 停止正在进行的回复。
     *
     * 真正的中断在核心那边（见 [AgentChat.interrupt]）：核心在下一个等待点收手，把已经
     * 产出的条目落进会话文件，并以 [ChatEvent.Aborted] 收尾 —— 这一回合的流因此照常走到
     * 结束。
     *
     * 这里**不**取消对事件流的收集：核心已经吐出来的分片必须照常渲染完。取消收集会把
     * 还在缓冲区里的分片一起丢掉，气泡里的文字就会比模型实际看到的那一份少一截（文件里
     * 是完整的「一～八」，屏幕上却停在「无序列表」），于是下一轮「继续」从「九」接上，
     * 两边对不上。收尾交给这一回合自己的结束事件（Aborted / Completed → TurnCompleted）。
     */
    private fun handleStopClicked() {
        // [turns] / [attachedKeys] 这类**内部字段**允许在 handler 里读写：它们是实现细节，不进
        // State，所以不违反"只有 handler 写状态"（State 仍然只经 [updateState] 改）。
        // 停的是**界面这条**会话正在跑的那一轮（别的会话那一轮不归这个按钮管）。
        if (turnOfChat(displayKey()) == null) return
        // 新会话的 id 还没回来时没有可指名的那条会话；核心里那条会话也还没建起来。
        val conversationId = state.activeConversationId ?: return
        // 幂等：已经请求过了就不再发一遍。
        if (state.isInterruptRequested) return
        // 状态位 = "已请求中断、回合尚未收尾"：只有回合真正收尾、或命令失败才清。
        updateConversation { copy(isInterruptRequested = true) }
        // 命令走 Effect（这是唯一的旁路出口），失败由 EffectFailed 清位 + 提示。
        effects.send(ChatEffect.Interrupt(conversationId))
    }

    /**
     * 继续被中断的那一回合。
     *
     * 和「发送」只差一点：**不加用户消息**，也不新起回合 —— 核心在同一个回合里接着采样
     * （见 [AgentChat.continueTurn]）。续出来的文字**另起一条助手气泡**：文件里它同样是
     * 同一回合下的第二条助手条目，上游的界面也是两条，所以这里不并回上一条。
     */
    private fun handleContinueClicked() {
        if (state.isSending || !state.canContinue) return
        val provider = state.activeProvider ?: return
        val model = state.activeModel ?: return
        if (provider.apiKey.isBlank()) return

        val turn = beginTurn(displayKey())
        val assistantId = UUID.randomUUID().toString()
        turn.streamingMessageId = assistantId
        turn.assistantIds += assistantId
        updateConversation {
            copy(
                canContinue = false,
                isSending = true,
                messages = messages + ChatMessage(
                    id = assistantId,
                    role = ChatRole.ASSISTANT,
                    text = "",
                    isStreaming = true,
                    timestamp = System.currentTimeMillis(),
                    modelLabel = model.modelId,
                ),
            )
        }

        launchTurn(turn) { active -> runContinuedTurn(active, provider, model) }
    }

    /**
     * 回合被平台停下了：收尾、把发送键换成「继续」，并在这一段回答后面插一行状态
     * （「你在 N秒 后停止了」+ 分隔线）。
     *
     * 状态行是列表里独立的一项，所以它两侧的空白和消息之间的一模一样。
     */
    private fun handleTurnInterrupted(turn: Turn, durationMs: Long) {
        finishTurn(turn)
        updateTurn(turn) {
            copy(
                canContinue = true,
                // 回合真正收尾了：中断请求的状态位在这里落下（见 [handleStopClicked]）。
                isInterruptRequested = false,
                messages = messages + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    text = "",
                    stoppedAfterMs = durationMs,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * 刚附着上会话：把上下文窗口对齐。
     *
     * 核心报的才是权威 —— 会话自己的选择优先，其次是模型配的，再次是默认值。但界面在新会话里
     * 可能已经先选过了：那就以它为准写进这条会话的文件，并把核心随即回的那条用量收进来。
     */
    /**
     * 附着会话之后，把核心那边的推理档位读回界面。
     *
     * 新建会话时核心已经按模型配置写下了第一条记录，所以这里读到的是那条 —— 界面显示的就是会话
     * 真正在用的值。
     */
    private suspend fun syncReasoningEffortAfterAttach(conversationId: String) {
        // 新对话还没附着时选的那个档位，在这里补交 —— 这是一条**出站命令**，走 Effect
        // （它失败时由第 5 条的 Rejected 把界面纠正回核心值）。
        val pending = pendingReasoningEffort
        if (pending != null) {
            effects.send(ChatEffect.SetReasoningEffort(conversationId, pending))
            // pending 的清除回 handler（等值守卫，D2）：本协程不写影子字段。
            sendAction(ChatAction.Internal.PendingReasoningEffortConsumed(pending))
        }
        // 读回来的值以 action 回流，由 handler 同步写（本函数自己**不**碰 state）。
        agentChat.reasoningEffort(conversationId)?.let { attached ->
            sendAction(ChatAction.Internal.ReasoningEffortSynced(conversationId, attached))
        }
    }

    /**
     * 刷新"当前模型允许哪些档"：**目录**给的（目录里有这个模型就用它那套）；目录里没有就是空的，
     * 面板按"不限制"列出全部。
     *
     * 只读一次目录、不落盘 —— 它决定聊天页那张档位面板列哪几档（对应 codex 的
     * `ModelInfo.supported_reasoning_levels`）。
     *
     * **读失败返回 `null`**（F7）：空列表在这张表的语义里是"不限制"，拿失败去冒充它会让面板列出
     * 该模型并不支持的档位。返回 `null` 时调用方不回流，保持现状。
     */
    private suspend fun readAllowedEfforts(providerId: String, modelId: String): List<String>? {
        // 网关上的 id 带 `厂商/` 前缀与 `:变体` 后缀，按归一化后的键也能认出来（与核心同一条规则）。
        val catalog = runCatching { providerRepository.catalog(providerId) }
        val failure = catalog.exceptionOrNull()
        if (failure != null) {
            reportReadFailure(R.string.chat_allowed_efforts_failed, failure)
            return null
        }
        return catalog.getOrThrow().findInCatalog(modelId)?.levels.orEmpty()
    }

    private suspend fun syncContextWindowAfterAttach(conversationId: String) {
        val pending = pendingContextWindow
        if (pending != null) {
            // pending 这条本来就是"用户已经选过、界面也已经显示着"的值，所以补交走 Effect。
            // 这里**不**提前回 Applied：`performSetContextWindow` 成功时本来就返回
            // `ContextWindowApplied`（Effect 的回执即确认），失败返回 `Rejected` 携核心权威值回退 ——
            // 提前发会让"Applied"这个语义（写进核心成功）在确认之前就成立（P1-10）。
            effects.send(ChatEffect.SetContextWindow(conversationId, pending))
            // pending 的清除回 handler（等值守卫，D2）：本协程不写影子字段。
            sendAction(ChatAction.Internal.PendingContextWindowConsumed(pending))
            return
        }
        // 读回来的值以 action 回流；核心没记过（null）时 handler 不写，保持原值。
        sendAction(
            ChatAction.Internal.ContextWindowSynced(
                conversationId,
                agentChat.contextWindow(conversationId),
            )
        )
    }

    /**
     * 用户在模型面板里改了当前会话的上下文窗口。
     *
     * 会话已经附着就直接写进它的文件（核心会立刻回报一次用量，环与面板随之刷新）；还没附着
     * —— 新会话的第一条消息还没发出去 —— 先记在界面上，等附着时一并写入。
     */
    private fun handleContextWindowSelected(tokens: Long) {
        // 意图：界面立刻跟上（乐观写入；失败时由 Rejected 回退）。
        updateConversation { copy(contextWindow = tokens) }
        val conversationId = state.activeConversationId
        if (conversationId == null || attachedKeys[conversationId] == null) {
            // 还没附着会话：先记下来，附着时补交（见 [syncContextWindowAfterAttach]）。
            pendingContextWindow = tokens
            return
        }
        // 写进会话文件是**出站命令**：走 Effect。成功 → Applied；失败 → Rejected（回退 + 提示）。
        effects.send(ChatEffect.SetContextWindow(conversationId, tokens))
    }

    /** 照 [runTurn] 的做法跑完这一轮，只是入口换成「续采样」。 */
    private suspend fun runContinuedTurn(
        turn: Turn,
        provider: ProviderConfig,
        model: ModelConfig,
    ) {
        // 与 [runTurn] 同一条规矩：附着期间被换走了，这一轮的请求就不发了。
        val epoch = conversationEpoch
        try {
            // 续的是**这一轮那条**会话（不是界面现在显示的那条）。
            val id = turn.chatKey.takeIf(String::isNotEmpty) ?: throw IllegalStateException(
                "Could not continue a conversation that is not open."
            )
            if (epoch != conversationEpoch) return

            val key = "$id|${provider.id}|${model.id}"
            if (attachedKeys[id] != key) {
                // 继续之前先把这条会话交给核心：它要从会话文件里认出那个没写完的回合，
                // 重启之后（或者换过模型之后）这一步是必须的。
                agentChat.startConversation(
                    sessionId = id,
                    provider = provider,
                    modelId = model.modelId,
                    instruction = CHAT_PERSONA,
                    settings = agentSettings(),
                )
                // 与 [runTurn] 同一条规矩：附着结果回流，协程不写注册表（D2）。
                sendAction(ChatAction.Internal.ConversationAttached(id, key))
                syncContextWindowAfterAttach(id)
                syncReasoningEffortAfterAttach(id)
                if (epoch != conversationEpoch) return
            }

            collectEvents(turn, agentChat.continueTurn(id))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            sendAction(
                ChatAction.Internal.TurnFailed(
                    turn.id,
                    error.message ?: error::class.simpleName.orEmpty(),
                )
            )
        }
    }

    private fun handleNewConversation() {
        // 换掉当前会话：代次 +1，让在途的"会话创建完成"作废（见 [conversationEpoch]）。
        conversationEpoch++
        val leftKey = displayKey()
        // 只放掉正在离开的那一条：别的会话（含正在跑的那一轮）一个都不碰。
        state.activeConversationId?.let { left -> releaseConversation(left) }
        updateState { copy(activeConversationId = null) }
        // 离开的那份照 keep-warm 计时（G4），与切会话同一条规矩（"新建对话"不影响这条规则）。
        keepWarm(leftKey)
        // 还没发第一条消息的新会话有它自己那一份空状态（见 [ConversationChats.NEW_CONVERSATION]）。
        updateConversation { copy(messages = emptyList(), isSending = false) }
        // 新会话的窗口按当前模型预设定；它第一次附着时核心会把这个值写进这条会话的文件。
        // 档位先照模型的默认档显示：附着时核心会定下这条会话自己的起点档（目录里有就用目录那份），
        // 随后从核心读回来。
        updateConversation {
            copy(
                contextWindow = newConversationContextWindow(),
                reasoningEffort = state.activeModel?.reasoningEffort.orEmpty(),
            )
        }
    }

    /**
     * 推理档位改的是**当前会话**：交给核心追加一条记录（会话文件里因此留下完整的变化史），请求从这
     * 一刻起用新值。供应商页那个模型级的档位不动 —— 它只作为"新建对话时抄一次"的起点。
     */
    /**
     * 选了以后：界面立刻跟上；还没附着会话就先记下来，附着那一刻交给核心。
     */
    private fun handleThoughtLevelSelected(action: ChatAction.ThoughtLevelSelected) {
        // 选了就是选了：界面立刻跟上。
        updateConversation { copy(reasoningEffort = action.value) }
        // 还没附着（新对话、第一条消息还没发出去）：核心那边没有会话文件可写，先记下来，
        // 附着那一刻再交给它 —— 与窗口那条 pending 同一个做法。
        val conversationId = state.activeConversationId
        if (conversationId == null || attachedKeys[conversationId] == null) {
            pendingReasoningEffort = action.value
            return
        }
        // 写进会话文件是**出站命令**：走 Effect。成功 → Applied（带核心读回的值）；
        // 失败 → Rejected（回退到核心值 + 提示）—— 界面不会停在一个没落定的档上。
        effects.send(ChatEffect.SetReasoningEffort(conversationId, action.value))
    }

    private fun handleModelSelected(action: ChatAction.ModelSelected) {
        val isSameSelection =
            state.activeProviderId == action.providerId && state.activeModelId == action.modelId
        if (isSameSelection) {
            updateState { copy(isModelPickerOpen = false) }
            return
        }
        // An ADK session is bound to one model, so a switch re-attaches it. The
        // stored session (and the transcript) stay.
        state.activeConversationId?.let { id -> resetSession(id) }
        val fallbackProviderId = state.activeProviderId
        val fallbackModelId = state.activeModelId
        updateState {
            copy(
                activeProviderId = action.providerId,
                activeModelId = action.modelId,
                isModelPickerOpen = false,
            )
        }
        // 会话里的窗口不跟着模型走：它在这条会话第一次附着时就定下了。只有还没有会话
        // （新会话）时，界面上的窗口才跟着换后的模型预设。
        if (state.activeConversationId == null) {
            updateConversation {
                copy(
                    contextWindow = newConversationContextWindow(),
                    // 换模型了：还没发第一条消息，档位先照新模型的默认档显示。
                    reasoningEffort = state.activeModel?.reasoningEffort.orEmpty(),
                )
            }
        }
        // 落盘是出站命令（D3）：失败经 ActiveModelPersistRejected 守卫回滚（D4）。
        // 在途期间置上守卫，别让偏好流的旧值把这次选择盖回去（F8）。
        pendingActiveModel = action.providerId to action.modelId
        effects.send(
            ChatEffect.PersistActiveModel(
                providerId = action.providerId,
                modelId = action.modelId,
                fallbackProviderId = fallbackProviderId,
                fallbackModelId = fallbackModelId,
            )
        )
        // 面板列哪几档也跟着模型走（目录里有的模型用目录那份）：只读 + 回流。
        val provider = state.activeProvider
        val modelId = state.activeModel?.modelId
        if (provider != null && modelId != null) {
            viewModelScope.launch { refreshAllowedEfforts(provider.id, modelId) }
        }
    }

    private fun handleConversationSelected(action: ChatAction.ConversationSelected) {
        // 选中的就是当前这条：抽屉由调用方（侧边栏）负责关，这里什么都不用做 ——
        // 否则会把 ADK session 重建、转写重读一遍，白费一次。
        if (action.id == state.activeConversationId) return

        // 换掉当前会话：代次 +1，让在途的"会话创建完成"作废（见 [conversationEpoch]）。
        conversationEpoch++
        // 离开的那一份（还在显示时可能是"新建但还没发第一条消息"那份，见 [displayKey]）。
        val leftKey = displayKey()
        // 只放掉正在离开的那一条（下次往它发消息会重挂）；目标那条不碰，正在跑的那一轮也不碰。
        state.activeConversationId?.let { left -> releaseConversation(left) }
        updateState { copy(activeConversationId = action.id) }
        // 离开的那份从这一刻起不再是"正在显示的那条"，照 keep-warm 计时（G4）：冷掉即被回收，
        // 切遍 N 条会话不会常驻 N 份消息列表。**按"离开"排期而不是按"进入"**：一直看着它超过窗口
        // 再切走时，按进入排的那次到期会被守卫（那一刻它还在显示）放过去，此后就没有第二次计时了 ——
        // 条目会一直留到 ViewModel 销毁。到期那一刻若它又跑了新一轮、或被切了回来，handler 的守卫
        // 同样会把它留下。
        keepWarm(leftKey)
        // 内存里已经有这条会话（那一轮还在跑，或还在 keep-warm 里）：投影随 activeConversationId
        // 自动切过去（D1）—— 已经流出去的片段都还在，接着渲染（照 ZCode 的 acquire 命中）。
        // 冷掉了才回落到从核心读转写。
        if (chats.has(action.id)) {
            viewModelScope.launch { refreshConversationFacts(action.id) }
            return
        }
        updateChat(action.id) { copy(messages = emptyList(), isSending = false) }
        viewModelScope.launch {
            val messages = runCatching { conversationStore.messagesOf(action.id) }
                .getOrDefault(emptyList())
            // 与启动恢复同一条规矩：正文解析（native）走 parseDispatcher，主线程不跑几百次 JNI。
            val fallbackModelLabel = modelLabelOf(action.id)
            val restored = withContext(parseDispatcher) {
                messages.map {
                    it.toChatMessage(
                        fallbackModelLabel = fallbackModelLabel,
                        parserFactory = markdownParserFactory,
                    )
                }
            }
            // 守卫搬进 handler（"读-判-写"全在一帧里）：选中的会话中途又变了，这条就被丢弃。
            sendAction(ChatAction.Internal.TranscriptLoaded(action.id, restored))
            // 窗口/用量/档位：同一个 launch 里再发一条，各自带自己的守卫。
            refreshConversationFacts(action.id)
        }
    }

    /**
     * 读会话状态失败时的统一出口（F7）。
     *
     * 这一系列读口（会话窗口 / 用量 / 档位 / 未完成回合 / 目录）以前都用 `runCatching{}.getOrNull()`
     * 或 `.getOrDefault(emptyList())` 把失败抹成"没有"：窗口被悄悄换成模型预设、档位显示成"未设置"、
     * "有没有未完成回合"变成"没有"（发送键上的「继续」无声消失）、目录读坏变成"不限制"（面板列出
     * 全部档位）。读失败**不再下结论**，并且提示一次。
     */
    private fun reportReadFailure(messageRes: Int, error: Throwable?) {
        Log.w(TAG, "会话状态读取失败：${error?.message}", error)
        sendEvent(ChatUiEvent.ShowToast(messageRes))
    }

    /** 读这条会话自己的窗口 / 用量 / 档位并回流；读不出来就**不回流**（F7）。 */
    private suspend fun refreshConversationFacts(conversationId: String) {
        readConversationFacts(conversationId)?.let { facts ->
            sendAction(ChatAction.Internal.ConversationFactsLoaded(facts))
        }
    }

    /** 读"这个模型允许哪些档位"并回流；读不出来就**不回流**（F7）。 */
    private suspend fun refreshAllowedEfforts(providerId: String, modelId: String) {
        readAllowedEfforts(providerId, modelId)?.let { levels ->
            sendAction(
                ChatAction.Internal.AllowedEffortsLoaded(
                    providerId = providerId,
                    modelId = modelId,
                    levels = levels,
                )
            )
        }
    }

    /**
     * 打开一条会话时，把它自己的三样东西从文件里读回来：窗口、上次报的用量、档位。
     *
     * 窗口必须用它的（否则设置那一栏显示的会和这条会话实际用的不是一回事）；用量只活在内存里
     * 的话，进程重启后面板就空了 —— 文件里的那份是重启后唯一的来源；档位是这条会话自己最后
     * 一条记录，没记过就是未设置。
     *
     * **只读，不写 state**：结果由调用方以 [ChatAction.Internal.ConversationFactsLoaded] 回流，
     * 由 handler 同步落状态（R2）。窗口从没记过（会话还没附着过）时由 handler 按当前模型预设推一个。
     *
     * **读失败返回 `null`**（F7）：不下结论、不回流 —— 三处 `getOrNull()` 会把"读失败"塞进与
     * "核心从没记过"同一个 `null`，于是界面把预设当成这条会话的值显示出来。
     */
    private suspend fun readConversationFacts(conversationId: String): ConversationFacts? {
        val storedWindow = runCatching { agentChat.conversationContextWindow(conversationId) }
        val storedUsage = runCatching { agentChat.conversationUsage(conversationId) }
        val storedEffort = runCatching { agentChat.conversationReasoningEffort(conversationId) }
        val failure = listOf(storedWindow, storedUsage, storedEffort)
            .firstOrNull { it.isFailure }
            ?.exceptionOrNull()
        if (failure != null) {
            reportReadFailure(R.string.chat_conversation_facts_failed, failure)
            return null
        }
        return ConversationFacts(
            conversationId = conversationId,
            window = storedWindow.getOrNull(),
            usage = storedUsage.getOrNull(),
            effort = storedEffort.getOrNull(),
        )
    }

    /** 还没有会话时界面上的窗口：当前模型预设，没填就按默认值。 */
    private fun newConversationContextWindow(): Long {
        val preset = state.activeModel?.contextLength ?: 0
        return if (preset > 0) preset.toLong() else DEFAULT_CONTEXT_WINDOW_TOKENS
    }

    private fun handleConversationDeleted(action: ChatAction.ConversationDeleted) {
        // 出站命令走 Effect：失败经 EffectFailed → ChatUiEvent.ShowError 回流（§3 第 13 条）；
        // 成功不需要回执 —— 列表由 conversationsStateFlow 回灌。
        effects.send(ChatEffect.DeleteConversation(action.id))
        if (state.activeConversationId == action.id) {
            // 删的正是当前会话：代次 +1，在途的会话创建结果作废。
            conversationEpoch++
            releaseConversation(action.id)
            updateState { copy(activeConversationId = null) }
            chats.drop(action.id)
            updateConversation { copy(messages = emptyList(), isSending = false) }
        }
    }

    // endregion

    // region Turn handling

    private suspend fun runTurn(
        turn: Turn,
        provider: ProviderConfig,
        model: ModelConfig,
        text: String,
    ) {
        // 创建 / 附着期间用户可能已经把界面换到别处了 —— 那就**别往下发**：用户已经不在看它，
        // 发出去只是白花 token，而且那条消息会孤零零留在会话里没有回复。
        val epoch = conversationEpoch
        try {
            val id = ensureConversation(provider, model, text) ?: throw IllegalStateException(
                "Could not open a conversation to send into."
            )
            if (epoch != conversationEpoch) return

            val key = "$id|${provider.id}|${model.id}"
            if (attachedKeys[id] != key) {
                // The ADK session carries the conversation's own id, so the model's
                // stored context and the transcript share one identity and a
                // resumed conversation is loaded rather than rebuilt.
                agentChat.startConversation(
                    sessionId = id,
                    provider = provider,
                    modelId = model.modelId,
                    instruction = CHAT_PERSONA,
                    settings = agentSettings(),
                )
                // 注册表只能由 handler 写（D2）：附着结果回流，本协程不动 attachedKeys。
                sendAction(ChatAction.Internal.ConversationAttached(id, key))
                syncContextWindowAfterAttach(id)
                syncReasoningEffortAfterAttach(id)
                // 附着期间被换掉了：这一轮的请求不能发出去（它会被算到新会话头上）。
                if (epoch != conversationEpoch) return
            }

            collectEvents(turn, agentChat.send(id, text))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            sendAction(
                ChatAction.Internal.TurnFailed(
                    turn.id,
                    error.message ?: error::class.simpleName.orEmpty(),
                )
            )
        }
    }

    /**
     * Maps a turn's events onto actions. Shared by the first send and by the resume
     * after the user answers — both stream the same kinds of event.
     *
     * 事件**按轮次路由**：每条都带上 [Turn.id]，落到**它自己那条会话**的状态上（见 [updateTurn]），
     * 界面只投影当前显示的那一条。所以切走之后它照旧在后台渲染、切回来接着看，也不会串到别的会话上
     * —— 照 ZCode 的按 topic 路由；而且**几条会话可以同时各跑各的**。
     */
    private suspend fun collectEvents(turn: Turn, events: Flow<ChatEvent>) {
        events.collect { event ->
            val id = turn.id
            sendAction(
                when (event) {
                    is ChatEvent.Text -> ChatAction.Internal.ReplyChunk(id, event.text)
                    is ChatEvent.Reasoning -> ChatAction.Internal.ReasoningChunk(id, event.text)
                    is ChatEvent.ToolCall ->
                        ChatAction.Internal.ToolCalled(id, event.name, event.arguments)
                    is ChatEvent.ToolResult ->
                        ChatAction.Internal.ToolReturned(id, event.name, event.result)
                    is ChatEvent.UserPromptRequested ->
                        ChatAction.Internal.PromptRequested(id, event.prompt, event.options)
                    is ChatEvent.Failed -> ChatAction.Internal.TurnFailed(id, event.detail, event.kind)
                    ChatEvent.Completed -> ChatAction.Internal.TurnCompleted(id)
                    is ChatEvent.Aborted -> ChatAction.Internal.TurnInterrupted(id, event.durationMs)
                    is ChatEvent.Usage -> ChatAction.Internal.UsageReceived(id, event.usage)
                }
            )
        }
    }

    /**
     * The agent stopped to ask something. Its event flow ends here, so the usual
     * end-of-turn path releases the composer; [ChatState.pendingPrompt] is what
     * keeps the ordinary input out of the way until the question is answered.
     *
     * 提问**属于它那条会话**（[ConversationChatState.pendingPrompts]）：几条会话可以同时各挂着
     * 自己的问题，答这条不会顶掉那条。模型可能在一轮里同时挂出多个交互调用（实测
     * `get_user_choice` + `adk_request_input`）：界面一次只问一个，答完当前这个再问下一个，答案按
     * 同样顺序攒着，全部收齐后**一起**提交 —— 少交一个，历史里就会留下没有结果的 tool_call，之后
     * 每次请求都被服务端 400 拒掉。
     */
    private fun handlePromptRequested(turn: Turn, action: ChatAction.Internal.PromptRequested) {
        // Close the open segment: whatever the model wrote before asking stays put, and
        // the empty placeholder created on send is dropped rather than left as a blank
        // bubble for the whole time the user takes to answer.
        sealAssistantSegment(turn)
        // 展示队首：后面还有问题的话，答完这个会自动接着问（见 handlePromptAnswered）。
        updateTurn(turn) {
            copy(pendingPrompts = pendingPrompts + ChatUserPrompt(action.prompt, action.options))
        }
    }

    /** Sends the answer back and streams the rest of the paused turn. */
    private fun handlePromptAnswered(action: ChatAction.PromptAnswered) {
        val answer = action.answer.trim()
        // 界面问的是**当前显示这条**会话的问题。
        val pending = state.conversation.pendingPrompts
        if (answer.isEmpty() || pending.isEmpty() || state.isSending) return
        val remaining = pending.drop(1)
        val answersSoFar = state.conversation.promptAnswers + answer

        if (remaining.isNotEmpty()) {
            // 还有下一个问题：继续问。这里**不算**在发送 —— 模型仍在等答案；答案先攒着。
            updateConversation { copy(pendingPrompts = remaining, promptAnswers = answersSoFar) }
            return
        }

        updateConversation {
            copy(pendingPrompts = emptyList(), promptAnswers = emptyList(), isSending = true)
        }
        val turn = beginTurn(displayKey())
        launchTurn(turn) { active -> resumeTurn(active, answersSoFar) }
    }

    private suspend fun resumeTurn(turn: Turn, answers: List<String>) {
        try {
            collectEvents(turn, agentChat.respondToPrompts(turn.chatKey, answers))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            sendAction(
                ChatAction.Internal.TurnFailed(
                    turn.id,
                    error.message ?: error::class.simpleName.orEmpty(),
                )
            )
        }
    }

    /**
     * Creates the conversation row on the first send; [title] comes from that message.
     *
     * **不写 state**（第 9 条）：只创建并返回 id，回合自己用这个返回值往下走；把 id 写进状态
     * 那一步以 [ChatAction.Internal.ConversationCreated] 回流，并带上发起时的 [conversationEpoch]
     * ——创建期间用户换了会话，这条就被丢弃（否则一条已被放弃的会话会"复活"成当前会话）。
     */
    private suspend fun ensureConversation(
        provider: ProviderConfig,
        model: ModelConfig,
        title: String,
    ): String? {
        state.activeConversationId?.let { return it }
        val epoch = conversationEpoch
        val created = runCatching {
            conversationStore.createConversation(
                providerId = provider.id,
                // 写进会话文件的是**线上模型名**（与 attach 时传给核心的一致），不是模型条目的 id。
                modelId = model.modelId,
                title = title.take(TITLE_MAX_LENGTH),
            )
        }.getOrNull() ?: return null
        sendAction(ChatAction.Internal.ConversationCreated(epoch, created.id))
        return created.id
    }

    /**
     * Appends streamed text, opening a new assistant segment when the previous one
     * was closed by a tool call.
     *
     * ## FULL semantics — matches ima's `gt.h0.g(String, Continuation)`
     *
     * ima drives its renderer two ways, and **all seven call sites use the FULL one**:
     * the renderer is handed the whole accumulated text each time and re-parses it from
     * scratch (`b6/a` mode `gt.c0.b` → `parser.reset(); parser.append(full)`), skipping
     * only when the incoming length equals the length it last parsed
     * (`if (str.length() == 0 || this.l == str.length()) return`).
     *
     * This mirrors that exactly: accumulate, `reset()`, append the whole thing, remember
     * the length. The only difference from ima's call shape is *where* accumulation
     * happens — the transport hands us deltas, so we accumulate here instead of in the
     * caller. The text the engine ends up holding is identical.
     *
     * ## What this does and does not cost
     *
     * Block-level incrementality is unaffected: `Update.index` is still the truncation
     * point, so `applied()` reuses every block before it and Compose only redraws what
     * changed. What FULL costs is the re-parse itself — O(n) per chunk instead of O(tail),
     * which is exactly why ima pairs it with the length guard above.
     *
     * A side benefit of FULL: it is self-healing. If the parser is dropped mid-segment
     * (a tool call closes the segment, `finalizeStreamInto` closes the handle), the next
     * chunk rebuilds the whole document from `message.text` rather than appending to an
     * empty buffer.
     */
    private fun appendReplyChunk(turn: Turn, text: String) {
        val targetId = turn.streamingMessageId ?: startAssistantSegment(turn)
        // 累加的基准是**这一轮那条会话**里的当前文本：切走之后它自己的文本也必须是它自己的。
        val fullText = (turnMessages(turn).firstOrNull { it.id == targetId }?.text ?: "") + text
        // Same guard as ima's g(): nothing new to parse, so leave the state alone.
        if (fullText.isEmpty() || fullText.length == turn.parsedLength) return
        turn.parsedLength = fullText.length
        // 文字先落进状态：这一步很便宜，而且要立刻可见（块还没解析出来时界面按 text 渲染）。
        updateTurn(turn) {
            copy(
                messages = messages.map { message ->
                    if (message.id == targetId) message.copy(text = fullText) else message
                }
            )
        }
        // 块交给后台解析（见 [Turn.commands]）。同一时刻只排一次：工作协程跑完会再取一次
        // 最新文本，期间到达的分片自然合并掉。
        turn.pendingParse.set(StreamParseRequest(targetId, fullText))
        if (turn.parseQueued.compareAndSet(false, true)) {
            sendStreamCommand(turn, StreamCommand.Parse)
        }
    }

    /**
     * 起一轮：解析 worker 是**这一轮的子协程**，不再"发射即忘"。
     *
     * 回合正常结束时：先把通道关掉 → worker 把还排着的命令（包括那条收尾）跑完 → `join` 等它收干净，
     * 这一轮才算结束 —— 所以一轮之后**不会有一份忘了收的解析**再去碰主线程（以前那版是随 ViewModel
     * 起的长命 worker，轮到下一轮甚至下一个测试都可能被它回来撞一下：`Dispatchers.Main is used
     * concurrently with setting it`）。这一轮被取消时（换会话 / 停止 / ViewModel 销毁）worker 跟着
     * 一起取消。
     */
    private fun beginTurn(key: String): Turn {
        val turn = Turn(id = UUID.randomUUID().toString(), chatKey = key)
        turns[turn.id] = turn
        return turn
    }

    /**
     * 起一轮（见 [beginTurn]）：解析 worker 是**这一轮的子协程**，不再"发射即忘"。
     *
     * 回合正常结束时：先把通道关掉 → worker 把还排着的命令（包括那条收尾）跑完 → `join` 等它收干净，
     * 这一轮才算结束 —— 所以一轮之后**不会有一份忘了收的解析**再去碰主线程（以前那版是随 ViewModel
     * 起的长命 worker，轮到下一轮甚至下一个测试都可能被它回来撞一下：`Dispatchers.Main is used
     * concurrently with setting it`）。这一轮被取消时（ViewModel 销毁）worker 跟着一起取消。
     *
     * 收尾只影响它自己那条会话：别的会话那一轮照旧在跑（见 [Turn]）。
     */
    private fun launchTurn(turn: Turn, block: suspend (Turn) -> Unit) {
        turn.job = viewModelScope.launch {
            val worker = launch { streamParseLoop(turn) }
            try {
                block(turn)
            } finally {
                turn.commands.close()
                worker.join()
                // 撤销登记**排在最后**：这一轮剩下的输出（还有 worker 贴回来的那些）都还在动作队列里，
                // 先让它们落到它自己那条会话上（见 [ChatAction.Internal.TurnRetired]）。
                sendAction(ChatAction.Internal.TurnRetired(turn.id))
                keepWarm(turn.chatKey)
            }
        }
    }

    /**
     * 让这条会话在内存里再热一阵（ZCode 的 keep-warm）：期间切回来还是内存里这份（已经流出去的
     * 片段都在，接着看）；冷掉之后才回落到读转写。
     *
     * 两个排期点：**回合收尾**（这一轮不再往它上面写东西了），以及**离开这条会话**（切走 /
     * 新建对话，G4）—— 以前只有前者，于是纯浏览过的会话会一直留在 `chats` 里，切遍 N 条会话就
     * 常驻 N 份消息列表，直到 ViewModel 销毁。
     *
     * 按"离开"排期而不是按"进入"：正显示着的那份到期时守卫一定放它过去，只有离开之后那次到期才有
     * 意义（[ChatAction.Internal.KeepWarmExpired] 里那一帧的判定）。
     *
     * 到期只发信号：丢不丢由 handler 在**那一帧**判定（又跑了新一轮、或者正显示着它，就不丢）——
     * 协程里直接 drop 是异步写影子状态（D2 收编的口子）。
     */
    private fun keepWarm(key: String) {
        viewModelScope.launch {
            delay(ConversationChats.KEEP_WARM_MS)
            sendAction(ChatAction.Internal.KeepWarmExpired(key))
        }
    }

    /**
     * 投递一条解析结果，**等 handler 贴完才返回**。
     *
     * 这是这一轮唯一"把解析贴进状态"的入口：worker 自己不碰 state（R1），而握手让"在途结果恒为
     * 1 条"——生产速度被消费速度钳住，于是今天靠"贴块发生在 worker 内"得到的隐含背压，改后由这里
     * 提供；同时 `worker.join()` 重新等价于"这一轮不再碰状态"（[launchTurn] 的承诺）。
     */
    private suspend fun deliverParsed(
        turn: Turn,
        targetId: String,
        update: MarkdownUpdate?,
        trailingBlock: MarkdownBlock?,
    ) {
        val ack = CompletableDeferred<Unit>()
        sendAction(ChatAction.Internal.StreamParsed(turn.id, targetId, update, trailingBlock, ack))
        ack.await()
    }

    /**
     * 流式解析的后台工作协程：**只有它碰 native 解析器句柄**。
     *
     * 一个协程顺序处理所有命令，所以句柄永远不会被两个线程同时使用（native 侧不是线程安全的）；
     * 命令按入队顺序处理，`Parse` 又会在自己内部把待办文本排空，因此「先解析完、再收尾」的顺序
     * 天然成立，不需要额外加锁。
     *
     * 它活多久由 [launchTurn] 决定：通道一关，它把还排着的命令跑完、释放句柄，然后自己结束。
     */
    private suspend fun streamParseLoop(turn: Turn) {
        val commands = turn.commands
        var parser: MarkdownParser? = null
        for (command in commands) {
            when (command) {
                StreamCommand.Parse -> {
                    while (true) {
                        val request = turn.pendingParse.getAndSet(null) ?: break
                        val startedAt = System.currentTimeMillis()
                        // FULL: re-parse from scratch rather than appending the delta.
                        val update = withContext(parseDispatcher) {
                            val active = parser ?: markdownParserFactory.create().also { parser = it }
                            active.reset()
                            active.append(request.text)
                        }
                        val parsedAt = System.currentTimeMillis()
                        // 贴块不再由 worker 直接做（那会绕过 action 通道）：投递 + 等回执。
                        // "贴块 Xms" 那条日志随之移进 handler（同一件事、同一线程，只是归属换位置）。
                        deliverParsed(turn, request.targetId, update, trailingBlock = null)
                        Log.d(
                            CHAT_PARSE_TAG,
                            "解析 ${request.text.length} 字 耗时 ${parsedAt - startedAt}ms"
                        )
                        // 节流：贴块 + 重组才是主线程上的成本，控制它的频率。
                        delay(STREAM_PARSE_MIN_INTERVAL_MS)
                    }
                    turn.parseQueued.set(false)
                    // 收尾竞态：清标志之后、工作协程再次挂起之前又来了新文本，就补排一次。
                    if (turn.pendingParse.get() != null && turn.parseQueued.compareAndSet(false, true)) {
                        commands.trySend(StreamCommand.Parse)
                    }
                }

                is StreamCommand.Finalize -> {
                    // 排在前面的 Parse 已经把文本排空了，这里直接收尾。
                    val targetId = command.targetId
                    val update = withContext(parseDispatcher) {
                        if (targetId == null) null else parser?.finalizeStream()
                    }
                    // targetId == null（整轮只有工具调用、没有正文段）就没有东西要贴：只关句柄、
                    // 不发 action。有目标时一次投递带上"先 update 后尾块"，顺序由 handler 保证。
                    if (targetId != null) {
                        deliverParsed(turn, targetId, update, command.trailingBlock)
                    }
                    withContext(parseDispatcher) {
                        parser?.close()
                        parser = null
                    }
                }

                StreamCommand.Close -> withContext(parseDispatcher) {
                    parser?.close()
                    parser = null
                }
            }
        }
        // 通道关了（这一轮收尾）：把句柄放掉再结束。句柄只归这个协程，所以在它里面放。
        withContext(parseDispatcher) {
            parser?.close()
            parser = null
        }
    }

    /**
     * 思考也是增量，直接往当前那条流式消息上累加。
     *
     * 它不走 Markdown 那条路（思考是纯文本），也不会开新段：思考通常先于正文到达，本来就属于同一条
     * 回复。
     */
    private fun appendReasoningChunk(turn: Turn, chunk: String) {
        // 与正文同一条规则：手上没有正在写的段就开一段。工具调用会把上一段收掉，而模型在工具返回
        // 之后往往还要再想一段 —— 那时 streamingMessageId 已经是空的，直接 return 就会把这一段思考
        // 整个丢掉（出现了"实时只看到第一段、重启后才看到第二段"）。
        val targetId = turn.streamingMessageId ?: startAssistantSegment(turn)
        updateTurn(turn) {
            copy(
                messages = messages.map { message ->
                    if (message.id == targetId) {
                        message.copy(thinking = message.thinking + chunk)
                    } else {
                        message
                    }
                }
            )
        }
    }

    /** 把一次解析结果贴到 [targetId] 那条消息上（块列表按 id 复用，Compose 只重绘变化的块）。 */
    private fun applyStreamBlocks(turn: Turn, targetId: String, update: MarkdownUpdate) {
        updateTurn(turn) {
            copy(
                messages = messages.map { message ->
                    if (message.id == targetId) {
                        message.copy(blocks = IncrementalMarkdownDocument.applied(update, message.blocks))
                    } else {
                        message
                    }
                }
            )
        }
    }

    /** 往 [targetId] 的块列表末尾追加一块（只用于收尾之后的失败原因，见 StreamCommand.Finalize）。 */
    private fun appendBlock(turn: Turn, targetId: String, block: MarkdownBlock) {
        updateTurn(turn) {
            copy(
                messages = messages.map { message ->
                    if (message.id == targetId) {
                        message.copy(blocks = message.blocks + block)
                    } else {
                        message
                    }
                }
            )
        }
    }

    /** Opens an assistant segment and returns its id. */
    private fun startAssistantSegment(turn: Turn): String {
        // A segment owns its parser; the previous one (if any) is done with.
        closeStreamParser(turn)
        val id = UUID.randomUUID().toString()
        turn.streamingMessageId = id
        turn.assistantIds += id
        val modelLabel = state.activeModel?.modelId
        updateTurn(turn) {
            copy(
                messages = messages + ChatMessage(
                    id = id,
                    role = ChatRole.ASSISTANT,
                    text = "",
                    isStreaming = true,
                    timestamp = System.currentTimeMillis(),
                    modelLabel = modelLabel,
                )
            )
        }
        return id
    }

    /**
     * Runs the engine's end-of-stream pass and folds the result into the message.
     *
     * This is what closes the block a chunk left open mid-line: a table still missing
     * its delimiter row, a fence without its closing marker, a trailing paragraph.
     * `finalizeStream()` returns final blocks for the tail; `applied()` merges them
     * the same way a chunk update is merged.
     */
    private fun finalizeStreamInto(turn: Turn, targetId: String?, trailingBlock: MarkdownBlock? = null) {
        // 交给这一轮的工作协程：它排在前面那些 Parse 之后，顺序天然正确（见 [launchTurn]）。
        sendStreamCommand(turn, StreamCommand.Finalize(targetId, trailingBlock))
    }

    private fun closeStreamParser(turn: Turn) {
        // Length tracking belongs to the parser instance, so it goes with it.
        turn.parsedLength = 0
        sendStreamCommand(turn, StreamCommand.Close)
    }

    /**
     * A tool call closes the current assistant segment: text written before the call
     * is the model's preamble and the answer after the result is a separate bubble,
     * so the transcript keeps the order things actually happened in.
     */
    private fun appendToolCall(turn: Turn, action: ChatAction.Internal.ToolCalled) {
        sealAssistantSegment(turn)
        appendToolEntry(turn, name = action.name, detail = action.arguments)
    }

    /**
     * 工具返回并进上面那张调用卡片 —— 一次调用的「问了什么 / 回了什么」放在同一张卡里
     * （用户对提问的回答也走这里）。只有找不到配对调用时才单独成条。
     */
    private fun appendToolResult(turn: Turn, action: ChatAction.Internal.ToolReturned) {
        // 按名字往前找最近一张「同名、还没有返回」的调用卡，与重建那条路径（`ConversationStore`
        // 的 absorbIntoOpenCall）规则一致。只认紧邻上一条的话，一条事件里并行调用的几个工具
        // 会各自多出一张独立的「xxx 返回」卡片，重启前后就对不上了。
        val open = turnMessages(turn).lastOrNull { message ->
            val tool = message.tool
            tool != null && !tool.isResultOnly && tool.result == null && tool.name == action.name
        }
        if (open != null) {
            updateTurn(turn) {
                copy(
                    messages = messages.map { message ->
                        if (message.id == open.id) {
                            message.copy(
                                tool = message.tool?.copy(
                                    result = action.result,
                                    status = ChatToolStatus.COMPLETED,
                                )
                            )
                        } else {
                            message
                        }
                    }
                )
            }
            return
        }
        appendToolEntry(turn, name = action.name, detail = "", result = action.result)
    }

    /**
     * 把"还没有结果"的工具卡一次标成给定状态（失败 / 已停止）。
     *
     * 照 ZCode：状态是执行侧推进的，所以回合失败或用户停止时，当时开着的卡要落到 failed / stopped，
     * 而不是永远停在"执行中"。
     */
    private fun markOpenTools(turn: Turn, status: ChatToolStatus) {
        updateTurn(turn) {
            copy(
                messages = messages.map { message ->
                    val tool = message.tool
                    if (tool != null && tool.result == null && tool.status == ChatToolStatus.RUNNING) {
                        message.copy(tool = tool.copy(status = status))
                    } else {
                        message
                    }
                }
            )
        }
    }

    private fun appendToolEntry(turn: Turn, name: String, detail: String, result: String? = null) {
        updateTurn(turn) {
            copy(
                messages = messages + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    text = "",
                    tool = ChatToolActivity(
                            name = name,
                            detail = detail,
                            result = result,
                            // 还没有结果就是在跑；带上结果才算完成（照 ZCode 的状态机）。
                            status = if (result == null) {
                                ChatToolStatus.RUNNING
                            } else {
                                ChatToolStatus.COMPLETED
                            },
                        ),
                    timestamp = System.currentTimeMillis(),
                )
            )
        }
    }

    /**
     * Ends the streaming segment. An empty one is removed rather than sealed: it is
     * the placeholder created on send, and a turn that opens with a tool call should
     * not leave a blank bubble above the tool entry.
     */
    private fun sealAssistantSegment(turn: Turn) {
        val targetId = turn.streamingMessageId ?: return
        turn.streamingMessageId = null
        // 「空」是指没东西可显示：**只想了、还没开口的那一段不算空**。按文本判空会把刚拿到的思考
        // 连同这一段一起删掉 —— 那正是"思考完、正文一出现就不见了"的原因。
        val isEmpty = turnMessages(turn).firstOrNull { it.id == targetId }
            ?.let { it.text.isNullOrEmpty() && it.thinking.isEmpty() }
            ?: true
        // Close the segment's blocks before dropping the parser (no-op when empty).
        if (!isEmpty) finalizeStreamInto(turn, targetId) else closeStreamParser(turn)
        updateTurn(turn) {
            copy(
                messages = if (isEmpty) {
                    messages.filterNot { it.id == targetId }
                } else {
                    messages.map {
                        if (it.id == targetId) {
                            it.copy(
                                isStreaming = false,
                                // 收尾时结算这段思考的时长（照 ZCode：时长随行一起给界面）。
                                thinkingMs = it.thinking
                                    .takeIf(String::isNotEmpty)
                                    ?.let { _ -> System.currentTimeMillis() - it.timestamp },
                            )
                        } else {
                            it
                        }
                    }
                }
            )
        }
        if (isEmpty) turn.assistantIds.remove(targetId)
    }

    private fun failTurn(turn: Turn, detail: String, kind: ChatFailureKind) {
        // A turn that already closed its segment on a tool call has nowhere to show
        // the failure, so open one rather than swallowing it.
        val targetId = turn.streamingMessageId ?: startAssistantSegment(turn)
        turn.streamingMessageId = null
        val errorBlock = MarkdownBlock(
            id = "error:${UUID.randomUUID()}",
            type = MarkdownBlockType.PARAGRAPH,
            isClosed = true,
            content = listOf(MarkdownInline(MarkdownInlineType.TEXT, literal = detail)),
        )
        // Close the partial answer's open block first, then append the reason as a
        // block of its own so it renders through the same path as everything else.
        // 尾块必须交给收尾去做（见 StreamCommand.Finalize）—— 这里直接加会被 applied() 截掉。
        finalizeStreamInto(turn, targetId, errorBlock)
        val finalText = turnMessages(turn).firstOrNull { it.id == targetId }?.let { message ->
            if (message.text.isEmpty()) detail else "${message.text}\n\n$detail"
        }
        updateTurn(turn) {
            copy(
                isSending = false,
                messages = messages.map { message ->
                    if (message.id != targetId) {
                        message
                    } else {
                        message.copy(
                            text = finalText.orEmpty(),
                            isStreaming = false,
                            isError = true,
                            // 分型随消息一起留在内存里：界面据它给一句"能怎么办"的提示。历史（从核心读回的
                            // 转写）没有这条信息 —— 核心不记它，所以恢复出来的失败消息不带分型。
                            failureKind = kind,
                        )
                    }
                },
            )
        }
        endTurn()
    }

    private fun finishTurn(turn: Turn) {
        val targetId = turn.streamingMessageId
        turn.streamingMessageId = null
        // Close whatever the last chunk left open before the turn ends.
        finalizeStreamInto(turn, targetId)
        updateTurn(turn) {
            copy(
                isSending = false,
                // 回合真正收尾了：中断请求的状态位在这里落下（TurnCompleted 侧，见 [handleStopClicked]）。
                isInterruptRequested = false,
                messages = messages.map { message ->
                    if (message.id == targetId) message.copy(isStreaming = false) else message
                },
            )
        }
        endTurn()
    }

    /**
     * 回合收尾的收口点。**不写任何东西、也不再需要重读列表**（修复方案 D5）：
     *
     * - 转写由核心在回合跑动时自行落盘，这里无话可写；
     * - 会话列表的更新由核心的"存储变了"推送驱动 —— `RustConversationStore` 防抖后重读并回灌
     *   `conversationsStateFlow`。D5 之前这里是"回合结束才刷一次列表"的补丁，那也是跑动期间
     *   列表长期陈旧的原因。
     */
    private fun endTurn() = Unit

    /**
     * 模型回复语言变了：把当前会话**重挂**一次 —— 系统指令只在附着时交给核心，所以下一次发送就用上
     * 新的那句（历史在会话文件里，重挂会读回来）。重挂不掐正在跑的那一轮，见 [resetSession]。
     *
     * 走到这里时语言一定真的变了（上游 `distinctUntilChanged`），所以不必再比对旧值 —— 只需要
     * 区分"第一次读回来"（偏好流的初值，不是用户改的）。
     */
    private fun handleLanguagePreference() {
        if (!languagePreferenceSeen) {
            languagePreferenceSeen = true
            return
        }
        state.activeConversationId?.let { id -> resetSession(id) }
    }

    /**
     * 交给核心的 Agent 设置：回复语言的**值** + 界面当前语言（"跟随应用语言"那档要用）。
     *
     * 两个值都**每次附着时现取**，所以改了界面语言会跟着走，回复语言也不必在本 ViewModel 里
     * 另存一份：真源只有偏好仓库那条流（见 [AgentOutputLanguage]）。规则也不在这里 —— 那条输出
     * 语言规则由**核心**拼（`rust/core/src/agent_settings.rs`），界面只给值。
     */
    private fun agentSettings(): AgentSettings = AgentSettings(
        outputLanguage = userPreferencesRepository.preferencesStateFlow.value.agentOutputLanguage,
        appLanguage = Locale.getDefault().toLanguageTag(),
    )

    /**
     * 用户拨过的那一行工具卡。
     *
     * 只记他的选择：没拨过的行不进这张表 —— 那种行由界面按运行状态推出自动开合
     * （跑着展开、结果回来收起），所以"开始跑时自动展开一次"不会被谁悄悄写坏。
     */
    private fun handleToolRowToggled(action: ChatAction.ToolRowToggled) {
        updateConversation { copy(toolRowOpen = toolRowOpen + (action.rowKey to action.expanded)) }
    }

    /**
     * Drops **one** conversation's attachment so the next send into it re-attaches (its transcript
     * stays, and every other conversation — running or not — is left alone).
     *
     * **刻意不取消进行中的回合**：切走 / 换会话都不该把那条回复掐掉，它会继续跑完（正在跑的那一轮
     * 自己带着它的会话与游标，见 [Turn]）。要停止只有 [handleStopClicked]（输入框那个停止按钮）
     * 这一条路径。
     *
     * 为什么**不**走 Effect 通道（修复方案 D3 的例外，门禁 R11 白名单）：释放必须与"下一次附着"
     * 严格有序 —— Effect 排队期间 `runTurn` 可能已经在 IO 线程上重新 `startConversation`，
     * 排在后面的旧释放令会把新附着割掉（核心对重复 start 是整体重挂，对释放是拆槽）。
     * 核心的释放是本地、幂等操作（未附着 = no-op、无文件 IO），属注册表维护而非出站命令，
     * 所以同步做；失败经 action 回流成可见提示 —— "失败可观察"不打折扣。
     */
    private fun releaseConversation(conversationId: String) {
        runCatching { agentChat.endConversation(conversationId) }
            .onFailure { error ->
                trySendAction(
                    ChatAction.Internal.EffectFailed(
                        tag = END_CONVERSATION_TAG,
                        message = error.message.orEmpty(),
                        conversationId = conversationId,
                    ),
                )
            }
        attachedKeys.remove(conversationId)
        // 还没有会话时界面先选的窗口 / 档位：只对"这条还没附着的新会话"有意义，放掉就等于放弃它们。
        pendingContextWindow = null
        pendingReasoningEffort = null
        updateState { copy(isContextPanelOpen = false) }
    }

    /**
     * 重挂一条会话（换模型 / 换回复语言）：放掉附着，并把它那份提问**作废** —— 重挂之后核心那边是
     * 一个新会话，原来挂着的那个问题已经没有人会答了（留着它会让输入区一直被它挡着）。
     */
    private fun resetSession(conversationId: String) {
        releaseConversation(conversationId)
        updateChat(conversationId) {
            copy(pendingPrompts = emptyList(), promptAnswers = emptyList())
        }
    }

    // endregion

    /** Single mutation point of [mutableStateFlow] (mirrors the other ViewModels). */
    private inline fun updateState(block: ChatState.() -> ChatState) {
        mutableStateFlow.update(block)
    }

    private companion object {
        /**
         * 人格那一句。**输出语言规则不在这里** —— 值随 [AgentSettings] 交给核心，由核心按 qwen-code
         * 那套结构拼在后面（`rust/core/src/agent_settings.rs`：Rule / Exception / 不改技术产物 /
         * 工具输出）。
         */
        const val CHAT_PERSONA = "You are Jasmine, a concise and helpful assistant."

        /** Conversation titles are the first user message, clipped for the list. */
        const val TITLE_MAX_LENGTH = 60

        /** 流式解析的日志标签（`adb logcat -s ChatParse`）。 */
        const val CHAT_PARSE_TAG = "ChatParse"

        /** 本类的通用日志标签。 */
        const val TAG = "ChatViewModel"

        /**
         * [ChatAction.Internal.EffectFailed] 的 tag：会话附着释放失败。释放是注册表维护
         * （必须与下一次附着严格有序），不走 Effect 通道，见 [releaseConversation]。
         */
        const val END_CONVERSATION_TAG = "EndConversation"

        /**
         * 两次「贴块 + 重组」之间的最小间隔。
         *
         * 解析本身已经在后台线程，主线程剩下的是把块合并进状态 + Compose 重组，这部分同样
         * 跟着分片频率走。50ms（约 20 次/秒）比一帧略长，肉眼仍是逐字出来，但主线程的
         * 峰值被压下来了。
         */
        const val STREAM_PARSE_MIN_INTERVAL_MS = 50L
    }
}
