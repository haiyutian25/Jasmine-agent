// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：androidx/compose/material3/tokens/AppBarSmallTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object AppBarSmallTokens {
    val ContainerHeight = 64.0.dp
    val SubtitleFont = AppTypography.labelMedium
    val TitleFont = AppTypography.titleLarge
}
