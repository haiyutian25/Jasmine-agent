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
// 本项目自有的设计令牌（来源：AndroidX Material3 token VERSION v0_11_0），不再由上游生成，可直接改。
// 只留本组件库真正用到的槽位（底栏容器色、条目三种颜色、指示条形状、高版高度与标签字体）；
// 上游那份里的容器高度/高度阴影/条目间距/底栏形状等槽位没有任何组件在用，已随裁剪删掉。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object NavigationBarTokens {
    val ContainerColor = ColorSchemeKeyTokens.SurfaceContainer
    val ItemActiveIconColor = ColorSchemeKeyTokens.OnSecondaryContainer
    val ItemActiveIndicatorColor = ColorSchemeKeyTokens.SecondaryContainer
    val ItemActiveIndicatorShape = ShapeKeyTokens.CornerFull
    val ItemActiveLabelTextColor = ColorSchemeKeyTokens.Secondary
    val ItemInactiveIconColor = ColorSchemeKeyTokens.OnSurfaceVariant
    val ItemInactiveLabelTextColor = ColorSchemeKeyTokens.OnSurfaceVariant
    val TallContainerHeight = 80.0.dp
    val LabelTextFont = TypographyKeyTokens.LabelMedium
}
