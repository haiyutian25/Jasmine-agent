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
// 只留本组件库真正用到的槽位（单值滑块的轨道/把手颜色与尺寸、禁用态透明度、刻度点尺寸）；
// 上游那份里的 Active*/Focus*/Hover*/Pressed*/Stop*/ValueIndicator*/Label* 等槽位没有任何组件在用，
// 已随裁剪删掉 —— 要加就照这里补一行。
//
// 与上游的差别：颜色槽不再走 M3 角色键（`Primary` / `OnSurface` / `SecondaryContainer`），
// 改成 `(CssVariables) -> Color` 直读自有主题（Primary -> primary、OnSurface -> cardForeground、
// SecondaryContainer -> subtleSurface，与 `TokenResolvers` 的镜像表逐条一致，取值不变）。
// 形状（`ShapeKeyTokens.CornerFull`）仍是形状令牌，照旧。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object SliderTokens {
    val ActiveHandleLeadingSpace = 6.0.dp
    val ActiveTrackColor: (CssVariables) -> Color = { it.primary }
    val DisabledActiveTrackColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledActiveTrackOpacity = 0.38f
    val DisabledHandleColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledHandleOpacity = 0.38f
    val DisabledInactiveTrackColor: (CssVariables) -> Color = { it.cardForeground }
    val DisabledInactiveTrackOpacity = 0.12f
    val HandleColor: (CssVariables) -> Color = { it.primary }
    val HandleHeight = 44.0.dp
    val HandleShape = ShapeKeyTokens.CornerFull
    val HandleWidth = 4.0.dp
    val InactiveTrackColor: (CssVariables) -> Color = { it.subtleSurface }
    val InactiveTrackHeight = 16.0.dp
    val StopIndicatorSize = 4.0.dp
}
