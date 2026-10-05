package com.lhzkml.jasmine.core.widgets.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.ProductionPalettes
import com.lhzkml.jasmine.core.ui.theme.ThemeResolver
import com.lhzkml.jasmine.core.widgets.slider.SliderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 令牌槽的**语义契约**回归（纯 JVM）。
 *
 * 与 [PaletteConsistencyTest] 的分工：那边管"每套调色板自身是否自洽"，
 * 这边管"某些槽的**方向/可覆盖性**这些不明显、但改错了会静默走偏的约定"。
 * 三条断言各自对应一个真实修过的缺陷，注释里写明了原来错在哪。
 */
class TokenSlotsContractTest {

    /** 全部出厂调色板：从 [ThemeResolver.families] 展开，新增 family 会自动纳入回归。 */
    private val palettes: List<CssVariables> =
        ThemeResolver.families.flatMap { listOf(it.light, it.dark) }

    /**
     * 上游规定 `SurfaceBright` **恒在基准面之上**、`SurfaceDim` **恒在其下**
     * （浅色 `#FEF7FF`→`#FFFFFF`、深色 `#1C1B1F`→`#3B383E`）。
     *
     * 原先两者都是"无条件往某个固定槽推"：深色下往 `background`（最暗的一档）推，
     * 于是 Bright 比基准面还暗、Dim 比基准面还亮 —— 语义整个反向。
     * 这条用"不反向"来锁（用 `>=`/`<=` 而不是严格不等：调色板里 `background == card`
     * 的极端情况下插值不动，那不算错）。
     */
    @Test
    fun `surfaceBright stays above and surfaceDim below the base surface`() {
        palettes.forEach { t ->
            val base = t.surface.luminance()
            assertTrue(
                "${t.themeId}: surfaceBright 的亮度（${t.surfaceBright.luminance()}）" +
                    "不该低于 surface（$base）—— 深色主题下会反向",
                t.surfaceBright.luminance() >= base,
            )
            assertTrue(
                "${t.themeId}: surfaceDim 的亮度（${t.surfaceDim.luminance()}）" +
                    "不该高于 surface（$base）—— 深色主题下会反向",
                t.surfaceDim.luminance() <= base,
            )
        }
    }

    /**
     * `error` / `onError` / `scrim` 曾经写死在 [CssVariables] 类体内，等于**绕过调色板**：
     * 各套主题曾共用一个红，谁想调都得改共用文件。现在是构造参数 —— 这条锁住"真的能按套覆盖"，
     * 并顺带锁住"派生槽跟着走"（`errorContainer` 是由 `error` 推的）。
     */
    @Test
    fun `error and scrim slots are per palette overridable`() {
        val base = ProductionPalettes.GeistDark
        val brandRed = Color(0xFF9A0007)
        val custom = base.copy(error = brandRed, scrim = Color(0xCC000000))

        assertEquals("copy 传进去的 error 应当生效", brandRed, custom.error)
        assertEquals(Color(0xCC000000), custom.scrim)
        assertTrue(
            "errorContainer 由 error 派生，error 改了它也该变",
            custom.errorContainer != base.errorContainer,
        )
        // copy 出来的实例是独立值，原实例不受影响：默认仍是深色那支上游基线红
        assertEquals(Color(0xFFF2B8B5), base.error)
        assertEquals(
            "浅色套应取浅色那支基线红",
            Color(0xFFB3261E),
            ProductionPalettes.GeistLight.error,
        )
    }

    /**
     * `SliderState` 的构造期校验：反向 `valueRange`（`1f..0f` 是合法表达式）与负 `steps`。
     * 上游把失败留给了更下游 —— 反向范围会在 setter 的 `coerceIn` 抛错（而基础重载是在
     * **组合期**写 `state.value`，栈里看不出是调用方传错了范围）；负 steps 会在算刻度时
     * 才炸（`steps = -1` 更隐蔽：产生 NaN 刻度）。这里要求构造即失败、且消息说得清原因。
     */
    @Test
    fun `slider state rejects descending range`() {
        val failure = runCatching { SliderState(valueRange = 1f..0f) }.exceptionOrNull()
        assertTrue(
            "反向 valueRange 应在构造期被拒绝，实际：$failure",
            failure is IllegalArgumentException,
        )
        assertTrue(
            "错误信息应点明范围要求，实际：${failure?.message}",
            failure?.message?.contains("ascending") == true,
        )
    }

    @Test
    fun `slider state rejects negative steps`() {
        val failure = runCatching { SliderState(steps = -1) }.exceptionOrNull()
        assertTrue(
            "负 steps 应在构造期被拒绝，实际：$failure",
            failure is IllegalArgumentException,
        )
    }

    /** 合法入参照旧：构造不该抛，且值被 coerce 进范围（与上游行为逐位一致）。 */
    @Test
    fun `slider state accepts valid input and coerces value`() {
        val state = SliderState(value = 5f, steps = 4, valueRange = 0f..10f)
        assertEquals(0f..10f, state.valueRange)
        assertEquals(4, state.steps)
        assertTrue("越界值应被 coerce 回范围", state.value in 0f..10f)
    }
}
