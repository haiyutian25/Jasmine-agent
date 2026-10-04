// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/FabPrimaryContainerTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object FabPrimaryContainerTokens {
    val ContainerColor: (CssVariables) -> Color = { it.primaryContainer }
    val ContainerElevation = ElevationTokens.Level3
    val FocusedContainerElevation = ElevationTokens.Level3
    val FocusedIconColor: (CssVariables) -> Color = { it.onPrimaryContainer }
    val HoveredContainerElevation = ElevationTokens.Level4
    val HoveredIconColor: (CssVariables) -> Color = { it.onPrimaryContainer }
    val IconColor: (CssVariables) -> Color = { it.onPrimaryContainer }
    val PressedContainerElevation = ElevationTokens.Level3
    val PressedIconColor: (CssVariables) -> Color = { it.onPrimaryContainer }
}
