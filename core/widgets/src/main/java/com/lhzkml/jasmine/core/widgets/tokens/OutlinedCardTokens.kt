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
// 只保留 Card.kt 真正引用到的槽位（容器色 / 海拔 / 形状，描边色与宽度，
// 禁用态描边色 / 不透明度 / 海拔，以及拖拽态海拔）；上游那份里的 Focus / Hover / Pressed /
// Dragged 描边色与图标相关槽位本组件库没用到，已随裁剪删掉。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object OutlinedCardTokens {
    val ContainerColor = ColorSchemeKeyTokens.Surface
    val ContainerElevation = ElevationTokens.Level0
    val ContainerShape = ShapeKeyTokens.CornerMedium
    val DisabledContainerElevation = ElevationTokens.Level0
    val DisabledOutlineColor = ColorSchemeKeyTokens.Outline
    const val DisabledOutlineOpacity = 0.12f
    val DraggedContainerElevation = ElevationTokens.Level3
    val OutlineColor = ColorSchemeKeyTokens.OutlineVariant
    val OutlineWidth = 1.0.dp
}

