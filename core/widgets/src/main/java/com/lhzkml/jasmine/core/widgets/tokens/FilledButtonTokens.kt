/*
 * Copyright 2021 The Android Open Source Project
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
// 颜色槽位直读自有主题 `CssVariables`（Primary -> primary、OnPrimary -> primaryForeground、
// OnSurface -> cardForeground、OnSurfaceVariant -> mutedForeground），取值与之前逐槽一致。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object FilledButtonTokens {
    val ContainerColor: (CssVariables) -> Color = { it.primary }
    val ContainerElevation = ElevationTokens.Level0
    val DisabledContainerColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledContainerElevation = ElevationTokens.Level0
    val DisabledContainerOpacity = 0.1f
    val DisabledLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val DisabledLabelTextOpacity = 0.38f
    val FocusedContainerElevation = ElevationTokens.Level0
    val HoveredContainerElevation = ElevationTokens.Level1
    val LabelTextColor: (CssVariables) -> Color = { it.primaryForeground }
    val PressedContainerElevation = ElevationTokens.Level0
}
