package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * ZCode（Z.ai）主题的**完整槽表** —— 逐槽照搬上游
 * `packages/ui/src/styles.css` 里的 `.theme-zai-light` / `.theme-zai-dark`。
 *
 * 三条口径，与需求「严格一模一样」对应：
 *
 *  1. **槽位完整**：上游两套各有 142 个变量，这里就 142 个字段，一个不少、一个不多
 *     （字段顺序与上游声明顺序一致；`--color-foo-bar` → `fooBar`，`--animated-gradient-text-*`
 *     这两个没有 `--color-` 前缀的也照收）。
 *  2. **取值原样**：数值来自上游 CSS 的**实际计算结果**。上游大量用
 *     `color-mix(in oklab, …)` / `oklch()` / `rgba()` 表达，这里不是手工换算，而是把上游的
 *     CSS 原样交给浏览器、再用 canvas 取像素读回的 RGBA（`getImageData`），
 *     所以结果就是浏览器渲染出的那一色 —— 与上游逐像素一致。
 *  3. **alpha 原样**：上游的面 / 描边 / 状态层大量是半透明（`rgba(13,13,13,.1)` 之类），
 *     这里**保留 alpha、不做合成**（与上一版为了塞进 16 个不透明槽而做合成不同）。
 *     所以本表里的 Color 可能与 `CssVariables` 的基础槽语义重叠但取值不同 —— 那是刻意的：
 *     基础槽服务本库既有组件，本表服务「与上游逐槽对齐」。
 *
 * 上游还引用了 4 个 tailwind 色板变量（`--color-neutral-800` / `-300` / `--color-yellow-600` / `-300`），
 * 它们的值按 `tailwindcss@4/theme.css` 的 oklch 原文喂给浏览器参与计算（见下方取值即为最终结果，
 * 例如 `foreground` = `#262626`，正是 neutral-800 的 sRGB）。
 *
 * 非颜色槽（`--font-mono`、`--text-ui-*`、`--tracking-wf-label`、`--ui-font-size`、
 * `--text-mobile-input-safe` 共 12 个）属于全局排版，不进本表 —— 本库的排版唯一来源是
 * `AppTypography`，重复一份只会造成第二个真相源。
 *
 * **为什么是普通 class 而不是 `data class`**：142 个构造参数会让 `data` 自动生成的
 * `copy` / `componentN` 系列把方法签名顶过 JVM 的 255 参数上限，类一加载就抛
 * `ClassFormatError: Too many arguments in method signature`（这是实打实撞过的坑）。
 * 这里不需要按值比较 —— 两套槽表是两个单例常量（[Light] / [Dark]），引用相等即值相等。
 * 「每个槽都被赋了值」由编译器保证：所有构造参数都**没有默认值**。
 */
@Immutable
class ZCodeSlots(
    private val animatedGradientTextStrongArgb: Int,
    private val animatedGradientTextSoftArgb: Int,
    private val backgroundArgb: Int,
    private val backgroundWinAltArgb: Int,
    private val backgroundAltArgb: Int,
    private val brandArgb: Int,
    private val iconBlueArgb: Int,
    private val trajectoryUserArgb: Int,
    private val trajectoryAssistantArgb: Int,
    private val trajectoryReasoningArgb: Int,
    private val trajectoryToolCallArgb: Int,
    private val trajectoryToolResultArgb: Int,
    private val workflowRuleArgb: Int,
    private val workflowTraceArgb: Int,
    private val workflowTraceStrongArgb: Int,
    private val accentArgb: Int,
    private val findHighlightArgb: Int,
    private val findHighlightActiveArgb: Int,
    private val borderArgb: Int,
    private val borderHoverArgb: Int,
    private val hoverArgb: Int,
    private val selectedArgb: Int,
    private val headerArgb: Int,
    private val panelArgb: Int,
    private val sidebarArgb: Int,
    private val surfaceArgb: Int,
    private val surfaceHoverArgb: Int,
    private val markdownInlineCodeArgb: Int,
    private val cardArgb: Int,
    private val cardSelectedArgb: Int,
    private val cardBorderArgb: Int,
    private val popoverArgb: Int,
    private val popoverForegroundArgb: Int,
    private val popoverHeaderArgb: Int,
    private val popoverBorderArgb: Int,
    private val inputArgb: Int,
    private val inputFocusedArgb: Int,
    private val inputBorderArgb: Int,
    private val inputBorderHoverArgb: Int,
    private val inputBorderFocusedArgb: Int,
    private val terminalBgArgb: Int,
    private val terminalFgArgb: Int,
    private val terminalCursorArgb: Int,
    private val terminalCursorAccentArgb: Int,
    private val terminalSelectionArgb: Int,
    private val terminalSelectionInactiveArgb: Int,
    private val terminalBlackArgb: Int,
    private val terminalRedArgb: Int,
    private val terminalGreenArgb: Int,
    private val terminalYellowArgb: Int,
    private val terminalBlueArgb: Int,
    private val terminalMagentaArgb: Int,
    private val terminalCyanArgb: Int,
    private val terminalWhiteArgb: Int,
    private val terminalBrightBlackArgb: Int,
    private val terminalBrightRedArgb: Int,
    private val terminalBrightGreenArgb: Int,
    private val terminalBrightYellowArgb: Int,
    private val terminalBrightBlueArgb: Int,
    private val terminalBrightMagentaArgb: Int,
    private val terminalBrightCyanArgb: Int,
    private val terminalBrightWhiteArgb: Int,
    private val usageChart1Argb: Int,
    private val usageChart2Argb: Int,
    private val usageChart3Argb: Int,
    private val usageChart4Argb: Int,
    private val usageChart5Argb: Int,
    private val usageChart6Argb: Int,
    private val contextBreakdown1Argb: Int,
    private val contextBreakdown2Argb: Int,
    private val contextBreakdown3Argb: Int,
    private val contextBreakdown4Argb: Int,
    private val contextBreakdown5Argb: Int,
    private val contextBreakdown6Argb: Int,
    private val contextBreakdown7Argb: Int,
    private val usageHeatmap0Argb: Int,
    private val usageHeatmap1Argb: Int,
    private val usageHeatmap2Argb: Int,
    private val usageHeatmap3Argb: Int,
    private val usageHeatmap4Argb: Int,
    private val tabArgb: Int,
    private val tabActiveArgb: Int,
    private val tabBorderArgb: Int,
    private val menuArgb: Int,
    private val menuHoverArgb: Int,
    private val primaryArgb: Int,
    private val primaryForegroundArgb: Int,
    private val secondaryArgb: Int,
    private val interactionAskSurfaceArgb: Int,
    private val interactionAskForegroundArgb: Int,
    private val interactionAskFillArgb: Int,
    private val interactionConfirmationSurfaceArgb: Int,
    private val interactionConfirmationForegroundArgb: Int,
    private val foregroundArgb: Int,
    private val foregroundSubtleArgb: Int,
    private val foregroundSubtlestArgb: Int,
    private val foregroundInverseArgb: Int,
    private val successArgb: Int,
    private val successForegroundArgb: Int,
    private val idleTaskArgb: Int,
    private val idleTaskSurfaceArgb: Int,
    private val destructiveArgb: Int,
    private val destructiveForegroundArgb: Int,
    private val warningArgb: Int,
    private val feedbackPrivacyHintArgb: Int,
    private val warningForegroundArgb: Int,
    private val diffAddedArgb: Int,
    private val diffAddedForegroundArgb: Int,
    private val diffRemovedArgb: Int,
    private val diffRemovedForegroundArgb: Int,
    private val gitNoneArgb: Int,
    private val gitModifiedArgb: Int,
    private val gitAddedArgb: Int,
    private val gitDeletedArgb: Int,
    private val gitRenamedArgb: Int,
    private val gitUntrackedArgb: Int,
    private val gitIgnoredArgb: Int,
    private val gitDescendantArgb: Int,
    private val toastArgb: Int,
    private val tooltipArgb: Int,
    private val tooltipForegroundArgb: Int,
    private val tooltipTagArgb: Int,
    private val tooltipTagForegroundArgb: Int,
    private val tagArgb: Int,
    private val fileNodeArgb: Int,
    private val fileNodeHoverArgb: Int,
    private val fileNodeForegroundArgb: Int,
    private val skillNodeArgb: Int,
    private val skillNodeHoverArgb: Int,
    private val skillNodeForegroundArgb: Int,
    private val commandNodeArgb: Int,
    private val commandNodeHoverArgb: Int,
    private val commandNodeForegroundArgb: Int,
    private val subagentNodeArgb: Int,
    private val subagentNodeHoverArgb: Int,
    private val subagentNodeForegroundArgb: Int,
    private val sessionNodeArgb: Int,
    private val sessionNodeHoverArgb: Int,
    private val sessionNodeForegroundArgb: Int,
    private val pluginNodeArgb: Int,
    private val pluginNodeHoverArgb: Int,
    private val pluginNodeForegroundArgb: Int,
    private val pluginPaidPlanBadgeArgb: Int,
    private val pluginPaidPlanBadgeForegroundArgb: Int,
) {
    // ── 逐槽的只读入口 ───────────────────────────────────────────────────────
    // 对外照旧是 `slots.terminalRed`（Color），但**构造参数收的是 ARGB 的 Int**。
    // 原因不是口味：`Color` 是 value class、编译后是 `long`，每个参数占 **2 个** JVM 参数位，
    // 142 个就是 284 位 —— 直接顶穿 JVM 的 255 上限，类一加载就抛
    // `ClassFormatError: Too many arguments in method signature`（撞过）。换成 Int 后
    // 每参 1 位、共 142 位，安全；下面的属性都是 getter，不占参数位。
    // 取值的可比性不受影响：`Color(argb)` 与 `Color(0xFF…)` 得到同一个 Color。
    val animatedGradientTextStrong: Color get() = Color(animatedGradientTextStrongArgb)
    val animatedGradientTextSoft: Color get() = Color(animatedGradientTextSoftArgb)
    val background: Color get() = Color(backgroundArgb)
    val backgroundWinAlt: Color get() = Color(backgroundWinAltArgb)
    val backgroundAlt: Color get() = Color(backgroundAltArgb)
    val brand: Color get() = Color(brandArgb)
    val iconBlue: Color get() = Color(iconBlueArgb)
    val trajectoryUser: Color get() = Color(trajectoryUserArgb)
    val trajectoryAssistant: Color get() = Color(trajectoryAssistantArgb)
    val trajectoryReasoning: Color get() = Color(trajectoryReasoningArgb)
    val trajectoryToolCall: Color get() = Color(trajectoryToolCallArgb)
    val trajectoryToolResult: Color get() = Color(trajectoryToolResultArgb)
    val workflowRule: Color get() = Color(workflowRuleArgb)
    val workflowTrace: Color get() = Color(workflowTraceArgb)
    val workflowTraceStrong: Color get() = Color(workflowTraceStrongArgb)
    val accent: Color get() = Color(accentArgb)
    val findHighlight: Color get() = Color(findHighlightArgb)
    val findHighlightActive: Color get() = Color(findHighlightActiveArgb)
    val border: Color get() = Color(borderArgb)
    val borderHover: Color get() = Color(borderHoverArgb)
    val hover: Color get() = Color(hoverArgb)
    val selected: Color get() = Color(selectedArgb)
    val header: Color get() = Color(headerArgb)
    val panel: Color get() = Color(panelArgb)
    val sidebar: Color get() = Color(sidebarArgb)
    val surface: Color get() = Color(surfaceArgb)
    val surfaceHover: Color get() = Color(surfaceHoverArgb)
    val markdownInlineCode: Color get() = Color(markdownInlineCodeArgb)
    val card: Color get() = Color(cardArgb)
    val cardSelected: Color get() = Color(cardSelectedArgb)
    val cardBorder: Color get() = Color(cardBorderArgb)
    val popover: Color get() = Color(popoverArgb)
    val popoverForeground: Color get() = Color(popoverForegroundArgb)
    val popoverHeader: Color get() = Color(popoverHeaderArgb)
    val popoverBorder: Color get() = Color(popoverBorderArgb)
    val input: Color get() = Color(inputArgb)
    val inputFocused: Color get() = Color(inputFocusedArgb)
    val inputBorder: Color get() = Color(inputBorderArgb)
    val inputBorderHover: Color get() = Color(inputBorderHoverArgb)
    val inputBorderFocused: Color get() = Color(inputBorderFocusedArgb)
    val terminalBg: Color get() = Color(terminalBgArgb)
    val terminalFg: Color get() = Color(terminalFgArgb)
    val terminalCursor: Color get() = Color(terminalCursorArgb)
    val terminalCursorAccent: Color get() = Color(terminalCursorAccentArgb)
    val terminalSelection: Color get() = Color(terminalSelectionArgb)
    val terminalSelectionInactive: Color get() = Color(terminalSelectionInactiveArgb)
    val terminalBlack: Color get() = Color(terminalBlackArgb)
    val terminalRed: Color get() = Color(terminalRedArgb)
    val terminalGreen: Color get() = Color(terminalGreenArgb)
    val terminalYellow: Color get() = Color(terminalYellowArgb)
    val terminalBlue: Color get() = Color(terminalBlueArgb)
    val terminalMagenta: Color get() = Color(terminalMagentaArgb)
    val terminalCyan: Color get() = Color(terminalCyanArgb)
    val terminalWhite: Color get() = Color(terminalWhiteArgb)
    val terminalBrightBlack: Color get() = Color(terminalBrightBlackArgb)
    val terminalBrightRed: Color get() = Color(terminalBrightRedArgb)
    val terminalBrightGreen: Color get() = Color(terminalBrightGreenArgb)
    val terminalBrightYellow: Color get() = Color(terminalBrightYellowArgb)
    val terminalBrightBlue: Color get() = Color(terminalBrightBlueArgb)
    val terminalBrightMagenta: Color get() = Color(terminalBrightMagentaArgb)
    val terminalBrightCyan: Color get() = Color(terminalBrightCyanArgb)
    val terminalBrightWhite: Color get() = Color(terminalBrightWhiteArgb)
    val usageChart1: Color get() = Color(usageChart1Argb)
    val usageChart2: Color get() = Color(usageChart2Argb)
    val usageChart3: Color get() = Color(usageChart3Argb)
    val usageChart4: Color get() = Color(usageChart4Argb)
    val usageChart5: Color get() = Color(usageChart5Argb)
    val usageChart6: Color get() = Color(usageChart6Argb)
    val contextBreakdown1: Color get() = Color(contextBreakdown1Argb)
    val contextBreakdown2: Color get() = Color(contextBreakdown2Argb)
    val contextBreakdown3: Color get() = Color(contextBreakdown3Argb)
    val contextBreakdown4: Color get() = Color(contextBreakdown4Argb)
    val contextBreakdown5: Color get() = Color(contextBreakdown5Argb)
    val contextBreakdown6: Color get() = Color(contextBreakdown6Argb)
    val contextBreakdown7: Color get() = Color(contextBreakdown7Argb)
    val usageHeatmap0: Color get() = Color(usageHeatmap0Argb)
    val usageHeatmap1: Color get() = Color(usageHeatmap1Argb)
    val usageHeatmap2: Color get() = Color(usageHeatmap2Argb)
    val usageHeatmap3: Color get() = Color(usageHeatmap3Argb)
    val usageHeatmap4: Color get() = Color(usageHeatmap4Argb)
    val tab: Color get() = Color(tabArgb)
    val tabActive: Color get() = Color(tabActiveArgb)
    val tabBorder: Color get() = Color(tabBorderArgb)
    val menu: Color get() = Color(menuArgb)
    val menuHover: Color get() = Color(menuHoverArgb)
    val primary: Color get() = Color(primaryArgb)
    val primaryForeground: Color get() = Color(primaryForegroundArgb)
    val secondary: Color get() = Color(secondaryArgb)
    val interactionAskSurface: Color get() = Color(interactionAskSurfaceArgb)
    val interactionAskForeground: Color get() = Color(interactionAskForegroundArgb)
    val interactionAskFill: Color get() = Color(interactionAskFillArgb)
    val interactionConfirmationSurface: Color get() = Color(interactionConfirmationSurfaceArgb)
    val interactionConfirmationForeground: Color get() = Color(interactionConfirmationForegroundArgb)
    val foreground: Color get() = Color(foregroundArgb)
    val foregroundSubtle: Color get() = Color(foregroundSubtleArgb)
    val foregroundSubtlest: Color get() = Color(foregroundSubtlestArgb)
    val foregroundInverse: Color get() = Color(foregroundInverseArgb)
    val success: Color get() = Color(successArgb)
    val successForeground: Color get() = Color(successForegroundArgb)
    val idleTask: Color get() = Color(idleTaskArgb)
    val idleTaskSurface: Color get() = Color(idleTaskSurfaceArgb)
    val destructive: Color get() = Color(destructiveArgb)
    val destructiveForeground: Color get() = Color(destructiveForegroundArgb)
    val warning: Color get() = Color(warningArgb)
    val feedbackPrivacyHint: Color get() = Color(feedbackPrivacyHintArgb)
    val warningForeground: Color get() = Color(warningForegroundArgb)
    val diffAdded: Color get() = Color(diffAddedArgb)
    val diffAddedForeground: Color get() = Color(diffAddedForegroundArgb)
    val diffRemoved: Color get() = Color(diffRemovedArgb)
    val diffRemovedForeground: Color get() = Color(diffRemovedForegroundArgb)
    val gitNone: Color get() = Color(gitNoneArgb)
    val gitModified: Color get() = Color(gitModifiedArgb)
    val gitAdded: Color get() = Color(gitAddedArgb)
    val gitDeleted: Color get() = Color(gitDeletedArgb)
    val gitRenamed: Color get() = Color(gitRenamedArgb)
    val gitUntracked: Color get() = Color(gitUntrackedArgb)
    val gitIgnored: Color get() = Color(gitIgnoredArgb)
    val gitDescendant: Color get() = Color(gitDescendantArgb)
    val toast: Color get() = Color(toastArgb)
    val tooltip: Color get() = Color(tooltipArgb)
    val tooltipForeground: Color get() = Color(tooltipForegroundArgb)
    val tooltipTag: Color get() = Color(tooltipTagArgb)
    val tooltipTagForeground: Color get() = Color(tooltipTagForegroundArgb)
    val tag: Color get() = Color(tagArgb)
    val fileNode: Color get() = Color(fileNodeArgb)
    val fileNodeHover: Color get() = Color(fileNodeHoverArgb)
    val fileNodeForeground: Color get() = Color(fileNodeForegroundArgb)
    val skillNode: Color get() = Color(skillNodeArgb)
    val skillNodeHover: Color get() = Color(skillNodeHoverArgb)
    val skillNodeForeground: Color get() = Color(skillNodeForegroundArgb)
    val commandNode: Color get() = Color(commandNodeArgb)
    val commandNodeHover: Color get() = Color(commandNodeHoverArgb)
    val commandNodeForeground: Color get() = Color(commandNodeForegroundArgb)
    val subagentNode: Color get() = Color(subagentNodeArgb)
    val subagentNodeHover: Color get() = Color(subagentNodeHoverArgb)
    val subagentNodeForeground: Color get() = Color(subagentNodeForegroundArgb)
    val sessionNode: Color get() = Color(sessionNodeArgb)
    val sessionNodeHover: Color get() = Color(sessionNodeHoverArgb)
    val sessionNodeForeground: Color get() = Color(sessionNodeForegroundArgb)
    val pluginNode: Color get() = Color(pluginNodeArgb)
    val pluginNodeHover: Color get() = Color(pluginNodeHoverArgb)
    val pluginNodeForeground: Color get() = Color(pluginNodeForegroundArgb)
    val pluginPaidPlanBadge: Color get() = Color(pluginPaidPlanBadgeArgb)
    val pluginPaidPlanBadgeForeground: Color get() = Color(pluginPaidPlanBadgeForegroundArgb)

    companion object {

        /** 上游 `.theme-zai-light` 的 142 个槽，取值即浏览器求值结果。 */
        val ZaiLight = ZCodeSlots(
            animatedGradientTextStrongArgb = 0xFF0D0D0D.toInt(),
            animatedGradientTextSoftArgb = 0x380E0E0E.toInt(),
            backgroundArgb = 0xFFF8F8F8.toInt(),
            backgroundWinAltArgb = 0xFFECECEE.toInt(),
            backgroundAltArgb = 0xB3F8F8F8.toInt(),
            brandArgb = 0xFF000000.toInt(),
            // 上游 --color-icon-blue: var(--color-terminal-bright-blue)
            iconBlueArgb = 0xFF0066DD.toInt(),
            trajectoryUserArgb = 0xFF2563EB.toInt(),
            trajectoryAssistantArgb = 0xFF0F766E.toInt(),
            trajectoryReasoningArgb = 0xFF7C3AED.toInt(),
            trajectoryToolCallArgb = 0xFFD97706.toInt(),
            trajectoryToolResultArgb = 0xFF0284C7.toInt(),
            workflowRuleArgb = 0x0E121212.toInt(),
            workflowTraceArgb = 0x4D0D0D0D.toInt(),
            workflowTraceStrongArgb = 0x8C0D0D0D.toInt(),
            accentArgb = 0xFFEBF4FF.toInt(),
            findHighlightArgb = 0xFFFFF4EB.toInt(),
            findHighlightActiveArgb = 0xFFFFB26B.toInt(),
            borderArgb = 0x1A0A0A0A.toInt(),
            borderHoverArgb = 0x260D0D0D.toInt(),
            hoverArgb = 0x0D141414.toInt(),
            selectedArgb = 0x0D141414.toInt(),
            headerArgb = 0xFFFFFFFF.toInt(),
            panelArgb = 0xFFFFFFFF.toInt(),
            sidebarArgb = 0xFFF0F0F0.toInt(),
            surfaceArgb = 0x08000000.toInt(),
            surfaceHoverArgb = 0x0D141414.toInt(),
            // 上游 --color-markdown-inline-code: var(--color-tag)
            markdownInlineCodeArgb = 0xFFE6E6E6.toInt(),
            cardArgb = 0xFFFFFFFF.toInt(),
            cardSelectedArgb = 0xFFFFFFFF.toInt(),
            cardBorderArgb = 0x1A0A0A0A.toInt(),
            popoverArgb = 0xFFFFFFFF.toInt(),
            popoverForegroundArgb = 0xFF262626.toInt(),
            popoverHeaderArgb = 0xFFF8F8F8.toInt(),
            popoverBorderArgb = 0x1A0A0A0A.toInt(),
            inputArgb = 0xFFFFFFFF.toInt(),
            inputFocusedArgb = 0xFFFFFFFF.toInt(),
            inputBorderArgb = 0x1A0A0A0A.toInt(),
            inputBorderHoverArgb = 0x260D0D0D.toInt(),
            inputBorderFocusedArgb = 0x260D0D0D.toInt(),
            terminalBgArgb = 0xFFF8F8F8.toInt(),
            terminalFgArgb = 0xFF262626.toInt(),
            terminalCursorArgb = 0xFF0D0D0D.toInt(),
            terminalCursorAccentArgb = 0xFFF8F8F8.toInt(),
            terminalSelectionArgb = 0x380980FF.toInt(),
            terminalSelectionInactiveArgb = 0x1A0A0A0A.toInt(),
            terminalBlackArgb = 0xFF5C5C5C.toInt(),
            terminalRedArgb = 0xFFE03131.toInt(),
            terminalGreenArgb = 0xFF1E8A3E.toInt(),
            terminalYellowArgb = 0xFFE07B00.toInt(),
            terminalBlueArgb = 0xFF0B7FFF.toInt(),
            terminalMagentaArgb = 0xFF9E77ED.toInt(),
            terminalCyanArgb = 0xFF0AA7A7.toInt(),
            terminalWhiteArgb = 0xFFADADAD.toInt(),
            terminalBrightBlackArgb = 0xFF888888.toInt(),
            terminalBrightRedArgb = 0xFFE03131.toInt(),
            terminalBrightGreenArgb = 0xFF1E8A3E.toInt(),
            terminalBrightYellowArgb = 0xFFE07B00.toInt(),
            terminalBrightBlueArgb = 0xFF0066DD.toInt(),
            terminalBrightMagentaArgb = 0xFF9E77ED.toInt(),
            terminalBrightCyanArgb = 0xFF0AA7A7.toInt(),
            terminalBrightWhiteArgb = 0xFF0D0D0D.toInt(),
            usageChart1Argb = 0xFF0B7FFF.toInt(),
            usageChart2Argb = 0xFF1E8A3E.toInt(),
            usageChart3Argb = 0xFF9E77ED.toInt(),
            usageChart4Argb = 0xFFE03131.toInt(),
            usageChart5Argb = 0xFFE07B00.toInt(),
            usageChart6Argb = 0xFF0AA7A7.toInt(),
            contextBreakdown1Argb = 0xFF0B7FFF.toInt(),
            contextBreakdown2Argb = 0xFF338FFF.toInt(),
            contextBreakdown3Argb = 0xFF5CA7FF.toInt(),
            contextBreakdown4Argb = 0xFF85BBFF.toInt(),
            contextBreakdown5Argb = 0xFFACD0FF.toInt(),
            contextBreakdown6Argb = 0xFFC8DDFF.toInt(),
            contextBreakdown7Argb = 0xFFE0ECFF.toInt(),
            usageHeatmap0Argb = 0x08000000.toInt(),
            usageHeatmap1Argb = 0x341471DD.toInt(),
            usageHeatmap2Argb = 0x611079EF.toInt(),
            usageHeatmap3Argb = 0x970E7BF8.toInt(),
            usageHeatmap4Argb = 0xD30166DB.toInt(),
            tabArgb = 0xFFF0F0F0.toInt(),
            tabActiveArgb = 0xFFFFFFFF.toInt(),
            tabBorderArgb = 0x1A0A0A0A.toInt(),
            menuArgb = 0xFFFFFFFF.toInt(),
            menuHoverArgb = 0xFFF0F0F0.toInt(),
            primaryArgb = 0xFF000000.toInt(),
            primaryForegroundArgb = 0xFFFFFFFF.toInt(),
            secondaryArgb = 0xFFE6E6E6.toInt(),
            interactionAskSurfaceArgb = 0xFFEBF4FF.toInt(),
            interactionAskForegroundArgb = 0xFF0066DD.toInt(),
            interactionAskFillArgb = 0x3346BE73.toInt(),
            interactionConfirmationSurfaceArgb = 0xFFEAF7EE.toInt(),
            interactionConfirmationForegroundArgb = 0xFF166B32.toInt(),
            foregroundArgb = 0xFF262626.toInt(),
            foregroundSubtleArgb = 0x99262626.toInt(),
            foregroundSubtlestArgb = 0x66252525.toInt(),
            foregroundInverseArgb = 0xFFFFFFFF.toInt(),
            successArgb = 0xFF1E8A3E.toInt(),
            successForegroundArgb = 0xFFFFFFFF.toInt(),
            idleTaskArgb = 0xFF9E77ED.toInt(),
            idleTaskSurfaceArgb = 0xFFF5F3FF.toInt(),
            destructiveArgb = 0xFFE03131.toInt(),
            destructiveForegroundArgb = 0xFFFFFFFF.toInt(),
            warningArgb = 0xFFE07B00.toInt(),
            feedbackPrivacyHintArgb = 0xFFD08700.toInt(),
            warningForegroundArgb = 0xFFFFFFFF.toInt(),
            diffAddedArgb = 0xFF1E8A3E.toInt(),
            diffAddedForegroundArgb = 0xFFFFFFFF.toInt(),
            diffRemovedArgb = 0xFFE03131.toInt(),
            diffRemovedForegroundArgb = 0xFFFFFFFF.toInt(),
            gitNoneArgb = 0xFF262626.toInt(),
            gitModifiedArgb = 0xFFE07B00.toInt(),
            gitAddedArgb = 0xFF1E8A3E.toInt(),
            gitDeletedArgb = 0xFFE03131.toInt(),
            gitRenamedArgb = 0xFF0B7FFF.toInt(),
            gitUntrackedArgb = 0xFF1E8A3E.toInt(),
            gitIgnoredArgb = 0x66252525.toInt(),
            gitDescendantArgb = 0xFFE07B00.toInt(),
            toastArgb = 0xFFFFFFFF.toInt(),
            tooltipArgb = 0xFFF0F0F0.toInt(),
            tooltipForegroundArgb = 0xFF0D0D0D.toInt(),
            tooltipTagArgb = 0xFFE6E6E6.toInt(),
            tooltipTagForegroundArgb = 0xFF5C5C5C.toInt(),
            tagArgb = 0xFFE6E6E6.toInt(),
            fileNodeArgb = 0x1A1D6CBA.toInt(),
            fileNodeHoverArgb = 0x291970BB.toInt(),
            fileNodeForegroundArgb = 0xFF1A70B8.toInt(),
            skillNodeArgb = 0x1A764EB1.toInt(),
            skillNodeHoverArgb = 0x297651AE.toInt(),
            skillNodeForegroundArgb = 0xFF7453B0.toInt(),
            commandNodeArgb = 0x1A58626C.toInt(),
            commandNodeHoverArgb = 0x29576470.toInt(),
            commandNodeForegroundArgb = 0xFF566270.toInt(),
            subagentNodeArgb = 0x1A6C7631.toInt(),
            subagentNodeHoverArgb = 0x29707C32.toInt(),
            subagentNodeForegroundArgb = 0xFF6F7A2F.toInt(),
            sessionNodeArgb = 0x1A148076.toInt(),
            sessionNodeHoverArgb = 0x2913837C.toInt(),
            sessionNodeForegroundArgb = 0xFF14807A.toInt(),
            pluginNodeArgb = 0x1ABA761D.toInt(),
            pluginNodeHoverArgb = 0x29BB7C19.toInt(),
            pluginNodeForegroundArgb = 0xFFB87A1A.toInt(),
            pluginPaidPlanBadgeArgb = 0xFFFFE3C8.toInt(),
            pluginPaidPlanBadgeForegroundArgb = 0xFF4B280F.toInt(),
        )

        /** 上游 `.theme-zai-dark` 的 142 个槽，取值即浏览器求值结果。 */
        val ZaiDark = ZCodeSlots(
            animatedGradientTextStrongArgb = 0xFFFFFFFF.toInt(),
            animatedGradientTextSoftArgb = 0x38FFFFFF.toInt(),
            backgroundArgb = 0xFF161616.toInt(),
            backgroundWinAltArgb = 0xFF2B2B2B.toInt(),
            backgroundAltArgb = 0x992B2B2B.toInt(),
            brandArgb = 0xFFFFFFFF.toInt(),
            // 上游 --color-icon-blue: var(--color-terminal-bright-blue)
            iconBlueArgb = 0xFF80BEFF.toInt(),
            trajectoryUserArgb = 0xFF60A5FA.toInt(),
            trajectoryAssistantArgb = 0xFF2DD4BF.toInt(),
            trajectoryReasoningArgb = 0xFFA78BFA.toInt(),
            trajectoryToolCallArgb = 0xFFF59E0B.toInt(),
            trajectoryToolResultArgb = 0xFF38BDF8.toInt(),
            workflowRuleArgb = 0x11FFFFFF.toInt(),
            workflowTraceArgb = 0x59FFFFFF.toInt(),
            workflowTraceStrongArgb = 0x99FFFFFF.toInt(),
            accentArgb = 0xFF001D3D.toInt(),
            findHighlightArgb = 0xFF542500.toInt(),
            findHighlightActiveArgb = 0xFFFF8A30.toInt(),
            borderArgb = 0x1AFFFFFF.toInt(),
            borderHoverArgb = 0x26FFFFFF.toInt(),
            hoverArgb = 0x0DFFFFFF.toInt(),
            selectedArgb = 0x1AFFFFFF.toInt(),
            headerArgb = 0xFF202020.toInt(),
            panelArgb = 0xFF202020.toInt(),
            sidebarArgb = 0xFF161616.toInt(),
            surfaceArgb = 0x0DFFFFFF.toInt(),
            surfaceHoverArgb = 0x1AFFFFFF.toInt(),
            // 上游 --color-markdown-inline-code: var(--color-tag)
            markdownInlineCodeArgb = 0xFF363636.toInt(),
            cardArgb = 0xFF2B2B2B.toInt(),
            cardSelectedArgb = 0xFF2B2B2B.toInt(),
            cardBorderArgb = 0x1AFFFFFF.toInt(),
            popoverArgb = 0xFF2B2B2B.toInt(),
            popoverForegroundArgb = 0xFFD4D4D4.toInt(),
            popoverHeaderArgb = 0xFF202020.toInt(),
            popoverBorderArgb = 0x1AFFFFFF.toInt(),
            inputArgb = 0xFF2B2B2B.toInt(),
            inputFocusedArgb = 0xFF2B2B2B.toInt(),
            inputBorderArgb = 0x1AFFFFFF.toInt(),
            inputBorderHoverArgb = 0x26FFFFFF.toInt(),
            inputBorderFocusedArgb = 0x26FFFFFF.toInt(),
            terminalBgArgb = 0xFF161616.toInt(),
            terminalFgArgb = 0xFFD4D4D4.toInt(),
            terminalCursorArgb = 0xFFF8F8F8.toInt(),
            terminalCursorAccentArgb = 0xFF161616.toInt(),
            terminalSelectionArgb = 0x47419AFF.toInt(),
            terminalSelectionInactiveArgb = 0x1AFFFFFF.toInt(),
            terminalBlackArgb = 0xFF363636.toInt(),
            terminalRedArgb = 0xFFFF5C5C.toInt(),
            terminalGreenArgb = 0xFF46BF72.toInt(),
            terminalYellowArgb = 0xFFFF8A30.toInt(),
            terminalBlueArgb = 0xFF4099FF.toInt(),
            terminalMagentaArgb = 0xFF7B5CE5.toInt(),
            terminalCyanArgb = 0xFF42C8C8.toInt(),
            terminalWhiteArgb = 0xFFADADAD.toInt(),
            terminalBrightBlackArgb = 0xFF747474.toInt(),
            terminalBrightRedArgb = 0xFFFF9999.toInt(),
            terminalBrightGreenArgb = 0xFF87D9A4.toInt(),
            terminalBrightYellowArgb = 0xFFFFB26B.toInt(),
            terminalBrightBlueArgb = 0xFF80BEFF.toInt(),
            terminalBrightMagentaArgb = 0xFFA888F2.toInt(),
            terminalBrightCyanArgb = 0xFF8EE5E5.toInt(),
            terminalBrightWhiteArgb = 0xFFF8F8F8.toInt(),
            usageChart1Argb = 0xFF4099FF.toInt(),
            usageChart2Argb = 0xFF46BF72.toInt(),
            usageChart3Argb = 0xFF7B5CE5.toInt(),
            usageChart4Argb = 0xFFFF5C5C.toInt(),
            usageChart5Argb = 0xFFFF8A30.toInt(),
            usageChart6Argb = 0xFF42C8C8.toInt(),
            contextBreakdown1Argb = 0xFF4099FF.toInt(),
            contextBreakdown2Argb = 0xFF66ADFF.toInt(),
            contextBreakdown3Argb = 0xFF80BEFF.toInt(),
            contextBreakdown4Argb = 0xFF9DCEFF.toInt(),
            contextBreakdown5Argb = 0xFFB9DDFF.toInt(),
            contextBreakdown6Argb = 0xFFD0E8FF.toInt(),
            contextBreakdown7Argb = 0xFFE4F1FF.toInt(),
            usageHeatmap0Argb = 0x0DFFFFFF.toInt(),
            usageHeatmap1Argb = 0x475DA9FF.toInt(),
            usageHeatmap2Argb = 0x7350A0FF.toInt(),
            usageHeatmap3Argb = 0xA3489CFF.toInt(),
            usageHeatmap4Argb = 0xCA82BFFF.toInt(),
            tabArgb = 0xFF202020.toInt(),
            tabActiveArgb = 0xFF161616.toInt(),
            tabBorderArgb = 0x1AFFFFFF.toInt(),
            menuArgb = 0xFF2B2B2B.toInt(),
            menuHoverArgb = 0xFF363636.toInt(),
            primaryArgb = 0xFFFFFFFF.toInt(),
            primaryForegroundArgb = 0xFF000000.toInt(),
            secondaryArgb = 0xFF363636.toInt(),
            interactionAskSurfaceArgb = 0xFF001D3D.toInt(),
            interactionAskForegroundArgb = 0xFF80BEFF.toInt(),
            interactionAskFillArgb = 0x3D47C071.toInt(),
            interactionConfirmationSurfaceArgb = 0x2944C170.toInt(),
            interactionConfirmationForegroundArgb = 0xFF87D9A4.toInt(),
            foregroundArgb = 0xFFD4D4D4.toInt(),
            foregroundSubtleArgb = 0x99D4D4D4.toInt(),
            foregroundSubtlestArgb = 0x4DD4D4D4.toInt(),
            foregroundInverseArgb = 0xFF000000.toInt(),
            successArgb = 0xFF46BF72.toInt(),
            successForegroundArgb = 0xFF000000.toInt(),
            idleTaskArgb = 0xFF7B5CE5.toInt(),
            idleTaskSurfaceArgb = 0xFF160D38.toInt(),
            destructiveArgb = 0xFFFF5C5C.toInt(),
            destructiveForegroundArgb = 0xFFFFFFFF.toInt(),
            warningArgb = 0xFFFF8A30.toInt(),
            feedbackPrivacyHintArgb = 0xFFFFDF20.toInt(),
            warningForegroundArgb = 0xFF000000.toInt(),
            diffAddedArgb = 0xFF46BF72.toInt(),
            diffAddedForegroundArgb = 0xFF000000.toInt(),
            diffRemovedArgb = 0xFFFF5C5C.toInt(),
            diffRemovedForegroundArgb = 0xFF000000.toInt(),
            gitNoneArgb = 0xFFD4D4D4.toInt(),
            gitModifiedArgb = 0xFFFF8A30.toInt(),
            gitAddedArgb = 0xFF46BF72.toInt(),
            gitDeletedArgb = 0xFFFF5C5C.toInt(),
            gitRenamedArgb = 0xFF4099FF.toInt(),
            gitUntrackedArgb = 0xFF46BF72.toInt(),
            gitIgnoredArgb = 0x4DD4D4D4.toInt(),
            gitDescendantArgb = 0xFFFF8A30.toInt(),
            toastArgb = 0xFF2B2B2B.toInt(),
            tooltipArgb = 0xFF2B2B2B.toInt(),
            tooltipForegroundArgb = 0xFFF8F8F8.toInt(),
            tooltipTagArgb = 0xFF363636.toInt(),
            tooltipTagForegroundArgb = 0xFFADADAD.toInt(),
            tagArgb = 0xFF363636.toInt(),
            fileNodeArgb = 0x2970AEE0.toInt(),
            fileNodeHoverArgb = 0x3872ADDF.toInt(),
            fileNodeForegroundArgb = 0xFF8FC5EF.toInt(),
            skillNodeArgb = 0x29A889DA.toInt(),
            skillNodeHoverArgb = 0x38A489DB.toInt(),
            skillNodeForegroundArgb = 0xFFBDA5E6.toInt(),
            commandNodeArgb = 0x249CA3B1.toInt(),
            commandNodeHoverArgb = 0x339BA5B4.toInt(),
            commandNodeForegroundArgb = 0xFFB5C0CC.toInt(),
            subagentNodeArgb = 0x29B4BB76.toInt(),
            subagentNodeHoverArgb = 0x38B6BF76.toInt(),
            subagentNodeForegroundArgb = 0xFFC8CD90.toInt(),
            sessionNodeArgb = 0x296AC7BB.toInt(),
            sessionNodeHoverArgb = 0x3869C4BB.toInt(),
            sessionNodeForegroundArgb = 0xFF93D8D2.toInt(),
            pluginNodeArgb = 0x29E0AE70.toInt(),
            pluginNodeHoverArgb = 0x38DFAD72.toInt(),
            pluginNodeForegroundArgb = 0xFFEFC58F.toInt(),
            pluginPaidPlanBadgeArgb = 0xFF5A341B.toInt(),
            pluginPaidPlanBadgeForegroundArgb = 0xFFFFD8B8.toInt(),
        )

        /**
         * 上游**默认主题**的浅色一套（`@theme`；它比 Zai 多 `--color-icon-blue` 与
         * `--color-markdown-inline-code` 两个槽，所以并集是 144）。
         * 取值同样是浏览器求值 + 读像素的结果 —— 这一套整体建立在 tailwind 色板上
         * （`--color-neutral-*`、`--color-sky-*` … 共 56 支），探针页里把
         * `tailwindcss@4/theme.css` 的 `@theme default {…}` 转成 `:root {…}` 注入后求值。
         */
        val DefaultLight = ZCodeSlots(
            animatedGradientTextStrongArgb = 0xFF0A0A0A.toInt(),
            animatedGradientTextSoftArgb = 0x38090909.toInt(),
            backgroundArgb = 0xFFFAFAFA.toInt(),
            backgroundAltArgb = 0x99F5F5F5.toInt(),
            backgroundWinAltArgb = 0xFFE5E5E5.toInt(),
            brandArgb = 0xFF00BCFF.toInt(),
            iconBlueArgb = 0xFF00A6F4.toInt(),
            trajectoryUserArgb = 0xFF2563EB.toInt(),
            trajectoryAssistantArgb = 0xFF0F766E.toInt(),
            trajectoryReasoningArgb = 0xFF7C3AED.toInt(),
            trajectoryToolCallArgb = 0xFFD97706.toInt(),
            trajectoryToolResultArgb = 0xFF0284C7.toInt(),
            workflowRuleArgb = 0x0E121212.toInt(),
            workflowTraceArgb = 0x4D0A0A0A.toInt(),
            workflowTraceStrongArgb = 0x8C090909.toInt(),
            accentArgb = 0xFFF0F9FF.toInt(),
            findHighlightArgb = 0xFFFDE68A.toInt(),
            findHighlightActiveArgb = 0xFFFACC15.toInt(),
            borderArgb = 0x1A0A0A0A.toInt(),
            borderHoverArgb = 0x330A0A0A.toInt(),
            hoverArgb = 0xFFE5E5E5.toInt(),
            selectedArgb = 0x1A0A0A0A.toInt(),
            headerArgb = 0xFFF5F5F5.toInt(),
            panelArgb = 0xFFF5F5F5.toInt(),
            sidebarArgb = 0xFFF5F5F5.toInt(),
            surfaceArgb = 0x08000000.toInt(),
            surfaceHoverArgb = 0x0D141414.toInt(),
            markdownInlineCodeArgb = 0xFFE5E5E5.toInt(),
            cardArgb = 0xFFFFFFFF.toInt(),
            cardSelectedArgb = 0xFFE5E5E5.toInt(),
            cardBorderArgb = 0x1A0A0A0A.toInt(),
            popoverArgb = 0xFFFFFFFF.toInt(),
            popoverForegroundArgb = 0xFF404040.toInt(),
            popoverHeaderArgb = 0xFFF5F5F5.toInt(),
            popoverBorderArgb = 0x1A0A0A0A.toInt(),
            inputArgb = 0xFFFFFFFF.toInt(),
            inputFocusedArgb = 0xFFFAFAFA.toInt(),
            inputBorderArgb = 0x1A0A0A0A.toInt(),
            inputBorderHoverArgb = 0x330A0A0A.toInt(),
            inputBorderFocusedArgb = 0xFF00BCFF.toInt(),
            terminalBgArgb = 0xFFFAFAFA.toInt(),
            terminalFgArgb = 0xFF0A0A0A.toInt(),
            terminalCursorArgb = 0xFF0A0A0A.toInt(),
            terminalCursorAccentArgb = 0xFFFAFAFA.toInt(),
            terminalSelectionArgb = 0x5200A5F3.toInt(),
            terminalSelectionInactiveArgb = 0x3800A4F6.toInt(),
            terminalBlackArgb = 0xFF404040.toInt(),
            terminalRedArgb = 0xFFFB2C36.toInt(),
            terminalGreenArgb = 0xFF00C950.toInt(),
            terminalYellowArgb = 0xFFF0B100.toInt(),
            terminalBlueArgb = 0xFF00A6F4.toInt(),
            terminalMagentaArgb = 0xFFE12AFB.toInt(),
            terminalCyanArgb = 0xFF00B8DB.toInt(),
            terminalWhiteArgb = 0xFF737373.toInt(),
            terminalBrightBlackArgb = 0xFF737373.toInt(),
            terminalBrightRedArgb = 0xFFFB2C36.toInt(),
            terminalBrightGreenArgb = 0xFF00C950.toInt(),
            terminalBrightYellowArgb = 0xFFF0B100.toInt(),
            terminalBrightBlueArgb = 0xFF00A6F4.toInt(),
            terminalBrightMagentaArgb = 0xFFE12AFB.toInt(),
            terminalBrightCyanArgb = 0xFF00B8DB.toInt(),
            terminalBrightWhiteArgb = 0xFF0A0A0A.toInt(),
            usageChart1Argb = 0xFF0084D1.toInt(),
            usageChart2Argb = 0xFF009689.toInt(),
            usageChart3Argb = 0xFF7F22FE.toInt(),
            usageChart4Argb = 0xFFFF2056.toInt(),
            usageChart5Argb = 0xFF615FFF.toInt(),
            usageChart6Argb = 0xFF0092B8.toInt(),
            contextBreakdown1Argb = 0xFF0084D1.toInt(),
            contextBreakdown2Argb = 0xFF00A6F4.toInt(),
            contextBreakdown3Argb = 0xFF00BCFF.toInt(),
            contextBreakdown4Argb = 0xFF74D4FF.toInt(),
            contextBreakdown5Argb = 0xFFB8E6FE.toInt(),
            contextBreakdown6Argb = 0xFFDFF2FE.toInt(),
            contextBreakdown7Argb = 0xFFE8F6FF.toInt(),
            usageHeatmap0Argb = 0x08000000.toInt(),
            usageHeatmap1Argb = 0x340093D3.toInt(),
            usageHeatmap2Argb = 0x61009BE5.toInt(),
            usageHeatmap3Argb = 0x970082CC.toInt(),
            usageHeatmap4Argb = 0xD20068A8.toInt(),
            tabArgb = 0xFFF5F5F5.toInt(),
            tabActiveArgb = 0xFFFFFFFF.toInt(),
            tabBorderArgb = 0x1A0A0A0A.toInt(),
            menuArgb = 0xFFFFFFFF.toInt(),
            menuHoverArgb = 0xFFF5F5F5.toInt(),
            primaryArgb = 0xFF0A0A0A.toInt(),
            primaryForegroundArgb = 0xFFFAFAFA.toInt(),
            secondaryArgb = 0xFFD4D4D4.toInt(),
            interactionAskSurfaceArgb = 0xFFF0F9FF.toInt(),
            interactionAskForegroundArgb = 0xFF0069A8.toInt(),
            interactionAskFillArgb = 0x3300C850.toInt(),
            interactionConfirmationSurfaceArgb = 0xFFF0FDF4.toInt(),
            interactionConfirmationForegroundArgb = 0xFF008236.toInt(),
            foregroundArgb = 0xFF404040.toInt(),
            foregroundSubtleArgb = 0x993F3F3F.toInt(),
            foregroundSubtlestArgb = 0x66414141.toInt(),
            foregroundInverseArgb = 0xFFFFFFFF.toInt(),
            successArgb = 0xFF00A63E.toInt(),
            successForegroundArgb = 0xFFFFFFFF.toInt(),
            idleTaskArgb = 0xFF7F22FE.toInt(),
            idleTaskSurfaceArgb = 0xFFF5F3FF.toInt(),
            destructiveArgb = 0xFFE7000B.toInt(),
            destructiveForegroundArgb = 0xFFFFFFFF.toInt(),
            warningArgb = 0xFFD08700.toInt(),
            feedbackPrivacyHintArgb = 0xFFD08700.toInt(),
            warningForegroundArgb = 0xFFFFFFFF.toInt(),
            diffAddedArgb = 0xFF00A63E.toInt(),
            diffAddedForegroundArgb = 0xFFFFFFFF.toInt(),
            diffRemovedArgb = 0xFFE7000B.toInt(),
            diffRemovedForegroundArgb = 0xFFFFFFFF.toInt(),
            gitNoneArgb = 0xFF404040.toInt(),
            gitModifiedArgb = 0xFFE17100.toInt(),
            gitAddedArgb = 0xFF009689.toInt(),
            gitDeletedArgb = 0xFFE7000B.toInt(),
            gitRenamedArgb = 0xFF0084D1.toInt(),
            gitUntrackedArgb = 0xFF009689.toInt(),
            gitIgnoredArgb = 0x66414141.toInt(),
            gitDescendantArgb = 0xFFE17100.toInt(),
            toastArgb = 0xFFFFFFFF.toInt(),
            tooltipArgb = 0xFFF5F5F5.toInt(),
            tooltipForegroundArgb = 0xFF0A0A0A.toInt(),
            tooltipTagArgb = 0xFFE5E5E5.toInt(),
            tooltipTagForegroundArgb = 0x990A0A0A.toInt(),
            tagArgb = 0xFFE5E5E5.toInt(),
            fileNodeArgb = 0x240080CD.toInt(),
            fileNodeHoverArgb = 0x330082D2.toInt(),
            fileNodeForegroundArgb = 0xFF0084D1.toInt(),
            skillNodeArgb = 0x248023FF.toInt(),
            skillNodeHoverArgb = 0x337D23FF.toInt(),
            skillNodeForegroundArgb = 0xFF7F22FE.toInt(),
            commandNodeArgb = 0x2463718E.toInt(),
            commandNodeHoverArgb = 0x3364738C.toInt(),
            commandNodeForegroundArgb = 0xFF45556C.toInt(),
            subagentNodeArgb = 0x24717832.toInt(),
            subagentNodeHoverArgb = 0x336E782D.toInt(),
            subagentNodeForegroundArgb = 0xFF6F7A2F.toInt(),
            sessionNodeArgb = 0x24009587.toInt(),
            sessionNodeHoverArgb = 0x33009687.toInt(),
            sessionNodeForegroundArgb = 0xFF009689.toInt(),
            pluginNodeArgb = 0x24E37100.toInt(),
            pluginNodeHoverArgb = 0x33E17300.toInt(),
            pluginNodeForegroundArgb = 0xFFE17100.toInt(),
            pluginPaidPlanBadgeArgb = 0xFFFFE3C8.toInt(),
            pluginPaidPlanBadgeForegroundArgb = 0xFF4B280F.toInt(),
        )

        /** 上游**默认主题**的深色一套（`.dark`）。取值来源同上。 */
        val DefaultDark = ZCodeSlots(
            animatedGradientTextStrongArgb = 0xFFFFFFFF.toInt(),
            animatedGradientTextSoftArgb = 0x33FFFFFF.toInt(),
            backgroundArgb = 0xFF171717.toInt(),
            backgroundAltArgb = 0x99262626.toInt(),
            backgroundWinAltArgb = 0xFF262626.toInt(),
            brandArgb = 0xFF00A6F4.toInt(),
            iconBlueArgb = 0xFF00A6F4.toInt(),
            trajectoryUserArgb = 0xFF60A5FA.toInt(),
            trajectoryAssistantArgb = 0xFF2DD4BF.toInt(),
            trajectoryReasoningArgb = 0xFFA78BFA.toInt(),
            trajectoryToolCallArgb = 0xFFF59E0B.toInt(),
            trajectoryToolResultArgb = 0xFF38BDF8.toInt(),
            workflowRuleArgb = 0x11FFFFFF.toInt(),
            workflowTraceArgb = 0x59FFFFFF.toInt(),
            workflowTraceStrongArgb = 0x99FFFFFF.toInt(),
            accentArgb = 0x8006304A.toInt(),
            findHighlightArgb = 0xFF713F12.toInt(),
            findHighlightActiveArgb = 0xFFA16207.toInt(),
            borderArgb = 0x1AF5F5F5.toInt(),
            borderHoverArgb = 0x4DF8F8F8.toInt(),
            hoverArgb = 0x1AFFFFFF.toInt(),
            selectedArgb = 0x1AFFFFFF.toInt(),
            headerArgb = 0xFF171717.toInt(),
            panelArgb = 0xFF171717.toInt(),
            sidebarArgb = 0xFF0A0A0A.toInt(),
            surfaceArgb = 0x0DFFFFFF.toInt(),
            surfaceHoverArgb = 0x1AFFFFFF.toInt(),
            markdownInlineCodeArgb = 0xFFE5E5E5.toInt(),
            cardArgb = 0xFF262626.toInt(),
            cardSelectedArgb = 0xFF404040.toInt(),
            cardBorderArgb = 0x1AF5F5F5.toInt(),
            popoverArgb = 0xFF262626.toInt(),
            popoverForegroundArgb = 0xFFE5E5E5.toInt(),
            popoverHeaderArgb = 0xFF404040.toInt(),
            popoverBorderArgb = 0x1AF5F5F5.toInt(),
            inputArgb = 0xFF262626.toInt(),
            inputFocusedArgb = 0xFF0A0A0A.toInt(),
            inputBorderArgb = 0x1AF5F5F5.toInt(),
            inputBorderHoverArgb = 0x4DF8F8F8.toInt(),
            inputBorderFocusedArgb = 0xFF00A6F4.toInt(),
            terminalBgArgb = 0xFF0A0A0A.toInt(),
            terminalFgArgb = 0xFFFAFAFA.toInt(),
            terminalCursorArgb = 0xFFFAFAFA.toInt(),
            terminalCursorAccentArgb = 0xFF0A0A0A.toInt(),
            terminalSelectionArgb = 0x4200A6F3.toInt(),
            terminalSelectionInactiveArgb = 0x29F9F9F9.toInt(),
            terminalBlackArgb = 0xFF262626.toInt(),
            terminalRedArgb = 0xFFE7000B.toInt(),
            terminalGreenArgb = 0xFF00A63E.toInt(),
            terminalYellowArgb = 0xFFD08700.toInt(),
            terminalBlueArgb = 0xFF0084D1.toInt(),
            terminalMagentaArgb = 0xFFC800DE.toInt(),
            terminalCyanArgb = 0xFF0092B8.toInt(),
            terminalWhiteArgb = 0xFFE5E5E5.toInt(),
            terminalBrightBlackArgb = 0xFF737373.toInt(),
            terminalBrightRedArgb = 0xFFE7000B.toInt(),
            terminalBrightGreenArgb = 0xFF00A63E.toInt(),
            terminalBrightYellowArgb = 0xFFD08700.toInt(),
            terminalBrightBlueArgb = 0xFF0084D1.toInt(),
            terminalBrightMagentaArgb = 0xFFC800DE.toInt(),
            terminalBrightCyanArgb = 0xFF0092B8.toInt(),
            terminalBrightWhiteArgb = 0xFFFAFAFA.toInt(),
            usageChart1Argb = 0xFF00A6F4.toInt(),
            usageChart2Argb = 0xFF00D5BE.toInt(),
            usageChart3Argb = 0xFFA684FF.toInt(),
            usageChart4Argb = 0xFFFF637E.toInt(),
            usageChart5Argb = 0xFF7C86FF.toInt(),
            usageChart6Argb = 0xFF00D3F3.toInt(),
            contextBreakdown1Argb = 0xFF00BCFF.toInt(),
            contextBreakdown2Argb = 0xFF74D4FF.toInt(),
            contextBreakdown3Argb = 0xFFB8E6FE.toInt(),
            contextBreakdown4Argb = 0xFFC4EAFF.toInt(),
            contextBreakdown5Argb = 0xFFCCEDFF.toInt(),
            contextBreakdown6Argb = 0xFFD5F0FF.toInt(),
            contextBreakdown7Argb = 0xFFDDF3FF.toInt(),
            usageHeatmap0Argb = 0x0DFFFFFF.toInt(),
            usageHeatmap1Argb = 0x4744C6FF.toInt(),
            usageHeatmap2Argb = 0x7224C0FF.toInt(),
            usageHeatmap3Argb = 0xA300BDFF.toInt(),
            usageHeatmap4Argb = 0xCA75D4FF.toInt(),
            tabArgb = 0xFF262626.toInt(),
            tabActiveArgb = 0xFF0A0A0A.toInt(),
            tabBorderArgb = 0x1AF5F5F5.toInt(),
            menuArgb = 0xFF0A0A0A.toInt(),
            menuHoverArgb = 0xFF171717.toInt(),
            primaryArgb = 0xFFFAFAFA.toInt(),
            primaryForegroundArgb = 0xFF0A0A0A.toInt(),
            secondaryArgb = 0xFF404040.toInt(),
            interactionAskSurfaceArgb = 0x8006304A.toInt(),
            interactionAskForegroundArgb = 0xFF74D4FF.toInt(),
            interactionAskFillArgb = 0x3D04DE71.toInt(),
            interactionConfirmationSurfaceArgb = 0x73022F14.toInt(),
            interactionConfirmationForegroundArgb = 0xFF7BF1A8.toInt(),
            foregroundArgb = 0xFFE5E5E5.toInt(),
            foregroundSubtleArgb = 0x99E4E4E4.toInt(),
            foregroundSubtlestArgb = 0x4DE5E5E5.toInt(),
            foregroundInverseArgb = 0xFFFFFFFF.toInt(),
            successArgb = 0xFF00C950.toInt(),
            successForegroundArgb = 0xFFFFFFFF.toInt(),
            idleTaskArgb = 0xFFA684FF.toInt(),
            idleTaskSurfaceArgb = 0x80300E68.toInt(),
            destructiveArgb = 0xFFFB2C36.toInt(),
            destructiveForegroundArgb = 0xFFFFFFFF.toInt(),
            warningArgb = 0xFFF0B100.toInt(),
            feedbackPrivacyHintArgb = 0xFFFFDF20.toInt(),
            warningForegroundArgb = 0xFFFFFFFF.toInt(),
            diffAddedArgb = 0xFF00C950.toInt(),
            diffAddedForegroundArgb = 0xFFFFFFFF.toInt(),
            diffRemovedArgb = 0xFFFB2C36.toInt(),
            diffRemovedForegroundArgb = 0xFFFFFFFF.toInt(),
            gitNoneArgb = 0xFFE5E5E5.toInt(),
            gitModifiedArgb = 0xFFFFB900.toInt(),
            gitAddedArgb = 0xFF00D5BE.toInt(),
            gitDeletedArgb = 0xFFFF6467.toInt(),
            gitRenamedArgb = 0xFF00BCFF.toInt(),
            gitUntrackedArgb = 0xFF00D5BE.toInt(),
            gitIgnoredArgb = 0x4DE5E5E5.toInt(),
            gitDescendantArgb = 0xFFFFB900.toInt(),
            toastArgb = 0xFF171717.toInt(),
            tooltipArgb = 0xFF171717.toInt(),
            tooltipForegroundArgb = 0xFFFAFAFA.toInt(),
            tooltipTagArgb = 0xFF262626.toInt(),
            tooltipTagForegroundArgb = 0x99FAFAFA.toInt(),
            tagArgb = 0xFF404040.toInt(),
            fileNodeArgb = 0x2E00BCFF.toInt(),
            fileNodeHoverArgb = 0x3D00BCFF.toInt(),
            fileNodeForegroundArgb = 0xFF74D4FF.toInt(),
            skillNodeArgb = 0x2EA685FF.toInt(),
            skillNodeHoverArgb = 0x3DA786FF.toInt(),
            skillNodeForegroundArgb = 0xFFC4B4FF.toInt(),
            commandNodeArgb = 0x29C7D3E0.toInt(),
            commandNodeHoverArgb = 0x38C8D6E4.toInt(),
            commandNodeForegroundArgb = 0xFFCAD5E2.toInt(),
            subagentNodeArgb = 0x2EB7BC74.toInt(),
            subagentNodeHoverArgb = 0x3DB8BC75.toInt(),
            subagentNodeForegroundArgb = 0xFFB7BD75.toInt(),
            sessionNodeArgb = 0x2E00D3BC.toInt(),
            sessionNodeHoverArgb = 0x3D00D5BC.toInt(),
            sessionNodeForegroundArgb = 0xFF46EDD5.toInt(),
            pluginNodeArgb = 0x2EFFB700.toInt(),
            pluginNodeHoverArgb = 0x3DFFBC00.toInt(),
            pluginNodeForegroundArgb = 0xFFFFD230.toInt(),
            pluginPaidPlanBadgeArgb = 0xFF5A341B.toInt(),
            pluginPaidPlanBadgeForegroundArgb = 0xFFFFD8B8.toInt(),
        )
    }
}
