package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.events.Event
import com.google.adk.kt.ids.Uuid
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import com.lhzkml.jasmine.core.data.model.TranscriptToolActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val APP_NAME = "jasmine"
private const val USER_ID = "local_user"

/** Author ADK stamps on the caller's own message events. */
private const val USER_AUTHOR = "user"

/**
 * Keys the display metadata is kept under in a session's `state`. ADK has no field for
 * a title, provider or model, and this is the only per-session store there is.
 *
 * Values are strings on purpose: `state` round-trips through JSON, and a timestamp
 * written as a number can come back as a different numeric type.
 */
private const val STATE_TITLE = "jasmine.title"
private const val STATE_PROVIDER_ID = "jasmine.providerId"
private const val STATE_MODEL_ID = "jasmine.modelId"
private const val STATE_CREATED_AT = "jasmine.createdAt"

private fun sessionKey(id: String) = SessionKey(appName = APP_NAME, userId = USER_ID, id = id)

/**
 * The conversation history the UI reads: the same sessions the agent runs on.
 *
 * There is no second store. A conversation *is* an ADK session — its id is the
 * session id, its turns are the session's events, and the display metadata ADK has no
 * field for lives in the session's `state`. Nothing here keeps its own copy.
 */
interface ConversationStore {
    /**
     * Snapshot of every conversation, most recently updated first.
     *
     * ADK's `SessionService` is not observable, so this is refreshed by [refresh] —
     * the caller does that after it changes anything, which is enough in one process.
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

    /** Re-reads the conversation list from the session store. */
    suspend fun refresh()
}

/**
 * [ConversationStore] over ADK's [SessionService].
 *
 * Reads go through ADK: `listSessions` for the list — it builds each session with an
 * empty event list, so listing stays cheap — and `listEvents` for one conversation's
 * turns. Writes are `createSession` / `deleteSession`.
 */
class AdkConversationStore(
    private val sessionService: SessionService,
) : ConversationStore {

    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())

    override val conversationsStateFlow: StateFlow<List<Conversation>> =
        conversations.asStateFlow()

    override suspend fun refresh() {
        conversations.value = sessionService
            .listSessions(appName = APP_NAME, userId = USER_ID)
            .sessions
            .map(Session::toConversation)
            .sortedByDescending(Conversation::updatedAt)
    }

    override suspend fun latestConversation(): Conversation? =
        sessionService
            .listSessions(appName = APP_NAME, userId = USER_ID)
            .sessions
            .map(Session::toConversation)
            .maxByOrNull(Conversation::updatedAt)

    override suspend fun messagesOf(conversationId: String): List<TranscriptMessage> =
        mergeToolResults(
            sessionService
                .listEvents(sessionKey(conversationId))
                .events
                .flatMap(Event::toTranscriptMessages)
        )

    override suspend fun createConversation(
        providerId: String,
        modelId: String,
        title: String,
    ): Conversation {
        val id = Uuid.random()
        val createdAt = System.currentTimeMillis()
        sessionService.createSession(
            key = sessionKey(id),
            state = mapOf(
                STATE_TITLE to title,
                STATE_PROVIDER_ID to providerId,
                STATE_MODEL_ID to modelId,
                STATE_CREATED_AT to createdAt.toString(),
            ),
        )
        refresh()
        return Conversation(
            id = id,
            title = title,
            providerId = providerId,
            modelId = modelId,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }

    override suspend fun deleteConversation(id: String) {
        sessionService.deleteSession(sessionKey(id))
        refresh()
    }
}

/** Reads the display metadata back out of the session's `state`. */
private fun Session.toConversation(): Conversation {
    val createdAt = state[STATE_CREATED_AT]?.toString()?.toLongOrNull() ?: 0L
    return Conversation(
        id = key.id.orEmpty(),
        title = state[STATE_TITLE]?.toString().orEmpty(),
        providerId = state[STATE_PROVIDER_ID]?.toString().orEmpty(),
        modelId = state[STATE_MODEL_ID]?.toString().orEmpty(),
        createdAt = createdAt,
        updatedAt = lastUpdateTime.toEpochMilliseconds(),
    )
}

/**
 * One turn of a session as the transcript shows it —— **事件里的每个 part 都产出对应条目**。
 *
 * 准则是「照着事件原样搬」，不是「挑一个」：同一段里连续的文字合成一条文本条目；每个
 * `functionCall` / `functionResponse` 各出一条条目（顺序严格按 parts 来）。调用与它的返回
 * **仍然合成同一张卡片** —— 配对由 [mergeToolResults] 做，本函数只负责一条不漏地摆出来。
 *
 * 以前这里只取**第一个** functionCall / functionResponse，且命中调用分支就 return：于是
 * 一条事件里并行调用的三个工具只重建出一张卡、带正文的调用事件还把正文整段丢掉。结果是
 * 「重启后能不能看全」取决于模型这次怎么发 —— 这正是要根除的。
 *
 * 只跳过一种：流式分片（`partial`）—— 聚合器会在 settled 事件里重复完整内容
 * （且实测 ADK 根本不把 partial 事件落库）。
 *
 * 注意 author 为 user 的 functionResponse：那是「用户对工具提问的回答」，属于工具返回，
 * 不是用户自己发的一条消息 —— 实时那边同样按工具返回处理（见 `AdkAgentChat.run` 的 user 分支）。
 */
private fun Event.toTranscriptMessages(): List<TranscriptMessage> {
    if (partial) return emptyList()

    val parts = content?.parts.orEmpty()
    val failure = errorMessage
    val role = if (author == USER_AUTHOR) ChatRole.USER else ChatRole.ASSISTANT
    val out = mutableListOf<TranscriptMessage>()

    fun flushText(buffer: StringBuilder, isError: Boolean = false) {
        if (buffer.isEmpty()) return
        val body = buffer.toString()
        buffer.setLength(0)
        out += TranscriptMessage(
            role = role,
            text = body,
            isError = isError,
            timestamp = timestamp,
            modelLabel = modelVersion,
        )
    }

    fun addTool(name: String, detail: String, result: String?) {
        out += TranscriptMessage(
            role = ChatRole.ASSISTANT,
            text = "",
            tool = TranscriptToolActivity(name = name, detail = detail, result = result),
            timestamp = timestamp,
            modelLabel = modelVersion,
        )
    }

    val text = StringBuilder()
    parts.forEach { part ->
        // 有文字先结一段：part 的先后就是实时那一轮的先后（先说话、再调工具）。
        part.text?.let { text.append(it) }
        part.functionCall?.let { call ->
            flushText(text)
            addTool(name = call.name, detail = call.args.abbreviated(), result = null)
        }
        part.functionResponse?.let { response ->
            flushText(text)
            addTool(name = response.name, detail = "", result = response.response.abbreviated())
        }
    }
    // 失败的那一轮：正文后面接上原因（与实时一致）。
    if (failure != null) {
        if (text.isNotEmpty()) text.append("\n\n")
        text.append(failure)
    }
    flushText(text, isError = failure != null)

    return out
}

/**
 * 把「工具返回」并进它对应的那张「工具调用」卡片 —— 一次调用的问与答属于同一张卡片。
 *
 * ADK 把调用和返回记成两条事件，而界面（以及实时那一轮，见 `ChatViewModel.appendToolResult`）
 * 是合在一张卡片里的。两边的配对规则必须完全一致：**按名字往前找最近一张「同名、还没有返回」
 * 的调用卡**。以前只认紧邻上一条，于是「一条事件里并行调了 3 个工具、下一条事件回 3 个结果」
 * 那种情况全都配不上对，界面上会凭空多出一堆独立的「xxx 返回」卡片。
 *
 * 找不到配对的返回单独成条，画成「xxx 返回」。
 */
private fun mergeToolResults(messages: List<TranscriptMessage>): List<TranscriptMessage> {
    val merged = mutableListOf<TranscriptMessage>()
    for (message in messages) {
        if (absorbIntoOpenCall(merged, message)) continue
        merged += message
    }
    return merged
}

/**
 * [message] 若是一条「只有返回」的工具条目，就把它并进 [merged] 里最近那张同名、尚无返回的调用卡。
 *
 * @return true 表示已经并进去了，调用方不要再单独添加这一条。
 */
private fun absorbIntoOpenCall(
    merged: MutableList<TranscriptMessage>,
    message: TranscriptMessage,
): Boolean {
    val tool = message.tool ?: return false
    if (!tool.isResultOnly) return false
    val index = merged.indexOfLast { candidate ->
        val open = candidate.tool
        open != null && !open.isResultOnly && open.name == tool.name && open.result == null
    }
    if (index < 0) return false
    merged[index] = merged[index].copy(tool = merged[index].tool?.copy(result = tool.result))
    return true
}
