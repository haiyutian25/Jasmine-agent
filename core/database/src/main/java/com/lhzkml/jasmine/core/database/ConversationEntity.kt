package com.lhzkml.jasmine.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One persisted chat conversation.
 *
 * [title] is derived from the first user message (the transcript itself lives in
 * [MessageEntity]); [providerId] / [modelId] record which model produced it, so
 * the chat can resume with the same one. [updatedAt] orders the history list.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long,
)
