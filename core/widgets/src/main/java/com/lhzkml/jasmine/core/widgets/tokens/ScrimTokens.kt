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
//
// 遮罩色直接写死同一个值：`CssVariables` 没有遮罩槽位（12 套调色板都没定义，浅色/深色本来也都是纯黑）。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color

internal object ScrimTokens {
    /** 遮罩色：M3 基线值（浅色/深色两套都是纯黑）。 */
    val ContainerColor: Color = Color(0xFF000000)
    const val ContainerOpacity = 0.32f
}
