package com.lhzkml.jasmine.feature.main.impl.chat

import com.lhzkml.jasmine.core.agent.ChatFailureKind
import com.lhzkml.jasmine.core.agent.ContextUsage
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.ModelConfig
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlock
import com.lhzkml.jasmine.feature.main.impl.SidebarConversation

/** 核心的默认上下文窗口（200K）在界面上的镜像；附着会话后立刻被核心报的值覆盖。 */
internal const val DEFAULT_CONTEXT_WINDOW_TOKENS = 200_000L

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
    /**
     * 这一条是**失败**时的分型（来自核心的跨边界分型）：界面据它给一句"能怎么办"的提示，
     * 而不是去猜错误文本。null = 不是失败，或这是一条从历史恢复出来的失败（核心不记分型）。
     */
    val failureKind: ChatFailureKind? = null,
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
     * 这次调用走到哪一步了 —— 状态是**数据**，界面读它，不再靠"有没有结果"猜。
     *
     * 恢复出来的历史只有"有没有结果"这一种信息，所以那条路径给默认值 [ChatToolStatus.COMPLETED]。
     */
    val status: ChatToolStatus = ChatToolStatus.COMPLETED,
) {
    /** 没有配对的调用事件，只有返回 —— 标题画成「xxx 返回」。 */
    val isResultOnly: Boolean get() = detail.isEmpty()
}

/**
 * 一次工具调用走到哪一步了。
 *
 * 四态都是**真的会出现**的：[RUNNING]（调用已发出、结果还没回）、[COMPLETED]（正常返回）、
 * [FAILED]（回合失败时把还没回来的调用收口）、[STOPPED]（用户停止 / 平台打断）。
 *
 * 以前照搬的是 ZCode 的六态，多出来的 `PENDING` / `DENIED` 属于**审批流程**，而这个平台没有审批 ——
 * 那两个值永远不会产生。留着它们会让"状态是数据"的读取端产生**虚假完备感**（枚举写着六种，实际
 * 只会出现四种），所以删掉；真要做审批时再加回来 —— 那时是新增功能，不是补死值。
 */
enum class ChatToolStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    STOPPED,
}

/**
 * 核心在一行转写里记的 `tool_status` → 本模块的状态。
 *
 * 核心目前只会写 `"completed"`（工具正常返回）与 `"stopped"`（回合被打断时把没回来的调用收口）；
 * 空串表示那一行不是工具行，走不到这里。
 *
 * `"running"` / `"failed"` 是**实时**那条路径自己产生的，不经这张表；`"pending"` / `"denied"` 是
 * 旧的审批语义（见 [ChatToolStatus]），只为兼容旧数据保留读法。认不出的取值一律按"已完成" ——
 * 一行工具记录既然落了盘，它当时就是跑完了的。
 */
internal fun toolStatusOf(raw: String): ChatToolStatus = when (raw) {
    "running", "pending" -> ChatToolStatus.RUNNING
    "failed", "denied" -> ChatToolStatus.FAILED
    "stopped" -> ChatToolStatus.STOPPED
    else -> ChatToolStatus.COMPLETED
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
 * 它是**界面看到的那一份**：全局的东西（[providers]、[conversations]、当前选的模型、面板开合）放在
 * 这里；**会话自己的东西**（消息、发送中、提问、窗口/档位/用量、工具卡展开态）属于
 * [ConversationChatState]，由 [ConversationChats] 每会话存一份 —— [conversation] 是**当前显示那条**
 * 的**派生投影**（修复方案 D1：由 `ChatViewModel.stateFlow` 的 combine 从会话表算出，没有人能
 * 手动写它 —— 门禁 R9）。
 *
 * 下面那些 `val … get()` 是会话级字段的读口：界面照旧读 `state.messages` / `state.isSending` …，
 * 而**写**一律写到会话那份状态上（见 `ChatViewModel.updateChat`），所以"正在跑的那一轮的输出"
 * 永远落在它自己那条会话里，而不是当前显示的那条。
 */
data class ChatState(
    /** 当前显示的那条会话自己的状态。 */
    val conversation: ConversationChatState = ConversationChatState(),

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
    /**
     * 当前模型**允许**的档位（线上取值，来自核心目录）；空 = 目录没声明、或者这模型不在目录里，
     * 面板按"不限制"列出全部。
     *
     * 目录里有这个模型就不用在界面上自己填：后端那套档位直接列进面板，对应 codex 的
     * `ModelInfo.supported_reasoning_levels`。
     */
    val allowedEfforts: List<String> = emptyList(),
) {
    val messages: List<ChatMessage> get() = conversation.messages
    val toolRowOpen: Map<String, Boolean> get() = conversation.toolRowOpen
    val input: String get() = conversation.input
    val isSending: Boolean get() = conversation.isSending
    val canContinue: Boolean get() = conversation.canContinue
    val contextUsage: ContextUsage? get() = conversation.contextUsage
    val contextWindow: Long get() = conversation.contextWindow
    val reasoningEffort: String get() = conversation.reasoningEffort
    val pendingPrompt: ChatUserPrompt? get() = conversation.pendingPrompt
    val isInterruptRequested: Boolean get() = conversation.isInterruptRequested

    val activeProvider: ProviderConfig?
        get() = providers.firstOrNull { it.id == activeProviderId }

    val activeModel: ModelConfig?
        get() = activeProvider?.models?.firstOrNull { it.id == activeModelId }

    /** A turn needs an endpoint *with credentials* plus a concrete model. */
    val isReady: Boolean
        get() = activeProvider?.apiKey?.isNotBlank() == true && activeModel != null
}

/**
 * 侧边栏（会话抽屉）要的那一片状态。
 *
 * 它是 [ChatState] 的**派生投影**：会话列表 → 抽屉要的形状（标题 + 时间小字 + 当前高亮）。
 * 单独成型是因为抽屉与聊天面关心的东西不同 —— 抽屉只关心"有哪些会话、哪条是当前"，
 * 而聊天面的状态每个流式分片都在变。分成两条流之后，一次分片不会连带抽屉一起重组。
 */
data class ChatSidebarState(
    val conversations: List<SidebarConversation> = emptyList(),
    val activeConversationId: String? = null,
)

/**
 * 一条会话自己的事实：打开它时从会话文件里读回来的三样。
 *
 * 纯数据 —— 读它的人（[ChatViewModel.readConversationFacts]）不碰 state，写 state 的是
 * [ChatAction.Internal.ConversationFactsLoaded] 的 handler（R2：异步必回流）。
 */
data class ConversationFacts(
    val conversationId: String,
    /** 会话自己记的窗口；null = 从没记过（handler 按当前模型预设推一个）。 */
    val window: Long?,
    /** 上次核心报的用量；null = 还没答过。 */
    val usage: ContextUsage?,
    /** 会话最后一条档位记录；null = 没记过（照实显示未设置，不替它编一个）。 */
    val effort: String?,
)
