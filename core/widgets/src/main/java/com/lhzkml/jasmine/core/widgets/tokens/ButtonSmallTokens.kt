// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/ButtonSmallTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object ButtonSmallTokens {
    val ContainerHeight = 40.0.dp
    val ContainerShapeRound = ShapeKeyTokens.CornerFull
    val ContainerShapeSquare = ShapeKeyTokens.CornerMedium
    val IconLabelSpace = 8.0.dp
    val IconSize = 20.0.dp
    val LeadingSpace = 16.0.dp
    val OutlinedOutlineWidth = 1.0.dp
    val PressedContainerShape = ShapeKeyTokens.CornerSmall
    val SelectedContainerShapeRound = ShapeKeyTokens.CornerFull
    val SelectedContainerShapeSquare = ShapeKeyTokens.CornerMedium
    val TrailingSpace = 16.0.dp
}
