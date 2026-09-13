package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Production-grade CSS Variables model with Editorial Aesthetic tokens:
 * --bg, --text/--fg, --surface/--card, --border,
 * --primary/--accent, --muted, --radius
 */
data class CssVariables(
    val themeId: String,
    val name: String,
    val description: String,
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
    
    // Radius Tokens (Editorial: 24px primary radius)
    val radiusSm: Dp = 10.dp,
    val radiusMd: Dp = 16.dp,
    val radiusLg: Dp = 24.dp
)

/**
 * Editorial Aesthetic and Top Industry Minimalist Color Palettes
 */
object ProductionPalettes {

    // 1. Editorial Aesthetic (Featured)
    val EditorialLight = CssVariables(
        themeId = "editorial-light",
        name = "Editorial Aesthetic (Light)",
        description = "Refined warm paper canvas (#FAFAFA) with high-contrast typography, Georgia serif accents, and pure black focal points.",
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
        radiusSm = 10.dp,
        radiusMd = 16.dp,
        radiusLg = 24.dp
    )

    val EditorialDark = CssVariables(
        themeId = "editorial-dark",
        name = "Editorial Aesthetic (Dark)",
        description = "Deep carbon black (#0A0A0A) editorial surface with crisp ivory text (#F5F5F5) and pure white accents.",
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
        radiusSm = 10.dp,
        radiusMd = 16.dp,
        radiusLg = 24.dp
    )

    // 2. Geist Minimal (Vercel)
    val GeistDark = CssVariables(
        themeId = "geist-dark",
        name = "Geist Minimal (Dark)",
        description = "Vercel's hyper-clean monochrome canvas with precision electric blue accent.",
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
        radiusSm = 10.dp,
        radiusMd = 16.dp,
        radiusLg = 24.dp
    )

    val GeistLight = CssVariables(
        themeId = "geist-light",
        name = "Geist Minimal (Light)",
        description = "Pure white studio canvas with razor-sharp typography and micro-contrast.",
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
        radiusSm = 10.dp,
        radiusMd = 16.dp,
        radiusLg = 24.dp
    )

    // 3. Linear Obsidian
    val LinearDark = CssVariables(
        themeId = "linear-dark",
        name = "Linear Obsidian (Dark)",
        description = "Linear's iconic deep obsidian dark surface with desaturated indigo focal points.",
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
        radiusSm = 10.dp,
        radiusMd = 16.dp,
        radiusLg = 24.dp
    )

    val LinearLight = CssVariables(
        themeId = "linear-light",
        name = "Linear Slate (Light)",
        description = "Muted slate-neutral light palette with soft indigo highlights.",
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
        radiusSm = 10.dp,
        radiusMd = 16.dp,
        radiusLg = 24.dp
    )

    // 4. Shadcn Zinc
    val ShadcnZincDark = CssVariables(
        themeId = "shadcn-zinc-dark",
        name = "Shadcn Zinc (Dark)",
        description = "The industry benchmark neutral zinc grayscale with inverted high-contrast primary.",
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
        radiusSm = 10.dp,
        radiusMd = 16.dp,
        radiusLg = 24.dp
    )

    val ShadcnZincLight = CssVariables(
        themeId = "shadcn-zinc-light",
        name = "Shadcn Zinc (Light)",
        description = "Pure zinc paper aesthetic with deep carbon elements.",
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
        radiusSm = 10.dp,
        radiusMd = 16.dp,
        radiusLg = 24.dp
    )

    // 5. Notion Warm
    val NotionWarmDark = CssVariables(
        themeId = "notion-warm-dark",
        name = "Notion Sepia (Dark)",
        description = "Warm organic dark tone inspired by Japanese minimalism and matte paper.",
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
        radiusSm = 10.dp,
        radiusMd = 16.dp,
        radiusLg = 24.dp
    )

    val NotionWarmLight = CssVariables(
        themeId = "notion-warm-light",
        name = "Notion Oat (Light)",
        description = "Warm off-white rice paper background with soft charcoal typography.",
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
        radiusSm = 10.dp,
        radiusMd = 16.dp,
        radiusLg = 24.dp
    )

    // 6. Braun Dieter Rams
    val DieterRamsDark = CssVariables(
        themeId = "dieter-rams-dark",
        name = "Braun Dieter Rams (Dark)",
        description = "Functional industrial matte black with legendary Braun international signal orange.",
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
        radiusSm = 10.dp,
        radiusMd = 16.dp,
        radiusLg = 24.dp
    )

    val DieterRamsLight = CssVariables(
        themeId = "dieter-rams-light",
        name = "Braun Dieter Rams (Light)",
        description = "Matte anodized aluminum and warm functional gray with safety orange focus point.",
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
        radiusSm = 10.dp,
        radiusMd = 16.dp,
        radiusLg = 24.dp
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
     * Palette family key of a concrete themeId ("editorial-light" -> "editorial").
     *
     * Resolved by exact themeId match against [families] rather than suffix
     * stripping, because not every themeId is shaped as "{key}-light/-dark" —
     * e.g. "shadcn-zinc-dark" belongs to family "shadcn" and "notion-warm-light"
     * to "notion"; suffix stripping would yield "shadcn-zinc"/"notion-warm" and
     * silently fall back to the Editorial palette downstream. The suffix strip
     * is kept only as a fallback for unknown persisted ids.
     */
    fun familyOf(themeId: String): String =
        families.firstOrNull { it.light.themeId == themeId || it.dark.themeId == themeId }?.key
            ?: themeId.removeSuffix("-light").removeSuffix("-dark")

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
