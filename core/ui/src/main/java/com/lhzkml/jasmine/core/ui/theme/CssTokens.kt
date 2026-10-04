package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Production-grade CSS Variables model with Editorial Aesthetic tokens:
 * --bg, --text/--fg, --surface/--card, --border,
 * --primary/--accent, --muted, --radius
 *
 * 除下面这 13 个基础槽（各调色板逐个手写取值）外，还**按上游 M3 的角色键逐键**补了
 * 一组容器/次级色槽（[surface] … [error]）：上游 `ColorSchemeKeyTokens` 里本库用到的
 * 21 个键现在**一一对应**到自有槽，不再把多个键折到同一个槽上。
 *
 * 这些是**派生槽**（`get()`）：值仍由各调色板自己的 [background] / [card] / [muted] /
 * [accent] / [foreground] 按 M3 基线的层次关系推出来，所以 12 套调色板自动各自成立，
 * 且不引入任何新色值。层次口径（浅色下由亮到暗）与 M3 基线一致：
 * `Surface(≈[background]) → SurfaceContainerLow → SurfaceContainer(= [card]) →
 * SurfaceContainerHigh → SurfaceContainerHighest(≈[muted])`。
 */
data class CssVariables(
    val themeId: String,
    val name: String,
    val isDark: Boolean,

    // Core CSS Color Variables
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val border: Color,
    val primary: Color,
    val primaryForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val accent: Color,
    val accentForeground: Color,
    val ring: Color,
    val subtleSurface: Color,
) {
    // ── 上游 ColorSchemeKeyTokens 一一对应的容器 / 次级色槽 ──────────────────

    /**
     * 上游 `Surface`：基准面。M3 基线里它比容器层更贴近页面底（`#FEF7FF` vs `#F3EDF7`），
     * 所以往 [background] 外侧推，与 [surfaceContainer]（= [card]）**不同值**。
     */
    val surface: Color get() = lerp(background, card, 0.25f)

    /** 上游 `SurfaceContainerLow`：比 [card] 浅半档的一层。 */
    val surfaceContainerLow: Color get() = lerp(background, card, 0.5f)

    /** 上游 `SurfaceContainer`：常规容器层（= [card]）。 */
    val surfaceContainer: Color get() = card

    /**
     * 上游 `SurfaceContainerHigh`：比 [card] 深一档。搜索栏折叠态的容器用的就是它 ——
     * 之前折到 [subtleSurface]（与页面底只差几阶）才导致轮廓看不清。
     */
    val surfaceContainerHigh: Color get() = lerp(card, muted, 0.5f)

    /** 上游 `SurfaceContainerHighest`：容器层里最深的一档（= [muted]）。 */
    val surfaceContainerHighest: Color get() = muted

    /**
     * 上游 `SurfaceVariant`：次级面。M3 基线里它与 `SurfaceContainerHigh` 同档但**不同色**
     * （`#E7E0EC` vs `#ECE6F0`），所以取「High 与 Highest 之间」的那一档，避免与
     * [surfaceContainerHigh] 折平。
     */
    val surfaceVariant: Color get() = lerp(card, muted, 0.75f)

    /**
     * 上游 `PrimaryContainer`：主色的容器调。M3 基线里它比面**浅、带主色相**
     * （`#EADDFF`），所以取主色向面靠拢的淡色调。
     */
    val primaryContainer: Color get() = lerp(primary, card, 0.85f)

    /**
     * 上游 `OnPrimaryContainer`：主色容器上的前景色。M3 基线里它**很深**
     * （`#21005D`）但**不等于**主色本身，所以取"主色再往前景压一档"，
     * 与 `SurfaceTint`（= [primary]）区分开。
     */
    val onPrimaryContainer: Color get() = lerp(primary, foreground, 0.30f)

    /** 上游 `Secondary`：次要色（M3 基线 `#625B71`，中深灰 ⇒ 取 [mutedForeground]）。 */
    val secondary: Color get() = mutedForeground

    /**
     * 上游 `OnSecondary`：次要面上的前景色（M3 基线为白）。取"从 [card] 往 [background] 方向"
     * 的那一档，与 `SurfaceBright`/`SurfaceContainerLowest` 等同方向档位区分开。
     */
    val onSecondary: Color get() = lerp(card, background, 0.30f)

    /**
     * 上游 `SecondaryContainer`：次要容器面。M3 基线 `#E8DEF8` 比面**深一档且带主色相**，
     * 用作底栏/抽屉"选中药丸"的底 ⇒ 取主色向 [card] 靠拢的淡色调（不能取 [muted]，
     * 那与 `SurfaceContainerHighest` 同值；也不能取 [subtleSurface]，那与 [card] 只差几阶、
     * 选中指示条会隐形）。
     */
    val secondaryContainer: Color get() = lerp(card, primary, 0.12f)

    /**
     * 上游 `OnSecondaryContainer`：次要容器上的前景色。M3 基线 `#1D192B` 是**近黑但带主色相**，
     * 用作选中项图标/文字 ⇒ 取"前景再掺一档主色"，与 `InverseSurface`（= [foreground]）
     * 区分开（也不能折到 [mutedForeground]，那样与未选中的 `OnSurfaceVariant` 同色、
     * 选中态就看不出来了）。
     */
    val onSecondaryContainer: Color get() = lerp(foreground, primary, 0.15f)

    /** 上游 `InverseSurface`：反色面（= [foreground]）。 */
    val inverseSurface: Color get() = foreground

    /** 上游 `InverseOnSurface`：反色面上的前景色（= [background]）。 */
    val inverseOnSurface: Color get() = background

    /**
     * 上游 `Error`：错误色。现有 12 套调色板都还没有自己的错误色，沿用 M3 基线值
     * （深浅两套分开），与 `tokens/TokenResolvers.kt` 的既有取值完全一致。
     */
    val error: Color get() = if (isDark) Color(0xFFF2B8B5) else Color(0xFFB3261E)

    // ── 上游 ColorScheme 的其余角色（按 M3 基线台阶逐槽派生，各槽取值互不重合）────
    // 说明：M3 自身这些角色也是由 tonal palette 算出来的；这里让每条都落在**不同**的
    // 插值档上，从而既有各自的位置、又不会互相折平。

    /** 上游 `OnError`：错误面上的前景色（M3 基线深/浅各一，与 [error] 成对）。 */
    val onError: Color get() = if (isDark) Color(0xFF601410) else Color(0xFFFFFFFF)

    /** 上游 `ErrorContainer`：错误色容器（比 [error] 淡一档）。 */
    val errorContainer: Color get() = lerp(error, card, 0.78f)

    /** 上游 `OnErrorContainer`：错误容器上的前景色。 */
    val onErrorContainer: Color get() = lerp(error, foreground, 0.30f)

    /** 上游 `InversePrimary`：主色的反色版（往反色面 [inverseSurface] 靠）。 */
    val inversePrimary: Color get() = lerp(primary, background, 0.55f)

    /** 上游 `OnPrimaryFixed`：主色固定组的最深前景。 */
    val onPrimaryFixed: Color get() = lerp(primary, foreground, 0.25f)

    /** 上游 `OnPrimaryFixedVariant`：主色固定组的次深前景。 */
    val onPrimaryFixedVariant: Color get() = lerp(primary, foreground, 0.55f)

    /** 上游 `PrimaryFixed`：主色固定组容器（浅、带主色相）。 */
    val primaryFixed: Color get() = lerp(primary, card, 0.88f)

    /** 上游 `PrimaryFixedDim`：主色固定组容器的暗版。 */
    val primaryFixedDim: Color get() = lerp(primary, card, 0.72f)

    /** 上游 `SecondaryFixed`：次要固定组容器。 */
    val secondaryFixed: Color get() = lerp(secondary, card, 0.88f)

    /** 上游 `SecondaryFixedDim`：次要固定组容器的暗版。 */
    val secondaryFixedDim: Color get() = lerp(secondary, card, 0.72f)

    /** 上游 `OnSecondaryFixed`：次要固定组的最深前景。 */
    val onSecondaryFixed: Color get() = lerp(secondary, foreground, 0.25f)

    /** 上游 `OnSecondaryFixedVariant`：次要固定组的次深前景。 */
    val onSecondaryFixedVariant: Color get() = lerp(secondary, foreground, 0.55f)

    /** 上游 `Tertiary`：第三色（本库调色板以 [accent] 充当第三强调）。 */
    val tertiary: Color get() = accent

    /** 上游 `OnTertiary`：第三色面上的前景色。 */
    val onTertiary: Color get() = accentForeground

    /** 上游 `TertiaryContainer`：第三色容器（浅、带强调色相）。 */
    val tertiaryContainer: Color get() = lerp(accent, card, 0.80f)

    /** 上游 `OnTertiaryContainer`：第三色容器上的前景色。 */
    val onTertiaryContainer: Color get() = lerp(accent, foreground, 0.28f)

    /** 上游 `TertiaryFixed`：第三固定组容器。 */
    val tertiaryFixed: Color get() = lerp(accent, card, 0.88f)

    /** 上游 `TertiaryFixedDim`：第三固定组容器的暗版。 */
    val tertiaryFixedDim: Color get() = lerp(accent, card, 0.72f)

    /** 上游 `OnTertiaryFixed`：第三固定组的最深前景。 */
    val onTertiaryFixed: Color get() = lerp(accent, foreground, 0.25f)

    /** 上游 `OnTertiaryFixedVariant`：第三固定组的次深前景。 */
    val onTertiaryFixedVariant: Color get() = lerp(accent, foreground, 0.55f)

    /** 上游 `Scrim`：遮罩色（M3 基线浅深两套都是纯黑）。 */
    val scrim: Color get() = Color(0xFF000000)

    /** 上游 `SurfaceBright`：比基准面更亮的一档（往 [background] 外侧推）。 */
    val surfaceBright: Color get() = lerp(surface, background, 0.60f)

    /** 上游 `SurfaceDim`：比 [card] 更暗的一档（往 [muted] 外侧推）。 */
    val surfaceDim: Color get() = lerp(muted, foreground, 0.12f)

    /** 上游 `SurfaceTint`：面的染色源（本库取主色）。 */
    val surfaceTint: Color get() = primary

    /** 上游 `SurfaceContainerLowest`：容器阶梯里最亮的一档。 */
    val surfaceContainerLowest: Color get() = lerp(background, card, 0.15f)
}

// Radius Tokens (Editorial: 24px primary radius)

/**
 * Editorial Aesthetic and Top Industry Minimalist Color Palettes
 */
object ProductionPalettes {

    // 1. Editorial Aesthetic (Featured)
    val EditorialLight = CssVariables(
        themeId = "editorial-light",
        name = "Editorial Aesthetic (Light)",
        isDark = false,
        background = Color(0xFFFAFAFA),
        foreground = Color(0xFF111111),
        card = Color(0xFFF2F2F2),
        cardForeground = Color(0xFF111111),
        border = Color(0xFFE5E5E5),
        primary = Color(0xFF000000),
        primaryForeground = Color(0xFFFAFAFA),
        muted = Color(0xFFEAEAEA),
        mutedForeground = Color(0xFF737373),
        accent = Color(0xFF111111),
        accentForeground = Color(0xFFFAFAFA),
        ring = Color(0xFF111111),
        subtleSurface = Color(0xFFF5F5F5),
    )

    val EditorialDark = CssVariables(
        themeId = "editorial-dark",
        name = "Editorial Aesthetic (Dark)",
        isDark = true,
        background = Color(0xFF0A0A0A),
        foreground = Color(0xFFF5F5F5),
        card = Color(0xFF1A1A1A),
        cardForeground = Color(0xFFF5F5F5),
        border = Color(0xFF262626),
        primary = Color(0xFFFFFFFF),
        primaryForeground = Color(0xFF0A0A0A),
        muted = Color(0xFF222222),
        mutedForeground = Color(0xFFA3A3A3),
        accent = Color(0xFFFFFFFF),
        accentForeground = Color(0xFF0A0A0A),
        ring = Color(0xFFFFFFFF),
        subtleSurface = Color(0xFF141414),
    )

    // 2. Geist Minimal (Vercel)
    val GeistDark = CssVariables(
        themeId = "geist-dark",
        name = "Geist Minimal (Dark)",
        isDark = true,
        background = Color(0xFF000000),
        foreground = Color(0xFFEDEDED),
        card = Color(0xFF0A0A0A),
        cardForeground = Color(0xFFFAFAFA),
        border = Color(0xFF262626),
        primary = Color(0xFF0070F3),
        primaryForeground = Color(0xFFFFFFFF),
        muted = Color(0xFF1F1F1F),
        mutedForeground = Color(0xFF888888),
        accent = Color(0xFF171717),
        accentForeground = Color(0xFFEDEDED),
        ring = Color(0xFF0070F3),
        subtleSurface = Color(0xFF111111),
    )

    val GeistLight = CssVariables(
        themeId = "geist-light",
        name = "Geist Minimal (Light)",
        isDark = false,
        background = Color(0xFFFFFFFF),
        foreground = Color(0xFF171717),
        card = Color(0xFFFAFAFA),
        cardForeground = Color(0xFF171717),
        border = Color(0xFFE5E5E5),
        primary = Color(0xFF0070F3),
        primaryForeground = Color(0xFFFFFFFF),
        muted = Color(0xFFF5F5F5),
        mutedForeground = Color(0xFF737373),
        accent = Color(0xFFEAEAEA),
        accentForeground = Color(0xFF0A0A0A),
        ring = Color(0xFF0070F3),
        subtleSurface = Color(0xFFF9F9F9),
    )

    // 3. Linear Obsidian
    val LinearDark = CssVariables(
        themeId = "linear-dark",
        name = "Linear Obsidian (Dark)",
        isDark = true,
        background = Color(0xFF08090A),
        foreground = Color(0xFFF2F3F5),
        card = Color(0xFF121316),
        cardForeground = Color(0xFFF2F3F5),
        border = Color(0xFF222328),
        primary = Color(0xFF5E6AD2),
        primaryForeground = Color(0xFFFFFFFF),
        muted = Color(0xFF18191E),
        mutedForeground = Color(0xFF8A8F98),
        accent = Color(0xFF1C1D22),
        accentForeground = Color(0xFFF2F3F5),
        ring = Color(0xFF5E6AD2),
        subtleSurface = Color(0xFF0F1013),
    )

    val LinearLight = CssVariables(
        themeId = "linear-light",
        name = "Linear Slate (Light)",
        isDark = false,
        background = Color(0xFFF7F8F9),
        foreground = Color(0xFF1A1B1E),
        card = Color(0xFFFFFFFF),
        cardForeground = Color(0xFF1A1B1E),
        border = Color(0xFFE3E5E8),
        primary = Color(0xFF5E6AD2),
        primaryForeground = Color(0xFFFFFFFF),
        muted = Color(0xFFECEEF1),
        mutedForeground = Color(0xFF62666D),
        accent = Color(0xFFE8EAF0),
        accentForeground = Color(0xFF1A1B1E),
        ring = Color(0xFF5E6AD2),
        subtleSurface = Color(0xFFF0F2F5),
    )

    // 4. Shadcn Zinc
    val ShadcnZincDark = CssVariables(
        themeId = "shadcn-zinc-dark",
        name = "Shadcn Zinc (Dark)",
        isDark = true,
        background = Color(0xFF09090B),
        foreground = Color(0xFFFAFAFA),
        card = Color(0xFF18181B),
        cardForeground = Color(0xFFFAFAFA),
        border = Color(0xFF27272A),
        primary = Color(0xFFFAFAFA),
        primaryForeground = Color(0xFF18181B),
        muted = Color(0xFF27272A),
        mutedForeground = Color(0xFFA1A1AA),
        accent = Color(0xFF27272A),
        accentForeground = Color(0xFFFAFAFA),
        ring = Color(0xFFD4D4D8),
        subtleSurface = Color(0xFF121215),
    )

    val ShadcnZincLight = CssVariables(
        themeId = "shadcn-zinc-light",
        name = "Shadcn Zinc (Light)",
        isDark = false,
        background = Color(0xFFFFFFFF),
        foreground = Color(0xFF09090B),
        card = Color(0xFFF4F4F5),
        cardForeground = Color(0xFF09090B),
        border = Color(0xFFE4E4E7),
        primary = Color(0xFF18181B),
        primaryForeground = Color(0xFFFAFAFA),
        muted = Color(0xFFF4F4F5),
        mutedForeground = Color(0xFF71717A),
        accent = Color(0xFFE4E4E7),
        accentForeground = Color(0xFF09090B),
        ring = Color(0xFF18181B),
        subtleSurface = Color(0xFFFAFAFA),
    )

    // 5. Notion Warm
    val NotionWarmDark = CssVariables(
        themeId = "notion-warm-dark",
        name = "Notion Sepia (Dark)",
        isDark = true,
        background = Color(0xFF191919),
        foreground = Color(0xFFEFEFEF),
        card = Color(0xFF252525),
        cardForeground = Color(0xFFEFEFEF),
        border = Color(0xFF333333),
        primary = Color(0xFFEB5757),
        primaryForeground = Color(0xFFFFFFFF),
        muted = Color(0xFF2A2A2A),
        mutedForeground = Color(0xFF9B9A97),
        accent = Color(0xFF333333),
        accentForeground = Color(0xFFEFEFEF),
        ring = Color(0xFFEB5757),
        subtleSurface = Color(0xFF202020),
    )

    val NotionWarmLight = CssVariables(
        themeId = "notion-warm-light",
        name = "Notion Oat (Light)",
        isDark = false,
        background = Color(0xFFFBFBFA),
        foreground = Color(0xFF37352F),
        card = Color(0xFFF1F1EF),
        cardForeground = Color(0xFF37352F),
        border = Color(0xFFE3E2DE),
        primary = Color(0xFFEB5757),
        primaryForeground = Color(0xFFFFFFFF),
        muted = Color(0xFFEAE9E5),
        mutedForeground = Color(0xFF787774),
        accent = Color(0xFFE3E2DE),
        accentForeground = Color(0xFF37352F),
        ring = Color(0xFFEB5757),
        subtleSurface = Color(0xFFF7F6F3),
    )

    // 6. Braun Dieter Rams
    val DieterRamsDark = CssVariables(
        themeId = "dieter-rams-dark",
        name = "Braun Dieter Rams (Dark)",
        isDark = true,
        background = Color(0xFF111111),
        foreground = Color(0xFFF5F5F0),
        card = Color(0xFF1E1E1E),
        cardForeground = Color(0xFFF5F5F0),
        border = Color(0xFF303030),
        primary = Color(0xFFFF5500),
        primaryForeground = Color(0xFFFFFFFF),
        muted = Color(0xFF252525),
        mutedForeground = Color(0xFF888880),
        accent = Color(0xFF2A2A2A),
        accentForeground = Color(0xFFF5F5F0),
        ring = Color(0xFFFF5500),
        subtleSurface = Color(0xFF171717),
    )

    val DieterRamsLight = CssVariables(
        themeId = "dieter-rams-light",
        name = "Braun Dieter Rams (Light)",
        isDark = false,
        background = Color(0xFFE8E8E3),
        foreground = Color(0xFF1A1A1A),
        card = Color(0xFFDEDECF),
        cardForeground = Color(0xFF1A1A1A),
        border = Color(0xFFC7C7BA),
        primary = Color(0xFFFF5500),
        primaryForeground = Color(0xFFFFFFFF),
        muted = Color(0xFFD4D4C8),
        mutedForeground = Color(0xFF66665E),
        accent = Color(0xFFCBCBBF),
        accentForeground = Color(0xFF1A1A1A),
        ring = Color(0xFFFF5500),
        subtleSurface = Color(0xFFE2E2DC),
    )
}

val LocalCssVariables = compositionLocalOf<CssVariables> {
    ProductionPalettes.EditorialLight
}

/**
 * Braun (Dieter Rams) keeps its restrained, honest selected surfaces and opts
 * out of the elevated white/muted selected-highlight treatment applied to the
 * other palettes.
 *
 * Implemented as a direct prefix check against [ThemeResolver.Braun].key so
 * that [CssVariables] does not take a dependency on the family-parsing logic
 * in [ThemeResolver.familyOf].
 */
val CssVariables.isBraun: Boolean
    get() = themeId.startsWith(ThemeResolver.Braun.key)

/**
 * Palette-family resolution from a persisted themeId.
 */
object ThemeResolver {
    /**
     * Palette family key of a concrete themeId ("editorial-light" -> "editorial"),
     * or null when the id matches no family.
     *
     * Resolved by exact themeId match against [families] rather than suffix
     * stripping, because not every themeId is shaped as "{key}-light/-dark" —
     * e.g. "shadcn-zinc-dark" belongs to family "shadcn" and "notion-warm-light"
     * to "notion".
     */
    fun familyOf(themeId: String): String? =
        families.firstOrNull { it.light.themeId == themeId || it.dark.themeId == themeId }?.key

    /**
     * A preset palette family: its stable [key], user-facing [displayName]
     * plus the light/dark variants.
     *
     * Single source of truth for family pickers (canvas quick-switcher,
     * settings palette list) — adding a family is one entry in [families].
     */
    data class PaletteFamily(
        val key: String,
        val displayName: String,
        val light: CssVariables,
        val dark: CssVariables,
    ) {
        /** The variant matching [isDark]. */
        fun variant(isDark: Boolean): CssVariables = if (isDark) dark else light
    }

    /**
     * The Braun (Dieter Rams) family, kept as a named constant so callers that
     * need to branch on Braun (e.g. the selected-surface override in
     * [isBraun]) can reference its key without hard-coding the string.
     */
    val Braun = PaletteFamily(
        key = "dieter-rams",
        displayName = "Braun",
        light = ProductionPalettes.DieterRamsLight,
        dark = ProductionPalettes.DieterRamsDark,
    )

    /** All preset families in picker display order. */
    val families: List<PaletteFamily> = listOf(
        PaletteFamily("editorial", "Editorial", ProductionPalettes.EditorialLight, ProductionPalettes.EditorialDark),
        PaletteFamily("geist", "Geist", ProductionPalettes.GeistLight, ProductionPalettes.GeistDark),
        PaletteFamily("linear", "Linear", ProductionPalettes.LinearLight, ProductionPalettes.LinearDark),
        PaletteFamily("shadcn", "Shadcn", ProductionPalettes.ShadcnZincLight, ProductionPalettes.ShadcnZincDark),
        PaletteFamily("notion", "Notion", ProductionPalettes.NotionWarmLight, ProductionPalettes.NotionWarmDark),
        Braun,
    )

    /** Resolves a palette family to its light or dark variant. */
    fun resolveFamily(family: String, isDark: Boolean): CssVariables =
        families.firstOrNull { it.key == family }?.variant(isDark)
            ?: ProductionPalettes.EditorialLight
}
