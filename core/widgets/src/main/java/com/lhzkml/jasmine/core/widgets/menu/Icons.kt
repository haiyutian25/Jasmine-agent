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
// 本项目自有的组件代码（移植自 AndroidX Material3 1.4.0 的 internal/Icons.kt 后自行维护），
// 不再跟随上游生成，可直接改。上游那份 419 行、几十个图标；本组件库目前只用到 Filled.ArrowDropDown
// 一个（ExposedDropdownMenuDefaults.TrailingIcon 的下拉箭头），故按可达性只留它，绘制改用标准的
// ImageVector.Builder（等价字形，路径数据照抄上游）。以后要用别的图标，照上游各补一个即可。

package com.lhzkml.jasmine.core.widgets.menu

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object Icons {
    internal object Filled {
        /** The drop-down arrow (Material ArrowDropDown, 24dp viewport). */
        val ArrowDropDown: ImageVector by lazy {
            ImageVector.Builder(
                name = "Filled.ArrowDropDown",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(7.0f, 10.0f)
                    lineToRelative(5.0f, 5.0f)
                    lineToRelative(5.0f, -5.0f)
                    close()
                }
            }.build()
        }
    }
}
