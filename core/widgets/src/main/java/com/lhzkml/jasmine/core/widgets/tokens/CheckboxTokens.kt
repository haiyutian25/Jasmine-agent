// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：androidx/compose/material3/tokens/CheckboxTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object CheckboxTokens {
    val ContainerHeight = 18.0.dp
    val ContainerShape = RoundedCornerShape(2.0.dp)
    val ContainerWidth = 18.0.dp
    val IconSize = 18.0.dp
    val SelectedContainerColor: (CssVariables) -> Color = { it.primary }
    val SelectedDisabledContainerColor: (CssVariables) -> Color = { it.cardForeground }
    const val SelectedDisabledContainerOpacity = 0.38f
    val SelectedDisabledContainerOutlineWidth = 0.0.dp
    val SelectedDisabledIconColor: (CssVariables) -> Color = { it.surface }
    val SelectedErrorContainerColor: (CssVariables) -> Color = { it.error }
    val SelectedErrorFocusContainerColor: (CssVariables) -> Color = { it.error }
    val SelectedErrorFocusIconColor: (CssVariables) -> Color = { it.onError }
    val SelectedErrorFocusOutlineWidth = 0.0.dp
    val SelectedErrorHoverContainerColor: (CssVariables) -> Color = { it.error }
    val SelectedErrorHoverIconColor: (CssVariables) -> Color = { it.onError }
    val SelectedErrorHoverOutlineWidth = 0.0.dp
    val SelectedErrorIconColor: (CssVariables) -> Color = { it.onError }
    val SelectedErrorPressedContainerColor: (CssVariables) -> Color = { it.error }
    val SelectedErrorPressedIconColor: (CssVariables) -> Color = { it.onError }
    val SelectedErrorPressedOutlineWidth = 0.0.dp
    val SelectedFocusContainerColor: (CssVariables) -> Color = { it.primary }
    val SelectedFocusIconColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedFocusOutlineWidth = 0.0.dp
    val SelectedHoverContainerColor: (CssVariables) -> Color = { it.primary }
    val SelectedHoverIconColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedHoverOutlineWidth = 0.0.dp
    val SelectedIconColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedOutlineWidth = 0.0.dp
    val SelectedPressedContainerColor: (CssVariables) -> Color = { it.primary }
    val SelectedPressedIconColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedPressedOutlineWidth = 0.0.dp
    val StateLayerShape = ShapeKeyTokens.CornerFull
    val StateLayerSize = 40.0.dp
    const val UnselectedDisabledContainerOpacity = 0.38f
    val UnselectedDisabledOutlineColor: (CssVariables) -> Color = { it.cardForeground }
    val UnselectedDisabledOutlineWidth = 2.0.dp
    val UnselectedErrorFocusOutlineColor: (CssVariables) -> Color = { it.error }
    val UnselectedErrorFocusOutlineWidth = 2.0.dp
    val UnselectedErrorHoverOutlineColor: (CssVariables) -> Color = { it.error }
    val UnselectedErrorHoverOutlineWidth = 2.0.dp
    val UnselectedErrorOutlineColor: (CssVariables) -> Color = { it.error }
    val UnselectedErrorPressedOutlineColor: (CssVariables) -> Color = { it.error }
    val UnselectedErrorPressedOutlineWidth = 2.0.dp
    val UnselectedFocusOutlineColor: (CssVariables) -> Color = { it.cardForeground }
    val UnselectedFocusOutlineWidth = 2.0.dp
    val UnselectedHoverOutlineColor: (CssVariables) -> Color = { it.cardForeground }
    val UnselectedHoverOutlineWidth = 2.0.dp
    val UnselectedOutlineColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedOutlineWidth = 2.0.dp
    val UnselectedPressedOutlineColor: (CssVariables) -> Color = { it.cardForeground }
    val UnselectedPressedOutlineWidth = 2.0.dp
}
