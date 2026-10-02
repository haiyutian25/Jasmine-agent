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

// 本项目自有的组件代码（移植自 AndroidX Material3 对应源码后自行维护），不再跟随上游生成，可直接改。
//
// 与上游的差别：上游这个 `Strings` 枚举整份指向 **M3 自己的资源**（`material3.R.string.m3c_*`）
// 与少数 compose-ui 资源（`androidx.compose.ui.R.string.*`）；本库改成指向**自己的**
// `core/widgets/src/main/res/values(-zh-rCN)/strings.xml`（`widgets_*`），于是组件库不再借
// 任何外部模块的文案资源。枚举只保留组件库里真正被引用的 14 条（上游 70 余条里其余零引用，
// 已随裁剪删掉 —— 要加就照这里补一行，并在两个 strings.xml 里各加一条）。

package com.lhzkml.jasmine.core.widgets.textfield

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.lhzkml.jasmine.core.widgets.R

@Composable
@ReadOnlyComposable
internal fun getString(string: Strings): String {
    LocalConfiguration.current
    val resources = LocalContext.current.resources
    return resources.getString(string.value)
}

@JvmInline
@Immutable
internal value class Strings constructor(val value: Int) {
    companion object {
        /** 输入框没有指定错误信息时的无障碍提示。 */
        inline val DefaultErrorMessage
            get() = Strings(R.string.widgets_error_default)

        /** 对话框面板名。 */
        inline val Dialog
            get() = Strings(R.string.widgets_dialog_pane_title)

        /** 下拉菜单展开态。 */
        inline val MenuExpanded
            get() = Strings(R.string.widgets_menu_expanded)

        /** 下拉菜单收起态。 */
        inline val MenuCollapsed
            get() = Strings(R.string.widgets_menu_collapsed)

        /** 展开/收起下拉菜单。 */
        inline val ToggleDropdownMenu
            get() = Strings(R.string.widgets_menu_toggle)

        /** 搜索框的"搜索"。 */
        inline val SearchBarSearch
            get() = Strings(R.string.widgets_search_bar_search)

        /** 搜索框有可用建议。 */
        inline val SuggestionsAvailable
            get() = Strings(R.string.widgets_suggestions_available)

        /** 底部弹层面板名。 */
        inline val BottomSheetPaneTitle
            get() = Strings(R.string.widgets_bottom_sheet_pane_title)

        /** 拖拽把手。 */
        inline val BottomSheetDragHandleDescription
            get() = Strings(R.string.widgets_bottom_sheet_drag_handle_description)

        /** 弹层"部分展开"。 */
        inline val BottomSheetPartialExpandDescription
            get() = Strings(R.string.widgets_bottom_sheet_collapse_description)

        /** 弹层"关闭"。 */
        inline val BottomSheetDismissDescription
            get() = Strings(R.string.widgets_bottom_sheet_dismiss_description)

        /** 弹层"已展开"。 */
        inline val BottomSheetExpandDescription
            get() = Strings(R.string.widgets_bottom_sheet_expand_description)

        /** 关闭弹层。 */
        inline val CloseSheet
            get() = Strings(R.string.widgets_close_sheet)

        /** 侧边栏（导航抽屉）菜单名。 */
        inline val NavigationMenu
            get() = Strings(R.string.widgets_navigation_menu)
    }
}
