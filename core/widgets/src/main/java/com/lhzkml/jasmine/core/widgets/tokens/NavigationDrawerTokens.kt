// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：androidx/compose/material3/tokens/NavigationDrawerTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object NavigationDrawerTokens {
    val ActiveFocusIconColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val ActiveFocusLabelTextColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val ActiveHoverIconColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val ActiveHoverLabelTextColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val ActiveIconColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val ActiveIndicatorColor: (CssVariables) -> Color = { it.secondaryContainer }
    val ActiveIndicatorHeight = 56.0.dp
    val ActiveIndicatorShape = ShapeKeyTokens.CornerFull
    val ActiveIndicatorWidth = 336.0.dp
    val ActiveLabelTextColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val ActivePressedIconColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val ActivePressedLabelTextColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val BottomContainerShape = ShapeKeyTokens.CornerLargeTop
    val ContainerHeightPercent = 100.0f
    val ContainerShape = ShapeKeyTokens.CornerLargeEnd
    val ContainerWidth = 360.0.dp
    val FocusIndicatorColor: (CssVariables) -> Color = { it.secondary }
    val HeadlineColor: (CssVariables) -> Color = { it.mutedForeground }
    val HeadlineFont = AppTypography.titleSmall
    val IconSize = 24.0.dp
    val InactiveFocusIconColor: (CssVariables) -> Color = { it.cardForeground }
    val InactiveFocusLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val InactiveHoverIconColor: (CssVariables) -> Color = { it.cardForeground }
    val InactiveHoverLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val InactiveIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val InactiveLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val InactivePressedIconColor: (CssVariables) -> Color = { it.cardForeground }
    val InactivePressedLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val LabelTextFont = AppTypography.labelLarge
    val LargeBadgeLabelColor: (CssVariables) -> Color = { it.mutedForeground }
    val LargeBadgeLabelFont = AppTypography.labelLarge
    val ModalContainerColor: (CssVariables) -> Color = { it.surfaceContainerLow }
    val ModalContainerElevation = ElevationTokens.Level1
    val StandardContainerColor: (CssVariables) -> Color = { it.surface }
    val StandardContainerElevation = ElevationTokens.Level0
}
