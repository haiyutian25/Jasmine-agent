// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/FilledTonalIconButtonTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object FilledTonalIconButtonTokens {
    val ContainerColor: (CssVariables) -> Color = { it.secondaryContainer }
    val DisabledContainerColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledContainerOpacity = 0.1f
    val DisabledColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledOpacity = 0.38f
    val FocusedColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val HoveredColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val Color: (CssVariables) -> Color = { it.onSecondaryContainer }
    val PressedColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val SelectedContainerColor: (CssVariables) -> Color = { it.secondary }
    val SelectedFocusedColor: (CssVariables) -> Color = { it.onSecondary }
    val SelectedHoveredColor: (CssVariables) -> Color = { it.onSecondary }
    val SelectedColor: (CssVariables) -> Color = { it.onSecondary }
    val SelectedPressedColor: (CssVariables) -> Color = { it.onSecondary }
    val UnselectedContainerColor: (CssVariables) -> Color = { it.secondaryContainer }
    val UnselectedFocusedColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val UnselectedHoveredColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val UnselectedColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val UnselectedPressedColor: (CssVariables) -> Color = { it.onSecondaryContainer }
}
