/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables


internal object OutlinedTextFieldTokens {
    val CaretColor: (CssVariables) -> Color = { it.primary }
    val ContainerShape = ShapeKeyTokens.CornerExtraSmall
    val DisabledInputColor: (CssVariables) -> Color = { it.cardForeground }
    const val DisabledInputOpacity = 0.38f
    val DisabledLabelColor: (CssVariables) -> Color = { it.cardForeground }
    const val DisabledLabelOpacity = 0.38f
    val DisabledLeadingIconColor: (CssVariables) -> Color = { it.cardForeground }
    const val DisabledLeadingIconOpacity = 0.38f
    val DisabledOutlineColor: (CssVariables) -> Color = { it.cardForeground }
    const val DisabledOutlineOpacity = 0.12f
    val DisabledSupportingColor: (CssVariables) -> Color = { it.cardForeground }
    const val DisabledSupportingOpacity = 0.38f
    val DisabledTrailingIconColor: (CssVariables) -> Color = { it.cardForeground }
    const val DisabledTrailingIconOpacity = 0.38f
    val ErrorFocusCaretColor: (CssVariables) -> Color = ErrorColorResolver
    val ErrorInputColor: (CssVariables) -> Color = { it.cardForeground }
    val ErrorLabelColor: (CssVariables) -> Color = ErrorColorResolver
    val ErrorLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ErrorOutlineColor: (CssVariables) -> Color = ErrorColorResolver
    val ErrorSupportingColor: (CssVariables) -> Color = ErrorColorResolver
    val ErrorTrailingIconColor: (CssVariables) -> Color = ErrorColorResolver
    val FocusInputColor: (CssVariables) -> Color = { it.cardForeground }
    val FocusLabelColor: (CssVariables) -> Color = { it.primary }
    val FocusLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val FocusOutlineColor: (CssVariables) -> Color = { it.primary }
    val FocusSupportingColor: (CssVariables) -> Color = { it.mutedForeground }
    val FocusTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val InputColor: (CssVariables) -> Color = { it.cardForeground }
    val InputPlaceholderColor: (CssVariables) -> Color = { it.mutedForeground }
    val InputPrefixColor: (CssVariables) -> Color = { it.mutedForeground }
    val InputSuffixColor: (CssVariables) -> Color = { it.mutedForeground }
    val LabelColor: (CssVariables) -> Color = { it.mutedForeground }
    val LeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val OutlineColor: (CssVariables) -> Color = { it.border }
    val SupportingColor: (CssVariables) -> Color = { it.mutedForeground }
    val TrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
}
