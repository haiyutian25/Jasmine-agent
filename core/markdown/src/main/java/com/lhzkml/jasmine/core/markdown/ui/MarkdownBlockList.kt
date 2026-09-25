package com.lhzkml.jasmine.core.markdown.ui

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlock
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlockType
import com.lhzkml.jasmine.core.markdown.model.MarkdownCellAlignment
import kotlinx.coroutines.launch
import com.lhzkml.jasmine.core.markdown.model.MarkdownContainerType
import com.lhzkml.jasmine.core.markdown.model.MarkdownInline
import com.lhzkml.jasmine.core.markdown.model.MarkdownInlineType
import com.lhzkml.jasmine.core.markdown.model.MarkdownPrefixContext
import com.lhzkml.jasmine.core.ui.theme.CssVariables

/**
 * 把 [MarkdownBlock] 列表渲染成 Compose 内容。
 *
 * 这是 `core:markdown` 的展示层，风格对齐 `core:ui/components` 的约定：
 * 无状态、只接基元与 [currentTheme]、颜色一律取自设计系统令牌，不硬编码色值。
 *
 * ## 与 ima 渲染层的对应关系
 *
 * ima 的 Compose 渲染层是 `gt.d0`（`{Block, 行内内容, 预构建表格}`）+ `wy.p` /
 * `xy.l` / `pd0.f` 几个混淆类。本文件是同等职责的自研实现 —— 结构与
 * Block/Inline 模型一一对应，但没有照搬其混淆后的类划分。
 *
 * ## 明确的取舍（不是遗漏）
 *
 * - **数学公式**：ima 的 native 侧只把公式抽成 `MATH_BLOCK` / `FORMULA` 节点，
 *   真正的排版由上层（web 端用 KaTeX）完成。本工程没有引入公式排版库，
 *   所以公式按等宽文本原样显示。
 * - **HTML 块/行内**：只按字面量显示，不解析执行。
 * - **图片**：工程没有图片加载库（无 Coil/Glide），显示 alt 文本而非图片。
 */
@Composable
fun MarkdownBlockList(
    blocks: List<MarkdownBlock>,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
    bodyFontSize: TextUnit = 15.sp,
    baseColor: Color = currentTheme.cardForeground,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            // key 用块 id —— native 侧保证同一个源码块在各次增量中 id 不变，
            // 所以这里可以让 Compose 复用节点，只重绘真正变化的块。
            androidx.compose.runtime.key(block.id) {
                MarkdownBlockView(
                    block = block,
                    currentTheme = currentTheme,
                    bodyFontSize = bodyFontSize,
                    baseColor = baseColor,
                )
            }
        }
    }
}

@Composable
private fun MarkdownBlockView(
    block: MarkdownBlock,
    currentTheme: CssVariables,
    bodyFontSize: TextUnit,
    baseColor: Color,
) {
    when (block.type) {
        MarkdownBlockType.HEADING -> HeadingBlock(block, currentTheme, bodyFontSize, baseColor)
        // ```mermaid 围栏交给图引擎；其余代码块照旧。
        MarkdownBlockType.CODE_BLOCK -> if (block.fenceInfo.equals(MERMAID_FENCE, ignoreCase = true)) {
            MermaidOrCodeBlock(block, currentTheme, bodyFontSize)
        } else {
            CodeBlock(block, currentTheme, bodyFontSize)
        }
        MarkdownBlockType.MATH_BLOCK -> MathBlock(block, currentTheme, bodyFontSize, baseColor)
        MarkdownBlockType.THEMATIC_BREAK -> Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(currentTheme.border)
        )
        MarkdownBlockType.TABLE -> TableBlock(block, currentTheme, bodyFontSize, baseColor)
        MarkdownBlockType.HTML_BLOCK -> Text(
            text = block.literal,
            fontSize = bodyFontSize,
            fontFamily = FontFamily.Monospace,
            color = currentTheme.mutedForeground,
            modifier = Modifier.fillMaxWidth(),
        )
        MarkdownBlockType.IMAGE -> Text(
            text = block.content.toAnnotatedString(currentTheme, bodyFontSize),
            fontSize = bodyFontSize,
            color = baseColor,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> ParagraphBlock(block, currentTheme, bodyFontSize, baseColor)
    }
}

/** 段落 / 列表项 / 引用 —— 三者都是 PARAGRAPH，靠 [MarkdownBlock.prefix] 区分。 */
@Composable
private fun ParagraphBlock(
    block: MarkdownBlock,
    currentTheme: CssVariables,
    bodyFontSize: TextUnit,
    baseColor: Color,
) {
    val quote = block.prefix.firstOrNull { it.containerType == MarkdownContainerType.QUOTE }
    val list = block.prefix.lastOrNull { it.containerType != MarkdownContainerType.QUOTE }
    val annot = block.content.toAnnotatedString(currentTheme, bodyFontSize)

    if (quote != null) {
        // 引用：左侧竖线 + 缩进，与 ima 的 showQuoteMarker 对应。
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(IntrinsicSize.Min)
                    .background(currentTheme.border)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = annot,
                fontSize = bodyFontSize,
                color = currentTheme.mutedForeground,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    if (list != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // 列表标记只在条目首行显示（native 侧已把续写行的 showListMarker 置 false）。
            val marker = if (list.showListMarker) listMarkerText(list) else ""
            Text(
                text = marker,
                fontSize = bodyFontSize,
                color = if (list.containerType == MarkdownContainerType.TASK_LIST) {
                    currentTheme.primary
                } else {
                    currentTheme.mutedForeground
                },
                modifier = Modifier.width(20.dp),
            )
            Text(
                text = annot,
                fontSize = bodyFontSize,
                color = baseColor,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    Text(
        text = annot,
        fontSize = bodyFontSize,
        color = baseColor,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun listMarkerText(prefix: MarkdownPrefixContext): String = when (prefix.containerType) {
    MarkdownContainerType.NUMBERED_LIST -> "${prefix.numberListIndex}."
    MarkdownContainerType.TASK_LIST -> if (prefix.taskListChecked) "☑" else "☐"
    MarkdownContainerType.BULLETED_LIST_1 -> "•"
    MarkdownContainerType.BULLETED_LIST_2 -> "◦"
    MarkdownContainerType.BULLETED_LIST_3 -> "▪"
    MarkdownContainerType.BULLETED_LIST_4 -> "·"
    else -> ""
}

@Composable
private fun HeadingBlock(
    block: MarkdownBlock,
    currentTheme: CssVariables,
    bodyFontSize: TextUnit,
    baseColor: Color,
) {
    val size = when (block.headingLevel) {
        1 -> bodyFontSize * 1.55f
        2 -> bodyFontSize * 1.32f
        3 -> bodyFontSize * 1.16f
        4 -> bodyFontSize * 1.06f
        else -> bodyFontSize
    }
    Text(
        text = block.content.toAnnotatedString(currentTheme, bodyFontSize),
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        color = baseColor,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodeBlock(block: MarkdownBlock, currentTheme: CssVariables, bodyFontSize: TextUnit) {
    // 代码块横向可滚，避免长行折行破坏缩进语义。
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(currentTheme.radiusSm))
            .background(currentTheme.subtleSurface)
            .padding(10.dp)
    ) {
        if (block.fenceInfo.isNotBlank()) {
            Text(
                text = block.fenceInfo,
                fontSize = (bodyFontSize.value * 0.78f).sp,
                color = currentTheme.mutedForeground,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            text = block.literal,
            fontSize = (bodyFontSize.value * 0.88f).sp,
            fontFamily = FontFamily.Monospace,
            color = currentTheme.cardForeground,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

/**
 * ` ```mermaid ` 块：**默认显示代码**，用户点「图片」才交给图引擎渲染。
 *
 * 与 ima 的 `MermaidCodeBlockComposable` 同一套交互（「图片 / 代码」切换），但默认
 * 停在「代码」页 —— 源码始终可读，渲染成图是一个显式动作。
 *
 * **没有回退逻辑**：渲染不成功就停在「图片」页的加载态，由用户自己切回「代码」。
 * 这样反而顺带解决了两个问题：
 *
 * - 流式过程中源码还在增长，而默认页就是代码，所以不会去建一张只画了半张的图；
 * - 渲染只在用户点开「图片」时发生，且全程复用 [MermaidRenderer] 里**唯一**的那个离屏
 *   WebView —— 存活数量不随 mermaid 块的数量增长（这正是之前一次创建 7 个 WebView
 *   把渲染进程压垮的原因）。
 */
@Composable
private fun MermaidOrCodeBlock(
    block: MarkdownBlock,
    currentTheme: CssVariables,
    bodyFontSize: TextUnit,
) {
    var showImage by remember(block.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val isDark = currentTheme.background.luminance() < 0.5f
    val scope = rememberCoroutineScope()

    // 保存图片：位图仍然问同一个渲染器要 —— 用户能点到这个按钮，说明已经在「图片」页
    // 渲染过一次，所以这里必定缓存命中，几乎立即返回（不会重新起一次渲染）。
    val saveImage: () -> Unit = {
        scope.launch {
            val rendered = MermaidRenderer.render(context, block.literal, isDark, density)
            val ok = rendered != null && saveMermaidImage(context, rendered.bitmap)
            toast(context, if (ok) "已保存到相册" else "保存失败")
        }
    }
    // API 29 起走分区存储，不需要权限；26-28 要先申请 WRITE_EXTERNAL_STORAGE。
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) saveImage() else toast(context, "需要存储权限才能保存图片") }

    Column(Modifier.fillMaxWidth()) {
        MermaidTabBar(
            showImage = showImage,
            onSelect = { showImage = it },
            currentTheme = currentTheme,
            bodyFontSize = bodyFontSize,
            onCopy = {
                copyMermaidSource(context, block.literal)
                toast(context, "已复制")
            },
            onSaveImage = {
                if (needsStoragePermission(context)) {
                    permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    saveImage()
                }
            },
        )
        if (showImage) {
            MermaidImage(
                source = block.literal,
                isDark = isDark,
                currentTheme = currentTheme,
            )
        } else {
            CodeBlock(block, currentTheme, bodyFontSize)
        }
    }
}

/**
 * 「代码 / 图片」切换条 + 右侧动作图标。**代码在前**，且默认选中的就是「代码」。
 *
 * 右侧按钮跟着当前页切换，与 ima 的 `FencedCodeTabBar` 一致（`FencedCodeBlockComposable.kt:411`）：
 * 代码页给「复制」（ima 用 `code_copy_icon`），图片页给「保存图片」（ima 用 `std_ic_download`）。
 * 两边都是纯图标，不带文字。
 */
@Composable
private fun MermaidTabBar(
    showImage: Boolean,
    onSelect: (Boolean) -> Unit,
    currentTheme: CssVariables,
    bodyFontSize: TextUnit,
    onCopy: () -> Unit,
    onSaveImage: () -> Unit,
) {
    // 整条做成一个**有边界的工具栏**：底色 + 描边 + 圆角。不这样做的话，两个 tab 加一个
    // 图标看起来就是三段散落的文字和符号，既读不出"这是一条工具栏"，也看不出哪块能点。
    val shape = RoundedCornerShape(currentTheme.radiusSm)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(currentTheme.subtleSurface)
            .border(1.dp, currentTheme.border, shape)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MermaidTab("代码", !showImage, currentTheme, bodyFontSize) { onSelect(false) }
            MermaidTab("图片", showImage, currentTheme, bodyFontSize) { onSelect(true) }
        }
        Spacer(Modifier.weight(1f))
        if (showImage) {
            MermaidActionIcon(Icons.Default.Download, "保存图片", currentTheme, onSaveImage)
        } else {
            MermaidActionIcon(Icons.Default.ContentCopy, "复制", currentTheme, onCopy)
        }
    }
}

/**
 * tab 栏右侧的图标动作按钮。
 *
 * 做成**有底色 + 描边的方块**（而不是裸图标）：这是它能被认出来的全部依据 ——
 * 尺寸定在 30dp，既够得着，又能让描边显出"这是个按键"。
 */
@Composable
private fun MermaidActionIcon(
    icon: ImageVector,
    label: String,
    currentTheme: CssVariables,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(currentTheme.radiusSm - 2.dp)
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(shape)
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            // 用前景色而不是弱化色：这是可点击的动作，不该看起来像禁用态。
            tint = currentTheme.foreground,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 轻量提示：几秒的反馈用系统 Toast 就够，不必为此引入 Snackbar 宿主。 */
private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Composable
private fun MermaidTab(
    label: String,
    selected: Boolean,
    currentTheme: CssVariables,
    bodyFontSize: TextUnit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(currentTheme.radiusSm - 2.dp)
    Text(
        text = label,
        fontSize = (bodyFontSize.value * 0.82f).sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        color = if (selected) currentTheme.foreground else currentTheme.mutedForeground,
        modifier = Modifier
            .clip(shape)
            // 选中态换成 card（比工具栏底色亮一档）再加描边，形成"陷进去"的层次。
            // 之前用 muted，与底色几乎同色，选中与否根本看不出来。
            .background(if (selected) currentTheme.card else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, currentTheme.border, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** 触发图引擎的围栏信息串。 */
private const val MERMAID_FENCE = "mermaid"

@Composable
private fun MathBlock(
    block: MarkdownBlock,
    currentTheme: CssVariables,
    bodyFontSize: TextUnit,
    baseColor: Color,
) {
    // 公式不引排版库，按等宽斜体显示原文（见文件头「取舍」说明）。
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(currentTheme.radiusSm))
            .background(currentTheme.subtleSurface)
            .padding(10.dp)
    ) {
        Text(
            text = block.literal,
            fontSize = (bodyFontSize.value * 0.95f).sp,
            fontFamily = FontFamily.Monospace,
            fontStyle = FontStyle.Italic,
            color = baseColor,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun TableBlock(
    block: MarkdownBlock,
    currentTheme: CssVariables,
    bodyFontSize: TextUnit,
    baseColor: Color,
) {
    // 列宽按内容自适应 + 整表横向可滚：不引表格布局库的前提下最稳的做法。
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(currentTheme.radiusSm))
            .horizontalScroll(rememberScrollState()),
    ) {
        block.table.forEach { row ->
            Row(
                Modifier
                    .background(
                        if (row.isHeader) currentTheme.subtleSurface else Color.Transparent
                    )
                    .padding(vertical = 6.dp)
            ) {
                row.cells.forEach { cell ->
                    Text(
                        text = cell.content.toAnnotatedString(currentTheme, bodyFontSize),
                        fontSize = (bodyFontSize.value * 0.92f).sp,
                        fontWeight = if (row.isHeader) FontWeight.SemiBold else FontWeight.Normal,
                        color = baseColor,
                        textAlign = when (cell.alignment) {
                            MarkdownCellAlignment.CENTER -> TextAlign.Center
                            MarkdownCellAlignment.RIGHT -> TextAlign.End
                            else -> TextAlign.Start
                        },
                        modifier = Modifier
                            .widthIn(min = 72.dp)
                            .padding(horizontal = 10.dp),
                    )
                }
            }
            if (row != block.table.last()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(currentTheme.border)
                )
            }
        }
    }
}

// ── 行内 → AnnotatedString ──────────────────────────────────────────────

/**
 * 把行内节点树折成 [AnnotatedString]。
 *
 * 行内样式（强调/粗体/删除线/下划线/高亮/代码/链接）在这里映射成 SpanStyle；
 * 结构是递归的，与 native 侧的 Inline.children 一一对应。
 */
private fun List<MarkdownInline>.toAnnotatedString(
    theme: CssVariables,
    bodyFontSize: TextUnit,
): AnnotatedString = buildAnnotatedString {
    appendInlines(this@toAnnotatedString, theme, bodyFontSize)
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlines(
    nodes: List<MarkdownInline>,
    theme: CssVariables,
    bodyFontSize: TextUnit,
) {
    nodes.forEach { node ->
        when (node.type) {
            MarkdownInlineType.TEXT -> append(node.literal.orEmpty())

            MarkdownInlineType.SOFT_BREAK -> append('\n')
            MarkdownInlineType.LINE_BREAK -> append('\n')

            MarkdownInlineType.CODE -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = theme.muted,
                    fontSize = (bodyFontSize.value * 0.9f).sp,
                )
            ) { append(node.literal.orEmpty()) }

            MarkdownInlineType.EMPHASIS -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlines(node.children, theme, bodyFontSize)
            }

            MarkdownInlineType.STRONG -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInlines(node.children, theme, bodyFontSize)
            }

            MarkdownInlineType.STRIKETHROUGH -> withStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough)
            ) { appendInlines(node.children, theme, bodyFontSize) }

            // [腾讯扩展] ==高亮== —— 对应 ima 的 <mark> 渲染。
            MarkdownInlineType.HIGHLIGHT -> withStyle(
                SpanStyle(background = theme.accent.copy(alpha = 0.28f))
            ) { appendInlines(node.children, theme, bodyFontSize) }

            // [腾讯扩展] ~下划线~ —— 对应 ima 的 <u>。web 端白名单没有 u，
            // 这是 native 侧独有的能力（见逆向包 FULL_RECOVERY.md §4）。
            MarkdownInlineType.UNDERLINE -> withStyle(
                SpanStyle(textDecoration = TextDecoration.Underline)
            ) { appendInlines(node.children, theme, bodyFontSize) }

            MarkdownInlineType.LINK -> withStyle(
                SpanStyle(
                    color = theme.primary,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium,
                )
            ) { appendInlines(node.children, theme, bodyFontSize) }

            // 无图片加载库：退化为 alt 文本（见文件头「取舍」说明）。
            MarkdownInlineType.IMAGE -> withStyle(
                SpanStyle(color = theme.mutedForeground, fontStyle = FontStyle.Italic)
            ) {
                val alt = node.children.plainText().ifEmpty { node.url.orEmpty() }
                append("🖼 ")
                append(alt)
            }

            MarkdownInlineType.FORMULA -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, fontStyle = FontStyle.Italic)
            ) { append(node.literal.orEmpty()) }

            MarkdownInlineType.HTML -> append(node.literal.orEmpty())

            else -> if (node.children.isEmpty()) {
                append(node.literal.orEmpty())
            } else {
                appendInlines(node.children, theme, bodyFontSize)
            }
        }
    }
}

/** 取行内树里的可见文本（图片 alt、纯文本回退用）。 */
internal fun List<MarkdownInline>.plainText(): String = buildString { collectPlain(this@plainText) }

private fun StringBuilder.collectPlain(nodes: List<MarkdownInline>) {
    nodes.forEach { node ->
        when (node.type) {
            MarkdownInlineType.SOFT_BREAK, MarkdownInlineType.LINE_BREAK -> append(' ')
            MarkdownInlineType.IMAGE ->
                if (node.children.isNotEmpty()) collectPlain(node.children)
                else append(node.literal.orEmpty())
            else -> if (node.children.isEmpty()) {
                append(node.literal.orEmpty())
            } else {
                collectPlain(node.children)
            }
        }
    }
}

/** 供调用方构造与正文一致的排版（避免各处自己拼 TextStyle）。 */
@Composable
fun markdownBodyStyle(currentTheme: CssVariables, bodyFontSize: TextUnit = 15.sp): TextStyle =
    remember(currentTheme, bodyFontSize) {
        TextStyle(fontSize = bodyFontSize, color = currentTheme.cardForeground)
    }
