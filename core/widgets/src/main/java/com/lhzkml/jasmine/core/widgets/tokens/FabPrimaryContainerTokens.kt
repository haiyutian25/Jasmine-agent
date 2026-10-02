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
// 只留本组件库真正用到的槽位（FAB 菜单的阴影高度）；上游那份里的各种颜色与 Focus/Hover/Pressed
// 三态高度没有任何组件在用，已随裁剪删掉 —— 要加就照这里补一行（若引用了 ColorScheme/Shape/
// Typography/Elevation 的键，也要在 TokenResolvers.kt 里补对应分支）。

package com.lhzkml.jasmine.core.widgets.tokens

internal object FabPrimaryContainerTokens {
    val ContainerElevation = ElevationTokens.Level3
}
