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

    private val settingsScreensDir = repoFile(
        "feature/settings/impl/src/main/java/com/lhzkml/jasmine/feature/settings/impl/screens"
    )

    /** ① 唯一写入点：`mutableStateFlow` 只能出现在 `updateState` 的定义里（D1 的投影覆写是只读例外）。 */
    @Test
    fun `mutableStateFlow is touched only by the single mutation point`() {
        val lines = chatViewModelSource.readLines()
        val definitionLine = lines.indexOfFirst { it.contains("fun updateState(") }
        assertTrue("找不到 updateState 的定义", definitionLine >= 0)

        val offenders = lines.withIndex()
            .filter { it.value.contains("mutableStateFlow") }
            .filter { it.index < definitionLine - 2 || it.index > definitionLine + 2 }
            // D1：stateFlow / state 的投影覆写可以**读**它（combine / project 的输入）；写仍然只有 updateState。
            .filter { !D1ProjectionRead.containsMatchIn(it.value) }
            .toList()

        assertEquals(
            "mutableStateFlow 只能出现在 updateState 的定义体里，以下行越界了：" +
                offenders.joinToString { "${it.index + 1}: ${it.value.trim()}" },
            0,
            offenders.size,
        )
    }

    /** R9（方案 D1）：会话投影只能由 `project()` 派生 —— 手动 `copy(conversation = …)` 就是回退。 */
    @Test
    fun `conversation projection is derived, never hand-copied`() {
        val lines = chatViewModelSource.readLines()
        val functions = functionRanges(lines)
        val offenders = lines.withIndex()
            // 精确匹配 copy(conversation = …（排除 copy(conversations = …）与注释行）。
            .filter { (_, line) -> R9CopyConversation.containsMatchIn(line) }
            .filter { (_, line) ->
                val trimmed = line.trimStart()
                !trimmed.startsWith("*") && !trimmed.startsWith("//")
            }
            .filter { (index, _) -> functions.lastOrNull { it.start <= index }?.name != "project" }

        assertEquals(
            "ChatState.conversation 只能由 project() 派生（手动投影已在方案 D1 删除）：" +
                offenders.joinToString { "L${it.index + 1}: ${it.value.trim()}" },
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

    /**
     * R8（P0 修复方案 D2）：**影子可变状态**与 StateFlow 同一条规矩 —— 写点只允许落在同步
     * handler 白名单里；suspend 函数与 launch/withContext/async 块里一律禁止。
     *
     * 覆盖对象是"不进 StateFlow 的行为状态"：`attachedKeys`、`conversationEpoch`、
     * `pendingContextWindow` / `pendingReasoningEffort`、`pendingActiveModel`、
     * `languagePreferenceSeen`、`chats`、`turns` 与 `Turn` 的可变字段。②⑥ 两条正则只认
     * `updateState`，看不见它们 —— 这条把那块灰色地带收进护栏。
     */
    @Test
    fun `shadow state is written only from synchronous handlers`() {
        val lines = chatViewModelSource.readLines()
        val functions = functionRanges(lines)
        val offenders = lines.withIndex()
            // 声明行（private var x = …）不是写点。
            .filter { (_, line) -> !PropertyDeclaration.containsMatchIn(line) }
            .filter { (_, line) -> ShadowWrite.containsMatchIn(line) }
            .filter { (index, _) ->
                val owner = functions.lastOrNull { it.start <= index } ?: return@filter true
                owner.isSuspend ||
                    isInsideAsyncBlock(lines, owner.start, index) ||
                    owner.name !in ShadowWriteHelpers
            }

        assertEquals(
            "影子状态只能由同步 handler 白名单写（异步结果先回流成 Internal action，方案 D2）：\n" +
                offenders.joinToString("\n") { "L${it.index + 1}: ${it.value.trim()}" },
            0,
            offenders.size,
        )
    }

    /**
     * R11（P0 修复方案 D3）：handler 必须是纯的 —— 边界调用（agent 门面 / 各仓库 / 平台语言仓库 /
     * 字体缓存 / 探测器）只允许出现在 suspend 函数或协程块里（异步路径）；同步函数里出现即违规。
     *
     * 白名单只剩三类例外：与附着严格有序、必须同步的**释放/拆毁**（`releaseConversation` /
     * `onCleared`，注释见 ChatViewModel）；装配订阅的 `init`；以及**纯内存读**
     * （`resolveContentFont` / `resolveFontPreviews` 查字体内存快照；`agentSettings` 读偏好仓库
     * 那条热 StateFlow 的 `.value`）—— 都是读内存里的现成值，无 IO、无副作用。
     */
    @Test
    fun `view model handlers do not touch boundaries synchronously`() {
        val roots = listOf(
            "feature/main/impl/src/main/java",
            "feature/settings/impl/src/main/java",
            "feature/provider/impl/src/main/java",
        )
        val offenders = roots
            .flatMap { root ->
                repoFile(root).walkTopDown()
                    .filter { it.extension == "kt" && it.name.endsWith("ViewModel.kt") }
                    .toList()
            }
            .flatMap { file ->
                val lines = file.readLines()
                val functions = functionRanges(lines)
                lines.withIndex()
                    .filter { (_, line) -> BoundaryCall.containsMatchIn(line) }
                    .filter { (index, _) ->
                        val owner = functions.lastOrNull { it.start <= index } ?: return@filter true
                        !owner.isSuspend &&
                            !isInsideAsyncBlock(lines, owner.start, index) &&
                            owner.name !in BoundaryWhitelist
                    }
                    .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
            }

        assertEquals(
            "handler 不得同步触碰边界（出站一律走 EffectRunner；读取走协程 + Internal 回流，方案 D3）：\n" +
                offenders.joinToString("\n"),
            0,
            offenders.size,
        )
    }

    /** View 层纯净度：设置页的屏幕不得直接调用平台 API（语言选择曾是唯一一处，已改为 VM 承接）。 */
    @Test
    fun `settings screens do not call the platform`() {
        val offenders = settingsScreensDir.listFiles()
            .orEmpty()
            .filter { it.extension == "kt" }
            .flatMap { file -> file.readLines().map { file.name to it } }
            .filter { (_, line) ->
                line.contains("AppCompatDelegate") ||
                    line.contains("setApplicationLocales") ||
                    line.contains("getApplicationLocales") ||
                    line.contains("LocaleListCompat")
            }

        assertEquals(
            "View 层不得直接调用平台 API（语言/主题之类经仓库 + ViewModel）：" +
                offenders.joinToString { "${it.first}: ${it.second.trim()}" },
            0,
            offenders.size,
        )
    }

    /**
     * 管道逃逸：任何 ViewModel 的状态写入都不得落在 `launch` / `withContext` / `async` 块里。
     *
     * 这条是"不对称"的病根 —— 同一个意图的同步部分在 `handleAction` 里守着约定、异步部分顺手写一下，
     * 守卫就形同虚设。异步结果必须先做成 action 回流（`Internal.*`），由 handler 同步落。
     */
    @Test
    fun `no view model writes state from an asynchronous block`() {
        val roots = listOf(
            "feature/main/impl/src/main/java",
            "feature/settings/impl/src/main/java",
            "feature/provider/impl/src/main/java",
        )
        val offenders = roots
            .flatMap { root ->
                repoFile(root).walkTopDown()
                    .filter { it.extension == "kt" && it.name.endsWith("ViewModel.kt") }
                    .toList()
            }
            .flatMap { file ->
                val lines = file.readLines()
                val functions = functionRanges(lines)
                lines.withIndex()
                    .filter { (_, line) -> StateWrite.containsMatchIn(line) }
                    .filter { (index, _) ->
                        val owner = functions.lastOrNull { it.start <= index } ?: return@filter false
                        isInsideAsyncBlock(lines, owner.start, index)
                    }
                    .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
            }

        assertEquals(
            "状态写入不得落在协程块里（异步结果要先以 action 回流）：\n" +
                offenders.joinToString("\n"),
            0,
            offenders.size,
        )
    }

    /** 全局可观察状态：View 层不得声明顶层 `mutableStateMapOf` —— 状态要住在状态容器里。 */
    @Test
    fun `no view file declares observable state at the top level`() {
        val offenders = listOf(
            "feature/main/impl/src/main/java",
            "feature/settings/impl/src/main/java",
        )
            .flatMap { root -> repoFile(root).walkTopDown().filter { it.extension == "kt" }.toList() }
            .flatMap { file -> file.readLines().map { file.name to it } }
            .filter { (_, line) -> TopLevelObservable.containsMatchIn(line) }

        assertEquals(
            "View 层不得声明顶层可观察状态（应放进 ChatState / ViewModel 作用域）：" +
                offenders.joinToString { "${it.first}: ${it.second.trim()}" },
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
     *
     * `init {` 也切成一个区间（名字记作 "init"）：否则类体的订阅装配代码会被误判进它上面那个
     * 函数，R11 会因此误伤。
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
            } ?: if (line.trimStart().startsWith("init {")) {
                FunctionRange(name = "init", start = index, end = lines.lastIndex, isSuspend = false)
            } else {
                null
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

        /** 顶层可观察状态：`private val x = mutableStateMapOf(...)` 这类声明。 */
        val TopLevelObservable = Regex("""^\s*(?:private\s+|internal\s+)?val\s+\w+\s*=\s*mutableState(Map|List)Of""")

        /** 状态写入点：各 VM 自己的 `updateState` / `updateEditor` / 直接摸 `mutableStateFlow`。 */
        val StateWrite = Regex("""(updateState|updateEditor|updateModelEditor|mutableStateFlow\s*\.\s*update)\s*[({]""")

        /** ① 的配套：D1 投影覆写里的**只读**引用（combine/project 的输入、两个 override 声明行）。 */
        val D1ProjectionRead = Regex(
            """override val state(?:Flow)?\b|combine\(mutableStateFlow|project\(mutableStateFlow"""
        )

        /** R9 的配套：`copy(conversation = …`（复数 `conversations` 不匹配；注释行在测试里另滤）。 */
        val R9CopyConversation = Regex("""copy\(conversation\s*=""")

        /** 方案 §4.3 的同步助手白名单：这些函数由 `handleAction` 同步调用（或本身就是 handler）。 */
        val SynchronousHelpers = setOf(
            "handleAction",
            // 每会话状态（照 ZCode 的 conversation projection）的唯一写入口：与 handleAction 同一条
            // 规矩 —— 只由同步的 handler 调用，自己不做任何异步。
            "updateChat",
            "releaseConversation",
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
            "handleToolRowToggled",
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

        /** R8 的配套：属性声明行（`private var x = …`）不是写点。 */
        val PropertyDeclaration = Regex("""^\s*(?:(?:private|protected|internal|public)\s+)*va[rl]\s""")

        /**
         * R8 的影子写点正则。只匹配**写**：索引写要求 `] =`（排除 `] ==` 这类读比较），
         * 等号带 `(?!=)` 排除 `==`。原子字段（`pendingParse`/`parseQueued`）与命令通道
         * （`commands`）为跨线程设计，不在此列。
         */
        val ShadowWrite = Regex(
            """attachedKeys\[[^\]]+\]\s*=(?!=)|attachedKeys\.(?:remove|clear|put)\b|""" +
                """conversationEpoch\s*(?:\+\+|--)|conversationEpoch\s*=(?!=)|""" +
                """pendingContextWindow\s*=(?!=)|pendingReasoningEffort\s*=(?!=)|""" +
                """pendingActiveModel\s*=(?!=)|""" +
                """languagePreferenceSeen\s*=(?!=)|""" +
                """chats\.(?:update|drop|rekey)\s*\(|""" +
                """turns\[[^\]]+\]\s*=(?!=)|turns\.(?:remove|clear|put)\b|""" +
                """\.(?:chatKey|streamingMessageId|parsedLength|job)\s*=(?!=)|""" +
                """assistantIds\s*(?:\+=|-=)|assistantIds\.(?:remove|add)\b"""
        )

        /**
         * R11 的边界调用正则：agent 门面、各仓库、平台语言仓库、字体缓存、探测器。
         * 注意这是文本级护栏：改掉 receiver 命名就绕得过它 —— 命名约定本身就是规矩的一部分。
         */
        val BoundaryCall = Regex(
            """(?:agentChat|conversationStore|providerRepository|userPreferencesRepository|""" +
                """customFontRepository|customFontFamilyCache|appLanguageRepository|providerProbe)\s*\."""
        )

        /** R11 白名单：例外类型见测试上的注释。 */
        val BoundaryWhitelist = setOf(
            "init",
            "releaseConversation",
            "onCleared",
            "resolveContentFont",
            "resolveFontPreviews",
            "agentSettings",
        )

        /**
         * R8 白名单 = 同步 handler/助手（同 [SynchronousHelpers]）加上四个"本身同步、由 handler
         * 直接调用"的注册表管理者与拆毁钩子。新影子状态字段或新写点出现时，先想明白为什么它
         * 配得上这份名单，再扩充。
         */
        val ShadowWriteHelpers = SynchronousHelpers + setOf(
            "beginTurn",
            "launchTurn",
            "rekeyTurns",
            "onCleared",
            "closeStreamParser",
        )
    }
}
