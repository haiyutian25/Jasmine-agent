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
// 本项目自有的设计令牌（来源：AndroidX Material3 token VERSION v0_103），不再由上游生成，可直接改。
// 只保留 Card.kt 真正引用到的槽位（容器色 / 海拔 / 形状，禁用态色 / 海拔 / 不透明度，
// 以及拖拽 / 聚焦 / 悬停 / 按下四态海拔）；上游那份里的 FocusIndicatorColor 与图标相关槽位
// 本组件库没用到，已随裁剪删掉。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object FilledCardTokens {
    val ContainerColor = ColorSchemeKeyTokens.SurfaceContainerHighest
    val ContainerElevation = ElevationTokens.Level0
    val ContainerShape = ShapeKeyTokens.CornerMedium
    val DisabledContainerColor = ColorSchemeKeyTokens.SurfaceVariant
    val DisabledContainerElevation = ElevationTokens.Level0
    val DisabledContainerOpacity = 0.38f
    val DraggedContainerElevation = ElevationTokens.Level3
    val FocusContainerElevation = ElevationTokens.Level0
    val HoverContainerElevation = ElevationTokens.Level1
    val PressedContainerElevation = ElevationTokens.Level0
}

