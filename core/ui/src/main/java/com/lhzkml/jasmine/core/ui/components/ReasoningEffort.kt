package com.lhzkml.jasmine.core.ui.components

import androidx.annotation.StringRes
import com.lhzkml.jasmine.core.ui.R

/**
 * 推理强度的一档：线上取值 + 它的显示文案。
 *
 * 取值是**发到模型那一侧的原词**（`low` / `high` / …），文案只是界面用。
 */
data class ReasoningEffortOption(
    val value: String,
    @StringRes val labelRes: Int,
)

/**
 * 「推理强度」的档位表 —— **全应用唯一一处**（值 → 文案），顺序就是选择器里的顺序。
 *
 * 这份表是**上游两家模型目录的并集**：`none` / `low` / `high` / `max`（DeepSeek 那套四档）∪
 * `low` … `max` 加一个界面档 `ultra`（OpenAI 目录）。两家都不声明的档这里就没有 —— 所以没有 `minimal`。
 *
 * 以前它在聊天输入行与供应商页各存一份（两份逐字相同，靠注释提醒"加档位时两处一起改"）——
 * 那是必然会漂移的结构。现在值、文案、过滤规则都在这里，那两处只调用。
 *
 * 与核心的对应关系：取值必须和 `rust/model-provider-info` 里 `presets::wire_level` 认得的词一致；
 * [UI_ONLY_ULTRA] 是**界面档**（线上没有这个词），发请求前核心会把它换成该模型支持的最强档。
 */
object ReasoningEffort {

    /** 未设置：请求里一个推理字段都不发。 */
    const val UNSET = ""

    /** 关闭：显式要求不思考。它表达的是"不思考"，不是"思考得很少"。 */
    const val OFF = "none"

    /** 界面档：线上没有这个词，由核心按模型换成支持的最强档。 */
    const val UI_ONLY_ULTRA = "ultra"

    /** 全部档位，顺序即面板顺序。 */
    val options: List<ReasoningEffortOption> = listOf(
        ReasoningEffortOption(UNSET, R.string.reasoning_effort_unset),
        ReasoningEffortOption(OFF, R.string.reasoning_effort_none),
        ReasoningEffortOption("low", R.string.reasoning_effort_low),
        ReasoningEffortOption("medium", R.string.reasoning_effort_medium),
        ReasoningEffortOption("high", R.string.reasoning_effort_high),
        ReasoningEffortOption("xhigh", R.string.reasoning_effort_xhigh),
        ReasoningEffortOption("max", R.string.reasoning_effort_max),
        ReasoningEffortOption(UI_ONLY_ULTRA, R.string.reasoning_effort_ultra),
    )

    /**
     * [value] 的文案资源；表里没有这个取值时返回 null。
     *
     * 返回 null 而不是"兜底成未设置"是有意的：兜底会把一个不认识的档**显示成另一个档**，
     * 调用方应当据此忽略它或原样显示取值，而不是悄悄换掉它的语义。
     */
    @StringRes
    fun labelRes(value: String): Int? = options.firstOrNull { it.value == value }?.labelRes

    /**
     * 这次可选的档位：**目录**给了这个模型哪几档就只列哪几档（没给 = 不限制，列全部）。
     *
     * 「未设置」不是档位、永远在（用户得能取消选择）；当前值即使不在目录里也留着 ——
     * 否则面板里看不到自己现在是什么档。对应 codex 的 `ModelInfo.supported_reasoning_levels`：
     * 它按后端模型目录过滤选择器，我们按核心目录传过来的那份 `declared` 来。
     */
    fun optionsFor(declared: List<String>, current: String): List<ReasoningEffortOption> {
        val declaredOptions = if (declared.isEmpty()) {
            // 不限制：列全部**线上**档 —— 界面档（ultra）不列，它得先知道模型支持什么才换得成。
            options.filter { it.value != UI_ONLY_ULTRA }
        } else {
            options.filter { it.value in declared }
        }
        val withUnset = if (declaredOptions.any { it.value == UNSET }) {
            declaredOptions
        } else {
            listOf(options.first()) + declaredOptions
        }
        if (withUnset.any { it.value == current }) {
            return withUnset
        }
        val extra = options.firstOrNull { it.value == current } ?: return withUnset
        return withUnset + extra
    }
}
