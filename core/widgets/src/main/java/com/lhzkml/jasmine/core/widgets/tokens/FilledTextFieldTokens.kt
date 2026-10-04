// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/FilledTextFieldTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object FilledTextFieldTokens {
    val ActiveIndicatorColor: (CssVariables) -> Color = { it.mutedForeground }
    val ActiveIndicatorHeight = 1.0.dp
    val CaretColor: (CssVariables) -> Color = { it.primary }
    val ContainerColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val ContainerShape = ShapeKeyTokens.CornerExtraSmallTop
    val DisabledActiveIndicatorColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledActiveIndicatorHeight = 1.0.dp
    val DisabledActiveIndicatorOpacity = 0.38f
    val DisabledContainerColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledContainerOpacity = 0.04f
    val DisabledInputColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledInputOpacity = 0.38f
    val DisabledLabelColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledLabelOpacity = 0.38f
    val DisabledLeadingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledLeadingIconOpacity = 0.38f
    val DisabledSupportingColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledSupportingOpacity = 0.38f
    val DisabledTrailingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledTrailingIconOpacity = 0.38f
    val ErrorActiveIndicatorColor: (CssVariables) -> Color = { it.error }
    val ErrorFocusActiveIndicatorColor: (CssVariables) -> Color = { it.error }
    val ErrorFocusCaretColor: (CssVariables) -> Color = { it.error }
    val ErrorFocusInputColor: (CssVariables) -> Color = { it.cardForeground }
    val ErrorFocusLabelColor: (CssVariables) -> Color = { it.error }
    val ErrorFocusLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ErrorFocusSupportingColor: (CssVariables) -> Color = { it.error }
    val ErrorFocusTrailingIconColor: (CssVariables) -> Color = { it.error }
    val ErrorHoverActiveIndicatorColor: (CssVariables) -> Color = { it.onErrorContainer }
    val ErrorHoverInputColor: (CssVariables) -> Color = { it.cardForeground }
    val ErrorHoverLabelColor: (CssVariables) -> Color = { it.onErrorContainer }
    val ErrorHoverLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ErrorHoverSupportingColor: (CssVariables) -> Color = { it.error }
    val ErrorHoverTrailingIconColor: (CssVariables) -> Color = { it.onErrorContainer }
    val ErrorInputColor: (CssVariables) -> Color = { it.cardForeground }
    val ErrorLabelColor: (CssVariables) -> Color = { it.error }
    val ErrorLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ErrorSupportingColor: (CssVariables) -> Color = { it.error }
    val ErrorTrailingIconColor: (CssVariables) -> Color = { it.error }
    val FocusActiveIndicatorColor: (CssVariables) -> Color = { it.primary }
    val FocusActiveIndicatorHeight = 2.0.dp
    val FocusInputColor: (CssVariables) -> Color = { it.cardForeground }
    val FocusLabelColor: (CssVariables) -> Color = { it.primary }
    val FocusLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val FocusSupportingColor: (CssVariables) -> Color = { it.mutedForeground }
    val FocusTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val HoverActiveIndicatorColor: (CssVariables) -> Color = { it.cardForeground }
    val HoverActiveIndicatorHeight = 1.0.dp
    val HoverInputColor: (CssVariables) -> Color = { it.cardForeground }
    val HoverLabelColor: (CssVariables) -> Color = { it.mutedForeground }
    val HoverLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val HoverSupportingColor: (CssVariables) -> Color = { it.mutedForeground }
    val HoverTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val InputColor: (CssVariables) -> Color = { it.cardForeground }
    val InputFont = AppTypography.bodyLarge
    val InputPlaceholderColor: (CssVariables) -> Color = { it.mutedForeground }
    val InputPrefixColor: (CssVariables) -> Color = { it.mutedForeground }
    val InputSuffixColor: (CssVariables) -> Color = { it.mutedForeground }
    val LabelColor: (CssVariables) -> Color = { it.mutedForeground }
    val LabelFont = AppTypography.bodyLarge
    val LeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val LeadingIconSize = 24.0.dp
    val SupportingColor: (CssVariables) -> Color = { it.mutedForeground }
    val SupportingFont = AppTypography.bodySmall
    val TrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TrailingIconSize = 24.0.dp
}
