package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.events.Event
import com.google.adk.kt.ids.Uuid
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.tools.BaseTool
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
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
        sessionService
            .listEvents(sessionKey(conversationId))
            .events
            .mapNotNull(Event::toTranscriptMessage)

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
 * settled event — and events with no text at all, which is how plain tool calls and
 * their results are left out (the transcript does not show those after a reload).
 *
 * Two shapes are reconstructed rather than read straight off: an answer to a paused
 * turn travels as a function response, so its `result` is the user's text; and a failed
 * turn is shown as its partial text plus the error, which is how the live turn renders
 * it too.
 */
private fun Event.toTranscriptMessage(): TranscriptMessage? {
    if (partial) return null

    val text = content?.parts?.mapNotNull { it.text }?.joinToString("").orEmpty()
    val answer = content?.parts?.firstNotNullOfOrNull { part ->
        part.functionResponse?.response?.get(BaseTool.RESULT_KEY)?.toString()
    }
    val failure = errorMessage
    val body = when {
        failure == null -> text.ifEmpty { answer.orEmpty() }
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
