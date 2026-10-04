// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：androidx/compose/material3/tokens/SecondaryNavigationTabTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object SecondaryNavigationTabTokens {
    val ActiveLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val ContainerColor: (CssVariables) -> Color = { it.surface }
    val ContainerElevation = ElevationTokens.Level0
    val ContainerHeight = 48.0.dp
    val ContainerShape = ShapeKeyTokens.CornerNone
    val DividerColor: (CssVariables) -> Color = { it.surfaceVariant }
    val DividerHeight = 1.0.dp
    val FocusLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val HoverLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val InactiveLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val LabelTextFont = AppTypography.titleSmall
    val PressedLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val ActiveIconColor: (CssVariables) -> Color = { it.cardForeground }
    val FocusIconColor: (CssVariables) -> Color = { it.cardForeground }
    val HoverIconColor: (CssVariables) -> Color = { it.cardForeground }
    val IconSize = 24.0.dp
    val InactiveIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val PressedIconColor: (CssVariables) -> Color = { it.cardForeground }
}
