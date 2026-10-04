// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/OutlinedAutocompleteTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object OutlinedAutocompleteTokens {
    val MenuContainerColor: (CssVariables) -> Color = { it.surfaceContainer }
    val MenuContainerElevation = ElevationTokens.Level2
    val MenuContainerShape = ShapeKeyTokens.CornerExtraSmall
    val TextFieldCaretColor: (CssVariables) -> Color = { it.primary }
    val TextFieldContainerColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val TextFieldContainerShape = ShapeKeyTokens.CornerExtraSmall
    val FieldDisabledInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldDisabledInputTextOpacity = 0.38f
    val FieldDisabledLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldDisabledLabelTextOpacity = 0.38f
    val TextFieldDisabledLeadingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val TextFieldDisabledLeadingIconOpacity = 0.38f
    val TextFieldDisabledOutlineColor: (CssVariables) -> Color = { it.cardForeground }
    val TextFieldDisabledOutlineOpacity = 0.12f
    val TextFieldDisabledOutlineWidth = 1.0.dp
    val FieldDisabledSupportingTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldDisabledSupportingTextOpacity = 0.38f
    val TextFieldDisabledTrailingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val TextFieldDisabledTrailingIconOpacity = 0.38f
    val TextFieldErrorFocusCaretColor: (CssVariables) -> Color = { it.error }
    val FieldErrorFocusInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldErrorFocusLabelTextColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorFocusLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldErrorFocusOutlineColor: (CssVariables) -> Color = { it.error }
    val FieldErrorFocusSupportingTextColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorFocusTrailingIconColor: (CssVariables) -> Color = { it.error }
    val FieldErrorHoverInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldErrorHoverLabelTextColor: (CssVariables) -> Color = { it.onErrorContainer }
    val TextFieldErrorHoverLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldErrorHoverOutlineColor: (CssVariables) -> Color = { it.onErrorContainer }
    val FieldErrorHoverSupportingTextColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorHoverTrailingIconColor: (CssVariables) -> Color = { it.onErrorContainer }
    val FieldErrorInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldErrorLabelTextColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldErrorOutlineColor: (CssVariables) -> Color = { it.error }
    val FieldErrorSupportingTextColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorTrailingIconColor: (CssVariables) -> Color = { it.error }
    val FieldFocusInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldFocusLabelTextColor: (CssVariables) -> Color = { it.primary }
    val TextFieldFocusLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldFocusOutlineColor: (CssVariables) -> Color = { it.primary }
    val TextFieldFocusOutlineWidth = 2.0.dp
    val FieldFocusSupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldFocusTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val FieldHoverInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldHoverLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldHoverLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldHoverOutlineColor: (CssVariables) -> Color = { it.cardForeground }
    val TextFieldHoverOutlineWidth = 1.0.dp
    val FieldHoverSupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldHoverTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val FieldInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldInputTextFont = AppTypography.bodyLarge
    val FieldLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val FieldLabelTextFont = AppTypography.bodyLarge
    val TextFieldLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldLeadingIconSize = 24.0.dp
    val TextFieldOutlineColor: (CssVariables) -> Color = { it.border }
    val TextFieldOutlineWidth = 1.0.dp
    val FieldSupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val FieldSupportingTextFont = AppTypography.bodySmall
    val TextFieldTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldTrailingIconSize = 24.0.dp
}
