package com.lhzkml.jasmine.feature.main.impl.chat

import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.core.agent.AgentChat
import com.lhzkml.jasmine.core.agent.ChatEvent
import com.lhzkml.jasmine.core.agent.ChatTurn
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.ModelConfig
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import com.lhzkml.jasmine.core.data.repository.ChatHistoryRepository
import com.lhzkml.jasmine.core.data.repository.ProviderRepository
import com.lhzkml.jasmine.core.data.repository.UserPreferencesRepository
import com.lhzkml.jasmine.core.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One rendered message. [isStreaming] marks the assistant message currently
 * being written; [isError] marks a turn that failed (its text carries the
 * reason, possibly appended after partial output).
 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
)

/**
 * Single immutable UI state for the chat surface (UDF).
 *
 * [providers], [conversations] and the active selection are mirrored from the
 * repositories; [activeProvider] / [activeModel] / [isReady] are derived so the
 * screen never has to re-do the lookup.
 */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val providers: List<ProviderConfig> = ProviderConfig.DEFAULTS,
    val conversations: List<Conversation> = emptyList(),
    val activeProviderId: String = "",
    val activeModelId: String = "",
    /** Persisted conversation behind [messages]; null until the first send. */
    val activeConversationId: String? = null,
    val isModelPickerOpen: Boolean = false,
    val isHistoryOpen: Boolean = false,
) {
    val activeProvider: ProviderConfig?
        get() = providers.firstOrNull { it.id == activeProviderId }

    val activeModel: ModelConfig?
        get() = activeProvider?.models?.firstOrNull { it.id == activeModelId }

    /** A turn needs an endpoint *with credentials* plus a concrete model. */
    val isReady: Boolean
        get() = activeProvider?.apiKey?.isNotBlank() == true && activeModel != null
}

/**
 * Actions sent from the UI to [ChatViewModel].
 */
sealed interface ChatAction {
    data class InputChanged(val value: String) : ChatAction
    data object SendClicked : ChatAction
    data object NewConversationClicked : ChatAction
    data object ModelPickerOpened : ChatAction
    data object ModelPickerDismissed : ChatAction
    data class ModelSelected(val providerId: String, val modelId: String) : ChatAction
    data object HistoryOpened : ChatAction
    data object HistoryDismissed : ChatAction
    data class ConversationSelected(val id: String) : ChatAction
    data class ConversationDeleted(val id: String) : ChatAction

    /**
     * Internal actions: results of asynchronous work posted back onto the action
     * channel so that all state mutations stay synchronous inside [handleAction].
     */
    sealed interface Internal : ChatAction {
        data class ProvidersReceived(val providers: List<ProviderConfig>) : Internal
        data class ConversationsReceived(val conversations: List<Conversation>) : Internal
        data class ActiveModelReceived(val providerId: String, val modelId: String) : Internal
        data class TranscriptRestored(
            val conversationId: String,
            val messages: List<TranscriptMessage>,
        ) : Internal
        data class ReplyChunk(val text: String) : Internal
        data class TurnFailed(val detail: String) : Internal
        data object TurnCompleted : Internal
    }
}

/**
 * ViewModel backing the chat surface (MVVM + unidirectional data flow).
 *
 * It owns the conversation: the active provider/model selection is mirrored from
 * (and persisted to) preferences, the transcript is persisted through
 * [ChatHistoryRepository], and the live ADK session lives in the injected
 * [AgentChat]. The two are kept deliberately separate — the repository is the
 * durable record, the ADK session is the model's working context, rebuilt (with
 * the transcript replayed into it) whenever the conversation or model changes.
 *
 * This ViewModel is scoped to the `Main` navigation entry, so leaving the main
 * screen ends the session; the transcript survives in Room.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val agentChat: AgentChat,
) : BaseViewModel<ChatState, Nothing, ChatAction>(initialState = ChatState()) {

    /** ADK session identity: the conversation + model it was built for. */
    private var sessionKey: String? = null

    /** Id of the assistant message currently being streamed, for chunk appends. */
    private var streamingMessageId: String? = null

    private var turnJob: Job? = null

    init {
        providerRepository
            .providersStateFlow
            .map { ChatAction.Internal.ProvidersReceived(it) }
            .onEach(::sendAction)
            .launchIn(viewModelScope)

        userPreferencesRepository
            .preferencesStateFlow
            .map { ChatAction.Internal.ActiveModelReceived(it.activeProviderId, it.activeModelId) }
            .onEach(::sendAction)
            .launchIn(viewModelScope)

        chatHistoryRepository
            .conversationsStateFlow
            .map { ChatAction.Internal.ConversationsReceived(it) }
            .onEach(::sendAction)
            .launchIn(viewModelScope)

        viewModelScope.launch { restoreLatestConversation() }
    }

    override fun handleAction(action: ChatAction) {
        when (action) {
            is ChatAction.InputChanged -> updateState { copy(input = action.value) }
            ChatAction.SendClicked -> handleSendClicked()
            ChatAction.NewConversationClicked -> handleNewConversation()
            ChatAction.ModelPickerOpened -> updateState { copy(isModelPickerOpen = true) }
            ChatAction.ModelPickerDismissed -> updateState { copy(isModelPickerOpen = false) }
            is ChatAction.ModelSelected -> handleModelSelected(action)
            ChatAction.HistoryOpened -> updateState { copy(isHistoryOpen = true) }
            ChatAction.HistoryDismissed -> updateState { copy(isHistoryOpen = false) }
            is ChatAction.ConversationSelected -> handleConversationSelected(action)
            is ChatAction.ConversationDeleted -> handleConversationDeleted(action)

            is ChatAction.Internal.ProvidersReceived ->
                updateState { copy(providers = action.providers) }
            is ChatAction.Internal.ConversationsReceived ->
                updateState { copy(conversations = action.conversations) }
            is ChatAction.Internal.ActiveModelReceived -> updateState {
                copy(activeProviderId = action.providerId, activeModelId = action.modelId)
            }
            is ChatAction.Internal.TranscriptRestored -> handleTranscriptRestored(action)
            is ChatAction.Internal.ReplyChunk -> appendReplyChunk(action.text)
            is ChatAction.Internal.TurnFailed -> failTurn(action.detail)
            ChatAction.Internal.TurnCompleted -> finishTurn()
        }
    }

    override fun onCleared() {
        resetSession()
        super.onCleared()
    }

    // region Restore

    /**
     * Brings back the most recently updated conversation, transcript and all.
     * The model selection is left to preferences: they are the single source for
     * "which model am I using", while the conversation only records what produced
     * it (shown in the history list).
     */
    private suspend fun restoreLatestConversation() {
        val conversation = runCatching { chatHistoryRepository.latestConversation() }
            .getOrNull() ?: return
        val messages = runCatching { chatHistoryRepository.messagesOf(conversation.id) }
            .getOrDefault(emptyList())
        sendAction(ChatAction.Internal.TranscriptRestored(conversation.id, messages))
    }

    private fun handleTranscriptRestored(action: ChatAction.Internal.TranscriptRestored) {
        // The user may have started a new conversation while the load was in flight.
        if (state.activeConversationId != null || state.messages.isNotEmpty()) return
        updateState {
            copy(
                activeConversationId = action.conversationId,
                messages = action.messages.map { it.toChatMessage() },
            )
        }
    }

    // endregion

    // region Action handlers

    private fun handleSendClicked() {
        val text = state.input.trim()
        if (text.isEmpty() || state.isSending) return
        val provider = state.activeProvider ?: return
        val model = state.activeModel ?: return
        if (provider.apiKey.isBlank()) return

        // The context to replay if this send has to (re)build the ADK session:
        // the transcript as it stands *before* this turn, minus failed replies.
        val history = state.messages
            .filterNot { it.isError }
            .filter { it.text.isNotBlank() }
            .map { ChatTurn(role = it.role, text = it.text) }

        val assistantId = UUID.randomUUID().toString()
        streamingMessageId = assistantId
        updateState {
            copy(
                input = "",
                isSending = true,
                messages = messages +
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = ChatRole.USER,
                        text = text,
                    ) +
                    ChatMessage(
                        id = assistantId,
                        role = ChatRole.ASSISTANT,
                        text = "",
                        isStreaming = true,
                    ),
            )
        }

        turnJob = viewModelScope.launch { runTurn(provider, model, text, history) }
    }

    private fun handleNewConversation() {
        resetSession()
        updateState {
            copy(messages = emptyList(), isSending = false, activeConversationId = null)
        }
    }

    private fun handleModelSelected(action: ChatAction.ModelSelected) {
        val isSameSelection =
            state.activeProviderId == action.providerId && state.activeModelId == action.modelId
        if (isSameSelection) {
            updateState { copy(isModelPickerOpen = false) }
            return
        }
        // An ADK session is bound to one model, so a switch rebuilds it. The
        // transcript stays: it is replayed into the new session.
        resetSession()
        updateState {
            copy(
                activeProviderId = action.providerId,
                activeModelId = action.modelId,
                isModelPickerOpen = false,
            )
        }
        viewModelScope.launch {
            userPreferencesRepository.updateActiveModel(action.providerId, action.modelId)
        }
    }

    private fun handleConversationSelected(action: ChatAction.ConversationSelected) {
        if (action.id == state.activeConversationId) {
            updateState { copy(isHistoryOpen = false) }
            return
        }
        resetSession()
        updateState {
            copy(
                isHistoryOpen = false,
                messages = emptyList(),
                isSending = false,
                activeConversationId = action.id,
            )
        }
        viewModelScope.launch {
            val messages = runCatching { chatHistoryRepository.messagesOf(action.id) }
                .getOrDefault(emptyList())
            // Bail out if the selection moved on while the query ran.
            if (state.activeConversationId != action.id) return@launch
            updateState { copy(messages = messages.map { it.toChatMessage() }) }
        }
    }

    private fun handleConversationDeleted(action: ChatAction.ConversationDeleted) {
        viewModelScope.launch {
            runCatching { chatHistoryRepository.deleteConversation(action.id) }
        }
        if (state.activeConversationId == action.id) {
            resetSession()
            updateState {
                copy(messages = emptyList(), isSending = false, activeConversationId = null)
            }
        }
    }

    // endregion

    // region Turn handling

    private suspend fun runTurn(
        provider: ProviderConfig,
        model: ModelConfig,
        text: String,
        history: List<ChatTurn>,
    ) {
        try {
            val id = ensureConversation(provider, model, text)
            if (id != null) {
                // Best effort: losing a transcript write must not break the chat.
                runCatching { chatHistoryRepository.appendMessage(id, ChatRole.USER, text) }
            }

            val key = "${id.orEmpty()}|${provider.id}|${model.id}"
            if (sessionKey != key) {
                agentChat.startConversation(
                    provider = provider,
                    modelId = model.modelId,
                    instruction = CHAT_INSTRUCTION,
                    history = history,
                )
                sessionKey = key
            }

            agentChat.send(text).collect { event ->
                sendAction(
                    when (event) {
                        is ChatEvent.Text -> ChatAction.Internal.ReplyChunk(event.text)
                        is ChatEvent.Failed -> ChatAction.Internal.TurnFailed(event.detail)
                        ChatEvent.Completed -> ChatAction.Internal.TurnCompleted
                    }
                )
            }
            // The runner is not guaranteed to report turn completion (ADK does not
            // reliably mark the final event), so never rely on it to unblock input.
            sendAction(ChatAction.Internal.TurnCompleted)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            sendAction(
                ChatAction.Internal.TurnFailed(error.message ?: error::class.simpleName.orEmpty())
            )
        }
    }

    /** Creates the conversation row on the first send; [title] comes from that message. */
    private suspend fun ensureConversation(
        provider: ProviderConfig,
        model: ModelConfig,
        title: String,
    ): String? {
        state.activeConversationId?.let { return it }
        val created = runCatching {
            chatHistoryRepository.createConversation(
                providerId = provider.id,
                modelId = model.id,
                title = title.take(TITLE_MAX_LENGTH),
            )
        }.getOrNull() ?: return null
        updateState { copy(activeConversationId = created.id) }
        return created.id
    }

    private fun appendReplyChunk(text: String) {
        val targetId = streamingMessageId ?: return
        updateState {
            copy(
                messages = messages.map { message ->
                    if (message.id == targetId) message.copy(text = message.text + text)
                    else message
                }
            )
        }
    }

    private fun failTurn(detail: String) {
        val targetId = streamingMessageId
        streamingMessageId = null
        val finalText = state.messages.firstOrNull { it.id == targetId }?.let { message ->
            if (message.text.isEmpty()) detail else "${message.text}\n\n$detail"
        }
        updateState {
            copy(
                isSending = false,
                messages = messages.map { message ->
                    if (message.id != targetId) {
                        message
                    } else {
                        message.copy(text = finalText.orEmpty(), isStreaming = false, isError = true)
                    }
                },
            )
        }
        persistAssistantMessage(finalText.orEmpty(), isError = true)
    }

    private fun finishTurn() {
        val targetId = streamingMessageId
        streamingMessageId = null
        val finalText = state.messages.firstOrNull { it.id == targetId }?.text
        updateState {
            copy(
                isSending = false,
                messages = messages.map { message ->
                    if (message.id == targetId) message.copy(isStreaming = false) else message
                },
            )
        }
        if (finalText != null) persistAssistantMessage(finalText, isError = false)
    }

    /**
     * The reply is persisted once the turn ends rather than per chunk: the
     * in-memory message is the live view, the row is the durable record.
     */
    private fun persistAssistantMessage(text: String, isError: Boolean) {
        val id = state.activeConversationId ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { chatHistoryRepository.appendMessage(id, ChatRole.ASSISTANT, text, isError) }
        }
    }

    /** Cancels any in-flight turn and drops the ADK session (the transcript stays). */
    private fun resetSession() {
        turnJob?.cancel()
        turnJob = null
        agentChat.endConversation()
        sessionKey = null
        streamingMessageId = null
    }

    // endregion

    /** Single mutation point of [mutableStateFlow] (mirrors the other ViewModels). */
    private inline fun updateState(block: ChatState.() -> ChatState) {
        mutableStateFlow.update(block)
    }

    private companion object {
        const val CHAT_INSTRUCTION =
            "You are Jasmine, a concise and helpful assistant. Answer in the user's language."

        /** Conversation titles are the first user message, clipped for the list. */
        const val TITLE_MAX_LENGTH = 60
    }
}

private fun TranscriptMessage.toChatMessage(): ChatMessage = ChatMessage(
    id = UUID.randomUUID().toString(),
    role = role,
    text = text,
    isError = isError,
)
