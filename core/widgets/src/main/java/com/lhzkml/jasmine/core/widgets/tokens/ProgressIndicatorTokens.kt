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
// 只留本组件库真正用到的槽位（活动指示色与轨道色）；上游那份里的 Active/Stop/Track 三种形状
// 没有任何组件在用（绘制里用的是圆头笔画），已随裁剪删掉。
// 颜色槽位直读自有主题 `CssVariables`（Primary -> primary、SecondaryContainer -> subtleSurface），
// 取值与之前逐槽一致，只是不再经 M3 角色键映射。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object ProgressIndicatorTokens {
    val ActiveIndicatorColor: (CssVariables) -> Color = { it.primary }
    val TrackColor: (CssVariables) -> Color = { it.secondaryContainer }
}
