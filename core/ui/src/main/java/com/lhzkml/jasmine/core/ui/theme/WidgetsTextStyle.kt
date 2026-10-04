// 本应用自有的「当前文字样式」CompositionLocal。
//
// 上游对应物：LocalTextStyle（声明在 上游/Text.kt，默认 TextStyle.Default，
// 由主题用 ProvideTextStyle(typography.bodyLarge) 与组件各自 provide）。`Text.kt` 那一套本库
// 不搬，但我们的组件需要同一份「当前样式」，所以按同等位置收进主题层；`JasmineTheme` 会把本层级的值
// （bodyLarge + 用户选择的字体）原样灌进来，组件逐个切过来时观感不变。

package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.text.TextStyle

/** 上游对应：主题体系里的 `LocalTextStyle`（默认 [TextStyle.Default]）。 */
val LocalWidgetsTextStyle =
    compositionLocalOf(structuralEqualityPolicy()) { TextStyle.Default }
