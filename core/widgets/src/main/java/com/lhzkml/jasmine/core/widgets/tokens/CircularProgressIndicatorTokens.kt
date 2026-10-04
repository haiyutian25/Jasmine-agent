// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/CircularProgressIndicatorTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object CircularProgressIndicatorTokens {
    val ActiveThickness = 4.0.dp
    val ActiveWaveAmplitude = 1.6.dp
    val ActiveWaveWavelength = 15.0.dp
    val Size = 40.0.dp
    val TrackActiveSpace = 4.0.dp
    val TrackThickness = 4.0.dp
    val WaveSize = 48.0.dp
}
