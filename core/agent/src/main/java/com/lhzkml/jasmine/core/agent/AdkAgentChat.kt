package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.runners.Runner
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * ADK-backed [AgentChat]: one [LlmAgent] whose model comes from [modelFactory],
 * driven by [InMemoryRunner] over an injected [SessionService].
 *
 * The session service is injected rather than constructed so this class stays
 * free of Android/Room wiring **and** so a test can pass
 * `InMemorySessionService()`. In the app the injected one is ADK's own
 * `RoomSessionService` (see `AgentModule`), which is what makes a conversation
 * survive process death.
 *
 * ## Session identity
 *
 * [startConversation] is handed a [sessionId] that is the *caller's*
 * conversation id, so the ADK session and the persisted transcript share one
 * identity. The model's context is therefore read straight out of the store: a
 * known session is resumed as-is, and an unknown one is created empty. There is
 * no replay path — the transcript is not re-fed to the model.
 *
 * [endConversation] closes the runner but deliberately does **not** delete the
 * session: dropping the in-memory object must not erase durable history.
 *
 * Events are filtered down to assistant text: the runner also emits the user's
 * own message event (already rendered locally) and bookkeeping events, so
 * anything authored by [USER_AUTHOR] is skipped. A non-null `errorMessage` is
 * surfaced as [ChatEvent.Failed]; `errorCode` alone is *not* treated as failure
 * because ADK also uses it to report a non-`STOP` finish reason such as
 * `MAX_TOKENS`.
 */
class AdkAgentChat(
    private val sessionService: SessionService,
    private val modelFactory: (ProviderConfig, String) -> Model,
) : AgentChat {

    private var runner: Runner? = null

    /** The id of the session the runner is attached to. */
    private var attachedSessionId: String? = null

    override suspend fun startConversation(
        sessionId: String,
        provider: ProviderConfig,
        modelId: String,
        instruction: String,
    ) {
        endConversation()

        val agent = LlmAgent(
            name = AGENT_NAME,
            model = modelFactory(provider, modelId),
            description = AGENT_DESCRIPTION,
            instruction = Instruction(instruction),
        )
        val key = SessionKey(appName = APP_NAME, userId = USER_ID, id = sessionId)
        if (sessionService.getSession(key) == null) {
            sessionService.createSession(key)
        }

        runner = InMemoryRunner(
            agent = agent,
            appName = APP_NAME,
            sessionService = sessionService,
        )
        attachedSessionId = sessionId
    }

    override fun send(text: String): Flow<ChatEvent> = flow {
        val activeRunner = checkNotNull(runner) { "No conversation: call startConversation first" }
        val activeSessionId =
            checkNotNull(attachedSessionId) { "No conversation: call startConversation first" }

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
        attachedSessionId = null
    }

    private companion object {
        const val APP_NAME = "jasmine"
        const val AGENT_NAME = "jasmine_agent"
        const val AGENT_DESCRIPTION = "Jasmine assistant"
        const val USER_ID = "local_user"

        /** Author ADK stamps on the caller's own message events. */
        const val USER_AUTHOR = "user"
    }
}