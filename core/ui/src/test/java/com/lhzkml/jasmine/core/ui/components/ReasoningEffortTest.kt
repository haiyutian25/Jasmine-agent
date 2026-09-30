package com.lhzkml.jasmine.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共享的推理档位表（P1-8）：这两条规则以前在两个 feature 里各写一份、没有测试，
 * 靠注释提醒"两处要一起改"。表下沉到 core:ui 之后，规则钉在这里。
 */
class ReasoningEffortTest {

    @Test
    fun `the table is the ordered union of both upstream catalogs`() {
        assertEquals(
            listOf("", "none", "low", "medium", "high", "xhigh", "max", "ultra"),
            ReasoningEffort.options.map { it.value },
        )
    }

    @Test
    fun `an unknown level has no label instead of silently borrowing one`() {
        assertNull(ReasoningEffort.labelRes("minimal"))
        assertEquals(ReasoningEffort.options.first().labelRes, ReasoningEffort.labelRes(""))
    }

    @Test
    fun `an unrestricted model lists every wire level but not the ui-only one`() {
        val options = ReasoningEffort.optionsFor(declared = emptyList(), current = "")
        // 「未设置」永远在，且排在最前。
        assertEquals(ReasoningEffort.UNSET, options.first().value)
        // 界面上换不成的那个档不列（线上没有这个词，只有知道模型支持什么才换得动）。
        assertTrue(options.none { it.value == ReasoningEffort.UI_ONLY_ULTRA })
        assertEquals(
            listOf("", "none", "low", "medium", "high", "xhigh", "max"),
            options.map { it.value },
        )
    }

    @Test
    fun `a declared model lists only the declared levels`() {
        val options = ReasoningEffort.optionsFor(declared = listOf("low", "high"), current = "high")
        assertEquals(listOf("", "low", "high"), options.map { it.value })
    }

    @Test
    fun `the current level stays visible even when the catalog does not declare it`() {
        // 否则面板里看不到自己现在是什么档。
        val options = ReasoningEffort.optionsFor(declared = listOf("low", "high"), current = "max")
        assertEquals(listOf("", "low", "high", "max"), options.map { it.value })
    }

    @Test
    fun `a declared table that already contains unset does not get a second one`() {
        val options = ReasoningEffort.optionsFor(declared = listOf("", "high"), current = "high")
        assertEquals(listOf("", "high"), options.map { it.value })
        assertEquals(1, options.count { it.value == ReasoningEffort.UNSET })
    }
}
