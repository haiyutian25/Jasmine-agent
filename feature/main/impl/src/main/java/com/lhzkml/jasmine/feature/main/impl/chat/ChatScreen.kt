package com.lhzkml.jasmine.feature.main.impl.chat

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.main.impl.relativeTimeText
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext


// ── Chat dimensions ────────────────────────────────────────────────────

internal val ChatDividerHeight = 1.dp

internal val ChatContentPaddingHorizontal = 16.dp
private val ChatMessageSpacing = 20.dp
/** 一轮之内的条目（正文 ↔ 工具调用）之间的间距：比气泡间距小，免得把一轮拆散。 */
private val ChatIntraTurnSpacing = 2.dp
internal val ChatBubblePaddingHorizontal = 12.dp
internal val ChatBubblePaddingVertical = 10.dp

internal val ChatBodyFontSize = 13.5.sp
internal val ChatMetaFontSize = 11.sp

/** 消息时间小标签与正文之间的间距。 */
internal val ChatMessageTimeGap = 4.dp

private val ChatTitleFontSize = 16.sp

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
internal val ChatSendButtonSize = 26.dp

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
