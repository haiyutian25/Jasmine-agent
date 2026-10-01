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
// 本组件库自己的共享小工具（移植自 AndroidX Material3 的 ColorScheme.kt 与 Badge.kt），
// 不再由上游生成，可直接改：底部导航栏与侧边栏共用的常量与徽标定位工具。

package com.lhzkml.jasmine.core.widgets.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.HorizontalRuler
import androidx.compose.ui.layout.VerticalRuler
import androidx.compose.ui.layout.layout

/** Disabled state layer alpha (upstream `ColorScheme.kt`). */
internal const val DisabledAlpha = 0.38f

/** Rulers that let a badge position itself against its anchor (upstream `Badge.kt`). */
internal val BadgeTopRuler = HorizontalRuler()
internal val BadgeEndRuler = VerticalRuler()

/** Wraps an anchor so that [BadgeTopRuler] and [BadgeEndRuler] report that anchor's bounds. */
internal fun Modifier.badgeBounds() =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(
            width = placeable.width,
            height = placeable.height,
            rulers = {
                BadgeEndRuler provides coordinates.size.width.toFloat()
                BadgeTopRuler provides 0f
            },
        ) {
            placeable.place(0, 0)
        }
    }
