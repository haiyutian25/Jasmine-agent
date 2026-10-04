// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/ElevatedCardTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object ElevatedCardTokens {
    val ContainerColor: (CssVariables) -> Color = { it.surfaceContainerLow }
    val ContainerElevation = ElevationTokens.Level1
    val ContainerShape = ShapeKeyTokens.CornerMedium
    val DisabledContainerColor: (CssVariables) -> Color = { it.surface }
    val DisabledContainerElevation = ElevationTokens.Level1
    val DisabledContainerOpacity = 0.38f
    val DraggedContainerElevation = ElevationTokens.Level4
    val FocusContainerElevation = ElevationTokens.Level1
    val FocusIndicatorColor: (CssVariables) -> Color = { it.secondary }
    val HoverContainerElevation = ElevationTokens.Level2
    val IconColor: (CssVariables) -> Color = { it.primary }
    val IconSize = 24.0.dp
    val PressedContainerElevation = ElevationTokens.Level1
}
