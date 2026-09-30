package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import kotlinx.coroutines.flow.Flow
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

    /**
     * 读存储失败的通知（D5）：核心的会话文件读不出来时，每次失败发一条（核心给的原因，供日志）。
     *
     * 有它之前，读失败被吞成空表 —— "目录坏了"和"还没有会话"在界面上长得一模一样，用户看到的
     * 是自己的历史凭空消失。现在 [conversationsStateFlow] **保留上一次成功的快照**，失败只作为
     * 提示出现。
     */
    val readFailures: Flow<String>

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

    /**
     * What the app has spent, read back out of every stored conversation.
     *
     * The lifetime total covers everything ever spent; the day list, the models and the streaks
     * cover the current month only, because each month turning over deletes the records of the
     * month before.
     */
    suspend fun usageStats(): AppUsage

    /** Re-reads the conversation list from the store. */
    suspend fun refresh()
}
