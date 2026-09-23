package com.lhzkml.jasmine.core.data.model

/**
 * A persisted chat conversation (metadata only — the transcript itself is a list
 * of [TranscriptMessage]).
 *
 * [providerId] / [modelId] record which model produced it so the chat can resume
 * with the same one; [updatedAt] orders the history list.
 */
data class Conversation(
    val id: String,
    val title: String,
    val providerId: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One transcript message as persisted. Carries no id: the UI owns the identity it
 * needs for list keys, and nothing else refers to an individual message.
 */
data class TranscriptMessage(
    val role: ChatRole,
    val text: String,
    val isError: Boolean = false,
)
