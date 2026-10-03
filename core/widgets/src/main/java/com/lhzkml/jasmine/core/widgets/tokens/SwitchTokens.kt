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
// 这里只留本组件实际用到的槽位；上游那份里的 Hover / Focus / Pressed 配色与部分尺寸槽位
// 我们从不渲染（SwitchColors 只有 选中 / 未选中 / 禁用 三态），已随本次瘦身一并删掉。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables

import androidx.compose.ui.unit.dp

internal object SwitchTokens {
    val DisabledSelectedHandleColor: (CssVariables) -> Color = { it.surface }
    val DisabledSelectedHandleOpacity = 1.0f
    val DisabledSelectedIconColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledSelectedIconOpacity = 0.38f
    val DisabledSelectedTrackColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledTrackOpacity = 0.12f
    val DisabledUnselectedHandleColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledUnselectedHandleOpacity = 0.38f
    val DisabledUnselectedIconColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val DisabledUnselectedIconOpacity = 0.38f
    val DisabledUnselectedTrackColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val DisabledUnselectedTrackOutlineColor: (CssVariables) -> Color = { it.cardForeground }
    val HandleShape = ShapeKeyTokens.CornerFull
    val SelectedHandleColor: (CssVariables) -> Color = { it.primaryForeground }
    val SelectedHandleWidth = 24.0.dp
    val SelectedIconColor: (CssVariables) -> Color = { it.onPrimaryContainer }
    val SelectedTrackColor: (CssVariables) -> Color = { it.primary }
    val TrackHeight = 32.0.dp
    val TrackOutlineWidth = 2.0.dp
    val TrackShape = ShapeKeyTokens.CornerFull
    val TrackWidth = 52.0.dp
    val UnselectedFocusTrackOutlineColor: (CssVariables) -> Color = { it.border }
    val UnselectedHandleColor: (CssVariables) -> Color = { it.border }
    val UnselectedHandleWidth = 16.0.dp
    val UnselectedIconColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
    val UnselectedTrackColor: (CssVariables) -> Color = { it.surfaceContainerHighest }
}
