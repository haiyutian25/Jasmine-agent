package com.lhzkml.jasmine.core.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.jasmine_ffi.AgentFailure
import uniffi.jasmine_protocol.ChatEvent as CoreChatEvent
import uniffi.jasmine_protocol.ContextUsageBreakdownItem as CoreBreakdownItem
import uniffi.jasmine_protocol.ContextUsageSource as CoreSource
import uniffi.jasmine_protocol.TokenUsage as CoreTokenUsage
import uniffi.jasmine_protocol.TokenUsageInfo as CoreTokenUsageInfo

/**
 * 跨边界的两张映射表（G6）。
 *
 * 这两张表都是"写错一个分支就有一类事件/一个说法静默失准"的地方，而它们只依赖生成绑定里的类型，
 * 不需要真的核心句柄 —— 所以纯 JVM 单测能钉住。
 *
 * 钉的是三件事：
 * 1. **九个事件变体**：核心报什么，这一侧就得是什么（漏一个 = 那类事件在界面上凭空消失）。
 * 2. **哪些事件是回合的结束**：`endsTurn` 判错会让"正在生成"永远不收尾，或者把用量当终态提前收尾。
 * 3. **失败分型**：界面据它决定"重试有没有意义"，认不出来就会给用户一句错的"能怎么办"。
 */
class RustAgentChatMappingTest {

    /** 核心的九个事件，逐个过一遍映射表。 */
    @Test
    fun `every core event lands on its own type`() {
        val pairs = listOf(
            CoreChatEvent.Text("正文分片") to ChatEvent.Text("正文分片"),
            CoreChatEvent.Reasoning("思考分片") to ChatEvent.Reasoning("思考分片"),
            CoreChatEvent.ToolCall("read_file", """{"path":"a.md"}""") to
                ChatEvent.ToolCall("read_file", """{"path":"a.md"}"""),
            CoreChatEvent.ToolResult("read_file", "ok") to ChatEvent.ToolResult("read_file", "ok"),
            CoreChatEvent.UserPromptRequested("继续吗", listOf("是", "否")) to
                ChatEvent.UserPromptRequested("继续吗", listOf("是", "否")),
            CoreChatEvent.Failed("boom") to ChatEvent.Failed("boom"),
            CoreChatEvent.Completed to ChatEvent.Completed,
            CoreChatEvent.Aborted(1_234u) to ChatEvent.Aborted(1_234L),
        )

        pairs.forEach { (core, expected) ->
            assertEquals("$core 应当映射成 $expected", expected, core.toChatEvent())
        }
    }

    /**
     * 回合中途的失败事件**没有分型**（核心的 `ChatEvent::Failed` 只带一句文本）。
     *
     * 单独钉住它，是因为它决定了界面的说法：分型能猜到就猜，猜不到就说"未知"，
     * 绝不能因为 `Failed` 的构造参数有默认值就把"没有分型"当成 UNKNOWN 之外的任何东西。
     */
    @Test
    fun `a mid-turn failure carries no kind`() {
        val mapped = CoreChatEvent.Failed("HTTP 503").toChatEvent() as ChatEvent.Failed

        assertEquals(ChatFailureKind.UNKNOWN, mapped.kind)
        assertEquals("HTTP 503", mapped.detail)
    }

    /** 用量带构成一起过来（构成是本地估算后按真值缩放的，见 MODULE_MAP §3.1）。 */
    @Test
    fun `usage comes across with its breakdown`() {
        val info = CoreTokenUsageInfo(
            totalTokenUsage = usage(total = 900),
            lastTokenUsage = usage(total = 300),
            modelContextWindow = 200_000L,
        )
        val breakdown = listOf(
            CoreBreakdownItem(CoreSource.SYSTEM_PROMPT, 100),
            CoreBreakdownItem(CoreSource.MCP_TOOL_SCHEMAS, 50),
            CoreBreakdownItem(CoreSource.MESSAGES, 150),
        )

        val mapped = CoreChatEvent.Usage(info, breakdown).toChatEvent()
        assertEquals(
            ChatEvent.Usage(
                ContextUsage(
                    // 「放进窗口的总量」取的是**最近一次**请求，不是累计。
                    usedTokens = 300,
                    totalTokens = 900,
                    modelContextWindow = 200_000L,
                    breakdown = listOf(
                        ContextUsageBucket(ContextUsageSource.SYSTEM_PROMPT, 100),
                        ContextUsageBucket(ContextUsageSource.MCP_TOOL_SCHEMAS, 50),
                        ContextUsageBucket(ContextUsageSource.MESSAGES, 150),
                    ),
                )
            ),
            mapped,
        )
    }

    /** 没配过窗口时是 null（界面据此不画占比）。 */
    @Test
    fun `an unset context window stays null`() {
        val info = CoreTokenUsageInfo(usage(total = 1), usage(total = 1), modelContextWindow = null)

        val mapped = CoreChatEvent.Usage(info, emptyList()).toChatEvent() as ChatEvent.Usage

        assertEquals(null, mapped.usage.modelContextWindow)
    }

    /**
     * 终态判定：正文 / 思考 / 工具调用与结果 / 用量都**不是**回合结束。
     *
     * 用量尤其关键：它随每一次采样轮都到，把它当终态会让界面提前收起"正在生成"。
     * 而 `UserPromptRequested` 反过来 —— 提问之后核心就不再产事件了，不收尾收集方会一直挂。
     */
    @Test
    fun `only the end-of-turn events end a turn`() {
        val endings = mapOf(
            ChatEvent.Text("x") to false,
            ChatEvent.Reasoning("x") to false,
            ChatEvent.ToolCall("t", "{}") to false,
            ChatEvent.ToolResult("t", "r") to false,
            ChatEvent.Usage(
                ContextUsage(usedTokens = 1, totalTokens = 1, modelContextWindow = null, breakdown = emptyList())
            ) to false,
            ChatEvent.Completed to true,
            ChatEvent.Aborted(1L) to true,
            ChatEvent.Failed("x") to true,
            ChatEvent.UserPromptRequested("q", emptyList()) to true,
        )

        assertEquals("九个变体都要在这张表里", 9, endings.size)
        endings.forEach { (event, expected) ->
            assertEquals("$event 的终态判定", expected, event.endsTurn())
        }
    }

    /** 核心跨边界报的四种失败，各有各的分型。 */
    @Test
    fun `every failure kind the core can report survives the boundary`() {
        assertEquals(ChatFailureKind.NO_SESSION, AgentFailure.NoSession("没有附着").toKind())
        assertEquals(ChatFailureKind.TRANSPORT, AgentFailure.Transport("连不上").toKind())
        assertEquals(ChatFailureKind.TRANSCRIPT, AgentFailure.Transcript("读取失败").toKind())
        assertEquals(ChatFailureKind.INTERNAL, AgentFailure.Internal("中毒").toKind())
    }

    /**
     * 本地抛的 `IllegalStateException`（会话没附着时 `AgentChat.send` 的契约）算**调用时序**问题，
     * 不是网络问题 —— 认不出来就会建议用户"检查网络"，那是错的。
     */
    @Test
    fun `a local contract violation is an internal problem, not a network one`() {
        assertEquals(ChatFailureKind.INTERNAL, IllegalStateException("尚未附着会话").toKind())
    }

    /** 认不出来的异常**不猜**：留 UNKNOWN，让界面说"未知"而不是编一个原因。 */
    @Test
    fun `an unrecognised throwable is not guessed at`() {
        assertEquals(ChatFailureKind.UNKNOWN, RuntimeException("不知道是什么").toKind())
    }

    private fun usage(total: Long) = CoreTokenUsage(
        inputTokens = total,
        cachedInputTokens = 0,
        cacheWriteInputTokens = 0,
        outputTokens = 0,
        reasoningOutputTokens = 0,
        totalTokens = total,
    )
}
