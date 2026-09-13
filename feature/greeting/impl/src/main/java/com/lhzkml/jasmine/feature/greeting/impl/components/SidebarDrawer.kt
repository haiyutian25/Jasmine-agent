package com.lhzkml.jasmine.feature.greeting.impl.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.greeting.impl.R
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Sidebar width; single source of truth for the panel and the push offset. */
val SidebarWidth: Dp = 295.dp

/** Drawer animation timing shared by the panel slide and the canvas push. */
const val SidebarDrawerAnimMillis = 320
val SidebarDrawerEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Left-edge zone (in dp) where a rightward swipe can open the closed drawer.
 * Exposed so other gesture owners (e.g. [SwipeTabContainer]) can exclude it.
 */
val SidebarEdgeZone = 32.dp

/** Horizontal fling velocity (px/s) that forces the drawer open/closed. */
private const val SidebarFlingVelocityThreshold = 300f

// ── Sidebar content dimensions ─────────────────────────────────────────

/** Inner padding of the sidebar content column. */
private val SidebarContentPaddingHorizontal = 16.dp
private val SidebarContentPaddingVertical = 14.dp

/** Workspace header: avatar, texts and spacing. */
private val SidebarAvatarSize = 34.dp
private val SidebarAvatarLetterFontSize = 17.sp
private val SidebarHeaderSpacing = 10.dp
private val SidebarWorkspaceNameFontSize = 14.sp
private val SidebarNameBadgeSpacing = 6.dp
private val SidebarProBadgeFontSize = 8.5.sp
private val SidebarProBadgeCornerRadius = 3.dp
private val SidebarProBadgePaddingHorizontal = 4.dp
private val SidebarProBadgePaddingVertical = 1.dp
private val SidebarSubtitleFontSize = 11.sp

/** Settings entry: icon size. */
private val SidebarSettingsIconSize = 32.dp

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
 */
@Composable
fun SidebarDrawer(
    isOpen: Boolean,
    currentTheme: CssVariables,
    onOpen: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
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
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Minimal sidebar content:
 * - Workspace / Profile header (top)
 * - Settings entry pinned to the bottom (moved here from the top nav bar)
 */
@Composable
fun AppSidebarContent(
    currentTheme: CssVariables,
    onOpenSettings: () -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
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
        // 1. Workspace / Profile Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SidebarHeaderSpacing)
        ) {
            // Workspace Avatar
            Box(
                modifier = Modifier
                    .size(SidebarAvatarSize)
                    .clip(RoundedCornerShape(currentTheme.radiusSm))
                    .background(currentTheme.subtleSurface)
                    .border(1.dp, currentTheme.border, RoundedCornerShape(currentTheme.radiusSm)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "J",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = SidebarAvatarLetterFontSize,
                    fontWeight = FontWeight.Bold,
                    color = currentTheme.foreground
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SidebarNameBadgeSpacing)
                ) {
                    Text(
                        text = "Jasmine Studio",
                        fontSize = SidebarWorkspaceNameFontSize,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.foreground
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(SidebarProBadgeCornerRadius))
                            .background(currentTheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = SidebarProBadgePaddingHorizontal, vertical = SidebarProBadgePaddingVertical)
                    ) {
                        Text(
                            text = "PRO",
                            fontSize = SidebarProBadgeFontSize,
                            fontWeight = FontWeight.Bold,
                            color = currentTheme.primary
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.sidebar_subtitle),
                    fontSize = SidebarSubtitleFontSize,
                    color = currentTheme.mutedForeground
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 2. Settings Entry (moved from the top nav bar, pinned to the bottom,
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
