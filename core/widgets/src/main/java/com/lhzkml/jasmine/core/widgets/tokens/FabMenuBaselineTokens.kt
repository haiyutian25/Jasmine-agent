// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：androidx/compose/material3/tokens/FabMenuBaselineTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object FabMenuBaselineTokens {
    val CloseButtonBetweenSpace = 8.0.dp
    val CloseButtonContainerElevation = ElevationTokens.Level3
    val CloseButtonContainerHeight = 56.0.dp
    val CloseButtonContainerShape = ShapeKeyTokens.CornerFull
    val CloseButtonContainerWidth = 56.0.dp
    val CloseButtonIconSize = 20.0.dp
    val ListItemBetweenSpace = 4.0.dp
    val ListItemContainerElevation = ElevationTokens.Level3
    val ListItemContainerHeight = 56.0.dp
    val ListItemContainerShape = ShapeKeyTokens.CornerFull
    val ListItemIconLabelSpace = 8.0.dp
    val ListItemIconSize = 24.0.dp
    val ListItemLeadingSpace = 24.0.dp
    val ListItemTrailingSpace = 24.0.dp
}
