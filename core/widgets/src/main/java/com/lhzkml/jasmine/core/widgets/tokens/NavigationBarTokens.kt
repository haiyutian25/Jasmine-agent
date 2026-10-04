// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/NavigationBarTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object NavigationBarTokens {
    val ContainerColor: (CssVariables) -> Color = { it.surfaceContainer }
    val ContainerElevation = ElevationTokens.Level2
    val ContainerHeight = 64.0.dp
    val ItemActiveIconColor: (CssVariables) -> Color = { it.onSecondaryContainer }
    val ItemActiveIndicatorColor: (CssVariables) -> Color = { it.secondaryContainer }
    val ItemActiveIndicatorIconLabelSpace = 4.0.dp
    val ItemActiveIndicatorShape = ShapeKeyTokens.CornerFull
    val ItemActiveLabelTextColor: (CssVariables) -> Color = { it.secondary }
    val ItemBetweenSpace = 0.0.dp
    val ItemInactiveIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ItemInactiveLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val NavShape = ShapeKeyTokens.CornerNone
    val TallContainerHeight = 80.0.dp
    val LabelTextFont = AppTypography.labelMedium
}
