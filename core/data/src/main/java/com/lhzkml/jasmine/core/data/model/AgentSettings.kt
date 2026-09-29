package com.lhzkml.jasmine.core.data.model

/**
 * Agent 的行为设置：**界面只给值，规则在核心**（`rust/core/src/agent_settings.rs`）。
 *
 * 眼下两项都与回复语言有关：
 * - [outputLanguage]：模型回复语言的取值，见 [AgentOutputLanguage]；
 * - [appLanguage]：界面当前的语言（BCP-47，如 `zh-CN` / `zh-TW`）—— 只有"跟随应用语言"那档用得上。
 *
 * 往后再加别的 Agent 控制项，也放这里：它一路原样传到核心（FFI 上同名一条记录）。
 */
data class AgentSettings(
    val outputLanguage: String = AgentOutputLanguage.FOLLOW_INPUT,
    val appLanguage: String = "",
)
