// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/OutlinedCardTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object OutlinedCardTokens {
    val ContainerColor: (CssVariables) -> Color = { it.surface }
    val ContainerElevation = ElevationTokens.Level0
    val ContainerShape = ShapeKeyTokens.CornerMedium
    val DisabledContainerElevation = ElevationTokens.Level0
    val DisabledOutlineColor: (CssVariables) -> Color = { it.border }
    const val DisabledOutlineOpacity = 0.12f
    val DraggedContainerElevation = ElevationTokens.Level3
    val DraggedOutlineColor: (CssVariables) -> Color = { it.muted }
    val FocusContainerElevation = ElevationTokens.Level0
    val FocusOutlineColor: (CssVariables) -> Color = { it.cardForeground }
    val HoverContainerElevation = ElevationTokens.Level1
    val HoverOutlineColor: (CssVariables) -> Color = { it.muted }
    val IconColor: (CssVariables) -> Color = { it.primary }
    val IconSize = 24.0.dp
    val OutlineColor: (CssVariables) -> Color = { it.muted }
    val OutlineWidth = 1.0.dp
    val PressedContainerElevation = ElevationTokens.Level0
    val PressedOutlineColor: (CssVariables) -> Color = { it.muted }
}
