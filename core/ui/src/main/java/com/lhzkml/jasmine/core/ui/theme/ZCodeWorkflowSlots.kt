package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

/**
 * ZCode 的**工作流图动效与配色参数**（上游 `packages/ui/src/styles.css` 尾部 `.wf-*` 那一段里定义的
 * 全局变量）。它们不是"颜色槽"，但同样是写死在样式表里的设计 token —— 既然要做到逐项对齐，就一并收进来。
 *
 * 上游原文（逐项照搬）：
 * ```
 * --wf-t-fast: 120ms;  --wf-t-base: 160ms;  --wf-t-enter: 200ms;  --wf-t-ink: 320ms;
 * --wf-ease: cubic-bezier(0.22, 0.61, 0.36, 1);
 * --wf-beat: 1.6s;
 * --wf-face-body: #54b9a6;  --wf-face-eye: #ffffff;
 * --wf-face-x: 0px;  --wf-face-y: 0px;
 * ```
 * 时长以 [Float]（毫秒 / 秒）原样存，缓动曲线直接用 Compose 的 [CubicBezierEasing] 表达同一组控制点。
 */
@Immutable
class ZCodeWorkflowSlots(
    /** `--wf-t-fast`：120ms。 */
    val tFastMs: Float,
    /** `--wf-t-base`：160ms。 */
    val tBaseMs: Float,
    /** `--wf-t-enter`：200ms。 */
    val tEnterMs: Float,
    /** `--wf-t-ink`：320ms。 */
    val tInkMs: Float,
    /** `--wf-beat`：心跳周期 1.6s。 */
    val beatSeconds: Float,
    /** `--wf-face-body`：工作流小人脸的主体色。 */
    val faceBody: Color,
    /** `--wf-face-eye`：眼睛色。 */
    val faceEye: Color,
    /** `--wf-face-x`：脸的横向偏移。 */
    val faceX: Float,
    /** `--wf-face-y`：脸的纵向偏移。 */
    val faceY: Float,
    /** `--wf-ease`：`cubic-bezier(0.22, 0.61, 0.36, 1)`。 */
    val ease: CubicBezierEasing,

    // ── 组件内部变量的**默认值**（上游写在各 `.wf-*` 规则里，形如 `var(--x, fallback)`）──
    // 它们由组件在运行时注入，样式表里给的是兜底值；这里照抄兜底值。

    /** `--wf-face-blink-time` 兜底 `230ms`。 */
    val faceBlinkTimeMs: Float,
    /** `--wf-face-blinks` 兜底 `2`（眨眼次数）。 */
    val faceBlinks: Int,
    /** `--wf-face-hops` 兜底 `2`（跳跃次数）。 */
    val faceHops: Int,
    /** `--wf-face-floats` 兜底 `1`（漂浮次数）。 */
    val faceFloats: Int,
    /** `--wf-face-shakes` 兜底 `3`（抖动次数）。 */
    val faceShakes: Int,
    /**
     * `--wf-avatar` 的兜底：上游写的是 `var(--color-border-hover)`，即**随主题变**的引用，
     * 不是固定色。所以这里用 [Color.Unspecified] 表示"未注入、回落到该套的 borderHover"，
     * 而不是硬编一个色值。
     */
    val avatar: Color,
)

/** 上游那一套（只有一套 —— 全局动画参数，不随主题变化）。 */
@Stable
val ZCodeWorkflow = ZCodeWorkflowSlots(
    tFastMs = 120f,
    tBaseMs = 160f,
    tEnterMs = 200f,
    tInkMs = 320f,
    beatSeconds = 1.6f,
    faceBody = Color(0xFF54B9A6),
    faceEye = Color(0xFFFFFFFF),
    faceX = 0f,
    faceY = 0f,
    ease = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f),
    faceBlinkTimeMs = 230f,
    faceBlinks = 2,
    faceHops = 2,
    faceFloats = 1,
    faceShakes = 3,
    avatar = Color.Unspecified,
)
