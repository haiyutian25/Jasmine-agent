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
    suspend fun persistInterruptedReply(text: String)

    /** Releases the runner. Stored history is left untouched. */
    fun endConversation()
}
