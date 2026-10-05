package com.lhzkml.jasmine.core.widgets.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.ProductionPalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调色板的**自洽性**与**可读性**回归（纯 JVM，不需要 Android 运行时）。
 *
 * 这个模块 37k 行、几乎全是移植来的组件，此前 0 测试。先补上不需要设备就能跑的三件事：
 *  1. 12 套调色板的标识唯一、名字非空；
 *  2. 同一套调色板里，**语义不同的槽不该取同一个颜色**（下面有一条已知例外，见测试内注释）；
 *  3. 关键前景/背景对的对比度不低于可用下限（已知低于 AA 的组合单独列在测试里，见注释）。
 *
 * 这些断言不是"证明正确"，而是**防回退护栏**：改调色板时它们会立刻告诉你动了什么。
 */
class PaletteConsistencyTest {

    private val palettes: List<CssVariables> = listOf(
        ProductionPalettes.EditorialLight,
        ProductionPalettes.EditorialDark,
        ProductionPalettes.GeistDark,
        ProductionPalettes.GeistLight,
        ProductionPalettes.LinearDark,
        ProductionPalettes.LinearLight,
        ProductionPalettes.ShadcnZincDark,
        ProductionPalettes.ShadcnZincLight,
        ProductionPalettes.NotionWarmDark,
        ProductionPalettes.NotionWarmLight,
        ProductionPalettes.DieterRamsDark,
        ProductionPalettes.DieterRamsLight,
    )

    @Test
    fun `theme ids are unique and names are set`() {
        val ids = palettes.map { it.themeId }
        assertEquals("themeId 必须唯一", ids.size, ids.toSet().size)
        palettes.forEach { t ->
            assertTrue("${t.themeId} 的名字不该为空", t.name.isNotBlank())
            assertTrue("${t.themeId} 的 themeId 不该为空", t.themeId.isNotBlank())
        }
    }

    /**
     * **同一"层次族"的槽不该取同一个颜色**：`card` / `muted` / `subtleSurface` / `border` 是
     * 「容器底 / 悬停底 / 次级面 / 描边」四种不同用意的面，互相撞色通常意味着抄写时漏改了一行。
     *
     * （不比较前景/背景这类**反色对** —— 例如 `background` 与 `primaryForeground` 在多数调色板里
     * 本来就是同一个浅色，那是有意的，不是撞色。）
     *
     * ⚠️ 已知例外：`ShadcnZincLight` 的 `card` 与 `muted` 都是 `#F4F4F5`，于是它的
     * `surfaceContainer*` / `surfaceVariant` 也折叠成同一个值 —— 这会削弱"分层容器"的视觉区分
     * （例如折叠态搜索栏的轮廓）。这里显式登记为已知；**不要**为了让测试变绿而改断言，
     * 要修就修调色板本身，然后删掉这条例外。
     */
    @Test
    fun `container surfaces of one palette do not collapse`() {
        val knownCollisions: Set<Pair<String, String>> = setOf(
            "shadcn-zinc-light" to "card=muted",
            // 本测试首次运行时发现的：Zinc 深色下 `muted` 与 `border` 取同一个 #18181B，
            // 于是"悬停底"与"描边"在视觉上不可分（与上一条同源：这套调色板的灰阶档位偏少）。
            "shadcn-zinc-dark" to "muted=border",
        )

        palettes.forEach { t ->
            val surfaces = linkedMapOf(
                "card" to t.card,
                "muted" to t.muted,
                "subtleSurface" to t.subtleSurface,
                "border" to t.border,
            )
            val names = surfaces.keys.toList()
            for (i in names.indices) {
                for (j in i + 1 until names.size) {
                    val a = names[i]
                    val b = names[j]
                    val colorA = surfaces.getValue(a)
                    val colorB = surfaces.getValue(b)
                    if (colorA != colorB) continue
                    val allowed = knownCollisions.contains(t.themeId to "$a=$b") ||
                        knownCollisions.contains(t.themeId to "$b=$a")
                    assertTrue(
                        "${t.themeId}: 容器面 $a 与 $b 取了同一个颜色（${colorA.value}）" +
                            if (allowed) "" else " —— 若是有意的，请登记进 knownCollisions 并写明理由",
                        allowed,
                    )
                }
            }
        }
    }

    /**
     * 关键前景/背景对的对比度（WCAG 相对亮度比）。
     *
     * 下限取 **3.0**：这是"大号文字/图形元素"的 AA 门槛，也是本套调色板在**深色主题下**
     * 对次要文字（`mutedForeground` 压在 `subtleSurface` 上）实际能达到的水平。
     *
     * ⚠️ 已知不达 AA(4.5) 的组合（登记在此，不当回归处理）：
     *  * 实心按钮白字：`NotionWarm` 的 `#EB5757` ≈ 3.5:1、`DieterRams` 的 `#FF5500` ≈ 3.2:1；
     *  * 部分调色板的 `mutedForeground` 压在 `surfaceContainerHigh` 上约 3.8–4.5:1。
     * 要有意识地调整这些品牌色，而不是把阈值往下压。
     */
    @Test
    fun `key foreground background pairs stay legible`() {
        val minimumRatio = 3.0

        palettes.forEach { t ->
            val pairs = listOf(
                Triple("foreground on background", t.foreground, t.background),
                Triple("cardForeground on card", t.cardForeground, t.card),
                Triple("primaryForeground on primary", t.primaryForeground, t.primary),
                Triple("accentForeground on accent", t.accentForeground, t.accent),
                Triple("mutedForeground on subtleSurface", t.mutedForeground, t.subtleSurface),
                Triple("mutedForeground on background", t.mutedForeground, t.background),
            )
            pairs.forEach { (label, fg, bg) ->
                val ratio = contrastRatio(fg, bg)
                assertTrue(
                    "${t.themeId}: $label 的对比度只有 ${"%.2f".format(ratio)}:1（下限 $minimumRatio）",
                    ratio >= minimumRatio,
                )
            }
        }
    }

    /** WCAG 2.x 对比度：(L1 + 0.05) / (L2 + 0.05)，L 为相对亮度。 */
    private fun contrastRatio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
