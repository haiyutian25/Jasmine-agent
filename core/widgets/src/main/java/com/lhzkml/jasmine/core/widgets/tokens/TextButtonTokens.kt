// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/TextButtonTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object TextButtonTokens {
    val DisabledContainerColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledContainerOpacity = 0.1f
    val DisabledIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val DisabledIconOpacity = 0.38f
    val DisabledLabelColor: (CssVariables) -> Color = { it.mutedForeground }
    val DisabledLabelOpacity = 0.38f
    val FocusedIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val FocusedLabelColor: (CssVariables) -> Color = { it.mutedForeground }
    val HoveredIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val HoveredLabelColor: (CssVariables) -> Color = { it.mutedForeground }
    val IconColor: (CssVariables) -> Color = { it.mutedForeground }
    val LabelColor: (CssVariables) -> Color = { it.mutedForeground }
    val PressedIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val PressedLabelColor: (CssVariables) -> Color = { it.mutedForeground }
}
