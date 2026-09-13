package com.lhzkml.jasmine.feature.greeting.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation contract of the greeting feature.
 *
 * Other modules only ever depend on this sealed hierarchy (never on the
 * implementation module) when they need to navigate into the feature.
 */
@Serializable
sealed interface GreetingNavKey : NavKey {

    /** Typewriter boot splash. */
    @Serializable
    data object Splash : GreetingNavKey

    /** Main canvas experience (sidebar + 4-tab scaffold). */
    @Serializable
    data object Main : GreetingNavKey

    /** Settings menu list (entry of the settings flow). */
    @Serializable
    data object SettingsMenu : GreetingNavKey

    /** Appearance settings (color mode + palette presets). */
    @Serializable
    data object AppearanceSettings : GreetingNavKey

    /** Language settings. */
    @Serializable
    data object LanguageSettings : GreetingNavKey

    /** Font settings (typography engine + custom fonts). */
    @Serializable
    data object FontSettings : GreetingNavKey

    /** Font-size adjustment. */
    @Serializable
    data object FontSizeSettings : GreetingNavKey
}
