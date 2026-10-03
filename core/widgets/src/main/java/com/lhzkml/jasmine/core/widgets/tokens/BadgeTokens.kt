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
// 只留本组件库真正用到的槽位（小圆点与带内容徽标的尺寸/形状/底色/字体）；上游那份里的 LargeColor
// 与 LargeLabelTextColor 没有任何组件在用，已随裁剪删掉。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables

import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.AppTypography

internal object BadgeTokens {
    val Color: (CssVariables) -> Color = { it.error }
    val LargeLabelTextFont = AppTypography.labelSmall
    val LargeShape = ShapeKeyTokens.CornerFull
    val LargeSize = 16.0.dp
    val Shape = ShapeKeyTokens.CornerFull
    val Size = 6.0.dp
}
