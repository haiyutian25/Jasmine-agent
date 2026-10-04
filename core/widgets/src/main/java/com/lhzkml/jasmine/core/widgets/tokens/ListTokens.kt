// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/ListTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object ListTokens {
    val DividerLeadingSpace = 16.0.dp
    val DividerTrailingSpace = 16.0.dp
    val FocusIndicatorColor: (CssVariables) -> Color = { it.secondary }
    val ListItemContainerColor: (CssVariables) -> Color = { it.surface }
    val ListItemContainerElevation = ElevationTokens.Level0
    val ListItemContainerShape = ShapeKeyTokens.CornerNone
    val ListItemDisabledLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val ListItemDisabledLabelTextOpacity = 0.38f
    val ListItemDisabledLeadingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val ListItemDisabledLeadingIconOpacity = 0.38f
    val ListItemDisabledTrailingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val ListItemDisabledTrailingIconOpacity = 0.38f
    val ListItemDraggedContainerElevation = ElevationTokens.Level4
    val ListItemDraggedLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val ListItemDraggedLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemDraggedTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemFocusLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val ListItemFocusLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemFocusTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemHoverLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val ListItemHoverLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemHoverTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val ListItemLabelTextFont = AppTypography.bodyLarge
    val ListItemLargeLeadingVideoHeight = 69.0.dp
    val ListItemLeadingAvatarColor: (CssVariables) -> Color = { it.primaryContainer }
    val ListItemLeadingAvatarLabelColor: (CssVariables) -> Color = { it.onPrimaryContainer }
    val ListItemLeadingAvatarLabelFont = AppTypography.titleMedium
    val ListItemLeadingAvatarShape = ShapeKeyTokens.CornerFull
    val ListItemLeadingAvatarSize = 40.0.dp
    val ListItemLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemLeadingIconSize = 24.0.dp
    val ListItemLeadingImageHeight = 56.0.dp
    val ListItemLeadingImageShape = ShapeKeyTokens.CornerNone
    val ListItemLeadingImageWidth = 56.0.dp
    val ListItemLeadingSpace = 16.0.dp
    val ListItemLeadingVideoShape = ShapeKeyTokens.CornerNone
    val ListItemLeadingVideoWidth = 100.0.dp
    val ListItemOneLineContainerHeight = 56.0.dp
    val ListItemOverlineColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemOverlineFont = AppTypography.labelSmall
    val ListItemPressedLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val ListItemPressedLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemPressedTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemSelectedTrailingIconColor: (CssVariables) -> Color = { it.primary }
    val ListItemSmallLeadingVideoHeight = 56.0.dp
    val ListItemSupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemSupportingTextFont = AppTypography.bodyMedium
    val ListItemThreeLineContainerHeight = 88.0.dp
    val ListItemTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemTrailingIconSize = 24.0.dp
    val ListItemTrailingSpace = 16.0.dp
    val ListItemTrailingSupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemTrailingSupportingTextFont = AppTypography.labelSmall
    val ListItemTwoLineContainerHeight = 72.0.dp
    val ListItemUnselectedTrailingIconColor: (CssVariables) -> Color = { it.cardForeground }
}
