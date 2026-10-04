// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/NavigationBarVerticalItemTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object NavigationBarVerticalItemTokens {
    val ActiveIndicatorHeight = 32.0.dp
    val ActiveIndicatorWidth = 56.0.dp
    val ContainerBetweenSpace = 6.0.dp
    val IconSize = 24.0.dp
}
