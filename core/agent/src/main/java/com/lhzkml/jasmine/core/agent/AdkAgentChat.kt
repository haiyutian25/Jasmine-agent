package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.events.Event
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.runners.Runner
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * ADK-backed [AgentChat]: one [LlmAgent] whose model comes from [modelFactory],
 * driven by [InMemoryRunner] over an [InMemorySessionService].
 *
 * The model is injected as a factory rather than built here, which keeps the
 * class free of transport wiring **and** makes the session/replay behaviour
 * testable against a recording [Model] without a network call.
 *
 * Nothing is persisted: the ADK session is created by [startConversation] and
 * thrown away by [endConversation]. The transcript's own persistence is a data
 * layer concern — this facade only replays what it is handed.
 *
 * Events are filtered down to assistant text: the runner also emits the user's
 * own message event (already rendered locally) and bookkeeping events, so
 * anything authored by [USER_AUTHOR] is skipped. A non-null `errorMessage` is
 * surfaced as [ChatEvent.Failed]; `errorCode` alone is *not* treated as failure
 * because ADK also uses it to report a non-`STOP` finish reason such as
 * `MAX_TOKENS`.
 */
class AdkAgentChat(
    private val modelFactory: (ProviderConfig, String) -> Model,
) : AgentChat {

    private val sessionService = InMemorySessionService()

    private var runner: Runner? = null
    private var sessionId: String? = null

    override suspend fun startConversation(
        provider: ProviderConfig,
        modelId: String,
        instruction: String,
        history: List<ChatTurn>,
    ) {
        endConversation()

        val agent = LlmAgent(
            name = AGENT_NAME,
            model = modelFactory(provider, modelId),
            description = AGENT_DESCRIPTION,
            instruction = Instruction(instruction),
        )
        val resolvedSessionId = UUID.randomUUID().toString()
        val session = sessionService.createSession(
            key = SessionKey(appName = APP_NAME, userId = USER_ID, id = resolvedSessionId),
            state = null,
        )
        // Replay before the runner exists: the request for the next turn is built
        // from the session's events, so this is what gives a restored
        // conversation its context.
        history.forEach { turn ->
            sessionService.appendEvent(session, turn.toEvent())
        }

        runner = InMemoryRunner(
            agent = agent,
            appName = APP_NAME,
            sessionService = sessionService,
        )
        sessionId = resolvedSessionId
    }

    override fun send(text: String): Flow<ChatEvent> = flow {
        val activeRunner = checkNotNull(runner) { "No conversation: call startConversation first" }
        val activeSessionId = checkNotNull(sessionId) { "No conversation: call startConversation first" }

        activeRunner
            .runAsync(
                userId = USER_ID,
                sessionId = activeSessionId,
                newMessage = Content(role = Role.USER, parts = listOf(Part(text = text))),
            )
            .collect { event ->
                val failure = event.errorMessage
                if (failure != null) {
                    emit(ChatEvent.Failed(failure))
                    return@collect
                }
                if (event.author != USER_AUTHOR) {
                    val chunk =
                        event.content
                            ?.parts
                            .orEmpty()
                            .mapNotNull { it.text }
                            .joinToString("")
                    if (chunk.isNotEmpty()) emit(ChatEvent.Text(chunk))
                }
                if (event.turnComplete) emit(ChatEvent.Completed)
            }
    }

    override fun endConversation() {
        runner?.close()
        runner = null
        sessionId = null
    }

    /**
     * A replayed turn is an ordinary session event: the runner only cares about
     * the content's role, while the author keeps it attributed correctly.
     */
    private fun ChatTurn.toEvent(): Event = Event(
        author = if (role == ChatRole.USER) USER_AUTHOR else AGENT_NAME,
        content = Content(
            role = if (role == ChatRole.USER) Role.USER else Role.MODEL,
            parts = listOf(Part(text = text)),
        ),
    )

    private companion object {
        const val APP_NAME = "jasmine"
        const val AGENT_NAME = "jasmine_agent"
        const val AGENT_DESCRIPTION = "Jasmine assistant"
        const val USER_ID = "local_user"

        /** Author ADK stamps on the caller's own message events. */
        const val USER_AUTHOR = "user"
    }
}
