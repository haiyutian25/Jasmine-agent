// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/ShapeTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

internal object ShapeTokens {
    val CornerExtraExtraLarge = RoundedCornerShape(48.0.dp)
    val CornerExtraLarge = RoundedCornerShape(28.0.dp)
    val CornerExtraLargeIncreased = RoundedCornerShape(32.0.dp)
    val CornerExtraLargeTop = RoundedCornerShape(
        topStart = 28.0.dp,
        topEnd = 28.0.dp,
        bottomEnd = 0.0.dp,
        bottomStart = 0.0.dp,
    )
    val CornerExtraSmall = RoundedCornerShape(4.0.dp)
    val CornerExtraSmallTop = RoundedCornerShape(
        topStart = 4.0.dp,
        topEnd = 4.0.dp,
        bottomEnd = 0.0.dp,
        bottomStart = 0.0.dp,
    )
    val CornerFull = CircleShape
    val CornerLarge = RoundedCornerShape(16.0.dp)
    val CornerLargeEnd = RoundedCornerShape(
        topStart = 0.0.dp,
        topEnd = 16.0.dp,
        bottomEnd = 16.0.dp,
        bottomStart = 0.0.dp,
    )
    val CornerLargeIncreased = RoundedCornerShape(20.0.dp)
    val CornerLargeStart = RoundedCornerShape(
        topStart = 16.0.dp,
        topEnd = 0.0.dp,
        bottomEnd = 0.0.dp,
        bottomStart = 16.0.dp,
    )
    val CornerLargeTop = RoundedCornerShape(
        topStart = 16.0.dp,
        topEnd = 16.0.dp,
        bottomEnd = 0.0.dp,
        bottomStart = 0.0.dp,
    )
    val CornerMedium = RoundedCornerShape(12.0.dp)
    val CornerNone = RectangleShape
    val CornerSmall = RoundedCornerShape(8.0.dp)
    val CornerValueExtraExtraLarge = CornerSize(48.0.dp)
    val CornerValueExtraLarge = CornerSize(28.0.dp)
    val CornerValueExtraLargeIncreased = CornerSize(32.0.dp)
    val CornerValueExtraSmall = CornerSize(4.0.dp)
    val CornerValueLarge = CornerSize(16.0.dp)
    val CornerValueLargeIncreased = CornerSize(20.0.dp)
    val CornerValueMedium = CornerSize(12.0.dp)
    val CornerValueNone = CornerSize(0.0.dp)
    val CornerValueSmall = CornerSize(8.0.dp)
}
