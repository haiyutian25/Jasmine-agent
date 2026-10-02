// 本应用自有的「当前文字样式」CompositionLocal。
//
// 上游对应物：androidx.compose.material3.LocalTextStyle（声明在 material3/Text.kt，默认 TextStyle.Default，
// 由 MaterialTheme 用 ProvideTextStyle(typography.bodyLarge) 与组件各自 provide）。`Text.kt` 那一套本库
// 不搬，但我们的组件需要同一份「当前样式」，所以按同等位置收进主题层；`JasmineTheme` 会把本层级的 M3 值
// （bodyLarge + 用户选择的字体）原样灌进来，组件逐个切过来时观感不变。

package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.text.TextStyle

/** 上游对应：`MaterialTheme` 体系里的 `LocalTextStyle`（默认 [TextStyle.Default]）。 */
val LocalWidgetsTextStyle =
    compositionLocalOf(structuralEqualityPolicy()) { TextStyle.Default }
