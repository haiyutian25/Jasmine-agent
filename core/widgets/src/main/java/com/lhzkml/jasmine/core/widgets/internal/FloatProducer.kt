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

// 本项目自有的组件代码（移植自 AndroidX Material3 对应源码后自行维护），不再跟随上游生成，可直接改。
// 上游位置：androidx/compose/material3/internal/TextFieldImpl.kt（M3 的 internal 共享件）——
// 输入框 / 顶部栏 / 侧边栏 / 下拉刷新都在用它，所以从 textfield 包挪到 internal 包。
// 符号名与上游一致（FloatProducer）。

package com.lhzkml.jasmine.core.widgets.internal

/**
 * Alternative to `() -> Float` but avoids boxing.
 *
 * !!! Do not use in public APIs !!!
 */

internal fun interface FloatProducer {
    /** Returns the Float. */
    operator fun invoke(): Float
}
