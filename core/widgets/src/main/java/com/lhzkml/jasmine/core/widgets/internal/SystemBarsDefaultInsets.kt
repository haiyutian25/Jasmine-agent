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

// 本项目自有的组件代码（移植自上游对应源码后自行维护），不再跟随上游生成，可直接改。
// 上游位置：internal/SystemBarsDefaultInsets.kt —— 它是 上游的 **internal 共享件**，
// 顶栏 / 底栏 / 导航栏 / 抽屉 / 宽导航轨都用它取「系统栏 + 刘海」的 insets，所以放在同一个 internal 包里，
// 而不是挂在某个具体组件目录下。符号名与上游一致（`WindowInsets.Companion.systemBarsForVisualComponents`）。

package com.lhzkml.jasmine.core.widgets.internal

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

internal val WindowInsets.Companion.systemBarsForVisualComponents: WindowInsets
    @Composable get() = systemBars.union(displayCutout)
