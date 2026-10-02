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
// 颜色槽位直读自有主题 `CssVariables`（Primary -> primary、OnPrimary -> primaryForeground、
// OnSurface -> cardForeground、SurfaceContainer -> card、OnSurfaceVariant -> mutedForeground）。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object FilledIconButtonTokens {
    val ContainerColor: (CssVariables) -> Color = { it.primary }
    val DisabledContainerColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledContainerOpacity = 0.1f
    val DisabledColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledOpacity = 0.38f
    val FocusedColor: (CssVariables) -> Color = { it.primaryForeground }
    val HoveredColor: (CssVariables) -> Color = { it.primaryForeground }
    val Color: (CssVariables) -> Color = { it.primaryForeground }
    val PressedColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedContainerColor: (CssVariables) -> Color = { it.primary }
    val SelectedFocusedColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedHoveredColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedPressedColor: (CssVariables) -> Color = { it.primaryForeground }
    val UnselectedContainerColor: (CssVariables) -> Color = { it.card }
    val UnselectedFocusedColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedHoveredColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedColor: (CssVariables) -> Color = { it.mutedForeground }
    val UnselectedPressedColor: (CssVariables) -> Color = { it.mutedForeground }
}
