// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/FilledIconButtonTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object FilledIconButtonTokens {
    val ContainerColor: (CssVariables) -> Color = { it.primary }
    val DisabledContainerColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledContainerOpacity = 0.1f
    val DisabledColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledOpacity = 0.38f
    val FocusedColor: (CssVariables) -> Color = { it.primaryForeground }
    val HoveredColor: (CssVariables) -> Color = { it.primaryForeground }
    val Color: (CssVariables) -> Color = { it.primaryForeground }
    val PressedColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedContainerColor: (CssVariables) -> Color = { it.primary }
    val SelectedFocusedColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedHoveredColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedPressedColor: (CssVariables) -> Color = { it.primaryForeground }
    val UnselectedContainerColor: (CssVariables) -> Color = { it.surfaceContainer }
    val UnselectedFocusedColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedHoveredColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedPressedColor: (CssVariables) -> Color = { it.mutedForeground }
}
