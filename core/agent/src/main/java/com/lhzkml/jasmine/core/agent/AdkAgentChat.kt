package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
import com.google.adk.kt.apps.App
import com.google.adk.kt.callbacks.AfterAgentCallback
import com.google.adk.kt.callbacks.CallbackChoice
import com.google.adk.kt.events.Event
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.runners.Runner
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.flow

/** How much of a tool's arguments/result is worth putting on screen. */
private const val ToolDetailMaxLength = 200

/**
 * ADK-backed [AgentChat]: one [LlmAgent] whose model comes from [modelFactory],
 * driven by [InMemoryRunner] over an injected [SessionService].
 *
 * The session service is injected rather than constructed so this class stays
 * free of Android/Room wiring **and** so a test can pass
 * `InMemorySessionService()`. In the app the injected one is ADK's own
 * `RoomSessionService` (see `AgentModule`), which is what makes a conversation
 * survive process death.
 *
 * Tools arrive already adapted to ADK's [BaseTool] (`AdkAgentTool` does that in
 * the composition root), so this class only hands them to the agent. ADK's own
 * `ToolProcessor` then runs the loop: declarations go into the request, a
 * `functionCall` comes back, the tool runs, its `functionResponse` is appended
 * and the model is called again.
 *
 * ## Session identity
 *
 * [startConversation] is handed a [sessionId] that is the *caller's*
 * conversation id, so the ADK session and the persisted transcript share one
 * identity. The model's context is therefore read straight out of the store: a
 * known session is resumed as-is, and an unknown one is created empty. There is
 * no replay path — the transcript is not re-fed to the model.
 *
 * [endConversation] closes the runner but deliberately does **not** delete the
 * session: dropping the in-memory object must not erase durable history.
 *
 * ## Paused turns
 *
 * A *long-running* tool (ADK's `adk_request_input` / `get_user_choice`) returns
 * without a result, which makes the runner end the turn on the function-call
 * event instead of re-calling the model. Such a call is surfaced as
 * [ChatEvent.UserPromptRequested] and remembered; [respondToPrompts] then injects
 * the `functionResponse` the runner was waiting for and streams the rest of the
 * turn. ADK correlates it by call id, so the id is kept here rather than exposed.
 *
 * ## Long-term memory
 *
 * With a memory service injected, the agent carries ADK's *after-agent callback* that
 * ingests the finished session (see [memoryIngestionCallbacks]), so `load_memory` has
 * something to find. Ingestion is the framework's hook, not a step of this class.
 *
 * ## Event mapping
 *
 * The run uses ADK's SSE streaming mode, so a model's answer arrives as partial deltas
 * and is then repeated in full on the settled event the aggregator emits. Text is taken
 * from the deltas only — taking it from the settled event as well would print the answer
 * twice — and settled events are read for tool activity alone.
 *
 * Tool activity is forwarded only from settled events: while streaming, the
 * aggregator also emits *partial* function calls whose arguments are still
 * incomplete, and acting on those would surface half-built calls.
 *
 * Events authored by [USER_AUTHOR] are skipped — the runner echoes the caller's
 * own message, which the UI already rendered. A non-null `errorMessage` becomes
 * [ChatEvent.Failed]; `errorCode` alone is *not* treated as failure because ADK
 * also uses it to report a non-`STOP` finish reason such as `MAX_TOKENS`.
 */
class AdkAgentChat(
    private val sessionService: SessionService,
    private val modelFactory: (ProviderConfig, String) -> Model,
    private val tools: List<BaseTool> = emptyList(),
    private val memoryService: MemoryService? = null,
) : AgentChat {

    private var runner: Runner? = null

    /** The id of the session the runner is attached to. */
    private var attachedSessionId: String? = null

    /**
     * Id of the current turn's invocation, recorded from the events as they arrive.
     *
     * The interrupted-reply event has to carry it: one turn is one invocation in ADK,
     * and the user's event and the assistant's share the same id (that is how the
     * stored sessions look).
     */
    private var activeInvocationId: String? = null

    /**
     * 已发出、还没被回答的交互调用（按提问顺序排队）。
     *
     * 以前这里只有一个槽位，后一个调用会把前一个**覆盖**掉 —— 模型一轮里同时调两个需要用户
     * 参与的工具时（实测 `get_user_choice` + `adk_request_input`），前一个的提问界面从不出现，
     * 于是它**永远拿不到 functionResponse**，历史里留下「有 tool_call、没有 tool_result」的
     * 残缺记录，之后每次请求都被服务端以 HTTP 400 拒掉，整个会话永久卡死。
     */
    private val pendingPrompts = ArrayDeque<PendingPrompt>()

    /**
     * 本轮是否已经挂出提问（即模型停下来等用户回答）。
     *
     * ADK 在一轮里执行完普通工具后会**接着再调一次模型**，而那一刻「等用户回答」的调用还没有
     * 结果 —— 服务端会以「An assistant message with 'tool_calls' must be followed by tool
     * messages responding to each 'tool_call_id'」直接 400（实测 15:30:29 那次：5 个调用里只有
     * 3 个普通工具的结果就发了请求）。所以一旦挂出提问就停掉这一轮的流，等用户答完由
     * [respondToPrompts] 发起新一轮继续。
     */
    private var pausedForPrompt = false

    /** The long-running call the agent is waiting on, if any. */
    private var pendingPrompt: PendingPrompt? = null

    private data class PendingPrompt(val name: String, val id: String?)

    override suspend fun startConversation(
        sessionId: String,
        provider: ProviderConfig,
        modelId: String,
        instruction: String,
    ) {
        endConversation()

        val agent = LlmAgent(
            name = AGENT_NAME,
            model = modelFactory(provider, modelId),
            description = AGENT_DESCRIPTION,
            instruction = Instruction(instruction),
            tools = tools,
            afterAgentCallbacks = memoryIngestionCallbacks(),
        )
        val key = SessionKey(appName = APP_NAME, userId = USER_ID, id = sessionId)
        if (sessionService.getSession(key) == null) {
            sessionService.createSession(key)
        }

        // ADK's own app declaration, which is also the only place resumability,
        // compaction and context caching are configurable. Left at their defaults, so
        // this is the same configuration the loose appName/agent constructor produced.
        runner = InMemoryRunner(
            app = App(appName = APP_NAME, rootAgent = agent),
            sessionService = sessionService,
            memoryService = memoryService,
        )
        attachedSessionId = sessionId
    }

    override fun send(text: String): Flow<ChatEvent> =
        run(newMessage = Content(role = Role.USER, parts = listOf(Part(text = text))))

    override fun respondToPrompts(answers: List<String>): Flow<ChatEvent> {
        check(pendingPrompts.isNotEmpty()) { "No prompt is waiting for an answer" }
        // 与提问顺序一一对应：调用方是排队问完、把答案按同一顺序收齐后才提交的。
        val pending = pendingPrompts.toList().take(answers.size)
        pendingPrompts.clear()
        val responses = pending.mapIndexed { index, prompt ->
            prompt to FunctionResponse(
                name = prompt.name,
                response = mapOf(BaseTool.RESULT_KEY to answers[index]),
                id = prompt.id,
            )
        }
        return flow {
            // 用户对提问的回答**不会**出现在事件流里：runner 不回显调用方自己的消息（下面
            // run() 里那个 USER_AUTHOR 跳过就是为它留的）。于是界面上那张工具卡片收不到
            // 「回了什么」，这里主动补一条返回。
            //
            // 格式必须与从 session 重建时一致 —— 两处都走 abbreviated()，卡片上的
            // `result=...` 才能一模一样。
            responses.forEach { (_, response) ->
                emit(
                    ChatEvent.ToolResult(
                        name = response.name,
                        result = response.response.abbreviated(),
                    )
                )
            }
            run(
                newMessage = Content(
                    role = Role.USER,
                    parts = responses.map { Part(functionResponse = it.second) },
                ),
            ).collect { emit(it) }
        }
    }

    override fun endConversation() {
        runner?.close()
        runner = null
        attachedSessionId = null
        activeInvocationId = null
        // 换会话/结束时清空：待答的调用属于**上一个 session**，留着会串到新会话上。
        pendingPrompts.clear()
    }

    /**
     * 见 [AgentChat.persistInterruptedReply]。
     *
     * 补写一条**非 partial** 的助手事件。两个必须遵守的点：
     *
     * 1. 事件不能是 partial —— `SessionService.appendEvent` 两处实现（默认实现与
     *    `RoomSessionService`）都在开头 `if (event.partial) return event` 直接跳过。
     * 2. 必须重新 `getSession` 拿一份最新的 session：`RoomSessionService.appendEvent`
     *    以 `expectedUpdateTime = session.lastUpdateTime` 做乐观锁，复用旧引用会写失败。
     */
    override suspend fun persistInterruptedReply(text: String) {
        if (text.isBlank()) return
        val sessionId = attachedSessionId ?: return
        val key = SessionKey(appName = APP_NAME, userId = USER_ID, id = sessionId)
        val session = sessionService.getSession(key) ?: return
        sessionService.appendEvent(
            session,
            Event(
                invocationId = activeInvocationId,
                author = AGENT_NAME,
                content = Content(role = Role.MODEL, parts = listOf(Part(text = text))),
            ),
        )
    }

    private fun run(newMessage: Content): Flow<ChatEvent> = flow {
        val activeRunner = checkNotNull(runner) { "No conversation: call startConversation first" }
        val activeSessionId =
            checkNotNull(attachedSessionId) { "No conversation: call startConversation first" }

        pausedForPrompt = false
        activeRunner
            .runAsync(
                userId = USER_ID,
                sessionId = activeSessionId,
                newMessage = newMessage,
                // ADK's per-run guard. Its `maxLlmCalls` limit is only enforced when a
                // RunConfig is supplied — ADK's own source warns that leaving it off
                // risks a run that never ends.
                //
                // SSE is ADK's streaming mode, and `LlmAgentTurn` is where it is read:
                // it is what makes the runner ask the model to stream.
                runConfig = RunConfig(streamingMode = StreamingMode.SSE),
            )
            // 挂出提问之后就停掉这一轮的流：否则 ADK 会在普通工具的返回落地后再调一次模型，
            // 而那时交互调用还没有结果 → 服务端 400（见 [pausedForPrompt]）。
            .takeWhile { !pausedForPrompt }
            .collect { event ->
                // 记下本回合的 invocation id：停止时补写助手事件要用它。
                activeInvocationId = event.invocationId ?: activeInvocationId
                val failure = event.errorMessage
                if (failure != null) {
                    emit(ChatEvent.Failed(failure))
                    return@collect
                }
                if (event.author != USER_AUTHOR) {
                    val parts = event.content?.parts.orEmpty()
                    // Only deltas carry text. A settled event repeats the whole answer the
                    // aggregator assembled, and is used for tool activity and nothing else.
                    if (event.partial) {
                        val chunk = parts.mapNotNull { it.text }.joinToString("")
                        if (chunk.isNotEmpty()) emit(ChatEvent.Text(chunk))
                    } else {
                        parts.forEach { part -> emitToolActivity(part) }
                    }
                }
                if (event.isFinalResponse) emit(ChatEvent.Completed)
            }

        // 因为暂停而提前掐断时，替 ADK 把「本轮结束」补上 —— 界面上那一轮才会正常落幕，
        // 输入框交回给提问控件。
        if (pausedForPrompt) emit(ChatEvent.Completed)
    }

    /**
     * ADK's own ingestion entry point: an after-agent callback is handed a
     * `CallbackContext`, and its `addSessionToMemory()` is what puts the session into
     * the memory service — the same thing a hand-written post-turn call would do, but
     * driven by the framework's hook and reading the live session directly.
     *
     * Registered only when a memory service exists: ADK throws from
     * `addSessionToMemory` when the invocation has none.
     */
    private fun memoryIngestionCallbacks(): List<AfterAgentCallback> =
        if (memoryService == null) {
            emptyList()
        } else {
            listOf(
                AfterAgentCallback { context ->
                    context.addSessionToMemory()
                    CallbackChoice.Continue(Unit)
                }
            )
        }

    /**
     * Reports one part of a settled event. A long-running call is both recorded (so
     * the turn can be resumed) and surfaced as a prompt; everything else is a plain
     * tool call or result.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<ChatEvent>.emitToolActivity(part: Part) {
        part.functionCall?.let { call ->
            val prompt = call.asUserPrompt()
            if (prompt == null) {
                emit(ChatEvent.ToolCall(name = call.name, arguments = call.args.abbreviated()))
            } else {
                // The call id is what ADK matches the answer against on resume.
                // 排队而不是覆盖：一轮里可能同时挂出多个提问（见 [pendingPrompts]）。
                if (pendingPrompts.none { it.id == call.id }) {
                    pendingPrompts.addLast(PendingPrompt(name = call.name, id = call.id))
                }
                // 挂出提问 → 本轮到此为止，别再让 ADK 往下跑（见 [pausedForPrompt]）。
                pausedForPrompt = true
                emit(ChatEvent.ToolCall(name = call.name, arguments = call.args.abbreviated()))
                emit(prompt)
            }
        }
        part.functionResponse?.let { response ->
            emit(
                ChatEvent.ToolResult(
                    name = response.name,
                    result = response.response.abbreviated(),
                )
            )
        }
    }

    private companion object {
        const val APP_NAME = "jasmine"
        const val AGENT_NAME = "jasmine_agent"
        const val AGENT_DESCRIPTION = "Jasmine assistant"
        const val USER_ID = "local_user"

        /** Author ADK stamps on the caller's own message events. */
        const val USER_AUTHOR = "user"

        /** ADK's two long-running tools: they stop the turn and wait for the user. */
        const val REQUEST_INPUT_TOOL = "adk_request_input"
        const val GET_USER_CHOICE_TOOL = "get_user_choice"
        const val MESSAGE_ARG = "message"
        const val OPTIONS_ARG = "options"
    }
}

/**
 * Turns a long-running call into the prompt it represents, or null for an ordinary
 * tool. Both shapes are ADK's own, so the argument names are read here rather than
 * modelled in the public contract.
 */
private fun FunctionCall.asUserPrompt(): ChatEvent.UserPromptRequested? = when (name) {
    "adk_request_input" -> ChatEvent.UserPromptRequested(
        prompt = args["message"]?.toString().orEmpty(),
        options = emptyList(),
    )

    "get_user_choice" -> ChatEvent.UserPromptRequested(
        prompt = "",
        options = (args["options"] as? List<*>).orEmpty().mapNotNull { it?.toString() },
    )

    else -> null
}

/** Renders model-supplied arguments / tool responses compactly for a status line. */
internal fun Map<String, Any?>.abbreviated(): String =
    entries.joinToString(", ") { (key, value) -> "$key=$value" }
        .ifEmpty { "—" }
        .let { if (it.length <= ToolDetailMaxLength) it else it.take(ToolDetailMaxLength - 1) + "…" }
