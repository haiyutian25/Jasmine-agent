package com.lhzkml.jasmine.feature.main.impl.chat

import com.lhzkml.jasmine.core.ui.theme.AppShapes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.lhzkml.jasmine.core.widgets.icon.Icon
import com.lhzkml.jasmine.core.widgets.text.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.agent.ChatFailureKind
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.markdown.ui.MarkdownBlockList
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.main.impl.R
import com.lhzkml.jasmine.feature.main.impl.relativeTimeText


/** Bubbles never span the full width — the asymmetry is what reads as a transcript. */
private const val ChatBubbleMaxWidthFraction = 0.85f

/** 「上一回合被停止」那行小字与它下面那条分隔线之间的距离。 */
private val ChatStopDividerGap = 8.dp

/** 底部两个 meta 标签（时间、模型名）之间的间距。 */
private val ChatMessageMetaGap = 6.dp

/** 时间小标签的内边距 —— 小小的就够，别做成按钮。纵向刻意只给 1dp，别撑高。 */
private val ChatTimeChipPaddingHorizontal = 6.dp
private val ChatTimeChipPaddingVertical = 1.dp

/** 时间小标签的行高（比字号略大一点点，既不裁字也不虚高）。 */
private val ChatMessageTimeLineHeight = 12.sp

private val ChatToolIconSize = 14.dp
private val ChatToolRowPaddingVertical = 4.dp
/** 工具名与它后面那个"可展开"尖角之间的距离。 */
private val ChatToolChevronGap = 4.dp
/** 工具行右侧那个"可以展开"的尖角。 */
private val ChatToolChevronSize = 16.dp
/** 展开出来的参数/结果相对工具行的左边距（与工具名对齐）。 */
private val ChatToolExpandedIndent = 22.dp

/** 三个点的大小、间隔、一个循环的时长、各点之间的相位差，以及点最多浮起多少。 */
private val ChatWaitingDotSize = 6.dp
private val ChatWaitingDotGap = 4.dp
private val ChatWaitingDotLift = 4.dp
private const val ChatWaitingDotCount = 3
private const val ChatWaitingDotPeriodMs = 900
private const val ChatWaitingDotStaggerMs = 140

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
 * 一行「上一回合被停止」的状态：浅灰小字 + 它下面一条通栏细线。
 *
 * 它是列表里独立的一项，两侧因此吃到和消息之间同一个 [ChatMessageSpacing]；小字与细线
 * 之间用 [ChatStopDividerGap]，所以整段读起来就是「消息 → 状态 → 消息」，两段空白一样。
 */
@Composable
internal fun TurnStoppedRow(stoppedAfterMs: Long, currentTheme: CssVariables) {
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
internal fun MessageBubble(
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

    val shape = AppShapes.medium
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
internal fun MessageMetaRow(time: String?, modelLabel: String?, currentTheme: CssVariables) {
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
            .clip(AppShapes.small)
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
                .clip(AppShapes.small)
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
internal fun ToolActivityRow(
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
                .clip(AppShapes.small)
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
