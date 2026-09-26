package com.lhzkml.jasmine.core.markdown.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.util.LruCache
import io.nano.tex.Graphics2D
import io.nano.tex.LaTeX
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * LaTeX 公式 → 位图。
 *
 * 引擎是 [MicroTeX](https://github.com/NanoMichael/MicroTeX)（MIT）—— 与 ima 用的是同一套：
 * 它 APK 里的 `libtex.so` + `io.nano.tex` 包就来自这个开源库。
 *
 * 引擎是**进程级单例**且初始化不便宜（解压 1.7 MB 字体到 filesDir + 载入 native），
 * 所以这里自己管生命周期，只初始化一次。
 */
internal object MicroTexRenderer {

    private const val TAG = "MicroTex"
    private val lock = Any()

    @Volatile
    private var initialized = false

    /** 只初始化一次；失败会抛异常，由调用方兜住。 */
    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            LaTeX.instance().init(context.applicationContext)
            initialized = true
        }
    }

    /**
     * 后台预热，尽早把字体解压 + native 载入做掉。
     *
     * 行内公式只能在组合期同步渲染，而首次 [ensureInitialized] 要解压 1.7 MB 字体 ——
     * 不预热的话，文档里第一个公式就会在主线程上做这件事。块级公式走的是后台线程，
     * 但行内公式可能出现在它前面（比如正文先写了 `$E=mc^2$`）。
     */
    fun warmUp(context: Context) {
        if (initialized) return
        scope.launch(Dispatchers.Default) {
            runCatching { ensureInitialized(context) }
                .onFailure { Log.w(TAG, "公式引擎预热失败", it) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 结果缓存。
     *
     * **行内公式必须在组合期同步渲染**（[androidx.compose.foundation.text.InlineTextContent]
     * 的占位尺寸在组合时就得定下来，异步拿不到），所以每次重组都会走到这里 ——
     * 没有缓存的话同一段文本会反复解析。
     *
     * 与图表位图不同，公式位图通常只有几十 KB，所以按**条数**限而不是字节。
     */
    private val cache = LruCache<String, Bitmap>(CACHE_ENTRIES)

    /**
     * 解析并光栅化成位图，失败返回 null。
     *
     * @param textSizeSp 公式字号（sp），内部按 density 放大光栅化，保证清晰
     * @param color      ARGB 前景色
     * @param density    屏幕密度
     */
    fun render(
        context: Context,
        latex: String,
        textSizeSp: Float,
        color: Int,
        density: Float,
    ): Bitmap? {
        val key = "$latex|$textSizeSp|$color|$density"
        cache.get(key)?.let { return it }
        // ⚠️ MicroTeX 的 Java/native 接口**不是线程安全的**，解析必须串行化。
        //
        // 我们有两处并发入口：块级公式在后台线程解析（`MathBlock` 用 Dispatchers.Default），
        // 行内公式在组合期主线程同步解析（InlineTextContent 的占位尺寸必须当场定下来）。
        // 两边同时进 native 会直接 **SIGBUS 崩掉进程**，实测栈：
        //     Fatal signal 7 (SIGBUS) ... tid (DefaultDispatcher)
        //     #05 io.nano.tex.LaTeX.nParse  #12 io.nano.tex.LaTeX.parse
        // 注意这种信号**catch(Throwable) 拦不住**，别指望上面的兜底。
        //
        // 与 `ensureInitialized` 共用同一把锁：初始化与解析同样不能重叠。
        val bitmap = synchronized(lock) {
            renderUncached(context, latex, textSizeSp, color, density)
        } ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun renderUncached(
        context: Context,
        latex: String,
        textSizeSp: Float,
        color: Int,
        density: Float,
    ): Bitmap? = try {
        ensureInitialized(context)
        val render = LaTeX.instance().parse(latex, textSizeSp * density, color)
        // 总高 = 主体高度 + 下降部分（getDepth 是 descent 的正值）。
        val width = max(1, render.width)
        val height = max(1, render.height + render.depth)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        render.draw(Graphics2D(Canvas(bitmap)), 0, 0)
        bitmap
    } catch (e: Throwable) {
        Log.w(TAG, "公式渲染失败: " + latex.take(80), e)
        null
    }

    private const val CACHE_ENTRIES = 256
}
