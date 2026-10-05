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

// 本项目自有的组件代码（移植自上游对应源码后自行维护），不再跟随上游生成，可直接改。

package com.lhzkml.jasmine.core.widgets.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 本层级的 [MotionScheme]。上游把它放在主题里（`MaterialTheme.motionScheme`）；
 * 我们的主题层（`core:ui`）在依赖方向上位于本模块**上游**，取不到这里的类型，所以
 * 由本模块自己持有并提供一个注入入口 [ProvideMotionScheme]。
 *
 * 没有宿主提供时用 standard 动效 —— 这与上游默认一致。
 */
val LocalMotionScheme = staticCompositionLocalOf { MotionScheme.standard() }

/**
 * 在 [content] 子树内改用给定的 [motionScheme]。
 *
 * 上游对应物是主题参数 `MaterialTheme(motionScheme = ...)`。这里做成组合器，是因为主题层
 * 拿不到本模块的类型（见 [LocalMotionScheme] 的说明）；调用方（feature / app 层）可以在
 * 树中任意位置切换，例如包在某个"重点交互"页面上：
 * ```
 * ProvideMotionScheme(MotionScheme.expressive()) { PromoPage() }
 * ```
 * 不调用它时全 app 恒定 [MotionScheme.standard]，与改造前完全一致（本库此前没有任何提供方）。
 */
@Composable
fun ProvideMotionScheme(
    motionScheme: MotionScheme,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMotionScheme provides motionScheme, content = content)
}
