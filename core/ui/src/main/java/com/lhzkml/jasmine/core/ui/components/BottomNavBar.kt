package com.lhzkml.jasmine.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class NavigationTab(
    @StringRes val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    CHAT(R.string.nav_tab_chat, Icons.Filled.Home, Icons.Outlined.Home, "bottom_tab_chat"),
}

/** Page settle animation timing for swipe tab switching. */
private const val TabSwipeAnimMillis = 320
private val TabSwipeEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Horizontal fling velocity (px/s) that forces a tab change. */
private const val TabSwipeFlingVelocityThreshold = 180f

/** Fraction of a page width a slow drag must cover to switch tabs. */
private const val TabSwipeDistanceThreshold = 0.28f

// ── Tab bar dimensions ─────────────────────────────────────────────────

/** Overall tab bar height. */
private val TabBarHeight = 66.dp

/** Height of each tab's clickable pill (touch target). */
private val TabPillHeight = 54.dp

/** Height of the hairline divider above the tab bar. */
private val TabDividerHeight = 1.dp

/** Padding around the tab row. */
private val TabRowPaddingHorizontal = 8.dp
private val TabRowPaddingVertical = 4.dp

/** Inner vertical padding inside each tab pill. */
private val TabPillPaddingVertical = 1.dp

/** Tab icon size. */
private val TabIconSize = 24.dp

/** Tab label font size and letter spacing. */
private val TabLabelFontSize = 14.sp
private val TabLabelLetterSpacing = 0.2.sp
/** Tight line height applied once the label scales up, keeping it in the pill. */
private val TabLabelLineHeight = 17.sp

/** Vertical spacing between the icon and the label. */
private val TabIconLabelSpacing = 0.dp

/** Duration (ms) of the selected-state color transitions. */
private const val TabColorAnimMillis = 200

/**
 * Production bottom navigation: page content area + tab bar in one unit.
 *
 * Optional swipe-to-switch (off by default): set [swipeable] to true and the
 * hosted pages change via horizontal drag — 1:1 finger tracking, fling or
 * 50% settle rule, and external tab changes (tapping the bar) animate as a
 * slide. [swipeEnabled] suspends the gesture at runtime (e.g. while the
 * sidebar drawer is open, so drags keep closing the drawer), and
 * [excludedStartZone] keeps the left edge reserved for a host edge gesture
 * (the drawer's edge swipe).
 *
 * The bar is pinned to the window bottom and steps aside while the keyboard is up,
 * so a focused composer can sit flush against the keyboard. The page above it only
 * has to clear `WindowInsets.ime` (the bar's own height is taken by the sibling
 * node below it in the Column) — that is exactly the official `imePadding()`
 * semantics, no manual inset arithmetic.
 *
 * Bar styling follows Linear / Vercel / Material 3 standards:
 * - 66dp content height + navigationBarsPadding(); the host renders the bar
 *   flush with the screen bottom, so the background extends under the system
 *   gesture bar while the 66dp content stays above it (single inset, no gap)
 * - Micro-pill active state highlight with smooth color transitions
 * - 1px subtle top border line (CSS border token)
 * - 54dp pill touch target with accessibility content descriptions
 *
 * @param content renders the page for a given tab (hosted above the bar)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductionBottomNavBar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
    swipeable: Boolean = false,
    swipeEnabled: Boolean = true,
    excludedStartZone: Dp = 0.dp,
    content: @Composable (NavigationTab) -> Unit = {}
) {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    // When fonts are scaled up, the (sp) tab label grows taller. Tighten its
    // line box so the icon+label pair keeps fitting the fixed pill and never
    // nears the system gesture bar. Icon-label spacing stays at 0 — a negative
    // padding is deliberately avoided; the tighter line box reclaims the room.
    val labelLineHeight = if (fontScale > 1f) TabLabelLineHeight else TextUnit.Unspecified
    // 键盘弹出时把底栏收起来。
    //
    // 底栏留在布局里会占掉一个底栏的高度，输入框就永远差这一段贴不到键盘；
    // 而把它叠到内容上又会直接盖住输入框（两种都试过）。收起来之后内容让到
    // 键盘顶部，输入框正好贴在键盘上 —— 微信 / Telegram 一类聊天界面就是这个做法。
    val imeVisible = WindowInsets.isImeVisible

    Column(modifier = modifier.fillMaxSize()) {
        // 1. Page content area (optionally swipeable)
        //
        // 只让出键盘（WindowInsets.ime），底栏的高度由下面的兄弟节点自然占掉，
        // 不需要在这里再算一次 —— 这正是官方 imePadding() 的语义。
        if (swipeable) {
            SwipeableTabPages(
                currentTab = currentTab,
                onTabChange = onTabSelected,
                enabled = swipeEnabled,
                excludedStartZone = excludedStartZone,
                modifier = Modifier.weight(1f).windowInsetsPadding(WindowInsets.ime),
                content = content
            )
        } else {
            Box(modifier = Modifier.weight(1f).windowInsetsPadding(WindowInsets.ime)) {
                content(currentTab)
            }
        }

        // 2. Tab bar
        if (!imeVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(currentTheme.background)
                    .navigationBarsPadding()
            ) {
                // 1px Subtle Top Border
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TabDividerHeight)
                        .background(currentTheme.border)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TabBarHeight)
                        .padding(horizontal = TabRowPaddingHorizontal, vertical = TabRowPaddingVertical),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavigationTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab

                        val iconColor by animateColorAsState(
                            targetValue = if (isSelected) currentTheme.primary else currentTheme.mutedForeground,
                            animationSpec = tween(TabColorAnimMillis),
                            label = "tab_icon_color"
                        )

                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) currentTheme.foreground else currentTheme.mutedForeground,
                            animationSpec = tween(TabColorAnimMillis),
                            label = "tab_text_color"
                        )

                        val pillBackground by animateColorAsState(
                            targetValue = if (isSelected) currentTheme.subtleSurface else currentTheme.background.copy(alpha = 0f),
                            animationSpec = tween(TabColorAnimMillis),
                            label = "tab_pill_bg"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(TabPillHeight)
                                .clip(RoundedCornerShape(currentTheme.radiusSm))
                                .background(pillBackground)
                                .clickable {
                                    onTabSelected(tab)
                                }
                                .padding(vertical = TabPillPaddingVertical)
                                .testTag(tab.testTag),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.titleRes),
                                    tint = iconColor,
                                    modifier = Modifier.size(TabIconSize)
                                )
                                Text(
                                    text = stringResource(tab.titleRes),
                                    fontSize = TabLabelFontSize,
                                    lineHeight = labelLineHeight,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor,
                                    letterSpacing = TabLabelLetterSpacing,
                                    modifier = Modifier.padding(top = TabIconLabelSpacing)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pager-style page area of the bottom navigation (internal): horizontal
 * drags switch tabs with finger tracking; external tab changes animate as a
 * slide. Only the pages intersecting the viewport are composed (at most two).
 */
@Composable
private fun SwipeableTabPages(
    currentTab: NavigationTab,
    onTabChange: (NavigationTab) -> Unit,
    enabled: Boolean,
    excludedStartZone: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (NavigationTab) -> Unit
) {
    val tabs = NavigationTab.entries
    val tabCount = tabs.size
    val currentIndex = currentTab.ordinal
    val scope = rememberCoroutineScope()

    // Page position in index units: an integer value means settled on that
    // page. Single source of truth for the rendered page offsets, driven by
    // host state changes or by a user drag.
    val pagePosition = remember { Animatable(currentIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    // While a gesture is active, rendering follows this value directly
    // (the gesture scope cannot call suspending Animatable APIs).
    var dragPos by remember { mutableStateOf(0f) }
    // Index targeted by the last settle animation that was started. Guards
    // against a host-state echo: when a gesture settle calls onTabChange, the
    // host state change restarts the LaunchedEffect below; without this guard
    // it would cancel the in-flight animation and restart it at zero velocity,
    // dropping the fling's velocity continuity.
    var settleTarget by remember { mutableIntStateOf(currentIndex) }

    // Follow external tab changes (tab bar taps) with a slide.
    LaunchedEffect(currentIndex) {
        if (!isDragging && settleTarget != currentIndex) {
            settleTarget = currentIndex
            pagePosition.animateTo(
                targetValue = currentIndex.toFloat(),
                animationSpec = tween(durationMillis = TabSwipeAnimMillis, easing = TabSwipeEasing)
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier.pointerInput(enabled, excludedStartZone, currentIndex) {
            if (!enabled) return@pointerInput
            val excludedPx = excludedStartZone.toPx()
            val widthPx = size.width.toFloat()
            val touchSlop = viewConfiguration.touchSlop
            val maxPos = (tabCount - 1).toFloat()

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Leave the left edge zone to the host edge gesture (drawer).
                if (down.position.x <= excludedPx) return@awaitEachGesture

                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var pendingX = 0f
                var pendingY = 0f
                var dragging = false
                var anchor = 0f
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
                                if (!isDragging) dragPos = pagePosition.value
                                anchor = dragPos
                                isDragging = true
                                dragPos = (dragPos - pendingX / widthPx).coerceIn(0f, maxPos)
                                current.consume()
                            } else {
                                break // vertical intent: let page scrollers win
                            }
                        }
                    } else {
                        if (current.isConsumed) break
                        dragPos = (dragPos - delta.x / widthPx).coerceIn(0f, maxPos)
                        current.consume()
                    }
                }

                if (dragging) {
                    val velocity = tracker.calculateVelocity().x
                    val target = when {
                        velocity < -TabSwipeFlingVelocityThreshold ->
                            (floor(dragPos).toInt() + 1).coerceAtMost(tabCount - 1)
                        velocity > TabSwipeFlingVelocityThreshold ->
                            (ceil(dragPos).toInt() - 1).coerceAtLeast(0)
                        else -> {
                            // Slow release: switch once the drag covers at least
                            // TabSwipeDistanceThreshold of a page width (either
                            // direction), otherwise snap back to the start page.
                            val startPage = round(anchor).toInt()
                            val displacement = dragPos - anchor
                            if (abs(displacement) >= TabSwipeDistanceThreshold) {
                                startPage + if (displacement > 0) 1 else -1
                            } else {
                                startPage
                            }.coerceIn(0, tabCount - 1)
                        }
                    }
                    val releasePos = dragPos
                    // Record the settle target BEFORE notifying the host: the
                    // resulting tab change restarts the LaunchedEffect above,
                    // which must see this value and skip, otherwise it would
                    // cancel the fling below and restart it at zero velocity.
                    settleTarget = target
                    scope.launch {
                        // Hand rendering back to the animation system without
                        // a visual jump: snap to the release position first.
                        pagePosition.snapTo(releasePos)
                        isDragging = false
                        pagePosition.animateTo(
                            targetValue = target.toFloat(),
                            animationSpec = tween(durationMillis = TabSwipeAnimMillis, easing = TabSwipeEasing),
                            initialVelocity = velocity / widthPx
                        )
                    }
                    if (target != currentIndex) onTabChange(tabs[target])
                }
            }
        }
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val pos = if (isDragging) dragPos else pagePosition.value

        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            val first = floor(pos).toInt().coerceIn(0, tabCount - 1)
            val last = if (pos > first && first < tabCount - 1) first + 1 else first
            for (index in first..last) {
                key(index) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(((index - pos) * widthPx).roundToInt(), 0) }
                    ) {
                        content(tabs[index])
                    }
                }
            }
        }
    }
}
