package com.lhzkml.jasmine.core.markdown.ui

import android.os.Looper
import android.os.MessageQueue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 一个 mermaid 块的「图片」页 —— 显示的是一张 [Bitmap]，不是活的 WebView。
 *
 * 渲染交给 [MermaidRenderer]（全进程一个离屏 WebView + LRU 缓存），这里只做两件事：
 * 拿位图、按容器宽度铺开。形态与 ima 的 `MermaidCodeBlockComposable` 一致 ——
 * 它的「图片」也是普通图片（区别只是它的位图来自服务端，我们的来自本地离屏渲染）。
 *
 * **没有回退逻辑**：渲染不出来就停在加载态，由用户自己切回「代码」页。
 *
 * @param isDark 主题变化要重渲 —— mermaid 的内置主题是渲染期生效的
 */
@Composable
internal fun MermaidImage(
    source: String,
    isDark: Boolean,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 按屏幕密度渲染，字形才清晰（位图是光栅，放大就糊）。
    val density = LocalDensity.current.density

    val rendered by produceState<MermaidBitmap?>(initialValue = null, source, isDark, density) {
        // 等效 ima 的 requestIdleCallback(render, { timeout: 2000 })：等主线程空闲再渲染，
        // 避免图表渲染抢占滚动帧；超时兜底保证一定能渲染（不会因主线程一直忙而卡住）。
        awaitIdle()
        value = MermaidRenderer.render(context, source, isDark, density)
    }

    // 背景与圆角由外层 ToolbarBlock 提供，这里只留内边距。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        val r = rendered
        if (r != null) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // 显示宽度取 `min(固有宽度, 容器宽 × MAX_WIDTH_RATIO)`。
                //
                // 「固有宽度」用的是宿主页回报的 CSS px（不是位图像素宽度）——
                // 位图按 scale 光栅化过，尺寸随缩放变；而 1 CSS px = 1 dp 这个映射
                // 才让图里的字号与正文一致。
                //
                // 为什么要封顶：甘特图这类固有尺寸能到 4564×220（20:1），按固有尺寸铺开
                // 要横滑十屏。封在 2 倍容器宽，既保留可读性，又只需滑一两屏。
                val maxDisplay = maxWidth.value * MAX_WIDTH_RATIO
                val display = r.naturalWidth.toFloat().coerceAtMost(maxDisplay)
                val scrollable = display > maxWidth.value
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (scrollable) {
                                Modifier.horizontalScroll(rememberScrollState())
                            } else {
                                Modifier
                            }
                        ),
                    horizontalArrangement = if (scrollable) Arrangement.Start else Arrangement.Center,
                ) {
                    Image(
                        bitmap = r.bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.width(display.dp),
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(estimateHeight(source).dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = currentTheme.mutedForeground,
                )
            }
        }
    }
}

/** 显示宽度相对容器宽度的上限倍数。见 [MermaidImage] 里的说明。 */
private const val MAX_WIDTH_RATIO = 2f

/** 与 ima 的 `TCe` / `vCe` 一致：每行 22、基础 48。位图到位前用它占位，避免高度跳动。 */
private fun estimateHeight(source: String): Int = source.count { it == '\n' } * LINE_HEIGHT + BASE_HEIGHT

private const val LINE_HEIGHT = 22
private const val BASE_HEIGHT = 48

/**
 * 等效 `requestIdleCallback(cb, { timeout: IDLE_TIMEOUT_MS })`：主线程空闲时恢复，
 * 超时则无条件恢复 —— 与 ima 的 `fCe` 语义一致（空闲优先，超时兜底）。
 */
private suspend fun awaitIdle() {
    withTimeoutOrNull(IDLE_TIMEOUT_MS) {
        suspendCancellableCoroutine<Unit> { cont ->
            val queue = Looper.getMainLooper().queue
            val idle = MessageQueue.IdleHandler {
                if (cont.isActive) cont.resume(Unit)
                false // 只触发一次
            }
            queue.addIdleHandler(idle)
            cont.invokeOnCancellation { queue.removeIdleHandler(idle) }
        }
    }
}

private const val IDLE_TIMEOUT_MS = 2000L
