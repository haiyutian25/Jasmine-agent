package com.lhzkml.jasmine.feature.main.impl.chat

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import android.util.Log
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.lhzkml.jasmine.feature.main.impl.relativeTimeText
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.agent.ChatFailureKind
import com.lhzkml.jasmine.core.agent.ContextUsage
import com.lhzkml.jasmine.core.agent.ContextUsageSource
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.markdown.ui.MarkdownBlockList
import com.lhzkml.jasmine.core.ui.components.BottomSheet
import com.lhzkml.jasmine.core.ui.components.Button
import com.lhzkml.jasmine.core.ui.components.ReasoningEffort
import com.lhzkml.jasmine.core.ui.components.ReasoningEffortOption
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import android.icu.text.CompactDecimalFormat
import com.lhzkml.jasmine.feature.main.impl.R
import java.text.NumberFormat
import java.util.Locale


// ── Chat dimensions ────────────────────────────────────────────────────

private val ChatDividerHeight = 1.dp

private val ChatContentPaddingHorizontal = 16.dp
private val ChatMessageSpacing = 20.dp
/** 一轮之内的条目（正文 ↔ 工具调用）之间的间距：比气泡间距小，免得把一轮拆散。 */
private val ChatIntraTurnSpacing = 2.dp
private val ChatBubblePaddingHorizontal = 12.dp
private val ChatBubblePaddingVertical = 10.dp

/** Bubbles never span the full width — the asymmetry is what reads as a transcript. */
private const val ChatBubbleMaxWidthFraction = 0.85f

private val ChatBodyFontSize = 13.5.sp
private val ChatMetaFontSize = 11.sp

/** 消息时间小标签与正文之间的间距。 */
private val ChatMessageTimeGap = 4.dp

/** 「上一回合被停止」那行小字与它下面那条分隔线之间的距离。 */
private val ChatStopDividerGap = 8.dp

/** 底部两个 meta 标签（时间、模型名）之间的间距。 */
private val ChatMessageMetaGap = 6.dp

/** 时间小标签的内边距 —— 小小的就够，别做成按钮。纵向刻意只给 1dp，别撑高。 */
private val ChatTimeChipPaddingHorizontal = 6.dp
private val ChatTimeChipPaddingVertical = 1.dp

/** 时间小标签的行高（比字号略大一点点，既不裁字也不虚高）。 */
private val ChatMessageTimeLineHeight = 12.sp
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
 * 失败消息上面那句"能怎么办"的提示。
 *
 * 只对**用户能做点什么**的几类给提示：网络问题可以重试、本地文件坏了重试没用、会话在核心侧没了
 * 只能重发。核心没给分型（[ChatFailureKind.UNKNOWN]）或内部问题时**不猜** —— 原始原因那一行已经
 * 是全部信息，硬加一句反而误导。
 */
@StringRes
private fun chatFailureHintRes(kind: ChatFailureKind): Int? = when (kind) {
    ChatFailureKind.TRANSPORT -> R.string.chat_failure_hint_transport
    ChatFailureKind.TRANSCRIPT -> R.string.chat_failure_hint_transcript
    ChatFailureKind.NO_SESSION -> R.string.chat_failure_hint_no_session
    ChatFailureKind.INTERNAL, ChatFailureKind.UNKNOWN -> null
}

/**
 * 发不出去时（输入为空 / 没有可续的回合）整只按钮的不透明度：ZCode 的 Button 基类就是
 * `disabled:opacity-50` —— 底色不换，只是整体变淡，所以按钮的"存在感"始终在。
 */
private const val ChatSendDisabledAlpha = 0.5f
private val ChatToolIconSize = 14.dp
private val ChatToolRowPaddingVertical = 4.dp
/** 工具名与它后面那个"可展开"尖角之间的距离。 */
private val ChatToolChevronGap = 4.dp
/** 工具行右侧那个"可以展开"的尖角。 */
private val ChatToolChevronSize = 16.dp
/** 展开出来的参数/结果相对工具行的左边距（与工具名对齐）。 */
private val ChatToolExpandedIndent = 22.dp
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
                onAction = onAction,
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

        if (state.isContextPanelOpen) {
            ContextUsageSheet(state = state, currentTheme = currentTheme, onAction = onAction)
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────

// ── Transcript ─────────────────────────────────────────────────────────

@Composable
private fun MessageList(
    state: ChatState,
    onAction: (ChatAction) -> Unit,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 跟随尾部：新消息和流式分片都会让最后一条变长。
    //
    // 两个细节都不能少（都是踩过的坑）：
    //  · 「在底部」要看**最后一条的底边是否贴到视口底**，不能只看最后一个 index 是否可见 ——
    //    模型回复常常比一屏还长，那时 index 一直是最后，可用户正在往回翻，跟着跳会把人拽走，
    //    而往下翻看结尾时又会被每个分片拉回顶部（表现为「滑不动」）。
    //  · 滚动要滚到**列表末尾**（`scrollOffset` 给极大值让它夹到末尾）。`scrollToItem(lastIndex)`
    //    只会把这一条的**顶部**对齐到视口顶 —— 回复越写越长，用户就永远看不到新内容。
    // 是否跟随尾部。**必须由手势决定**，不能只看滚动位置 —— 见下面 effect 里的说明。
    var followTail by remember { mutableStateOf(true) }
    val lastMessage = state.messages.lastOrNull()
    LaunchedEffect(state.messages.size, lastMessage?.text, lastMessage?.isStreaming) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        // 自己发了新消息 → 恢复跟随（发送后本来就该跳到最新）。
        if (lastMessage?.role == ChatRole.USER) followTail = true

        val atBottom = listState.isAtBottom()
        Log.d(
            CHAT_SCROLL_TAG,
            "触发 size=${state.messages.size} 文本长=${lastMessage?.text?.length} " +
                "streaming=${lastMessage?.isStreaming} follow=$followTail atBottom=$atBottom " +
                "前=${listState.describe()}"
        )
        // ⚠️ 不能只用 `atBottom` 决定是否跟随。实测日志：
        //     lastTop=205 lastBottom 从 8000 涨到 14125、viewportEnd=1546 —— 列表停在最顶部、
        //     回复在下面不断变长，"不贴底"的原因是**内容变长**而不是用户滑走了。
        //     用几何位置判断的话，回复一开始变长就永远算"不在底部"，一次都不会跟随。
        //     所以判据是：用户没自己拖过（followTail），或者用户此刻确实在底部。
        if (followTail || atBottom) {
            // 滚动必须**不可取消**：key 里有流式文本，每个分片都会重启这个 effect，
            // 上一次的 `scrollToItem`（挂起函数）会在滚完之前被取消掉。
            withContext(NonCancellable) {
                listState.scrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
            }
            followTail = true
            Log.d(CHAT_SCROLL_TAG, "已滚到末尾 后=${listState.describe()}")
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            // 用户一拖动列表就停止跟随。必须用**手势**判断：滚动位置无法区分
            // 「内容变长了」和「用户滑走了」（见上面 effect 的日志说明）。
            // 用 Initial 阶段，在列表自己的滚动消费掉事件之前看到它。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val dragged = event.changes.any { change ->
                            change.pressed && change.position != change.previousPosition
                        }
                        if (dragged) {
                            followTail = false
                            Log.d(CHAT_SCROLL_TAG, "用户手动拖动 → 停止跟随")
                        }
                    }
                }
            },
        contentPadding = PaddingValues(
            horizontal = ChatContentPaddingHorizontal,
            vertical = 14.dp
        ),
    ) {
        itemsIndexed(state.messages, key = { _, message -> message.id }) { index, message ->
            val tool = message.tool
            val stoppedAfterMs = message.stoppedAfterMs
            // 间距按位置算，不再全列表统一：
            // - 轮与轮之间（用户那条、以及它下面的第一段回复）用 [ChatMessageSpacing]；
            // - 「上一回合被停止」那行两侧也用 [ChatMessageSpacing]（这条是按你的要求定下的）；
            // - 其余（同一轮里的正文 ↔ 工具调用）用更小的 [ChatIntraTurnSpacing]。
            val previous = if (index > 0) state.messages[index - 1] else null
            val gap = when {
                previous == null -> 0.dp
                previous.role == ChatRole.USER -> ChatMessageSpacing
                message.role == ChatRole.USER -> ChatMessageSpacing
                previous.stoppedAfterMs != null -> ChatMessageSpacing
                stoppedAfterMs != null -> ChatMessageSpacing
                else -> ChatIntraTurnSpacing
            }
            // 用户那条的「时间」也等这一轮回复画完才出现：发送那刻回复还只是占位的流式气泡。
            val turnSettled = index < state.messages.lastIndex &&
                state.messages.subList(index + 1, state.messages.size).none { it.isStreaming }
            // 一轮的末尾：后面没有消息了，或者下一条是用户的新消息。
            val turnEnd = index == state.messages.lastIndex ||
                state.messages[index + 1].role == ChatRole.USER
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = gap)
            ) {
                when {
                    stoppedAfterMs != null -> TurnStoppedRow(
                        stoppedAfterMs = stoppedAfterMs,
                        currentTheme = currentTheme,
                    )
                    tool == null -> MessageBubble(
                        message = message,
                        currentTheme = currentTheme,
                        showTime = message.role != ChatRole.USER || turnSettled,
                    )
                    else -> ToolActivityRow(
                            activity = tool,
                            // 用户拨过就以他的为准；没拨过这里传 null，行内按运行状态自动开合。
                            expandedOverride = state.toolRowOpen[message.id],
                            onToggle = { onAction(ChatAction.ToolRowToggled(message.id, it)) },
                            currentTheme = currentTheme,
                        )
                }
                // 一轮的「时间 + 模型名」只在**整条回复结束时**出现一次。
                //
                // 一轮回复常被工具调用切成好几段（先说一句 → 调工具 → 再接着说），
                // 每段都挂标签就会冒出好几个标签，而且回复还没完就出现了。用户那条自己
                // 在气泡下方已经有时间，这里不重复。状态行自己不是一条消息，不挂标签。
                if (
                    turnEnd &&
                    !message.isStreaming &&
                    message.role == ChatRole.ASSISTANT &&
                    stoppedAfterMs == null
                ) {
                    Spacer(modifier = Modifier.height(ChatMessageTimeGap))
                    MessageMetaRow(
                        time = relativeTimeText(message.timestamp),
                        modelLabel = message.modelLabel,
                        currentTheme = currentTheme,
                    )
                }
            }
        }
    }
}

/**
 * 一行「上一回合被停止」的状态：浅灰小字 + 它下面一条通栏细线。
 *
 * 它是列表里独立的一项，两侧因此吃到和消息之间同一个 [ChatMessageSpacing]；小字与细线
 * 之间用 [ChatStopDividerGap]，所以整段读起来就是「消息 → 状态 → 消息」，两段空白一样。
 */
@Composable
private fun TurnStoppedRow(stoppedAfterMs: Long, currentTheme: CssVariables) {
    val seconds = stoppedAfterMs / 1000
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.chat_turn_stopped, seconds),
            fontSize = ChatMetaFontSize,
            color = currentTheme.mutedForeground,
        )
        Spacer(modifier = Modifier.height(ChatStopDividerGap))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChatDividerHeight)
                .background(currentTheme.border)
        )
    }
}

/** 跟随滚动的调试日志标签（`adb logcat -s ChatScroll`）。 */
private const val CHAT_SCROLL_TAG = "ChatScroll"

/**
 * 列表是否停在底部 —— 判断依据是**最后一条的底边贴到视口底**。
 *
 * 只看「最后一个 index 可见」不够：一条比一屏还长的回复，index 从头到尾都在最后，
 * 但用户可能正停在它中间往回看 —— 那时不该把他拽到底部。
 */
private fun LazyListState.isAtBottom(tolerancePx: Int = 8): Boolean {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return true
    return last.index == info.totalItemsCount - 1 &&
        last.offset + last.size <= info.viewportEndOffset + tolerancePx
}

/** 布局快照，供日志定位「为什么不跟随」。 */
private fun LazyListState.describe(): String {
    val info = layoutInfo
    val first = info.visibleItemsInfo.firstOrNull()
    val last = info.visibleItemsInfo.lastOrNull()
    return buildString {
        append("items=").append(info.totalItemsCount)
        append(" 可见=").append(info.visibleItemsInfo.size)
        append(" first=").append(first?.index)
        append(" last=").append(last?.index)
        append(" lastTop=").append(last?.offset)
        append(" lastBottom=").append(last?.let { it.offset + it.size })
        append(" viewportEnd=").append(info.viewportEndOffset)
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
private fun MessageBubble(
    message: ChatMessage,
    currentTheme: CssVariables,
    /** 用户那条的「时间」要等这一轮回复画完才出现，见 MessageList 里的判断。 */
    showTime: Boolean = true,
) {
    val isUser = message.role == ChatRole.USER
    // 模型还一个字都没回来（既没思考、也没正文）时，位置让给动态等待提示（见 WaitingDots），
    // 而不再是一个静止的省略号。思考已到、只是正文还没到的情况不算在这里：那时上面那行已经说明在想了。
    val waitingForModel = message.isStreaming &&
        message.thinking.isEmpty() &&
        message.blocks.isEmpty() &&
        message.text.isEmpty()
    val text = message.text
    val time = relativeTimeText(message.timestamp)

    if (!isUser) {
        // The model's reply renders as Markdown blocks, built incrementally as chunks
        // arrive. `text` is still the fallback: a message with no blocks (a plain
        // transcript row that failed to parse, or a tool-only turn) shows as text.
        Column(modifier = Modifier.fillMaxWidth()) {
            // 失败分型给出的"能怎么办"（P1：以前只能匹配错误文本猜）。排在原始原因之前当一句导语，
            // 原因本身照旧由下面的块/正文显示。
            message.failureKind?.let { kind ->
                chatFailureHintRes(kind)?.let { hintRes ->
                    Text(
                        text = stringResource(hintRes),
                        fontSize = ChatMetaFontSize,
                        color = currentTheme.mutedForeground,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(ChatReasoningBottomGap))
                }
            }
            // 深度思考排在正文之前（模型先想、再答）：收起时只留一行。
            if (message.thinking.isNotEmpty()) {
                ReasoningRow(
                    thinking = message.thinking,
                    // 「思考进行中」= 这一轮还在流式、且**正文和工具都还没出来**（模型正只在想）。它同时
                    // 管三件事：文案带扫光、内容自动展开、想完自动收起。正文一开始写它就成了完成态。
                    isThinking = message.isStreaming &&
                        message.blocks.isEmpty() &&
                        message.text.isEmpty(),
                    thinkingMs = message.thinkingMs,
                    currentTheme = currentTheme,
                )
                // 只在思考下面确实还有正文时才留这点间隙。只有思考的那些消息（工具调用前那一轮）
                // 不留：否则它和紧跟着的工具行之间会多出一段空白，而工具之间是紧挨着的。
                if (message.blocks.isNotEmpty() || message.text.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(ChatReasoningBottomGap))
                }
            }
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
            } else if (waitingForModel) {
                WaitingDots(currentTheme = currentTheme)
            } else if (text.isNotEmpty()) {
                // 空字符串不画：一个空的 Text 照样占一整行，只带思考的那条消息下面会因此多出一大块。
                Text(
                    text = text,
                    fontSize = ChatBodyFontSize,
                    color = if (message.isError) {
                        currentTheme.mutedForeground
                    } else {
                        currentTheme.cardForeground
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // 模型这侧的 meta（时间 + 模型名）不在这里画 —— 它属于「整轮回复」，由
            // MessageList 在这一轮的最后一条消息之后统一画，见那里的说明。
        }
        return
    }

    val shape = RoundedCornerShape(currentTheme.radiusMd)
    // 气泡贴右；时间在**气泡正下方、和气泡左边缘对齐**（小标签）。
    //
    // 三层约束都要满足：不进气泡（会跟正文抢地方、跟着气泡底色走）、
    // 不跑到屏幕最左（离气泡太远，看不出属于谁）、也不越过气泡的左边界。
    // 做法是外层容器（宽度跟着气泡 wrap）承载「气泡 + 时间」，两者都贴这个容器的左边，
    // 容器本身再靠右 —— 于是时间的左边缘正好落在气泡左边缘上。
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(ChatBubbleMaxWidthFraction)
                .wrapContentWidth(Alignment.End),
            horizontalAlignment = Alignment.Start,
        ) {
            Column(
                modifier = Modifier
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
            if (time != null && showTime) {
                Spacer(modifier = Modifier.height(ChatMessageTimeGap))
                MessageMetaRow(time = time, modelLabel = null, currentTheme = currentTheme)
            }
        }
    }
}

/**
 * 消息底部的一行 meta：时间 + 模型名（都为空时什么都不画）。
 *
 * 模型名紧跟时间后面 —— 先看「什么时候」，再看「谁答的」；用户那条只给时间。
 */
@Composable
private fun MessageMetaRow(time: String?, modelLabel: String?, currentTheme: CssVariables) {
    if (time == null && modelLabel == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChatMessageMetaGap),
    ) {
        time?.let { MessageMetaChip(text = it, currentTheme = currentTheme) }
        modelLabel?.let { MessageMetaChip(text = it, currentTheme = currentTheme) }
    }
}

/**
 * 底部 meta 的小标签（时间、模型名共用）：灰底圆角小卡片。
 *
 * 这些字和正文都是同一种灰字，不加底色会和正文糊在一起分不清；给一层浅底就明确是
 * 「meta 信息」而不是消息内容。时间与模型名共用同一种样式，读起来是一组。
 */
@Composable
private fun MessageMetaChip(text: String, currentTheme: CssVariables) {
    Text(
        text = text,
        fontSize = ChatMetaFontSize,
        // 行高必须显式压下来：默认行距（约 1.33 倍）会把这个小标签撑得又高又空。
        lineHeight = ChatMessageTimeLineHeight,
        color = currentTheme.mutedForeground,
        modifier = Modifier
            .clip(RoundedCornerShape(currentTheme.radiusSm))
            .background(currentTheme.muted)
            .padding(
                horizontal = ChatTimeChipPaddingHorizontal,
                vertical = ChatTimeChipPaddingVertical,
            ),
    )
}


// ── Tool activity ──────────────────────────────────────────────────────

/**
 * One tool call, as a single line: a wrench, what was called, and a caret saying the call can be
 * opened. Opening it prints the arguments and the result as plain text.
 *
 * No card: this is execution trace, not something the model said, so it stays one line until the
 * reader asks for the detail.
 */
// ── 扫光文字 ───────────────────────────────────────────────────────────

/**
 * 「正在…」用的扫光文字：底色"淡"、一条**窄**实体亮带从文字左边扫到右边（ZCode 的
 * `animated-gradient-text` + `shimmer.tsx`；周期见 [ChatReasoningSweepPeriodMs]）。
 *
 * 思考行的「正在思考」与工具行的「调用工具」共用它 —— 这样"进行中"在全 app 里是同一套视觉语言。
 * 亮带半宽 = 字数 × [ChatReasoningSweepHalfWidthPerChar]（ZCode 的 `spread = 2`），行程按文字实测
 * 宽度算：**窄带**才有"扫过去"的观感，拿固定几百 px 的宽渐变铺在短标签上等于没有。
 */
@Composable
private fun SweepText(text: String, currentTheme: CssVariables) {
    val sweep = rememberInfiniteTransition(label = "sweep")
    val shift by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(ChatReasoningSweepPeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep-shift"
    )
    val halfBand = with(LocalDensity.current) {
        ChatReasoningSweepHalfWidthPerChar.toPx() * text.length
    }
    var textWidth by remember { mutableStateOf(0f) }
    val soft = currentTheme.foreground.copy(alpha = ChatReasoningSweepSoftAlpha)
    // 亮带从左侧外面走到右侧外面：行程 = 文字宽 + 两个半宽。
    val travel = textWidth + 2f * halfBand
    val center = -halfBand + travel * shift
    Text(
        text = text,
        style = TextStyle(
            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(soft, currentTheme.foreground, soft),
                start = Offset(center - halfBand, 0f),
                end = Offset(center + halfBand, 0f)
            ),
            fontSize = ChatMetaFontSize,
            fontWeight = FontWeight.Medium
        ),
        onTextLayout = { textWidth = it.size.width.toFloat() }
    )
}

// ── 等待模型开口 ───────────────────────────────────────────────────────

/** 三个点的大小、间隔、一个循环的时长、各点之间的相位差，以及点最多浮起多少。 */
private val ChatWaitingDotSize = 6.dp
private val ChatWaitingDotGap = 4.dp
private val ChatWaitingDotLift = 4.dp
private const val ChatWaitingDotCount = 3
private const val ChatWaitingDotPeriodMs = 900
private const val ChatWaitingDotStaggerMs = 140

/**
 * 等待模型开口时的动态提示：三个点依次浮起又落下（透明度与位置一起变），替代原来那个静止的「…」。
 *
 * 它是个循环状态机：每点跑同一个无限动画，只差一个起始延迟，所以看上去像一波波流过。整块宽度固定、
 * 高度取点的大小，所以它在出现和消失时都不会把气泡撑得跳动。
 */
@Composable
private fun WaitingDots(currentTheme: CssVariables) {
    val transition = rememberInfiniteTransition(label = "waiting-dots")
    Row(
        modifier = Modifier.height(ChatWaitingDotSize + ChatWaitingDotLift),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(ChatWaitingDotCount) { index ->
            val phase = transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = ChatWaitingDotPeriodMs,
                        delayMillis = index * ChatWaitingDotStaggerMs,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                ),
                label = "waiting-dot-$index"
            )
            // 三角波：0 → 1 → 0，一个循环里浮起再落下。
            val p = phase.value
            val wave = if (p < 0.5f) p * 2f else (1f - p) * 2f
            Box(
                modifier = Modifier
                    .padding(end = ChatWaitingDotGap)
                    .offset(y = ChatWaitingDotLift * -wave)
                    .size(ChatWaitingDotSize)
                    .clip(CircleShape)
                    .background(
                        currentTheme.mutedForeground.copy(alpha = 0.3f + 0.7f * wave)
                    )
            )
        }
    }
}

// ── 深度思考 ───────────────────────────────────────────────────────────

/** 思考块：图标/箭头大小、行内边距，以及展开内容的缩进、左导线与限高。 */
private val ChatReasoningIconSize = 15.dp
private val ChatReasoningChevronSize = 15.dp
private val ChatReasoningRowPaddingVertical = 2.dp
private val ChatReasoningBottomGap = 6.dp
private val ChatReasoningContentTopGap = 4.dp
private val ChatReasoningContentIndent = 6.dp
private val ChatReasoningRuleGap = 10.dp
private val ChatReasoningRuleWidth = 1.dp
private val ChatReasoningMaxHeight = 240.dp

/**
 * 扫光文字一轮多久（ZCode 那边是 4s，这里按真机观感调快 —— 4 秒一轮显得太慢）。
 */
private const val ChatReasoningSweepPeriodMs = 1000

/**
 * 扫光**亮带**的半宽：每个字 2dp。
 *
 * 照 ZCode 的 `shimmer.tsx`（`spread = 2`，即每个字 2px）+ `animated-gradient-text`：亮带很窄，
 * 底色是"淡"（`--animated-gradient-text-soft: rgba(10,10,10,0.22)`），亮带是实体色。
 * 之前用固定 220px 跨度铺在几十像素的短标签上，对比被摊平，看起来就是没扫光。
 */
private val ChatReasoningSweepHalfWidthPerChar = 2.dp

/** 亮带之外的文字底色（ZCode 的 `--animated-gradient-text-soft` = 实体色 22%）。 */
private const val ChatReasoningSweepSoftAlpha = 0.22f

/**
 * 「深度思考」：模型答之前想过什么。一行是脑图标 + 文案 + 折线箭头，点这一行手动展开/收起。
 *
 * 开合时机：**思考中自动展开**（内容跟着流式增长，一眼就能看见）、**思考结束自动收起**；用户手动
 * 开合过之后就交给他，不再自动动。
 *
 * 交互与样式照 ZCode 的思考块：整行可点、箭头收起时朝右、展开后转 90° 朝下（它用的是折线箭头，不是
 * 实心三角）；展开的内容加一条左导线、限高滚动、吸底跟随最新思考、纯文本按原样换行。思考期间文案是
 * 带扫光的「正在深度思考」，结束后换成「思考 · 持续 N 秒」。
 */
@Composable
private fun ReasoningRow(
    thinking: String,
    isThinking: Boolean,
    /** 这段思考花了多久；null = 还在想，或没记到。 */
    thinkingMs: Long? = null,
    currentTheme: CssVariables,
) {
    var expanded by remember { mutableStateOf(false) }
    // 用户手动开过之后就尊重他的选择；没动过的话，思考结束自动收起
    // （ZCode 的 `autoCollapseKey = streaming ? null : state`）。
    var touched by remember { mutableStateOf(false) }
    // 思考中自动展开（内容跟着流式增长，直接看得见），思考结束自动收起 —— 用户手动开合过之后就
    // 尊重他的选择（ZCode 的 `autoCollapseKey = streaming ? null : state` 也是这个意思）。
    LaunchedEffect(isThinking) {
        if (!touched) {
            expanded = isThinking
        }
    }
    // 展开着看的时候内容吸底跟随最新思考（ZCode 的 autoFollowBottom）。
    val thinkingScroll = rememberScrollState()
    LaunchedEffect(thinking, expanded) {
        if (expanded) {
            thinkingScroll.scrollTo(thinkingScroll.maxValue)
        }
    }
    // 收起时右侧那一行摘要（ZCode 的流式摘要）：取**最后一个非空行**，随思考增长往前滚。
    val summary = remember(thinking) {
        thinking.lineSequence().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(currentTheme.radiusSm))
                .clickable {
                    touched = true
                    expanded = !expanded
                }
                .padding(vertical = ChatReasoningRowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = LucideIcons.Brain,
                contentDescription = null,
                tint = currentTheme.mutedForeground,
                modifier = Modifier.size(ChatReasoningIconSize)
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (isThinking) {
                // 思考期间一直扫。条件里**不能**再带 `!expanded` —— 思考中现在会自动展开，带上的话就
                // 永远扫不到（ZCode 那个 `!isOpen` 是针对"手动展开"的，我们这里展开是自动的）。
                SweepText(stringResource(R.string.chat_reasoning_thinking), currentTheme)
            } else {
                // 完成态照 ZCode：「思考 · 持续了 N 秒」（一秒都不到时用「持续了几秒」）。
                Text(
                    text = when {
                        thinkingMs == null -> stringResource(R.string.chat_reasoning_title)
                        thinkingMs < 1_000L ->
                            stringResource(R.string.chat_reasoning_duration_short)
                        else -> stringResource(
                            R.string.chat_reasoning_duration,
                            (thinkingMs / 1_000L).toInt()
                        )
                    },
                    fontSize = ChatMetaFontSize,
                    fontWeight = FontWeight.Medium,
                    color = currentTheme.mutedForeground
                )
            }
            // 展开时下面就是思考正文，这一行摘要就不重复了。
            if (summary.isNotEmpty() && !expanded) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "·",
                    fontSize = ChatMetaFontSize,
                    color = currentTheme.mutedForeground
                )
                Spacer(modifier = Modifier.width(6.dp))
                // 摘要跟着思考增长往左滚，**始终露出最新那几个字**（ZCode 的流式摘要就是这么做的：
                // 溢出隐藏 + 内容一变就把滚动推到末尾）。
                val summaryScroll = rememberScrollState()
                LaunchedEffect(summary) { summaryScroll.scrollTo(summaryScroll.maxValue) }
                Box(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary,
                        fontSize = ChatMetaFontSize,
                        color = currentTheme.mutedForeground,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(summaryScroll)
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Icon(
                imageVector = LucideIcons.ChevronRight,
                contentDescription = stringResource(R.string.chat_reasoning_expand_cd),
                tint = currentTheme.mutedForeground,
                modifier = Modifier
                    .size(ChatReasoningChevronSize)
                    .rotate(if (expanded) 90f else 0f)
            )
        }
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = ChatReasoningContentIndent, top = ChatReasoningContentTopGap)
            ) {
                Box(
                    modifier = Modifier
                        .width(ChatReasoningRuleWidth)
                        .fillMaxHeight()
                        .background(currentTheme.border)
                )
                Spacer(modifier = Modifier.width(ChatReasoningRuleGap))
                Text(
                    text = thinking,
                    fontSize = ChatMetaFontSize,
                    color = currentTheme.mutedForeground,
                    modifier = Modifier
                        .heightIn(max = ChatReasoningMaxHeight)
                        .verticalScroll(thinkingScroll)
                )
            }
        }
    }
}

/**
 * 卡头图标按工具类型选 —— ZCode 的每个 renderer 都有自己的图标（edit 是铅笔、todo 是清单、execute
 * 是终端）。这里取 Material **核心**图标集里含义最接近的那个。
 */
private fun toolCallIconOf(name: String, resultOnly: Boolean): ImageVector =
    when (toolCallKindOf(name)) {
        ToolCallKind.DIFF -> LucideIcons.Pencil
        ToolCallKind.TODO -> LucideIcons.ListTodo
        ToolCallKind.TERMINAL -> LucideIcons.SquareTerminal
        ToolCallKind.PLAIN -> if (resultOnly) LucideIcons.Check else LucideIcons.Wrench
    }

@Composable
private fun ToolActivityRow(
    activity: ChatToolActivity,
    expandedOverride: Boolean?,
    onToggle: (Boolean) -> Unit,
    currentTheme: CssVariables,
) {
    val resultOnly = activity.isResultOnly
    // 状态读数据，不猜。
    val running = activity.status == ChatToolStatus.RUNNING
    // 展开态：用户拨过（[expandedOverride] 非空）以他的为准；没拨过就按运行状态自动开合 ——
    // 跑着展开看进度、结果一回来收好（ZCode 的 autoOpen / autoCollapseOnComplete）。
    //
    // 这里是**推导**出来的、不记任何东西：以前那版靠"首次组合把它写进全局表里"，一写就把
    // "没拨过才自动开"的守卫写坏，自动展开从此静默失效；而且那张表是进程级的、从不清理。
    val expanded = expandedOverride ?: running
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(currentTheme.radiusSm))
                .clickable { onToggle(!expanded) }
                .padding(vertical = ChatToolRowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = toolCallIconOf(activity.name, resultOnly),
                contentDescription = null,
                tint = currentTheme.mutedForeground,
                modifier = Modifier.size(ChatToolIconSize)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // kindLabel：还在跑就扫光，做完了是安静的淡色（ZCode 的 ToolSummaryRow 就是这么分的）。
            if (running) {
                SweepText(
                    text = stringResource(R.string.chat_tool_kind_running),
                    currentTheme = currentTheme
                )
            } else {
                Text(
                    text = stringResource(R.string.chat_tool_kind_done),
                    fontSize = ChatMetaFontSize,
                    fontWeight = FontWeight.Medium,
                    color = currentTheme.mutedForeground
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            // primaryText：工具名。
            Text(
                text = activity.name,
                fontSize = ChatMetaFontSize,
                color = currentTheme.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            // 状态词（四态各有各的词，见 ChatToolStatus）。
            Text(
                text = stringResource(
                    when (activity.status) {
                        ChatToolStatus.RUNNING -> R.string.chat_tool_status_running
                        ChatToolStatus.COMPLETED -> R.string.chat_tool_status_done
                        ChatToolStatus.FAILED -> R.string.chat_tool_status_failed
                        ChatToolStatus.STOPPED -> R.string.chat_tool_status_stopped
                    }
                ),
                fontSize = ChatMetaFontSize,
                color = currentTheme.mutedForeground
            )
            // 载荷形状认不出来、展开区按原文显示时，标一下（E2）：不让"解析失败"和"没有内容"
            // 看起来一模一样 —— 那是这条降级链上唯一真正的坑。
            if (toolCallFallsBackToRaw(activity.name, activity.detail, activity.result)) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.chat_tool_raw_hint),
                    fontSize = ChatMetaFontSize,
                    color = currentTheme.mutedForeground
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = LucideIcons.ChevronRight,
                contentDescription = stringResource(R.string.chat_tool_expand_cd),
                tint = currentTheme.mutedForeground,
                modifier = Modifier
                    .size(ChatToolChevronSize)
                    .rotate(if (expanded) 90f else 0f)
            )
        }
        // 展开/收起带高度动画（ZCode 那边折起来有 300ms 的动画）。
        AnimatedVisibility(visible = expanded) {
            // 展开区与思考块同一套（左导线 + 缩进）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = ChatToolExpandedIndent, top = ChatReasoningContentTopGap)
            ) {
                Box(
                    modifier = Modifier
                        .width(ChatReasoningRuleWidth)
                        .fillMaxHeight()
                        .background(currentTheme.border)
                )
                Spacer(modifier = Modifier.width(ChatReasoningRuleGap))
                // 展开内容按工具类型分流（照 ZCode 的 resolveToolCallRenderer）：改动文件画补丁、
                // 待办画清单、命令画终端输出，认不出的走兜底纯文本。
                ToolCallDetail(
                    name = activity.name,
                    detail = activity.detail,
                    result = activity.result,
                    currentTheme = currentTheme,
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
    // 暂停之后：输入框空着才是「继续」；一敲进内容它就变回「发送」——那条内容就是新的一轮。
    val resume = state.canContinue && !canSend
    // 「推理强度」选择面板（复用我们的 BottomSheet）的开合。
    var isEffortSheetOpen by remember { mutableStateOf(false) }
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
            // Left: 上下文容量入口 —— 一个环，环身画「最近一次请求 / 配置的窗口」的比例；
            // 点一下弹出面板（复用 BottomSheet）。没有用量或没配窗口时只是空轨道。
            ContextUsageRing(usage = state.contextUsage, currentTheme = currentTheme) {
                onAction(ChatAction.ContextPanelOpened)
            }

            Spacer(modifier = Modifier.width(ChatComposerToolRowGap))

            // 模型入口，点击打开模型选择。有激活模型时显示模型名；没有时以前是空串，
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
            Button(
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
                rippleEnabled = false,
                modifier = Modifier.size(ChatSendButtonSize),
                testTag = "chat_send_btn"
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

            ContextWindowSection(
                window = state.contextWindow,
                currentTheme = currentTheme,
                onSelect = { onAction(ChatAction.ContextWindowSelected(it)) },
            )
        }
    }
}

// ── Context window picker ──────────────────────────────────────────────

/** 档位之间的间距。 */
private val ChatContextStepGap = 8.dp

/** 档位小按钮的内边距与最小高度（宽度由标签自己撑开）。 */
private val ChatContextStepPaddingHorizontal = 12.dp
private val ChatContextStepPaddingVertical = 8.dp
private val ChatContextStepMinHeight = 38.dp

/** 可选档位，第一个是核心的默认值。 */
private val ChatContextWindowSteps = listOf(
    200_000L to "200K",
    400_000L to "400K",
    600_000L to "600K",
    800_000L to "800K",
    1_000_000L to "1M",
)

/**
 * 当前会话的上下文窗口：几个固定档位点一下即选，「不设上限」选它就没有占比可算，
 * 底下还能自己填一个数。
 *
 * 它决定环与面板的分母，所以放在模型面板里 —— 换模型和改窗口是同一件事的两面。
 */
@Composable
private fun ContextWindowSection(
    window: Long,
    currentTheme: CssVariables,
    onSelect: (Long) -> Unit,
) {
    var custom by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChatDividerHeight)
                .background(currentTheme.border)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.chat_context_window_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = currentTheme.foreground
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = contextWindowLabel(window),
                fontSize = ChatMetaFontSize,
                fontFamily = FontFamily.Monospace,
                color = currentTheme.mutedForeground
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChatContextStepGap),
        ) {
            ChatContextWindowSteps.forEach { (tokens, label) ->
                ContextWindowStep(
                    label = label,
                    isSelected = window == tokens,
                    currentTheme = currentTheme,
                    onClick = { onSelect(tokens) },
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 自定义：填一个以 K 为单位的数（比如 300 = 300K），确定即生效。
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = custom,
                onValueChange = { typed -> custom = typed.filter(Char::isDigit).take(7) },
                singleLine = true,
                textStyle = TextStyle(
                    color = currentTheme.foreground,
                    fontSize = ChatBodyFontSize
                ),
                cursorBrush = SolidColor(currentTheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_context_window_custom"),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(currentTheme.radiusSm))
                            .background(currentTheme.subtleSurface)
                            .padding(
                                horizontal = ChatContextStepPaddingHorizontal,
                                vertical = ChatContextStepPaddingVertical,
                            )
                    ) {
                        if (custom.isEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_context_window_custom_hint),
                                fontSize = ChatBodyFontSize,
                                color = currentTheme.mutedForeground
                            )
                        }
                        inner()
                    }
                }
            )
            Spacer(modifier = Modifier.width(ChatContextStepGap))
            val customTokens = custom.toLongOrNull()?.times(1000)
            ContextWindowStep(
                label = stringResource(R.string.chat_context_window_custom_apply),
                isSelected = customTokens != null && customTokens == window,
                currentTheme = currentTheme,
                enabled = customTokens != null,
                onClick = { customTokens?.let(onSelect) },
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.chat_context_window_hint),
            fontSize = ChatMetaFontSize,
            color = currentTheme.mutedForeground
        )
    }
}

/**
 * 一个档位：**由内容撑开**的长方形胶囊，选中的用主色描边。
 *
 * 刻意不用共享的 [Button]：它自带 `sizeIn(minWidth = 48dp, minHeight = 48dp)` 的**触摸**尺寸
 * （发送键那边是靠显式给尺寸把它夹住的），套在「1M」这种短标签上会变成一个正方形，四周留一大块
 * 空白。这里自己量：内容 + 内边距决定宽度，高度只保底 [ChatContextStepMinHeight]。
 */
@Composable
private fun ContextWindowStep(
    label: String,
    isSelected: Boolean,
    currentTheme: CssVariables,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(currentTheme.radiusSm)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (isSelected) currentTheme.primary.copy(alpha = 0.14f) else currentTheme.subtleSurface
            )
            .border(
                width = ChatDividerHeight,
                color = if (isSelected) currentTheme.primary else currentTheme.border,
                shape = shape
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = ChatContextStepMinHeight)
            .padding(
                horizontal = ChatContextStepPaddingHorizontal,
                vertical = ChatContextStepPaddingVertical,
            )
            .testTag("chat_context_window_$label"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = ChatMetaFontSize,
            color = when {
                isSelected -> currentTheme.primary
                enabled -> currentTheme.foreground
                else -> currentTheme.mutedForeground
            }
        )
    }
}

/** 当前窗口的显示名：固定档位用它的简写，别的数按 K/M 显示。 */
private fun contextWindowLabel(tokens: Long): String {
    val step = ChatContextWindowSteps.firstOrNull { it.first == tokens }
    return step?.second ?: tokenCountLabel(tokens)
}

/** 「200K」「1M」「128K」这种按千 / 百万取整的写法；不是整千就退回系统的紧凑记法。 */
private fun tokenCountLabel(tokens: Long): String = when {
    tokens >= 1_000_000 && tokens % 1_000_000 == 0L -> "${tokens / 1_000_000}M"
    tokens >= 1_000 && tokens % 1_000 == 0L -> "${tokens / 1_000}K"
    else -> compactTokenText(tokens, 1)
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

// ── Context usage ──────────────────────────────────────────────────────

/** 环的外径：照 ZCode 那个入口的 14px。 */
private val ChatContextRingSize = 14.dp

/** 面板里那条分段横条的高度，照 ZCode 的 h-2。 */
private val ChatContextBarHeight = 8.dp

/** 分段之间留一条细缝，免得分段连成一片看不出分界。 */
private val ChatContextBarSegmentGap = 2.dp

/** 分类行前面的色块，照 ZCode 的 size-2。 */
private val ChatContextDotSize = 8.dp

/** 分类行之间的间距。 */
private val ChatContextRowGap = 8.dp

/**
 * 色阶的 5 档混色比例，照 ZCode：第 1 档是主色本身，最后一档只剩 28% 主色。
 *
 * 颜色只跟**排序后的位次**有关（占比越大越深），所以某一块的占比一变、它的颜色也跟着变 ——
 * 这一点与 ZCode 相同。
 */
private val ChatContextToneMix = listOf(1f, 0.78f, 0.58f, 0.42f, 0.28f)

private fun contextTone(index: Int, currentTheme: CssVariables): Color {
    val mix = ChatContextToneMix[index.coerceIn(0, ChatContextToneMix.lastIndex)]
    return lerp(currentTheme.primary, currentTheme.card, 1f - mix)
}

/**
 * 输入框里的环形入口。
 *
 * 环身画「最近一次请求放进窗口的 token / 平台配的窗口」，与 ZCode 那个入口同一个意思；没配窗口
 * 或还没有用量时就只有一条淡轨道。点它打开 [ContextUsageSheet]。
 */
/** 选择面板的标题与行距、以及选中行那个对勾的大小。 */
private val ChatEffortSheetTitleGap = 10.dp
private val ChatEffortSheetRowPaddingVertical = 12.dp
private val ChatEffortSheetCheckSize = 16.dp

/**
 * 「推理强度」的选择面板：可选档位各一行，当前档用品牌色 + 一个对勾。
 *
 * 复用我们自己的 [BottomSheet]（与同页的模型面板、上下文面板同一个做法）—— 供应商页那个原本是
 * M3 的 `DropdownMenu`，这次也一起换成了它。
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
                        .clip(RoundedCornerShape(currentTheme.radiusSm))
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

    Button(
        onClick = onClick,
        rippleEnabled = false,
        testTag = "chat_reasoning_effort_btn",
        modifier = Modifier.height(ChatSendButtonSize)
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

@Composable
private fun ContextUsageRing(
    usage: ContextUsage?,
    currentTheme: CssVariables,
    onClick: () -> Unit,
) {
    val window = usage?.modelContextWindow ?: 0L
    val used = usage?.usedTokens ?: 0L
    val progress = if (window > 0) (used.toFloat() / window).coerceIn(0f, 1f) else 0f
    val trackColor = currentTheme.mutedForeground.copy(alpha = 0.25f)
    val progressColor = currentTheme.mutedForeground.copy(alpha = 0.7f)

    Button(
        onClick = onClick,
        rippleEnabled = false,
        modifier = Modifier.size(ChatSendButtonSize),
        testTag = "chat_context_usage_btn",
    ) {
        Canvas(modifier = Modifier.size(ChatContextRingSize)) {
            // 线宽取 ZCode 那个环的比例（24 的 viewBox 里 stroke 是 4），跟着尺寸缩放。
            val stroke = size.minDimension * 4f / 24f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawCircle(
                color = trackColor,
                radius = arcSize.minDimension / 2f,
                center = center,
                style = Stroke(width = stroke),
            )
            if (progress > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * 面板本体：标题 + 右侧总量、按来源的分段横条、分类行、收尾一条分隔线。
 *
 * 布局与 ZCode 的工具栏面板一致，只是按你的要求去掉了「平均缓存命中率」那一行。分类里
 * **五个来源都列**，为 0 的也列 —— 技能与 MCP 工具还要补，先把位置留出来。
 */
@Composable
private fun ContextUsageSheet(
    state: ChatState,
    currentTheme: CssVariables,
    onAction: (ChatAction) -> Unit,
) {
    BottomSheet(
        onDismiss = { onAction(ChatAction.ContextPanelDismissed) },
        currentTheme = currentTheme,
        modifier = Modifier.testTag("chat_context_usage_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            val usage = state.contextUsage
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.chat_context_usage_title),
                    fontSize = ChatBodyFontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = currentTheme.foreground
                )
                Spacer(modifier = Modifier.weight(1f))
                if (usage != null) {
                    Text(
                        text = contextUsageSummary(usage),
                        fontSize = ChatMetaFontSize,
                        fontFamily = FontFamily.Monospace,
                        color = currentTheme.mutedForeground
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // 五个来源都列（技能与 MCP 工具的功能还没做，现在是 0，先把位置留着）；排序只影响
            // 先后与配色，0 的那些自然落到最后。
            val rows = usage?.breakdown.orEmpty().sortedByDescending { it.tokens }
            if (rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_context_usage_empty),
                    fontSize = ChatMetaFontSize,
                    color = currentTheme.mutedForeground
                )
                return@Column
            }

            val total = rows.sumOf { it.tokens }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChatContextBarHeight)
                    .clip(CircleShape)
            ) {
                // 条上只铺有内容的那几段；色阶仍按**它在分类行里的位次**取，好让条与行对得上。
                val barRows = rows.withIndex().filter { (_, bucket) -> bucket.tokens > 0 }
                barRows.forEachIndexed { position, (index, bucket) ->
                    Box(
                        modifier = Modifier
                            .weight(bucket.tokens.toFloat() / total.toFloat())
                            .height(ChatContextBarHeight)
                            .background(contextTone(index, currentTheme))
                    )
                    if (position != barRows.lastIndex) {
                        Spacer(modifier = Modifier.width(ChatContextBarSegmentGap))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            rows.forEachIndexed { index, bucket ->
                if (index > 0) Spacer(modifier = Modifier.height(ChatContextRowGap))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(ChatContextDotSize)
                            .clip(RoundedCornerShape(2.dp))
                            .background(contextTone(index, currentTheme))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(bucket.source.labelRes()),
                        fontSize = ChatMetaFontSize,
                        color = currentTheme.mutedForeground
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        // 分类占比按这一轮请求内部各来源的份额算，与 ZCode 同一口径。
                        text = contextPercentText(bucket.tokens.toDouble() / total.toDouble()),
                        fontSize = ChatMetaFontSize,
                        fontFamily = FontFamily.Monospace,
                        color = currentTheme.foreground
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChatDividerHeight)
                    .background(currentTheme.border)
            )
            // 当前模型自己的参数：这是「模型提供商」里预设的那两个数，作为参考放在这里 ——
            // 会话的窗口就是按它（或它的默认值）在第一次附着时定下的。
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.chat_context_model_section),
                fontSize = ChatMetaFontSize,
                color = currentTheme.mutedForeground
            )
            Spacer(modifier = Modifier.height(6.dp))
            ContextModelParamRow(
                label = stringResource(R.string.chat_context_model_window),
                value = state.activeModel?.contextLength,
                currentTheme = currentTheme,
            )
            Spacer(modifier = Modifier.height(4.dp))
            ContextModelParamRow(
                label = stringResource(R.string.chat_context_model_output),
                value = state.activeModel?.maxOutputLength,
                currentTheme = currentTheme,
            )
        }
    }
}

/** 「模型设定」里的一行：名目 + 右侧 mono 数值；没配过就写「未设置」。 */
@Composable
private fun ContextModelParamRow(
    label: String,
    value: Int?,
    currentTheme: CssVariables,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = ChatMetaFontSize,
            color = currentTheme.mutedForeground
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (value != null && value > 0) {
                tokenCountLabel(value.toLong())
            } else {
                stringResource(R.string.chat_context_model_unset)
            },
            fontSize = ChatMetaFontSize,
            fontFamily = FontFamily.Monospace,
            color = currentTheme.foreground
        )
    }
}

/** 分类名，与核心的 [ContextUsageSource] 一一对应。 */
private fun ContextUsageSource.labelRes(): Int = when (this) {
    ContextUsageSource.SYSTEM_PROMPT -> R.string.chat_context_system_prompt
    ContextUsageSource.SYSTEM_TOOL_SCHEMAS -> R.string.chat_context_system_tools
    ContextUsageSource.SKILLS -> R.string.chat_context_skills
    ContextUsageSource.MCP_TOOL_SCHEMAS -> R.string.chat_context_mcp_tools
    ContextUsageSource.MESSAGES -> R.string.chat_context_messages
}

/** 「2.2万/100万 (2.2%)」；没配窗口时只给已用的那个数。 */
private fun contextUsageSummary(usage: ContextUsage): String {
    val used = compactTokenText(usage.usedTokens, 1)
    val window = usage.modelContextWindow ?: return used
    val ratio = usage.usedTokens.toDouble() / window.toDouble()
    return "$used/${compactTokenText(window, 0)} (${contextPercentText(ratio)})"
}

/** 「2.2万」「100万」：ICU 的紧凑十进制记法，中文给的正是万 / 亿。 */
private fun compactTokenText(tokens: Long, fractionDigits: Int): String =
    CompactDecimalFormat
        .getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT)
        .apply { maximumFractionDigits = fractionDigits }
        .format(tokens)

/** 百分比，1 位小数。 */
private fun contextPercentText(ratio: Double): String =
    NumberFormat.getPercentInstance(Locale.getDefault())
        .apply { maximumFractionDigits = 1 }
        .format(ratio.coerceIn(0.0, 1.0))
