package com.lhzkml.jasmine.core.agent

/**
 * 整个 App 的用量，由核心从每个会话文件里读回来（不是界面自己算的）。
 *
 * [totalTokens] 是**全部时间**的累计；[days]、[models] 与两个连续天数都只覆盖**本月** ——
 * 每个月翻篇时，上个月的用量记录会被核心从会话文件里删掉，只有这条累计总数会一直留着。
 */
data class AppUsage(
    /** 至今一共花掉的 token。 */
    val totalTokens: Long,
    /** 连续有消耗的天数：从今天往前数，今天还没消耗就从昨天数起。 */
    val currentStreakDays: Int,
    /** 历史上最长的那段连续天数。 */
    val longestStreakDays: Int,
    /** 有消耗的每一天。 */
    val days: List<UsageDay>,
    /** 窗口内每个模型的用量，花得多的在前（不分供应商）。 */
    val models: List<ModelUsage>,
) {
    /** 没有任何记录时的形状，省得界面到处判空。 */
    companion object {
        val Empty = AppUsage(
            totalTokens = 0,
            currentStreakDays = 0,
            longestStreakDays = 0,
            days = emptyList(),
            models = emptyList(),
        )
    }
}

/** 某一天花掉的 token。 */
data class UsageDay(
    /** 本地日历日，`YYYY-MM-DD`。 */
    val date: String,
    val tokens: Long,
)

/** 某个模型花掉的 token（跨全部对话累计）。 */
data class ModelUsage(
    val modelId: String,
    val tokens: Long,
)
