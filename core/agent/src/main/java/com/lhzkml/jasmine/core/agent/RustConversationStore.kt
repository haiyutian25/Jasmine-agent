package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import uniffi.jasmine_ffi.AgentHandle
import uniffi.jasmine_protocol.Role

/**
 * [ConversationStore] over the core's own session files.
 *
 * There is no second store: the core writes the transcript as each turn runs, and this reads it
 * back. The handle built here is never attached to a conversation — every call below is a fact
 * about files — so one instance can be shared while each [RustAgentChat] owns its own. Reads and
 * writes are blocking calls into the core, so they run on [Dispatchers.IO].
 */
class RustConversationStore(
    sessionsDir: String,
) : ConversationStore {

    private val handle = AgentHandle(sessionsDir, DeviceClock)
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())

    override val conversationsStateFlow: StateFlow<List<Conversation>> =
        conversations.asStateFlow()

    override suspend fun latestConversation(): Conversation? {
        val listed = read()
        return listed.firstOrNull()
    }

    override suspend fun messagesOf(conversationId: String): List<TranscriptMessage> =
        withContext(Dispatchers.IO) {
            handle.transcript(conversationId).map { entry ->
                TranscriptMessage(
                    role = if (entry.role == Role.USER) ChatRole.USER else ChatRole.ASSISTANT,
                    text = entry.text,
                    stoppedAfterMs = entry.stoppedAfterMs?.toLong(),
                    timestamp = entry.recordedAt,
                    modelLabel = entry.modelLabel,
                )
            }
        }

    override suspend fun createConversation(
        providerId: String,
        modelId: String,
        title: String,
    ): Conversation {
        val id = UUID.randomUUID().toString()
        withContext(Dispatchers.IO) { handle.createConversation(id, providerId, modelId, title) }
        read()
        return Conversation(
            id = id,
            title = title,
            providerId = providerId,
            modelId = modelId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun deleteConversation(id: String) {
        withContext(Dispatchers.IO) { handle.deleteConversation(id) }
        read()
    }

    override suspend fun interruptedTurn(conversationId: String): String? =
        withContext(Dispatchers.IO) { handle.interruptedTurn(conversationId) }

    override suspend fun refresh() {
        read()
    }

    private suspend fun read(): List<Conversation> {
        val listed = withContext(Dispatchers.IO) {
            handle.conversations().map { summary ->
                Conversation(
                    id = summary.sessionId,
                    title = summary.title,
                    providerId = summary.providerId,
                    modelId = summary.modelId,
                    createdAt = summary.updatedAt,
                    updatedAt = summary.updatedAt,
                )
            }
        }
        conversations.value = listed
        return listed
    }
}
