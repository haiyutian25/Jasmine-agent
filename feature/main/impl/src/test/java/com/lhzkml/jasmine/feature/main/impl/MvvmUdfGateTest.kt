package com.lhzkml.jasmine.feature.main.impl

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 严格 MVVM+UDF 的门禁（修复方案 §4.3）。
 *
 * 这是**防回退护栏**，不是正确性证明：它把"状态只能由 `handleAction`（及其同步调用的纯助手）写"
 * 这条约定变成可执行的检查 —— 以后有人把一个异步结果直接写进 `mutableStateFlow`，单测立刻红。
 *
 * 断言按方案的分期启用：① 今天就成立；②③ 分别在 P1（读-回流）与 P4（流式解析）完成后成立。
 */
class MvvmUdfGateTest {

    private val chatViewModelSource = repoFile(
        "feature/main/impl/src/main/java/com/lhzkml/jasmine/feature/main/impl/chat/ChatViewModel.kt"
    )

    private val usageStatsSource = repoFile(
        "feature/main/impl/src/main/java/com/lhzkml/jasmine/feature/main/impl/UsageStatsViewModel.kt"
    )

    /** ① 唯一写入点：`mutableStateFlow` 只能出现在 `updateState` 的定义里。 */
    @Test
    fun `mutableStateFlow is touched only by the single mutation point`() {
        val lines = chatViewModelSource.readLines()
        val definitionLine = lines.indexOfFirst { it.contains("fun updateState(") }
        assertTrue("找不到 updateState 的定义", definitionLine >= 0)

        val offenders = lines.withIndex()
            .filter { it.value.contains("mutableStateFlow") }
            .filter { it.index < definitionLine - 2 || it.index > definitionLine + 2 }
            .toList()

        assertEquals(
            "mutableStateFlow 只能出现在 updateState 的定义体里，以下行越界了：" +
                offenders.joinToString { "${it.index + 1}: ${it.value.trim()}" },
            0,
            offenders.size,
        )
    }

    /** ② 禁用规则：`updateState(` 的调用点不得位于异步作用域内，且必须落在同步助手白名单里。 */
    @Test
    fun `updateState is never called from an asynchronous scope`() {
        val lines = chatViewModelSource.readLines()
        val functions = functionRanges(lines)
        val callSites = lines.withIndex()
            // 两种写法都算：`updateState { … }`（本项目用的是这个）与 `updateState(…)`。
            .filter { CallSite.containsMatchIn(it.value) }
            // 定义行本身不是调用点。
            .filter { !it.value.contains("fun updateState(") }

        assertTrue("一个 updateState 调用点都没找到？正则过时了？", callSites.isNotEmpty())

        callSites.forEach { (lineIndex, text) ->
            val function = functions.lastOrNull { it.start <= lineIndex }
            assertTrue(
                "第 ${lineIndex + 1} 行的 updateState 找不到所属函数：${text.trim()}",
                function != null,
            )
            val owner = function!!
            assertFalse(
                "第 ${lineIndex + 1} 行的 updateState 位于 suspend 函数 ${owner.name} 里：" +
                    "异步结果必须先回流成 action（方案 §2、§3）",
                owner.isSuspend,
            )
            assertFalse(
                "第 ${lineIndex + 1} 行的 updateState 位于 ${owner.name} 的" +
                    "launch/withContext/async 块里：异步结果必须先回流成 action",
                isInsideAsyncBlock(lines, owner.start, lineIndex),
            )
            assertTrue(
                "第 ${lineIndex + 1} 行的 updateState 所在函数 ${owner.name} 不在同步助手白名单里" +
                    "（要么它是异步路径，要么白名单需要显式扩充）",
                owner.name in SynchronousHelpers,
            )
        }
    }

    /** ③ worker 隔离：`streamParseLoop` 里不得贴块（贴块只能经 `Internal.StreamParsed` 回流）。 */
    @Test
    fun `the stream parse worker never touches the state`() {
        val lines = chatViewModelSource.readLines()
        val worker = functionRanges(lines).firstOrNull { it.name == "streamParseLoop" }
        assertTrue("找不到 streamParseLoop", worker != null)

        val body = lines.subList(worker!!.start, worker.end + 1)
        listOf("updateState", "applyStreamBlocks", "appendBlock").forEach { forbidden ->
            val hit = body.indexOfFirst { it.contains(forbidden) }
            assertEquals(
                "streamParseLoop 里出现了 $forbidden（第 ${worker.start + hit + 1} 行）：" +
                    "解析结果必须投递成 Internal.StreamParsed（方案 §3 第 10 条）",
                -1,
                hit,
            )
        }
    }

    /** ② 的配套：用量页同样不许直摸状态。 */
    @Test
    fun `usage stats view model writes state through its own mutation point`() {
        val lines = usageStatsSource.readLines()
        val definitionLine = lines.indexOfFirst { it.contains("fun updateState(") }
        assertTrue("UsageStatsViewModel 找不到 updateState 的定义", definitionLine >= 0)

        val offenders = lines.withIndex()
            .filter { it.value.contains("mutableStateFlow") }
            .filter { it.index < definitionLine - 2 || it.index > definitionLine + 2 }
            .toList()

        assertEquals(
            "UsageStatsViewModel 里 mutableStateFlow 只能出现在 updateState 的定义体里：" +
                offenders.joinToString { "${it.index + 1}: ${it.value.trim()}" },
            0,
            offenders.size,
        )
    }

    private data class FunctionRange(
        val name: String,
        val start: Int,
        val end: Int,
        val isSuspend: Boolean,
    )

    /**
     * 按"函数头 → 下一个函数头"切区间。
     *
     * 修饰符要全带：`override fun handleAction` 也是函数头（漏了它，`handleAction` 里的调用点会被
     * 误判成上一个函数 —— 这正是本门禁第一版踩过的坑）。
     */
    private fun functionRanges(lines: List<String>): List<FunctionRange> {
        val header = Regex(
            """^\s*(?:(?:private|protected|internal|public|override|inline|suspend|operator|tailrec)\s+)*fun\s+([A-Za-z_][A-Za-z0-9_]*)"""
        )
        val starts = lines.mapIndexedNotNull { index, line ->
            header.find(line)?.let {
                FunctionRange(
                    name = it.groupValues[1],
                    start = index,
                    end = lines.lastIndex,
                    isSuspend = line.contains("suspend fun"),
                )
            }
        }
        return starts.mapIndexed { i, range ->
            range.copy(end = (starts.getOrNull(i + 1)?.start ?: lines.size) - 1)
        }
    }

    /**
     * 从函数头到调用点，调用点是否落在某个 `launch {` / `withContext(` / `async {` 打开的块里。
     *
     * 用花括号深度判断（不解析字符串，够用作护栏）：见到异步块时记住它开在哪一层，调用点更深就是"在里面"。
     */
    private fun isInsideAsyncBlock(lines: List<String>, from: Int, to: Int): Boolean {
        val asyncOpen = Regex("""(launch|withContext|async)\s*[({]""")
        val openDepths = mutableListOf<Int>()
        var depth = 0
        for (index in from..to) {
            val line = lines[index]
            if (index < to && asyncOpen.containsMatchIn(line) && line.contains("{")) {
                openDepths += depth + 1
            }
            depth += line.count { it == '{' } - line.count { it == '}' }
            if (index < to) {
                // 已经闭合的异步块不再算数。
                openDepths.removeAll { it > depth }
            } else {
                return openDepths.any { it <= depth }
            }
        }
        return false
    }

    private fun repoFile(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        requireNotNull(dir) { "找不到仓库根（未见 settings.gradle.kts）" }
        val file = File(dir, relativePath)
        require(file.exists()) { "找不到源文件：${file.absolutePath}" }
        return file
    }

    private companion object {
        /** `updateState` 的调用点（两种写法都要认）。 */
        val CallSite = Regex("""updateState\s*[({]""")

        /** 方案 §4.3 的同步助手白名单：这些函数由 `handleAction` 同步调用（或本身就是 handler）。 */
        val SynchronousHelpers = setOf(
            "handleAction",
            "resetSession",
            "handleTranscriptRestored",
            "handleSendClicked",
            "handleContinueClicked",
            "handleTurnInterrupted",
            "handleNewConversation",
            "handleThoughtLevelSelected",
            "handleModelSelected",
            "handleConversationSelected",
            "handleConversationDeleted",
            "handlePromptRequested",
            "handlePromptAnswered",
            "handleContextWindowSelected",
            "handleStopClicked",
            "applyStreamBlocks",
            "appendBlock",
            "startAssistantSegment",
            "appendReplyChunk",
            "appendReasoningChunk",
            "appendToolCall",
            "appendToolResult",
            "markOpenTools",
            "appendToolEntry",
            "sealAssistantSegment",
            "failTurn",
            "finishTurn",
            "endTurn",
            "handleLanguagePreference",
        )
    }
}
