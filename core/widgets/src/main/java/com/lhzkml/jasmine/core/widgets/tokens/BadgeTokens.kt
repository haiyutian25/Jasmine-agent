// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：androidx/compose/material3/tokens/BadgeTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object BadgeTokens {
    val Color: (CssVariables) -> Color = { it.error }
    val LargeColor: (CssVariables) -> Color = { it.error }
    val LargeLabelTextColor: (CssVariables) -> Color = { it.onError }
    val LargeLabelTextFont = AppTypography.labelSmall
    val LargeShape = ShapeKeyTokens.CornerFull
    val LargeSize = 16.0.dp
    val Shape = ShapeKeyTokens.CornerFull
    val Size = 6.0.dp
}
