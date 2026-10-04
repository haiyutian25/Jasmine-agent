/*
 * Copyright 2023 The Android Open Source Project
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
// 上游位置：internal/TextFieldImpl.kt（上游把它声明在 internal 共享文件里，
// 组件侧只是 internal 调用），顶栏 / 底栏 / 徽标 / 对话框 / 输入框都在用，所以挪到 internal 包。
// 符号名与上游一致（`ProvideContentColorTextStyle`）。
//
// 这里下发的是**自有**的 local：`LocalWidgetsContentColor` / `LocalWidgetsTextStyle`（core/ui/theme，
// 由 JasmineTheme 注入）—— 下游内容件（Text / Icon）也已是自有实现，两边配对一致，本文件无 上游依赖。

package com.lhzkml.jasmine.core.widgets.internal

import com.lhzkml.jasmine.core.ui.theme.LocalWidgetsContentColor
import com.lhzkml.jasmine.core.ui.theme.LocalWidgetsTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * ProvideContentColorTextStyle
 *
 * A convenience method to provide values to both LocalWidgetsContentColor and LocalWidgetsTextStyle in one call.
 * This is less expensive than nesting calls to CompositionLocalProvider.
 *
 * Text styles will be merged with the current value of LocalWidgetsTextStyle.
 */

@Composable
internal fun ProvideContentColorTextStyle(
    contentColor: Color,
    textStyle: TextStyle,
    content: @Composable () -> Unit,
) {
    val mergedStyle = LocalWidgetsTextStyle.current.merge(textStyle)
    CompositionLocalProvider(
        LocalWidgetsContentColor provides contentColor,
        LocalWidgetsTextStyle provides mergedStyle,
        content = content,
    )
}
