package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.jasmine_ffi.AgentFailure
import uniffi.jasmine_ffi.AgentHandle
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
    sessionsDir: String,
) : AgentChat {

    private val handle = AgentHandle(sessionsDir, DeviceClock)

    /** How many messages the core's context holds right now (diagnostics). */
    fun contextLen(): Int = handle.contextLen().toInt()

    override suspend fun startConversation(
        sessionId: String,
        provider: ProviderConfig,
        modelId: String,
        instruction: String,
    ) {
        withContext(Dispatchers.IO) {
            handle.startConversation(sessionId, provider.toProviderInput(), modelId, instruction)
        }
    }

    override fun send(text: String): Flow<ChatEvent> =
        turn { listener -> handle.send(text, listener) }

    override fun respondToPrompts(answers: List<String>): Flow<ChatEvent> =
        turn { listener -> handle.respondToPrompts(answers, listener) }

    override suspend fun interrupt() {
        withContext(Dispatchers.IO) { handle.interrupt() }
    }

    override fun continueTurn(): Flow<ChatEvent> = turn { listener -> handle.recoverTurn(listener) }

    override suspend fun contextWindow(): Long? =
        withContext(Dispatchers.IO) { handle.contextWindow() }

    override suspend fun conversationContextWindow(sessionId: String): Long? =
        withContext(Dispatchers.IO) { handle.conversationContextWindow(sessionId)?.toLong() }

    override suspend fun conversationUsage(sessionId: String): ContextUsage? =
        withContext(Dispatchers.IO) {
            handle.conversationUsage(sessionId)?.let { snapshot ->
                coreContextUsage(snapshot.info, snapshot.breakdown)
            }
        }

    override fun setContextWindow(tokens: Long): Flow<ChatEvent> =
        once { listener -> handle.setContextWindow(tokens.toULong(), listener) }

    override suspend fun persistInterruptedReply(text: String) {
        withContext(Dispatchers.IO) { handle.persistInterruptedReply(text) }
    }

    override fun endConversation() {
        handle.endConversation()
    }

    /**
     * Runs one turn and forwards what the core reports.
     *
     * Failures do not throw — they arrive as [ChatEvent.Failed], which is what [AgentChat.send]
     * promises. The flow completes when the turn does: [ChatEvent.Completed], a failure, or a
     * prompt that stops the turn until [respondToPrompts] is called.
     *
     * Cancelling the flow does not interrupt the core's turn: the Rust call is synchronous and
     * runs to completion on its own thread, and the events it produces after that go nowhere.
     * Interrupting a turn is not implemented yet.
     */
    private fun turn(run: (EventListener) -> Unit): Flow<ChatEvent> = callbackFlow {
        val listener = object : EventListener {
            override fun onEvent(event: CoreChatEvent) {
                val mapped = event.toChatEvent()
                trySend(mapped)
                if (mapped.endsTurn()) close()
            }
        }
        val job = launch(Dispatchers.IO) {
            try {
                run(listener)
            } catch (failure: AgentFailure) {
                trySend(ChatEvent.Failed(failure.message ?: failure.toString()))
                close()
            }
        }
        awaitClose { job.cancel() }
    }

    /**
     * Runs one core call that reports events and then returns — setting the context window.
     *
     * Unlike [turn] this cannot wait for an end-of-turn event: the call is over once it returns, so
     * the flow closes then, after everything the call emitted.
     */
    private fun once(run: (EventListener) -> Unit): Flow<ChatEvent> = callbackFlow {
        val listener = object : EventListener {
            override fun onEvent(event: CoreChatEvent) {
                trySend(event.toChatEvent())
            }
        }
        val job = launch(Dispatchers.IO) {
            try {
                run(listener)
            } catch (failure: AgentFailure) {
                trySend(ChatEvent.Failed(failure.message ?: failure.toString()))
            }
            close()
        }
        awaitClose { job.cancel() }
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
    // 界面给每个模型配的 token 预算（上下文长度 / 最大输出）与推理强度随 provider 一起进核心；
    // 0 / 空串表示没设置，核心自己决定默认。
    models = models.map { model ->
        ModelInput(
            id = model.id,
            modelId = model.modelId,
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
