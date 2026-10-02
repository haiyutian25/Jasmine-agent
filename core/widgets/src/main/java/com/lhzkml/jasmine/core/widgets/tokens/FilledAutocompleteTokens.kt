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

internal object FilledAutocompleteTokens {
    val TextFieldActiveIndicatorColor = ColorSchemeKeyTokens.OnSurfaceVariant
    val TextFieldCaretColor = ColorSchemeKeyTokens.Primary
    val TextFieldContainerColor = ColorSchemeKeyTokens.SurfaceContainerHighest
    val TextFieldDisabledActiveIndicatorColor = ColorSchemeKeyTokens.OnSurface
    val TextFieldDisabledActiveIndicatorOpacity = 0.38f
    val FieldDisabledInputTextColor = ColorSchemeKeyTokens.OnSurface
    val FieldDisabledInputTextOpacity = 0.38f
    val FieldDisabledLabelTextColor = ColorSchemeKeyTokens.OnSurface
    val TextFieldDisabledLeadingIconColor = ColorSchemeKeyTokens.OnSurface
    val TextFieldDisabledLeadingIconOpacity = 0.38f
    val FieldDisabledSupportingTextColor = ColorSchemeKeyTokens.OnSurface
    val FieldDisabledSupportingTextOpacity = 0.38f
    val TextFieldDisabledTrailingIconColor = ColorSchemeKeyTokens.OnSurface
    val TextFieldDisabledTrailingIconOpacity = 0.38f
    val TextFieldErrorActiveIndicatorColor = ColorSchemeKeyTokens.Error
    val TextFieldErrorFocusCaretColor = ColorSchemeKeyTokens.Error
    val FieldErrorInputTextColor = ColorSchemeKeyTokens.OnSurface
    val FieldErrorLabelTextColor = ColorSchemeKeyTokens.Error
    val TextFieldErrorLeadingIconColor = ColorSchemeKeyTokens.OnSurfaceVariant
    val TextFieldErrorTrailingIconColor = ColorSchemeKeyTokens.Error
    val TextFieldFocusActiveIndicatorColor = ColorSchemeKeyTokens.Primary
    val FieldFocusInputTextColor = ColorSchemeKeyTokens.OnSurface
    val FieldFocusLabelTextColor = ColorSchemeKeyTokens.Primary
    val TextFieldFocusLeadingIconColor = ColorSchemeKeyTokens.OnSurfaceVariant
    val TextFieldFocusTrailingIconColor = ColorSchemeKeyTokens.OnSurfaceVariant
    val FieldInputTextColor = ColorSchemeKeyTokens.OnSurface
    val FieldLabelTextColor = ColorSchemeKeyTokens.OnSurfaceVariant
    val TextFieldLeadingIconColor = ColorSchemeKeyTokens.OnSurfaceVariant
    val FieldSupportingTextColor = ColorSchemeKeyTokens.OnSurfaceVariant
    val TextFieldTrailingIconColor = ColorSchemeKeyTokens.OnSurfaceVariant
}

