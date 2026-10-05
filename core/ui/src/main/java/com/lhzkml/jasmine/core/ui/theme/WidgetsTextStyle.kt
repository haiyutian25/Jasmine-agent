// 本应用自有的「当前文字样式」CompositionLocal。
//
// 上游对应物：LocalTextStyle（声明在 上游/Text.kt，默认 TextStyle.Default，
// 由主题用 ProvideTextStyle(typography.bodyLarge) 与组件各自 provide）。`Text.kt` 那一套本库
// 不搬，但我们的组件需要同一份「当前样式」，所以按同等位置收进主题层；`JasmineTheme` 会把本层级的值
// （bodyLarge + 用户选择的字体）灌进来，组件逐个切过来时观感不变。
//
// 默认值：与上游主题体系里的 `LocalTextStyle` 默认值同源 —— 上游那侧的默认不是裸的
// `TextStyle.Default`，而是主题自己的 `DefaultTextStyle`（`tokens/DefaultTextStyle.kt`：
// `includeFontPadding = false` + `LineHeightStyle(Center, Trim.None)`）。此前这里给的是裸默认，
// 「没有主题包裹」（预览、单测、单独用组件）时会退化到平台默认行高口径。取值与
// `core:widgets` 的 `tokens/DefaultTextStyle.kt` 逐项一致（那份是 internal，跨模块取不到，故就地写一份）。

package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle

/** 上游对应：主题体系里的 `LocalTextStyle`（默认值见文件头说明）。 */
val LocalWidgetsTextStyle =
    compositionLocalOf(structuralEqualityPolicy()) {
        TextStyle.Default.copy(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle =
                LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
        )
    }
