package com.lhzkml.jasmine.core.data.model

/**
 * Domain model for persisted UI preferences (theme, typography, language, color mode,
 * font scale, active custom font).
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
) {
    companion object {
        val DEFAULT = UserPreferences(
            themeId = "editorial-light",
            typographyChoice = "EDITORIAL",
            colorMode = ColorMode.SYSTEM.id,
            fontScale = 1.0f,
            activeCustomFontId = "",
        )
    }
}
