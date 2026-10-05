package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * ZCode 的 **@提及图标槽**（上游 `.prompt-mention::before` 用的三个变量）。
 *
 * 上游原文的兜底值是 `transparent` / `none` / `none` —— 也就是说**默认什么都不画**，
 * 真正的图标由引用方在运行时按"被提及的对象"注入（skill logo、文件类型图标等）。
 * 这里照抄这三个兜底值，语义保持一致：用 [Color.Unspecified] 表达"没有颜色"，
 * 用 `null` 表达 CSS 的 `none`（无图 / 无遮罩）。
 *
 * 上游 CSS 摘录：
 * ```
 * .prompt-mention::before {
 *   background: var(--mention-icon-color, transparent) var(--mention-image, none) center / contain no-repeat;
 *   mask: var(--mention-mask, none) center / contain no-repeat;
 * }
 * ```
 */
@Immutable
data class ZCodeMentionSlots(
    /** `--mention-icon-color` 兜底 `transparent`（即"没有颜色"）。 */
    val iconColor: Color,
    /** `--mention-image` 兜底 `none` —— 运行时注入的图片资源标识。 */
    val image: String?,
    /** `--mention-mask` 兜底 `none` —— 运行时注入的遮罩。 */
    val mask: String?,
)

/** 上游那三个兜底值。 */
val ZCodeMention = ZCodeMentionSlots(
    iconColor = Color.Unspecified,
    image = null,
    mask = null,
)
