// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：androidx/compose/material3/tokens/DialogTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object DialogTokens {
    val ActionFocusLabelTextColor: (CssVariables) -> Color = { it.primary }
    val ActionHoverLabelTextColor: (CssVariables) -> Color = { it.primary }
    val ActionLabelTextColor: (CssVariables) -> Color = { it.primary }
    val ActionLabelTextFont = AppTypography.labelLarge
    val ActionPressedLabelTextColor: (CssVariables) -> Color = { it.primary }
    val ContainerColor: (CssVariables) -> Color = { it.surfaceContainerHigh }
    val ContainerElevation = ElevationTokens.Level3
    val ContainerShape = ShapeKeyTokens.CornerExtraLarge
    val HeadlineColor: (CssVariables) -> Color = { it.cardForeground }
    val HeadlineFont = AppTypography.headlineSmall
    val SupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val SupportingTextFont = AppTypography.bodyMedium
    val IconColor: (CssVariables) -> Color = { it.secondary }
    val IconSize = 24.0.dp
}
