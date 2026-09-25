package com.lhzkml.jasmine.feature.main.impl.chat

import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.core.agent.AgentChat
import com.lhzkml.jasmine.core.agent.ChatEvent
import com.lhzkml.jasmine.core.agent.ConversationStore
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.ModelConfig
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import com.lhzkml.jasmine.core.data.repository.ProviderRepository
import com.lhzkml.jasmine.core.data.repository.UserPreferencesRepository
import com.lhzkml.jasmine.core.markdown.IncrementalMarkdownDocument
import com.lhzkml.jasmine.core.markdown.IncrementalMarkdownParser
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlock
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlockType
import com.lhzkml.jasmine.core.markdown.model.MarkdownInline
import com.lhzkml.jasmine.core.markdown.model.MarkdownInlineType
import com.lhzkml.jasmine.core.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One rendered transcript entry: model output, or a tool the agent called.
 *
 * [isStreaming] marks the assistant message currently being written; [isError]
 * marks a turn that failed (its text carries the reason, possibly appended after
 * partial output). [tool] is set instead of [text] on an execution-trace entry.
 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val tool: ChatToolActivity? = null,
    /**
     * 增量解析出的块列表 —— 渲染层用它，[text] 只用于写回会话记录。
     *
     * 这是 ima 的做法：它同样把模型输出以纯文本回写 transcript，而 UI 走
     * Block 列表。块的 `id` 由 native 侧保证跨增量稳定，所以 Compose 能
     * 复用节点、只重绘真正变化的块。
     */
    val blocks: List<MarkdownBlock> = emptyList(),
)

/**
 * A tool invocation shown inline in the transcript, so the agent's work stays
 * visible while the model itself is silent. [detail] is the arguments for a call
 * and the tool's answer for a result.
 *
 * Not persisted: the transcript stores the reply, not the execution trace.
 */
data class ChatToolActivity(
    val name: String,
    val detail: String,
    val isResult: Boolean,
)

/**
 * A question the agent is waiting on. [options] is empty when free-form text is
 * expected, otherwise the user must pick one of them.
 *
 * While this is set the turn is paused — the model is not running — so the screen
 * offers the answer controls instead of the ordinary composer.
 */
data class ChatUserPrompt(
    val prompt: String,
    val options: List<String>,
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
    /** Set while the agent is blocked on a question; see [ChatUserPrompt]. */
    val pendingPrompt: ChatUserPrompt? = null,
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
    data class ConversationSelected(val id: String) : ChatAction
    data class ConversationDeleted(val id: String) : ChatAction

    /** The user's answer to the pending question; resumes the paused turn. */
    data class PromptAnswered(val answer: String) : ChatAction

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
        data class ToolCalled(val name: String, val arguments: String) : Internal
        data class ToolReturned(val name: String, val result: String) : Internal
        data class PromptRequested(val prompt: String, val options: List<String>) : Internal
        data class TurnFailed(val detail: String) : Internal
        data object TurnCompleted : Internal
    }
}

/**
 * ViewModel backing the chat surface (MVVM + unidirectional data flow).
 *
 * It owns the conversation: the active provider/model selection is mirrored from
 * (and persisted to) preferences, and both the live turn ([AgentChat]) and the
 * history ([ConversationStore]) are ADK sessions — one conversation, one record.
 * The runner appends every user message and model reply to the session as it runs,
 * so this class writes no transcript of its own.
 *
 * This ViewModel is scoped to the `Main` navigation entry, so leaving the main
 * screen releases the runner; both the transcript and the session survive.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val conversationStore: ConversationStore,
    private val agentChat: AgentChat,
) : BaseViewModel<ChatState, Nothing, ChatAction>(initialState = ChatState()) {

    /** ADK session identity: the conversation + model it was built for. */
    private var sessionKey: String? = null

    /** Id of the assistant message currently being streamed, for chunk appends. */
    private var streamingMessageId: String? = null

    /**
     * Incremental Markdown parser for the segment currently streaming.
     *
     * One parser per assistant segment: a tool call closes the segment and the text
     * after its result is a fresh segment, so the parser starts over with it. The
     * native handle is NOT thread-safe — every use happens synchronously inside
     * `handleAction`, which is where the action channel's single consumer runs.
     */
    private var streamParser: IncrementalMarkdownParser? = null

    /**
     * Length of the text the parser was last fed.
     *
     * Mirrors `gt.h0`'s `l` field: the FULL path in ima skips the re-parse when the
     * incoming text has the same length as the one it last parsed, and it *assigns*
     * here rather than accumulating (the DELTA path is the one that accumulates).
     */
    private var streamParsedLength: Int = 0

    /**
     * Assistant segments written during the current turn. A turn can hold several —
     * text before a tool call and text after its result are separate bubbles — and
     * they are joined into the single row the transcript stores.
     */
    private val turnAssistantIds = mutableListOf<String>()

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

        conversationStore
            .conversationsStateFlow
            .map { ChatAction.Internal.ConversationsReceived(it) }
            .onEach(::sendAction)
            .launchIn(viewModelScope)

        // The session store is not observable, so the list has to be read once here.
        viewModelScope.launch { runCatching { conversationStore.refresh() } }
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
            is ChatAction.ConversationSelected -> handleConversationSelected(action)
            is ChatAction.ConversationDeleted -> handleConversationDeleted(action)
            is ChatAction.PromptAnswered -> handlePromptAnswered(action)

            is ChatAction.Internal.ProvidersReceived ->
                updateState { copy(providers = action.providers) }
            is ChatAction.Internal.ConversationsReceived ->
                updateState { copy(conversations = action.conversations) }
            is ChatAction.Internal.ActiveModelReceived -> updateState {
                copy(activeProviderId = action.providerId, activeModelId = action.modelId)
            }
            is ChatAction.Internal.TranscriptRestored -> handleTranscriptRestored(action)
            is ChatAction.Internal.ReplyChunk -> appendReplyChunk(action.text)
            is ChatAction.Internal.ToolCalled -> appendToolCall(action)
            is ChatAction.Internal.ToolReturned -> appendToolResult(action)
            is ChatAction.Internal.PromptRequested -> handlePromptRequested(action)
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
        val conversation = runCatching { conversationStore.latestConversation() }
            .getOrNull() ?: return
        val messages = runCatching { conversationStore.messagesOf(conversation.id) }
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

        // The placeholder bubble is what the UI shows while the model is silent. It
        // is dropped again if the turn opens with a tool call instead of text.
        turnAssistantIds.clear()
        val assistantId = UUID.randomUUID().toString()
        streamingMessageId = assistantId
        turnAssistantIds += assistantId
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

        turnJob = viewModelScope.launch { runTurn(provider, model, text) }
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
        // An ADK session is bound to one model, so a switch re-attaches it. The
        // stored session (and the transcript) stay.
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
        // 选中的就是当前这条：抽屉由调用方（侧边栏）负责关，这里什么都不用做 ——
        // 否则会把 ADK session 重建、转写重读一遍，白费一次。
        if (action.id == state.activeConversationId) return

        resetSession()
        updateState {
            copy(
                messages = emptyList(),
                isSending = false,
                activeConversationId = action.id,
            )
        }
        viewModelScope.launch {
            val messages = runCatching { conversationStore.messagesOf(action.id) }
                .getOrDefault(emptyList())
            // Bail out if the selection moved on while the query ran.
            if (state.activeConversationId != action.id) return@launch
            updateState { copy(messages = messages.map { it.toChatMessage() }) }
        }
    }

    private fun handleConversationDeleted(action: ChatAction.ConversationDeleted) {
        viewModelScope.launch {
            runCatching { conversationStore.deleteConversation(action.id) }
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
    ) {
        try {
            val id = ensureConversation(provider, model, text) ?: throw IllegalStateException(
                "Could not open a conversation to send into."
            )

            val key = "$id|${provider.id}|${model.id}"
            if (sessionKey != key) {
                // The ADK session carries the conversation's own id, so the model's
                // stored context and the transcript share one identity and a
                // resumed conversation is loaded rather than rebuilt.
                agentChat.startConversation(
                    sessionId = id,
                    provider = provider,
                    modelId = model.modelId,
                    instruction = CHAT_INSTRUCTION,
                )
                sessionKey = key
            }

            collectEvents(agentChat.send(text))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            sendAction(
                ChatAction.Internal.TurnFailed(error.message ?: error::class.simpleName.orEmpty())
            )
        }
    }

    /**
     * Maps a turn's events onto actions. Shared by the first send and by the resume
     * after the user answers — both stream the same kinds of event.
     */
    private suspend fun collectEvents(events: Flow<ChatEvent>) {
        events.collect { event ->
            sendAction(
                when (event) {
                    is ChatEvent.Text -> ChatAction.Internal.ReplyChunk(event.text)
                    is ChatEvent.ToolCall ->
                        ChatAction.Internal.ToolCalled(event.name, event.arguments)
                    is ChatEvent.ToolResult ->
                        ChatAction.Internal.ToolReturned(event.name, event.result)
                    is ChatEvent.UserPromptRequested ->
                        ChatAction.Internal.PromptRequested(event.prompt, event.options)
                    is ChatEvent.Failed -> ChatAction.Internal.TurnFailed(event.detail)
                    ChatEvent.Completed -> ChatAction.Internal.TurnCompleted
                }
            )
        }
    }

    /**
     * The agent stopped to ask something. Its event flow ends here, so the usual
     * end-of-turn path releases the composer; [ChatState.pendingPrompt] is what
     * keeps the ordinary input out of the way until the question is answered.
     */
    private fun handlePromptRequested(action: ChatAction.Internal.PromptRequested) {
        // Close the open segment: whatever the model wrote before asking stays put, and
        // the empty placeholder created on send is dropped rather than left as a blank
        // bubble for the whole time the user takes to answer.
        sealAssistantSegment()
        updateState {
            copy(pendingPrompt = ChatUserPrompt(prompt = action.prompt, options = action.options))
        }
    }

    /** Sends the answer back and streams the rest of the paused turn. */
    private fun handlePromptAnswered(action: ChatAction.PromptAnswered) {
        val answer = action.answer.trim()
        if (answer.isEmpty() || state.pendingPrompt == null || state.isSending) return
        updateState { copy(pendingPrompt = null, isSending = true) }
        turnJob = viewModelScope.launch { resumeTurn(answer) }
    }

    private suspend fun resumeTurn(answer: String) {
        try {
            collectEvents(agentChat.respondToPrompt(answer))
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
            conversationStore.createConversation(
                providerId = provider.id,
                modelId = model.id,
                title = title.take(TITLE_MAX_LENGTH),
            )
        }.getOrNull() ?: return null
        updateState { copy(activeConversationId = created.id) }
        return created.id
    }

    /**
     * Appends streamed text, opening a new assistant segment when the previous one
     * was closed by a tool call.
     *
     * ## FULL semantics — matches ima's `gt.h0.g(String, Continuation)`
     *
     * ima drives its renderer two ways, and **all seven call sites use the FULL one**:
     * the renderer is handed the whole accumulated text each time and re-parses it from
     * scratch (`b6/a` mode `gt.c0.b` → `parser.reset(); parser.append(full)`), skipping
     * only when the incoming length equals the length it last parsed
     * (`if (str.length() == 0 || this.l == str.length()) return`).
     *
     * This mirrors that exactly: accumulate, `reset()`, append the whole thing, remember
     * the length. The only difference from ima's call shape is *where* accumulation
     * happens — the transport hands us deltas, so we accumulate here instead of in the
     * caller. The text the engine ends up holding is identical.
     *
     * ## What this does and does not cost
     *
     * Block-level incrementality is unaffected: `Update.index` is still the truncation
     * point, so `applied()` reuses every block before it and Compose only redraws what
     * changed. What FULL costs is the re-parse itself — O(n) per chunk instead of O(tail),
     * which is exactly why ima pairs it with the length guard above.
     *
     * A side benefit of FULL: it is self-healing. If the parser is dropped mid-segment
     * (a tool call closes the segment, `finalizeStreamInto` closes the handle), the next
     * chunk rebuilds the whole document from `message.text` rather than appending to an
     * empty buffer.
     */
    private fun appendReplyChunk(text: String) {
        val targetId = streamingMessageId ?: startAssistantSegment()
        val parser = streamParser ?: IncrementalMarkdownParser().also {
            streamParser = it
            streamParsedLength = 0
        }
        val fullText = (state.messages.firstOrNull { it.id == targetId }?.text ?: "") + text
        // Same guard as ima's g(): nothing new to parse, so leave the state alone.
        if (fullText.isEmpty() || fullText.length == streamParsedLength) return
        // FULL: re-parse from scratch rather than appending the delta.
        parser.reset()
        val update = parser.append(fullText)
        streamParsedLength = fullText.length
        updateState {
            copy(
                messages = messages.map { message ->
                    if (message.id == targetId) {
                        message.copy(
                            text = fullText,
                            blocks = IncrementalMarkdownDocument.applied(update, message.blocks),
                        )
                    } else {
                        message
                    }
                }
            )
        }
    }

    /** Opens an assistant segment and returns its id. */
    private fun startAssistantSegment(): String {
        // A segment owns its parser; the previous one (if any) is done with.
        closeStreamParser()
        val id = UUID.randomUUID().toString()
        streamingMessageId = id
        turnAssistantIds += id
        updateState {
            copy(
                messages = messages + ChatMessage(
                    id = id,
                    role = ChatRole.ASSISTANT,
                    text = "",
                    isStreaming = true,
                )
            )
        }
        return id
    }

    /**
     * Runs the engine's end-of-stream pass and folds the result into the message.
     *
     * This is what closes the block a chunk left open mid-line: a table still missing
     * its delimiter row, a fence without its closing marker, a trailing paragraph.
     * `finalizeStream()` returns final blocks for the tail; `applied()` merges them
     * the same way a chunk update is merged.
     */
    private fun finalizeStreamInto(targetId: String?) {
        val parser = streamParser ?: return
        if (targetId != null) {
            val update = parser.finalizeStream()
            updateState {
                copy(
                    messages = messages.map { message ->
                        if (message.id == targetId) {
                            message.copy(
                                blocks = IncrementalMarkdownDocument.applied(update, message.blocks)
                            )
                        } else {
                            message
                        }
                    }
                )
            }
        }
        closeStreamParser()
    }

    private fun closeStreamParser() {
        streamParser?.close()
        streamParser = null
        // Length tracking belongs to the parser instance, so it goes with it.
        streamParsedLength = 0
    }

    /**
     * A tool call closes the current assistant segment: text written before the call
     * is the model's preamble and the answer after the result is a separate bubble,
     * so the transcript keeps the order things actually happened in.
     */
    private fun appendToolCall(action: ChatAction.Internal.ToolCalled) {
        sealAssistantSegment()
        appendToolEntry(name = action.name, detail = action.arguments, isResult = false)
    }

    private fun appendToolResult(action: ChatAction.Internal.ToolReturned) {
        appendToolEntry(name = action.name, detail = action.result, isResult = true)
    }

    private fun appendToolEntry(name: String, detail: String, isResult: Boolean) {
        updateState {
            copy(
                messages = messages + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    text = "",
                    tool = ChatToolActivity(name = name, detail = detail, isResult = isResult),
                )
            )
        }
    }

    /**
     * Ends the streaming segment. An empty one is removed rather than sealed: it is
     * the placeholder created on send, and a turn that opens with a tool call should
     * not leave a blank bubble above the tool entry.
     */
    private fun sealAssistantSegment() {
        val targetId = streamingMessageId ?: return
        streamingMessageId = null
        val isEmpty = state.messages.firstOrNull { it.id == targetId }?.text.isNullOrEmpty()
        // Close the segment's blocks before dropping the parser (no-op when empty).
        if (!isEmpty) finalizeStreamInto(targetId) else closeStreamParser()
        updateState {
            copy(
                messages = if (isEmpty) {
                    messages.filterNot { it.id == targetId }
                } else {
                    messages.map { if (it.id == targetId) it.copy(isStreaming = false) else it }
                }
            )
        }
        if (isEmpty) turnAssistantIds.remove(targetId)
    }

    private fun failTurn(detail: String) {
        // A turn that already closed its segment on a tool call has nowhere to show
        // the failure, so open one rather than swallowing it.
        val targetId = streamingMessageId ?: startAssistantSegment()
        streamingMessageId = null
        // Close the partial answer's open block first, then append the reason as a
        // block of its own so it renders through the same path as everything else.
        finalizeStreamInto(targetId)
        val errorBlock = MarkdownBlock(
            id = "error:${UUID.randomUUID()}",
            type = MarkdownBlockType.PARAGRAPH,
            isClosed = true,
            content = listOf(MarkdownInline(MarkdownInlineType.TEXT, literal = detail)),
        )
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
                        message.copy(
                            text = finalText.orEmpty(),
                            isStreaming = false,
                            isError = true,
                            blocks = message.blocks + errorBlock,
                        )
                    }
                },
            )
        }
        endTurn()
    }

    private fun finishTurn() {
        val targetId = streamingMessageId
        streamingMessageId = null
        // Close whatever the last chunk left open before the turn ends.
        finalizeStreamInto(targetId)
        updateState {
            copy(
                isSending = false,
                messages = messages.map { message ->
                    if (message.id == targetId) message.copy(isStreaming = false) else message
                },
            )
        }
        endTurn()
    }

    /**
     * Ends the turn's bookkeeping. Nothing is written: the runner already appended this
     * turn's user message and model replies to the session as they happened, so the
     * transcript needs no write of its own.
     *
     * All that is left is to drop the turn's segment ids and re-read the conversation
     * list, whose `updatedAt` the session store moved.
     */
    private fun endTurn() {
        turnAssistantIds.clear()
        if (state.activeConversationId == null) return
        viewModelScope.launch { runCatching { conversationStore.refresh() } }
    }

    /** Cancels any in-flight turn and drops the ADK session (the transcript stays). */
    private fun resetSession() {
        turnJob?.cancel()
        turnJob = null
        agentChat.endConversation()
        sessionKey = null
        streamingMessageId = null
        closeStreamParser()
        turnAssistantIds.clear()
        // A question belongs to the conversation that asked it.
        updateState { copy(pendingPrompt = null) }
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
    // Stored rows are plain text; parse them once so restored history renders as
    // Markdown too. Never a live stream, so a single pass is enough.
    blocks = parseMarkdownBlocks(text),
)

/**
 * One-shot parse of an already-finished message.
 *
 * Two steps, in this order: the append result carries every block (the parser starts
 * at offset 0), then the finalize result carries the tail that only becomes final
 * once the stream ends. Skipping the first step would drop the stable prefix.
 */
private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    if (markdown.isEmpty()) return emptyList()
    val parser = IncrementalMarkdownParser()
    return try {
        val ast = mutableListOf<MarkdownBlock>()
        IncrementalMarkdownParser.apply(parser.append(markdown), ast)
        IncrementalMarkdownParser.apply(parser.finalizeStream(), ast)
        ast
    } catch (error: Throwable) {
        // Native boundary: a failure here must not take down history restore.
        emptyList()
    } finally {
        parser.close()
    }
}
