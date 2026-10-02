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
// 与上游的差别：上游这里是 M3 的 **颜色角色键**（`ColorSchemeKeyTokens.Surface` 等），再由
// `ColorScheme.fromToken` 映射到 M3 主题；本库不再做这层「角色键 → 主题」映射 ——
// 槽位直接在**自有主题** `CssVariables` 上实现，取值与之前逐槽一致（观感不变）：
//   ContainerColor Surface        -> CssVariables.card
//   OnScrollContainerColor SurfaceContainer -> CssVariables.card
//   LeadingIconColor / TitleColor OnSurface -> CssVariables.cardForeground
//   TrailingIconColor / SubtitleColor OnSurfaceVariant -> CssVariables.mutedForeground

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object AppBarTokens {
    val ContainerColor: (CssVariables) -> Color = { it.card }
    val LeadingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val OnScrollContainerColor: (CssVariables) -> Color = { it.card }
    val SubtitleColor: (CssVariables) -> Color = { it.mutedForeground }
    val TitleColor: (CssVariables) -> Color = { it.cardForeground }
    val TrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
}
