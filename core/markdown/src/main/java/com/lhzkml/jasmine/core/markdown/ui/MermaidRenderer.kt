package com.lhzkml.jasmine.core.markdown.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.LruCache
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume

/**
 * mermaid 离屏渲染器 —— **全进程只持有一个 WebView**，且它从不显示。
 *
 * ## 为什么不把 WebView 放进界面
 *
 * 图引擎（`src/main/assets/mermaid/`）是从 ima 的 `markdown-viewer-mobile` 取来的浏览器
 * ESM 包，只能在 WebView 里跑。但把 WebView 当作「显示控件」会带来两个问题：
 * 每个 mermaid 块一个 WebView（渲染进程内存开销随块数线性增长），以及流式过程中反复
 * 建销。ima 的聊天侧干脆把渲染放到服务端，本地只显示一张图片 —— 这里用「本地离屏渲染」
 * 复刻同样的产物形态：**交给界面的是一张 Bitmap**，WebView 只是生成它的工具。
 *
 * ## 像素怎么取
 *
 * 不用 `WebView.draw()` —— 未挂进窗口的 Chromium WebView 不保证产出帧，`draw()` 常拿到
 * 空白。改由宿主页内部完成 `SVG → <img> → <canvas> → toDataURL('image/png')`，
 * 把 PNG 以 data URL 回传，Kotlin 侧解码。整条链路不依赖 WebView 自身被绘制，
 * 所以这个 WebView **永远不需要挂进任何窗口**。
 *
 * ## 并发与缓存
 *
 * mermaid 的渲染会改动页面里的 DOM，不可重入 —— 用 [mutex] 串行化，一次只渲一张。
 * 结果按 `主题|缩放|源码` 缓存在 [cache]（字节计量的 LRU），所以「代码 ↔ 图片」
 * 反复切换不会重复渲染 —— 对应 ima 的 `FencedCodeCache`。
 */
internal object MermaidRenderer {

    private val mutex = Mutex()
    private val main = Handler(Looper.getMainLooper())

    private var webView: WebView? = null
    private var pageReady = false

    /** 当前等待中的渲染请求。由 [mutex] 保证同一时刻至多一个。 */
    private var pending: ((Result<RawRender>) -> Unit)? = null

    /** 页面就绪前攒下的那一次调用。 */
    private var queued: (() -> Unit)? = null

    private val cache = object : LruCache<String, MermaidBitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: MermaidBitmap): Int = value.bitmap.byteCount
    }

    /**
     * 把 mermaid 源码渲染成位图（附带固有尺寸）；失败返回 null。
     *
     * @param scale 位图相对 SVG 固有尺寸的放大倍数，传屏幕密度可得到清晰的字形
     */
    suspend fun render(
        context: Context,
        source: String,
        isDark: Boolean,
        scale: Float,
    ): MermaidBitmap? {
        val key = "$isDark|$scale|$source"
        cache.get(key)?.let { return it }
        return mutex.withLock {
            cache.get(key)?.let { return@withLock it }
            val rendered = withContext(Dispatchers.Main) {
                renderOnMain(context.applicationContext, source, isDark, scale)
            }
            if (rendered != null) cache.put(key, rendered)
            rendered
        }
    }

    /** 内存吃紧时由宿主调用。 */
    fun trim() {
        cache.evictAll()
    }

    private suspend fun renderOnMain(
        appContext: Context,
        source: String,
        isDark: Boolean,
        scale: Float,
    ): MermaidBitmap? {
        val web = webView ?: createWebView(appContext).also { webView = it }
        val raw = awaitRender(web, source, isDark, scale) ?: return null
        val bitmap = decodeDataUrl(raw.dataUrl) ?: return null
        return MermaidBitmap(bitmap, raw.naturalWidth, raw.naturalHeight)
    }

    /**
     * 发指令并等桥回调。页面还没就绪时先把调用攒起来，等宿主页的 `onReady` 再发。
     */
    private suspend fun awaitRender(
        web: WebView,
        source: String,
        isDark: Boolean,
        scale: Float,
    ): RawRender? = suspendCancellableCoroutine { cont ->
        val fire = {
            Log.d(TAG, "派发渲染指令，源码 ${source.length} 字符，scale=$scale")
            web.evaluateJavascript(
                "window.setTheme($isDark);" +
                    "window.renderDiagram(${JSONObject.quote(source)}, $scale);",
                null,
            )
        }
        pending = { result -> if (cont.isActive) cont.resume(result.getOrNull()) }
        cont.invokeOnCancellation { pending = null }
        if (pageReady) {
            fire()
        } else {
            // 页面模块还没执行完，先攒着 —— 由宿主页的 onReady 回调触发。
            Log.d(TAG, "页面未就绪，指令入队")
            queued = fire
        }
    }

    private fun createWebView(appContext: Context): WebView {
        Log.d(TAG, "创建离屏 WebView")
        return WebView(appContext).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.configure()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = intercept(appContext, request)
            }
            addJavascriptInterface(
                Bridge(
                    readyCallback = {
                        main.post {
                            Log.d(TAG, "页面就绪（module 已执行）")
                            pageReady = true
                            queued?.invoke()
                            queued = null
                        }
                    },
                    resultCallback = { status, dataUrl, width, height, message ->
                        // 桥回调在 WebView 的线程上，协程续体只能在主线程恢复。
                        main.post {
                            Log.d(TAG, "渲染回调 status=$status dataUrl=${dataUrl.length}B " +
                                "固有尺寸=${width}x$height")
                            // 宿主页会把诊断信息塞在 message 里，逐行打出来（logcat 单行有长度上限）。
                            message.split('\n').forEach { Log.d(TAG, "  | $it") }
                            val deliver = pending ?: return@post
                            pending = null
                            deliver(
                                if (status == "rendered" && dataUrl.isNotEmpty()) {
                                    Result.success(RawRender(dataUrl, width, height))
                                } else {
                                    Result.failure(IllegalStateException(message.ifEmpty { status }))
                                }
                            )
                        }
                    },
                ),
                BRIDGE_NAME,
            )
            loadUrl(PAGE_URL)
        }
    }

    private fun decodeDataUrl(dataUrl: String): Bitmap? = try {
        val base64 = dataUrl.substringAfter("base64,", "")
        if (base64.isEmpty()) {
            Log.w(TAG, "data URL 里没有 base64 段")
            null
        } else {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size).also {
                Log.d(TAG, "PNG ${bytes.size}B → Bitmap ${it?.width}x${it?.height}")
            }
        }
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "base64 解码失败", e)
        null
    }
}

/** 缓存上限取可用堆的 1/8，与 Android 上 `LruCache` 的常规取法一致。 */
private fun cacheBytes(): Int {
    val eighth = (Runtime.getRuntime().maxMemory() / 8).toInt()
    return eighth.coerceIn(4 shl 20, 48 shl 20)
}

private const val TAG = "MermaidRenderer"
private const val BRIDGE_NAME = "__mermaidBridge"
private const val ASSET_DIR = "mermaid"
private const val PAGE_FILE = "mermaid.html"

/** 假域名：请求由 [intercept] 从 assets 应答，不出网。 */
private const val VIRTUAL_HOST = "mermaid.local"

/** Vite 依赖表里用的原始发布路径前缀，需要剥掉。 */
private const val CDN_PREFIX = "/static.ima.qq.com/ima/assets/markdown-viewer-mobile/assets/"
private val PAGE_URL: String get() = "https://$VIRTUAL_HOST/$PAGE_FILE"

/**
 * 把 `mermaid.local/<file>` 映射到 `assets/mermaid/<file>`。
 *
 * ⚠️ 必须带 `Access-Control-Allow-Origin: *`。`shouldInterceptRequest` 交出的响应在
 *    WebView 里不带来源信息，模块脚本（`import()`）的加载会被当成跨源请求拦掉，
 *    报 `TypeError: Failed to fetch dynamically imported module`。ima 的
 *    `h00/b.java` 给每个拦截响应都加了这一行 —— 同样的坑。
 */
private fun intercept(context: Context, request: WebResourceRequest): WebResourceResponse? {
    val url = request.url
    if (url.host != VIRTUAL_HOST) return null
    val raw = url.path.orEmpty()
    // Vite 的 __vite__mapDeps 依赖表里写的是根相对路径（那批资产原本发在 CDN 上），
    // 到了我们的假域名下会变成 https://mermaid.local/static.ima.qq.com/...；
    // 而资产在 assets 里是平铺的，所以要把这段 CDN 前缀剥掉。
    val name = if (raw.startsWith(CDN_PREFIX)) raw.removePrefix(CDN_PREFIX) else raw.trimStart('/')
    // 不给出 assets 目录的机会。
    if (name.isEmpty() || name.contains("..")) {
        Log.w(TAG, "拦截拒绝（非法路径）: $name")
        return notFound()
    }
    return try {
        val stream = context.assets.open("$ASSET_DIR/$name")
        Log.d(TAG, "拦截命中 $name → ${mimeOf(name)}")
        WebResourceResponse(mimeOf(name), "utf-8", stream).apply {
            setResponseHeaders(mapOf("Access-Control-Allow-Origin" to "*"))
        }
    } catch (e: IOException) {
        Log.w(TAG, "拦截失败 $name（assets 里没有？）", e)
        notFound()
    }
}

private fun notFound() =
    WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)

@android.annotation.SuppressLint("SetJavaScriptEnabled")
private fun WebSettings.configure() {
    javaScriptEnabled = true
    // 只从 assets 取东西，不需要文件/内容访问，也不存 DOM 存储。
    allowFileAccess = false
    allowContentAccess = false
    domStorageEnabled = false
    cacheMode = WebSettings.LOAD_NO_CACHE
}

/**
 * 模块脚本必须带 JS 的 MIME，否则 WebView 会拒绝执行 `import`；
 * 所以这里按扩展名给准确的类型，而不是一律 `application/octet-stream`。
 */
private fun mimeOf(name: String): String = when {
    name.endsWith(".js") || name.endsWith(".mjs") -> "text/javascript"
    name.endsWith(".html") -> "text/html"
    name.endsWith(".css") -> "text/css"
    name.endsWith(".json") -> "application/json"
    name.endsWith(".svg") -> "image/svg+xml"
    else -> "application/octet-stream"
}

/**
 * 渲染完成的位图 + 它的**固有尺寸**（SVG 的 CSS px）。
 *
 * 两者都要：位图是按 `scale` 光栅化的，尺寸随缩放变；而固有尺寸对应
 * 「1 CSS px = 1 dp」这个映射，是唯一能定出正确显示宽度、且与图表真实尺寸无关的量。
 */
internal class MermaidBitmap(
    val bitmap: Bitmap,
    val naturalWidth: Int,
    val naturalHeight: Int,
)

/** 宿主页回报的原始结果：PNG data URL + 固有尺寸。 */
private class RawRender(
    val dataUrl: String,
    val naturalWidth: Int,
    val naturalHeight: Int,
)

/** 宿主页 → Kotlin 的通道。方法必须是 public 且带 [JavascriptInterface]。 */
private class Bridge(
    private val readyCallback: () -> Unit,
    private val resultCallback: (
        status: String,
        dataUrl: String,
        width: Int,
        height: Int,
        message: String,
    ) -> Unit,
) {
    /** 宿主页的 module 脚本执行完毕 —— 此刻 `window.renderDiagram` 才存在。 */
    @JavascriptInterface
    fun onReady() = readyCallback.invoke()

    @JavascriptInterface
    fun onRendered(status: String, dataUrl: String, width: Int, height: Int, message: String) {
        resultCallback(status, dataUrl, width, height, message)
    }
}
