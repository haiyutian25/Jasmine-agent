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
                .mapNotNull(Event::toTranscriptMessage)
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
 * One turn of a session as the transcript shows it, or null for events the transcript
 * does not carry.
 *
 * Skipped: streaming chunks (`partial`) — the aggregator repeats them in full on the
 * settled event — and events with no text and no function response, which is how an
 * echo of the user's own message is left out.
 *
 * Two shapes are reconstructed rather than read straight off: a tool call, and a tool's
 * response, become tool entries so a **reloaded** conversation looks the same as the
 * live turn did (the live side builds those in `ChatViewModel.appendToolEntry`); and a
 * failed turn is shown as its partial text plus the error, which is how the live turn
 * renders it too. Note that a user-authored function response — how a tool's question
 * comes back — is a tool response, not a message of the user's own.
 */
private fun Event.toTranscriptMessage(): TranscriptMessage? {
    if (partial) return null

    val parts = content?.parts.orEmpty()

    // 工具调用：正文为空。以前这里因为「正文空」被整条丢掉，重新加载后界面上就没有
    // 「调用 xxx」了 —— 与实时那一轮不一致。现在原样还原成工具条目。
    val call = parts.firstNotNullOfOrNull { it.functionCall }
    if (call != null) {
        return TranscriptMessage(
            role = ChatRole.ASSISTANT,
            text = "",
            tool = TranscriptToolActivity(
                name = call.name,
                detail = call.args.abbreviated(),
            ),
        )
    }

    val text = parts.mapNotNull { it.text }.joinToString("")
    val response = parts.firstNotNullOfOrNull { it.functionResponse }
    val failure = errorMessage

    // 工具返回：先产出「只有返回」的条目，再由 [mergeToolResults] 并进它上面那张调用卡片
    // —— 一张卡片放「问了什么 + 回了什么」。这里包含 author 为 user 的那种：用户对提问的
    // 回答，ADK 记成 author=user 的 functionResponse；实时那边同样把它当 ToolResult 发出去
    // （见 AdkAgentChat.run 的 user 分支），两边形状一致。以前它被当成普通用户消息，重启后
    // 会凭空多出一条「像是用户主动发的」气泡。
    if (response != null) {
        return TranscriptMessage(
            role = ChatRole.ASSISTANT,
            text = "",
            tool = TranscriptToolActivity(
                name = response.name,
                detail = "",
                result = response.response.abbreviated(),
            ),
        )
    }

    val body = when {
        failure == null -> text
        text.isEmpty() -> failure
        else -> "$text\n\n$failure"
    }
    if (body.isEmpty()) return null

    return TranscriptMessage(
        role = if (author == USER_AUTHOR) ChatRole.USER else ChatRole.ASSISTANT,
        text = body,
        isError = failure != null,
    )
}

/**
 * 把「工具返回」并进它上面那张「工具调用」卡片 —— 一次调用的问与答属于同一张卡片。
 *
 * ADK 把调用和返回记成两条事件，而界面（以及实时那一轮，见 `ChatViewModel.appendToolResult`）
 * 是合在一张卡片里的。只有紧跟其后、同名、且尚未有返回的调用才吸收它；找不到配对的返回
 * 单独成条，界面上画成「xxx 返回」。
 */
private fun mergeToolResults(messages: List<TranscriptMessage>): List<TranscriptMessage> {
    val merged = mutableListOf<TranscriptMessage>()
    for (message in messages) {
        val tool = message.tool
        val open = merged.lastOrNull()?.tool
        val absorbed = tool != null && tool.isResultOnly &&
            open != null && open.name == tool.name && open.result == null
        if (absorbed) {
            merged[merged.lastIndex] = merged.last().copy(
                tool = open?.copy(result = tool?.result)
            )
        } else {
            merged += message
        }
    }
    return merged
}
