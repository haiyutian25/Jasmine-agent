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

// 本项目自有的设计令牌（来源：AndroidX Material3 token VERSION 14_0_0），不再由上游生成，可直接改。
//
// 与上游的差别：上游的 TitleFont 是字体令牌键（`TypographyKeyTokens.TitleLarge`），再经
// `Typography.fromToken` 映射；本库直接读**自有主题**的排版档 `AppTypography.titleLarge`
// （那一档已按 M3 基线数值逐项抄进 `core/ui/theme/Type.kt`，观感不变）。容器高度是纯尺寸，无映射。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object AppBarSmallTokens {
    val ContainerHeight = 64.0.dp

    val TitleFont: TextStyle
        @Composable @ReadOnlyComposable get() = AppTypography.titleLarge
}
