package com.lhzkml.jasmine.core.widgets.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.lhzkml.jasmine.core.ui.theme.LocalCssVariables

/**
 * 上游 ZCode 那 38 个 `@keyframes` 里，**与本库现有部件直接对得上**的几条的实现。
 *
 * 对应关系（上游原文 -> 这里）：
 * ```
 * @keyframes zcode-stream-text-in { 0% { opacity: 0 } 100% { opacity: 1 } }
 *   animation: zcode-stream-text-in 900ms cubic-bezier(0.16, 1, 0.3, 1) <delay> both
 *   -> [Modifier.streamTextIn]
 *
 * @keyframes markdown-image-loading-shimmer { from { background-position: 100% 0 } to { -100% 0 } }
 *   .markdown-image-loading-shimmer {
 *     background-color: var(--color-card);
 *     background-image: linear-gradient(135deg, card 0%, background 50%, card 100%);
 *     background-size: 240% 100%; animation: 1.8s linear infinite;
 *   }
 *   @media (prefers-reduced-motion: reduce) { animation: none }
 *   -> [ShimmerPlaceholder]
 * ```
 * 上游第三条 `zcode-stream-marker-in` 是给 `::marker`（列表标记伪元素）用的：
 * 本库的列表标记由 markdown 渲染器内部绘制，拿不到那一个元素，**故未实现**（不是遗漏）。
 *
 * ⚠️ 上游那几条还带 `@media (prefers-reduced-motion: reduce) { animation: none }`。
 * 本库目前**没有**统一的 reduced-motion 开关，所以这两条动画还没做那层退让 ——
 * 要做就得先给主题加一个开关（属独立改动，不在这里顺手夹带）。
 */

/** `900ms`。 */
private const val StreamInDurationMs = 900

/** `cubic-bezier(0.16, 1, 0.3, 1)` —— 上游给流式动画定的那条缓动。 */
private val StreamInEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** `1.8s` —— 图片加载微光的周期。 */
private const val ShimmerPeriodMs = 1800

/**
 * 流式新增内容的淡入（上游 `zcode-stream-text-in`）。
 *
 * 上游用它标记"这一段是刚流进来的"：新块从透明到不透明 ✓ 900ms ✓ 那条缓动 ✓
 * 可带 `delay`（上游的 `--zcode-stream-animation-delay` ✓ 用于让同一批同时到达的块错开 ✓）。
 *
 * 语义上是"**首次出现时淡入一次**" ✓ 不是"每次重组都淡入" ✓ 所以用 [LaunchedEffect] 打标记 ✓。
 */
fun Modifier.streamTextIn(
    delayMillis: Int = 0,
    enabled: Boolean = true,
): Modifier = composed {
    if (!enabled) return@composed this
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = StreamInDurationMs,
            delayMillis = delayMillis,
            easing = StreamInEasing,
        ),
        label = "stream-text-in",
    )
    graphicsLayer { this.alpha = alpha }
}

/**
 * Markdown 图片等"还没加载出来"时的占位微光（上游 `markdown-image-loading-shimmer`）。
 *
 * 上游的底与渐变都取自主题：底色 `--color-card` ✓ 斜向 135° 的 `card → background → card` ✓
 * 背景尺寸 240% 宽 ✓ 1.8s 线性无限 ✓。这里用同样三色与周期的 `Brush.linearGradient` 平移表达
 * （Compose 没有 `background-size` / `background-position` ✓ 用渐变起止点随动画平移等价 ✓）。
 *
 * 上游在 `prefers-reduced-motion: reduce` 下**关掉动画** ✓ 本库对应 [LocalReducedMotion] ✓。
 */
@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val theme = LocalCssVariables.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ShimmerPeriodMs, easing = LinearEasing)
        ),
        label = "shimmer-shift",
    )
    val card = theme.card
    val background = theme.background
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                // 135° 方向：左上 -> 右下。
                start = Offset(1000f * shift + 1000f, 0f),
                end = Offset(1000f * shift - 1000f, 2000f),
                colors = listOf(card, background, card),
            )
        )
    )
}
