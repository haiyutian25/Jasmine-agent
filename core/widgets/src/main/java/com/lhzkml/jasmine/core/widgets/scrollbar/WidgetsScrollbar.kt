package com.lhzkml.jasmine.core.widgets.scrollbar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.LocalCssVariables

/**
 * 照 ZCode 的口径做的纵向滚动条（上游 `styles.css` 里那四条全局滚动条规则）。
 *
 * 上游原文与对应关系：
 * ```
 * *::-webkit-scrollbar            { width: 14px; height: 14px; }   -> [TrackWidth]
 * *::-webkit-scrollbar-track      { background: transparent; }     -> 轨道不画
 * *::-webkit-scrollbar-thumb      {
 *     min-height: 32px; min-width: 32px;                            -> [MinThumbLength]
 *     border: 3px solid transparent;                                -> [ThumbInset]（把拇指内缩）
 *     border-radius: 9999px;                                        -> 全圆角
 *     background: var(--color-border);                              -> [CssVariables.zcode].border
 *     background-clip: padding-box;
 *   }
 * ```
 * 上游这条规则**用的是半透明的 `--color-border`**，所以这里也保留 alpha：
 * 滚动条压在内容上时颜色会跟着底走，而不是一条固定灰。
 *
 * 与上游的一处必要偏差：CSS 的 `border: 3px solid transparent` 是把拇指**内缩** 3px，
 * Compose 没有等价的"透明边框"写法，用左右各 3dp 的空白代替（视觉等价）。
 */
object WidgetsScrollbarDefaults {
    /** `::-webkit-scrollbar { width: 14px }`。 */
    val TrackWidth: Dp = 14.dp

    /** `min-height: 32px`。 */
    val MinThumbLength: Dp = 32.dp

    /** `border: 3px solid transparent` —— 拇指与轨道边缘之间留 3dp。 */
    val ThumbInset: Dp = 3.dp
}

/**
 * 把纵向滚动条画在 [content] 的右侧。
 *
 * 用法：
 * ```
 * val scroll = rememberScrollState()
 * WidgetsScrollbar(scrollState = scroll) {
 *     Column(Modifier.verticalScroll(scroll)) { … }
 * }
 * ```
 */
@Composable
fun WidgetsScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    theme: CssVariables = LocalCssVariables.current,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        content()

        val thumbColor = theme.zcode.border
        val density = LocalDensity.current
        val trackWidthPx = with(density) { WidgetsScrollbarDefaults.TrackWidth.toPx() }
        val minThumbPx = with(density) { WidgetsScrollbarDefaults.MinThumbLength.toPx() }
        val insetPx = with(density) { WidgetsScrollbarDefaults.ThumbInset.toPx() }

        val viewport = scrollState.viewportSize
        val contentSize = scrollState.maxValue + viewport
        // 内容没超出视口（或尺寸还没测量出来）就不画 —— 与浏览器一致：没有可滚动的量就没有条。
        if (viewport <= 0 || contentSize <= viewport) return@Box

        val trackHeight = viewport.toFloat()
        val thumbHeightPx = (trackHeight * viewport / contentSize).coerceAtLeast(minThumbPx)
        val maxThumbOffset = (trackHeight - thumbHeightPx).coerceAtLeast(0f)
        val thumbOffsetPx =
            if (scrollState.maxValue > 0) {
                maxThumbOffset * (scrollState.value.toFloat() / scrollState.maxValue)
            } else {
                0f
            }

        val thumbHeight = with(density) { thumbHeightPx.toDp() }
        val thumbOffset = with(density) { thumbOffsetPx.toDp() }
        val thumbWidth = with(density) { (trackWidthPx - insetPx * 2).coerceAtLeast(0f).toDp() }

        Box(
            modifier = Modifier
                .offset(x = (WidgetsScrollbarDefaults.TrackWidth - thumbWidth) / 2, y = thumbOffset)
                .width(thumbWidth)
                .height(thumbHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(thumbColor)
        )
    }
}
