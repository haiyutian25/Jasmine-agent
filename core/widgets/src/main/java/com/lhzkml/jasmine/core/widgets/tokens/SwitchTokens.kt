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
// 本项目自有的设计令牌（来源：AndroidX Material3 token VERSION v0_210），不再由上游生成，可直接改。
// 这里只留本组件实际用到的槽位；上游那份里的 Hover / Focus / Pressed 配色与部分尺寸槽位
// 我们从不渲染（SwitchColors 只有 选中 / 未选中 / 禁用 三态），已随本次瘦身一并删掉。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object SwitchTokens {
    val DisabledSelectedHandleColor = ColorSchemeKeyTokens.Surface
    val DisabledSelectedHandleOpacity = 1.0f
    val DisabledSelectedIconColor = ColorSchemeKeyTokens.OnSurface
    val DisabledSelectedIconOpacity = 0.38f
    val DisabledSelectedTrackColor = ColorSchemeKeyTokens.OnSurface
    val DisabledTrackOpacity = 0.12f
    val DisabledUnselectedHandleColor = ColorSchemeKeyTokens.OnSurface
    val DisabledUnselectedHandleOpacity = 0.38f
    val DisabledUnselectedIconColor = ColorSchemeKeyTokens.SurfaceContainerHighest
    val DisabledUnselectedIconOpacity = 0.38f
    val DisabledUnselectedTrackColor = ColorSchemeKeyTokens.SurfaceContainerHighest
    val DisabledUnselectedTrackOutlineColor = ColorSchemeKeyTokens.OnSurface
    val HandleShape = ShapeKeyTokens.CornerFull
    val SelectedHandleColor = ColorSchemeKeyTokens.OnPrimary
    val SelectedHandleWidth = 24.0.dp
    val SelectedIconColor = ColorSchemeKeyTokens.OnPrimaryContainer
    val SelectedTrackColor = ColorSchemeKeyTokens.Primary
    val TrackHeight = 32.0.dp
    val TrackOutlineWidth = 2.0.dp
    val TrackShape = ShapeKeyTokens.CornerFull
    val TrackWidth = 52.0.dp
    val UnselectedFocusTrackOutlineColor = ColorSchemeKeyTokens.Outline
    val UnselectedHandleColor = ColorSchemeKeyTokens.Outline
    val UnselectedHandleWidth = 16.0.dp
    val UnselectedIconColor = ColorSchemeKeyTokens.SurfaceContainerHighest
    val UnselectedTrackColor = ColorSchemeKeyTokens.SurfaceContainerHighest
}
