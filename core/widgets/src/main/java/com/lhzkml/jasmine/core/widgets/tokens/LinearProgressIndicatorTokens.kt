// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/LinearProgressIndicatorTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object LinearProgressIndicatorTokens {
    val ActiveThickness = 4.0.dp
    val ActiveWaveAmplitude = 3.0.dp
    val ActiveWaveWavelength = 40.0.dp
    val Height = 4.0.dp
    val IndeterminateActiveWaveWavelength = 20.0.dp
    val StopSize = 4.0.dp
    val StopTrailingSpace = 0.0.dp
    val TrackActiveSpace = 4.0.dp
    val TrackThickness = 4.0.dp
    val WaveHeight = 10.0.dp
}
