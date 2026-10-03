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
// 只保留菜单家族（Menu.kt / ExposedDropdownMenu.kt）真正引用到的槽位，其余已随裁剪删掉。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object OutlinedAutocompleteTokens {
    val TextFieldCaretColor: (CssVariables) -> Color = { it.primary }
    val FieldDisabledInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldDisabledInputTextOpacity = 0.38f
    val FieldDisabledLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldDisabledLabelTextOpacity = 0.38f
    val TextFieldDisabledLeadingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val TextFieldDisabledLeadingIconOpacity = 0.38f
    val TextFieldDisabledOutlineColor: (CssVariables) -> Color = { it.cardForeground }
    val TextFieldDisabledOutlineOpacity = 0.12f
    val FieldDisabledSupportingTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldDisabledSupportingTextOpacity = 0.38f
    val TextFieldDisabledTrailingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val TextFieldDisabledTrailingIconOpacity = 0.38f
    val TextFieldErrorFocusCaretColor: (CssVariables) -> Color = { it.error }
    val FieldErrorInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldErrorLabelTextColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldErrorOutlineColor: (CssVariables) -> Color = { it.error }
    val TextFieldErrorTrailingIconColor: (CssVariables) -> Color = { it.error }
    val FieldFocusInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldFocusLabelTextColor: (CssVariables) -> Color = { it.primary }
    val TextFieldFocusLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldFocusOutlineColor: (CssVariables) -> Color = { it.primary }
    val TextFieldFocusTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val FieldInputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val FieldLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldOutlineColor: (CssVariables) -> Color = { it.border }
    val FieldSupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val TextFieldTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
}

