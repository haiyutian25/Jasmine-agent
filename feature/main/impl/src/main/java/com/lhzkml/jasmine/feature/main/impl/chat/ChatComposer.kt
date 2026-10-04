package com.lhzkml.jasmine.feature.main.impl.chat

import com.lhzkml.jasmine.core.ui.theme.AppShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import com.lhzkml.jasmine.core.widgets.icon.Icon
import com.lhzkml.jasmine.core.widgets.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import com.lhzkml.jasmine.core.widgets.bottomsheet.BottomSheet
import com.lhzkml.jasmine.core.widgets.button.Button as WidgetsButton
import com.lhzkml.jasmine.core.widgets.button.ButtonDefaults
import com.lhzkml.jasmine.core.ui.components.ReasoningEffort
import com.lhzkml.jasmine.core.ui.components.ReasoningEffortOption
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.main.impl.R


private val ChatComposerVerticalPadding = 10.dp

/** 输入区与下方工具行之间的间距。 */
private val ChatComposerToolRowGap = 8.dp

/**
 * 发送按钮的圆角：ZCode 的发送按钮是 `rounded-lg`（Tailwind 的 0.5rem = 8px），照抄绝对值 8dp ——
 * 不是正圆（原来用 `CircleShape`）。
 */
private val ChatSendButtonCorner = 8.dp

/** 「推理强度」控件的尺寸：脑图标、竖条（宽/高/圆角）、与前后元素的间隔。 */
private val ChatEffortIconSize = 14.dp
private val ChatEffortBarWidth = 3.dp
private val ChatEffortBarHeight = 15.dp
private val ChatEffortBarCorner = 2.dp
private val ChatEffortGap = 3.dp

/** 竖条轨道的透明度：ZCode 是 `bg-current/10`，底太淡看不出轨道，这里略深一点。 */
private const val ChatEffortTrackAlpha = 0.15f

/**
 * 发不出去时（输入为空 / 没有可续的回合）整只按钮的不透明度：ZCode 的 Button 基类就是
 * `disabled:opacity-50` —— 底色不换，只是整体变淡，所以按钮的"存在感"始终在。
 */
private const val ChatSendDisabledAlpha = 0.5f

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

/** 选择面板的标题与行距、以及选中行那个对勾的大小。 */
private val ChatEffortSheetTitleGap = 10.dp
private val ChatEffortSheetRowPaddingVertical = 12.dp
private val ChatEffortSheetCheckSize = 16.dp

// ── Composer ───────────────────────────────────────────────────────────

@Composable
internal fun Composer(
    state: ChatState,
    currentTheme: CssVariables,
    onAction: (ChatAction) -> Unit,
) {
    val canSend = state.input.isNotBlank() && !state.isSending
    // 暂停之后：输入框空着才是「继续」；一敲进内容它就变回「发送」——那条内容就是新的一轮。
    val resume = state.canContinue && !canSend
    // 「推理强度」选择面板（复用我们的 BottomSheet）的开合。
    var isEffortSheetOpen by remember { mutableStateOf(false) }
    val shape = AppShapes.medium

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
            // Left: 上下文容量入口 —— 一个环，环身画「最近一次请求 / 配置的窗口」的比例；
            // 点一下弹出面板（复用 BottomSheet）。没有用量或没配窗口时只是空轨道。
            ContextUsageRing(usage = state.contextUsage, currentTheme = currentTheme) {
                onAction(ChatAction.ContextPanelOpened)
            }

            Spacer(modifier = Modifier.width(ChatComposerToolRowGap))

            // 模型入口，点击打开模型选择。有激活模型时显示模型名；没有时以前是空串，
            // 按钮虽然存在却完全看不见，看起来就像「没有选择模型的按钮」。这里给个占位文案，
            // 让它始终可见。
            // 保留原有的“无外观”定制（透明底 / 无边框 / 无阴影 / 零内边距），
            // 只把行为容器换成 core:widgets 的自有 Button。
            WidgetsButton(
                onClick = { onAction(ChatAction.ModelPickerOpened) },
                modifier = Modifier.testTag("chat_active_model_btn"),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                elevation = null,
                contentPadding = PaddingValues(0.dp)
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

            // 推理强度：有激活模型才显示（没有模型就无从谈起档位）。点开面板挑一档，落库走
            // ChatAction.ThoughtLevelSelected —— 改的是**这个会话**的档位（不是模型配置）。
            // 这次会话能选的档位：目录给了这个模型哪几档就列哪几档（没给 = 不限制）。
            val effortOptions = ReasoningEffort.optionsFor(
                declared = state.allowedEfforts,
                current = state.reasoningEffort,
            )
            state.activeModel?.let {
                Spacer(modifier = Modifier.width(ChatComposerToolRowGap))
                ChatReasoningEffortControl(
                    effort = state.reasoningEffort,
                    options = effortOptions,
                    currentTheme = currentTheme,
                    onClick = { isEffortSheetOpen = true }
                )
            }

            // 选择面板：与供应商页那处同一个做法（都复用我们的 BottomSheet），选中的档打勾 + 品牌色。
            // 只列这个模型支持的档（+「未设置」+ 当前值）。
            if (isEffortSheetOpen) {
                ChatReasoningEffortSheet(
                    current = state.reasoningEffort,
                    options = effortOptions,
                    currentTheme = currentTheme,
                    onDismiss = { isEffortSheetOpen = false },
                    onSelect = { value ->
                        isEffortSheetOpen = false
                        onAction(ChatAction.ThoughtLevelSelected(value))
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Right: the send button, three faces — stop while a reply is in flight, continue
            // when the last turn was interrupted, send otherwise. 形状与状态处理照 ZCode 的
            // ConversationComposer（圆角方、发不出去时不换底色只是整体降到 50% 不透明，即它的
            // Button 基类 `disabled:opacity-50`）；配色不走它的 `variant="secondary"` 灰底 ——
            // 这里三副面孔统一"品牌色底 + 反色图标"，停止那副把箭头换成 `<SquareIcon fill-current>`
            // 那样的**实心方块**即可。
            WidgetsButton(
                onClick = {
                    when {
                        // 回复中它是「停止」：让核心收手，见 handleStopClicked。
                        state.isSending -> onAction(ChatAction.StopClicked)
                        // 空输入且上一回合被中断：它是「继续」，不加用户消息接着采样。
                        resume -> onAction(ChatAction.ContinueClicked)
                        // 输入框里有内容：它是「发送」，那条内容就是新的一轮。
                        canSend -> onAction(ChatAction.SendClicked)
                    }
                },
                modifier = Modifier
                    .size(ChatSendButtonSize)
                    .testTag("chat_send_btn"),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                elevation = null,
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 底色一次画出来（shape 直接交给 background）：不再夹 `clip` + `alpha`
                        // 两层，"发不出去就变淡"把透明度合进颜色里。
                        .background(
                            // 三副面孔共用品牌色底（这套主题里就是黑）：发送是白箭头、停止是白方块，
                            // 只有"发不出去"时才整体变淡（ZCode 的 `disabled:opacity-50`：底色不换）。
                            color = if (canSend || state.isSending || resume) {
                                currentTheme.primary
                            } else {
                                currentTheme.primary.copy(alpha = ChatSendDisabledAlpha)
                            },
                            shape = RoundedCornerShape(ChatSendButtonCorner)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isSending) {
                        Icon(
                            imageVector = LucideIcons.SquareFilled,
                            // 已请求中断、回合还没收尾：方块降到 50%（ZCode 那套 `disabled:opacity-50`），
                            // 无障碍文案也换一副 —— 这样 `isInterruptRequested` 有真实的消费者。
                            contentDescription = stringResource(
                                if (state.isInterruptRequested) {
                                    R.string.chat_stopping_cd
                                } else {
                                    R.string.chat_stop_cd
                                }
                            ),
                            // 停止：黑底 + **白**方块（与发送那副面孔同一套反色，只是把箭头换成方块）。
                            tint = currentTheme.primaryForeground.copy(
                                alpha = if (state.isInterruptRequested) ChatSendDisabledAlpha else 1f,
                            ),
                            modifier = Modifier.size(ChatSendIconSize)
                        )
                    } else if (resume) {
                        Icon(
                            imageVector = LucideIcons.Play,
                            contentDescription = stringResource(R.string.chat_continue_cd),
                            tint = currentTheme.primaryForeground,
                            modifier = Modifier.size(ChatSendIconSize)
                        )
                    } else {
                        Icon(
                            imageVector = LucideIcons.ArrowUp,
                            contentDescription = stringResource(R.string.chat_send_cd),
                            // 箭头跟着一起变淡，和上面那层底色是同一个比例。
                            tint = if (canSend) {
                                currentTheme.primaryForeground
                            } else {
                                currentTheme.primaryForeground.copy(alpha = ChatSendDisabledAlpha)
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
internal fun PromptPanel(
    prompt: ChatUserPrompt,
    currentTheme: CssVariables,
    onAnswer: (String) -> Unit,
) {
    val shape = AppShapes.medium
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
                // 保留原有的“无外观”定制（透明底 / 无边框 / 无阴影 / 零内边距），
                // 只把行为容器换成 core:widgets 的自有 Button。
                WidgetsButton(
                    onClick = { onAnswer(option) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_prompt_option_$option"),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    elevation = null,
                    contentPadding = PaddingValues(0.dp)
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
    val shape = AppShapes.medium

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
        WidgetsButton(
            onClick = { if (canSend) onAnswer(answer.trim()) },
            modifier = Modifier
                .size(ChatSendButtonSize)
                .testTag("chat_prompt_send_btn"),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            elevation = null,
            contentPadding = PaddingValues(0.dp)
        ) {
            // 与主输入行那只是同一套外观：圆角方、品牌色底；发不出去时只是整体变淡。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = if (canSend) {
                            currentTheme.primary
                        } else {
                            currentTheme.primary.copy(alpha = ChatSendDisabledAlpha)
                        },
                        shape = RoundedCornerShape(ChatSendButtonCorner)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = LucideIcons.ArrowUp,
                    contentDescription = stringResource(R.string.chat_send_cd),
                    tint = if (canSend) {
                        currentTheme.primaryForeground
                    } else {
                        currentTheme.primaryForeground.copy(alpha = ChatSendDisabledAlpha)
                    },
                    modifier = Modifier.size(ChatSendIconSize)
                )
            }
        }
    }
}

/**
 * 「推理强度」的选择面板：可选档位各一行，当前档用品牌色 + 一个对勾。
 *
 * 复用我们自己的 [BottomSheet]（与同页的模型面板、上下文面板同一个做法）—— 供应商页那个原本是
 * 上游的 `DropdownMenu`，这次也一起换成了它。
 */
@Composable
private fun ChatReasoningEffortSheet(
    current: String,
    options: List<ReasoningEffortOption>,
    currentTheme: CssVariables,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    BottomSheet(
        onDismiss = onDismiss,
        currentTheme = currentTheme,
        modifier = Modifier.testTag("chat_reasoning_effort_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.chat_reasoning_effort),
                fontSize = ChatBodyFontSize,
                fontWeight = FontWeight.SemiBold,
                color = currentTheme.foreground
            )
            Spacer(modifier = Modifier.height(ChatEffortSheetTitleGap))
            options.forEach { option ->
                val selected = option.value == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.small)
                        .clickable { onSelect(option.value) }
                        .padding(vertical = ChatEffortSheetRowPaddingVertical),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(option.labelRes),
                        fontSize = ChatBodyFontSize,
                        color = if (selected) currentTheme.primary else currentTheme.foreground,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(
                            imageVector = LucideIcons.Check,
                            contentDescription = null,
                            tint = currentTheme.primary,
                            modifier = Modifier.size(ChatEffortSheetCheckSize)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「推理强度」控件，照 ZCode 的 `ThoughtLevelCycleControl` 的样子：**脑图标 + 一条竖进度条 + 当前
 * 档位名**。点一下打开选择面板（[ChatReasoningEffortSheet]，复用我们自己的 `BottomSheet`）——
 * 手机上没有悬停，做成下拉面板比"点一下切一档"更好挑。
 *
 * 进度条的比例用它的算法（点亮数 = 当前档在档位表里的位置 + 1，分母 = 档位数）：空串（未设置）与
 * `none`（关闭）都停在空轨道，其余依次铺满。
 */
@Composable
private fun ChatReasoningEffortControl(
    effort: String,
    options: List<ReasoningEffortOption>,
    currentTheme: CssVariables,
    onClick: () -> Unit,
) {
    val label = stringResource(
        options.firstOrNull { it.value == effort }?.labelRes
            ?: ReasoningEffort.options.first().labelRes
    )
    // 竖条只表达"思考强度"：未设置与关闭都停在空轨道，其余按在**这次可选**的那几张档里的位置铺满。
    val thinking = options.filter {
        it.value.isNotEmpty() && it.value != ReasoningEffort.OFF
    }
    val position = thinking.indexOfFirst { it.value == effort }
    val progress = if (position < 0 || thinking.isEmpty()) {
        0f
    } else {
        (position + 1).toFloat() / thinking.size
    }

    WidgetsButton(
        onClick = onClick,
        modifier = Modifier
            .height(ChatSendButtonSize)
            .testTag("chat_reasoning_effort_btn"),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        elevation = null,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = LucideIcons.Brain,
                contentDescription = stringResource(R.string.chat_reasoning_effort) + " " + label,
                tint = currentTheme.mutedForeground,
                modifier = Modifier.size(ChatEffortIconSize)
            )
            Spacer(modifier = Modifier.width(ChatEffortGap))
            // 竖条：一条淡轨道，填充从底部往上长（ZCode 就是居底 + 百分比高度）。
            Box(
                modifier = Modifier
                    .width(ChatEffortBarWidth)
                    .height(ChatEffortBarHeight)
                    .clip(RoundedCornerShape(ChatEffortBarCorner))
                    .background(currentTheme.mutedForeground.copy(alpha = ChatEffortTrackAlpha)),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(progress)
                            .clip(RoundedCornerShape(ChatEffortBarCorner))
                            .background(currentTheme.primary)
                    )
                }
            }
            Spacer(modifier = Modifier.width(ChatEffortGap))
            Text(
                text = label,
                fontSize = ChatMetaFontSize,
                color = currentTheme.mutedForeground,
                maxLines = 1
            )
        }
    }
}
