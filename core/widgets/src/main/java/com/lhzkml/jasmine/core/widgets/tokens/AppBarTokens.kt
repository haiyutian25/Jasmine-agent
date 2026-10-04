// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/AppBarTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object AppBarTokens {
    val AvatarSize = 32.0.dp
    val ContainerColor: (CssVariables) -> Color = { it.surface }
    val ContainerElevation = ElevationTokens.Level0
    val ContainerShape = ShapeKeyTokens.CornerNone
    val IconButtonSpace = 0.0.dp
    val IconSize = 24.0.dp
    val LeadingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val LeadingSpace = 4.0.dp
    val OnScrollContainerColor: (CssVariables) -> Color = { it.surfaceContainer }
    val OnScrollContainerElevation = ElevationTokens.Level2
    val SubtitleColor: (CssVariables) -> Color = { it.mutedForeground }
    val TitleColor: (CssVariables) -> Color = { it.cardForeground }
    val TrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TrailingSpace = 4.0.dp
}
