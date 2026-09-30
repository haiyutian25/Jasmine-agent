package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.AgentSettings
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
     * Forwards one core call's events as a flow.
     *
     * The events go through an **unbounded** channel, for the same reason the ViewModels' action
     * channels are: a stream of tokens must never be dropped to make room — the reply would come out
     * truncated with nothing reported anywhere, and the user would see it just stop mid-sentence.
     *
     * `callbackFlow` cannot be used here: it hands out [Channel.BUFFERED] (64 slots) and its `trySend`
     * fails, silently, the moment the collector falls behind — which the main thread does whenever it
     * is busy. The listener runs on a thread the core owns, so waiting for room is not an option
     * either; queuing is.
     *
     * The core's call runs on [Dispatchers.IO] and the flow ends when a terminal event closes the
     * channel ([ChatEvent.Completed]/a failure) or, for calls that just report and return, when the
     * call is over.
     *
     * [stopsTheTurnWhenCancelled] covers the case the channel cannot: the collector is cancelled
     * (nobody is listening any more) while the core is still sampling. Cancelling this coroutine does
     * not reach into the core's own thread, so it asks the core to stop instead — see [turn].
     */
    private fun coreEvents(
        sessionId: String?,
        stopsTheTurnWhenCancelled: Boolean = false,
        run: (EventListener) -> Unit,
    ): Flow<ChatEvent> = flow {
        val events = Channel<ChatEvent>(capacity = Channel.UNLIMITED)
        val listener = object : EventListener {
            override fun onEvent(event: CoreChatEvent) {
                val mapped = event.toChatEvent()
                events.trySend(mapped)
                if (mapped.endsTurn()) {
                    events.close()
                }
            }
        }
        val worker = CoroutineScope(Dispatchers.IO).launch {
            try {
                run(listener)
            } catch (failure: AgentFailure) {
                events.trySend(ChatEvent.Failed(failure.message ?: failure.toString()))
            }
            events.close()
        }
        var drained = false
        try {
            for (event in events) {
                emit(event)
            }
            drained = true
        } finally {
            events.close()
            if (!drained && stopsTheTurnWhenCancelled && sessionId != null) {
                // 取消是"没人再收了"，不是"这一轮跑完了"：通报核心收手。它会在下一个等待点停住，
                // 把已经产出的部分留下 —— 这也是 [ChatEvent.Aborted] 的来源。别的会话不受影响。
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { handle.interrupt(sessionId) }
                }
            }
            worker.cancel()
        }
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

/** One core event, as this module's consumers see it. */
private fun CoreChatEvent.toChatEvent(): ChatEvent = when (this) {
    is CoreChatEvent.Text -> ChatEvent.Text(v1)
    is CoreChatEvent.Reasoning -> ChatEvent.Reasoning(v1)
    is CoreChatEvent.ToolCall -> ChatEvent.ToolCall(name, arguments)
    is CoreChatEvent.ToolResult -> ChatEvent.ToolResult(name, result)
    is CoreChatEvent.UserPromptRequested -> ChatEvent.UserPromptRequested(prompt, options)
    is CoreChatEvent.Failed -> ChatEvent.Failed(v1)
    CoreChatEvent.Completed -> ChatEvent.Completed
    is CoreChatEvent.Aborted -> ChatEvent.Aborted(this.durationMs.toLong())
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

/** Whether this event is the last one of its turn. */
private fun ChatEvent.endsTurn(): Boolean = when (this) {
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
}
