// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：androidx/compose/material3/tokens/SmallIconButtonTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object SmallIconButtonTokens {
    val ContainerHeight = 40.0.dp
    val ContainerShapeRound = ShapeKeyTokens.CornerFull
    val ContainerShapeSquare = ShapeKeyTokens.CornerMedium
    val DefaultLeadingSpace = 8.0.dp
    val DefaultTrailingSpace = 8.0.dp
    val IconSize = 24.0.dp
    val NarrowLeadingSpace = 4.0.dp
    val NarrowTrailingSpace = 4.0.dp
    val OutlinedOutlineWidth = 1.0.dp
    val PressedContainerShape = ShapeKeyTokens.CornerSmall
    val SelectedContainerShapeRound = ShapeKeyTokens.CornerMedium
    val SelectedContainerShapeSquare = ShapeKeyTokens.CornerFull
    val WideLeadingSpace = 14.0.dp
    val WideTrailingSpace = 14.0.dp
}
