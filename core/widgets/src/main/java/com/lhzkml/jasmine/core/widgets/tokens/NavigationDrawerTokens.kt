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
// 本项目自有的设计令牌（来源：AndroidX Material3 token VERSION v0_210），不再由上游生成，可直接改。
// 只留本组件库真正用到的槽位（选中/未选中的图标与标签颜色、指示条的颜色尺寸形状、抽屉两种容器的
// 颜色与高度）；上游那份里的 Focus/Hover/Pressed 三态、宽轨时代的 Headline/Badge/图标尺寸/宽度百分比等
// 槽位没有任何组件在用，已随裁剪删掉 —— 要加就照这里补一行（若引用了 ColorScheme/Shape/Typography/
// Elevation 的键，也要在 TokenResolvers.kt 里补对应分支）。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object NavigationDrawerTokens {
    val ActiveIconColor = ColorSchemeKeyTokens.OnSecondaryContainer
    val ActiveIndicatorColor = ColorSchemeKeyTokens.SecondaryContainer
    val ActiveIndicatorHeight = 56.0.dp
    val ActiveIndicatorShape = ShapeKeyTokens.CornerFull
    val ActiveLabelTextColor = ColorSchemeKeyTokens.OnSecondaryContainer
    val ContainerShape = ShapeKeyTokens.CornerLargeEnd
    val ContainerWidth = 360.0.dp
    val InactiveIconColor = ColorSchemeKeyTokens.OnSurfaceVariant
    val InactiveLabelTextColor = ColorSchemeKeyTokens.OnSurfaceVariant
    val ModalContainerColor = ColorSchemeKeyTokens.SurfaceContainerLow
    val StandardContainerColor = ColorSchemeKeyTokens.Surface
    val StandardContainerElevation = ElevationTokens.Level0
}
