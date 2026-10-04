// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/RadioButtonTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object RadioButtonTokens {
    val DisabledSelectedIconColor: (CssVariables) -> Color = { it.cardForeground }
    const val DisabledSelectedIconOpacity = 0.38f
    val DisabledUnselectedIconColor: (CssVariables) -> Color = { it.cardForeground }
    const val DisabledUnselectedIconOpacity = 0.38f
    val IconSize = 20.0.dp
    val SelectedFocusIconColor: (CssVariables) -> Color = { it.primary }
    val SelectedHoverIconColor: (CssVariables) -> Color = { it.primary }
    val SelectedIconColor: (CssVariables) -> Color = { it.primary }
    val SelectedPressedIconColor: (CssVariables) -> Color = { it.primary }
    val StateLayerSize = 40.0.dp
    val UnselectedFocusIconColor: (CssVariables) -> Color = { it.cardForeground }
    val UnselectedHoverIconColor: (CssVariables) -> Color = { it.cardForeground }
    val UnselectedIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedPressedIconColor: (CssVariables) -> Color = { it.cardForeground }
}
