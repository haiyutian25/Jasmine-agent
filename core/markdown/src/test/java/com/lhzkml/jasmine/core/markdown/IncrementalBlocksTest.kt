package com.lhzkml.jasmine.core.markdown

import com.lhzkml.jasmine.core.markdown.model.MarkdownBlock
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlockType
import com.lhzkml.jasmine.core.markdown.model.MarkdownInline
import com.lhzkml.jasmine.core.markdown.model.MarkdownInlineType
import com.lhzkml.jasmine.core.markdown.model.MarkdownUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 块列表的两条纯函数（G3 那一族不变量）。
 *
 * 流式渲染的块列表这一层有**两种合成方式**，它们必须是同一个语义：
 * - [IncrementalMarkdownParser.apply]：**就地改**一个可变列表（UDF 之外的调用方，如 [ChatRestore] 那条恢复路径）；
 * - [IncrementalMarkdownDocument.applied]：返回新列表、**不碰入参**（UDF 用 —— `ChatMessage.blocks` 是 `List`，
 *   界面上某一帧的块列表不许被下一次追加改掉，G3 修的正是这一类"别名"）。
 *
 * 这两个函数都是纯 Kotlin（不碰 native），所以能在 JVM 里钉死；`IncrementalMarkdownDocument` 的
 * **实例**做不到 —— 它的构造要 `IncrementalMarkdownParser()`（`NativeBridge` 在 `init` 里
 * `System.loadLibrary`），而生产代码里也确实没有任何地方构造它（只用它的伴生 `applied`）。
 */
class IncrementalBlocksTest {

    // ── 就地版：说明"为什么可变列表不能交出去" ──────────────────────────

    /**
     * `apply` 改的是**传进去的那个列表本身**（截断 + 追加）—— 这正是 `blocks` 必须给副本的理由。
     *
     * 谁在用：`ChatRestore` 的恢复路径（`apply(parser.append(markdown), ast)`）。
     */
    @Test
    fun `apply mutates the target list in place`() {
        val target = mutableListOf(block("old"))

        IncrementalMarkdownParser.apply(update(index = 1, blocks = listOf(block("new"))), target)

        assertEquals(listOf("old", "new"), target.map { it.id })
    }

    /** `index` 之前的块原样留着（稳定前缀不重解析），`index` 起的旧尾巴被截掉。 */
    @Test
    fun `apply keeps the stable prefix and replaces the tail`() {
        val target = mutableListOf(block("a"), block("b"), block("c"))

        IncrementalMarkdownParser.apply(update(index = 1, blocks = listOf(block("b'"))), target)

        assertEquals(listOf("a", "b'"), target.map { it.id })
    }

    /** `index` 越界 = 纯追加（流式最常见的形状）。 */
    @Test
    fun `apply appends when the index is past the end`() {
        val target = mutableListOf(block("a"))

        IncrementalMarkdownParser.apply(update(index = 5, blocks = listOf(block("b"))), target)

        assertEquals(listOf("a", "b"), target.map { it.id })
    }

    // ── 不可变版：UDF 那条路 ───────────────────────────────────────────

    /**
     * `applied` **不改入参**（谁在用：`ChatViewModel` 每收到一个流式分片就
     * `message.copy(blocks = applied(update, message.blocks))`）—— 原列表必须原封不动，
     * 否则"上一帧"与"这一帧"会共用同一份列表，界面就是"历史跟着变"。
     */
    @Test
    fun `applied never mutates the list it is given`() {
        val ast = listOf(block("a"), block("b"))

        val patched = IncrementalMarkdownDocument.applied(
            update(index = 1, blocks = listOf(block("b'"))),
            ast,
        )

        assertEquals("原列表不能被动", listOf("a", "b"), ast.map { it.id })
        assertEquals(listOf("a", "b'"), patched.map { it.id })
    }

    /** 三种分支，逐条对：空增量原样返回、纯追加、从中间截断重建。 */
    @Test
    fun `applied follows its three branches`() {
        val ast = listOf(block("a"), block("b"))

        assertSame(
            "index 越界且没有新块：原样返回同一个列表（别多复制一份）",
            ast,
            IncrementalMarkdownDocument.applied(update(index = 2, blocks = emptyList()), ast),
        )
        assertEquals(
            "index 越界但有新块：接在后面",
            listOf("a", "b", "c"),
            IncrementalMarkdownDocument.applied(update(index = 2, blocks = listOf(block("c"))), ast).map { it.id },
        )
        assertEquals(
            "index 落在中间：截断后重建",
            listOf("a", "z"),
            IncrementalMarkdownDocument.applied(update(index = 1, blocks = listOf(block("z"))), ast).map { it.id },
        )
    }

    /** 两种合成方式对同一个增量给出**同一个结果**（否则恢复路径与流式路径会分叉）。 */
    @Test
    fun `the in-place and the immutable form agree`() {
        val initial = listOf(block("a"), block("b"), block("c"))
        val update = update(index = 1, blocks = listOf(block("b2"), block("c2")))

        val inPlace = initial.toMutableList()
        IncrementalMarkdownParser.apply(update, inPlace)

        assertEquals(inPlace, IncrementalMarkdownDocument.applied(update, initial))
    }

    private companion object {
        fun block(id: String) = MarkdownBlock(
            id = id,
            type = MarkdownBlockType.PARAGRAPH,
            content = listOf(MarkdownInline(MarkdownInlineType.TEXT, literal = id)),
        )

        fun update(index: Int, blocks: List<MarkdownBlock>) = MarkdownUpdate(
            index = index,
            advanced = false,
            newlyCompletedCount = 0,
            blocks = blocks,
        )
    }
}
