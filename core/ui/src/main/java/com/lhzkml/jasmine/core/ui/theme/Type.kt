package com.lhzkml.jasmine.core.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.R

/**
 * App-wide content font family, driven by the user's typography choice
 * (Editorial / Sans / Mono) and provided near the root. Content text —
 * headings, labels, body, buttons — should read this. Code & technical text
 * (CSS snippets, token names, hex values, numeric readouts) intentionally keep
 * [FontFamily.Monospace] and must NOT read this.
 */
val LocalContentFontFamily = compositionLocalOf<FontFamily> { FontFamily.SansSerif }

/**
 * 全应用的排版选择（EDITORIAL / SANS / MONO）—— 排版引擎的**唯一**枚举。
 *
 * 谁在用它：聊天外壳的状态（`MainState.typographyChoice` / `activeContentFont`，驱动
 * [LocalContentFontFamily]）、字体设置页的选择器、字号页与类型预览（没有按屏幕镜像的枚举）。
 *
 * 归属说明：它原先定义在 `feature:settings:impl` 的 screens 包里，于是 `feature:main:impl` 的
 * ViewModel 得反过来依赖**另一个 feature 的界面包**（P1-10/V-10）。它是排版概念、不是设置页私产，
 * 而且两边的界面本来就都依赖 `core:ui` —— 所以放在这里与 [AppTypography] 同处。
 *
 * 持久化按名字（`name`）；每个取值自带它的显示文案与映射到的 [FontFamily]。
 * [labelRes] 是选择器里的短标签（Serif/Sans/Mono），[titleRes] 是设置页上的正式名。
 */
enum class AppTypographyChoice(
    @StringRes val titleRes: Int,
    @StringRes val labelRes: Int,
    val font: FontFamily,
) {
    EDITORIAL(R.string.settings_typography_editorial_title, R.string.font_label_serif, FontFamily.Serif),
    SANS(R.string.settings_typography_sans_title, R.string.font_label_sans, FontFamily.SansSerif),
    MONO(R.string.settings_typography_mono_title, R.string.font_label_mono, FontFamily.Monospace)
}

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 46.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1.2).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.8).sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    // 顶部栏标题那一档。上游标题栏用的是 M3 基线的 TitleLarge（22sp / 行高 28sp / 字距 0 /
    // 字重 Regular / FontFamily.SansSerif），本库把它收进自有主题、数值逐项照抄，观感与之前完全一致。
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    // Tooltip 正文那一档。上游 PlainTooltip 用 M3 基线的 BodySmall（12sp / 行高 16sp /
    // 字距 0.4sp / 字重 Regular），本库把它收进自有主题、数值逐项照抄，观感不变。
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp
    )
)

