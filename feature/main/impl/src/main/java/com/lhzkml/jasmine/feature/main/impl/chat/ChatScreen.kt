package com.lhzkml.jasmine.feature.main.impl.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.ui.components.BottomSheet
import com.lhzkml.jasmine.core.ui.components.Button
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.main.impl.R

// ── Chat dimensions ────────────────────────────────────────────────────

/** Header row: active model on the left, "new chat" on the right (48dp touch target). */
private val ChatHeaderHeight = 48.dp
private val ChatDividerHeight = 1.dp

private val ChatContentPaddingHorizontal = 16.dp
private val ChatMessageSpacing = 12.dp
private val ChatBubblePaddingHorizontal = 12.dp
private val ChatBubblePaddingVertical = 10.dp

/** Bubbles never span the full width — the asymmetry is what reads as a transcript. */
private const val ChatBubbleMaxWidthFraction = 0.85f

private val ChatBodyFontSize = 13.5.sp
private val ChatMetaFontSize = 11.sp
private val ChatTitleFontSize = 16.sp

private val ChatHeaderIconSize = 18.dp
private val ChatHistoryRowActionIconSize = 16.dp
private val ChatHistoryRowPaddingVertical = 11.dp

private val ChatComposerVerticalPadding = 10.dp
private val ChatSendButtonSize = 44.dp
private val ChatSendIconSize = 18.dp
private val ChatSendSpinnerSize = 16.dp
private val ChatPickerListMaxHeight = 380.dp

/**
 * Chat surface: the home tab. Renders a transcript plus a composer, driven
 * entirely by [ChatState] — every intent leaves through [onAction].
 *
 * When no usable model is selected the transcript is replaced by a setup
 * prompt, because a chat without an endpoint is a dead end. [onOpenSettings]
 * is the escape hatch to the provider list (settings → Model Providers).
 */
@Composable
fun ChatScreen(
    state: ChatState,
    onAction: (ChatAction) -> Unit,
    currentTheme: CssVariables,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.background)
    ) {
        if (state.isReady) {
            Column(modifier = Modifier.fillMaxSize()) {
                ChatHeader(state = state, currentTheme = currentTheme, onAction = onAction)
                MessageList(
                    state = state,
                    currentTheme = currentTheme,
                    modifier = Modifier.weight(1f),
                )
                Composer(state = state, currentTheme = currentTheme, onAction = onAction)
            }
        } else {
            ChatSetup(
                state = state,
                currentTheme = currentTheme,
                onAction = onAction,
                onOpenSettings = onOpenSettings,
            )
        }

        if (state.isModelPickerOpen) {
            ModelPickerSheet(state = state, currentTheme = currentTheme, onAction = onAction)
        }
        if (state.isHistoryOpen) {
            HistorySheet(state = state, currentTheme = currentTheme, onAction = onAction)
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────

@Composable
private fun ChatHeader(
    state: ChatState,
    currentTheme: CssVariables,
    onAction: (ChatAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChatHeaderHeight)
            .padding(horizontal = ChatContentPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The active model doubles as the entry point for switching models.
        Button(
            onClick = { onAction(ChatAction.ModelPickerOpened) },
            rippleEnabled = false,
            modifier = Modifier.weight(1f),
            testTag = "chat_active_model_btn"
        ) {
            Text(
                text = state.activeModel?.modelId.orEmpty(),
                fontSize = ChatMetaFontSize,
                color = currentTheme.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (state.conversations.isNotEmpty()) {
            Button(
                onClick = { onAction(ChatAction.HistoryOpened) },
                rippleEnabled = false,
                testTag = "chat_history_btn"
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = stringResource(R.string.chat_history_cd),
                    tint = currentTheme.mutedForeground,
                    modifier = Modifier.size(ChatHeaderIconSize)
                )
            }
        }
        if (state.messages.isNotEmpty()) {
            Button(
                onClick = { onAction(ChatAction.NewConversationClicked) },
                rippleEnabled = false,
                testTag = "chat_new_conversation_btn"
            ) {
                Text(
                    text = stringResource(R.string.chat_new_conversation),
                    fontSize = ChatMetaFontSize,
                    fontWeight = FontWeight.Medium,
                    color = currentTheme.mutedForeground
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChatDividerHeight)
            .background(currentTheme.border)
    )
}

// ── Transcript ─────────────────────────────────────────────────────────

@Composable
private fun MessageList(
    state: ChatState,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Follow the tail: new messages and streaming chunks both grow the last item.
    val lastText = state.messages.lastOrNull()?.text
    LaunchedEffect(state.messages.size, lastText) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = ChatContentPaddingHorizontal,
            vertical = 14.dp
        ),
        verticalArrangement = Arrangement.spacedBy(ChatMessageSpacing)
    ) {
        items(state.messages, key = { it.id }) { message ->
            MessageBubble(message = message, currentTheme = currentTheme)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, currentTheme: CssVariables) {
    val isUser = message.role == ChatRole.USER
    val shape = RoundedCornerShape(currentTheme.radiusMd)

    Column(
        modifier = Modifier
            .fillMaxWidth(ChatBubbleMaxWidthFraction)
            .wrapContentWidth(if (isUser) Alignment.End else Alignment.Start)
            .clip(shape)
            .background(
                when {
                    isUser -> currentTheme.primary
                    message.isError -> currentTheme.subtleSurface
                    else -> currentTheme.card
                }
            )
            .then(
                if (isUser) Modifier else Modifier.border(ChatDividerHeight, currentTheme.border, shape)
            )
            .padding(
                horizontal = ChatBubblePaddingHorizontal,
                vertical = ChatBubblePaddingVertical
            )
    ) {
        Text(
            text = message.text.ifEmpty { if (message.isStreaming) "…" else "" },
            fontSize = ChatBodyFontSize,
            color = when {
                isUser -> currentTheme.primaryForeground
                message.isError -> currentTheme.mutedForeground
                else -> currentTheme.cardForeground
            }
        )
    }
}

// ── Composer ───────────────────────────────────────────────────────────

@Composable
private fun Composer(
    state: ChatState,
    currentTheme: CssVariables,
    onAction: (ChatAction) -> Unit,
) {
    val canSend = state.input.isNotBlank() && !state.isSending
    val shape = RoundedCornerShape(currentTheme.radiusMd)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ChatContentPaddingHorizontal,
                vertical = ChatComposerVerticalPadding
            ),
        verticalAlignment = Alignment.Bottom
    ) {
        BasicTextField(
            value = state.input,
            onValueChange = { onAction(ChatAction.InputChanged(it)) },
            modifier = Modifier
                .weight(1f)
                .testTag("chat_input")
                .clip(shape)
                .background(currentTheme.subtleSurface)
                .border(ChatDividerHeight, currentTheme.border, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            textStyle = TextStyle(fontSize = ChatBodyFontSize, color = currentTheme.foreground),
            cursorBrush = SolidColor(currentTheme.primary),
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { if (canSend) onAction(ChatAction.SendClicked) }
            ),
            decorationBox = { innerTextField ->
                if (state.input.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_input_hint),
                        fontSize = ChatBodyFontSize,
                        color = currentTheme.mutedForeground
                    )
                }
                innerTextField()
            }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = { if (canSend) onAction(ChatAction.SendClicked) },
            rippleEnabled = false,
            modifier = Modifier.size(ChatSendButtonSize),
            testTag = "chat_send_btn"
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(if (canSend) currentTheme.primary else currentTheme.subtleSurface),
                contentAlignment = Alignment.Center
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ChatSendSpinnerSize),
                        color = currentTheme.primaryForeground,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send_cd),
                        tint = if (canSend) currentTheme.primaryForeground else currentTheme.mutedForeground,
                        modifier = Modifier.size(ChatSendIconSize)
                    )
                }
            }
        }
    }
}

// ── Setup prompt ───────────────────────────────────────────────────────

@Composable
private fun ChatSetup(
    state: ChatState,
    currentTheme: CssVariables,
    onAction: (ChatAction) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val provider = state.activeProvider
    val explanation =
        if (state.activeModel == null || provider == null) {
            stringResource(R.string.chat_setup_hint)
        } else {
            stringResource(R.string.chat_api_key_hint, provider.name)
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.chat_setup_title),
            fontSize = ChatTitleFontSize,
            fontWeight = FontWeight.SemiBold,
            color = currentTheme.foreground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = explanation,
            fontSize = ChatBodyFontSize,
            color = currentTheme.mutedForeground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { onAction(ChatAction.ModelPickerOpened) },
            currentTheme = currentTheme,
            containerColor = currentTheme.primary,
            border = BorderStroke(0.dp, Color.Transparent),
            testTag = "chat_choose_model_btn"
        ) {
            Text(
                text = stringResource(R.string.chat_setup_action),
                fontSize = ChatBodyFontSize,
                fontWeight = FontWeight.SemiBold,
                color = currentTheme.primaryForeground
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onOpenSettings,
            rippleEnabled = false,
            testTag = "chat_manage_providers_btn"
        ) {
            Text(
                text = stringResource(R.string.chat_manage_providers),
                fontSize = ChatMetaFontSize,
                color = currentTheme.mutedForeground
            )
        }
    }
}

// ── History ────────────────────────────────────────────────────────────

/**
 * Persisted conversations, most recently updated first. Selecting one loads its
 * transcript and rebuilds the ADK session with that history replayed into it.
 */
@Composable
private fun HistorySheet(
    state: ChatState,
    currentTheme: CssVariables,
    onAction: (ChatAction) -> Unit,
) {
    BottomSheet(
        onDismiss = { onAction(ChatAction.HistoryDismissed) },
        currentTheme = currentTheme,
        modifier = Modifier.testTag("chat_history_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.chat_history_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = currentTheme.foreground
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (state.conversations.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_history_empty),
                    fontSize = ChatBodyFontSize,
                    color = currentTheme.mutedForeground,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = ChatPickerListMaxHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    state.conversations.forEach { conversation ->
                        HistoryRow(
                            conversation = conversation,
                            isCurrent = conversation.id == state.activeConversationId,
                            currentTheme = currentTheme,
                            onSelect = { onAction(ChatAction.ConversationSelected(conversation.id)) },
                            onDelete = { onAction(ChatAction.ConversationDeleted(conversation.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    conversation: Conversation,
    isCurrent: Boolean,
    currentTheme: CssVariables,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Button(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        testTag = "chat_history_${conversation.id}"
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ChatHistoryRowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    fontSize = ChatBodyFontSize,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isCurrent) currentTheme.primary else currentTheme.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // The model that produced it — not necessarily the current one.
                    text = conversation.modelId,
                    fontSize = ChatMetaFontSize,
                    color = currentTheme.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onDelete,
                rippleEnabled = false,
                testTag = "chat_history_delete_${conversation.id}"
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.chat_history_delete_cd),
                    tint = currentTheme.mutedForeground,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(ChatHistoryRowActionIconSize)
                )
            }
        }
    }
}

// ── Model picker ───────────────────────────────────────────────────────

/**
 * Lists every configured model grouped by provider. Providers with no models
 * are hidden — there is nothing to pick, and the empty state points at the
 * provider screen instead.
 */
@Composable
private fun ModelPickerSheet(
    state: ChatState,
    currentTheme: CssVariables,
    onAction: (ChatAction) -> Unit,
) {
    BottomSheet(
        onDismiss = { onAction(ChatAction.ModelPickerDismissed) },
        currentTheme = currentTheme,
        modifier = Modifier.testTag("chat_model_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.chat_pick_model_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = currentTheme.foreground
            )
            Spacer(modifier = Modifier.height(12.dp))

            val selectable = state.providers.filter { it.models.isNotEmpty() }
            if (selectable.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_pick_model_empty),
                    fontSize = ChatBodyFontSize,
                    color = currentTheme.mutedForeground,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = ChatPickerListMaxHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    selectable.forEach { provider ->
                        Text(
                            text = provider.name,
                            fontSize = ChatMetaFontSize,
                            fontWeight = FontWeight.SemiBold,
                            color = currentTheme.mutedForeground,
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                        )
                        provider.models.forEach { model ->
                            val isActive = provider.id == state.activeProviderId &&
                                model.id == state.activeModelId
                            Button(
                                onClick = { onAction(ChatAction.ModelSelected(provider.id, model.id)) },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "chat_pick_${model.id}"
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = model.modelId,
                                        fontSize = ChatBodyFontSize,
                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isActive) currentTheme.primary else currentTheme.foreground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = currentTheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
