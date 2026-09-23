package com.lhzkml.jasmine.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for the chat transcript.
 *
 * Queries are validated against the schema at compile time, so the SQL here is
 * checked by the build rather than at runtime.
 */
@Dao
interface ChatHistoryDao {

    /** All conversations, most recently updated first. */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    /** The conversation to restore on launch, or null when none exists yet. */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestConversation(): ConversationEntity?

    /** Transcript of one conversation, oldest first. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY seq ASC")
    suspend fun messagesOf(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Insert
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchConversation(id: String, updatedAt: Long)

    /** Messages are removed by the foreign key's cascade. */
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)
}
