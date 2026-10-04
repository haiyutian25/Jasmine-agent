// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：androidx/compose/material3/tokens/TypefaceTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 字体族与字重令牌；字阶表 TypeScaleTokens 引用这里的 Font / Weight 槽。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal object TypefaceTokens {
    val Brand = FontFamily.SansSerif
    val Plain = FontFamily.SansSerif
    val WeightBold = FontWeight.Bold
    val WeightMedium = FontWeight.Medium
    val WeightRegular = FontWeight.Normal
}
