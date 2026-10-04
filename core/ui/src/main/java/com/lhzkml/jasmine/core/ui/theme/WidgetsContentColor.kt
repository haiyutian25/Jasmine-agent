// 本应用自有的「内容色」CompositionLocal。
//
// 上游对应物：LocalContentColor（声明在 上游包根的 ContentColor.kt，
// 默认 Color.Black，由 Surface / 各组件自己 provide）。这里按同一位置把它收进我们的主题层：
// 组件不再读上游的 LocalContentColor，改读本文件这份；`JasmineTheme` 会把本层级的值原样灌进来
// （见 Theme.kt），所以组件逐个切过来时观感不变。

package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** 上游对应：主题体系里的 `LocalContentColor`（默认 [Color.Black]）。 */
val LocalWidgetsContentColor = compositionLocalOf { Color.Black }
