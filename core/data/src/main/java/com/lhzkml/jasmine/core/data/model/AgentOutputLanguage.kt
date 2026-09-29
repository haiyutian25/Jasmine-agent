package com.lhzkml.jasmine.core.data.model

/**
 * 「模型回复语言」的**取值**，照 qwen-code 那套（它的 `general.outputLanguage`）：
 *
 * - [FOLLOW_INPUT]：**跟随输入** —— 用户用什么语言问，就用什么语言答；
 * - [FOLLOW_APP]：**跟随应用语言** —— 界面显示什么语言，模型就用什么语言答；
 * - [ENGLISH] / [SIMPLIFIED_CHINESE] / [TRADITIONAL_CHINESE]：**固定某一种语言**。
 *
 * 这里只有**取值**（界面选择器要用）—— **规则不在这里**：那条"输出语言规则"由核心拼
 * （`rust/core/src/agent_settings.rs`，照 qwen-code 的 `output-language.md`），界面只把这个值传过去。
 * 所以这几个常量与核心的 `agent_settings::output_language` **同值**，改要一起改。
 *
 * 它是**全局**设置（不做会话级）：整机一个值，改完从下一次附着会话起生效。
 */
object AgentOutputLanguage {
    /** 跟随输入（默认）。 */
    const val FOLLOW_INPUT = "auto"

    /** 跟随应用显示的语言。 */
    const val FOLLOW_APP = "app"

    const val ENGLISH = "en"
    const val SIMPLIFIED_CHINESE = "zh-Hans"
    const val TRADITIONAL_CHINESE = "zh-Hant"

    /** 设置页里的顺序：两个"跟随"在前，三种固定语言在后。 */
    val CHOICES: List<String> = listOf(
        FOLLOW_INPUT,
        FOLLOW_APP,
        ENGLISH,
        SIMPLIFIED_CHINESE,
        TRADITIONAL_CHINESE,
    )
}
