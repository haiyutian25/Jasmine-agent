package com.lhzkml.jasmine.core.markdown

import com.lhzkml.jasmine.core.markdown.model.MarkdownUpdate

/**
 * ima `incremark` 引擎的 JNI 入口。
 *
 * 与腾讯 `com.tencent.incremark.NativeBridge` 的 9 个方法**逐一对应**，只是包名换成
 * 本工程自己的（JNI 符号名必须与 Kotlin 包名一致）：
 *
 * ```
 * ima 的地址     ima 的符号                                   对应 native 函数
 * ──────────────────────────────────────────────────────────────────────────────
 * 0x20e18  Java_com_tencent_incremark_NativeBridge_nativeNew          incremark_parser_new
 * 0x20e1c  ..._nativeFree                                            incremark_parser_free
 * 0x20e24  ..._nativeReset                                           incremark_parser_reset
 * 0x20e2c  ..._nativeAppend                                          incremark_parser_append
 * 0x21508  ..._nativeFinalize                                        incremark_parser_finalize
 * 0x21534  ..._nativeCopyBuffer                                      incremark_parser_copy_buffer
 * 0x215d4  ..._nativeRenderPlainText                                 incremark_parser_render_plaintext
 * 0x215d8  ..._nativeRenderMarkdown                                  incremark_parser_render_markdown
 * 0x216ac  ..._nativeRenderPlainTextOf                               incremark_render_plaintext
 * ```
 *
 * 本工程编译出的 .so 已用 llvm-nm 核对：这 9 个 Java_* 与 JNI_OnLoad 全部导出，
 * 与 ima 的数量一致。
 *
 * ⚠️ 本 object 与 9 个方法必须保持 **public**：JNI 符号名是按包名+类名+方法名
 *    拼出来的（`Java_com_lhzkml_jasmine_core_markdown_NativeBridge_nativeNew`），
 *    标成 internal 会让 Kotlin 给方法名加模块后缀，符号名随之失配。
 */
object NativeBridge {

    init {
        System.loadLibrary("incremark_jni")
    }

    /** 分配一个 parser，返回其 native handle（0 表示失败）。 */
    external fun nativeNew(): Long

    /** 释放 handle。 */
    external fun nativeFree(handle: Long)

    /** 清空 parser 状态但保留 handle（用于 FULL 模式整篇重解析）。 */
    external fun nativeReset(handle: Long)

    /** 追加一段 UTF-8 增量文本，返回本次增量结果。 */
    external fun nativeAppend(handle: Long, utf8: ByteArray): MarkdownUpdate

    /** 结束流式输入，返回收尾增量（未闭合块在此闭合）。 */
    external fun nativeFinalize(handle: Long): MarkdownUpdate

    /** 取回 parser 内部累计的完整原文。 */
    external fun nativeCopyBuffer(handle: Long): String

    /** 渲染为 Markdown 文本。 */
    external fun nativeRenderMarkdown(handle: Long): String

    /** 渲染为纯文本（基于已有 handle）。 */
    external fun nativeRenderPlainText(handle: Long): String

    /** 渲染为纯文本（无 handle，直接吃一段 Markdown）。 */
    external fun nativeRenderPlainTextOf(utf8: ByteArray): String
}
