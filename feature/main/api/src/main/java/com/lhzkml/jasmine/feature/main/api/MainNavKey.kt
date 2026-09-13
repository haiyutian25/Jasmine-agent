package com.lhzkml.jasmine.feature.main.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation contract of the main feature.
 *
 * Other modules only ever depend on this sealed hierarchy (never on the
 * implementation module) when they need to navigate into the feature.
 */
@Serializable
sealed interface MainNavKey : NavKey {

    /** Typewriter boot splash. */
    @Serializable
    data object Splash : MainNavKey

    /** Main canvas experience (sidebar + canvas tab). */
    @Serializable
    data object Main : MainNavKey
}

// 设置流（SettingsMenu / AppearanceSettings / LanguageSettings / FontSettings /
// FontSizeSettings）已拆到 :feature:settings:api 的 [SettingsNavKey]，由外壳的
// NavDisplay 一并组装。
