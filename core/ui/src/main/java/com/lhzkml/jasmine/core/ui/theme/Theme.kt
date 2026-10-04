package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 本应用的主题入口。只下发**自有**的 CompositionLocal：
 *
 * * 颜色：[LocalCssVariables]（各组件与令牌表都直读它）
 * * 形状：[LocalWidgetsShapes]（注入自有 [AppShapes]）
 * * 内容色：[LocalWidgetsContentColor]
 * * 文字样式：[LocalWidgetsTextStyle]（= [AppTypography.bodyLarge] + 用户选择的字体）
 *
 * 主题层不再包一层上游主题，只下发自有 local；上游那套
 * `ColorScheme` / `Shapes` / `Typography` / `LocalContentColor` / `LocalTextStyle` 已无任何读取方。
 * 观感与改造前一致 —— 原先把值灌进上游角色只是为了喂上游组件，而那些组件现在都换成自有实现了。
 */
@Composable
fun JasmineTheme(
    cssVars: CssVariables = ProductionPalettes.GeistDark,
    content: @Composable () -> Unit
) {
    val contentFont = LocalContentFontFamily.current

    CompositionLocalProvider(
        LocalCssVariables provides cssVars,
        // LocalWidgets* 是给 core:widgets 里那些不在组合环境中的结点（ModifierNode）用的：
        // 它们读不到组合局部，只能通过 CompositionLocal 拿到同一份值。
        LocalWidgetsShapes provides AppShapes,
        // 默认内容色取主题前景色。语义与原先主题下的 LocalContentColor 相同
        // （上游那边会解析成 colorScheme.onBackground，正是 cssVars.foreground）。
        LocalWidgetsContentColor provides cssVars.foreground,
        // 默认文字样式 = bodyLarge + 用户选择的字体：没自己钉字体的 Text 都继承它；
        // 显式设了 fontFamily 的（代码、字体预览、装饰字母）用各自的，不受影响。
        LocalWidgetsTextStyle provides AppTypography.bodyLarge.copy(fontFamily = contentFont),
    ) {
        content()
    }
}

/** 由 [JasmineTheme] 注入的自有形状表（对应上游主题的 shapes）。 */
val LocalWidgetsShapes = staticCompositionLocalOf { AppShapes }

/**
 * 是否给「表面色 + 海拔」染色（对应上游 `ColorScheme.kt` 的 `LocalTonalElevationEnabled`，
 * 语义与默认值一致：`staticCompositionLocalOf { true }`；置 false 时其下所有 Surface 都不再做海拔染色）。
 * `core:widgets` 的 `CssVariables.applyTonalElevation` 读它。
 */
val LocalWidgetsTonalElevationEnabled = staticCompositionLocalOf { true }

/**
 * 「容器色 → 该用在上面的内容色」——与上游的 `ColorScheme.contentColorFor` 同语义，但**是纯函数**：
 * 拿我们自己的槽位比对（[accent] 配 [accentForeground]、[card] 配 [cardForeground] …），
 * 不去问主题，所以在 `remember {}` 这类**非组合上下文**里也能调用。
 * 匹配不到时返回 [Color.Unspecified]，调用方照旧回落 `LocalContentColor`。
 *
 * 槽位对应关系与原先灌进上游角色的映射逐条一致：`primaryContainer -> accent`、
 * `surfaceVariant -> subtleSurface`、`inverseSurface -> foreground` …
 */
fun CssVariables.contentColorFor(backgroundColor: Color): Color =
    when (backgroundColor) {
        accent -> accentForeground
        card -> cardForeground
        primary -> primaryForeground
        background -> foreground
        subtleSurface -> mutedForeground
        foreground -> background
        else -> Color.Unspecified
    }
