package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.flow.Flow

/**
 * One incremental piece of an assistant turn.
 *
 * A turn is a sequence: any amount of [Text] and [ToolCall]/[ToolResult] pairs, then
 * [Completed], [Failed], or [UserPromptRequested] when the agent stopped to ask
 * something. Tool activity is reported so the caller can show what the agent is
 * doing — while a tool runs the model is silent, and without this the UI would look
 * frozen.
 */
sealed interface ChatEvent {
    /** More assistant text arrived; append it to the running reply. */
    data class Text(val text: String) : ChatEvent

    /**
     * More of the model's **thinking** arrived. It is not part of the reply: the chat keeps it in a
     * collapsible block above the answer, and it streams ahead of it.
     */
    data class Reasoning(val text: String) : ChatEvent

    /**
     * The model asked to call a tool. [arguments] is the raw argument map rendered
     * for display; the tool itself is executed by ADK, not by the caller.
     */
    data class ToolCall(val name: String, val arguments: String) : ChatEvent

    /** A tool finished; [result] is what it returned, already truncated for display. */
    data class ToolResult(val name: String, val result: String) : ChatEvent

    /**
     * The agent stopped and is waiting on the user: a tool asked a question or offered
     * a choice. **The turn ends here** — no [Completed] follows, and nothing more is
     * emitted until [AgentChat.respondToPrompts] is called.
     *
     * [options] is empty when free-form text is expected, otherwise it holds the
     * choices the user must pick from.
     */
    data class UserPromptRequested(
        val prompt: String,
        val options: List<String>,
    ) : ChatEvent

    /** The turn failed; [detail] is the raw reason (HTTP status, provider error…). */
    data class Failed(val detail: String) : ChatEvent

    /** The turn finished normally. */
    data object Completed : ChatEvent

    /**
     * The turn stopped because the platform asked it to; what it had produced so far is already
     * in the transcript. [durationMs] is how long that turn had been running.
     */
    data class Aborted(val durationMs: Long) : ChatEvent

    /**
     * One request's context window: what it cost, how big the window is, and where the tokens
     * went. 它是请求的元信息而不是回复的一步，但和回复一样，要等模型答完才到。
     */
    data class Usage(val usage: ContextUsage) : ChatEvent
}

/**
 * Multi-turn conversation facade over ADK.
 *
 * Keeps ADK entirely inside this module: the caller passes a session id, a
 * [ProviderConfig] and a model id, and receives plain [ChatEvent]s.
 *
 * Lifecycle is explicit — [startConversation] attaches to the session named by
 * [startConversation.sessionId], [send] appends to it, [endConversation] releases
 * the runner without erasing stored history. Switching provider or model
 * therefore means starting a new conversation.
 *
 * Implementations are stateful and **not** thread-safe: one instance per
 * conversation owner (the ViewModel), and one in-flight [send] at a time.
 */
interface AgentChat {
    /**
     * Attaches to the conversation identified by [sessionId] — the caller's own
     * persisted conversation id, so the model's working context and the durable
     * transcript share one identity.
     *
     * The conversation's context is whatever the session store already holds for
     * that id; there is no replay path. A conversation whose session is gone
     * starts from an empty context.
     */
    suspend fun startConversation(
        sessionId: String,
        provider: ProviderConfig,
        modelId: String,
        instruction: String,
    )

    /**
     * Appends [text] to the running conversation and streams the reply.
     *
     * Transport/provider failures arrive as [ChatEvent.Failed] rather than
     * throwing; only cancellation propagates.
     *
     * @throws IllegalStateException when called before [startConversation].
     */
    fun send(text: String): Flow<ChatEvent>

    /**
     * Answers the pending [ChatEvent.UserPromptRequested]s and resumes the paused turn,
     * streaming whatever the model does next — including another prompt.
     *
     * @param answers 与**提问顺序一一对应**的回答。一轮里可能同时挂出多个交互调用
     *   （实测模型会在一轮里并列调 `get_user_choice` 和 `adk_request_input`），界面会排队逐个
     *   问、把答案按顺序收齐后一起提交。少交一个，历史里就会留下「有 tool_call、没有
     *   tool_result」的残缺记录，之后每次请求都被服务端以 HTTP 400 拒掉、会话永久卡死。
     * @throws IllegalStateException when no prompt is pending.
     */
    fun respondToPrompts(answers: List<String>): Flow<ChatEvent>

    /**
     * Persists the partial reply left behind when the caller cancelled a [send].
     *
     * Needed because ADK writes an assistant reply as **one settled event at the end of
     * the turn** — streaming chunks are never stored on their own. A cancelled turn
     * therefore leaves only the user's event behind, and since the transcript is rebuilt
     * from the session's events, that half-written reply would vanish on the next reload
     * (and the model's context would not contain it either).
     *
     * A caller that keeps the partial text on screen must call this so both sides agree.
     * No-op when [text] is blank or no conversation is attached.
     */
    /**
     * Stops the turn that is running.
     *
     * The turn is not cut off mid-flight: the core notices at its next await point, keeps what it
     * had already produced (it is already in the transcript), and ends the flow with
     * [ChatEvent.Aborted].
     */
    suspend fun interrupt()

    /**
     * The window the attached conversation runs against, in tokens.
     *
     * `null` when no conversation is attached: nothing has been resolved yet, and the caller's own
     * default stands in.
     */
    suspend fun contextWindow(): Long?

    /**
     * The window one conversation recorded, in tokens, read straight from its file.
     *
     * Needs no attachment: it is what the window picker shows for a conversation that was just
     * opened, before anything is sent into it. `null` means the conversation never got one — the
     * caller's own default stands in until it is first attached.
     */
    suspend fun conversationContextWindow(sessionId: String): Long?

    /**
     * What one conversation last reported costing, read straight from its file.
     *
     * Needs no attachment: it is what the caller shows for a conversation that was just opened —
     * and after a restart it is the only source, since a live figure does not survive the process.
     * `null` means the conversation never reported a cost.
     */
    suspend fun conversationUsage(sessionId: String): ContextUsage?

    /**
     * Sets the window the conversation runs against, in tokens.
     *
     * The core records it in the conversation's own file, so the conversation is resumed with it.
     * The flow carries the usage event the core reports straight back — the window changed, so the
     * figure the platform shows is stale the moment it returns.
     */
    fun setContextWindow(tokens: Long): Flow<ChatEvent>

    /**
     * Continues the turn that was stopped.
     *
     * Nothing is added to the conversation: the core resumes sampling under the same turn, so the
     * model picks its answer up where it left off. A turn that finished is not resumed.
     */
    fun continueTurn(): Flow<ChatEvent>

    suspend fun persistInterruptedReply(text: String)

    /** Releases the runner. Stored history is left untouched. */
    fun endConversation()
}
