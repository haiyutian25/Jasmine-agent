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
// 只留本组件库真正用到的槽位（底栏容器色、条目三种颜色、指示条形状、高版高度与标签字体）；
// 上游那份里的容器高度/高度阴影/条目间距/底栏形状等槽位没有任何组件在用，已随裁剪删掉。
//
// 与上游的差别：上游这里是 M3 的**颜色角色键**（`ColorSchemeKeyTokens.*`）与字体键
// （`TypographyKeyTokens.*`），再由 `fromToken` 映射到 M3 主题；本库不再做这层「键 → 主题」映射 ——
// 颜色槽位直接在**自有主题** `CssVariables` 上实现、字体直接取自有排版档，取值与之前逐槽一致：
//   ContainerColor            SurfaceContainer   -> CssVariables.card
//   ItemActiveIconColor       OnSecondaryContainer -> CssVariables.mutedForeground
//   ItemActiveIndicatorColor  SecondaryContainer -> CssVariables.subtleSurface
//   ItemActiveLabelTextColor  Secondary          -> CssVariables.mutedForeground
//   ItemInactive*Color        OnSurfaceVariant -> CssVariables.mutedForeground
//   LabelTextFont             LabelMedium        -> AppTypography.labelMedium
// 形状与高度仍是纯令牌（形状走 `ShapeKeyTokens` → `LocalWidgetsShapes`，高度是 dp）。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.AppTypography
import com.lhzkml.jasmine.core.ui.theme.CssVariables

internal object NavigationBarTokens {
    val ContainerColor: (CssVariables) -> Color = { it.card }
    val ItemActiveIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ItemActiveIndicatorColor: (CssVariables) -> Color = { it.subtleSurface }
    val ItemActiveIndicatorShape = ShapeKeyTokens.CornerFull
    val ItemActiveLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val ItemInactiveIconColor: (CssVariables) -> Color = { it.mutedForeground }
    val ItemInactiveLabelTextColor: (CssVariables) -> Color = { it.mutedForeground }
    val TallContainerHeight = 80.0.dp

    /** 标签字体：直接取自有排版档，不再经字体令牌键映射。 */
    val LabelTextFont: TextStyle
        @Composable @ReadOnlyComposable get() = AppTypography.labelMedium
}
