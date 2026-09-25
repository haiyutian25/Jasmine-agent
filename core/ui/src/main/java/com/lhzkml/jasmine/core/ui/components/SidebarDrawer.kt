package com.lhzkml.jasmine.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.R
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Sidebar width; single source of truth for the panel and the push offset. */
val SidebarWidth: Dp = 295.dp

/** Drawer animation timing shared by the panel slide and the canvas push. */
const val SidebarDrawerAnimMillis = 320
val SidebarDrawerEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Left-edge zone (in dp) where a rightward swipe can open the closed drawer.
 *
 * Exposed so other gesture owners can exclude it. (The bottom bar's swipe-to-
 * switch used to reserve it; that bar is gone, so the drawer is currently the
 * only consumer.)
 */
val SidebarEdgeZone = 32.dp

/** Horizontal fling velocity (px/s) that forces the drawer open/closed. */
private const val SidebarFlingVelocityThreshold = 300f

// ── Sidebar content dimensions ─────────────────────────────────────────

/** Inner padding of the sidebar content column. */
private val SidebarContentPaddingHorizontal = 16.dp
private val SidebarContentPaddingVertical = 14.dp

/** Settings entry: icon size. */
private val SidebarSettingsIconSize = 32.dp

/** 「新建对话」按钮：图标尺寸 / 内边距，以及与列表之间的间距。 */
private val SidebarNewConversationIconSize = 16.dp
private val SidebarNewConversationPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
private val SidebarNewConversationGap = 8.dp

/** 历史对话行：字号沿用聊天区的正文 / 说明两级。 */
private val SidebarConversationTitleFontSize = 13.5.sp
private val SidebarConversationMetaFontSize = 11.sp
private val SidebarConversationRowPaddingVertical = 11.dp
private val SidebarConversationDeleteIconSize = 16.dp

/**
 * 侧边栏里的一条历史对话。
 *
 * 这里用中性类型而不是 core:data 的 `Conversation` —— core:ui 不依赖 core:data
 * （也不该依赖），由调用方映射后传进来。
 *
 * @param subtitle 次要说明，例如产生这条对话的模型名
 */
data class SidebarConversation(
    val id: String,
    val title: String,
    val subtitle: String,
)

/**
 * Self-contained push-style sidebar drawer with gesture support.
 *
 * Signature behavior: opening the drawer does NOT overlay the host UI — it
 * physically pushes the main [content] to the right while the panel slides in
 * from the left edge. Everything for that effect lives inside this component:
 * - Push-canvas animation of [content] (plain horizontal offset)
 * - Panel slide animation anchored on the left edge
 * - Tap-outside interceptor covering only the area right of the panel,
 *   so taps on the panel itself can never collapse the drawer
 * - Gestures: rightward swipe from the left edge opens the drawer;
 *   leftward swipe anywhere closes it (velocity-aware fling + 50% settle rule)
 *
 * The open/closed state stays with the host ([isOpen]); gestures drive the
 * visual progress internally and notify the host via [onOpen]/[onClose].
 *
 * @param content main UI that gets pushed to the right when the drawer opens
 * @param conversations 历史对话列表；空列表时显示空态文案
 * @param activeConversationId 当前对话，用于高亮
 * @param onNewConversation 「新建对话」按钮（位于列表顶部）
 * @param onConversationSelected / [onConversationDeleted] 列表行的选择与删除
 */
@Composable
fun SidebarDrawer(
    isOpen: Boolean,
    currentTheme: CssVariables,
    onOpen: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    conversations: List<SidebarConversation> = emptyList(),
    activeConversationId: String? = null,
    onNewConversation: () -> Unit = {},
    onConversationSelected: (String) -> Unit = {},
    onConversationDeleted: (String) -> Unit = {},
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Drawer visibility fraction: 0f = closed, 1f = fully open. Single source
    // of truth driving BOTH the panel slide and the canvas push, whether the
    // change comes from host state or from a user drag.
    val progress = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    // While a gesture is active, rendering follows this fraction directly
    // (the gesture scope cannot call suspending Animatable APIs).
    var dragFraction by remember { mutableStateOf(0f) }
    // Target of the last settle animation that was started. Guards against a
    // host-state echo: when a gesture settle calls onOpen/onClose, the host
    // state change restarts the LaunchedEffect below; without this guard it
    // would cancel the in-flight animation and restart it at zero velocity,
    // dropping the fling's velocity continuity.
    var settleTarget by remember { mutableFloatStateOf(0f) }

    // Follow external state changes (top-bar toggle, back button, ...) unless
    // a user gesture is currently driving the drawer.
    LaunchedEffect(isOpen) {
        val target = if (isOpen) 1f else 0f
        if (!isDragging && settleTarget != target) {
            settleTarget = target
            progress.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = SidebarDrawerAnimMillis, easing = SidebarDrawerEasing)
            )
        }
    }

    val p = if (isDragging) dragFraction else progress.value
    val pushOffset = SidebarWidth * p
    val panelOffset = -SidebarWidth * (1f - p)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.card)
            .pointerInput(Unit) {
                val edgeZonePx = SidebarEdgeZone.toPx()
                val widthPx = SidebarWidth.toPx()
                val touchSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Closed: only a touch starting on the left edge may drag the
                    // drawer open (so content scrollers keep working elsewhere).
                    // Open (or mid-animation): a drag from anywhere can close it.
                    if (progress.value <= 0f && down.position.x > edgeZonePx) {
                        return@awaitEachGesture
                    }

                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var pendingX = 0f
                    var pendingY = 0f
                    var dragging = false
                    var current = down

                    while (current.pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        current = event.changes.firstOrNull() ?: break
                        val delta = current.positionChange()
                        tracker.addPosition(current.uptimeMillis, current.position)

                        if (!dragging) {
                            if (current.isConsumed) break // a child took ownership
                            pendingX += delta.x
                            pendingY += delta.y
                            if (abs(pendingX) > touchSlop || abs(pendingY) > touchSlop) {
                                if (abs(pendingX) > abs(pendingY)) {
                                    // Horizontal intent: take over the gesture.
                                    dragging = true
                                    if (!isDragging) dragFraction = progress.value
                                    isDragging = true
                                    dragFraction = (dragFraction + pendingX / widthPx).coerceIn(0f, 1f)
                                    current.consume()
                                } else {
                                    break // vertical intent: let content scrollers win
                                }
                            }
                        } else {
                            if (current.isConsumed) break
                            dragFraction = (dragFraction + delta.x / widthPx).coerceIn(0f, 1f)
                            current.consume()
                        }
                    }

                    if (dragging) {
                        val velocity = tracker.calculateVelocity().x
                        val target = when {
                            velocity > SidebarFlingVelocityThreshold -> 1f
                            velocity < -SidebarFlingVelocityThreshold -> 0f
                            else -> if (dragFraction >= 0.5f) 1f else 0f
                        }
                        val releaseFraction = dragFraction
                        // Record the settle target BEFORE notifying the host: the
                        // resulting state change restarts the LaunchedEffect above,
                        // which must see this value and skip, otherwise it would
                        // cancel the fling below and restart it at zero velocity.
                        settleTarget = target
                        scope.launch {
                            // Hand rendering back to the animation system without
                            // a visual jump: snap to the release position first.
                            progress.snapTo(releaseFraction)
                            isDragging = false
                            progress.animateTo(
                                targetValue = target,
                                animationSpec = tween(durationMillis = SidebarDrawerAnimMillis, easing = SidebarDrawerEasing),
                                initialVelocity = velocity / widthPx
                            )
                        }
                        // Idempotent: no-op when the host is already in that state.
                        if (target >= 1f) onOpen() else onClose()
                    }
                }
            }
    ) {
        // 1. Main content (directly pushed to the right when the drawer expands)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = pushOffset)
        ) {
            content()
        }

        // 2. Tap-outside interceptor: geometrically covers ONLY the area right of the
        // sidebar, so taps on the sidebar itself can never fall through to it
        // (unconsumed taps on non-interactive sidebar areas would otherwise
        // propagate down to a full-screen interceptor and collapse the drawer).
        // Active as soon as the drawer starts opening (incl. mid-drag).
        if (p > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = SidebarWidth)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose
                    )
                    .testTag("sidebar_outside_dismiss")
            )
        }

        // 3. Sliding panel (anchored on the left, drawn above the interceptor)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(SidebarWidth)
                .offset(x = panelOffset)
        ) {
            AppSidebarContent(
                currentTheme = currentTheme,
                onOpenSettings = onOpenSettings,
                onCloseDrawer = onClose,
                conversations = conversations,
                activeConversationId = activeConversationId,
                onNewConversation = onNewConversation,
                onConversationSelected = onConversationSelected,
                onConversationDeleted = onConversationDeleted,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 侧边栏内容：顶部「新建对话」+ 历史对话列表 + 底部设置入口。
 *
 * 历史对话原来挂在聊天页顶部的历史图标上、用 ModalBottomSheet 弹出；现在整体搬进
 * 侧边栏 —— 弹层会遮住聊天区，而侧边栏本来就是为导航准备的。聊天页顶部那一行
 * （当前模型 / 历史图标 / 新建对话）随之删除，聊天区因此多出约 48dp。
 *
 * The former workspace/brand header was intentionally removed.
 */
@Composable
fun AppSidebarContent(
    currentTheme: CssVariables,
    onOpenSettings: () -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    conversations: List<SidebarConversation> = emptyList(),
    activeConversationId: String? = null,
    onNewConversation: () -> Unit = {},
    onConversationSelected: (String) -> Unit = {},
    onConversationDeleted: (String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = SidebarContentPaddingHorizontal, vertical = SidebarContentPaddingVertical)
            .testTag("app_sidebar_drawer")
    ) {
        // 1. 新建对话（放在历史列表顶部）
        Button(
            onClick = {
                onNewConversation()
                onCloseDrawer()
            },
            currentTheme = currentTheme,
            fillWidth = true,
            contentPadding = SidebarNewConversationPadding,
            contentAlignment = Alignment.Center,
            testTag = "sidebar_new_conversation_btn"
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = currentTheme.foreground,
                    modifier = Modifier.size(SidebarNewConversationIconSize)
                )
                Spacer(modifier = Modifier.width(SidebarNewConversationGap))
                Text(
                    text = stringResource(R.string.sidebar_new_conversation),
                    fontSize = SidebarConversationTitleFontSize,
                    fontWeight = FontWeight.Medium,
                    color = currentTheme.foreground
                )
            }
        }

        Spacer(modifier = Modifier.height(SidebarNewConversationGap))

        // 2. 历史对话列表：占满剩余高度（空态也在这块里），超出可滚。
        //    设置入口因此始终贴在底部。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (conversations.isEmpty()) {
                Text(
                    text = stringResource(R.string.sidebar_history_empty),
                    fontSize = SidebarConversationMetaFontSize,
                    color = currentTheme.mutedForeground,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                conversations.forEach { conversation ->
                    SidebarConversationRow(
                        conversation = conversation,
                        isCurrent = conversation.id == activeConversationId,
                        currentTheme = currentTheme,
                        onSelect = {
                            onConversationSelected(conversation.id)
                            onCloseDrawer()
                        },
                        onDelete = { onConversationDeleted(conversation.id) },
                    )
                }
            }
        }

        // 3. Settings Entry (moved from the top nav bar, pinned to the bottom,
        // aligned to the right edge). Bare icon button (no chrome).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    onOpenSettings()
                    onCloseDrawer()
                },
                rippleEnabled = false,
                testTag = "sidebar_settings_btn"
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.sidebar_cd_settings),
                    tint = currentTheme.foreground,
                    modifier = Modifier.size(SidebarSettingsIconSize)
                )
            }
        }
    }
}

/**
 * 历史对话行：标题 + 次要说明（产生它的模型），右侧删除按钮。
 * 当前对话用 primary 色 + SemiBold 标出 —— 与原来 BottomSheet 里的行保持一致。
 */
@Composable
private fun SidebarConversationRow(
    conversation: SidebarConversation,
    isCurrent: Boolean,
    currentTheme: CssVariables,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Button(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        testTag = "sidebar_conversation_${conversation.id}"
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SidebarConversationRowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    fontSize = SidebarConversationTitleFontSize,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isCurrent) currentTheme.primary else currentTheme.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = conversation.subtitle,
                    fontSize = SidebarConversationMetaFontSize,
                    color = currentTheme.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onDelete,
                rippleEnabled = false,
                testTag = "sidebar_conversation_delete_${conversation.id}"
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.sidebar_cd_delete_conversation),
                    tint = currentTheme.mutedForeground,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(SidebarConversationDeleteIconSize)
                )
            }
        }
    }
}
