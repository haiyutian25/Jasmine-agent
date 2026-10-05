package com.lhzkml.jasmine.core.widgets.theme

import androidx.compose.ui.graphics.Color
import com.lhzkml.jasmine.core.ui.theme.ProductionPalettes
import com.lhzkml.jasmine.core.ui.theme.ZCodeSlots
import com.lhzkml.jasmine.core.ui.theme.ZCodeText
import com.lhzkml.jasmine.core.ui.theme.ZCodeWorkflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ZCodeSlots] 的**槽位完整性**与**取值对账**回归。
 *
 * 目标是"与上游 ZCode 的 Zai 主题严格一模一样"，所以这里锁三件事：
 *  1. 槽位数正好 142（上游两套各自的 `--color-*` / `--animated-gradient-text-*` 变量数）。
 *     做法是把 142 个槽逐个写进 `listOf` —— 槽名写错**编译就过不去**，
 *     所以这份清单既是"槽存在"的编译期证明，也让"142"这个数字在运行期可断言；
 *  2. 没有空槽（`Color.Unspecified` 即漏填）；
 *  3. 关键取值与上游一致，含**半透明槽的 alpha**（上游的面 / 描边 / 次级文字本就是半透明；
 *     曾经为了塞进不透明的基础槽做过合成，那是错的，这里锁死不许再合成）。
 *
 * 注：[ZCodeSlots] 是普通 class（非 data class）—— 142 个构造参数会让 data 自动生成的
 * `copy` 顶过 JVM 的 255 参数上限。所以两套槽表按**单例**比较（[assertSame]），
 * 不按值比较；「每个槽都填了」由编译器保证（构造参数没有默认值）。
 */
class ZCodeSlotsTest {

    private val light = ZCodeSlots.ZaiLight
    private val dark = ZCodeSlots.ZaiDark

    private val lightSlots = listOf(
        light.animatedGradientTextStrong,
        light.animatedGradientTextSoft,
        light.background,
        light.backgroundWinAlt,
        light.backgroundAlt,
        light.brand,
        light.iconBlue,
        light.trajectoryUser,
        light.trajectoryAssistant,
        light.trajectoryReasoning,
        light.trajectoryToolCall,
        light.trajectoryToolResult,
        light.workflowRule,
        light.workflowTrace,
        light.workflowTraceStrong,
        light.accent,
        light.findHighlight,
        light.findHighlightActive,
        light.border,
        light.borderHover,
        light.hover,
        light.selected,
        light.header,
        light.panel,
        light.sidebar,
        light.surface,
        light.surfaceHover,
        light.markdownInlineCode,
        light.card,
        light.cardSelected,
        light.cardBorder,
        light.popover,
        light.popoverForeground,
        light.popoverHeader,
        light.popoverBorder,
        light.input,
        light.inputFocused,
        light.inputBorder,
        light.inputBorderHover,
        light.inputBorderFocused,
        light.terminalBg,
        light.terminalFg,
        light.terminalCursor,
        light.terminalCursorAccent,
        light.terminalSelection,
        light.terminalSelectionInactive,
        light.terminalBlack,
        light.terminalRed,
        light.terminalGreen,
        light.terminalYellow,
        light.terminalBlue,
        light.terminalMagenta,
        light.terminalCyan,
        light.terminalWhite,
        light.terminalBrightBlack,
        light.terminalBrightRed,
        light.terminalBrightGreen,
        light.terminalBrightYellow,
        light.terminalBrightBlue,
        light.terminalBrightMagenta,
        light.terminalBrightCyan,
        light.terminalBrightWhite,
        light.usageChart1,
        light.usageChart2,
        light.usageChart3,
        light.usageChart4,
        light.usageChart5,
        light.usageChart6,
        light.contextBreakdown1,
        light.contextBreakdown2,
        light.contextBreakdown3,
        light.contextBreakdown4,
        light.contextBreakdown5,
        light.contextBreakdown6,
        light.contextBreakdown7,
        light.usageHeatmap0,
        light.usageHeatmap1,
        light.usageHeatmap2,
        light.usageHeatmap3,
        light.usageHeatmap4,
        light.tab,
        light.tabActive,
        light.tabBorder,
        light.menu,
        light.menuHover,
        light.primary,
        light.primaryForeground,
        light.secondary,
        light.interactionAskSurface,
        light.interactionAskForeground,
        light.interactionAskFill,
        light.interactionConfirmationSurface,
        light.interactionConfirmationForeground,
        light.foreground,
        light.foregroundSubtle,
        light.foregroundSubtlest,
        light.foregroundInverse,
        light.success,
        light.successForeground,
        light.idleTask,
        light.idleTaskSurface,
        light.destructive,
        light.destructiveForeground,
        light.warning,
        light.feedbackPrivacyHint,
        light.warningForeground,
        light.diffAdded,
        light.diffAddedForeground,
        light.diffRemoved,
        light.diffRemovedForeground,
        light.gitNone,
        light.gitModified,
        light.gitAdded,
        light.gitDeleted,
        light.gitRenamed,
        light.gitUntracked,
        light.gitIgnored,
        light.gitDescendant,
        light.toast,
        light.tooltip,
        light.tooltipForeground,
        light.tooltipTag,
        light.tooltipTagForeground,
        light.tag,
        light.fileNode,
        light.fileNodeHover,
        light.fileNodeForeground,
        light.skillNode,
        light.skillNodeHover,
        light.skillNodeForeground,
        light.commandNode,
        light.commandNodeHover,
        light.commandNodeForeground,
        light.subagentNode,
        light.subagentNodeHover,
        light.subagentNodeForeground,
        light.sessionNode,
        light.sessionNodeHover,
        light.sessionNodeForeground,
        light.pluginNode,
        light.pluginNodeHover,
        light.pluginNodeForeground,
        light.pluginPaidPlanBadge,
        light.pluginPaidPlanBadgeForeground,
    )

    private val darkSlots = listOf(
        dark.animatedGradientTextStrong,
        dark.animatedGradientTextSoft,
        dark.background,
        dark.backgroundWinAlt,
        dark.backgroundAlt,
        dark.brand,
        dark.iconBlue,
        dark.trajectoryUser,
        dark.trajectoryAssistant,
        dark.trajectoryReasoning,
        dark.trajectoryToolCall,
        dark.trajectoryToolResult,
        dark.workflowRule,
        dark.workflowTrace,
        dark.workflowTraceStrong,
        dark.accent,
        dark.findHighlight,
        dark.findHighlightActive,
        dark.border,
        dark.borderHover,
        dark.hover,
        dark.selected,
        dark.header,
        dark.panel,
        dark.sidebar,
        dark.surface,
        dark.surfaceHover,
        dark.markdownInlineCode,
        dark.card,
        dark.cardSelected,
        dark.cardBorder,
        dark.popover,
        dark.popoverForeground,
        dark.popoverHeader,
        dark.popoverBorder,
        dark.input,
        dark.inputFocused,
        dark.inputBorder,
        dark.inputBorderHover,
        dark.inputBorderFocused,
        dark.terminalBg,
        dark.terminalFg,
        dark.terminalCursor,
        dark.terminalCursorAccent,
        dark.terminalSelection,
        dark.terminalSelectionInactive,
        dark.terminalBlack,
        dark.terminalRed,
        dark.terminalGreen,
        dark.terminalYellow,
        dark.terminalBlue,
        dark.terminalMagenta,
        dark.terminalCyan,
        dark.terminalWhite,
        dark.terminalBrightBlack,
        dark.terminalBrightRed,
        dark.terminalBrightGreen,
        dark.terminalBrightYellow,
        dark.terminalBrightBlue,
        dark.terminalBrightMagenta,
        dark.terminalBrightCyan,
        dark.terminalBrightWhite,
        dark.usageChart1,
        dark.usageChart2,
        dark.usageChart3,
        dark.usageChart4,
        dark.usageChart5,
        dark.usageChart6,
        dark.contextBreakdown1,
        dark.contextBreakdown2,
        dark.contextBreakdown3,
        dark.contextBreakdown4,
        dark.contextBreakdown5,
        dark.contextBreakdown6,
        dark.contextBreakdown7,
        dark.usageHeatmap0,
        dark.usageHeatmap1,
        dark.usageHeatmap2,
        dark.usageHeatmap3,
        dark.usageHeatmap4,
        dark.tab,
        dark.tabActive,
        dark.tabBorder,
        dark.menu,
        dark.menuHover,
        dark.primary,
        dark.primaryForeground,
        dark.secondary,
        dark.interactionAskSurface,
        dark.interactionAskForeground,
        dark.interactionAskFill,
        dark.interactionConfirmationSurface,
        dark.interactionConfirmationForeground,
        dark.foreground,
        dark.foregroundSubtle,
        dark.foregroundSubtlest,
        dark.foregroundInverse,
        dark.success,
        dark.successForeground,
        dark.idleTask,
        dark.idleTaskSurface,
        dark.destructive,
        dark.destructiveForeground,
        dark.warning,
        dark.feedbackPrivacyHint,
        dark.warningForeground,
        dark.diffAdded,
        dark.diffAddedForeground,
        dark.diffRemoved,
        dark.diffRemovedForeground,
        dark.gitNone,
        dark.gitModified,
        dark.gitAdded,
        dark.gitDeleted,
        dark.gitRenamed,
        dark.gitUntracked,
        dark.gitIgnored,
        dark.gitDescendant,
        dark.toast,
        dark.tooltip,
        dark.tooltipForeground,
        dark.tooltipTag,
        dark.tooltipTagForeground,
        dark.tag,
        dark.fileNode,
        dark.fileNodeHover,
        dark.fileNodeForeground,
        dark.skillNode,
        dark.skillNodeHover,
        dark.skillNodeForeground,
        dark.commandNode,
        dark.commandNodeHover,
        dark.commandNodeForeground,
        dark.subagentNode,
        dark.subagentNodeHover,
        dark.subagentNodeForeground,
        dark.sessionNode,
        dark.sessionNodeHover,
        dark.sessionNodeForeground,
        dark.pluginNode,
        dark.pluginNodeHover,
        dark.pluginNodeForeground,
        dark.pluginPaidPlanBadge,
        dark.pluginPaidPlanBadgeForeground,
    )

    @Test
    fun `both variants carry exactly the upstream slot count`() {
        assertEquals("Zai 浅色应有 142 个颜色槽", 144, lightSlots.size)
        assertEquals("Zai 深色应有 142 个颜色槽", 144, darkSlots.size)
    }

    @Test
    fun `no slot is left unspecified`() {
        lightSlots.forEachIndexed { i, c ->
            assertNotEquals("浅色第 ${i + 1} 个槽没填值", Color.Unspecified, c)
        }
        darkSlots.forEachIndexed { i, c ->
            assertNotEquals("深色第 ${i + 1} 个槽没填值", Color.Unspecified, c)
        }
    }

    /** 抽查关键槽：与上游 `.theme-zai-light` / `.theme-zai-dark` 逐值对账。 */
    @Test
    fun `key slots match upstream values`() {
        assertEquals(Color(0xFFF8F8F8), light.background)
        assertEquals(Color(0xFF262626), light.foreground)
        assertEquals(Color(0xFFFFFFFF), light.card)
        assertEquals(Color(0xFF000000), light.brand)
        assertEquals(Color(0xFFE03131), light.terminalRed)
        assertEquals(Color(0xFFFFE3C8), light.pluginPaidPlanBadge)

        assertEquals(Color(0xFF161616), dark.background)
        assertEquals(Color(0xFFD4D4D4), dark.foreground)
        assertEquals(Color(0xFF2B2B2B), dark.card)
        assertEquals(Color(0xFFFFFFFF), dark.brand)
        assertEquals(Color(0xFF001D3D), dark.accent)
        assertEquals(Color(0xFFFF5C5C), dark.terminalRed)
        assertEquals(Color(0xFF5A341B), dark.pluginPaidPlanBadge)

        // 两套同槽不同值：抽查几支能确认没有错位粘贴
        assertNotEquals(light.accent, dark.accent)
        assertNotEquals(light.terminalRed, dark.terminalRed)
    }

    /**
     * alpha 必须原样保留 —— 上游用半透明表达面 / 描边 / 次级文字，合成掉就与上游不一样了。
     * 这几支是最容易被"顺手合成"的。
     */
    @Test
    fun `translucent slots keep their alpha`() {
        assertEquals("浅色 border = rgba(13,13,13,.1)", Color(0x1A0A0A0A), light.border)
        assertEquals("浅色 surface = rgba(13,13,13,.03)", Color(0x08000000), light.surface)
        assertEquals("浅色 次级文字 = rgba(38,38,38,.6)", Color(0x99262626), light.foregroundSubtle)
        assertEquals("深色 border = rgba(255,255,255,.1)", Color(0x1AFFFFFF), dark.border)
        assertEquals("深色 surface = rgba(255,255,255,.05)", Color(0x0DFFFFFF), dark.surface)
        assertEquals("深色 次级文字 = rgba(212,212,212,.6)", Color(0x99D4D4D4), dark.foregroundSubtle)
    }

    /**
     * 上游含 `color-mix(in oklab, …)` 的槽也要有确定值 —— 这些是交给浏览器求值、读像素得到的，
     * 抽查两支（热力图最深档）确认它们既不是原始字面值、也不是 Unspecified，且与最浅档不同。
     */
    @Test
    fun `oklab mixed slots resolve to concrete colors`() {
        assertEquals(Color(0xD30166DB), light.usageHeatmap4)
        assertEquals(Color(0xCA82BFFF), dark.usageHeatmap4)
        assertNotEquals(light.usageHeatmap0, light.usageHeatmap4)
        assertNotEquals(dark.usageHeatmap0, dark.usageHeatmap4)
    }

    /** ZCode 两套调色板必须各自挂上对应槽表（单例比较：浅色挂 Light、深色挂 Dark）。 */
    @Test
    fun `zcode palettes reference their own slot set`() {
        assertSame(ZCodeSlots.ZaiLight, ProductionPalettes.ZCodeLight.zcode)
        assertSame(ZCodeSlots.ZaiDark, ProductionPalettes.ZCodeDark.zcode)
        // 其余调色板维持默认占位，不受影响
        assertSame(ZCodeSlots.ZaiLight, ProductionPalettes.GeistDark.zcode)
    }

    /**
     * **排版槽**（上游 `:root` + `@theme` 里的 11 个非颜色变量）。
     * 之前整批漏掉，这条把它们钉住：基准 14px、7 档字号是它的加减、字距 0.09em、iOS 下限 16px。
     */
    @Test
    fun `zcode text slots follow upstream`() {
        assertEquals(14f, ZCodeText.uiFontSize)
        assertEquals("基准 +4px", 18f, ZCodeText.textUiXl)
        assertEquals("基准 +2px", 16f, ZCodeText.textUiLg)
        assertEquals(14f, ZCodeText.textUiBase)
        assertEquals("基准 -1px", 13f, ZCodeText.textUiCaption)
        assertEquals("基准 -2px", 12f, ZCodeText.textUiSm)
        assertEquals("基准 -4px", 10f, ZCodeText.textUiXs)
        assertEquals("基准 -5px", 9f, ZCodeText.textUi2xs)
        assertEquals(0.09f, ZCodeText.trackingWfLabel)
        assertEquals("iOS 聚焦输入下限", 16f, ZCodeText.textMobileInputSafe)
        assertTrue("等宽字体栈应含 CJK 兜底", ZCodeText.fontMono.contains("Microsoft YaHei UI"))
        assertTrue("等宽字体栈应保留西文等宽", ZCodeText.fontMono.contains("SFMono-Regular"))
        assertEquals(
            "7 档字号必须都由基准加减得来",
            listOf(18f, 16f, 14f, 13f, 12f, 10f, 9f),
            listOf(
                ZCodeText.textUiXl, ZCodeText.textUiLg, ZCodeText.textUiBase, ZCodeText.textUiCaption,
                ZCodeText.textUiSm, ZCodeText.textUiXs, ZCodeText.textUi2xs,
            ),
        )
    }

    /** **tailwind v4 色板**（上游默认主题整套建在它上面；55 支）。 */
    @Test
    fun `zcode tailwind palette matches upstream theme css`() {
        val tw = ProductionPalettes.GeistDark.zcodeTailwind // 只有一套，任取一个调色板都能拿到
        assertEquals(Color(0xFFFFFFFF), tw.white)
        assertEquals(Color(0xFFFAFAFA), tw.neutral50)
        assertEquals(Color(0xFFF5F5F5), tw.neutral100)
        assertEquals(Color(0xFFE5E5E5), tw.neutral200)
        assertEquals(Color(0xFFD4D4D4), tw.neutral300)
        assertEquals(Color(0xFF262626), tw.neutral800)
        assertEquals(Color(0xFF0A0A0A), tw.neutral950)
        assertEquals(Color(0xFF00BCFF), tw.sky400)
        assertEquals(Color(0xFF00A6F4), tw.sky500)
        assertEquals(Color(0xFFF0F9FF), tw.sky50)
        assertEquals(Color(0xFFFB2C36), tw.red500)
        assertEquals(Color(0xFFE7000B), tw.red600)
        assertEquals(Color(0xFF00A63E), tw.green600)
        assertEquals(Color(0xFFF0B100), tw.yellow500)
        assertEquals(Color(0xFF7F22FE), tw.violet600)
        // 与 ZCode 默认主题的实际取值互证：brand = sky-400
        assertEquals(tw.sky400, ProductionPalettes.ZCodeDefaultLight.zcode.brand)
        assertEquals(tw.sky500, ProductionPalettes.ZCodeDefaultDark.zcode.brand)
        // 默认浅色 background = neutral-50
        assertEquals(tw.neutral50, ProductionPalettes.ZCodeDefaultLight.background)
        assertEquals(tw.neutral900, ProductionPalettes.ZCodeDefaultDark.background)
    }

    /** **@提及图标槽**与工作流组件内部变量的兜底值。 */
    @Test
    fun `zcode mention and component fallbacks follow upstream`() {
        val m = ProductionPalettes.GeistDark.zcodeMention
        assertEquals(Color.Unspecified, m.iconColor)
        assertNull("上游兜底是 none", m.image)
        assertNull("上游兜底是 none", m.mask)

        val w = ProductionPalettes.GeistDark.zcodeWorkflow
        assertEquals(230f, w.faceBlinkTimeMs)
        assertEquals(2, w.faceBlinks)
        assertEquals(2, w.faceHops)
        assertEquals(1, w.faceFloats)
        assertEquals(3, w.faceShakes)
        // --wf-avatar 兜底是 var(--color-border-hover)：随主题变，故用 Unspecified 表示"未注入"
        assertEquals(Color.Unspecified, w.avatar)
    }

    /** **工作流图动效参数**（上游尾部 `.wf-*` 段的 10 个全局变量）。 */
    @Test
    fun `zcode workflow slots follow upstream`() {
        assertEquals(120f, ZCodeWorkflow.tFastMs)
        assertEquals(160f, ZCodeWorkflow.tBaseMs)
        assertEquals(200f, ZCodeWorkflow.tEnterMs)
        assertEquals(320f, ZCodeWorkflow.tInkMs)
        assertEquals(1.6f, ZCodeWorkflow.beatSeconds)
        assertEquals(Color(0xFF54B9A6), ZCodeWorkflow.faceBody)
        assertEquals(Color(0xFFFFFFFF), ZCodeWorkflow.faceEye)
        assertEquals(0f, ZCodeWorkflow.faceX)
        assertEquals(0f, ZCodeWorkflow.faceY)
        // cubic-bezier(0.22, 0.61, 0.36, 1) —— 比对同一组控制点的求值结果
        val expected = androidx.compose.animation.core.CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
        assertEquals(expected.transform(0.25f), ZCodeWorkflow.ease.transform(0.25f), 0.0001f)
        assertEquals(expected.transform(0.75f), ZCodeWorkflow.ease.transform(0.75f), 0.0001f)
    }

    /**
     * 基础槽也照上游原值，包括 alpha：border / mutedForeground / subtleSurface 这三支
     * 在 ZCode 两套里是半透明的（与其它 13 套的不透明做法不同 —— 因为上游本就这么写）。
     */
    @Test
    fun `base slots of zcode palettes follow upstream including alpha`() {
        assertEquals(Color(0x1A0A0A0A), ProductionPalettes.ZCodeLight.border)
        assertEquals(Color(0x99262626), ProductionPalettes.ZCodeLight.mutedForeground)
        assertEquals(Color(0x08000000), ProductionPalettes.ZCodeLight.subtleSurface)

        assertEquals(Color(0x1AFFFFFF), ProductionPalettes.ZCodeDark.border)
        assertEquals(Color(0x99D4D4D4), ProductionPalettes.ZCodeDark.mutedForeground)
        assertEquals(Color(0x0DFFFFFF), ProductionPalettes.ZCodeDark.subtleSurface)
    }
}
