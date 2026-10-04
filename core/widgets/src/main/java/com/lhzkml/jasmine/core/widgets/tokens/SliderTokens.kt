// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：androidx/compose/material3/tokens/SliderTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object SliderTokens {
    val ActiveContainerOpacity = 1.0f
    val ActiveHandleHeight = 44.0.dp
    val ActiveHandleLeadingSpace = 6.0.dp
    val ActiveHandlePadding = 6.0.dp
    val ActiveHandleShape = ShapeKeyTokens.CornerFull
    val ActiveHandleTrailingSpace = 6.0.dp
    val ActiveHandleWidth = 4.0.dp
    val ActiveTrackColor: (CssVariables) -> Color = { it.primary }
    val ActiveTrackHeight = 16.0.dp
    val ActiveTrackShape = ShapeKeyTokens.CornerFull
    val ActiveTrackShapeLeading = ShapeKeyTokens.CornerFull
    val DisabledActiveTrackColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledActiveTrackOpacity = 0.38f
    val DisabledHandleColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledHandleOpacity = 0.38f
    val DisabledHandleWidth = 4.0.dp
    val DisabledInactiveTrackColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledInactiveTrackOpacity = 0.12f
    val DisabledStopColor: (CssVariables) -> Color = { it.cardForeground }
    val FocusActiveTrackColor: (CssVariables) -> Color = { it.primary }
    val FocusHandleWidth = 2.0.dp
    val FocusInactiveTrackColor: (CssVariables) -> Color = { it.secondaryContainer }
    val FocusStopColor: (CssVariables) -> Color = { it.primary }
    val HandleColor: (CssVariables) -> Color = { it.primary }
    val HandleHeight = 44.0.dp
    val HandleShape = ShapeKeyTokens.CornerFull
    val HandleWidth = 4.0.dp
    val HoverHandleColor: (CssVariables) -> Color = { it.primary }
    val HoverHandleWidth = 4.0.dp
    val HoverStopColor: (CssVariables) -> Color = { it.primary }
    val InactiveContainerOpacity = 1.0f
    val InactiveTrackColor: (CssVariables) -> Color = { it.secondaryContainer }
    val InactiveTrackHeight = 16.0.dp
    val InactiveTrackShape = ShapeKeyTokens.CornerFull
    val LabelContainerColor: (CssVariables) -> Color = { it.primary }
    val LabelTextColor: (CssVariables) -> Color = { it.inverseOnSurface }
    val PressedActiveTrackColor: (CssVariables) -> Color = { it.primary }
    val PressedHandleColor: (CssVariables) -> Color = { it.primary }
    val PressedHandleWidth = 2.0.dp
    val PressedInactiveTrackColor: (CssVariables) -> Color = { it.secondaryContainer }
    val PressedStopColor: (CssVariables) -> Color = { it.primary }
    val SliderActiveHandleColor: (CssVariables) -> Color = { it.primary }
    val StopIndicatorColor: (CssVariables) -> Color = { it.secondaryContainer }
    val StopIndicatorColorSelected: (CssVariables) -> Color = { it.secondaryContainer }
    val StopIndicatorShape = ShapeKeyTokens.CornerFull
    val StopIndicatorSize = 4.0.dp
    val StopIndicatorTrailingSpace = 6.0.dp
    val ValueIndicatorActiveBottomSpace = 12.0.dp
    val ValueIndicatorContainerColor: (CssVariables) -> Color = { it.inverseSurface }
    val ValueIndicatorLabelTextColor: (CssVariables) -> Color = { it.inverseOnSurface }
    val ValueIndicatorLabelTextFont = AppTypography.labelLarge
}
