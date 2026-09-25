package com.lhzkml.jasmine.core.markdown

import com.lhzkml.jasmine.core.markdown.model.MarkdownBlock
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlockType
import com.lhzkml.jasmine.core.markdown.model.MarkdownCellAlignment
import com.lhzkml.jasmine.core.markdown.model.MarkdownContainerType
import com.lhzkml.jasmine.core.markdown.model.MarkdownInline
import com.lhzkml.jasmine.core.markdown.model.MarkdownInlineType
import com.lhzkml.jasmine.core.markdown.model.MarkdownPrefixContext
import com.lhzkml.jasmine.core.markdown.model.MarkdownTableCell
import com.lhzkml.jasmine.core.markdown.model.MarkdownTableRow
import com.lhzkml.jasmine.core.markdown.model.MarkdownUpdate

/**
 * native -> Kotlin 的对象构造工厂。
 *
 * 对应腾讯的 `com.tencent.incremark.IncremarkBridge`：native 侧组装
 * Update/Block/Inline 树时通过 JNI 回调这 6 个静态方法。
 *
 * **全部集合参数用数组**（`Array<MarkdownInline>` 等）—— 与 ima 相同的取舍：
 * JNI 侧构造数组比构造 List 方便。这里再把数组转成 List 交给不可变的 data class。
 *
 * 4 个 String 参数可空：native 传 NULL 时统一落成 `""`，与 ima 的
 * `str == null ? "" : str` 三元表达式一致。
 *
 * ⚠️ 不要把这些方法（以及本 object）标成 `internal` —— Kotlin 会给 internal
 *    函数名加模块后缀（`makeUpdate$core_markdown`），native 侧的
 *    `GetStaticMethodID("makeUpdate")` 就会找不到。ima 的对应类同样是 public。
 */
object IncremarkBridge {

    @JvmStatic
    fun makeUpdate(
        index: Int,
        advanced: Boolean,
        newlyCompletedCount: Int,
        blocks: Array<MarkdownBlock>,
    ): MarkdownUpdate = MarkdownUpdate(index, advanced, newlyCompletedCount, blocks.toList())

    @JvmStatic
    fun makeBlock(
        id: String,
        type: Int,
        headingLevel: Int,
        fenceInfo: String?,
        literal: String?,
        url: String?,
        title: String?,
        isClosed: Boolean,
        content: Array<MarkdownInline>,
        table: Array<MarkdownTableRow>,
        prefix: Array<MarkdownPrefixContext>,
    ): MarkdownBlock = MarkdownBlock(
        id = id,
        type = MarkdownBlockType.from(type),
        headingLevel = headingLevel,
        fenceInfo = fenceInfo ?: "",
        literal = literal ?: "",
        url = url ?: "",
        title = title ?: "",
        isClosed = isClosed,
        content = content.toList(),
        table = table.toList(),
        prefix = prefix.toList(),
    )

    @JvmStatic
    fun makeInline(
        type: Int,
        literal: String?,
        url: String?,
        title: String?,
        children: Array<MarkdownInline>,
    ): MarkdownInline = MarkdownInline(
        type = MarkdownInlineType.from(type),
        literal = literal,
        url = url,
        title = title,
        children = children.toList(),
    )

    @JvmStatic
    fun makePrefix(
        containerType: Int,
        showListMarker: Boolean,
        showQuoteMarker: Boolean,
        isEndBlock: Boolean,
        numberListIndex: Int,
        taskListChecked: Boolean,
    ): MarkdownPrefixContext = MarkdownPrefixContext(
        containerType = MarkdownContainerType.from(containerType),
        showListMarker = showListMarker,
        showQuoteMarker = showQuoteMarker,
        isEndBlock = isEndBlock,
        numberListIndex = numberListIndex,
        taskListChecked = taskListChecked,
    )

    @JvmStatic
    fun makeTableRow(
        isHeader: Boolean,
        cells: Array<MarkdownTableCell>,
    ): MarkdownTableRow = MarkdownTableRow(isHeader, cells.toList())

    /** 对齐值是 native 侧的字符编码（'l'/'c'/'r'）转成的 0..3。 */
    @JvmStatic
    fun makeTableCell(
        content: Array<MarkdownInline>,
        alignment: Int,
    ): MarkdownTableCell = MarkdownTableCell(
        content = content.toList(),
        alignment = MarkdownCellAlignment.from(alignment),
    )
}
