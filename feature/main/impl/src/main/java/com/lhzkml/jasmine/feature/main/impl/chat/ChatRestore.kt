package com.lhzkml.jasmine.feature.main.impl.chat

import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import com.lhzkml.jasmine.core.markdown.IncrementalMarkdownParser
import com.lhzkml.jasmine.core.markdown.MarkdownParserFactory
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlock
import java.util.UUID

/**
 * @param fallbackModelLabel 事件里**没有**记模型名时用的兜底值（由 [ChatViewModel.modelLabelOf]
 *   从会话记录里取）。事件里记着（`Event.modelVersion`）就优先用它 —— 那是逐条精确的。
 * @param parserFactory 一次性解析正文用的解析器工厂（解析端口注入，见 [MarkdownParserFactory]）。
 */
internal fun TranscriptMessage.toChatMessage(
    fallbackModelLabel: String? = null,
    parserFactory: MarkdownParserFactory,
): ChatMessage = ChatMessage(
    id = UUID.randomUUID().toString(),
    role = role,
    text = text,
    isError = isError,
    // 工具条目（调用 / 返回）没有正文，界面上按工具行渲染；正文为空的普通消息也一样。
    tool = tool?.let {
        ChatToolActivity(
            name = it.name,
            detail = it.detail,
            result = it.result,
            // 状态从行数据里读（核心给的取值见 `toolStatusOf`），不从"有没有结果"反推。
            status = toolStatusOf(toolStatus),
        )
    },
    // Stored rows are plain text; parse them once so restored history renders as
    // Markdown too. Never a live stream, so a single pass is enough.
    // 工具条目没有正文，跳过解析 —— 免得为一次空文档白跑 native。
    blocks = if (text.isEmpty()) emptyList() else parseMarkdownBlocks(text, parserFactory),
    timestamp = timestamp,
    // 优先用事件自己记的模型名；旧数据没有，才回退到会话记录的模型。
    modelLabel = modelLabel ?: fallbackModelLabel,
    stoppedAfterMs = stoppedAfterMs,
    // 历史里也带思考（核心把它挂回到这一轮的回复上），所以重开对话照样看得到思考块。
    thinking = thinking,
)

/**
 * One-shot parse of an already-finished message.
 *
 * Two steps, in this order: the append result carries every block (the parser starts
 * at offset 0), then the finalize result carries the tail that only becomes final
 * once the stream ends. Skipping the first step would drop the stable prefix.
 *
 * [IncrementalMarkdownParser.apply] 是伴生函数，纯 Kotlin、不碰 native，因此这里
 * 与生产端共用同一条「截断+追加」的合并语义，而不是另写一份。
 */
internal fun parseMarkdownBlocks(
    markdown: String,
    parserFactory: MarkdownParserFactory,
): List<MarkdownBlock> {
    if (markdown.isEmpty()) return emptyList()
    val parser = parserFactory.create()
    return try {
        val ast = mutableListOf<MarkdownBlock>()
        IncrementalMarkdownParser.apply(parser.append(markdown), ast)
        IncrementalMarkdownParser.apply(parser.finalizeStream(), ast)
        ast
    } catch (error: Throwable) {
        // Native boundary: a failure here must not take down history restore.
        emptyList()
    } finally {
        parser.close()
    }
}
