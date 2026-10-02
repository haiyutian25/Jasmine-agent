package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Composable
fun JasmineTheme(
    cssVars: CssVariables = ProductionPalettes.GeistDark,
    content: @Composable () -> Unit
) {
    val m3ColorScheme = if (cssVars.isDark) {
        darkColorScheme(
            primary = cssVars.primary,
            onPrimary = cssVars.primaryForeground,
            primaryContainer = cssVars.accent,
            onPrimaryContainer = cssVars.accentForeground,
            secondary = cssVars.mutedForeground,
            onSecondary = cssVars.foreground,
            // 侧边栏（core:widgets 的抽屉）选中态的指示条取的就是 secondaryContainer，
            // 不映射它会回落到 M3 基线的紫调。
            secondaryContainer = cssVars.subtleSurface,
            onSecondaryContainer = cssVars.mutedForeground,
            background = cssVars.background,
            onBackground = cssVars.foreground,
            surface = cssVars.card,
            onSurface = cssVars.cardForeground,
            surfaceVariant = cssVars.subtleSurface,
            onSurfaceVariant = cssVars.mutedForeground,
            outline = cssVars.border,
            outlineVariant = cssVars.muted,
            // 下面这些角色是 core:widgets 的令牌会引用到的（Filled 输入框的底色就是
            // surfaceContainerHighest）：不映射就会回落到 M3 基线的紫调色。
            surfaceContainerLowest = cssVars.background,
            surfaceContainerLow = cssVars.card,
            surfaceContainer = cssVars.card,
            surfaceContainerHigh = cssVars.subtleSurface,
            surfaceContainerHighest = cssVars.subtleSurface,
            surfaceDim = cssVars.muted,
            surfaceBright = cssVars.background,
            surfaceTint = cssVars.primary,
            inverseSurface = cssVars.foreground,
            inverseOnSurface = cssVars.background,
            inversePrimary = cssVars.accent,
            tertiary = cssVars.accent,
            onTertiary = cssVars.accentForeground,
            tertiaryContainer = cssVars.subtleSurface,
            onTertiaryContainer = cssVars.mutedForeground,
            tertiaryFixed = cssVars.accent,
            tertiaryFixedDim = cssVars.muted,
            onTertiaryFixed = cssVars.accentForeground,
            onTertiaryFixedVariant = cssVars.accentForeground,
            primaryFixed = cssVars.primary,
            primaryFixedDim = cssVars.muted,
            onPrimaryFixed = cssVars.primaryForeground,
            onPrimaryFixedVariant = cssVars.primaryForeground,
            secondaryFixed = cssVars.muted,
            secondaryFixedDim = cssVars.subtleSurface,
            onSecondaryFixed = cssVars.mutedForeground,
            onSecondaryFixedVariant = cssVars.mutedForeground
        )
    } else {
        lightColorScheme(
            primary = cssVars.primary,
            onPrimary = cssVars.primaryForeground,
            primaryContainer = cssVars.accent,
            onPrimaryContainer = cssVars.accentForeground,
            secondary = cssVars.mutedForeground,
            onSecondary = cssVars.foreground,
            // 同 dark：抽屉选中态指示条用的是 secondaryContainer。
            secondaryContainer = cssVars.subtleSurface,
            onSecondaryContainer = cssVars.mutedForeground,
            background = cssVars.background,
            onBackground = cssVars.foreground,
            surface = cssVars.card,
            onSurface = cssVars.cardForeground,
            surfaceVariant = cssVars.subtleSurface,
            onSurfaceVariant = cssVars.mutedForeground,
            outline = cssVars.border,
            outlineVariant = cssVars.muted,
            // 同 dark：把 core:widgets 令牌会引用的角色都映射到本调色板。
            surfaceContainerLowest = cssVars.background,
            surfaceContainerLow = cssVars.card,
            surfaceContainer = cssVars.card,
            surfaceContainerHigh = cssVars.subtleSurface,
            surfaceContainerHighest = cssVars.subtleSurface,
            surfaceDim = cssVars.muted,
            surfaceBright = cssVars.background,
            surfaceTint = cssVars.primary,
            inverseSurface = cssVars.foreground,
            inverseOnSurface = cssVars.background,
            inversePrimary = cssVars.accent,
            tertiary = cssVars.accent,
            onTertiary = cssVars.accentForeground,
            tertiaryContainer = cssVars.subtleSurface,
            onTertiaryContainer = cssVars.mutedForeground,
            tertiaryFixed = cssVars.accent,
            tertiaryFixedDim = cssVars.muted,
            onTertiaryFixed = cssVars.accentForeground,
            onTertiaryFixedVariant = cssVars.accentForeground,
            primaryFixed = cssVars.primary,
            primaryFixedDim = cssVars.muted,
            onPrimaryFixed = cssVars.primaryForeground,
            onPrimaryFixedVariant = cssVars.primaryForeground,
            secondaryFixed = cssVars.muted,
            secondaryFixedDim = cssVars.subtleSurface,
            onSecondaryFixed = cssVars.mutedForeground,
            onSecondaryFixedVariant = cssVars.mutedForeground
        )
    }

    val contentFont = LocalContentFontFamily.current

    CompositionLocalProvider(LocalCssVariables provides cssVars) {
        MaterialTheme(
            colorScheme = m3ColorScheme,
            shapes = AppShapes,
            typography = AppTypography
        ) {
            // The default text style follows the user's chosen content font, so
            // any Text that does not pin its own fontFamily inherits it. Text
            // that explicitly sets fontFamily (code, font previews, decorative
            // letters) keeps its own and is unaffected.
            //
            // LocalWidgets* 是给 core:widgets 里那些 ModifierNode 用的：它们不在组合
            // 环境里，读不到 MaterialTheme，只能通过 CompositionLocal 拿到同一份值。
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = contentFont),
                LocalWidgetsColorScheme provides m3ColorScheme,
                LocalWidgetsShapes provides AppShapes,
            ) {
                content()
            }
        }
    }
}

/**
 * 供 `core:widgets` 里不在组合环境中的结点（ModifierNode）读取当前主题的配色与形状：
 * 由 [JasmineTheme] 注入；没有宿主提供时退回 M3 基线值。
 */
val LocalWidgetsColorScheme = staticCompositionLocalOf<ColorScheme> { lightColorScheme() }

val LocalWidgetsShapes = staticCompositionLocalOf { Shapes() }

/**
 * 「容器色 → 该用在上面的内容色」——与 M3 `ColorScheme.contentColorFor` 同语义，但**是纯函数**：
 * 拿我们自己的槽位比对（[accent] 配 [accentForeground]、[card] 配 [cardForeground] …），
 * 不去问 M3 主题，所以在 `remember {}` 这类**非组合上下文**里也能调用。
 * 匹配不到时返回 [Color.Unspecified]，调用方照旧回落 `LocalContentColor`。
 *
 * 槽位对应关系与 [JasmineTheme] 里的映射逐条一致：`primaryContainer -> accent`、
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

