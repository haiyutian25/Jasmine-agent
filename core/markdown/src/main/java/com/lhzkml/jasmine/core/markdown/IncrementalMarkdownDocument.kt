package com.lhzkml.jasmine.core.markdown

import com.lhzkml.jasmine.core.markdown.model.MarkdownBlock
import com.lhzkml.jasmine.core.markdown.model.MarkdownUpdate
import java.io.Closeable

/**
 * 文档层封装：parser + 累积块列表。
 *
 * 与腾讯 `com.tencent.incremark.IncrementalMarkdownDocument` 方法体一致。
 *
 * ⚠️ 与逆向结论相同的提醒：ima 的 App 主路径（`gt.h0`）**并未使用**这个类，
 *    而是直接用 [IncrementalMarkdownParser] 加锁。本工程默认也不用它 ——
 *    块列表作为不可变状态放在 `ChatMessage.blocks` 里（UDF 要求）。
 *    保留本类是为了完整对上 ima 的 JNI 封装层，并便于单测/脚本化使用。
 */
class IncrementalMarkdownDocument : Closeable {

    private val parser = IncrementalMarkdownParser()

    /** 累积的块列表 —— 即 [IncrementalMarkdownParser.apply] 的作用对象。 */
    private val _blocks = mutableListOf<MarkdownBlock>()

    val blocks: List<MarkdownBlock> get() = _blocks

    /** 追加增量并立即应用到块列表，返回最新块列表。 */
    fun append(chunk: String): List<MarkdownBlock> {
        IncrementalMarkdownParser.apply(parser.append(chunk), _blocks)
        return blocks
    }

    /** 收尾并应用。 */
    fun finalizeStream(): List<MarkdownBlock> {
        IncrementalMarkdownParser.apply(parser.finalizeStream(), _blocks)
        return blocks
    }

    val buffer: String get() = parser.buffer

    fun renderMarkdown(): String = parser.renderMarkdown()

    fun renderPlainText(): String = parser.renderPlainText()

    /** 重置解析器**并清空块列表**（注意与 `parser.reset()` 的差别）。 */
    fun reset() {
        parser.reset()
        _blocks.clear()
    }

    override fun close() {
        parser.close()
    }

    companion object {
        /**
         * 不可变风格的应用：返回应用增量后的新列表。
         *
         * 供 UDF 使用（`ChatMessage.blocks` 是 `List`，不能就地改）。
         * 稳定性来自 native 侧保证的 `id` 不变：`ast` 的前 `index` 项与重解析结果
         * 的前 `index` 项相等，因此截断+追加是安全的。
         */
        fun applied(update: MarkdownUpdate, ast: List<MarkdownBlock>): List<MarkdownBlock> =
            when {
                update.index >= ast.size && update.blocks.isEmpty() -> ast
                update.index >= ast.size -> ast + update.blocks
                else -> ast.subList(0, update.index).toList() + update.blocks
            }
    }
}
