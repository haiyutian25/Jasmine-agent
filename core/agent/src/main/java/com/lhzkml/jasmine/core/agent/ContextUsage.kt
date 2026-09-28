package com.lhzkml.jasmine.core.agent

/**
 * 一次请求之后，上下文窗口的样子。
 *
 * 数值是 provider 自己报的 usage，不是本地估算；核心在每轮回答结束后把它和这一轮请求的
 * 构成一起发出来。会话重开、或换过模型之后，要等下一轮回答回来才有新的值。
 */
data class ContextUsage(
    /** 最近一次请求放进窗口的 token（核心 `TokenUsage::tokens_in_context_window` 的口径）。 */
    val usedTokens: Long,
    /** 这条会话累计消耗的 token。 */
    val totalTokens: Long,
    /** 平台配置的上下文窗口；`null` 表示没配过，此时算不出占比。 */
    val modelContextWindow: Long?,
    /** 这一次请求的 token 去向，按来源分。 */
    val breakdown: List<ContextUsageBucket>,
)

/** [ContextUsage.breakdown] 里的一项。 */
data class ContextUsageBucket(val source: ContextUsageSource, val tokens: Long)

/** 请求里 token 的去向，与核心的 `ContextUsageSource` 一一对应。 */
enum class ContextUsageSource {
    /** 每次请求都带的系统指令。 */
    SYSTEM_PROMPT,

    /** 提供给模型的工具 schema。 */
    SYSTEM_TOOL_SCHEMAS,

    /** 技能贡献的部分；核心还没有这个来源，暂时恒为 0。 */
    SKILLS,

    /** MCP server 贡献的工具 schema。 */
    MCP_TOOL_SCHEMAS,

    /** 对话本身：用户和模型说过的话。 */
    MESSAGES,
}
