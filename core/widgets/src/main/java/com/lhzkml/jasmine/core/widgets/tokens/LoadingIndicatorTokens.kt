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
// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 加载指示器（形状变换型）用到的全部 7 个槽位；上游这份里没有未使用的槽位。
// 颜色槽位直读自有主题 `CssVariables`（Primary -> primary、OnPrimaryContainer -> accentForeground、
// PrimaryContainer -> accent），取值与之前逐槽一致，只是不再经 M3 角色键映射。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object LoadingIndicatorTokens {
    val ActiveIndicatorColor: (CssVariables) -> Color = { it.primary }
    val ActiveSize = 38.0.dp
    val ContainedActiveColor: (CssVariables) -> Color = { it.accentForeground }
    val ContainedContainerColor: (CssVariables) -> Color = { it.accent }
    val ContainerHeight = 48.0.dp
    val ContainerShape = ShapeKeyTokens.CornerFull
    val ContainerWidth = 48.0.dp
}
