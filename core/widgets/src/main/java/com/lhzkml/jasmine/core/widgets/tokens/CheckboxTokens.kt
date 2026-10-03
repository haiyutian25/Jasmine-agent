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
// 只留本组件库真正用到的槽位（选中/未选中的容器色与描边色、禁用态透明度、选中图标色、状态层尺寸）；
// 上游那份里的 Focus/Hover/Pressed/Error 三态成对色、容器尺寸与形状、图标尺寸、各档描边宽度
// 都没有任何组件在用（绘制里用的字面量），已随裁剪删掉。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables

import androidx.compose.ui.unit.dp

internal object CheckboxTokens {
    val SelectedContainerColor: (CssVariables) -> Color = { it.primary }
    val SelectedDisabledContainerColor: (CssVariables) -> Color = { it.cardForeground }
    const val SelectedDisabledContainerOpacity = 0.38f
    val SelectedIconColor: (CssVariables) -> Color = { it.primaryForeground }
    val StateLayerSize = 40.0.dp
    const val UnselectedDisabledContainerOpacity = 0.38f
    val UnselectedDisabledOutlineColor: (CssVariables) -> Color = { it.cardForeground }
    val UnselectedOutlineColor: (CssVariables) -> Color = { it.mutedForeground }
}
