package com.lhzkml.jasmine.feature.main.impl.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.theme.CssVariables

/**
 * 工具调用**展开后**的内容渲染 —— 照 ZCode 的 `resolveToolCallRenderer(toolName)` 分流：一个工具名
 * 对应一个"渲染器"，各渲染器自己决定怎么画自己的载荷（补丁 / 清单 / 终端输出 / 兜底纯文本）。
 *
 * 与 ZCode 的对应关系（那边实现在 `packages/ui/src/ToolCallBlocks/renderers` 目录下，分流在
 * `ToolCallBlocks/resolveRenderer.js`）：
 *
 * | ZCode | 这里 | 载荷形状 |
 * |---|---|---|
 * | `renderers/edit.tsx`（PencilIcon + 文件 chip + 增删统计） | [DiffContent] | 补丁文本（加减行） |
 * | `renderers/todo.tsx`（ListTodoIcon + 完成数/总数） | [TodoContent] | 清单行或 JSON 项 |
 * | `renderers/execute.tsx`（SquareTerminalIcon） | [TerminalContent] | 参数里的 command + 输出 |
 * | 其它 | [PlainContent] | 参数 + 结果原文 |
 *
 * **新增一个工具类型**：加一个 [ToolCallKind]、把工具名写进 [toolCallKindOf]、在 [ToolCallDetail] 里
 * 分到对应渲染器 —— 界面其余部分不用动。
 */
enum class ToolCallKind {
    /** 改动文件：画成补丁（加行 / 减行分色）。 */
    DIFF,

    /** 待办清单：一行一项，完成打点、待办淡色。 */
    TODO,

    /** 命令执行：命令一行 + 输出块。 */
    TERMINAL,

    /** 兜底：参数与结果原文。 */
    PLAIN,
}

/**
 * 工具名 → 渲染器。名字表刻意写得宽：各家内置 / MCP 工具的叫法都收进来，认不出的落到 [ToolCallKind.PLAIN]。
 */
fun toolCallKindOf(name: String): ToolCallKind = when (name.lowercase()) {
    "edit",
    "edit_file",
    "write",
    "write_file",
    "apply_patch",
    "str_replace",
    "str_replace_editor",
    "create_file",
    "multiedit",
    -> ToolCallKind.DIFF

    "todo",
    "todo_write",
    "todowrite",
    "update_plan",
    "set_tasks",
    "plan",
    -> ToolCallKind.TODO

    "bash",
    "shell",
    "sh",
    "exec",
    "execute",
    "execute_command",
    "run",
    "run_command",
    "terminal",
    "local_shell",
    -> ToolCallKind.TERMINAL

    else -> ToolCallKind.PLAIN
}

/**
 * 这条载荷能不能按它的渲染器画出来？画不出来就是**降级**：退回原文，并在卡片上标出来（E2）。
 *
 * 以前降级是**无声**的 —— edit 类工具的参数换个名字，展开区就一片空白，没人分得清"解析失败"和
 * "本来就没有内容"。现在这件事看得见。
 *
 * 在界面这一侧推导，而不是存进 `ChatToolActivity`：它是 name / detail / result 的**纯函数**，
 * 不是新状态 —— 存进模型会有两个真相，也会让 ViewModel 反过来依赖渲染细节。
 */
fun toolCallFallsBackToRaw(name: String, detail: String, result: String?): Boolean =
    when (toolCallKindOf(name)) {
        // 补丁认不出来时展开区会是空的 —— 这一路最需要兜底。
        ToolCallKind.DIFF -> patchTextOf(detail, result).isBlank()

        // 清单一项都认不出来（纯文本里没有 `- [ ]` 也没有 status/content 对）。
        ToolCallKind.TODO -> todoItemsOf(result ?: detail).isEmpty()

        // 命令和输出都没有：终端渲染器什么也画不出来。
        ToolCallKind.TERMINAL -> commandOf(detail).isEmpty() && result.isNullOrBlank()

        // 兜底渲染器画的就是原文，不存在"降级"。
        ToolCallKind.PLAIN -> false
    }

/** 展开区的内容：先按工具名分流，再交给对应的渲染器。 */
@Composable
fun ToolCallDetail(
    name: String,
    detail: String,
    result: String?,
    currentTheme: CssVariables,
) {
    // 认不出形状就画原文：宁可丑，不能空（E2）。
    if (toolCallFallsBackToRaw(name, detail, result)) {
        PlainContent(detail = detail, result = result, currentTheme = currentTheme)
        return
    }
    when (toolCallKindOf(name)) {
        ToolCallKind.DIFF -> DiffContent(detail = detail, result = result, currentTheme = currentTheme)
        ToolCallKind.TODO -> TodoContent(text = result ?: detail, currentTheme = currentTheme)
        ToolCallKind.TERMINAL -> TerminalContent(
            command = commandOf(detail),
            output = result,
            currentTheme = currentTheme,
        )
        ToolCallKind.PLAIN -> PlainContent(detail = detail, result = result, currentTheme = currentTheme)
    }
}

// ── 渲染器 ─────────────────────────────────────────────────────────────

/** 补丁：头部一行增减统计，下面按行分色（加行用主色、减行淡色、其余正常）。 */
@Composable
private fun DiffContent(detail: String, result: String?, currentTheme: CssVariables) {
    val patch = patchTextOf(detail, result)
    if (patch.isBlank()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(ToolDetailLineGap)) {
        Text(
            text = diffStatOf(patch),
            fontSize = ToolDetailFontSize,
            color = currentTheme.mutedForeground,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(currentTheme.radiusSm))
                .background(currentTheme.subtleSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Column {
                patch.lineSequence().forEach { line ->
                    Text(
                        text = line.ifEmpty { " " },
                        fontSize = ToolDetailFontSize,
                        color = when {
                            line.startsWith("+") -> currentTheme.primary
                            line.startsWith("-") -> currentTheme.mutedForeground
                            else -> currentTheme.cardForeground
                        },
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 待办清单：一行一项，完成的打点变主色、文案转淡（ZCode 的 todo 渲染器那三种状态）。 */
@Composable
private fun TodoContent(text: String, currentTheme: CssVariables) {
    val items = todoItemsOf(text)
    if (items.isEmpty()) {
        PlainContent(detail = text, result = null, currentTheme = currentTheme)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(ToolDetailLineGap)) {
        Text(
            text = "${items.count { it.done }}/${items.size}",
            fontSize = ToolDetailFontSize,
            fontFamily = FontFamily.Monospace,
            color = currentTheme.mutedForeground,
        )
        Column {
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(ToolDetailDotSize)
                            .clip(CircleShape)
                            .background(
                                if (item.done) currentTheme.primary else currentTheme.mutedForeground
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.text,
                        fontSize = ToolDetailFontSize,
                        color = if (item.done) {
                            currentTheme.mutedForeground
                        } else {
                            currentTheme.cardForeground
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 命令执行：命令一行 + 输出块（ZCode 的 execute 渲染器）。 */
@Composable
private fun TerminalContent(command: String, output: String?, currentTheme: CssVariables) {
    Column(verticalArrangement = Arrangement.spacedBy(ToolDetailLineGap)) {
        if (command.isNotEmpty()) {
            Text(
                text = "$ $command",
                fontSize = ToolDetailFontSize,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = currentTheme.cardForeground,
            )
        }
        if (!output.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(currentTheme.radiusSm))
                    .background(currentTheme.subtleSurface)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = output,
                    fontSize = ToolDetailFontSize,
                    fontFamily = FontFamily.Monospace,
                    color = currentTheme.mutedForeground,
                )
            }
        }
    }
}

/** 兜底：参数与结果原文。 */
@Composable
private fun PlainContent(detail: String, result: String?, currentTheme: CssVariables) {
    Column {
        if (detail.isNotEmpty()) {
            Text(
                text = detail,
                fontSize = ToolDetailFontSize,
                color = currentTheme.mutedForeground,
            )
        }
        result?.let {
            Text(
                text = it,
                fontSize = ToolDetailFontSize,
                color = currentTheme.mutedForeground,
            )
        }
    }
}

// ── 载荷解析 ───────────────────────────────────────────────────────────

/** 补丁在哪：先在参数里找（patch / diff / new_string 那类字段），没有就用结果正文。 */
private fun patchTextOf(detail: String, result: String?): String {
    val fromDetail = stringFieldOf(detail, "patch")
        ?: stringFieldOf(detail, "diff")
        ?: stringFieldOf(detail, "new_string")
    if (!fromDetail.isNullOrBlank()) {
        return unescape(fromDetail)
    }
    return result.orEmpty()
}

/** 增减统计（ZCode 的 edit 卡头右侧就是它）。 */
private fun diffStatOf(patch: String): String {
    val added = patch.lineSequence().count { it.startsWith("+") && !it.startsWith("+++") }
    val removed = patch.lineSequence().count { it.startsWith("-") && !it.startsWith("---") }
    return "+$added  -$removed"
}

/** 待办项：先认清单写法，再认 JSON 里的 status / content 对。 */
private fun todoItemsOf(text: String): List<ToolTodoItem> {
    val markdown = MarkdownTodoItem.findAll(text).map { match ->
        val done = match.groupValues[1].equals("x", ignoreCase = true)
        ToolTodoItem(text = match.groupValues[2].trim(), done = done)
    }.toList()
    if (markdown.isNotEmpty()) {
        return markdown
    }
    return JsonTodoItem.findAll(text).map { match ->
        val status = match.groupValues[1]
        ToolTodoItem(
            text = unescape(match.groupValues[2]),
            done = status.equals("completed", ignoreCase = true) ||
                status.equals("done", ignoreCase = true),
        )
    }.toList()
}

/** 参数里某个字符串字段的值（`{"command":"ls -la"}` 得到 `ls -la`）。 */
private fun commandOf(detail: String): String =
    (stringFieldOf(detail, "command") ?: stringFieldOf(detail, "cmd") ?: "").let(::unescape)

private fun stringFieldOf(json: String, field: String): String? =
    Regex("\"$field\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)

private fun unescape(value: String): String = value
    .replace("\\n", "\n")
    .replace("\\t", "\t")
    .replace("\\\"", "\"")
    .replace("\\\\", "\\")

private data class ToolTodoItem(val text: String, val done: Boolean)

private val MarkdownTodoItem = Regex("^\\s*[-*]\\s*\\[([ xX])]\\s*(.+)$", RegexOption.MULTILINE)

private val JsonTodoItem = Regex(
    "\"status\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
    RegexOption.DOT_MATCHES_ALL,
)

// ── 尺寸 ──────────────────────────────────────────────────────────────

private val ToolDetailLineGap = 2.dp
private val ToolDetailDotSize = 6.dp

/** 展开内容的字号：与聊天里的元信息同号（`ChatScreen` 那个常量是文件私有的，这里自己给一个）。 */
private val ToolDetailFontSize = 12.sp
