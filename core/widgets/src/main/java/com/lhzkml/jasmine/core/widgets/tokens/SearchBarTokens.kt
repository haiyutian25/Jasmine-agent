// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/SearchBarTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object SearchBarTokens {
    val AvatarShape = ShapeKeyTokens.CornerFull
    val AvatarSize = 30.0.dp
    val ContainerColor: (CssVariables) -> Color = { it.surfaceContainerHigh }
    val ContainerElevation = ElevationTokens.Level3
    val ContainerHeight = 56.0.dp
    val ContainerShape = ShapeKeyTokens.CornerFull
    val FocusIndicatorColor: (CssVariables) -> Color = { it.secondary }
    val HoverSupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val InputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val InputTextFont = AppTypography.bodyLarge
    val LeadingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val PressedSupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val SupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val SupportingTextFont = AppTypography.bodyLarge
    val TrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
}
