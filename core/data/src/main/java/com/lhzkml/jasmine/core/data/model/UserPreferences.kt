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
        )
    }
}
