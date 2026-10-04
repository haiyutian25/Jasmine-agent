// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/StateTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens


internal object StateTokens {
    const val DraggedStateLayerOpacity = 0.16f
    const val FocusStateLayerOpacity = 0.1f
    const val HoverStateLayerOpacity = 0.08f
    const val PressedStateLayerOpacity = 0.1f
}
