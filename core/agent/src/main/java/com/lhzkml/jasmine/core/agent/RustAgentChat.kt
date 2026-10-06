package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.AgentSettings
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import uniffi.jasmine_ffi.AgentFailure
import uniffi.jasmine_ffi.AgentHandle
import uniffi.jasmine_ffi.AgentSettings as CoreAgentSettings
import uniffi.jasmine_ffi.EventListener
import uniffi.jasmine_ffi.ModelInput
import uniffi.jasmine_ffi.ProviderInput
import uniffi.jasmine_model_provider_info.WireApi
import uniffi.jasmine_protocol.ChatEvent as CoreChatEvent
import uniffi.jasmine_protocol.ContextUsageBreakdownItem as CoreContextUsageBreakdownItem
import uniffi.jasmine_protocol.ContextUsageSource as CoreContextUsageSource
import uniffi.jasmine_protocol.TokenUsageInfo as CoreTokenUsageInfo

/**
 * [AgentChat] backed by the Rust core.
 *
 * The engine is the Rust core: the turn loop, both wire protocols and the
 * built-in tools run in Rust. What stays on this side is the translation the boundary needs —
 * the core's calls are synchronous, so they run on [Dispatchers.IO], and the core reports events
 * through a callback, which becomes the [Flow] the interface promises.
 *
 * Attaching loads whatever context the conversation's own file already holds; the instruction,
 * the provider's metadata and the credential cross with the call.
 */
class RustAgentChat(
    private val handle: AgentHandle,
) : AgentChat {

    /** How many messages one conversation's context holds right now (diagnostics). */
    fun contextLen(sessionId: String): Int = handle.contextLen(sessionId).toInt()

    override suspend fun startConversation(
        sessionId: String,
        provider: ProviderConfig,
        modelId: String,
        instruction: String,
        settings: AgentSettings,
    ) {
        withContext(Dispatchers.IO) {
            handle.startConversation(
                sessionId,
                provider.toProviderInput(),
                modelId,
                instruction,
                CoreAgentSettings(
                    outputLanguage = settings.outputLanguage,
                    appLanguage = settings.appLanguage,
                ),
            )
        }
    }

    override fun send(sessionId: String, text: String): Flow<ChatEvent> =
        turn(sessionId) { listener -> handle.send(sessionId, text, listener) }

    override fun respondToPrompts(sessionId: String, answers: List<String>): Flow<ChatEvent> =
        turn(sessionId) { listener -> handle.respondToPrompts(sessionId, answers, listener) }

    override suspend fun interrupt(sessionId: String) {
        withContext(Dispatchers.IO) { handle.interrupt(sessionId) }
    }

    override fun continueTurn(sessionId: String): Flow<ChatEvent> =
        turn(sessionId) { listener -> handle.recoverTurn(sessionId, listener) }

    override suspend fun contextWindow(sessionId: String): Long? =
        withContext(Dispatchers.IO) { handle.contextWindow(sessionId) }

    override suspend fun conversationContextWindow(sessionId: String): Long? =
        withContext(Dispatchers.IO) { handle.conversationContextWindow(sessionId)?.toLong() }

    override suspend fun conversationUsage(sessionId: String): ContextUsage? =
        withContext(Dispatchers.IO) {
            handle.conversationUsage(sessionId)?.let { snapshot ->
                coreContextUsage(snapshot.info, snapshot.breakdown)
            }
        }

    override suspend fun reasoningEffort(sessionId: String): String? =
        withContext(Dispatchers.IO) { handle.reasoningEffort(sessionId) }

    override suspend fun conversationReasoningEffort(sessionId: String): String? =
        withContext(Dispatchers.IO) { handle.conversationReasoningEffort(sessionId) }

    override suspend fun setReasoningEffort(sessionId: String, value: String) {
        withContext(Dispatchers.IO) { handle.setReasoningEffort(sessionId, value) }
    }

    override fun setContextWindow(sessionId: String, tokens: Long): Flow<ChatEvent> =
        once { listener -> handle.setContextWindow(sessionId, tokens.toULong(), listener) }

    override suspend fun persistInterruptedReply(sessionId: String, text: String) {
        withContext(Dispatchers.IO) { handle.persistInterruptedReply(sessionId, text) }
    }

    override fun endConversation(sessionId: String) {
        handle.endConversation(sessionId)
    }

    /**
     * Runs one turn and forwards what the core reports.
     *
     * Failures do not throw — they arrive as [ChatEvent.Failed], which is what [AgentChat.send]
     * promises. The flow completes when the turn does: [ChatEvent.Completed], a failure, or a
     * prompt that stops the turn until [respondToPrompts] is called.
     *
     * Cancelling the flow **stops that conversation's turn**: the collector going away — the user
     * leaving the screen, the ViewModel being cleared, the conversation being closed — interrupts the
     * core, which stops sampling at its next await point and keeps what it had produced. Cancelling
     * the coroutine alone would not: it is parked in a synchronous JNI call, so the turn would run to
     * its end unnoticed, spending the provider's tokens for events nobody collects.
     *
     * Only that conversation is stopped: [sessionId] is what the interrupt is aimed at, and every
     * other conversation's turn keeps running (see [AgentChat]).
     */
    private fun turn(sessionId: String, run: (EventListener) -> Unit): Flow<ChatEvent> =
        coreEvents(sessionId, stopsTheTurnWhenCancelled = true, run = run)

    /**
     * Runs one core call that reports events and then returns — setting the context window.
     *
     * Unlike [turn] this cannot wait for an end-of-turn event: the call is over once it returns, so
     * the flow closes then, after everything the call emitted. There is no turn to stop either, so a
     * cancelled collection just leaves it alone.
     */
    private fun once(run: (EventListener) -> Unit): Flow<ChatEvent> = coreEvents(null) { listener ->
        run(listener)
    }

    /**
     * Forwards one core call's events as a flow, **bounded** and without dropping anything.
     *
     * 通道与收口的规则都在 [coreEventFlow] 里（那是可以单测的部分）；这里只把"没人收时怎么让核心
     * 收手"接上去 —— [stopsTheTurnWhenCancelled] 覆盖通道覆盖不到的那一种情况：收集方被取消
     * （没人再听了）而核心还在采样。取消这个协程够不到核心自己的线程，所以反过来请核心停下，
     * 见 [turn]。
     */
    private fun coreEvents(
        sessionId: String?,
        stopsTheTurnWhenCancelled: Boolean = false,
        run: (EventListener) -> Unit,
    ): Flow<ChatEvent> = coreEventFlow(
        run = run,
        onAbandoned = {
            if (stopsTheTurnWhenCancelled && sessionId != null) {
                handle.interrupt(sessionId)
            }
        },
    )
}

/**
 * 把"一次核心调用 + 它的回调"变成一条 Flow。
 *
 * 从 `RustAgentChat` 里提出来是为了**能测**：它只依赖 [EventListener] 与 [ChatEvent]，不需要
 * 真的核心句柄。两条不变量都在这里：
 *
 * - **失败必须兜住所有异常**，不只是 [AgentFailure]。`AgentChat.send` 的契约写明会话没附着时抛
 *   `IllegalStateException`，而生成绑定里这些方法并不声明 `AgentFailure` —— 只兜 `AgentFailure`
 *   的话，真正会发生的异常会逃到这个没有 handler 的根作用域，而且跳过下面的收口。
 * - **通道必须收口**，正常结束、失败、取消三条路都要。漏掉收口，收集方 `for (event in events)`
 *   就永久挂起（界面永远停在"正在生成"），而终态事件压根没产生。
 *
 * 通道本身是 [EventSink]（有界 + 文本合并 + 终态不丢）；[run] 在 [Dispatchers.IO] 上跑；
 * 收集方走了而这一轮还没结束时叫一次 [onAbandoned]（让核心收手）。
 */
internal fun coreEventFlow(
    run: (EventListener) -> Unit,
    onAbandoned: suspend () -> Unit = {},
): Flow<ChatEvent> = flow {
    val events = Channel<ChatEvent>(capacity = EVENT_CHANNEL_CAPACITY)
    val sink = EventSink(events)
    val listener = object : EventListener {
        override fun onEvent(event: CoreChatEvent) {
            sink.offer(event.toChatEvent())
        }
    }
    val worker = CoroutineScope(Dispatchers.IO).launch {
        try {
            run(listener)
        } catch (cancellation: CancellationException) {
            // 取消不是"这一轮失败了"：原样抛出，别把它翻译成一条 Failed 事件。
            throw cancellation
        } catch (failure: Throwable) {
            sink.offer(
                ChatEvent.Failed(
                    detail = failure.message ?: failure.toString(),
                    kind = failure.toKind(),
                )
            )
        } finally {
            sink.finish()
        }
    }
    var drained = false
    try {
        for (event in events) {
            emit(event)
        }
        drained = true
    } finally {
        events.close()
        if (!drained) {
            // 取消是"没人再收了"，不是"这一轮跑完了"：通报核心收手。它会在下一个等待点停住，
            // 把已经产出的部分留下 —— 这也是 [ChatEvent.Aborted] 的来源。别的会话不受影响。
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { onAbandoned() }
            }
        }
        worker.cancel()
    }
}

/** The provider entry the core's boundary takes. */
internal fun ProviderConfig.toProviderInput(): ProviderInput = ProviderInput(
    id = id,
    name = name,
    baseUrl = baseUrl,
    wireApi = when (apiType) {
        ProviderApiType.CHAT_COMPLETIONS -> WireApi.CHAT
        ProviderApiType.RESPONSES -> WireApi.RESPONSES
    },
    apiKey = apiKey,
    // 界面给每个模型配的 token 预算（上下文长度 / 最大输出）与默认档随 provider 一起进核心；0 / 空串
    // 表示没设置，核心自己决定默认（起点档先问模型目录）。档位**表**不在这里 —— 那是目录的事。
    models = models.map { model ->
        ModelInput(
            id = model.id,
            modelId = model.modelId,
            name = model.name,
            contextLength = model.contextLength.toUInt(),
            maxOutputLength = model.maxOutputLength.toUInt(),
            reasoningEffort = model.reasoningEffort,
        )
    },
)

/**
 * One core event, as this module's consumers see it.
 *
 * `internal`（而不是 private）只是为了**能测**：九个变体全靠它翻译，写错一个就是一类事件在界面
 * 上消失。它只依赖生成绑定里的类型，不需要真的核心句柄。
 */
internal fun CoreChatEvent.toChatEvent(): ChatEvent = when (this) {
    is CoreChatEvent.Text -> ChatEvent.Text(v1)
    is CoreChatEvent.Reasoning -> ChatEvent.Reasoning(v1)
    is CoreChatEvent.ToolCall -> ChatEvent.ToolCall(name, arguments)
    is CoreChatEvent.ToolResult -> ChatEvent.ToolResult(name, result)
    is CoreChatEvent.UserPromptRequested -> ChatEvent.UserPromptRequested(prompt, options)
    is CoreChatEvent.Failed -> ChatEvent.Failed(v1)
    CoreChatEvent.Completed -> ChatEvent.Completed
    is CoreChatEvent.Aborted -> ChatEvent.Aborted(this.durationMs.toLong())
    is CoreChatEvent.Compacting -> ChatEvent.Compacting(tokens, contextWindow)
    is CoreChatEvent.Usage -> ChatEvent.Usage(coreContextUsage(info, breakdown))
}

/** 核心报的用量，翻译成本模块的；实时事件与从文件恢复走的是同一条路。 */
private fun coreContextUsage(
    info: CoreTokenUsageInfo,
    breakdown: List<CoreContextUsageBreakdownItem>,
): ContextUsage = ContextUsage(
    // 与核心的 TokenUsage::tokens_in_context_window 同一个口径：一次请求放进窗口的总量。
    usedTokens = info.lastTokenUsage.totalTokens,
    totalTokens = info.totalTokenUsage.totalTokens,
    modelContextWindow = info.modelContextWindow,
    breakdown = breakdown.map { bucket ->
        ContextUsageBucket(source = bucket.source.toContextUsageSource(), tokens = bucket.tokens)
    },
)

/** 核心的来源枚举，翻译成本模块自己的。 */
private fun CoreContextUsageSource.toContextUsageSource(): ContextUsageSource = when (this) {
    CoreContextUsageSource.SYSTEM_PROMPT -> ContextUsageSource.SYSTEM_PROMPT
    CoreContextUsageSource.SYSTEM_TOOL_SCHEMAS -> ContextUsageSource.SYSTEM_TOOL_SCHEMAS
    CoreContextUsageSource.SKILLS -> ContextUsageSource.SKILLS
    CoreContextUsageSource.MCP_TOOL_SCHEMAS -> ContextUsageSource.MCP_TOOL_SCHEMAS
    CoreContextUsageSource.MESSAGES -> ContextUsageSource.MESSAGES
}

/**
 * 失败 → 界面分型。
 *
 * 除了核心跨边界报的 [AgentFailure]，这里还要认**本地抛的**异常：`AgentChat.send` 的契约写明
 * 会话没附着时抛 `IllegalStateException`，那是调用时序问题，与"网络失败"不是一回事 —— 认不出来
 * 就会给用户一句错的"能怎么办"。
 *
 * 「回合中途的失败」走的是另一条路：核心的 `ChatEvent::Failed` 只带一句文本，所以那条路的分型是
 * [ChatFailureKind.UNKNOWN] —— 给事件也带上分型要改跨边界的事件协议，另行评估。
 */
internal fun Throwable.toKind(): ChatFailureKind = when (this) {
    is AgentFailure.NoSession -> ChatFailureKind.NO_SESSION
    is AgentFailure.Transport -> ChatFailureKind.TRANSPORT
    is AgentFailure.Transcript -> ChatFailureKind.TRANSCRIPT
    is AgentFailure.Internal -> ChatFailureKind.INTERNAL
    is IllegalStateException -> ChatFailureKind.INTERNAL
    else -> ChatFailureKind.UNKNOWN
}

/**
 * 跨边界失败里那句"能显示的原因"。
 *
 * 四个变体各带一个 `detail`，但生成出来的基类上没有这个属性（只有 `message`，内容是
 * `detail=...` 这种调试形状），所以要在这里逐个取出来。分型问"该说什么、重试有没有意义"，
 * 这一句就是直接给用户看的那半句。
 */
internal val AgentFailure.detail: String
    get() = when (val failure = this) {
        is AgentFailure.NoSession -> failure.detail
        is AgentFailure.Transport -> failure.detail
        is AgentFailure.Transcript -> failure.detail
        is AgentFailure.Internal -> failure.detail
    }

/** Whether this event is the last one of its turn. */
internal fun ChatEvent.endsTurn(): Boolean = when (this) {
    // A prompt stops the turn: the interactive call has no result yet, and nothing more arrives
    // until the answers are submitted.
    ChatEvent.Completed,
    is ChatEvent.Aborted,
    is ChatEvent.Failed,
    is ChatEvent.UserPromptRequested,
    -> true
    is ChatEvent.Text,
    is ChatEvent.Reasoning,
    is ChatEvent.ToolCall,
    is ChatEvent.ToolResult,
    -> false
    // 用量随每个采样轮一起到，不是回合的结束。
    is ChatEvent.Usage -> false
    // 压缩是这一轮**中途**的一次停顿，压完照常往下跑。
    is ChatEvent.Compacting -> false
}

/**
 * 事件通道的容量。
 *
 * 64 与 `Channel.BUFFERED` 同量级：够吸收一次突发（工具调用前后连着几条），又不至于让界面
 * 落后太远。**取舍**：不采用"丢帧"策略 —— 文本增量丢掉会让界面与落盘转录不一致，而
 * 缓冲 + 合并已经能压住积压，不必再牺牲一致性。
 */
private const val EVENT_CHANNEL_CAPACITY = 64

/**
 * 把核心回调线程上的事件排进通道 —— **有界**，文本只合并、不丢。
 *
 * 为什么不是无界队列：核心按增量产事件，一次长回复上千条；界面（主线程）一忙，队列就无限涨。
 * 涨的是进程内存，而积压的事件在界面追上之前**已经是过去时**了。有界 + 文本合并把"界面忙"
 * 变成**背压**：核心回调线程在非得等的时候等一等。
 *
 * 不变量（比容量重要）：
 * - **顺序不变**：文本/推理的增量按到达顺序拼接；非文本事件入队前先把攒下的增量并成一条发出去。
 * - **不丢**：文本/推理满了就并进缓冲，凑一条再发（合成一条同类型事件，界面照样只是往后追加）；
 *   其余事件频次低（工具调用、终态、用量），没位置就阻塞等待 —— 丢掉一条终态，flow 就永远不闭合。
 * - **终态优先收口**：终态事件入队后随即关通道，之后到达的任何事件都不会再被消费。
 *
 * 线程：[offer] / [finish] 由核心的回调线程调用（可能多线程），内部用锁串行化。
 */
internal class EventSink(private val events: Channel<ChatEvent>) {

    private val lock = Any()
    private val pendingText = StringBuilder()
    private val pendingReasoning = StringBuilder()

    /** 排入一条事件；[ChatEvent.endsTurn] 为真时收口。 */
    fun offer(event: ChatEvent) {
        val terminal = synchronized(lock) {
            when (event) {
                is ChatEvent.Text -> {
                    pendingText.append(event.text)
                    drainText()
                    return
                }
                is ChatEvent.Reasoning -> {
                    pendingReasoning.append(event.text)
                    drainReasoning()
                    return
                }
                else -> Unit
            }
            // 非文本事件：先把攒下的增量按原顺序发出去，再发它自己，顺序才对得上。
            drainBuffered()
            sendBlocking(event)
            event.endsTurn()
        }
        if (terminal) events.close()
    }

    /** 核心这一轮结束了（正常返回或报错）：把残留增量发完再收口。 */
    fun finish() {
        synchronized(lock) { drainBuffered() }
        events.close()
    }

    /** 通道还有位置就把增量整条发出去（快路径：一条增量一条事件，与改造前一致）。 */
    private fun drainText() {
        while (pendingText.isNotEmpty() && events.trySend(ChatEvent.Text(pendingText.toString())).isSuccess) {
            pendingText.clear()
        }
    }

    private fun drainReasoning() {
        while (pendingReasoning.isNotEmpty() && events.trySend(ChatEvent.Reasoning(pendingReasoning.toString())).isSuccess) {
            pendingReasoning.clear()
        }
    }

    /** 把攒下的增量发完 —— 推理在前、文本在后，与它们的到达顺序一致。 */
    private fun drainBuffered() {
        if (pendingReasoning.isNotEmpty()) {
            sendBlocking(ChatEvent.Reasoning(pendingReasoning.toString()))
            pendingReasoning.clear()
        }
        if (pendingText.isNotEmpty()) {
            sendBlocking(ChatEvent.Text(pendingText.toString()))
            pendingText.clear()
        }
    }

    /**
     * 等通道腾出位置再入队。只在非文本事件（频次低）与收尾时用；通道已收口时直接放弃 ——
     * 没有接收方了，阻塞没有意义。
     */
    private fun sendBlocking(event: ChatEvent) {
        try {
            runBlocking { events.send(event) }
        } catch (_: ClosedSendChannelException) {
            // 通道已关（终端事件或取消）：这一条没有接收方了。
        }
    }
}
