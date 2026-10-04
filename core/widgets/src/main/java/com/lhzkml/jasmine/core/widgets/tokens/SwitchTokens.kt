// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：androidx/compose/material3/tokens/SwitchTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object SwitchTokens {
    val DisabledSelectedHandleColor: (CssVariables) -> Color = { it.surface }
    val DisabledSelectedHandleOpacity = 1.0f
    val DisabledSelectedIconColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledSelectedIconOpacity = 0.38f
    val DisabledSelectedTrackColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledTrackOpacity = 0.12f
    val DisabledUnselectedHandleColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledUnselectedHandleOpacity = 0.38f
    val DisabledUnselectedIconColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val DisabledUnselectedIconOpacity = 0.38f
    val DisabledUnselectedTrackColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val DisabledUnselectedTrackOutlineColor: (CssVariables) -> Color = { it.cardForeground }
    val FocusIndicatorColor: (CssVariables) -> Color = { it.secondary }
    val HandleShape = ShapeKeyTokens.CornerFull
    val PressedHandleHeight = 28.0.dp
    val PressedHandleWidth = 28.0.dp
    val SelectedFocusHandleColor: (CssVariables) -> Color = { it.primaryContainer }
    val SelectedFocusIconColor: (CssVariables) -> Color = { it.onPrimaryContainer }
    val SelectedFocusTrackColor: (CssVariables) -> Color = { it.primary }
    val SelectedHandleColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedHandleHeight = 24.0.dp
    val SelectedHandleWidth = 24.0.dp
    val SelectedHoverHandleColor: (CssVariables) -> Color = { it.primaryContainer }
    val SelectedHoverIconColor: (CssVariables) -> Color = { it.onPrimaryContainer }
    val SelectedHoverTrackColor: (CssVariables) -> Color = { it.primary }
    val SelectedIconColor: (CssVariables) -> Color = { it.onPrimaryContainer }
    val SelectedIconSize = 16.0.dp
    val SelectedPressedHandleColor: (CssVariables) -> Color = { it.primaryContainer }
    val SelectedPressedIconColor: (CssVariables) -> Color = { it.onPrimaryContainer }
    val SelectedPressedTrackColor: (CssVariables) -> Color = { it.primary }
    val SelectedTrackColor: (CssVariables) -> Color = { it.primary }
    val StateLayerShape = ShapeKeyTokens.CornerFull
    val StateLayerSize = 40.0.dp
    val TrackHeight = 32.0.dp
    val TrackOutlineWidth = 2.0.dp
    val TrackShape = ShapeKeyTokens.CornerFull
    val TrackWidth = 52.0.dp
    val UnselectedFocusHandleColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedFocusIconColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val UnselectedFocusTrackColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val UnselectedFocusTrackOutlineColor: (CssVariables) -> Color = { it.border }
    val UnselectedHandleColor: (CssVariables) -> Color = { it.border }
    val UnselectedHandleHeight = 16.0.dp
    val UnselectedHandleWidth = 16.0.dp
    val UnselectedHoverHandleColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedHoverIconColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val UnselectedHoverTrackColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val UnselectedHoverTrackOutlineColor: (CssVariables) -> Color = { it.border }
    val UnselectedIconColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val UnselectedIconSize = 16.0.dp
    val UnselectedPressedHandleColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedPressedIconColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val UnselectedPressedTrackColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val UnselectedPressedTrackOutlineColor: (CssVariables) -> Color = { it.border }
    val UnselectedTrackColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val UnselectedTrackOutlineColor: (CssVariables) -> Color = { it.border }
    val IconHandleHeight = 24.0.dp
    val IconHandleWidth = 24.0.dp
}
