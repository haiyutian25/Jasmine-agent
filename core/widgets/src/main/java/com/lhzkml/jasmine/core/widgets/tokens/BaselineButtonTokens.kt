// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：androidx/compose/material3/tokens/BaselineButtonTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object BaselineButtonTokens {
    val ContainerColor: (CssVariables) -> Color = { it.primary }
    val ContainerElevation = ElevationTokens.Level0
    val ContainerHeight = 40.0.dp
    val ContainerShapeRound = ShapeKeyTokens.CornerFull
    val ContainerShapeSquare = ShapeKeyTokens.CornerMedium
    val DisabledContainerColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledContainerElevation = ElevationTokens.Level0
    val DisabledContainerOpacity = 0.1f
    val DisabledIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val DisabledIconOpacity = 0.38f
    val DisabledLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val DisabledLabelTextOpacity = 0.38f
    val FocusedContainerElevation = ElevationTokens.Level0
    val FocusedIconColor: (CssVariables) -> Color = { it.primaryForeground }
    val FocusedLabelTextColor: (CssVariables) -> Color = { it.primaryForeground }
    val HoveredContainerElevation = ElevationTokens.Level1
    val HoveredIconColor: (CssVariables) -> Color = { it.primaryForeground }
    val HoveredLabelTextColor: (CssVariables) -> Color = { it.primaryForeground }
    val IconColor: (CssVariables) -> Color = { it.primaryForeground }
    val IconLabelSpace = 8.0.dp
    val IconSize = 20.0.dp
    val LabelTextColor: (CssVariables) -> Color = { it.primaryForeground }
    val LabelTextSelectedColor: (CssVariables) -> Color = { it.primaryForeground }
    val LabelTextUnselectedColor: (CssVariables) -> Color = { it.mutedForeground }
    val LeadingSpace = 24.0.dp
    val PressedContainerElevation = ElevationTokens.Level0
    val PressedContainerShape = ShapeKeyTokens.CornerSmall
    val PressedIconColor: (CssVariables) -> Color = { it.primaryForeground }
    val PressedLabelTextColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedContainerColor: (CssVariables) -> Color = { it.primary }
    val SelectedContainerShapeRound = ShapeKeyTokens.CornerFull
    val SelectedContainerShapeSquare = ShapeKeyTokens.CornerMedium
    val SelectedFocusedIconColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedFocusedLabelTextColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedHoveredIconColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedHoveredLabelTextColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedIconColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedPressedIconColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedPressedLabelTextColor: (CssVariables) -> Color = { it.primaryForeground }
    val TrailingSpace = 24.0.dp
    val UnselectedContainerColor: (CssVariables) -> Color = { it.surfaceContainer }
    val UnselectedFocusedIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedFocusedLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedHoveredIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedHoveredLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedPressedIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedPressedLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
}
