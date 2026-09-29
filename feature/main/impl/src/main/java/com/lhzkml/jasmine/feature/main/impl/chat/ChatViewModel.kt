package com.lhzkml.jasmine.feature.main.impl.chat

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.core.agent.AgentChat
import com.lhzkml.jasmine.core.agent.ChatEvent
import com.lhzkml.jasmine.core.agent.ContextUsage
import com.lhzkml.jasmine.core.agent.ConversationStore
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.ModelConfig
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import com.lhzkml.jasmine.core.data.repository.ProviderRepository
import com.lhzkml.jasmine.core.data.repository.UserPreferencesRepository
import com.lhzkml.jasmine.core.markdown.IncrementalMarkdownDocument
import com.lhzkml.jasmine.core.markdown.IncrementalMarkdownParser
import com.lhzkml.jasmine.core.markdown.MarkdownParser
import com.lhzkml.jasmine.core.markdown.MarkdownParserFactory
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlock
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlockType
import com.lhzkml.jasmine.core.markdown.model.MarkdownInline
import com.lhzkml.jasmine.core.markdown.model.MarkdownInlineType
import com.lhzkml.jasmine.core.markdown.model.MarkdownUpdate
import com.lhzkml.jasmine.core.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 核心的默认上下文窗口（200K）在界面上的镜像；附着会话后立刻被核心报的值覆盖。 */
private const val DEFAULT_CONTEXT_WINDOW_TOKENS = 200_000L

/**
 * One rendered transcript entry: model output, or a tool the agent called.
 *
 * [isStreaming] marks the assistant message currently being written; [isError]
 * marks a turn that failed (its text carries the reason, possibly appended after
 * partial output). [tool] is set instead of [text] on an execution-trace entry.
 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val tool: ChatToolActivity? = null,
    /**
     * 增量解析出的块列表 —— 渲染层用它，[text] 只用于写回会话记录。
     *
     * 这是 ima 的做法：它同样把模型输出以纯文本回写 transcript，而 UI 走
     * Block 列表。块的 `id` 由 native 侧保证跨增量稳定，所以 Compose 能
     * 复用节点、只重绘真正变化的块。
     */
    val blocks: List<MarkdownBlock> = emptyList(),
    /**
     * 这条消息的时间（epoch 毫秒）。0 表示未知（例如历史里没有时间信息的老条目），
     * 界面此时不显示时间。
     */
    val timestamp: Long = 0L,
    /**
     * 产生这条消息的模型名（界面上跟时间并排的小标签）。null = 不知道，不显示。
     *
     * 实时那一轮是发送时生效的模型；从历史恢复时用会话记录里的模型 —— ADK 的事件里
     * 没有模型名，所以会话中途换过模型的话，老消息会显示成会话当前记录的那个模型。
     */
    val modelLabel: String? = null,
    /**
     * 这一条是「上一回合被停止」的状态行：值是那一回合跑了多久（毫秒）。
     *
     * 非 null 时界面只画一行浅灰小字 + 一条分隔线，不画气泡；它两侧的间距与消息之间
     * 的间距相同（见 ChatScreen 的 ChatMessageSpacing）。
     */
    val stoppedAfterMs: Long? = null,
    /**
     * 这条回复之前模型「想过」的内容（深度思考），增量攒起来的。
     *
     * 它不属于正文：界面在正文上方单独画一个可折叠块（见 ChatScreen 的 ReasoningRow）。为空就不画。
     * 思考流通常先于正文到达。
     */
    val thinking: String = "",
    /**
     * 这段思考花了多久（毫秒）；null = 还在想，或者没有记到。
     *
     * 段收尾时结算（段创建的时刻 → 收尾的时刻），界面据此显示「思考 · 持续了 N 秒」（照 ZCode 的
     * `durationMs`：时长是**数据**，不是界面自己算的）。
     */
    val thinkingMs: Long? = null,
)

/**
 * A tool invocation shown inline in the transcript, so the agent's work stays
 * visible while the model itself is silent.
 *
 * 一次调用的「问了什么」和「回了什么」同属这张卡片：[detail] 是参数，[result] 是返回。
 * 返回可能是工具的输出，也可能是用户对提问的回答（那种事件的 author 是 user）。
 *
 * 实时这一轮不落库（转写里存的是回复本身），但重新加载时由 `ConversationStore`
 * 从 session 的事件里还原出同样的形状。
 */
data class ChatToolActivity(
    val name: String,
    /** 调用参数；只有返回、没有配对调用时为空字符串。 */
    val detail: String,
    /** 工具返回；null 表示还没有返回。 */
    val result: String? = null,
    /**
     * 这次调用走到哪一步了 —— 照 ZCode 的 `chat.toolCall.status.*` 六态，状态是**数据**，
     * 界面读它，不再靠"有没有结果"猜。
     *
     * 恢复出来的历史只有"有没有结果"这一种信息，所以那条路径给默认值 [ChatToolStatus.COMPLETED]。
     */
    val status: ChatToolStatus = ChatToolStatus.COMPLETED,
) {
    /** 没有配对的调用事件，只有返回 —— 标题画成「xxx 返回」。 */
    val isResultOnly: Boolean get() = detail.isEmpty()
}

/**
 * 一次工具调用走到哪一步了（照 ZCode 的 `chat.toolCall.status.*` 六态）。
 *
 * 我们目前产生得出 [RUNNING]（调用已发出、结果还没回）与 [COMPLETED]（正常返回）；[FAILED] /
 * [STOPPED] 等失败与中断那两条路径接上后也用它（[PENDING] / [DENIED] 留给还没做的审批流程）。
 */
enum class ChatToolStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    DENIED,
    STOPPED,
}

/**
 * A question the agent is waiting on. [options] is empty when free-form text is
 * expected, otherwise the user must pick one of them.
 *
 * While this is set the turn is paused — the model is not running — so the screen
 * offers the answer controls instead of the ordinary composer.
 */
data class ChatUserPrompt(
    val prompt: String,
    val options: List<String>,
)

/**
 * Single immutable UI state for the chat surface (UDF).
 *
 * [providers], [conversations] and the active selection are mirrored from the
 * repositories; [activeProvider] / [activeModel] / [isReady] are derived so the
 * screen never has to re-do the lookup.
 */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    /** 上一回合被中断了，输入区的按钮因此是「继续」形态；见 [handleContinueClicked]。 */
    val canContinue: Boolean = false,
    /** 首帧先空着：内置种子由核心给出，仓库那条流立刻会送上第一份。 */
    val providers: List<ProviderConfig> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val activeProviderId: String = "",
    val activeModelId: String = "",
    /** Persisted conversation behind [messages]; null until the first send. */
    val activeConversationId: String? = null,
    val isModelPickerOpen: Boolean = false,
    /** 输入框左侧那个环形入口打开的面板（上下文容量）。 */
    val isContextPanelOpen: Boolean = false,
    /** 最近一轮回答之后核心报的上下文用量；一次都还没答过时为 null。 */
    val contextUsage: ContextUsage? = null,

    /**
     * 当前会话的上下文窗口（token 数；模型面板里可改）。
     *
     * 初值是核心的默认值，附着会话后立刻用核心报的值覆盖 —— 那里才是权威：这个数在会话第一次
     * 附着时就定下了，会话中途换模型不会变。
     */
    val contextWindow: Long = DEFAULT_CONTEXT_WINDOW_TOKENS,

    /**
     * 当前会话的推理档位（codex 那套值；空串 = 未设置）。
     *
     * 它是**这次对话**的值：会话第一次附着时核心按目录里的起点档抄一次，之后在输入框那边改就是改它；
     * 打开旧会话时用它自己的记录填回来。
     */
    val reasoningEffort: String = "",
    /**
     * 当前模型**允许**的档位（线上取值，来自核心目录）；空 = 目录没声明、或者这模型不在目录里，
     * 面板按"不限制"列出全部。
     *
     * 目录里有这个模型就不用在界面上自己填：后端那套档位直接列进面板，对应 codex 的
     * `ModelInfo.supported_reasoning_levels`。
     */
    val allowedEfforts: List<String> = emptyList(),
    /** Set while the agent is blocked on a question; see [ChatUserPrompt]. */
    val pendingPrompt: ChatUserPrompt? = null,
) {
    val activeProvider: ProviderConfig?
        get() = providers.firstOrNull { it.id == activeProviderId }

    val activeModel: ModelConfig?
        get() = activeProvider?.models?.firstOrNull { it.id == activeModelId }

    /** A turn needs an endpoint *with credentials* plus a concrete model. */
    val isReady: Boolean
        get() = activeProvider?.apiKey?.isNotBlank() == true && activeModel != null
}

/**
 * Actions sent from the UI to [ChatViewModel].
 */
sealed interface ChatAction {
    data class InputChanged(val value: String) : ChatAction
    data object SendClicked : ChatAction

    /** 停止正在进行的回复：让核心收手，见 [handleStopClicked]。 */
    data object StopClicked : ChatAction

    /** 继续被中断的回合：不加用户消息，接着采样，见 [handleContinueClicked]。 */
    data object ContinueClicked : ChatAction
    data object NewConversationClicked : ChatAction
    data object ModelPickerOpened : ChatAction
    data object ModelPickerDismissed : ChatAction

    /** 输入框左侧的环形入口：点一下弹出上下文容量面板。 */
    data object ContextPanelOpened : ChatAction
    data object ContextPanelDismissed : ChatAction
    data class ModelSelected(val providerId: String, val modelId: String) : ChatAction

    /** 模型面板里改了当前会话的上下文窗口（token 数）。 */
    data class ContextWindowSelected(val tokens: Long) : ChatAction

    /**
     * 输入框里的「推理强度」切了一档：改的是**当前模型**的配置（codex 的 `ReasoningEffort` 那四档，
     * 空串 = 未设置 = 请求里不发推理字段），见 [handleThoughtLevelSelected]。
     */
    data class ThoughtLevelSelected(val value: String) : ChatAction
    data class ConversationSelected(val id: String) : ChatAction
    data class ConversationDeleted(val id: String) : ChatAction

    /** The user's answer to the pending question; resumes the paused turn. */
    data class PromptAnswered(val answer: String) : ChatAction

    /**
     * Internal actions: results of asynchronous work posted back onto the action
     * channel so that all state mutations stay synchronous inside [handleAction].
     */
    sealed interface Internal : ChatAction {
        data class ProvidersReceived(val providers: List<ProviderConfig>) : Internal
        data class ConversationsReceived(val conversations: List<Conversation>) : Internal
        data class ActiveModelReceived(val providerId: String, val modelId: String) : Internal
        data class TranscriptRestored(
            val conversationId: String,
            val messages: List<TranscriptMessage>,
        ) : Internal
        data class ReplyChunk(val text: String) : Internal
        data class ReasoningChunk(val text: String) : Internal
        data class ToolCalled(val name: String, val arguments: String) : Internal
        data class ToolReturned(val name: String, val result: String) : Internal
        data class PromptRequested(val prompt: String, val options: List<String>) : Internal
        data class TurnFailed(val detail: String) : Internal
        data object TurnCompleted : Internal
        data class TurnInterrupted(val durationMs: Long) : Internal
        data class UsageReceived(val usage: ContextUsage) : Internal
    }
}

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
) : BaseViewModel<ChatState, Nothing, ChatAction>(initialState = ChatState()) {

    /** ADK session identity: the conversation + model it was built for. */
    private var sessionKey: String? = null

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

    /** Id of the assistant message currently being streamed, for chunk appends. */
    private var streamingMessageId: String? = null

    /**
     * 流式解析的待办命令。
     *
     * 解析**不在** `handleAction` 里做了：那里跑在 `viewModelScope`（Main）上，而 FULL 模式
     * 每来一个分片都要重解析整篇回复（O(n)）。长回复时主线程会被持续打满 —— 真机实测
     * 主线程 100% CPU 持续 15 秒、`Skipped 438 frames!  Davey! duration=7738ms`。
     *
     * 现在改成投递给一个后台工作协程（[startStreamParseWorker]）：解析在 Default 上做，
     * 主线程只负责把结果贴进状态。
     */
    private val streamCommands = Channel<StreamCommand>(Channel.UNLIMITED)

    /**
     * 待解析的最新文本。
     *
     * 关键在**合并**：模型一秒能吐几十个分片，而每次都是全量重解析，逐条排队会让解析
     * 永远追不上、回复越来越滞后。所以只保留最新一份，工作协程取走时用 `getAndSet(null)`，
     * 中间那些分片自然被跳过 —— 结果与逐条解析一致（FULL 模式本来就只认最终文本）。
     */
    private val pendingStreamParse = AtomicReference<StreamParseRequest?>(null)

    /** 已经排了一次解析，不必再排（省掉每个分片一次入队）。 */
    private val streamParseQueued = AtomicBoolean(false)

    /**
     * Length of the text the parser was last fed.
     *
     * Mirrors `gt.h0`'s `l` field: the FULL path in ima skips the re-parse when the
     * incoming text has the same length as the one it last parsed, and it *assigns*
     * here rather than accumulating (the DELTA path is the one that accumulates).
     */
    private var streamParsedLength: Int = 0

    /** 一次流式解析请求：解析成 [text] 之后，块要贴到 [targetId] 这条消息上。 */
    private class StreamParseRequest(val targetId: String, val text: String)

    /** 交给后台工作协程的活。 */
    private sealed interface StreamCommand {
        /** 有新文本待解析（文本本身在 [pendingStreamParse] 里）。 */
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
     * Assistant segments written during the current turn. A turn can hold several —
     * text before a tool call and text after its result are separate bubbles — and
     * they are joined into the single row the transcript stores.
     */
    private val turnAssistantIds = mutableListOf<String>()

    private var turnJob: Job? = null

    init {
        providerRepository
            .providersStateFlow
            .map { ChatAction.Internal.ProvidersReceived(it) }
            .onEach(::sendAction)
            .launchIn(viewModelScope)

        userPreferencesRepository
            .preferencesStateFlow
            .map { ChatAction.Internal.ActiveModelReceived(it.activeProviderId, it.activeModelId) }
            .onEach(::sendAction)
            .launchIn(viewModelScope)

        conversationStore
            .conversationsStateFlow
            .map { ChatAction.Internal.ConversationsReceived(it) }
            .onEach(::sendAction)
            .launchIn(viewModelScope)

        // The session store is not observable, so the list has to be read once here.
        viewModelScope.launch { runCatching { conversationStore.refresh() } }
        viewModelScope.launch { restoreLatestConversation() }
        // 流式解析的后台工作者：槽点见 startStreamParseWorker。
        startStreamParseWorker()
    }

    override fun handleAction(action: ChatAction) {
        when (action) {
            is ChatAction.InputChanged -> updateState { copy(input = action.value) }
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
            is ChatAction.ThoughtLevelSelected -> handleThoughtLevelSelected(action)
            is ChatAction.ConversationSelected -> handleConversationSelected(action)
            is ChatAction.ConversationDeleted -> handleConversationDeleted(action)
            is ChatAction.PromptAnswered -> handlePromptAnswered(action)

            is ChatAction.Internal.ProvidersReceived ->
                updateState { copy(providers = action.providers) }
            is ChatAction.Internal.ConversationsReceived ->
                updateState { copy(conversations = action.conversations) }
            is ChatAction.Internal.ActiveModelReceived -> {
                updateState {
                    copy(activeProviderId = action.providerId, activeModelId = action.modelId)
                }
                // 当前模型要等偏好读回来才知道，所以新会话的窗口、模型允许的档位都在这里再对一次。
                if (state.activeConversationId == null) {
                    updateState {
                        copy(
                            contextWindow = newConversationContextWindow(),
                            // 还没有会话：先照模型配置里的默认档显示 —— 第一次附着时核心会定下这条会话
                            // 自己的起点档（目录里有这个模型就用目录的），附着后
                            // [syncReasoningEffortAfterAttach] 再把核心那边的值读回来。
                            reasoningEffort = state.activeModel?.reasoningEffort.orEmpty(),
                        )
                    }
                }
                viewModelScope.launch { refreshAllowedEfforts() }
            }
            is ChatAction.Internal.TranscriptRestored -> {
                handleTranscriptRestored(action)
                // 这条会话是不是有一个还没写完的回合，决定发送键要不要是「继续」。
                // 它是会话文件里的事实，所以重启之后打开这条会话同样拿得到。
                viewModelScope.launch {
                    val unfinished = runCatching {
                        conversationStore.interruptedTurn(action.conversationId)
                    }.getOrNull()
                    updateState { copy(canContinue = unfinished != null) }
                    // 窗口同样从它的文件里读回来 —— 重启之后要显示的是这条会话自己的值。
                    restoreConversationState(action.conversationId)
                }
            }
            is ChatAction.Internal.ReplyChunk -> appendReplyChunk(action.text)
            is ChatAction.Internal.ReasoningChunk -> appendReasoningChunk(action.text)
            is ChatAction.Internal.ToolCalled -> appendToolCall(action)
            is ChatAction.Internal.ToolReturned -> appendToolResult(action)
            is ChatAction.Internal.PromptRequested -> handlePromptRequested(action)
            is ChatAction.Internal.TurnFailed -> {
                // 这一轮失败时还开着的工具卡标成「执行失败」（照 ZCode 的 failed 态）。
                markOpenTools(ChatToolStatus.FAILED)
                failTurn(action.detail)
            }
            ChatAction.Internal.TurnCompleted -> finishTurn()
            is ChatAction.Internal.TurnInterrupted -> {
                // 用户停止时还开着的工具卡标成「已停止」。
                markOpenTools(ChatToolStatus.STOPPED)
                handleTurnInterrupted(action.durationMs)
            }
            // 用量只是这一轮的附带信息：面板开着就刷新，消息不动。
            is ChatAction.Internal.UsageReceived -> updateState {
                copy(contextUsage = action.usage)
            }
        }
    }

    override fun onCleared() {
        resetSession()
        super.onCleared()
    }

    // region Restore

    /**
     * Brings back the most recently updated conversation, transcript and all.
     * The model selection is left to preferences: they are the single source for
     * "which model am I using", while the conversation only records what produced
     * it (shown in the history list).
     */
    private suspend fun restoreLatestConversation() {
        val conversation = runCatching { conversationStore.latestConversation() }
            .getOrNull() ?: return
        val messages = runCatching { conversationStore.messagesOf(conversation.id) }
            .getOrDefault(emptyList())
        sendAction(ChatAction.Internal.TranscriptRestored(conversation.id, messages))
    }

    private fun handleTranscriptRestored(action: ChatAction.Internal.TranscriptRestored) {
        // The user may have started a new conversation while the load was in flight.
        if (state.activeConversationId != null || state.messages.isNotEmpty()) return
        updateState {
            copy(
                activeConversationId = action.conversationId,
                messages = action.messages.map {
                    it.toChatMessage(
                        fallbackModelLabel = modelLabelOf(action.conversationId),
                        parserFactory = markdownParserFactory,
                    )
                },
            )
        }
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

    // region Action handlers

    private fun handleSendClicked() {
        val text = state.input.trim()
        if (text.isEmpty() || state.isSending) return
        val provider = state.activeProvider ?: return
        val model = state.activeModel ?: return
        if (provider.apiKey.isBlank()) return

        // The placeholder bubble is what the UI shows while the model is silent. It
        // is dropped again if the turn opens with a tool call instead of text.
        turnAssistantIds.clear()
        val assistantId = UUID.randomUUID().toString()
        streamingMessageId = assistantId
        turnAssistantIds += assistantId
        // 用户消息和它的回复占同一个时间点（这一轮是同时开始的）。
        val now = System.currentTimeMillis()
        updateState {
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

        turnJob = viewModelScope.launch { runTurn(provider, model, text) }
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
        val running = turnJob ?: return
        if (!running.isActive) return

        viewModelScope.launch {
            runCatching { agentChat.interrupt() }
        }
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

        turnAssistantIds.clear()
        val assistantId = UUID.randomUUID().toString()
        streamingMessageId = assistantId
        turnAssistantIds += assistantId
        updateState {
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

        turnJob = viewModelScope.launch { runContinuedTurn(provider, model) }
    }

    /**
     * 回合被平台停下了：收尾、把发送键换成「继续」，并在这一段回答后面插一行状态
     * （「你在 N秒 后停止了」+ 分隔线）。
     *
     * 状态行是列表里独立的一项，所以它两侧的空白和消息之间的一模一样。
     */
    private fun handleTurnInterrupted(durationMs: Long) {
        finishTurn()
        updateState {
            copy(
                canContinue = true,
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
    private suspend fun syncReasoningEffortAfterAttach() {
        // 新对话还没附着时选的那个档位，在这里补交（核心会把它追加进会话文件）。
        val pending = pendingReasoningEffort
        if (pending != null) {
            pendingReasoningEffort = null
            runCatching { agentChat.setReasoningEffort(pending) }
        }
        agentChat.reasoningEffort()?.let { attached ->
            updateState { copy(reasoningEffort = attached) }
        }
    }

    /**
     * 刷新"当前模型允许哪些档"：**目录**给的（目录里有这个模型就用它那套）；目录里没有就是空的，
     * 面板按"不限制"列出全部。
     *
     * 只读一次目录、不落盘 —— 它决定聊天页那张档位面板列哪几档（对应 codex 的
     * `ModelInfo.supported_reasoning_levels`）。
     */
    private suspend fun refreshAllowedEfforts() {
        val provider = state.activeProvider ?: return
        val modelId = state.activeModel?.modelId ?: return
        val levels = runCatching { providerRepository.catalog(provider.id) }
            .getOrDefault(emptyList())
            .firstOrNull { it.modelId == modelId }
            ?.levels
            .orEmpty()
        updateState { copy(allowedEfforts = levels) }
    }

    private suspend fun syncContextWindowAfterAttach() {
        val pending = pendingContextWindow
        if (pending != null) {
            pendingContextWindow = null
            updateState { copy(contextWindow = pending) }
            collectEvents(agentChat.setContextWindow(pending))
            return
        }
        agentChat.contextWindow()?.let { attached ->
            updateState { copy(contextWindow = attached) }
        }
    }

    /**
     * 用户在模型面板里改了当前会话的上下文窗口。
     *
     * 会话已经附着就直接写进它的文件（核心会立刻回报一次用量，环与面板随之刷新）；还没附着
     * —— 新会话的第一条消息还没发出去 —— 先记在界面上，等附着时一并写入。
     */
    private fun handleContextWindowSelected(tokens: Long) {
        updateState { copy(contextWindow = tokens) }
        if (sessionKey == null) {
            pendingContextWindow = tokens
            return
        }
        viewModelScope.launch { collectEvents(agentChat.setContextWindow(tokens)) }
    }

    /** 照 [runTurn] 的做法跑完这一轮，只是入口换成「续采样」。 */
    private suspend fun runContinuedTurn(provider: ProviderConfig, model: ModelConfig) {

        try {
            val id = state.activeConversationId ?: throw IllegalStateException(
                "Could not continue a conversation that is not open."
            )

            val key = "$id|${provider.id}|${model.id}"
            if (sessionKey != key) {
                // 继续之前先把这条会话交给核心：它要从会话文件里认出那个没写完的回合，
                // 重启之后（或者换过模型之后）这一步是必须的。
                agentChat.startConversation(
                    sessionId = id,
                    provider = provider,
                    modelId = model.modelId,
                    instruction = CHAT_INSTRUCTION,
                )
                sessionKey = key
                syncContextWindowAfterAttach()
                syncReasoningEffortAfterAttach()
            }

            collectEvents(agentChat.continueTurn())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            sendAction(
                ChatAction.Internal.TurnFailed(error.message ?: error::class.simpleName.orEmpty())
            )
        }
    }

    private fun handleNewConversation() {
        resetSession()
        updateState {
            copy(messages = emptyList(), isSending = false, activeConversationId = null)
        }
        // 新会话的窗口按当前模型预设定；它第一次附着时核心会把这个值写进这条会话的文件。
        // 档位先照模型的默认档显示：附着时核心会定下这条会话自己的起点档（目录里有就用目录那份），
        // 随后从核心读回来。
        updateState {
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
        updateState { copy(reasoningEffort = action.value) }
        // 还没附着（新对话、第一条消息还没发出去）：核心那边没有会话文件可写，先记下来，
        // 附着那一刻再交给它 —— 与窗口那条 pending 同一个做法。
        if (sessionKey == null) {
            pendingReasoningEffort = action.value
            return
        }
        viewModelScope.launch {
            runCatching { agentChat.setReasoningEffort(action.value) }
            // 以核心的值读回来：写失败时界面不会显示一个没落地的值。
            val current = runCatching { agentChat.reasoningEffort() }.getOrNull()
            if (current != null) updateState { copy(reasoningEffort = current) }
        }
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
        resetSession()
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
            updateState {
                copy(
                    contextWindow = newConversationContextWindow(),
                    // 换模型了：还没发第一条消息，档位先照新模型的默认档显示。
                    reasoningEffort = state.activeModel?.reasoningEffort.orEmpty(),
                )
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.updateActiveModel(action.providerId, action.modelId)
        }
        // 面板列哪几档也跟着模型走（目录里有的模型用目录那份）。
        viewModelScope.launch { refreshAllowedEfforts() }
    }

    private fun handleConversationSelected(action: ChatAction.ConversationSelected) {
        // 选中的就是当前这条：抽屉由调用方（侧边栏）负责关，这里什么都不用做 ——
        // 否则会把 ADK session 重建、转写重读一遍，白费一次。
        if (action.id == state.activeConversationId) return

        resetSession()
        updateState {
            copy(
                messages = emptyList(),
                isSending = false,
                activeConversationId = action.id,
            )
        }
        viewModelScope.launch {
            val messages = runCatching { conversationStore.messagesOf(action.id) }
                .getOrDefault(emptyList())
            // Bail out if the selection moved on while the query ran.
            if (state.activeConversationId != action.id) return@launch
            updateState {
                copy(
                    messages = messages.map {
                        it.toChatMessage(
                            fallbackModelLabel = modelLabelOf(action.id),
                            parserFactory = markdownParserFactory,
                        )
                    }
                )
            }
            restoreConversationState(action.id)
        }
    }

    /**
     * 打开一条会话时，把它自己的两样东西从文件里读回来：窗口，以及上次报的用量。
     *
     * 窗口必须用它的（否则设置那一栏显示的会和这条会话实际用的不是一回事）；用量只活在内存里
     * 的话，进程重启后面板就空了 —— 文件里的那份是重启后唯一的来源。从没记过（会话还没附着过）
     * 时，窗口按当前模型的预设推一个，那是它第一次附着时会定下的值。
     */
    private suspend fun restoreConversationState(conversationId: String) {
        val storedWindow = runCatching {
            agentChat.conversationContextWindow(conversationId)
        }.getOrNull()
        val storedUsage = runCatching { agentChat.conversationUsage(conversationId) }.getOrNull()
        // 这条会话自己记的推理档位（最后一条为准）；没记过就是未设置，照实显示，不替它编一个。
        val storedEffort = runCatching {
            agentChat.conversationReasoningEffort(conversationId)
        }.getOrNull()
        if (state.activeConversationId != conversationId) return
        updateState {
            copy(
                contextWindow = storedWindow ?: newConversationContextWindow(),
                contextUsage = storedUsage,
                reasoningEffort = storedEffort.orEmpty(),
            )
        }
    }

    /** 还没有会话时界面上的窗口：当前模型预设，没填就按默认值。 */
    private fun newConversationContextWindow(): Long {
        val preset = state.activeModel?.contextLength ?: 0
        return if (preset > 0) preset.toLong() else DEFAULT_CONTEXT_WINDOW_TOKENS
    }

    private fun handleConversationDeleted(action: ChatAction.ConversationDeleted) {
        viewModelScope.launch {
            runCatching { conversationStore.deleteConversation(action.id) }
        }
        if (state.activeConversationId == action.id) {
            resetSession()
            updateState {
                copy(messages = emptyList(), isSending = false, activeConversationId = null)
            }
        }
    }

    // endregion

    // region Turn handling

    private suspend fun runTurn(
        provider: ProviderConfig,
        model: ModelConfig,
        text: String,
    ) {
        try {
            val id = ensureConversation(provider, model, text) ?: throw IllegalStateException(
                "Could not open a conversation to send into."
            )

            val key = "$id|${provider.id}|${model.id}"
            if (sessionKey != key) {
                // The ADK session carries the conversation's own id, so the model's
                // stored context and the transcript share one identity and a
                // resumed conversation is loaded rather than rebuilt.
                agentChat.startConversation(
                    sessionId = id,
                    provider = provider,
                    modelId = model.modelId,
                    instruction = CHAT_INSTRUCTION,
                )
                sessionKey = key
                syncContextWindowAfterAttach()
                syncReasoningEffortAfterAttach()
            }

            collectEvents(agentChat.send(text))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            sendAction(
                ChatAction.Internal.TurnFailed(error.message ?: error::class.simpleName.orEmpty())
            )
        }
    }

    /**
     * Maps a turn's events onto actions. Shared by the first send and by the resume
     * after the user answers — both stream the same kinds of event.
     */
    private suspend fun collectEvents(events: Flow<ChatEvent>) {
        events.collect { event ->
            sendAction(
                when (event) {
                    is ChatEvent.Text -> ChatAction.Internal.ReplyChunk(event.text)
        is ChatEvent.Reasoning -> ChatAction.Internal.ReasoningChunk(event.text)
                    is ChatEvent.ToolCall ->
                        ChatAction.Internal.ToolCalled(event.name, event.arguments)
                    is ChatEvent.ToolResult ->
                        ChatAction.Internal.ToolReturned(event.name, event.result)
                    is ChatEvent.UserPromptRequested ->
                        ChatAction.Internal.PromptRequested(event.prompt, event.options)
                    is ChatEvent.Failed -> ChatAction.Internal.TurnFailed(event.detail)
                    ChatEvent.Completed -> ChatAction.Internal.TurnCompleted
                    is ChatEvent.Aborted -> ChatAction.Internal.TurnInterrupted(event.durationMs)
                    is ChatEvent.Usage -> ChatAction.Internal.UsageReceived(event.usage)
                }
            )
        }
    }

    /**
     * 已挂出、还没被回答的提问（按提问顺序排队），以及已经收到的回答。
     *
     * 模型可能在一轮里**同时**挂出多个交互调用（实测 `get_user_choice` + `adk_request_input`）。
     * 界面一次只问一个：答完当前这个再问下一个，答案按同样顺序攒着，全部收齐后**一起**提交 ——
     * 少交一个，历史里就会留下没有结果的 tool_call，之后每次请求都被服务端 400 拒掉。
     */
    private val pendingPrompts = ArrayDeque<ChatUserPrompt>()
    private val promptAnswers = mutableListOf<String>()

    /**
     * The agent stopped to ask something. Its event flow ends here, so the usual
     * end-of-turn path releases the composer; [ChatState.pendingPrompt] is what
     * keeps the ordinary input out of the way until the question is answered.
     */
    private fun handlePromptRequested(action: ChatAction.Internal.PromptRequested) {
        // Close the open segment: whatever the model wrote before asking stays put, and
        // the empty placeholder created on send is dropped rather than left as a blank
        // bubble for the whole time the user takes to answer.
        sealAssistantSegment()
        pendingPrompts.addLast(
            ChatUserPrompt(prompt = action.prompt, options = action.options)
        )
        // 展示队首：后面还有问题的话，答完这个会自动接着问（见 handlePromptAnswered）。
        updateState { copy(pendingPrompt = pendingPrompts.first()) }
    }

    /** Sends the answer back and streams the rest of the paused turn. */
    private fun handlePromptAnswered(action: ChatAction.PromptAnswered) {
        val answer = action.answer.trim()
        if (answer.isEmpty() || state.pendingPrompt == null || state.isSending) return
        promptAnswers += answer
        pendingPrompts.removeFirstOrNull()

        val next = pendingPrompts.firstOrNull()
        if (next != null) {
            // 还有下一个问题：继续问。这里**不算**在发送 —— 模型仍在等答案。
            updateState { copy(pendingPrompt = next) }
            return
        }

        val answers = promptAnswers.toList()
        promptAnswers.clear()
        updateState { copy(pendingPrompt = null, isSending = true) }
        turnJob = viewModelScope.launch { resumeTurn(answers) }
    }

    private suspend fun resumeTurn(answers: List<String>) {
        try {
            collectEvents(agentChat.respondToPrompts(answers))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            sendAction(
                ChatAction.Internal.TurnFailed(error.message ?: error::class.simpleName.orEmpty())
            )
        }
    }

    /** Creates the conversation row on the first send; [title] comes from that message. */
    private suspend fun ensureConversation(
        provider: ProviderConfig,
        model: ModelConfig,
        title: String,
    ): String? {
        state.activeConversationId?.let { return it }
        val created = runCatching {
            conversationStore.createConversation(
                providerId = provider.id,
                // 写进会话文件的是**线上模型名**（与 attach 时传给核心的一致），不是模型条目的 id。
                modelId = model.modelId,
                title = title.take(TITLE_MAX_LENGTH),
            )
        }.getOrNull() ?: return null
        updateState { copy(activeConversationId = created.id) }
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
    private fun appendReplyChunk(text: String) {
        val targetId = streamingMessageId ?: startAssistantSegment()
        val fullText = (state.messages.firstOrNull { it.id == targetId }?.text ?: "") + text
        // Same guard as ima's g(): nothing new to parse, so leave the state alone.
        if (fullText.isEmpty() || fullText.length == streamParsedLength) return
        streamParsedLength = fullText.length
        // 文字先落进状态：这一步很便宜，而且要立刻可见（块还没解析出来时界面按 text 渲染）。
        updateState {
            copy(
                messages = messages.map { message ->
                    if (message.id == targetId) message.copy(text = fullText) else message
                }
            )
        }
        // 块交给后台解析（见 streamCommands）。同一时刻只排一次：工作协程跑完会再取一次
        // 最新文本，期间到达的分片自然合并掉。
        pendingStreamParse.set(StreamParseRequest(targetId, fullText))
        if (streamParseQueued.compareAndSet(false, true)) {
            streamCommands.trySend(StreamCommand.Parse)
        }
    }

    /**
     * 流式解析的后台工作协程：**只有它碰 native 解析器句柄**。
     *
     * 一个协程顺序处理所有命令，所以句柄永远不会被两个线程同时使用（native 侧不是线程安全的）；
     * 命令按入队顺序处理，`Parse` 又会在自己内部把待办文本排空，因此「先解析完、再收尾」的顺序
     * 天然成立，不需要额外加锁。
     */
    private fun startStreamParseWorker() {
        viewModelScope.launch {
            var parser: MarkdownParser? = null
            for (command in streamCommands) {
                when (command) {
                    StreamCommand.Parse -> {
                        while (true) {
                            val request = pendingStreamParse.getAndSet(null) ?: break
                            val startedAt = System.currentTimeMillis()
                            // FULL: re-parse from scratch rather than appending the delta.
                            val update = withContext(Dispatchers.Default) {
                                val active = parser ?: markdownParserFactory.create().also { parser = it }
                                active.reset()
                                active.append(request.text)
                            }
                            val parsedAt = System.currentTimeMillis()
                            applyStreamBlocks(request.targetId, update)
                            Log.d(
                                CHAT_PARSE_TAG,
                                "解析 ${request.text.length} 字 耗时 ${parsedAt - startedAt}ms，" +
                                    "贴块 ${System.currentTimeMillis() - parsedAt}ms"
                            )
                            // 节流：贴块 + 重组才是主线程上的成本，控制它的频率。
                            delay(STREAM_PARSE_MIN_INTERVAL_MS)
                        }
                        streamParseQueued.set(false)
                        // 收尾竞态：清标志之后、工作协程再次挂起之前又来了新文本，就补排一次。
                        if (pendingStreamParse.get() != null && streamParseQueued.compareAndSet(false, true)) {
                            streamCommands.trySend(StreamCommand.Parse)
                        }
                    }

                    is StreamCommand.Finalize -> {
                        // 排在前面的 Parse 已经把文本排空了，这里直接收尾。
                        val targetId = command.targetId
                        val update = withContext(Dispatchers.Default) {
                            if (targetId == null) null else parser?.finalizeStream()
                        }
                        if (targetId != null && update != null) applyStreamBlocks(targetId, update)
                        // 收尾之后再追加尾块（失败原因），否则会被上面的截断吃掉。
                        if (targetId != null && command.trailingBlock != null) {
                            appendBlock(targetId, command.trailingBlock)
                        }
                        withContext(Dispatchers.Default) {
                            parser?.close()
                            parser = null
                        }
                    }

                    StreamCommand.Close -> withContext(Dispatchers.Default) {
                        parser?.close()
                        parser = null
                    }
                }
            }
        }
    }

    /**
     * 思考也是增量，直接往当前那条流式消息上累加。
     *
     * 它不走 Markdown 那条路（思考是纯文本），也不会开新段：思考通常先于正文到达，本来就属于同一条
     * 回复。
     */
    private fun appendReasoningChunk(chunk: String) {
        // 与正文同一条规则：手上没有正在写的段就开一段。工具调用会把上一段收掉，而模型在工具返回
        // 之后往往还要再想一段 —— 那时 streamingMessageId 已经是空的，直接 return 就会把这一段思考
        // 整个丢掉（出现了"实时只看到第一段、重启后才看到第二段"）。
        val targetId = streamingMessageId ?: startAssistantSegment()
        updateState {
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
    private fun applyStreamBlocks(targetId: String, update: MarkdownUpdate) {
        updateState {
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
    private fun appendBlock(targetId: String, block: MarkdownBlock) {
        updateState {
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
    private fun startAssistantSegment(): String {
        // A segment owns its parser; the previous one (if any) is done with.
        closeStreamParser()
        val id = UUID.randomUUID().toString()
        streamingMessageId = id
        turnAssistantIds += id
        updateState {
            copy(
                messages = messages + ChatMessage(
                    id = id,
                    role = ChatRole.ASSISTANT,
                    text = "",
                    isStreaming = true,
                    timestamp = System.currentTimeMillis(),
                    modelLabel = activeModel?.modelId,
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
    private fun finalizeStreamInto(targetId: String?, trailingBlock: MarkdownBlock? = null) {
        // 交给工作协程：它排在前面那些 Parse 之后，顺序天然正确（见 startStreamParseWorker）。
        streamCommands.trySend(StreamCommand.Finalize(targetId, trailingBlock))
    }

    private fun closeStreamParser() {
        // Length tracking belongs to the parser instance, so it goes with it.
        streamParsedLength = 0
        streamCommands.trySend(StreamCommand.Close)
    }

    /**
     * A tool call closes the current assistant segment: text written before the call
     * is the model's preamble and the answer after the result is a separate bubble,
     * so the transcript keeps the order things actually happened in.
     */
    private fun appendToolCall(action: ChatAction.Internal.ToolCalled) {
        sealAssistantSegment()
        appendToolEntry(name = action.name, detail = action.arguments)
    }

    /**
     * 工具返回并进上面那张调用卡片 —— 一次调用的「问了什么 / 回了什么」放在同一张卡里
     * （用户对提问的回答也走这里）。只有找不到配对调用时才单独成条。
     */
    private fun appendToolResult(action: ChatAction.Internal.ToolReturned) {
        // 按名字往前找最近一张「同名、还没有返回」的调用卡，与重建那条路径（`ConversationStore`
        // 的 absorbIntoOpenCall）规则一致。只认紧邻上一条的话，一条事件里并行调用的几个工具
        // 会各自多出一张独立的「xxx 返回」卡片，重启前后就对不上了。
        val open = state.messages.lastOrNull { message ->
            val tool = message.tool
            tool != null && !tool.isResultOnly && tool.result == null && tool.name == action.name
        }
        if (open != null) {
            updateState {
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
        appendToolEntry(name = action.name, detail = "", result = action.result)
    }

    /**
     * 把"还没有结果"的工具卡一次标成给定状态（失败 / 已停止）。
     *
     * 照 ZCode：状态是执行侧推进的，所以回合失败或用户停止时，当时开着的卡要落到 failed / stopped，
     * 而不是永远停在"执行中"。
     */
    private fun markOpenTools(status: ChatToolStatus) {
        updateState {
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

    private fun appendToolEntry(name: String, detail: String, result: String? = null) {
        updateState {
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
    private fun sealAssistantSegment() {
        val targetId = streamingMessageId ?: return
        streamingMessageId = null
        // 「空」是指没东西可显示：**只想了、还没开口的那一段不算空**。按文本判空会把刚拿到的思考
        // 连同这一段一起删掉 —— 那正是"思考完、正文一出现就不见了"的原因。
        val isEmpty = state.messages.firstOrNull { it.id == targetId }
            ?.let { it.text.isNullOrEmpty() && it.thinking.isEmpty() }
            ?: true
        // Close the segment's blocks before dropping the parser (no-op when empty).
        if (!isEmpty) finalizeStreamInto(targetId) else closeStreamParser()
        updateState {
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
        if (isEmpty) turnAssistantIds.remove(targetId)
    }

    private fun failTurn(detail: String) {
        // A turn that already closed its segment on a tool call has nowhere to show
        // the failure, so open one rather than swallowing it.
        val targetId = streamingMessageId ?: startAssistantSegment()
        streamingMessageId = null
        val errorBlock = MarkdownBlock(
            id = "error:${UUID.randomUUID()}",
            type = MarkdownBlockType.PARAGRAPH,
            isClosed = true,
            content = listOf(MarkdownInline(MarkdownInlineType.TEXT, literal = detail)),
        )
        // Close the partial answer's open block first, then append the reason as a
        // block of its own so it renders through the same path as everything else.
        // 尾块必须交给收尾去做（见 StreamCommand.Finalize）—— 这里直接加会被 applied() 截掉。
        finalizeStreamInto(targetId, errorBlock)
        val finalText = state.messages.firstOrNull { it.id == targetId }?.let { message ->
            if (message.text.isEmpty()) detail else "${message.text}\n\n$detail"
        }
        updateState {
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
                        )
                    }
                },
            )
        }
        endTurn()
    }

    private fun finishTurn() {
        val targetId = streamingMessageId
        streamingMessageId = null
        // Close whatever the last chunk left open before the turn ends.
        finalizeStreamInto(targetId)
        updateState {
            copy(
                isSending = false,
                messages = messages.map { message ->
                    if (message.id == targetId) message.copy(isStreaming = false) else message
                },
            )
        }
        endTurn()
    }

    /**
     * Ends the turn's bookkeeping. Nothing is written: the runner already appended this
     * turn's user message and model replies to the session as they happened, so the
     * transcript needs no write of its own.
     *
     * All that is left is to drop the turn's segment ids and re-read the conversation
     * list, whose `updatedAt` the session store moved.
     */
    private fun endTurn() {
        turnAssistantIds.clear()
        if (state.activeConversationId == null) return
        viewModelScope.launch { runCatching { conversationStore.refresh() } }
    }

    /**
     * Drops the ADK session so the next send re-attaches (the transcript stays).
     *
     * **刻意不取消进行中的回合**：回复途中切模型 / 切对话不该把那条回复掐掉，它会继续
     * 跑完。要停止只有 [handleStopClicked]（输入框那个停止按钮）这一条路径。
     *
     * 因此这里也不做收尾 —— `streamingMessageId`、流式解析器和 `isSending` 都属于仍在
     * 跑的那一轮，回合自己走到 [finishTurn] 时会清干净。切对话时消息被整体替换，后续
     * 分片会因为按 id 找不到目标消息而被丢弃（见 [appendReplyChunk]），不会串进新对话。
     */
    private fun resetSession() {
        agentChat.endConversation()
        sessionKey = null
        // A question belongs to the conversation that asked it.
        pendingPrompts.clear()
        promptAnswers.clear()
        // 用量是那条会话的东西，换了会话就没有了。窗口不在这里复位：会话里的窗口不随模型变，
        // 换会话 / 新建会话的调用点各自负责把它设成对的值。
        pendingContextWindow = null
        pendingReasoningEffort = null
        updateState {
            copy(pendingPrompt = null, contextUsage = null, isContextPanelOpen = false)
        }
    }

    // endregion

    /** Single mutation point of [mutableStateFlow] (mirrors the other ViewModels). */
    private inline fun updateState(block: ChatState.() -> ChatState) {
        mutableStateFlow.update(block)
    }

    private companion object {
        const val CHAT_INSTRUCTION =
            "You are Jasmine, a concise and helpful assistant. Answer in the user's language."

        /** Conversation titles are the first user message, clipped for the list. */
        const val TITLE_MAX_LENGTH = 60

        /** 流式解析的日志标签（`adb logcat -s ChatParse`）。 */
        const val CHAT_PARSE_TAG = "ChatParse"

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

/**
 * @param fallbackModelLabel 事件里**没有**记模型名时用的兜底值（由 [ChatViewModel.modelLabelOf]
 *   从会话记录里取）。事件里记着（`Event.modelVersion`）就优先用它 —— 那是逐条精确的。
 * @param parserFactory 一次性解析正文用的解析器工厂（解析端口注入，见 [MarkdownParserFactory]）。
 */
private fun TranscriptMessage.toChatMessage(
    fallbackModelLabel: String? = null,
    parserFactory: MarkdownParserFactory,
): ChatMessage = ChatMessage(
    id = UUID.randomUUID().toString(),
    role = role,
    text = text,
    isError = isError,
    // 工具条目（调用 / 返回）没有正文，界面上按工具行渲染；正文为空的普通消息也一样。
    tool = tool?.let {
        ChatToolActivity(
            name = it.name,
            detail = it.detail,
            result = it.result,
            // 状态从行数据里读（核心给的取值与 ZCode 一致），不从"有没有结果"反推。
            status = when (toolStatus) {
                "pending" -> ChatToolStatus.PENDING
                "running" -> ChatToolStatus.RUNNING
                "failed" -> ChatToolStatus.FAILED
                "denied" -> ChatToolStatus.DENIED
                "stopped" -> ChatToolStatus.STOPPED
                else -> ChatToolStatus.COMPLETED
            },
        )
    },
    // Stored rows are plain text; parse them once so restored history renders as
    // Markdown too. Never a live stream, so a single pass is enough.
    // 工具条目没有正文，跳过解析 —— 免得为一次空文档白跑 native。
    blocks = if (text.isEmpty()) emptyList() else parseMarkdownBlocks(text, parserFactory),
    timestamp = timestamp,
    // 优先用事件自己记的模型名；旧数据没有，才回退到会话记录的模型。
    modelLabel = modelLabel ?: fallbackModelLabel,
    stoppedAfterMs = stoppedAfterMs,
    // 历史里也带思考（核心把它挂回到这一轮的回复上），所以重开对话照样看得到思考块。
    thinking = thinking,
)

/**
 * One-shot parse of an already-finished message.
 *
 * Two steps, in this order: the append result carries every block (the parser starts
 * at offset 0), then the finalize result carries the tail that only becomes final
 * once the stream ends. Skipping the first step would drop the stable prefix.
 *
 * [IncrementalMarkdownParser.apply] 是伴生函数，纯 Kotlin、不碰 native，因此这里
 * 与生产端共用同一条「截断+追加」的合并语义，而不是另写一份。
 */
private fun parseMarkdownBlocks(
    markdown: String,
    parserFactory: MarkdownParserFactory,
): List<MarkdownBlock> {
    if (markdown.isEmpty()) return emptyList()
    val parser = parserFactory.create()
    return try {
        val ast = mutableListOf<MarkdownBlock>()
        IncrementalMarkdownParser.apply(parser.append(markdown), ast)
        IncrementalMarkdownParser.apply(parser.finalizeStream(), ast)
        ast
    } catch (error: Throwable) {
        // Native boundary: a failure here must not take down history restore.
        emptyList()
    } finally {
        parser.close()
    }
}
