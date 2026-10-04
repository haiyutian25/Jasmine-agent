// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/SearchViewTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object SearchViewTokens {
    val ContainerColor: (CssVariables) -> Color = { it.surfaceContainerHigh }
    val ContainerElevation = ElevationTokens.Level3
    val DividerColor: (CssVariables) -> Color = { it.border }
    val DockedContainerShape = ShapeKeyTokens.CornerExtraLarge
    val DockedHeaderContainerHeight = 56.0.dp
    val FullScreenContainerShape = ShapeKeyTokens.CornerNone
    val FullScreenHeaderContainerHeight = 72.0.dp
    val HeaderInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val HeaderInputTextFont = AppTypography.bodyLarge
    val HeaderLeadingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val HeaderSupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val HeaderSupportingTextFont = AppTypography.bodyLarge
    val HeaderTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
}
