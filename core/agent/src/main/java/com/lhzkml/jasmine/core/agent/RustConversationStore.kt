package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import uniffi.jasmine_ffi.AgentHandle
import uniffi.jasmine_ffi.ConversationStoreListener
import uniffi.jasmine_protocol.AppUsageStats as CoreAppUsageStats
import uniffi.jasmine_protocol.Role

/**
 * [ConversationStore] over the core's own session files.
 *
 * There is no second store: the core writes the transcript as each turn runs, and this reads it
 * back. It shares the process's single [AgentHandle] with the chat side (D5) — that sharing is what
 * makes the core's store-changed signal reachable: the signal fires on whichever instance wrote the
 * file, and with separate services this store would never hear about a turn's output.
 *
 * Reads and writes are blocking calls into the core, so they run on [Dispatchers.IO]. The core's
 * signal only says "something changed"; the refresh itself is debounced here, because a running
 * turn writes a line per event.
 */
class RustConversationStore(
    private val handle: AgentHandle,
) : ConversationStore {

    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())

    /**
     * 核心推送的"存储变了"信号。`conflate` 让积压的信号合并成一个 —— 刷新是"读当前状态"，
     * 排在后面的那次读已经包含了前面所有的变化，中间那些重复刷新没有意义。
     */
    private val storeChanges = Channel<Unit>(Channel.CONFLATED)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 回调在核心的调用线程上同步执行（回合跑着时就是跑回合那条 IO 线程）：只允许"投一条信号"
        // 这种极轻的操作 —— 重活与异常都不能在这里做，否则会拖住/污染正在进行的那次调用。
        handle.setStoreListener(
            object : ConversationStoreListener {
                override fun onStoreChanged() {
                    storeChanges.trySend(Unit)
                }
            }
        )
        scope.launch {
            storeChanges.receiveAsFlow()
                .debounce(STORE_CHANGE_DEBOUNCE_MS)
                .collect { read() }
        }
    }

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

    private companion object {
        /**
         * 核心信号的防抖窗口（ms）。
         *
         * 回合中途每落一行都会发信号（流式分片级别），逐条刷新会把 `list_sessions` 的目录扫描
         * 打成密集 IO。300ms 足够让侧边栏的"最近更新"跟手，又把扫描频率压到人眼看不出的延迟。
         */
        const val STORE_CHANGE_DEBOUNCE_MS = 300L
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
