/*
 * Copyright 2024 The Android Open Source Project
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
// 上游位置：tokens/TypographyTokens.kt 末尾的 DefaultTextStyle /
// DefaultLineHeightStyle，以及 internal/DefaultPlatformTextStyle.kt 的 expect/actual。
// 本库是 Android 应用，把 android 侧的 actual 直接内联（`DefaultIncludeFontPadding = false`），取值逐项一致。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle

internal val DefaultLineHeightStyle =
    LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

/** 上游的 `defaultPlatformTextStyle()` 在 android 侧返回 `PlatformTextStyle(includeFontPadding = false)`。 */
internal val DefaultTextStyle =
    TextStyle.Default.copy(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = DefaultLineHeightStyle,
    )
