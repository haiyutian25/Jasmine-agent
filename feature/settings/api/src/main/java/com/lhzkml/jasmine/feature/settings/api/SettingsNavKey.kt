package com.lhzkml.jasmine.feature.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation contract of the settings feature.
 *
 * Other modules only ever depend on this sealed hierarchy (never on the
 * implementation module) when they need to navigate into the settings flow.
 */
@Serializable
sealed interface SettingsNavKey : NavKey {

    /** Settings menu list (entry of the settings flow). */
    @Serializable
    data object SettingsMenu : SettingsNavKey

    /** Appearance settings (color mode + palette presets). */
    @Serializable
    data object AppearanceSettings : SettingsNavKey

    /** Language settings. */
    @Serializable
    data object LanguageSettings : SettingsNavKey

    /** Font settings (typography engine + custom fonts). */
    @Serializable
    data object FontSettings : SettingsNavKey

    /** Font-size adjustment. */
    @Serializable
    data object FontSizeSettings : SettingsNavKey

    /** Usage statistics: what the app has spent, and on which providers and models. */
    @Serializable
    data object UsageStats : SettingsNavKey

    /**
     * **行为与权限** 设置页：模型侧的行为控制（眼下是"模型回复语言"，往后放工具权限、审核这些）。
     */
    @Serializable
    data object BehaviourAndPermissions : SettingsNavKey
}
