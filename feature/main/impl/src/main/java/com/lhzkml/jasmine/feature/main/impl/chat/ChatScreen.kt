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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
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
import com.lhzkml.jasmine.core.markdown.ui.MarkdownBlockList
import com.lhzkml.jasmine.core.ui.components.BottomSheet
import com.lhzkml.jasmine.core.ui.components.Button
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.main.impl.R


// ── Chat dimensions ────────────────────────────────────────────────────

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

/** 模型选择行的行高（历史对话行已随侧边栏一起搬走）。 */
private val ChatModelRowPaddingVertical = 11.dp

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

/**
 * 输入区（光标 + 文字）相对提示文字下移的量。
 *
 * 光标是按「行框」画的，而行框顶部比汉字墨迹高出不少 —— 13.5sp 下真机实测：
 *
 *     光标       y[1004..1046]  43px = 15.6dp
 *     汉字墨迹   y[1020..1054]  35px = 12.7dp
 *
 * 也就是光标的顶比汉字顶高 12px、底又比汉字底高 8px，看上去就是「光标浮在文字上方」。
 * 提示文字与真实输入文字本来就在同一落点（距卡片顶 16.0dp vs 16.4dp），所以对齐的目标
 * 是提示文字：把输入区下移 4dp（≈12px）去贴它，而不是把提示文字上提。
 *
 * ⚠️ 这里必须用 `padding` 而不是 `offset`：`offset` 只挪绘制位置、不挪输入框自己的
 * 可视区域，内容超过最大行数滚动到底时，上一行的底部会从顶部露出约 11px（实测）。
 * 用 `padding` 时输入框整体下移、内容与可视区相对关系不变，滚动不会露出残行。
 */
private val ChatInputTextOffset = 4.dp

/**
 * 输入区的行高 —— 必须显式写成字体的自然行距（13.5sp 下实测 55px = 20dp）。
 *
 * 不指定行高时，Compose 画光标用的是「行框」高度，而滚动又按光标高度来算。
 * 这个字体在 13.5sp 下光标只有 43px、行距却有 55px，两者不一致的后果是：
 * 每滚一行只推进 43px，顶部留下 12px 的上一行残影 —— 表现就是**最上面那行被切掉**，
 * `maxLines = 5` 实际只能看全 4 行（真机实测：顶行仅露出 10px）。
 * 把行高定成 55px 后光标高度与行距一致，滚动按整行推进，5 行就能全部看全。
 */
private val ChatInputLineHeight = 20.sp

/**
 * 输入区的最小高度（2.5 行）。
 *
 * 工具行从 48dp 收到 26dp 后，空输入时的卡片从 99.6dp 掉到 77.8dp，看着瘪了。
 * 这里给输入区一个最小高度，把卡片撑回略高于原来的水平：
 *   50dp 输入区 + 4dp 下移 + 8dp 间隔 + 26dp 按钮 + 20dp 内边距 ≈ 108dp（原 99.6dp）。
 */
private val ChatInputMinHeight = 50.dp
private val ChatSendSpinnerSize = 16.dp
private val ChatPickerListMaxHeight = 380.dp

/**
 * Chat surface: the home tab. Renders a transcript plus a composer, driven
 * entirely by [ChatState] — every intent leaves through [onAction].
 *
 * When no usable model is selected the transcript is replaced by a setup
 * prompt, because a chat without an endpoint is a dead end.
 *
 * 顶部原来有一行（当前模型 / 历史对话图标 / 新建对话）。它已整体删除：历史对话与
 * 新建对话都搬进了侧边栏（见 `AppSidebarContent`），当前模型在输入区左下角已有
 * 入口 —— 这一行占掉的 48dp 现在还给聊天区。
 */
@Composable
fun ChatScreen(
    state: ChatState,
    onAction: (ChatAction) -> Unit,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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

        if (state.isModelPickerOpen) {
            ModelSheet(state = state, currentTheme = currentTheme, onAction = onAction)
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────

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
    val resultOnly = activity.isResultOnly
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
            imageVector = if (resultOnly) Icons.Outlined.Check else Icons.Outlined.Build,
            contentDescription = null,
            tint = currentTheme.mutedForeground,
            modifier = Modifier.size(ChatToolIconSize)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (resultOnly) R.string.chat_tool_result else R.string.chat_tool_call,
                    activity.name
                ),
                fontSize = ChatMetaFontSize,
                fontWeight = FontWeight.Medium,
                color = currentTheme.foreground
            )
            if (activity.detail.isNotEmpty()) {
                Text(
                    text = activity.detail,
                    fontSize = ChatMetaFontSize,
                    color = currentTheme.mutedForeground,
                    maxLines = ChatToolDetailMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 返回和调用参数同属这张卡片：文本自带 `result=` 前缀，与调用那边的 `message=`
            // 用同一个 `key=value` 规则，一眼能区分开。
            activity.result?.let { result ->
                Text(
                    text = result,
                    fontSize = ChatMetaFontSize,
                    color = currentTheme.mutedForeground,
                    maxLines = ChatToolDetailMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
                // 最小高度：空输入时也别把卡片压得太扁（见 ChatInputMinHeight）。
                // 文字始终从顶端开始排，所以这个下限不会影响已有内容的位置。
                .heightIn(min = ChatInputMinHeight)
                .testTag("chat_input"),
            textStyle = TextStyle(
                fontSize = ChatBodyFontSize,
                lineHeight = ChatInputLineHeight,
                color = currentTheme.foreground
            ),
            cursorBrush = SolidColor(currentTheme.primary),
            maxLines = 5,
            // 回车键保持「换行」而不是「发送」：输入区本来就允许多行（maxLines = 5），
            // 把 imeAction 设成 Send 会让 IME 把回车键画成发送按钮，换行就按不出来了。
            // 发送只走右下角那个按钮。
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            decorationBox = { innerTextField ->
                // 两者放进同一个 Box：提示文字保持原位，输入区（光标 + 文字）额外下移，
                // 让光标的行框和汉字墨迹对齐。原因见 ChatInputTextOffset。
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (state.input.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_input_hint),
                            fontSize = ChatBodyFontSize,
                            color = currentTheme.mutedForeground
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = ChatInputTextOffset)
                    ) {
                        innerTextField()
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(ChatComposerToolRowGap))

        Row(
            // 行高锁到按钮尺寸，把「工具行比按钮高出来的那截」挤掉。
            //
            // 左侧模型按钮用的是通用 Button，它自带 sizeIn(min = 48dp) 的触摸高度，
            // 会把这一行撑到 132px；发送按钮只有 26dp，于是两者之间白白空出 22dp，
            // 加上 8dp 间隔，输入区最后一行到按钮之间有 30dp 空白（真机实测 29.5dp）。
            // 这里显式给高度，通用 Button 的 sizeIn 会被传入约束夹住（与发送按钮的
            // size() 同一个机制），行高就变成 26dp，空白只剩 8dp 间隔。
            modifier = Modifier
                .fillMaxWidth()
                .height(ChatSendButtonSize),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: 模型入口，点击打开模型选择。有激活模型时显示模型名；没有时以前是空串，
            // 按钮虽然存在却完全看不见，看起来就像「没有选择模型的按钮」。这里给个占位文案，
            // 让它始终可见。
            Button(
                onClick = { onAction(ChatAction.ModelPickerOpened) },
                rippleEnabled = false,
                testTag = "chat_active_model_btn"
            ) {
                val modelLabel = state.activeModel?.modelId?.takeIf { it.isNotEmpty() }
                    ?: stringResource(R.string.chat_choose_model)
                Text(
                    text = modelLabel,
                    fontSize = ChatMetaFontSize,
                    color = currentTheme.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Right: the send button, two faces — stop while a reply is in flight,
            // send otherwise. 空输入时以前显示「加号」，现在统一显示发送箭头（不可发时置灰），
            // 这样按钮的含义始终一致，不需要用户猜那个加号是干什么的。
            Button(
                onClick = {
                    when {
                        // 回复中它是「停止」：取消对事件流的收集，见 handleStopClicked。
                        state.isSending -> onAction(ChatAction.StopClicked)
                        canSend -> onAction(ChatAction.SendClicked)
                    }
                },
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
                    if (state.isSending) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.chat_stop_cd),
                            tint = currentTheme.primaryForeground,
                            modifier = Modifier.size(ChatSendIconSize)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.chat_send_cd),
                            tint = if (canSend) {
                                currentTheme.primaryForeground
                            } else {
                                currentTheme.mutedForeground
                            },
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
            // 与输入区一致：回车保持「换行」，不画成发送按钮。发送走右边那个按钮。
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
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

// ── Model picker ───────────────────────────────────────────────────────

/**
 * 模型选择：列出所有 provider 下已配置的模型，按 provider 分组（模型名 + 所属 provider）。
 *
 * 选中后由 `ChatViewModel.handleModelSelected` 落库并**重建 ADK session** ——
 * 一个 session 绑定一个模型，所以切换模型会重新挂载（转写记录本身保留）。
 */
@Composable
private fun ModelSheet(
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

            val options = state.providers.flatMap { provider ->
                provider.models.map { model -> provider to model }
            }
            if (options.isEmpty()) {
                // 还没在「模型提供商」里配过任何模型 —— 给出可执行的下一步，而不是空列表。
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
                    options.forEach { (provider, model) ->
                        ModelRow(
                            providerId = provider.id,
                            providerName = provider.name,
                            modelId = model.modelId,
                            isCurrent = provider.id == state.activeProviderId &&
                                model.id == state.activeModelId,
                            currentTheme = currentTheme,
                            onSelect = {
                                onAction(ChatAction.ModelSelected(provider.id, model.id))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    providerId: String,
    providerName: String,
    modelId: String,
    isCurrent: Boolean,
    currentTheme: CssVariables,
    onSelect: () -> Unit,
) {
    Button(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        testTag = "chat_model_${providerId}_$modelId"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ChatModelRowPaddingVertical)
        ) {
            Text(
                text = modelId,
                fontSize = ChatBodyFontSize,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isCurrent) currentTheme.primary else currentTheme.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = providerName,
                fontSize = ChatMetaFontSize,
                color = currentTheme.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
