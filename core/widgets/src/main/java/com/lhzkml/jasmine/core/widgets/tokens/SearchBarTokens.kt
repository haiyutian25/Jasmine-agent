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
// 只留本组件库真正用到的槽位（搜索栏折叠态的颜色、高度与形状）；上游那份里的 Avatar/Focus/Hover/
// Pressed/字体 等槽位没有任何组件在用，已随裁剪删掉 —— 要加就照这里补一行。
// 颜色槽位直读自有主题 `CssVariables`（SurfaceContainerHigh -> subtleSurface、OnSurface ->
// cardForeground、OnSurfaceVariant -> mutedForeground），取值与之前逐槽一致，不再经 M3 角色键映射。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables


internal object SearchBarTokens {
    val ContainerColor: (CssVariables) -> Color = { it.surfaceContainerHigh }
    val ContainerHeight = 56.0.dp
    val ContainerShape = ShapeKeyTokens.CornerFull
    val InputTextColor: (CssVariables) -> Color = { it.cardForeground }
    val LeadingIconColor: (CssVariables) -> Color = { it.cardForeground }
    val SupportingTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val TrailingIconColor: (CssVariables) -> Color = { it.mutedForeground }
}
