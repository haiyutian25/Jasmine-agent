package com.lhzkml.jasmine.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One persisted transcript message.
 *
 * Ordering uses the auto-generated [seq] rather than [createdAt]: the user
 * message and the assistant reply of a single turn can land in the same
 * millisecond, and the transcript must not depend on timestamp resolution.
 * Deleting a conversation cascades to its messages.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val conversationId: String,
    val role: String,
    val text: String,
    val isError: Boolean,
    val createdAt: Long,
)
