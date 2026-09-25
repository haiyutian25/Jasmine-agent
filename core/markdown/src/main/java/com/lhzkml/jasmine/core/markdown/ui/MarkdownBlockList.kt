package com.lhzkml.jasmine.core.markdown.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
// ⚠️ Compose 1.12 起 appendInlineContent 从 androidx.compose.ui.text 移到了 foundation.text
//    （定义在 InlineTextContentKt 里）。用旧包名会报 Unresolved reference。
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlock
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlockType
import com.lhzkml.jasmine.core.markdown.model.MarkdownCellAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * - **数学公式**：`MATH_BLOCK` / `FORMULA` 都交给 [MicroTexRenderer]（与 ima 同一套引擎）。
 *   行内公式用 `InlineTextContent` 嵌进文本流，因此**必须在组合期同步渲染** ——
 *   占位尺寸当场就得定下来。ima 的做法一样（`xd0.c` 直接在构造函数里 `LaTeX.parse`）。
 * - **HTML 块/行内**：只按字面量显示，不解析执行。
 * - **图片**：由 Coil 加载。
 */
@Composable
fun MarkdownBlockList(
    blocks: List<MarkdownBlock>,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
    bodyFontSize: TextUnit = 15.sp,
    baseColor: Color = currentTheme.cardForeground,
) {
    // 行内公式在组合期同步渲染，所以引擎要尽早备好（详见 MicroTexRenderer.warmUp）。
    val context = LocalContext.current
    LaunchedEffect(context) { MicroTexRenderer.warmUp(context) }

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
        // 图片块：`![alt](url)` 独占一行时解析成这个类型。
        MarkdownBlockType.IMAGE -> {
            val image = block.content.soleImage()
            if (image != null) {
                MarkdownImage(
                    url = image.first.url.orEmpty(),
                    alt = image.first.children.plainText(),
                    currentTheme = currentTheme,
                    linkUrl = image.second,
                )
            } else {
                // 兜底：结构不是「一张图」时仍按文本走，不至于整块消失。
                val inline = block.content.toAnnotatedString(
                    currentTheme, bodyFontSize, rememberMathEnv(baseColor),
                )
                Text(
                    text = inline.text,
                    inlineContent = inline.inlineContent,
                    fontSize = bodyFontSize,
                    color = baseColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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
    val inline = block.content.toAnnotatedString(
        currentTheme, bodyFontSize, rememberMathEnv(baseColor),
    )
    // 整段只有一张图（可能被链接包裹）时交给图片组件，见 soleImage 的说明。
    val image = block.content.soleImage()

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
            ParagraphContent(
                image = image,
                inline = inline,
                currentTheme = currentTheme,
                bodyFontSize = bodyFontSize,
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
            ParagraphContent(
                image = image,
                inline = inline,
                currentTheme = currentTheme,
                bodyFontSize = bodyFontSize,
                color = baseColor,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    ParagraphContent(
        image = image,
        inline = inline,
        currentTheme = currentTheme,
        bodyFontSize = bodyFontSize,
        color = baseColor,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 段落的内容区：整段是一张图就交给 [MarkdownImage]，否则是样式化文本。
 *
 * 引用 / 列表 / 普通段落三者的差别只在**前缀与颜色**，内容区是同一套逻辑 ——
 * 所以抽成一个函数，图片支持就不必在三处各写一遍。
 */
@Composable
private fun ParagraphContent(
    image: Pair<MarkdownInline, String?>?,
    inline: InlineRenderResult,
    currentTheme: CssVariables,
    bodyFontSize: TextUnit,
    color: Color,
    modifier: Modifier,
) {
    if (image != null) {
        MarkdownImage(
            url = image.first.url.orEmpty(),
            alt = image.first.children.plainText(),
            currentTheme = currentTheme,
            modifier = modifier,
            linkUrl = image.second,
        )
    } else {
        Text(
            text = inline.text,
            inlineContent = inline.inlineContent,
            fontSize = bodyFontSize,
            color = color,
            modifier = modifier,
        )
    }
}

/**
 * 这段行内内容去掉空白后若只剩一张图，就把它取出来（连同外层可能的链接 url）。
 *
 * 为什么要单独判出来、而不是在 `AnnotatedString` 里嵌 inline content：
 * inline content 的占位尺寸必须在组合期定死，而图片真实尺寸要等网络回来才知道 ——
 * 定小了把图挤扁，定大了留一片空白。整段交给 [MarkdownImage] 就能按容器宽度自适应。
 *
 * 返回 `图片节点 to 外层链接url`；不是「单图段」则返回 null（走文本渲染）。
 */
private fun List<MarkdownInline>.soleImage(): Pair<MarkdownInline, String?>? {
    val only = filterNot { it.isBlankInline() }.singleOrNull() ?: return null
    // 地址不是链接的不算图片，返回 null 让它走文本渲染、原样显示源码。
    if (only.type == MarkdownInlineType.IMAGE && isImageUrl(only.url.orEmpty())) return only to null
    // [![alt](img)](link) —— 链接里只包着一张图。
    if (only.type == MarkdownInlineType.LINK) {
        val inner = only.children.filterNot { it.isBlankInline() }.singleOrNull()
        if (inner?.type == MarkdownInlineType.IMAGE && isImageUrl(inner.url.orEmpty())) {
            return inner to only.url
        }
    }
    return null
}

/** 源码里的地址是不是一个真链接；`![alt](图片地址)` 这种占位文字不算。 */
private fun isImageUrl(url: String): Boolean {
    val u = url.trim()
    if (u.isEmpty()) return false
    if (u.contains("://")) return true
    if (u.startsWith("/") || u.startsWith("//")) return true
    if (u.startsWith("data:") || u.startsWith("file:") || u.startsWith("content:")) return true
    return u.contains('.') || u.contains('/')
}

/** 空白文本节点（图片前后的缩进/换行常产生这种节点）。 */
private fun MarkdownInline.isBlankInline(): Boolean =
    type == MarkdownInlineType.TEXT && literal.orEmpty().isBlank()

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
    val inline = block.content.toAnnotatedString(
        currentTheme, bodyFontSize, rememberMathEnv(baseColor),
    )
    Text(
        text = inline.text,
        inlineContent = inline.inlineContent,
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        color = baseColor,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodeBlock(block: MarkdownBlock, currentTheme: CssVariables, bodyFontSize: TextUnit) {
    val context = LocalContext.current
    // 与 mermaid 块共用同一种容器：左边是语言，右边是复制。
    ToolbarBlock(
        currentTheme = currentTheme,
        toolbar = {
            Text(
                text = block.fenceInfo.ifBlank { "代码" },
                fontSize = (bodyFontSize.value * 0.78f).sp,
                color = currentTheme.mutedForeground,
            )
            Spacer(Modifier.weight(1f))
            ToolbarIconButton(Icons.Default.ContentCopy, "复制", currentTheme) {
                copyToClipboard(context, block.literal)
                toast(context, "已复制")
            }
        },
    ) {
        CodeText(block, currentTheme, bodyFontSize)
    }
}

/** 代码正文：横向可滚，避免长行折行破坏缩进语义。 */
@Composable
private fun CodeText(block: MarkdownBlock, currentTheme: CssVariables, bodyFontSize: TextUnit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(10.dp)
    ) {
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

    ToolbarBlock(
        currentTheme = currentTheme,
        toolbar = {
            MermaidTab("代码", !showImage, currentTheme, bodyFontSize) { showImage = false }
            MermaidTab("图片", showImage, currentTheme, bodyFontSize) { showImage = true }
            Spacer(Modifier.weight(1f))
            if (showImage) {
                ToolbarIconButton(Icons.Default.Download, "保存图片", currentTheme) {
                    if (needsStoragePermission(context)) {
                        permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        saveImage()
                    }
                }
            } else {
                ToolbarIconButton(Icons.Default.ContentCopy, "复制", currentTheme) {
                    copyToClipboard(context, block.literal)
                    toast(context, "已复制")
                }
            }
        },
    ) {
        if (showImage) {
            MermaidImage(
                source = block.literal,
                isDark = isDark,
                currentTheme = currentTheme,
            )
        } else {
            // 只渲染正文 —— 工具栏已经由上面的 ToolbarBlock 提供了。
            CodeText(block, currentTheme, bodyFontSize)
        }
    }
}

/**
 * 带工具栏的块容器：一个外框（底色 + 描边 + 圆角）里装「顶部工具栏 + 分隔线 + 内容」。
 *
 * 工具栏必须和内容在**同一个框**里。各自独立成框的话，看起来就是上下两块东西，
 * 读不出"上面这条是这块内容的操作栏"。
 */
@Composable
private fun ToolbarBlock(
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
    toolbar: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(currentTheme.radiusSm)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(currentTheme.subtleSurface)
            .border(1.dp, currentTheme.border, shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = toolbar,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(currentTheme.border)
        )
        content()
    }
}

/** 工具栏右侧的图标按钮：有底色 + 描边的方块，30dp 够得着、也看得出能点。 */
@Composable
private fun ToolbarIconButton(
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
    val context = LocalContext.current
    val density = LocalDensity.current
    val mathSize = blockMathSize(bodyFontSize)

    // 解析 + 光栅化不算便宜，按「源码 / 字号 / 颜色 / 密度」缓存。
    val bitmap by produceState<Bitmap?>(null, block.literal, mathSize, baseColor, density.density) {
        value = withContext(Dispatchers.Default) {
            MicroTexRenderer.render(
                context = context,
                latex = block.literal,
                textSizeSp = mathSize,
                color = baseColor.toArgb(),
                density = density.density,
            )
        }
    }

    // 不加背景卡片：卡片的内边距会白白吃掉左右各 10dp，公式能用的宽度就少了。
    // 去掉之后公式可以用满整行，也就有条件把字号放大（见 BlockMathScale）。
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val rendered = bitmap
        if (rendered != null) {
            // 位图是按 density 放大的，除回去就是它该占的 dp 尺寸。
            val imageWidth = with(density) { rendered.width.toDp() }
            val imageHeight = with(density) { rendered.height.toDp() }
            val overflows = imageWidth > maxWidth
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (overflows) {
                            Modifier.horizontalScroll(rememberScrollState())
                        } else {
                            Modifier
                        }
                    ),
                // 装得下就居中（数学排版惯例，ima 的 xd0.c 也有居中分支）；装不下则从左侧起可滚动。
                horizontalArrangement = if (overflows) Arrangement.Start else Arrangement.Center,
            ) {
                Image(
                    bitmap = rendered.asImageBitmap(),
                    contentDescription = block.literal,
                    modifier = Modifier.size(imageWidth, imageHeight),
                )
            }
        } else {
            // 还没渲出来（或公式有语法错）：退回显示源码，不留空白。
            Text(
                text = block.literal,
                fontSize = mathSize.sp,
                fontFamily = FontFamily.Monospace,
                fontStyle = FontStyle.Italic,
                color = baseColor,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun TableBlock(
    block: MarkdownBlock,
    currentTheme: CssVariables,
    bodyFontSize: TextUnit,
    baseColor: Color,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = LocalTextStyle.current
    val cellFontSize = (bodyFontSize.value * 0.92f).sp

    // 每列取所有行里最宽的那一格，全表共用这一组列宽。
    // 逐行各自算的话，表头（"类型"两个字）和内容（长文本）会各占各的宽度，列就对不齐。
    //
    // 测量样式必须和下面 Text 实际用的样式一致：Text 会拿 LocalTextStyle 兜底（字体、字距…），
    // 只按 fontSize/fontWeight 去量会偏窄，末字被挤到下一行。
    val columnWidths = remember(block.table, cellFontSize, currentTheme, measurer, density, baseStyle) {
        val columns = block.table.maxOfOrNull { it.cells.size } ?: 0
        (0 until columns).map { index ->
            val widest = block.table.maxOfOrNull { row ->
                val cell = row.cells.getOrNull(index) ?: return@maxOfOrNull 0
                measurer.measure(
                    text = cell.content.toAnnotatedString(currentTheme, bodyFontSize).text,
                    style = baseStyle.merge(
                        TextStyle(
                            fontSize = cellFontSize,
                            fontWeight = if (row.isHeader) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    ),
                ).size.width
            } ?: 0
            (with(density) { widest.toDp() } + CellPadding * 2).coerceAtLeast(CellMinWidth)
        }
    }

    // 整表横向可滚：列多/内容长时不挤成一团。
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
                row.cells.forEachIndexed { index, cell ->
                    val inline = cell.content.toAnnotatedString(
                        currentTheme, bodyFontSize, rememberMathEnv(baseColor),
                    )
                    Text(
                        text = inline.text,
                        inlineContent = inline.inlineContent,
                        fontSize = cellFontSize,
                        fontWeight = if (row.isHeader) FontWeight.SemiBold else FontWeight.Normal,
                        color = baseColor,
                        textAlign = when (cell.alignment) {
                            MarkdownCellAlignment.CENTER -> TextAlign.Center
                            MarkdownCellAlignment.RIGHT -> TextAlign.End
                            else -> TextAlign.Start
                        },
                        modifier = Modifier
                            .width(columnWidths.getOrElse(index) { CellMinWidth })
                            .padding(horizontal = CellPadding),
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

private val CellPadding = 10.dp
private val CellMinWidth = 72.dp

// ── 行内 → AnnotatedString ──────────────────────────────────────────────

/**
 * 行内渲染的产物：样式化文本 + 需要以 Composable 形式嵌进文本流的内容（目前只有图片）。
 *
 * `Text` 组件同时接受这两样，所以调用点必须成对传下去 —— 只传 text 的话，图片位置会
 * 变成一个不可见的空占位。
 */
private class InlineRenderResult(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
)

/**
 * 把行内节点树折成 [InlineRenderResult]。
 *
 * 行内样式（强调/粗体/删除线/下划线/高亮/代码/链接）在这里映射成 SpanStyle；
 * 结构是递归的，与 native 侧的 Inline.children 一一对应。
 */
private fun List<MarkdownInline>.toAnnotatedString(
    theme: CssVariables,
    bodyFontSize: TextUnit,
    math: MathEnv? = null,
): InlineRenderResult {
    // 图片和公式都要先在遍历中登记、再由调用方交给 Text，所以边遍历边往这里塞。
    val contents = mutableMapOf<String, InlineTextContent>()
    val text = buildAnnotatedString {
        appendInlines(this@toAnnotatedString, theme, bodyFontSize, contents, math)
    }
    return InlineRenderResult(text, contents)
}

/**
 * 行内公式的渲染环境。
 *
 * 行内公式**只能在组合期同步渲染** —— `InlineTextContent` 的占位尺寸在组合时就得定下来，
 * 异步拿不到（ima 也是这么做的：`xd0.c` 直接在构造函数里 `LaTeX.parse`）。
 * 所以 context / density / 文字色要一路传到行内遍历里。
 *
 * 传 null 表示不渲染公式（比如只测量文本宽度时），此时公式退化成等宽文本。
 */
private class MathEnv(
    val context: Context,
    val density: Density,
    val color: Color,
)

/** 组合期取一次 [MathEnv]，避免每次重组都新建对象、让下游的 key 失效。 */
@Composable
private fun rememberMathEnv(color: Color): MathEnv {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density, color) { MathEnv(context, density, color) }
}

/** 行内公式字号：略小于正文，跟上下文文字协调（嵌在文字流里，不能喧宾夺主）。 */
private fun mathSize(bodyFontSize: TextUnit): Float = bodyFontSize.value * 0.95f

/**
 * 块级公式字号：独立成行，可以比正文大一些。
 *
 * 放大是有前提的 —— 块级公式不再套背景卡片（卡片左右各 10dp 内边距会吃掉可用宽度），
 * 这样它才能用满整行而不至于频繁触发横向滚动。
 */
private fun blockMathSize(bodyFontSize: TextUnit): Float = bodyFontSize.value * BLOCK_MATH_SCALE

private const val BLOCK_MATH_SCALE = 1.15f

/**
 * 行内公式 → inline content。
 *
 * 位图是按 `字号 × density` 光栅化的，所以「位图宽 ÷ density」就是它在 dp 下的宽度；
 * 再用 [Density.toSp] 换成 sp，才能跟着系统字号缩放一起走。
 */
private fun inlineFormulaContent(bitmap: Bitmap, density: Density): InlineTextContent {
    // ⚠️ Density.toSp() 的输入约定是【像素】，它内部自己会除 density 和 fontScale。
    //    这里若先手动除一次 density，公式会被压到 1/density（实测 32px 算成 4.2sp 而不是 11.6sp）。
    val w = with(density) { bitmap.width.toFloat().toSp() }
    val h = with(density) { bitmap.height.toFloat().toSp() }
    return InlineTextContent(
        placeholder = Placeholder(
            width = w,
            height = h,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        ),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 行内图片 → inline content。占位尺寸取行高，跟着字号走，不撑变形。 */
private fun inlineImageContent(
    url: String,
    theme: CssVariables,
    bodyFontSize: TextUnit,
): InlineTextContent {
    val size = (bodyFontSize.value * 1.15f).sp
    return InlineTextContent(
        placeholder = Placeholder(
            width = size,
            height = size,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        ),
    ) { alternate ->
        AsyncImage(
            model = url,
            contentDescription = alternate,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlines(
    nodes: List<MarkdownInline>,
    theme: CssVariables,
    bodyFontSize: TextUnit,
    contents: MutableMap<String, InlineTextContent>,
    math: MathEnv?,
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
                appendInlines(node.children, theme, bodyFontSize, contents, math)
            }

            MarkdownInlineType.STRONG -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInlines(node.children, theme, bodyFontSize, contents, math)
            }

            MarkdownInlineType.STRIKETHROUGH -> withStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough)
            ) { appendInlines(node.children, theme, bodyFontSize, contents, math) }

            // [腾讯扩展] ==高亮== —— 对应 ima 的 <mark> 渲染。
            MarkdownInlineType.HIGHLIGHT -> withStyle(
                SpanStyle(background = theme.accent.copy(alpha = 0.28f))
            ) { appendInlines(node.children, theme, bodyFontSize, contents, math) }

            // [腾讯扩展] ~下划线~ —— 对应 ima 的 <u>。web 端白名单没有 u，
            // 这是 native 侧独有的能力（见逆向包 FULL_RECOVERY.md §4）。
            MarkdownInlineType.UNDERLINE -> withStyle(
                SpanStyle(textDecoration = TextDecoration.Underline)
            ) { appendInlines(node.children, theme, bodyFontSize, contents, math) }

            MarkdownInlineType.LINK -> {
                val inner = node.children.filterNot { it.isBlankInline() }.singleOrNull()
                if (inner?.type == MarkdownInlineType.IMAGE && !isImageUrl(inner.url.orEmpty())) {
                    // 链接里包的是一张「地址不是链接」的图，整块按原始写法显示。
                    append("[![${inner.children.plainText()}](${inner.url})](${node.url})")
                } else {
                    withStyle(
                        SpanStyle(
                            color = theme.primary,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium,
                        )
                    ) { appendInlines(node.children, theme, bodyFontSize, contents, math) }
                }
            }

            // 行内图片：嵌进文本流，由 Coil 加载。
            MarkdownInlineType.IMAGE -> {
                val url = node.url.orEmpty()
                if (isImageUrl(url)) {
                    val id = "md-image-" + contents.size
                    appendInlineContent(id, alternateText = node.children.plainText())
                    contents[id] = inlineImageContent(url, theme, bodyFontSize)
                } else {
                    // 源码里的地址不是链接（`![alt](图片地址)` 这类示例写法），按原始写法显示。
                    append("![${node.children.plainText()}]($url)")
                }
            }

            MarkdownInlineType.FORMULA -> {
                val latex = node.literal.orEmpty()
                val bitmap = math?.let {
                    MicroTexRenderer.render(
                        context = it.context,
                        latex = latex,
                        textSizeSp = mathSize(bodyFontSize),
                        color = it.color.toArgb(),
                        density = it.density.density,
                    )
                }
                if (bitmap != null) {
                    val id = "md-formula-" + contents.size
                    appendInlineContent(id, alternateText = latex)
                    contents[id] = inlineFormulaContent(bitmap, math.density)
                } else {
                    // 引擎不可用或公式语法错误时退化成等宽文本，内容不丢。
                    withStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, fontStyle = FontStyle.Italic)
                    ) { append(latex) }
                }
            }

            MarkdownInlineType.HTML -> append(node.literal.orEmpty())

            else -> if (node.children.isEmpty()) {
                append(node.literal.orEmpty())
            } else {
                appendInlines(node.children, theme, bodyFontSize, contents, math)
            }
        }
    }
}

/** 递归取出行内树里的可见文本 —— 图片的 alt 文本就是这么来的（`literal` 只在叶子上）。 */
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
