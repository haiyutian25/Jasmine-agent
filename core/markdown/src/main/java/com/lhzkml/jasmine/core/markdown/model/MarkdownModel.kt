package com.lhzkml.jasmine.core.markdown.model

/*
 * 增量 Markdown 数据模型。
 *
 * 结构对齐腾讯 ima 的 `com.tencent.incremark` JNI 封装层（见逆向包
 * `incremark_recon/reconstructed_jni/IncremarkModels.kt`），字段语义一一对应，
 * 仅重命名以免与本工程其它 `Block`/`Update` 撞名：
 *
 *     ima 原名          ->  本文件
 *     Block             ->  MarkdownBlock
 *     Inline            ->  MarkdownInline
 *     BlockType         ->  MarkdownBlockType
 *     InlineType        ->  MarkdownInlineType
 *     ContainerType     ->  MarkdownContainerType
 *     PrefixContext     ->  MarkdownPrefixContext
 *     TableRow/Cell     ->  MarkdownTableRow / MarkdownTableCell
 *     Update            ->  MarkdownUpdate
 *
 * 与 ima 相比**未做**的两件事：
 *   1. 表格 colspan/rowspan —— ima 的原模型中同样未暴露（合并信息在它的渲染层
 *      另行处理），这里也留待渲染层。
 *   2. `Update.advanced` / `newlyCompletedCount` —— 保留字段以维持契约一致，
 *      但和 ima 一样**不参与** apply()，只供 UI 做动画/统计。
 */

/**
 * 块级类型。code 与 ima 的 `BlockType` 完全一致。
 *
 * 注意：**没有引用块/列表类型** —— 在 ima 的扁平模型里，引用与列表不改变块类型，
 * 而是通过 [MarkdownPrefixContext] 挂在 [MarkdownBlock.prefix] 上。
 */
enum class MarkdownBlockType(val code: Int) {
    PARAGRAPH(0),
    HEADING(1),
    CODE_BLOCK(2),
    MATH_BLOCK(3),
    THEMATIC_BREAK(4),
    TABLE(5),
    HTML_BLOCK(6),
    IMAGE(7),
    OTHER(8),
    ;

    companion object {
        /** 未识别时兜底 [OTHER]，与 ima 的 `BlockType.from` 一致。 */
        fun from(code: Int): MarkdownBlockType = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

/**
 * 容器/前缀类型。code 与 ima 的 `ContainerType` 一致。
 *
 * 无序列表有 [BULLETED_LIST_1] ~ [BULLETED_LIST_4] 四档，对应嵌套深度 ——
 * 因为扁平的块模型没有父子关系，缩进层级只能编码在这里。
 */
enum class MarkdownContainerType(val code: Int) {
    QUOTE(0),
    NUMBERED_LIST(1),
    TASK_LIST(2),
    BULLETED_LIST_1(3),
    BULLETED_LIST_2(4),
    BULLETED_LIST_3(5),
    BULLETED_LIST_4(6),
    ;

    companion object {
        /** ⚠️ 兜底是 [QUOTE] 而不是列表，与 ima 的 `ContainerType.from` 一致。 */
        fun from(code: Int): MarkdownContainerType = entries.firstOrNull { it.code == code } ?: QUOTE
    }
}

/** 行内类型。code 与 ima 的 `InlineType` 一致；标注 [腾讯扩展] 的三项上游 cmark-gfm 没有。 */
enum class MarkdownInlineType(val code: Int) {
    TEXT(0),
    SOFT_BREAK(1),
    LINE_BREAK(2),
    CODE(3),
    HTML(4),
    EMPHASIS(5),
    STRONG(6),
    STRIKETHROUGH(7),

    /** [腾讯扩展] `==高亮==` */
    HIGHLIGHT(8),

    /** [腾讯扩展] `~下划线~` */
    UNDERLINE(9),
    LINK(10),
    IMAGE(11),

    /** [腾讯扩展] `$数学$` */
    FORMULA(12),
    OTHER(13),
    ;

    companion object {
        fun from(code: Int): MarkdownInlineType = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

/**
 * 一个已解析的块。
 *
 * [id] 是**稳定标识**：同一个源码块在连续多次 [com.lhzkml.jasmine.core.markdown.IncrementalMarkdownEngine.append]
 * 中保持同一个 id，UI 可以据此复用节点、只对变化的块做动画。
 * id 由「块在源码中的起始偏移 + 类型」派生，因此块内容增长不会换 id。
 *
 * [isClosed] 为 false 表示这是一段**尚未写完**的块（流式过程中正在增长的尾部）——
 * 默认 true 与 ima 的 `Block.isClosed` 默认值一致。
 */
data class MarkdownBlock(
    val id: String,
    val type: MarkdownBlockType,
    val headingLevel: Int = 0,
    /** 围栏代码块的信息串（语言名），如 ```kotlin 的 `kotlin`。 */
    val fenceInfo: String = "",
    /** 代码块/MATH_BLOCK 的原始正文；行内块为空串。 */
    val literal: String = "",
    val url: String = "",
    val title: String = "",
    val isClosed: Boolean = true,
    val content: List<MarkdownInline> = emptyList(),
    val table: List<MarkdownTableRow> = emptyList(),
    val prefix: List<MarkdownPrefixContext> = emptyList(),
)

/**
 * 一个行内节点。可递归（[children]）—— 例如 `**粗 *斜* 体**` 是 STRONG 包着
 * 一个 TEXT 和一个 EMPHASIS。
 */
data class MarkdownInline(
    val type: MarkdownInlineType,
    val literal: String? = null,
    val url: String? = null,
    val title: String? = null,
    val children: List<MarkdownInline> = emptyList(),
)

/**
 * 容器前缀上下文：决定引用符号、列表标记与缩进。
 *
 * [isEndBlock] 标记「本容器最后一个条目」—— 扁平模型没有父子关系，收尾信息
 * 只能这样带出来。`prefix` 是一个**栈**：引用里的有序列表会得到
 * `[QUOTE, NUMBERED_LIST]` 两项。
 */
data class MarkdownPrefixContext(
    val containerType: MarkdownContainerType,
    val showListMarker: Boolean = false,
    val showQuoteMarker: Boolean = false,
    val isEndBlock: Boolean = false,
    val numberListIndex: Int = 0,
    val taskListChecked: Boolean = false,
)

/** 表格行。[isHeader] 对应 GFM 表格的分隔行之前那一行。 */
data class MarkdownTableRow(
    val isHeader: Boolean,
    val cells: List<MarkdownTableCell>,
)

/** 表格单元格。[alignment] 来自分隔行（`:---:` 之类），用于渲染对齐。 */
data class MarkdownTableCell(
    val content: List<MarkdownInline>,
    val alignment: MarkdownCellAlignment = MarkdownCellAlignment.NONE,
)

/**
 * GFM 表格列对齐。
 *
 * code 与 native 侧一致：cmark-gfm 的 table 扩展把对齐存成字符 `'l'/'c'/'r'`，
 * incremark 层把它折算成 0..3 传给 JNI。
 */
enum class MarkdownCellAlignment(val code: Int) {
    NONE(0),
    LEFT(1),
    CENTER(2),
    RIGHT(3),
    ;

    companion object {
        fun from(code: Int): MarkdownCellAlignment = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/**
 * 一次增量解析的结果 —— 整个流式渲染的核心。
 *
 * 语义（与 ima 的 `Update` 及其 native 访问器一一对应）：
 *
 * - [index]：**稳定前缀的块数**。列表 `ast[0, index)` 属于已定型的旧块，
 *   调用方必须原样保留、不重建。
 * - [blocks]：从 [index] 起重新产出的块（可能包含正在增长的未闭合尾块）。
 * - [advanced] / [newlyCompletedCount]：**不参与** apply()，只供 UI 动画/统计。
 *
 * 与前一次结果的合成规则见 [com.lhzkml.jasmine.core.markdown.IncrementalMarkdownEngine.apply]。
 */
data class MarkdownUpdate(
    val index: Int,
    val advanced: Boolean,
    val newlyCompletedCount: Int,
    val blocks: List<MarkdownBlock>,
) {
    companion object {
        /** 空增量。与 ima 实测的 `Update(0, false, 0, emptyList())` 一致。 */
        val EMPTY = MarkdownUpdate(0, false, 0, emptyList())
    }
}
