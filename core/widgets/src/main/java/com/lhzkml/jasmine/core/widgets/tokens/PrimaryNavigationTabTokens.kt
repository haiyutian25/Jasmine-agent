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
// 只保留 Tab / TabRow 真正引用到的槽位（活动指示条的色/高/形状、活动标签文字色、容器色与高、标签字体）；
// 上游那份里的 Focus / Hover / Pressed 态色与图标相关槽位本组件库都没用到，已随裁剪删掉。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

internal object PrimaryNavigationTabTokens {
    val ActiveIndicatorColor = ColorSchemeKeyTokens.Primary
    val ActiveIndicatorHeight = 3.0.dp
    val ActiveIndicatorShape = RoundedCornerShape(3.0.dp)
    val ContainerColor = ColorSchemeKeyTokens.Surface
    val ContainerHeight = 48.0.dp
    val ActiveLabelTextColor = ColorSchemeKeyTokens.Primary
    val LabelTextFont = TypographyKeyTokens.TitleSmall
}

