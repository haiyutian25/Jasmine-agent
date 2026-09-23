package com.lhzkml.jasmine.core.data.repository

import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManager
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import com.lhzkml.jasmine.core.database.ChatHistoryDao
import com.lhzkml.jasmine.core.database.ConversationEntity
import com.lhzkml.jasmine.core.database.MessageEntity
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Data-layer entry point for the persisted chat transcript.
 *
 * Room entities stay behind this boundary: callers work with [Conversation] and
 * [TranscriptMessage] and never see a DAO.
 */
interface ChatHistoryRepository {
    /** Hot stream of all conversations, most recently updated first. */
    val conversationsStateFlow: StateFlow<List<Conversation>>

    /** The conversation to restore on launch, or null when none exists yet. */
    suspend fun latestConversation(): Conversation?

    /** Transcript of [conversationId], oldest first. */
    suspend fun messagesOf(conversationId: String): List<TranscriptMessage>

    /** Creates and returns a new conversation titled [title]. */
    suspend fun createConversation(
        providerId: String,
        modelId: String,
        title: String,
    ): Conversation

    /**
     * Appends one message and bumps the conversation's `updatedAt`.
     *
     * @throws android.database.sqlite.SQLiteConstraintException when
     * [conversationId] no longer exists — callers treat persistence as
     * best-effort and must not let a failed write break the conversation.
     */
    suspend fun appendMessage(
        conversationId: String,
        role: ChatRole,
        text: String,
        isError: Boolean = false,
    )

    /** Deletes the conversation; its messages go with it (foreign key cascade). */
    suspend fun deleteConversation(id: String)
}

/**
 * Room-backed implementation. Reads expose a [StateFlow] over the DAO's observed
 * query; each write is a single statement (plus the `updatedAt` touch).
 */
class ChatHistoryRepositoryImpl(
    private val chatHistoryDao: ChatHistoryDao,
    dispatcherManager: DispatcherManager,
) : ChatHistoryRepository {

    // Long-lived repository scope on a deterministic dispatcher. A SupervisorJob
    // keeps a failed collection from killing the scope (and the StateFlow with it).
    private val repositoryScope = CoroutineScope(SupervisorJob() + dispatcherManager.default)

    override val conversationsStateFlow: StateFlow<List<Conversation>> =
        chatHistoryDao
            .observeConversations()
            .map { rows -> rows.map(ConversationEntity::toConversation) }
            .stateIn(
                scope = repositoryScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    override suspend fun latestConversation(): Conversation? =
        chatHistoryDao.latestConversation()?.toConversation()

    override suspend fun messagesOf(conversationId: String): List<TranscriptMessage> =
        chatHistoryDao.messagesOf(conversationId).map(MessageEntity::toTranscriptMessage)

    override suspend fun createConversation(
        providerId: String,
        modelId: String,
        title: String,
    ): Conversation {
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            providerId = providerId,
            modelId = modelId,
            createdAt = now,
            updatedAt = now,
        )
        chatHistoryDao.upsertConversation(conversation.toEntity())
        return conversation
    }

    override suspend fun appendMessage(
        conversationId: String,
        role: ChatRole,
        text: String,
        isError: Boolean,
    ) {
        val now = System.currentTimeMillis()
        chatHistoryDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = role.id,
                text = text,
                isError = isError,
                createdAt = now,
            )
        )
        chatHistoryDao.touchConversation(conversationId, now)
    }

    override suspend fun deleteConversation(id: String) =
        chatHistoryDao.deleteConversation(id)
}

// ── Room ↔ domain ─────────────────────────────────────────────────────

private fun ConversationEntity.toConversation(): Conversation = Conversation(
    id = id,
    title = title,
    providerId = providerId,
    modelId = modelId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    providerId = providerId,
    modelId = modelId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun MessageEntity.toTranscriptMessage(): TranscriptMessage = TranscriptMessage(
    role = ChatRole.fromId(role),
    text = text,
    isError = isError,
)
