package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.flow.Flow

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

    /** Releases the runner. Stored history is left untouched. */
    fun endConversation()
}