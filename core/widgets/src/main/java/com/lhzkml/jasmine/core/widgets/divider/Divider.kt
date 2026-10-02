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
// 本项目自有的组件代码（移植自 AndroidX Material3 1.4.0 的 Divider.kt 后自行维护），不再跟随上游生成，可直接改。
// 只保留两个正式入口：HorizontalDivider 与 VerticalDivider —— 上游那个已废弃的 `Divider`（重命名前的旧名）没搬。
// 默认值走 tokens/DividerTokens：Thickness = 1dp、Color = OutlineVariant → 本应用主题的 muted。

package com.lhzkml.jasmine.core.widgets.divider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.lhzkml.jasmine.core.widgets.tokens.DividerTokens
import com.lhzkml.jasmine.core.widgets.tokens.value

/**
 * [Material Design divider](https://m3.material.io/components/divider/overview)
 *
 * 一条横向细线，用于在列表与布局里分组内容。`TabRow` 的默认分隔线与 `SearchBar` 里
 * 输入框与结果之间的那条线用的都是它。
 *
 * @param modifier 应用到这条线上的 [Modifier]。
 * @param thickness 线的粗细；传 [Dp.Hairline] 会得到"与屏幕密度无关的单像素"线。
 * @param color 线的颜色。
 */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color,
) =
    Canvas(modifier.fillMaxWidth().height(thickness)) {
        drawLine(
            color = color,
            strokeWidth = thickness.toPx(),
            start = Offset(0f, thickness.toPx() / 2),
            end = Offset(size.width, thickness.toPx() / 2),
        )
    }

/**
 * [Material Design divider](https://m3.material.io/components/divider/overview)
 *
 * 一条纵向细线，用法同 [HorizontalDivider]，只是方向不同。
 *
 * @param modifier 应用到这条线上的 [Modifier]。
 * @param thickness 线的粗细；传 [Dp.Hairline] 会得到"与屏幕密度无关的单像素"线。
 * @param color 线的颜色。
 */
@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color,
) =
    Canvas(modifier.fillMaxHeight().width(thickness)) {
        drawLine(
            color = color,
            strokeWidth = thickness.toPx(),
            start = Offset(thickness.toPx() / 2, 0f),
            end = Offset(thickness.toPx() / 2, size.height),
        )
    }

/** [HorizontalDivider] 与 [VerticalDivider] 的默认值。 */
object DividerDefaults {
    /** 默认粗细。 */
    val Thickness: Dp = DividerTokens.Thickness

    /** 默认颜色（= 本应用主题的 `muted`，与上游 `OutlineVariant` 对应）。 */
    val color: Color
        @Composable get() = DividerTokens.Color.value
}
