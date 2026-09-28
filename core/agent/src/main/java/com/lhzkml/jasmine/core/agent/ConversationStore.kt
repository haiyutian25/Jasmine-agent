package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import kotlinx.coroutines.flow.StateFlow

/**
 * The conversation history the UI reads: the same conversations the core runs on.
 *
 * There is no second store. A conversation is one of the core's own session files — the id is the
 * file's session id, its turns are the lines that file recorded, and the display metadata rides on
 * the file's first line. Nothing here keeps its own copy.
 */
interface ConversationStore {
    /**
     * Snapshot of every conversation, most recently updated first.
     *
     * The core's files are not observable, so this is refreshed by [refresh] — the caller does
     * that after it changes anything, which is enough in one process.
     */
    val conversationsStateFlow: StateFlow<List<Conversation>>

    /** The conversation to restore on launch, or null when none exists yet. */
    suspend fun latestConversation(): Conversation?

    /** Transcript of [conversationId], oldest first. */
    suspend fun messagesOf(conversationId: String): List<TranscriptMessage>

    /** Creates the session backing a new conversation, titled [title]. */
    suspend fun createConversation(
        providerId: String,
        modelId: String,
        title: String,
    ): Conversation

    /** Deletes the conversation and, with it, the session. */
    suspend fun deleteConversation(id: String)

    /**
     * The conversation's turn that stopped before finishing, if it has one, so its answer can be
     * picked up again. It is a fact about the stored conversation, not about the live turn.
     */
    suspend fun interruptedTurn(conversationId: String): String?

    /** Re-reads the conversation list from the store. */
    suspend fun refresh()
}
