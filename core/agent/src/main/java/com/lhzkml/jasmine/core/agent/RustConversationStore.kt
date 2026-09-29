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
import uniffi.jasmine_protocol.AppUsageStats as CoreAppUsageStats
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
                    thinking = entry.thinking,
                    toolStatus = entry.toolStatus,
                    stoppedAfterMs = entry.stoppedAfterMs?.toLong(),
                    timestamp = entry.recordedAt,
                    modelLabel = entry.modelLabel,
                    // 核心把「调用 + 结果」合成一行，这里还原成工具卡（与实时那一轮的形状一致）。
                    tool = entry.toolName?.let { name ->
                        com.lhzkml.jasmine.core.data.model.TranscriptToolActivity(
                            name = name,
                            detail = entry.toolDetail.orEmpty(),
                            result = entry.toolResult,
                        )
                    },
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

    override suspend fun usageStats(): AppUsage =
        withContext(Dispatchers.IO) { handle.usageStats().toAppUsage() }

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

/** 核心报的统计，翻译成本模块自己的形状。 */
private fun CoreAppUsageStats.toAppUsage(): AppUsage = AppUsage(
    totalTokens = totalTokens,
    currentStreakDays = currentStreakDays.toInt(),
    longestStreakDays = longestStreakDays.toInt(),
    days = days.map { UsageDay(date = it.date, tokens = it.tokens) },
    models = models.map { ModelUsage(modelId = it.modelId, tokens = it.tokens) },
)
