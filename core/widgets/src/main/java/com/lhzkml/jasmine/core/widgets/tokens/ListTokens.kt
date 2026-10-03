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
// 只为菜单家族保留（Menu.kt 的 DropdownMenuItem 取色用）：标签/前后图标色、禁用态的两组色与不透明度、图标尺寸；
// 上游那份还有 Avatar/Dragged/Focus/Hover/Pressed/Selected 以及各种尺寸排版槽位，本组件库都没用到，已随裁剪删掉。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables

import androidx.compose.ui.unit.dp

internal object ListTokens {
    val ListItemDisabledLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val ListItemDisabledLabelTextOpacity = 0.38f
    val ListItemDisabledLeadingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val ListItemDisabledLeadingIconOpacity = 0.38f
    val ListItemDisabledTrailingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val ListItemDisabledTrailingIconOpacity = 0.38f
    val ListItemLabelTextColor: (CssVariables) -> Color = { it.cardForeground }
    val ListItemLeadingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemLeadingIconSize = 24.0.dp
    val ListItemTrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ListItemTrailingIconSize = 24.0.dp
}

