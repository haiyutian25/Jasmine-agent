package com.lhzkml.jasmine.core.data.model

/**
 * Domain model for persisted UI preferences (theme, typography, language, color mode,
 * font scale, active custom font, active model provider + model).
 *
 * [activeProviderId] / [activeModelId] point at the provider/model the chat uses;
 * both empty means "nothing selected yet", which the chat UI turns into a prompt.
 *
 * Note: navigation chrome state (currentTab / isSidebarOpen) is NOT
 * part of preferences — it is session-transient UI position and always starts fresh
 * after process death.
 */
data class UserPreferences(
    val themeId: String,
    val typographyChoice: String,
    val colorMode: String,
    val fontScale: Float,
    val activeCustomFontId: String,
    val activeProviderId: String,
    val activeModelId: String,
    /**
     * 模型回复语言（见 [AgentOutputLanguage]）；**全局**一个值，不做会话级。默认跟随输入。
     *
     * 它只是一个**值**：核心按它拼那条输出语言规则（`rust/core/src/agent_settings.rs`），
     * 所以在下一次附着会话时生效。
     */
    val agentOutputLanguage: String = AgentOutputLanguage.FOLLOW_INPUT,
) {
    companion object {
        val DEFAULT = UserPreferences(
            themeId = "editorial-light",
            typographyChoice = "EDITORIAL",
            colorMode = ColorMode.SYSTEM.id,
            fontScale = 1.0f,
            activeCustomFontId = "",
            activeProviderId = "",
            activeModelId = "",
            agentOutputLanguage = AgentOutputLanguage.FOLLOW_INPUT,
        )
    }
}
