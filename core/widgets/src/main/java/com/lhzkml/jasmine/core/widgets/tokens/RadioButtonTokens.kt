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
// 只留本组件库真正用到的槽位（选中/未选中的图标色、禁用态的两组色与不透明度、图标尺寸与状态层尺寸）；
// 上游那份里的 Selected/Unselected 各三种交互态色（Focus*/Hover*/Pressed* 共 6 槽）没有任何组件在用
// （绘制里用的是基色 + 动画），已随裁剪删掉。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object RadioButtonTokens {
    val DisabledSelectedIconColor = ColorSchemeKeyTokens.OnSurface
    const val DisabledSelectedIconOpacity = 0.38f
    val DisabledUnselectedIconColor = ColorSchemeKeyTokens.OnSurface
    const val DisabledUnselectedIconOpacity = 0.38f
    val IconSize = 20.0.dp
    val SelectedIconColor = ColorSchemeKeyTokens.Primary
    val StateLayerSize = 40.0.dp
    val UnselectedIconColor = ColorSchemeKeyTokens.OnSurfaceVariant
}
