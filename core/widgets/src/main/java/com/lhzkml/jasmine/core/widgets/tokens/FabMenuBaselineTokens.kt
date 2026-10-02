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
// 本项目自有的设计令牌（来源：AndroidX Material3 token VERSION v0_14_0），不再由上游生成，可直接改。
// 只留本组件库真正用到的槽位（FAB 菜单的关闭按钮尺寸与条目尺寸/间距/形状）；上游那份里的
// CloseButtonContainerShape/ContainerWidth/ContainerElevation 与 ListItemContainerElevation 没有任何
// 组件在用（形状与高度按数学算、阴影取 FabPrimaryContainerTokens），已随裁剪删掉。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object FabMenuBaselineTokens {
    val CloseButtonBetweenSpace = 8.0.dp
    val CloseButtonContainerHeight = 56.0.dp
    val CloseButtonIconSize = 20.0.dp
    val ListItemBetweenSpace = 4.0.dp
    val ListItemContainerHeight = 56.0.dp
    val ListItemContainerShape = ShapeKeyTokens.CornerFull
    val ListItemIconLabelSpace = 8.0.dp
    val ListItemLeadingSpace = 24.0.dp
    val ListItemTrailingSpace = 24.0.dp
}
