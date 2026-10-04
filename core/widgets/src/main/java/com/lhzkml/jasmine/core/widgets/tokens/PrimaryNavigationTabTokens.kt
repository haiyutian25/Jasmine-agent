// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/PrimaryNavigationTabTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object PrimaryNavigationTabTokens {
    val ActiveIndicatorColor: (CssVariables) -> Color = { it.primary }
    val ActiveIndicatorHeight = 3.0.dp
    val ActiveIndicatorShape = RoundedCornerShape(3.0.dp)
    val ContainerColor: (CssVariables) -> Color = { it.surface }
    val ContainerElevation = ElevationTokens.Level0
    val ContainerHeight = 48.0.dp
    val ContainerShape = ShapeKeyTokens.CornerNone
    val ActiveFocusIconColor: (CssVariables) -> Color = { it.primary }
    val ActiveHoverIconColor: (CssVariables) -> Color = { it.primary }
    val ActiveIconColor: (CssVariables) -> Color = { it.primary }
    val ActivePressedIconColor: (CssVariables) -> Color = { it.primary }
    val IconAndLabelTextContainerHeight = 64.0.dp
    val IconSize = 24.0.dp
    val InactiveFocusIconColor: (CssVariables) -> Color = { it.cardForeground }
    val InactiveHoverIconColor: (CssVariables) -> Color = { it.cardForeground }
    val InactiveIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val InactivePressedIconColor: (CssVariables) -> Color = { it.cardForeground }
    val ActiveFocusLabelTextColor: (CssVariables) -> Color = { it.primary }
    val ActiveHoverLabelTextColor: (CssVariables) -> Color = { it.primary }
    val ActiveLabelTextColor: (CssVariables) -> Color = { it.primary }
    val ActivePressedLabelTextColor: (CssVariables) -> Color = { it.primary }
    val InactiveFocusLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val InactiveHoverLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val InactiveLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val InactivePressedLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val LabelTextFont = AppTypography.titleSmall
}
