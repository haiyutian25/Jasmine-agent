package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.runtime.Immutable

/**
 * ZCode 的**排版槽**（上游 `packages/ui/src/styles.css` 的 `:root` 与 `@theme` 里的非颜色变量）。
 *
 * 之前只搬了颜色槽，把这批漏了；它们同样是设计 token（字号梯度、字距、等宽字体栈、iOS 输入下限），
 * 上游全部以 `--ui-font-size: 14px` 为基准做加减，所以这里也照抄成派生式，改基准整数就整体缩放。
 *
 * 取值口径与颜色槽一致：**逐项照搬上游原文**，不做换算。
 * * `px` / `em` / `ms` 对数用 [Float] 原样存（不转 sp/dp —— 转了就不再是上游那个数）；
 * * `--wf-ease` 那种曲线放在 [ZCodeWorkflowSlots]（属动效参数）。
 */
@Immutable
class ZCodeTextSlots(
    /** `--ui-font-size`：全 UI 的字号基准，上游 `14px`。 */
    val uiFontSize: Float,

    /** `--text-ui-xl` = 基准 + 4px。 */
    val textUiXl: Float,
    /** `--text-ui-lg` = 基准 + 2px。 */
    val textUiLg: Float,
    /** `--text-ui-base` = 基准。 */
    val textUiBase: Float,
    /** `--text-ui-caption` = 基准 − 1px。 */
    val textUiCaption: Float,
    /** `--text-ui-sm` = 基准 − 2px。 */
    val textUiSm: Float,
    /** `--text-ui-xs` = 基准 − 4px。 */
    val textUiXs: Float,
    /** `--text-ui-2xs` = 基准 − 5px。上游注明：**仅限工作流图轴面**（刻度标签 / 通道码 / 单位后缀），不得用于内容文本。 */
    val textUi2xs: Float,

    /** `--tracking-wf-label`：大写等宽微标签（CAUSALITY GRAPH / TIMEBASE / KEY）在 9–10px 下需要加宽的字距，单位 em。 */
    val trackingWfLabel: Float,

    /** `--text-mobile-input-safe`：iOS Safari 会缩放小于 16px 的聚焦输入，该平台下限不能跟着界面字号降低。 */
    val textMobileInputSafe: Float,

    /**
     * `--font-mono`：上游保留各平台西文等宽字体，并在通用 monospace 之前显式插入各平台 CJK 无衬线字体
     * （Windows 的 Consolas 不含中文字形，否则中文会落到宋体）。
     */
    val fontMono: String,
)

/** 上游那一套（只有一套 —— 这些是全局排版，不随主题明暗或调色板变化）。 */
val ZCodeText = ZCodeTextSlots(
    uiFontSize = 14f,
    textUiXl = 18f, // 14 + 4
    textUiLg = 16f, // 14 + 2
    textUiBase = 14f,
    textUiCaption = 13f, // 14 - 1
    textUiSm = 12f, // 14 - 2
    textUiXs = 10f, // 14 - 4
    textUi2xs = 9f, // 14 - 5
    trackingWfLabel = 0.09f,
    textMobileInputSafe = 16f,
    fontMono =
        "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, \"Liberation Mono\", " +
            "\"Courier New\", \"Microsoft YaHei UI\", \"Microsoft YaHei\", \"PingFang SC\", " +
            "\"Noto Sans CJK SC\", monospace",
)
