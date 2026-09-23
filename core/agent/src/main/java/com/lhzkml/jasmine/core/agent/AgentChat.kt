package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.flow.Flow

/**
 * One prior turn replayed into a fresh session.
 *
 * Deliberately narrower than a stored transcript message: only turns that
 * actually happened should be replayed, so the caller drops failed replies
 * (an error string is not model output).
 */
data class ChatTurn(
    val role: ChatRole,
    val text: String,
)

/**
 * One incremental piece of an assistant turn.
 */
sealed interface ChatEvent {
    /** More assistant text arrived; append it to the running reply. */
    data class Text(val text: String) : ChatEvent

    /** The turn failed; [detail] is the raw reason (HTTP status, provider error…). */
    data class Failed(val detail: String) : ChatEvent

    /** The turn finished normally. */
    data object Completed : ChatEvent
}

/**
 * Multi-turn conversation facade over ADK.
 *
 * Keeps ADK entirely inside this module: the caller passes a [ProviderConfig]
 * and a model id, and receives plain [ChatEvent]s. Conversation history is held
 * by ADK's in-memory session service, so callers only track what they render.
 *
 * Lifecycle is explicit — [startConversation] creates a fresh session (dropping
 * any previous history), [send] appends to it, [endConversation] discards it.
 * Switching provider or model therefore means starting a new conversation.
 *
 * Implementations are stateful and **not** thread-safe: one instance per
 * conversation owner (the ViewModel), and one in-flight [send] at a time.
 */
interface AgentChat {
    /**
     * Creates a new conversation against [provider] / [modelId] with
     * [instruction] as the system prompt, discarding any existing history.
     *
     * [history] is replayed into the new session so a restored transcript keeps
     * its context: the runner builds the model request from the session's
     * events, so without replaying them the model would answer as if the
     * conversation were brand new.
     */
    suspend fun startConversation(
        provider: ProviderConfig,
        modelId: String,
        instruction: String,
        history: List<ChatTurn> = emptyList(),
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

    /** Drops the conversation and releases the runner. */
    fun endConversation()
}
