package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Production-grade CSS Variables model with Editorial Aesthetic tokens:
 * --bg, --text/--fg, --surface/--card, --border,
 * --primary/--accent, --muted, --radius
 *
 * 除下面这 13 个基础槽（各调色板逐个手写取值）外，还**按上游的角色键逐键**补了
 * 一组容器/次级色槽（[surface] … [error]）：上游 `ColorSchemeKeyTokens` 里本库用到的
 * 21 个键现在**一一对应**到自有槽，不再把多个键折到同一个槽上。
 *
 * 这些是**派生槽**（`get()`）：值仍由各调色板自己的 [background] / [card] / [muted] /
 * [accent] / [foreground] 按 上游基线的层次关系推出来，所以 12 套调色板自动各自成立，
 * 且不引入任何新色值。层次口径（浅色下由亮到暗）与 上游基线一致：
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

    // ── 错误色组与遮罩色：与上面 13 个基础槽一样**逐套手写** ────────────────────
    // 这三个此前写死在类体内（上游基线红），等于**绕过了调色板**：12 套主题共用同一个红，
    // 谁想调都得改这个共用文件（Badge 直接消费 error，所以它不是纯内部色）。
    // 收成构造参数后各套可单独覆盖；默认值保持原取值不变（深浅各一），现有 12 套观感一位不差。
    val error: Color = if (isDark) Color(0xFFF2B8B5) else Color(0xFFB3261E),
    val onError: Color = if (isDark) Color(0xFF601410) else Color(0xFFFFFFFF),
    val scrim: Color = Color(0xFF000000),

    // ── ZCode（Z.ai）主题的完整槽表 ─────────────────────────────────────────
    // 上游那套有 142 个颜色槽（面 / 状态 / 终端 / 图表 / Git 状态 / 图节点 …），远超本表
    // 上面这些通用槽；它们整体收在 [ZCodeSlots] 里，由这一项挂在调色板上，
    // 访问形如 `cssVars.zcode.terminalRed` / `cssVars.zcode.gitModified`。
    // 默认给 [ZCodeSlots.Light]：本库目前只有 ZCode 这一套 family 用这些槽，
    // 其余 13 套取到的是纯占位值，也就为此不必改一个字。
    val zcode: ZCodeSlots = ZCodeSlots.ZaiLight,

    /**
     * ZCode 的**排版槽**（`--ui-font-size` 基准与 7 档字号、字距、iOS 输入下限、等宽字体栈）。
     * 只有一套 —— 排版不随明暗/调色板变化，所以默认给 [ZCodeText] 即可，其余调色板取不到它也无碍。
     */
    val zcodeText: ZCodeTextSlots = ZCodeText,

    /** ZCode 的**工作流图动效参数**（4 档时长、缓动曲线、心跳周期、小人脸配色/偏移/动画次数）。同样只有一套。 */
    val zcodeWorkflow: ZCodeWorkflowSlots = ZCodeWorkflow,

    /** ZCode 依赖的 **tailwind v4 色板**（55 支原始色）。只有一套。 */
    val zcodeTailwind: ZCodeTailwindPalette = ZCodeTailwind,

    /** ZCode 的 **@提及图标槽**（三个兜底值）。只有一套。 */
    val zcodeMention: ZCodeMentionSlots = ZCodeMention,
) {
    // ── 上游 ColorSchemeKeyTokens 一一对应的容器 / 次级色槽 ──────────────────

    /**
     * 上游 `Surface`：基准面。上游基线里它比容器层更贴近页面底（`#FEF7FF` vs `#F3EDF7`），
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
     * 上游 `SurfaceVariant`：次级面。上游基线里它与 `SurfaceContainerHigh` 同档但**不同色**
     * （`#E7E0EC` vs `#ECE6F0`），所以取「High 与 Highest 之间」的那一档，避免与
     * [surfaceContainerHigh] 折平。
     */
    val surfaceVariant: Color get() = lerp(card, muted, 0.75f)

    /**
     * 上游 `PrimaryContainer`：主色的容器调。上游基线里它比面**浅、带主色相**
     * （`#EADDFF`），所以取主色向面靠拢的淡色调。
     */
    val primaryContainer: Color get() = lerp(primary, card, 0.85f)

    /**
     * 上游 `OnPrimaryContainer`：主色容器上的前景色。上游基线里它**很深**
     * （`#21005D`）但**不等于**主色本身，所以取"主色再往前景压一档"，
     * 与 `SurfaceTint`（= [primary]）区分开。
     */
    val onPrimaryContainer: Color get() = lerp(primary, foreground, 0.30f)

    /** 上游 `Secondary`：次要色（上游基线 `#625B71`，中深灰 ⇒ 取 [mutedForeground]）。 */
    val secondary: Color get() = mutedForeground

    /**
     * 上游 `OnSecondary`：次要面上的前景色（上游基线为白）。取"从 [card] 往 [background] 方向"
     * 的那一档，与 `SurfaceBright`/`SurfaceContainerLowest` 等同方向档位区分开。
     */
    val onSecondary: Color get() = lerp(card, background, 0.30f)

    /**
     * 上游 `SecondaryContainer`：次要容器面。上游基线 `#E8DEF8` 比面**深一档且带主色相**，
     * 用作底栏/抽屉"选中药丸"的底 ⇒ 取主色向 [card] 靠拢的淡色调（不能取 [muted]，
     * 那与 `SurfaceContainerHighest` 同值；也不能取 [subtleSurface]，那与 [card] 只差几阶、
     * 选中指示条会隐形）。
     */
    val secondaryContainer: Color get() = lerp(card, primary, 0.12f)

    /**
     * 上游 `OnSecondaryContainer`：次要容器上的前景色。上游基线 `#1D192B` 是**近黑但带主色相**，
     * 用作选中项图标/文字 ⇒ 取"前景再掺一档主色"，与 `InverseSurface`（= [foreground]）
     * 区分开（也不能折到 [mutedForeground]，那样与未选中的 `OnSurfaceVariant` 同色、
     * 选中态就看不出来了）。
     */
    val onSecondaryContainer: Color get() = lerp(foreground, primary, 0.15f)

    /** 上游 `InverseSurface`：反色面（= [foreground]）。 */
    val inverseSurface: Color get() = foreground

    /** 上游 `InverseOnSurface`：反色面上的前景色（= [background]）。 */
    val inverseOnSurface: Color get() = background

    // ── 上游 ColorScheme 的其余角色（按 上游基线台阶逐槽派生，各槽取值互不重合）────
    // 说明：上游这些角色也是由 tonal palette 算出来的；这里让每条都落在**不同**的
    // 插值档上，从而既有各自的位置、又不会互相折平。


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

    /**
     * 上游 `SurfaceBright`：**恒比 [surface] 更亮**的一档。
     *
     * 上游规定 Bright 永远在基准面之上（浅色 `#FEF7FF`→`#FFFFFF`、深色 `#1C1B1F`→`#3B383E`），
     * 而 [surface] 是 [background] 与 [card] 之间的插值，所以"更亮"就是往这两端里**更亮的那端**推。
     * 注意别用 `isDark` 判断哪端更亮：那是"深色主题的 background 必定最暗"的假设，
     * 12 套调色板里并不都成立（有深色套的 card 比 background 暗）。按实测亮度取向才稳。
     *
     * 此前无条件往 [background] 推 —— 深色下 [background] 多半是最暗的一档，于是 Bright 比基准面
     * 还暗，语义整个反向（该槽当前无读取方，属"死且错"，所以修它零视觉影响）。
     */
    val surfaceBright: Color get() = lerp(surface, brighterAnchor, 0.60f)

    /**
     * 上游 `SurfaceDim`：**恒比基准面更暗**的一档（与 [surfaceBright] 反向），
     * 即往两端里更暗的那端推。
     */
    val surfaceDim: Color get() = lerp(surface, darkerAnchor, 0.35f)

    /** [background] 与 [card] 里更亮的一端（二者相同时仍返回其一，插值结果为 [surface] 本身）。 */
    private val brighterAnchor: Color
        get() = if (background.luminance() >= card.luminance()) background else card

    /** [background] 与 [card] 里更暗的一端。 */
    private val darkerAnchor: Color
        get() = if (background.luminance() >= card.luminance()) card else background

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

    // 7. ZCode（Z.ai）—— 完整照搬 ZCode 的 Zai 主题（上游：
    //    packages/ui/src/styles.css 的 `.theme-zai-light` / `.theme-zai-dark`）。
    //
    // 上游那套共 142 个颜色槽，全部在 [ZCodeSlots] 里逐槽给出（每套末尾的 `zcode = ...` 那一行）；
    // 本处这 16 个基础槽是「本库既有组件在用的通用槽」，逐个取上游最贴近那一支的**原值**：
    //   background      <- --color-background        card           <- --color-card
    //   foreground      <- --color-foreground        muted          <- --color-secondary（= --color-tag）
    //   primary         <- --color-primary           accent         <- --color-accent
    //   ring            <- --color-brand             subtleSurface  <- --color-surface
    //   mutedForeground <- --color-foreground-subtle
    //   accentForeground <- --color-interaction-ask-foreground
    // ZCode 没有 cardForeground，取 foreground（那边卡片上的文字就是 --color-foreground）。
    //
    // ⚠️ 与"只填 16 槽、并把半透明合成为不透明"的做法不同，这里**保留上游的 alpha**：
    // 上游的面/描边/次级文字本来就是半透明（`border = rgba(13,13,13,.1)` 等），
    // 合成会改变叠加后的实际观感，也就不再"严格一模一样"。
    // 代价是本套的 border / mutedForeground / subtleSurface 带 alpha，消费它们的既有组件
    // （描边、次级文字、浅面）会按叠加渲染 —— 这与上游一致，属预期。
    // 取值一律来自上游 CSS 的**实际求值结果**（把上游 CSS 交给浏览器、用 canvas 取像素读回 RGBA），
    // 不是手工换算；含 oklch / color-mix 的那些槽因此也与上游逐像素一致。
    val ZCodeLight = CssVariables(
        themeId = "zcode-light",
        name = "ZCode (Light)",
        isDark = false,
        background = Color(0xFFF8F8F8),
        foreground = Color(0xFF262626),
        card = Color(0xFFFFFFFF),
        cardForeground = Color(0xFF262626),
        border = Color(0x1A0A0A0A),
        primary = Color(0xFF000000),
        primaryForeground = Color(0xFFFFFFFF),
        muted = Color(0xFFE6E6E6),
        mutedForeground = Color(0x99262626),
        accent = Color(0xFFEBF4FF),
        accentForeground = Color(0xFF0066DD),
        ring = Color(0xFF000000),
        subtleSurface = Color(0x08000000),
        zcode = ZCodeSlots.ZaiLight,
    )

    val ZCodeDark = CssVariables(
        themeId = "zcode-dark",
        name = "ZCode (Dark)",
        isDark = true,
        background = Color(0xFF161616),
        foreground = Color(0xFFD4D4D4),
        card = Color(0xFF2B2B2B),
        cardForeground = Color(0xFFD4D4D4),
        border = Color(0x1AFFFFFF),
        primary = Color(0xFFFFFFFF),
        primaryForeground = Color(0xFF000000),
        muted = Color(0xFF363636),
        mutedForeground = Color(0x99D4D4D4),
        accent = Color(0xFF001D3D),
        accentForeground = Color(0xFF80BEFF),
        ring = Color(0xFFFFFFFF),
        subtleSurface = Color(0x0DFFFFFF),
        zcode = ZCodeSlots.ZaiDark,
    )

    // 8. ZCode 默认主题 —— 上游 `packages/ui/src/styles.css` 的 `@theme`（浅色）+ `.dark`（深色）。
    //    与上面那套 Zai 是**同一个设计系统的两个变体**：这套是 ZCode 开箱外观（tailwind neutral + sky），
    //    Zai 是品牌主题（纯黑白 brand）。两套的槽表都是 144 个槽（并集），取值各自逐槽照搬上游。
    //    基础槽同样取上游最贴近那一支的原值，含 alpha。
    val ZCodeDefaultLight = CssVariables(
        themeId = "zcode-default-light",
        name = "ZCode (Light)",
        isDark = false,
        background = Color(0xFFFAFAFA),
        foreground = Color(0xFF404040),
        card = Color(0xFFFFFFFF),
        cardForeground = Color(0xFF404040),
        border = Color(0x1A0A0A0A),
        primary = Color(0xFF0A0A0A),
        primaryForeground = Color(0xFFFAFAFA),
        muted = Color(0xFFE5E5E5),
        mutedForeground = Color(0x993F3F3F),
        accent = Color(0xFFF0F9FF),
        accentForeground = Color(0xFF0069A8),
        ring = Color(0xFF00BCFF),
        subtleSurface = Color(0x08000000),
        zcode = ZCodeSlots.DefaultLight,
    )

    val ZCodeDefaultDark = CssVariables(
        themeId = "zcode-default-dark",
        name = "ZCode (Dark)",
        isDark = true,
        background = Color(0xFF171717),
        foreground = Color(0xFFE5E5E5),
        card = Color(0xFF262626),
        cardForeground = Color(0xFFE5E5E5),
        border = Color(0x1AF5F5F5),
        primary = Color(0xFFFAFAFA),
        primaryForeground = Color(0xFF0A0A0A),
        muted = Color(0xFF404040),
        mutedForeground = Color(0x99E4E4E4),
        accent = Color(0x8006304A),
        accentForeground = Color(0xFF74D4FF),
        ring = Color(0xFF00A6F4),
        subtleSurface = Color(0x0DFFFFFF),
        zcode = ZCodeSlots.DefaultDark,
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
        PaletteFamily("zai", "Zai", ProductionPalettes.ZCodeLight, ProductionPalettes.ZCodeDark),
        PaletteFamily(
            "zcode",
            "ZCode",
            ProductionPalettes.ZCodeDefaultLight,
            ProductionPalettes.ZCodeDefaultDark,
        ),
    )

    /** Resolves a palette family to its light or dark variant. */
    fun resolveFamily(family: String, isDark: Boolean): CssVariables =
        families.firstOrNull { it.key == family }?.variant(isDark)
            ?: ProductionPalettes.EditorialLight
}
