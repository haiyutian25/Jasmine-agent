package com.lhzkml.jasmine.core.widgets.theme

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.ThemeResolver
import com.lhzkml.jasmine.core.ui.theme.contentColorFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * `CssVariables.contentColorFor` 的映射表回归。
 *
 * 这张表曾经只有 6 个分支，而**主流容器色**（`surfaceContainer*` 这类派生值）故意不在表里 ——
 * 于是它们全部落到 `else` 返回 `Color.Unspecified`。当时的问题不在"返回 Unspecified"本身
 * （上游同样如此），而在**调用方没有回落**：`Surface` 把结果无条件灌进 `LocalWidgetsContentColor`，
 * 图标遂按自身颜色画，深色主题下就成了"隐形图标"。
 *
 * 所以这里锁两件事：
 *  1. 表里该命中的**必须**命中（否则回落到环境内容色，颜色就不对）；
 *  2. 派生容器色**必须**保持 `Unspecified` —— 这是设计，回落由 `Surface` /
 *     `ProvideContentColorTextStyle` 的 `takeOrElse` 负责。若哪天有人"顺手"给某个派生色
 *     编一个内容色，这条会亮，提醒他先想清楚回落链。
 */
class ContentColorForTest {

    /** 全部出厂调色板：从 [ThemeResolver.families] 展开，新增 family 会自动纳入回归。 */
    private val palettes: List<CssVariables> =
        ThemeResolver.families.flatMap { listOf(it.light, it.dark) }

    @Test
    fun `mapped container slots resolve to their content slot`() {
        palettes.forEach { t ->
            // ── 这些槽的值在每套调色板里都**只**与自己的对应内容槽配对，可以锁死结果 ──
            assertEquals("${t.themeId}: primary", t.primaryForeground, t.contentColorFor(t.primary))
            assertEquals("${t.themeId}: accent", t.accentForeground, t.contentColorFor(t.accent))
            assertEquals(
                "${t.themeId}: primaryContainer",
                t.accentForeground,
                t.contentColorFor(t.primaryContainer),
            )
            assertEquals("${t.themeId}: background", t.foreground, t.contentColorFor(t.background))
            assertEquals("${t.themeId}: card", t.cardForeground, t.contentColorFor(t.card))
            assertEquals("${t.themeId}: secondary", t.onSecondary, t.contentColorFor(t.secondary))
            assertEquals("${t.themeId}: tertiary", t.onTertiary, t.contentColorFor(t.tertiary))
            assertEquals("${t.themeId}: error", t.onError, t.contentColorFor(t.error))
            assertEquals(
                "${t.themeId}: errorContainer",
                t.onErrorContainer,
                t.contentColorFor(t.errorContainer),
            )
        }
    }

    /**
     * 这几个容器槽在个别调色板里会与**更早分支**的槽取同一个值，`when` 按顺序匹配，于是命中的
     * 不是"自己那一支"。实测已知：
     *  * `shadcn-zinc-light.surfaceVariant` 与 `foreground` 同值 → 命中 `foreground` 分支；
     *  * `shadcn-zinc-dark.inverseSurface` 与 `card` 同值 → 命中 `surface/card` 分支；
     *  * `geist-dark.surface`（派生值）与 `subtleSurface` 同值 → 命中 `surfaceVariant/subtleSurface` 分支。
     *
     * 这些不构成缺陷（结果仍是**某个**合理的内容色），但也说明"槽值互不重合"这个前提靠不住，
     * 所以这里只要求**命中某一支而不是 Unspecified** —— 落到 `Unspecified` 才是真问题。
     */
    @Test
    fun `collision-prone container slots still resolve to some content color`() {
        palettes.forEach { t ->
            val slots = linkedMapOf(
                "subtleSurface" to t.subtleSurface,
                "surfaceVariant" to t.surfaceVariant,
                "surface" to t.surface,
                "inverseSurface" to t.inverseSurface,
                "foreground" to t.foreground,
                "secondaryContainer" to t.secondaryContainer,
                "tertiaryContainer" to t.tertiaryContainer,
            )
            slots.forEach { (label, container) ->
                assertNotEquals(
                    "${t.themeId}: $label 应当命中某一支映射（否则会退化成 Unspecified）",
                    Color.Unspecified,
                    t.contentColorFor(container),
                )
            }
        }
    }

    /**
     * `surface` 是**派生值**（`lerp(background, card, 0.25f)`，见 `CssTokens.kt`），在不同调色板里
     * 可能恰好与某个基槽同值（例如 GeistDark 下就等于 `subtleSurface`），所以不锁定它具体命中哪一支；
     * 但要保证它**确实命中某一支** —— 命中不了就会一路 `Unspecified`，正是曾经的隐形图标成因。
     *
     * 同理，`inverseSurface` 在个别调色板里与 `card` 同值（`shadcn-zinc-dark`），那会命中
     * `surface/card` 分支而不是 `inverseSurface` 分支 —— 这里也只要求"命中某一支"，
     * 不锁死具体是哪一支。
     */
    @Test
    fun `derived surface still resolves to some content color`() {
        palettes.forEach { t ->
            assertNotEquals(
                "${t.themeId}: surface 应当命中某一支映射（否则会退化成 Unspecified）",
                Color.Unspecified,
                t.contentColorFor(t.surface),
            )
            assertNotEquals(
                "${t.themeId}: inverseSurface 应当命中某一支映射（否则会退化成 Unspecified）",
                Color.Unspecified,
                t.contentColorFor(t.inverseSurface),
            )
        }
    }

    /**
     * 派生容器色里**真正派生**的那些（`surfaceContainerHigh` / `Highest`：与任何基槽都不相等的
     * 插值色）必须落到 `Unspecified` —— 这是"调用方负责回落"的契约本身，不是遗漏。
     *
     * 注意 `surfaceContainerLowest` / `surfaceContainer` 这类在部分调色板下**就等于** `background`
     * / `card`（`CssTokens.kt` 的派生定义使然），它们命中映射是对的，所以这里排除掉：
     * 断言只针对"值不等于任何基槽"的那些容器色。
     */
    @Test
    fun `genuinely derived container slots stay unspecified so callers fall back`() {
        palettes.forEach { t ->
            val baseSlots = setOf(
                t.background, t.foreground, t.card, t.cardForeground, t.border, t.primary,
                t.primaryForeground, t.muted, t.mutedForeground, t.accent, t.accentForeground,
                t.ring, t.subtleSurface, t.primaryContainer, t.secondary, t.onSecondary,
                t.secondaryContainer, t.onSecondaryContainer, t.tertiary, t.onTertiary,
                t.tertiaryContainer, t.onTertiaryContainer, t.error, t.onError,
                t.errorContainer, t.onErrorContainer, t.inverseSurface, t.inverseOnSurface,
                // 这两支也是 contentColorFor 的判据（`surface, card -> …` / `surfaceVariant, … -> …`）：
                // 派生色一旦恰好与它们同值，命中映射表就是**正确行为**，不该判为"应保持 Unspecified"。
                // 漏收它们会误报 —— ZCode 默认浅色的 surfaceDim 就恰好等于 surface（两者只差 1 阶，
                // Oklab 插值 + 舍入后没动），于是被误判。
                t.surface, t.surfaceVariant,
            )
            listOf(
                "surfaceContainerHigh" to t.surfaceContainerHigh,
                "surfaceContainerHighest" to t.surfaceContainerHighest,
                "surfaceBright" to t.surfaceBright,
                "surfaceDim" to t.surfaceDim,
                "surfaceTint" to t.surfaceTint,
                "scrim" to t.scrim,
            ).filterNot { baseSlots.contains(it.second) }.forEach { (name, container) ->
                assertEquals(
                    "${t.themeId}: 派生容器色 $name (${container.value}) 应当保持 Unspecified（回落交给消费点）",
                    Color.Unspecified,
                    t.contentColorFor(container),
                )
            }
        }
    }

    /** 命中映射的容器色，其结果**不能**还是 `Unspecified`（否则等于没映射）。 */
    @Test
    fun `mapped lookups never return unspecified`() {
        val mapped = listOf<CssVariables.() -> Color>(
            { primary }, { accent }, { primaryContainer }, { background }, { card }, { surface },
            { subtleSurface }, { surfaceVariant }, { secondary }, { secondaryContainer },
            { tertiary }, { tertiaryContainer }, { error }, { errorContainer }, { inverseSurface },
        )
        palettes.forEach { t ->
            mapped.forEach { slot ->
                assertNotEquals(
                    "${t.themeId}: a mapped container returned Unspecified",
                    Color.Unspecified,
                    t.contentColorFor(t.slot()),
                )
            }
        }
    }
}
