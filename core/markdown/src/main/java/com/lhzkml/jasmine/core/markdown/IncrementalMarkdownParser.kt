package com.lhzkml.jasmine.core.markdown

import com.lhzkml.jasmine.core.markdown.model.MarkdownBlock
import com.lhzkml.jasmine.core.markdown.model.MarkdownUpdate
import java.io.Closeable
import java.nio.charset.StandardCharsets

/**
 * 增量 Markdown 解析器。
 *
 * 与腾讯 `com.tencent.incremark.IncrementalMarkdownParser` 的方法体**逐行等价**，
 * 只是把 native 调用接到本工程自己编译的 `libincremark_jni.so` 上。
 *
 * ★ 实测要点（沿用逆向结论）：ima 的真实调用方 `gt.h0` **并不使用**
 *   [IncrementalMarkdownDocument] 这个上层封装，而是直接持有本类的实例，
 *   配一把锁使用。本工程在 `ChatViewModel` 里同样直接持有（所有调用都发生在
 *   `handleAction` 的同步执行段内，天然串行）。
 */
class IncrementalMarkdownParser : Closeable {

    /** native parser handle。0 表示已关闭。 */
    private var handle: Long = NativeBridge.nativeNew()

    init {
        check(handle != 0L) { "incremark_parser_new failed" }
    }

    /**
     * 追加一段增量文本，返回本次增量结果。
     *
     * 文本以 UTF-8 编码后传给 native（native 只吃 ByteArray）—— 与 ima 相同。
     */
    fun append(chunk: String): MarkdownUpdate {
        val h = handle
        check(h != 0L) { "parser already closed" }
        return NativeBridge.nativeAppend(h, chunk.toByteArray(StandardCharsets.UTF_8))
    }

    /** 结束流式输入，返回收尾增量（未闭合块在此闭合）。 */
    fun finalizeStream(): MarkdownUpdate {
        val h = handle
        check(h != 0L) { "parser already closed" }
        return NativeBridge.nativeFinalize(h)
    }

    /** 取回 parser 内部累计的完整原文。 */
    val buffer: String
        get() {
            val h = handle
            check(h != 0L) { "parser already closed" }
            return NativeBridge.nativeCopyBuffer(h)
        }

    /** 渲染为 Markdown 文本。 */
    fun renderMarkdown(): String {
        val h = handle
        check(h != 0L) { "parser already closed" }
        return NativeBridge.nativeRenderMarkdown(h)
    }

    /** 渲染为纯文本。 */
    fun renderPlainText(): String {
        val h = handle
        check(h != 0L) { "parser already closed" }
        return NativeBridge.nativeRenderPlainText(h)
    }

    /**
     * 清空解析状态但保留 handle。
     * 调用方在「整篇重解析」（FULL 模式）时先 reset 再 append。
     */
    fun reset() {
        val h = handle
        check(h != 0L) { "parser already closed" }
        NativeBridge.nativeReset(h)
    }

    override fun close() {
        val h = handle
        if (h != 0L) {
            NativeBridge.nativeFree(h)
            handle = 0L
        }
    }

    companion object {

        /**
         * 增量算法上层入口 —— 把 Update 应用到已有的块列表。
         *
         * ★ 这是整个流式渲染的语义核心，与 ima **逐行等价**：
         *
         *     从 update.index 起截断旧尾部，再追加本次增量块。
         *     旧块 [0, index) 完全复用，不重解析、不重建。
         *
         * `advanced` / `newlyCompletedCount` **不参与**，只供 UI 使用。
         */
        fun apply(update: MarkdownUpdate, ast: MutableList<MarkdownBlock>) {
            if (update.index < ast.size) {
                ast.subList(update.index, ast.size).clear()
            }
            ast.addAll(update.blocks)
        }

        /** 无 handle 的一次性「Markdown → 纯文本」。 */
        fun renderPlainText(markdown: String): String =
            NativeBridge.nativeRenderPlainTextOf(markdown.toByteArray(StandardCharsets.UTF_8))
    }
}
