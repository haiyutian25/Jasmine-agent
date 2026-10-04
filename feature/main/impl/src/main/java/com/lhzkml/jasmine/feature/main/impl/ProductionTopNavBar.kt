package com.lhzkml.jasmine.feature.main.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import com.lhzkml.jasmine.core.widgets.icon.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import com.lhzkml.jasmine.core.widgets.button.Button as WidgetsButton
import com.lhzkml.jasmine.core.widgets.button.ButtonDefaults
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.CenterAlignedTopAppBar
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBarDefaults
import com.lhzkml.jasmine.core.widgets.text.Text

// ── Top nav bar dimensions ─────────────────────────────────────────────

/** Content row height (both main and sub-page shapes). */
private val ProductionTopNavBarHeight = 47.dp

/** Horizontal padding of the content row. */
private val ProductionTopNavBarPaddingHorizontal = 10.dp

/**
 * Left inset the app bar component already applies to its navigation slot
 * (upstream's `TopAppBarHorizontalPadding`). Only the difference is added
 * back below, so the action icon keeps its original 10.dp offset.
 */
private val WidgetsAppBarNavigationIconInset = 4.dp

/** Icon size of the leading action (menu / back). */
private val ProductionTopNavActionIconSize = 28.dp

/** 顶栏两个动作按钮的触控盒边长：与旧容器的最小触控尺寸同值（48.dp）。 */
private val TopNavActionButtonSize = 48.dp

// 标题字型不在这里定：交给组件下发（`AppBarSmallTokens.TitleFont` = 自有主题的
// `AppTypography.titleLarge`，22sp / Regular / 字距 0），与「设置 → 调试 → 顶部导航栏」
// 演示页里那个标题完全一致。

/** Height of the hairline divider below the bar. */
private val ProductionTopNavDividerHeight = 1.dp

/**
 * Minimal top navigation bar with two shapes:
 * - Main shape ([pageTitle] == null): sidebar toggle button on the left.
 * - Sub-page shape ([pageTitle] != null, e.g. settings flow): back button on
 *   the left + centered page title.
 * Always finished with the 1px bottom divider rule.
 *
 * 容器是 `core:widgets` 的自有 [TopAppBar] / [CenterAlignedTopAppBar]（它的主题
 * 与令牌映射都已清零），高度按原实现钉死为 [ProductionTopNavBarHeight]（47.dp）；颜色、
 * 图标、文案与底部细线照旧；唯一有意偏离原样的地方是**标题字型改由组件下发**
 * （22sp，与「设置 → 调试 → 顶部导航栏」演示页一致，原先是写死的 14.5sp Bold）。
 *
 * 放在 `feature:main:impl` 而不是原先的 `core:ui`：`core:widgets` 依赖 `core:ui`
 * （它要用 `CssVariables`），反向再依赖会成环，所以只消费方这一层才能同时看到两者。
 */
@Composable
internal fun ProductionTopNavBar(
    currentTheme: CssVariables,
    onOpenSidebar: () -> Unit = {},
    pageTitle: String? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(currentTheme.background)
    ) {
        val colors = TopAppBarDefaults.topAppBarColors(
            containerColor = currentTheme.background,
            scrolledContainerColor = currentTheme.background,
            navigationIconContentColor = currentTheme.foreground,
            titleContentColor = currentTheme.foreground,
            actionIconContentColor = currentTheme.foreground,
        )
        // 与原先的 statusBarsPadding() 等价：只吃状态栏，不吃横屏时的左右系统栏。
        val windowInsets = WindowInsets.statusBars.only(WindowInsetsSides.Vertical)

        val navigationIcon: @Composable () -> Unit = {
            Box(
                modifier = Modifier.padding(
                    start = ProductionTopNavBarPaddingHorizontal - WidgetsAppBarNavigationIconInset
                )
            ) {
                if (pageTitle != null) {
                    // 保留原有的“无外观”定制：透明底 / 无边框 / 无阴影 / 零内边距，
                    // 并固定 48dp 触控盒（与旧容器的 ButtonMinTouchTarget 同值），
                    // 只把行为容器换成 core:widgets 的自有 Button。
                    WidgetsButton(
                        onClick = { onBack() },
                        modifier = Modifier
                            .size(TopNavActionButtonSize)
                            .testTag("top_nav_back_btn"),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        elevation = null,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.top_nav_cd_back),
                            tint = currentTheme.foreground,
                            modifier = Modifier.size(ProductionTopNavActionIconSize)
                        )
                    }
                } else {
                    WidgetsButton(
                        onClick = { onOpenSidebar() },
                        modifier = Modifier
                            .size(TopNavActionButtonSize)
                            .testTag("top_nav_sidebar_btn"),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        elevation = null,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = LucideIcons.Menu,
                            contentDescription = stringResource(R.string.top_nav_cd_open_sidebar),
                            tint = currentTheme.foreground,
                            modifier = Modifier.size(ProductionTopNavActionIconSize)
                        )
                    }
                }
            }
        }

        if (pageTitle != null) {
            // Sub-page shape: back button (left) + centered title. 组件的标题是按整条宽度
            // 居中的（只有撞上导航图标时才会让位），所以与原来的 Box(Center) 一致。
            CenterAlignedTopAppBar(
                title = {
                    // 不写字号/字重/字距：由组件下发（见上面 TitleFont 那条注释），
                    // 与调试页演示的标题一致。
                    Text(
                        text = pageTitle,
                        color = currentTheme.foreground
                    )
                },
                navigationIcon = navigationIcon,
                expandedHeight = ProductionTopNavBarHeight,
                windowInsets = windowInsets,
                colors = colors
            )
        } else {
            // Main shape: sidebar toggle button (plain icon, no ripple), no title.
            TopAppBar(
                title = {},
                navigationIcon = navigationIcon,
                expandedHeight = ProductionTopNavBarHeight,
                windowInsets = windowInsets,
                colors = colors
            )
        }

        // 1px Subtle Bottom Border Rule (Linear / Vercel style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ProductionTopNavDividerHeight)
                .background(currentTheme.border)
        )
    }
}
