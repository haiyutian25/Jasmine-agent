// 本项目自有的组件代码（移植自上游的 Scaffold.kt 后自行维护），不再跟随上游生成，可直接改。
// 上游位置：Scaffold.kt
// 与上游的差异（其余逐行一致）：
//   * 默认 containerColor：上游主题的背景色 -> LocalCssVariables.current.background
//   * 默认 contentColor：contentColorFor(...) -> 自有的 CssVariables.contentColorFor(...)
//   * 容器：自有的 Surface（不再经 上游的 Surface）
//   * MutableWindowInsets：上游取自 上游 internal（foundation 里那份是 experimental），
//     这里在本文件内自带一份同名实现（逐行一致）。
//   * ScaffoldDefaults.contentWindowInsets：上游是 expect/actual，Android 侧为
//     `systemBars.union(displayCutout)`，这里直接取同一个值。
/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lhzkml.jasmine.core.widgets.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.lhzkml.jasmine.core.ui.theme.LocalCssVariables
import com.lhzkml.jasmine.core.ui.theme.LocalWidgetsContentColor
import com.lhzkml.jasmine.core.ui.theme.contentColorFor
import com.lhzkml.jasmine.core.widgets.surface.Surface

/**
 * Scaffold 实现基础的界面布局骨架：把应用的若干部件（顶栏 / 底栏 / 提示条 / FAB / 正文）按既定的
 * 层叠顺序摆好，并把计算出的 [PaddingValues] 交给正文，让正文自己避开这些横条。
 *
 * （自有移植版：[topBar] / [bottomBar] / [snackbarHost] / [floatingActionButton] 与上游同名同义。）
 *
 * @param modifier 作用在 Scaffold 上的修饰符
 * @param topBar 顶部栏
 * @param bottomBar 底部栏
 * @param snackbarHost 承载提示条的槽位
 * @param floatingActionButton 主要动作按钮
 * @param floatingActionButtonPosition FAB 的位置
 * @param containerColor 背景色，传 [Color.Transparent] 表示不画底色
 * @param contentColor 内容的推荐前景色；[containerColor] 不是主题色时回落到当前的 content color
 * @param contentWindowInsets 通过 [PaddingValues] 交给 [content] 的窗口 inset。只有当 [topBar] /
 *   [bottomBar] 不存在时，Scaffold 才会自己去考虑上/下的 inset（存在时由它们各自负责）。
 *   被父布局或其它 inset 修饰符消费掉的 inset 会从 [contentWindowInsets] 中扣除。
 * @param content 正文；拿到的 [PaddingValues] 应通过 [Modifier.padding] 与
 *   [Modifier.consumeWindowInsets] 施加到内容根上。若内容要竖向滚动，这个 padding 应加在滚动
 *   内容的子项上，而不是滚动容器本身上。
 */
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = LocalCssVariables.current.background,
    contentColor: Color =
        LocalCssVariables.current.contentColorFor(containerColor)
            .takeOrElse { LocalWidgetsContentColor.current },
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    val safeInsets = remember(contentWindowInsets) { MutableWindowInsets(contentWindowInsets) }
    Surface(
        modifier =
            modifier.onConsumedWindowInsetsChanged { consumedWindowInsets ->
                // 把已被消费掉的窗口 inset 从调用方传进来的 contentWindowInsets 里扣掉
                safeInsets.insets = contentWindowInsets.exclude(consumedWindowInsets)
            },
        color = containerColor,
        contentColor = contentColor,
    ) {
        ScaffoldLayout(
            fabPosition = floatingActionButtonPosition,
            topBar = topBar,
            bottomBar = bottomBar,
            content = content,
            snackbar = snackbarHost,
            contentWindowInsets = safeInsets,
            fab = floatingActionButton,
        )
    }
}

/**
 * [Scaffold] 的布局实现。
 *
 * @param fabPosition FAB 的位置（存在时）
 * @param topBar 顶部的内容
 * @param content 主体
 * @param snackbar 提示条（画在 [content] 之上）
 * @param fab FAB（画在提示条之下、底栏之上）
 * @param bottomBar 底部的内容
 */
@Composable
private fun ScaffoldLayout(
    fabPosition: FabPosition,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
    snackbar: @Composable () -> Unit,
    fab: @Composable () -> Unit,
    contentWindowInsets: WindowInsets,
    bottomBar: @Composable () -> Unit,
) {
    // content padding 的载体：测量期间更新，且在 subcompose 正文之前就写好；用单个
    // remembered 的 PaddingValues 承载，值变化时不必重组。
    val contentPadding = remember {
        object : PaddingValues {
            var paddingHolder by mutableStateOf(PaddingValues(0.dp))

            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                paddingHolder.calculateLeftPadding(layoutDirection)

            override fun calculateTopPadding(): Dp = paddingHolder.calculateTopPadding()

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                paddingHolder.calculateRightPadding(layoutDirection)

            override fun calculateBottomPadding(): Dp = paddingHolder.calculateBottomPadding()
        }
    }

    val topBarContent: @Composable () -> Unit = remember(topBar) { { Box { topBar() } } }
    val snackbarContent: @Composable () -> Unit = remember(snackbar) { { Box { snackbar() } } }
    val fabContent: @Composable () -> Unit = remember(fab) { { Box { fab() } } }
    val bodyContent: @Composable () -> Unit =
        remember(content, contentPadding) { { Box { content(contentPadding) } } }
    val bottomBarContent: @Composable () -> Unit = remember(bottomBar) { { Box { bottomBar() } } }
    SubcomposeLayout { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        // 提示条与 FAB 只考虑底部与左右
        val leftInset = contentWindowInsets.getLeft(this@SubcomposeLayout, layoutDirection)
        val rightInset = contentWindowInsets.getRight(this@SubcomposeLayout, layoutDirection)
        val bottomInset = contentWindowInsets.getBottom(this@SubcomposeLayout)

        val topBarPlaceable =
            subcompose(ScaffoldLayoutContent.TopBar, topBarContent)
                .first()
                .measure(looseConstraints)

        val snackbarPlaceable =
            subcompose(ScaffoldLayoutContent.Snackbar, snackbarContent)
                .first()
                .measure(looseConstraints.offset(-leftInset - rightInset, -bottomInset))

        val fabPlaceable =
            subcompose(ScaffoldLayoutContent.Fab, fabContent)
                .first()
                .measure(looseConstraints.offset(-leftInset - rightInset, -bottomInset))

        val isFabEmpty = fabPlaceable.width == 0 && fabPlaceable.height == 0
        val fabPlacement =
            if (!isFabEmpty) {
                val fabWidth = fabPlaceable.width
                val fabHeight = fabPlaceable.height
                // FAB 距布局左边的距离，已考虑 LTR / RTL
                val fabLeftOffset =
                    when (fabPosition) {
                        FabPosition.Start -> {
                            if (layoutDirection == LayoutDirection.Ltr) {
                                FabSpacing.roundToPx() + leftInset
                            } else {
                                layoutWidth - FabSpacing.roundToPx() - fabWidth - rightInset
                            }
                        }
                        FabPosition.End,
                        FabPosition.EndOverlay -> {
                            if (layoutDirection == LayoutDirection.Ltr) {
                                layoutWidth - FabSpacing.roundToPx() - fabWidth - rightInset
                            } else {
                                FabSpacing.roundToPx() + leftInset
                            }
                        }
                        else -> (layoutWidth - fabWidth + leftInset - rightInset) / 2
                    }

                FabPlacement(left = fabLeftOffset, width = fabWidth, height = fabHeight)
            } else {
                null
            }

        val bottomBarPlaceable =
            subcompose(ScaffoldLayoutContent.BottomBar, bottomBarContent)
                .first()
                .measure(looseConstraints)

        val isBottomBarEmpty = bottomBarPlaceable.width == 0 && bottomBarPlaceable.height == 0

        val fabOffsetFromBottom =
            fabPlacement?.let {
                if (isBottomBarEmpty || fabPosition == FabPosition.EndOverlay) {
                    it.height +
                        FabSpacing.roundToPx() +
                        contentWindowInsets.getBottom(this@SubcomposeLayout)
                } else {
                    // 总高 = 底栏高 + FAB 高 + 两者间距
                    bottomBarPlaceable.height + it.height + FabSpacing.roundToPx()
                }
            }

        val snackbarHeight = snackbarPlaceable.height
        val snackbarOffsetFromBottom =
            if (snackbarHeight != 0) {
                snackbarHeight +
                    (fabOffsetFromBottom
                        ?: bottomBarPlaceable.height.takeIf { !isBottomBarEmpty }
                        ?: contentWindowInsets.getBottom(this@SubcomposeLayout))
            } else {
                0
            }

        // subcompose 正文之前先更新 padding 载体
        val insets = contentWindowInsets.asPaddingValues(this)
        contentPadding.paddingHolder =
            PaddingValues(
                top =
                    if (topBarPlaceable.width == 0 && topBarPlaceable.height == 0) {
                        insets.calculateTopPadding()
                    } else {
                        topBarPlaceable.height.toDp()
                    },
                bottom =
                    if (isBottomBarEmpty) {
                        insets.calculateBottomPadding()
                    } else {
                        bottomBarPlaceable.height.toDp()
                    },
                start = insets.calculateStartPadding(layoutDirection),
                end = insets.calculateEndPadding(layoutDirection),
            )

        val bodyContentPlaceable =
            subcompose(ScaffoldLayoutContent.MainContent, bodyContent)
                .first()
                .measure(looseConstraints)

        layout(layoutWidth, layoutHeight) {
            // 放置顺序决定绘制顺序，与上游各部件默认的海拔一致
            bodyContentPlaceable.place(0, 0)
            topBarPlaceable.place(0, 0)
            snackbarPlaceable.place(
                (layoutWidth - snackbarPlaceable.width +
                    contentWindowInsets.getLeft(this@SubcomposeLayout, layoutDirection) -
                    contentWindowInsets.getRight(this@SubcomposeLayout, layoutDirection)) / 2,
                layoutHeight - snackbarOffsetFromBottom,
            )
            // 底栏始终贴底
            bottomBarPlaceable.place(0, layoutHeight - (bottomBarPlaceable.height))
            // 这里刻意不用 placeRelative：leftOffset 已经自己考虑了 RTL
            fabPlacement?.let { placement ->
                fabPlaceable.place(placement.left, layoutHeight - fabOffsetFromBottom!!)
            }
        }
    }
}

/** [Scaffold] 的默认值。 */
object ScaffoldDefaults {
    /**
     * 交由 Scaffold 内容槽使用并消费的默认 inset。
     *
     * 上游此处是 expect/actual，Android 侧取 `systemBars.union(displayCutout)`，这里取同一个值。
     */
    val contentWindowInsets: WindowInsets
        @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)
}

/** 挂在 [Scaffold] 上的 FAB 可能出现的位置。 */
@kotlin.jvm.JvmInline
value class FabPosition internal constructor(@Suppress("unused") private val value: Int) {
    companion object {
        /** 贴底靠前（存在底栏时在其上方）。 */
        val Start = FabPosition(0)

        /** 贴底居中（存在底栏时在其上方）。 */
        val Center = FabPosition(1)

        /** 贴底靠后（存在底栏时在其上方）。 */
        val End = FabPosition(2)

        /** 贴底靠后并覆盖在底栏之上（存在底栏时）。 */
        val EndOverlay = FabPosition(3)
    }

    override fun toString(): String {
        return when (this) {
            Start -> "FabPosition.Start"
            Center -> "FabPosition.Center"
            End -> "FabPosition.End"
            else -> "FabPosition.EndOverlay"
        }
    }
}

/**
 * [Scaffold] 内部 FAB 的放置信息。
 *
 * @property left FAB 距底栏左边的偏移，已按 RTL 调整
 * @property width FAB 的宽
 * @property height FAB 的高
 */
@Immutable private class FabPlacement(val left: Int, val width: Int, val height: Int)

// FAB 距底栏 / Scaffold 底部的间距
private val FabSpacing = 16.dp

private enum class ScaffoldLayoutContent {
    TopBar,
    MainContent,
    Snackbar,
    Fab,
    BottomBar,
}

/**
 * 取值可变的 [WindowInsets]，且实例本身不变，从而避免 [WindowInsets] 变化引起重组。
 *
 * 上游取自 上游 internal 的同名实现（foundation 里那份被标为 experimental，不能跨模块使用），
 * 这里自带一份，逐行一致。
 */
private class MutableWindowInsets(initialInsets: WindowInsets = WindowInsets(0, 0, 0, 0)) :
    WindowInsets {
    /**
     * [left][getLeft]、[top][getTop]、[right][getRight]、[bottom][getBottom] 实际使用的
     * [WindowInsets]。
     */
    var insets by mutableStateOf(initialInsets)

    override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int =
        insets.getLeft(density, layoutDirection)

    override fun getTop(density: Density): Int = insets.getTop(density)

    override fun getRight(density: Density, layoutDirection: LayoutDirection): Int =
        insets.getRight(density, layoutDirection)

    override fun getBottom(density: Density): Int = insets.getBottom(density)
}
