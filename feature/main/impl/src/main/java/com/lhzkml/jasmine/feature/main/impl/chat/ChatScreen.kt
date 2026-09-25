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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.lhzkml.jasmine.core.markdown.ui.MarkdownBlockList
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

/** 输入区与下方工具行之间的间距。 */
private val ChatComposerToolRowGap = 8.dp
/**
 * 发送/加号/停止按钮的直径。
 *
 * 26dp 是对着 ima 量的：它输入区右侧那两个圆（语音、加号）外接框都是 72px ÷ 2.75 = 26.2dp。
 * 之前这里写 44dp，比 ima 大了 69%，视觉上过于抢眼。
 *
 * 这里刻意**不**额外放大触摸区 —— 视觉圆多大，可点区域就多大。
 * （工程里 `Button` 自带 `sizeIn(minWidth = ButtonMinTouchTarget)`，但外层 `size()`
 * 传下来的约束会把它夹住，所以实际尺寸就是这个值。）
 */
private val ChatSendButtonSize = 26.dp
private val ChatToolIconSize = 14.dp
private val ChatToolRowPaddingVertical = 8.dp

/** Tool arguments/results are a status note, not the point — keep them to two lines. */
private const val ChatToolDetailMaxLines = 2
/** 跟着 [ChatSendButtonSize] 等比缩小（18dp/44dp → 14dp/26dp），图标与圆的占比和 ima 一致。 */
private val ChatSendIconSize = 14.dp
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
        Column(modifier = Modifier.fillMaxSize()) {
            ChatHeader(state = state, currentTheme = currentTheme, onAction = onAction)
            MessageList(
                state = state,
                currentTheme = currentTheme,
                modifier = Modifier.weight(1f),
            )
            // While the agent is blocked on a question the composer steps aside:
            // the turn is paused, so a new message would go nowhere.
            val pendingPrompt = state.pendingPrompt
            if (pendingPrompt == null) {
                Composer(state = state, currentTheme = currentTheme, onAction = onAction)
            } else {
                PromptPanel(
                    prompt = pendingPrompt,
                    currentTheme = currentTheme,
                    onAnswer = { onAction(ChatAction.PromptAnswered(it)) },
                )
            }
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
    //
    // Only when the list is already at the bottom — streaming chunks arrive many times
    // a second, and without this guard scrolling back through history while the model
    // is still writing would be yanked down on every chunk.
    val lastText = state.messages.lastOrNull()?.text
    LaunchedEffect(state.messages.size, lastText) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        // null = first layout, nothing rendered yet: following is the right default.
        val atBottom = lastVisible == null || lastVisible >= state.messages.lastIndex - 1
        if (atBottom) listState.scrollToItem(state.messages.lastIndex)
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
            val tool = message.tool
            if (tool == null) {
                MessageBubble(message = message, currentTheme = currentTheme)
            } else {
                ToolActivityRow(activity = tool, currentTheme = currentTheme)
            }
        }
    }
}

/**
 * One transcript message.
 *
 * Only the user's side is a bubble: it marks what the user said, and its fill is what
 * makes that stand out. The model's reply is plain text on the page instead — a card
 * around every answer is chrome that competes with the words.
 *
 * The user bubble is capped at [ChatBubbleMaxWidthFraction] of the row but must sit
 * flush against the row's end, so the cap is applied to a box that the [Row] itself
 * places at the end. Capping the bubble directly would leave the remainder of that box
 * as dead space to its right.
 */
@Composable
private fun MessageBubble(message: ChatMessage, currentTheme: CssVariables) {
    val isUser = message.role == ChatRole.USER
    val text = message.text.ifEmpty { if (message.isStreaming) "…" else "" }

    if (!isUser) {
        // The model's reply renders as Markdown blocks, built incrementally as chunks
        // arrive. `text` is still the fallback: a message with no blocks (a plain
        // transcript row that failed to parse, or a tool-only turn) shows as text.
        if (message.blocks.isNotEmpty()) {
            MarkdownBlockList(
                blocks = message.blocks,
                currentTheme = currentTheme,
                bodyFontSize = ChatBodyFontSize,
                baseColor = if (message.isError) {
                    currentTheme.mutedForeground
                } else {
                    currentTheme.cardForeground
                },
            )
            return
        }
        Text(
            text = text,
            fontSize = ChatBodyFontSize,
            color = if (message.isError) currentTheme.mutedForeground else currentTheme.cardForeground,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    val shape = RoundedCornerShape(currentTheme.radiusMd)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(ChatBubbleMaxWidthFraction)
                .wrapContentWidth(Alignment.End)
                .clip(shape)
                .background(currentTheme.primary)
                .padding(
                    horizontal = ChatBubblePaddingHorizontal,
                    vertical = ChatBubblePaddingVertical
                )
        ) {
            Text(
                text = text,
                fontSize = ChatBodyFontSize,
                color = currentTheme.primaryForeground,
            )
        }
    }
}

// ── Tool activity ──────────────────────────────────────────────────────

/**
 * One tool call or its result, shown inline in the transcript.
 *
 * Deliberately understated — a full-width note rather than a bubble — because this
 * is execution trace, not something the model said. It exists so the agent's work
 * stays visible: while a tool runs the model is silent, and without it the screen
 * would look frozen.
 */
@Composable
private fun ToolActivityRow(activity: ChatToolActivity, currentTheme: CssVariables) {
    val shape = RoundedCornerShape(currentTheme.radiusSm)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(currentTheme.subtleSurface)
            .border(ChatDividerHeight, currentTheme.border, shape)
            .padding(
                horizontal = ChatBubblePaddingHorizontal,
                vertical = ChatToolRowPaddingVertical
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (activity.isResult) Icons.Outlined.Check else Icons.Outlined.Build,
            contentDescription = null,
            tint = currentTheme.mutedForeground,
            modifier = Modifier.size(ChatToolIconSize)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (activity.isResult) R.string.chat_tool_result else R.string.chat_tool_call,
                    activity.name
                ),
                fontSize = ChatMetaFontSize,
                fontWeight = FontWeight.Medium,
                color = currentTheme.foreground
            )
            Text(
                text = activity.detail,
                fontSize = ChatMetaFontSize,
                color = currentTheme.mutedForeground,
                maxLines = ChatToolDetailMaxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
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

    // One card holds both the text field and a tool row underneath it: model picker
    // on the left, the action button on the right.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ChatContentPaddingHorizontal,
                vertical = ChatComposerVerticalPadding
            )
            .clip(shape)
            .background(currentTheme.subtleSurface)
            .border(ChatDividerHeight, currentTheme.border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = state.input,
            onValueChange = { onAction(ChatAction.InputChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_input"),
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

        Spacer(modifier = Modifier.height(ChatComposerToolRowGap))

        Row(
            modifier = Modifier.fillMaxWidth(),
            // 贴底而不是垂直居中：左侧模型按钮自带 48dp 最小高度，会把这一行撑到 132px，
            // 26dp 的发送按钮若居中，上下就各空出 30px（实测按钮底距内容区底 10.5dp，
            // ima 只有 4.7dp）。改成 Bottom 后只有较矮的发送按钮会下移，模型按钮本来
            // 就占满整行高度，位置不变。
            verticalAlignment = Alignment.Bottom
        ) {
            // Left: the active model doubles as the entry point for switching models.
            Button(
                onClick = { onAction(ChatAction.ModelPickerOpened) },
                rippleEnabled = false,
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

            Spacer(modifier = Modifier.weight(1f))

            // Right: a single button with three faces — empty input / plus, typable /
            // send, reply in flight / stop. Only Send is wired to an action; the plus
            // and stop faces are presentational for now.
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
                        .background(
                            if (canSend || state.isSending) {
                                currentTheme.primary
                            } else {
                                currentTheme.subtleSurface
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.isSending -> Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.chat_stop_cd),
                            tint = currentTheme.primaryForeground,
                            modifier = Modifier.size(ChatSendIconSize)
                        )

                        canSend -> Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.chat_send_cd),
                            tint = currentTheme.primaryForeground,
                            modifier = Modifier.size(ChatSendIconSize)
                        )

                        else -> Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.chat_add_cd),
                            tint = currentTheme.mutedForeground,
                            modifier = Modifier.size(ChatSendIconSize)
                        )
                    }
                }
            }
        }
    }
}

// ── Agent question ─────────────────────────────────────────────────────

/**
 * Replaces the composer while the agent waits on an answer. A question offering
 * options becomes one button per option; anything else becomes a one-line reply
 * field.
 */
@Composable
private fun PromptPanel(
    prompt: ChatUserPrompt,
    currentTheme: CssVariables,
    onAnswer: (String) -> Unit,
) {
    val shape = RoundedCornerShape(currentTheme.radiusMd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ChatContentPaddingHorizontal,
                vertical = ChatComposerVerticalPadding
            )
            .clip(shape)
            .background(currentTheme.card)
            .border(ChatDividerHeight, currentTheme.primary, shape)
            .padding(ChatBubblePaddingHorizontal, ChatBubblePaddingVertical)
    ) {
        Text(
            text = stringResource(R.string.chat_prompt_title),
            fontSize = ChatMetaFontSize,
            fontWeight = FontWeight.Medium,
            color = currentTheme.mutedForeground
        )
        if (prompt.prompt.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = prompt.prompt,
                fontSize = ChatBodyFontSize,
                color = currentTheme.foreground
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (prompt.options.isEmpty()) {
            FreeTextAnswer(currentTheme = currentTheme, onAnswer = onAnswer)
        } else {
            prompt.options.forEach { option ->
                Button(
                    onClick = { onAnswer(option) },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "chat_prompt_option_$option"
                ) {
                    Text(
                        text = option,
                        fontSize = ChatBodyFontSize,
                        color = currentTheme.foreground,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }
    }
}

/** One-line reply field for a question that offers no options. */
@Composable
private fun FreeTextAnswer(currentTheme: CssVariables, onAnswer: (String) -> Unit) {
    var answer by remember { mutableStateOf("") }
    val canSend = answer.isNotBlank()
    val shape = RoundedCornerShape(currentTheme.radiusMd)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = answer,
            onValueChange = { answer = it },
            modifier = Modifier
                .weight(1f)
                .testTag("chat_prompt_input")
                .clip(shape)
                .background(currentTheme.subtleSurface)
                .border(ChatDividerHeight, currentTheme.border, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            textStyle = TextStyle(fontSize = ChatBodyFontSize, color = currentTheme.foreground),
            cursorBrush = SolidColor(currentTheme.primary),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onAnswer(answer.trim()) }),
            decorationBox = { innerTextField ->
                if (answer.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_prompt_hint),
                        fontSize = ChatBodyFontSize,
                        color = currentTheme.mutedForeground
                    )
                }
                innerTextField()
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { if (canSend) onAnswer(answer.trim()) },
            rippleEnabled = false,
            modifier = Modifier.size(ChatSendButtonSize),
            testTag = "chat_prompt_send_btn"
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(if (canSend) currentTheme.primary else currentTheme.subtleSurface),
                contentAlignment = Alignment.Center
            ) {
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
