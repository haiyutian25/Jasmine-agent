// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：androidx/compose/material3/tokens/FilledAutocompleteTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object FilledAutocompleteTokens {
    val MenuContainerColor: (CssVariables) -> Color = { it.surfaceContainer }
    val MenuContainerElevation = ElevationTokens.Level2
    val MenuContainerShape = ShapeKeyTokens.CornerExtraSmall
    val TextFieldActiveIndicatorColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldActiveIndicatorHeight = 1.0.dp
    val TextFieldCaretColor: (CssVariables) -> Color = { it.primary }
    val TextFieldContainerColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val TextFieldContainerShape = ShapeKeyTokens.CornerExtraSmallTop
    val TextFieldDisabledActiveIndicatorColor: (CssVariables) -> Color = { it.cardForeground }
    val TextFieldDisabledActiveIndicatorHeight = 1.0.dp
    val TextFieldDisabledActiveIndicatorOpacity = 0.38f
    val TextFieldDisabledContainerColor: (CssVariables) -> Color = { it.cardForeground }
    val TextFieldDisabledContainerOpacity = 0.04f
    val FieldDisabledInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldDisabledInputTextOpacity = 0.38f
    val FieldDisabledLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldDisabledLabelTextOpacity = 0.38f
    val TextFieldDisabledLeadingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val TextFieldDisabledLeadingIconOpacity = 0.38f
    val FieldDisabledSupportingTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldDisabledSupportingTextOpacity = 0.38f
    val TextFieldDisabledTrailingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val TextFieldDisabledTrailingIconOpacity = 0.38f
    val TextFieldErrorActiveIndicatorColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorFocusActiveIndicatorColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorFocusCaretColor: (CssVariables) -> Color = { it.error }
    val FieldErrorFocusInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldErrorFocusLabelTextColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorFocusLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val FieldErrorFocusSupportingTextColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorFocusTrailingIconColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorHoverActiveIndicatorColor: (CssVariables) -> Color = { it.onErrorContainer }
    val FieldErrorHoverInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldErrorHoverLabelTextColor: (CssVariables) -> Color = { it.onErrorContainer }
    val TextFieldErrorHoverLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val FieldErrorHoverSupportingTextColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorHoverTrailingIconColor: (CssVariables) -> Color = { it.onErrorContainer }
    val FieldErrorInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldErrorLabelTextColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val FieldErrorSupportingTextColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorTrailingIconColor: (CssVariables) -> Color = { it.error }
    val TextFieldFocusActiveIndicatorColor: (CssVariables) -> Color = { it.primary }
    val TextFieldFocusActiveIndicatorHeight = 2.0.dp
    val FieldFocusInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldFocusLabelTextColor: (CssVariables) -> Color = { it.primary }
    val TextFieldFocusLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val FieldFocusSupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldFocusTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldHoverActiveIndicatorColor: (CssVariables) -> Color = { it.cardForeground }
    val TextFieldHoverActiveIndicatorHeight = 1.0.dp
    val FieldHoverInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldHoverLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldHoverLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val FieldHoverSupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldHoverTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val FieldInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldInputTextFont = AppTypography.bodyLarge
    val FieldLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val FieldLabelTextFont = AppTypography.bodyLarge
    val TextFieldLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldLeadingIconSize = 20.0.dp
    val FieldSupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val FieldSupportingTextFont = AppTypography.bodySmall
    val TextFieldTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldTrailingIconSize = 24.0.dp
}
