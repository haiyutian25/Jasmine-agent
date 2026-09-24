package com.lhzkml.jasmine.feature.main.impl

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.core.data.model.ColorMode
import com.lhzkml.jasmine.core.data.model.InstalledFont
import com.lhzkml.jasmine.core.data.model.PresetFont
import com.lhzkml.jasmine.core.data.model.UserPreferences
import com.lhzkml.jasmine.core.data.repository.CustomFontRepository
import com.lhzkml.jasmine.core.data.repository.UserPreferencesRepository
import com.lhzkml.jasmine.core.ui.base.BaseViewModel
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.ThemeResolver
import com.lhzkml.jasmine.core.ui.components.NavigationTab
import com.lhzkml.jasmine.feature.main.impl.fonts.CustomFontFamilyCache
import com.lhzkml.jasmine.feature.settings.impl.screens.AppTypographyChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single immutable UI state for the main feature (UDF).
 *
 * [theme] and [activeContentFont] are derived from the raw preference fields by the
 * ViewModel on every state update; all other fields are set directly by actions.
 *
 * Screen-to-screen navigation (settings flow) is NOT part of this state — it
 * lives on the Navigation 3 back stack, which handles system back, predictive
 * back and process-death restore.
 */
data class MainState(
    // Raw preference inputs
    val themeId: String,
    val colorMode: ColorMode,
    val isSystemDark: Boolean,
    // Derived
    val theme: CssVariables,
    val activeContentFont: FontFamily,
    // Navigation / chrome
    val currentTab: NavigationTab,
    val isSidebarOpen: Boolean,
    // Typography
    val typographyChoice: AppTypographyChoice,
    val fontScale: Float,
    val activeCustomFontId: String,
    val installedFonts: List<InstalledFont>,
    val downloadProgress: Map<String, Float>,
)

/**
 * One-time events emitted by [MainViewModel]; consumed exactly once by the UI.
 */
sealed interface MainEvent {
    /** Show a transient toast carrying a string resource, formatted with [formatArgs] when present. */
    data class ShowToast(@StringRes val messageRes: Int, val formatArgs: List<Any> = emptyList()) : MainEvent
}

/**
 * Actions sent from the UI to [MainViewModel] via [BaseViewModel.trySendAction].
 */
sealed interface MainAction {

    data class TabSelected(val tab: NavigationTab) : MainAction
    data object SidebarOpened : MainAction
    data object SidebarClosed : MainAction
    data object SidebarToggled : MainAction

    data class ThemeSelected(val palette: CssVariables) : MainAction
    data class ColorModeChanged(val mode: ColorMode) : MainAction
    data class SystemDarkModeChanged(val isDark: Boolean) : MainAction

    data class TypographySelected(val choice: AppTypographyChoice) : MainAction
    data class CustomFontSelected(val fontId: String) : MainAction
    data class FontDownloadClicked(val preset: PresetFont) : MainAction
    data class FontDeleteClicked(val fontId: String) : MainAction
    data class FontImportRequested(val uri: Uri, val fallbackName: String) : MainAction
    data class FontScaleSaved(val scale: Float) : MainAction

    /**
     * Internal actions: results of asynchronous work posted back onto the action
     * channel so that all state mutations stay synchronous inside [handleAction].
     */
    sealed interface Internal : MainAction {
        data class PreferencesReceived(val preferences: UserPreferences) : Internal
        data class InstalledFontsReceived(val fonts: List<InstalledFont>) : Internal
        data class DownloadProgressReceived(val progress: Map<String, Float>) : Internal
        data class FontDownloadCompleted(val success: Boolean) : Internal
        data class FontImportCompleted(val fontId: String?) : Internal
    }
}

/**
 * Resolves the effective [CssVariables] from the raw theme inputs.
 */
private fun resolveTheme(
    themeId: String,
    colorMode: ColorMode,
    isSystemDark: Boolean,
): CssVariables {
    val family = ThemeResolver.familyOf(themeId)
    val effectiveIsDark = when (colorMode) {
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
        ColorMode.SYSTEM -> isSystemDark
    }
    return ThemeResolver.resolveFamily(family.orEmpty(), effectiveIsDark)
}

/**
 * Single ViewModel backing the main feature (MVVM + unidirectional data flow).
 *
 * The UI renders [stateFlow] and sends every user intent as a [MainAction];
 * one-shot feedback (toasts) is delivered through [eventFlow]. State mutations
 * happen synchronously inside [handleAction]; asynchronous work (persistence,
 * font downloads) posts follow-up [MainAction.Internal] actions.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val customFontRepository: CustomFontRepository,
    private val customFontFamilyCache: CustomFontFamilyCache,
) : BaseViewModel<MainState, MainEvent, MainAction>(
    initialState = run {
        val isSystemDark =
            (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        MainState(
            themeId = UserPreferences.DEFAULT.themeId,
            colorMode = ColorMode.fromId(UserPreferences.DEFAULT.colorMode),
            isSystemDark = isSystemDark,
            theme = resolveTheme(
                themeId = UserPreferences.DEFAULT.themeId,
                colorMode = ColorMode.fromId(UserPreferences.DEFAULT.colorMode),
                isSystemDark = isSystemDark,
            ),
            activeContentFont = AppTypographyChoice.EDITORIAL.font,
            currentTab = NavigationTab.CHAT,
            isSidebarOpen = false,
            typographyChoice = AppTypographyChoice.EDITORIAL,
            fontScale = UserPreferences.DEFAULT.fontScale,
            activeCustomFontId = UserPreferences.DEFAULT.activeCustomFontId,
            installedFonts = emptyList(),
            downloadProgress = emptyMap(),
        )
    },
) {

    init {
        userPreferencesRepository
            .preferencesStateFlow
            .map { MainAction.Internal.PreferencesReceived(it) }
            .onEach(::sendAction)
            .launchIn(viewModelScope)

        customFontRepository
            .installedVersion
            .map { MainAction.Internal.InstalledFontsReceived(customFontRepository.installedFonts()) }
            .onEach(::sendAction)
            .launchIn(viewModelScope)

        customFontRepository
            .downloadProgress
            .map { MainAction.Internal.DownloadProgressReceived(it) }
            .onEach(::sendAction)
            .launchIn(viewModelScope)
    }

    override fun handleAction(action: MainAction) {
        when (action) {
            is MainAction.TabSelected -> updateState { copy(currentTab = action.tab) }
            MainAction.SidebarOpened -> updateState { copy(isSidebarOpen = true) }
            MainAction.SidebarClosed -> updateState { copy(isSidebarOpen = false) }
            MainAction.SidebarToggled -> updateState { copy(isSidebarOpen = !isSidebarOpen) }

            is MainAction.ThemeSelected -> handleThemeSelected(action)
            is MainAction.ColorModeChanged -> handleColorModeChanged(action)
            is MainAction.SystemDarkModeChanged -> {
                if (state.isSystemDark != action.isDark) {
                    updateState { copy(isSystemDark = action.isDark) }
                }
            }

            is MainAction.TypographySelected -> handleTypographySelected(action)
            is MainAction.CustomFontSelected -> handleCustomFontSelected(action)
            is MainAction.FontDownloadClicked -> handleFontDownloadClicked(action)
            is MainAction.FontDeleteClicked -> handleFontDeleteClicked(action)
            is MainAction.FontImportRequested -> handleFontImportRequested(action)
            is MainAction.FontScaleSaved -> handleFontScaleSaved(action)

            is MainAction.Internal.PreferencesReceived -> handlePreferencesReceived(action)
            is MainAction.Internal.InstalledFontsReceived -> {
                updateState { copy(installedFonts = action.fonts) }
            }
            is MainAction.Internal.DownloadProgressReceived -> {
                updateState { copy(downloadProgress = action.progress) }
            }
            is MainAction.Internal.FontDownloadCompleted -> handleFontDownloadCompleted(action)
            is MainAction.Internal.FontImportCompleted -> handleFontImportCompleted(action)
        }
    }

    // region Action handlers

    private fun handleThemeSelected(action: MainAction.ThemeSelected) {
        updateState { copy(themeId = action.palette.themeId) }
        viewModelScope.launch { userPreferencesRepository.updateTheme(action.palette.themeId) }
    }

    private fun handleColorModeChanged(action: MainAction.ColorModeChanged) {
        updateState { copy(colorMode = action.mode) }
        viewModelScope.launch { userPreferencesRepository.updateColorMode(action.mode.id) }
    }

    private fun handleTypographySelected(action: MainAction.TypographySelected) {
        // Selecting a system engine clears any custom-font override.
        updateState { copy(typographyChoice = action.choice, activeCustomFontId = "") }
        viewModelScope.launch {
            userPreferencesRepository.updateTypography(action.choice.name)
            userPreferencesRepository.updateActiveCustomFont("")
        }
    }

    private fun handleCustomFontSelected(action: MainAction.CustomFontSelected) {
        updateState { copy(activeCustomFontId = action.fontId) }
        viewModelScope.launch { userPreferencesRepository.updateActiveCustomFont(action.fontId) }
    }

    private fun handleFontDownloadClicked(action: MainAction.FontDownloadClicked) {
        viewModelScope.launch {
            val success = customFontRepository.downloadPreset(action.preset)
            sendAction(MainAction.Internal.FontDownloadCompleted(success))
        }
    }

    private fun handleFontDeleteClicked(action: MainAction.FontDeleteClicked) {
        if (state.activeCustomFontId == action.fontId) {
            updateState { copy(activeCustomFontId = "") }
            viewModelScope.launch { userPreferencesRepository.updateActiveCustomFont("") }
        }
        customFontFamilyCache.evict(action.fontId)
        viewModelScope.launch { customFontRepository.deleteFont(action.fontId) }
        sendEvent(MainEvent.ShowToast(R.string.font_deleted_toast))
    }

    private fun handleFontImportRequested(action: MainAction.FontImportRequested) {
        viewModelScope.launch {
            val fontId = customFontRepository.importFont(action.uri, action.fallbackName)
            sendAction(MainAction.Internal.FontImportCompleted(fontId))
        }
    }

    private fun handleFontScaleSaved(action: MainAction.FontScaleSaved) {
        updateState { copy(fontScale = action.scale) }
        viewModelScope.launch { userPreferencesRepository.updateFontScale(action.scale) }
        sendEvent(MainEvent.ShowToast(R.string.font_size_saved_toast))
    }

    // endregion

    // region Internal action handlers

    private fun handlePreferencesReceived(action: MainAction.Internal.PreferencesReceived) {
        val prefs = action.preferences
        updateState {
            copy(
                themeId = prefs.themeId,
                colorMode = ColorMode.fromId(prefs.colorMode),
                typographyChoice = AppTypographyChoice.entries
                    .firstOrNull { it.name == prefs.typographyChoice }
                    ?: AppTypographyChoice.EDITORIAL,
                fontScale = prefs.fontScale,
                activeCustomFontId = prefs.activeCustomFontId,
                // Navigation chrome state (currentTab / isSidebarOpen) is
                // intentionally NOT restored here — it is session-transient and
                // always starts fresh (default tab) after process death.
            )
        }
    }

    private fun handleFontDownloadCompleted(action: MainAction.Internal.FontDownloadCompleted) {
        sendEvent(
            MainEvent.ShowToast(
                if (action.success) {
                    R.string.font_download_complete_toast
                } else {
                    R.string.font_download_failed_toast
                },
            ),
        )
    }

    private fun handleFontImportCompleted(action: MainAction.Internal.FontImportCompleted) {
        val fontId = action.fontId
        if (fontId != null) {
            updateState { copy(activeCustomFontId = fontId) }
            viewModelScope.launch { userPreferencesRepository.updateActiveCustomFont(fontId) }
            sendEvent(MainEvent.ShowToast(R.string.font_imported_toast))
        } else {
            sendEvent(MainEvent.ShowToast(R.string.font_import_failed_toast))
        }
    }

    // endregion

    /**
     * Updates [mutableStateFlow] and re-derives the derived fields ([MainState.theme],
     * [MainState.activeContentFont]) so they always stay consistent with the raw inputs.
     *
     * Note: navigation chrome state (currentTab / isSidebarOpen) is deliberately NOT
     * persisted to DataStore — it is transient per-session UI position. The settings
     * flow is not tracked here at all: it lives on the Navigation 3 back stack, which
     * is serialized and restored across process death by the navigation library.
     */
    private inline fun updateState(block: MainState.() -> MainState) {
        mutableStateFlow.update { current ->
            val next = current.block()
            next.copy(
                theme = resolveTheme(
                    themeId = next.themeId,
                    colorMode = next.colorMode,
                    isSystemDark = next.isSystemDark,
                ),
                activeContentFont = customFontFamilyCache.fontFamilyFor(next.activeCustomFontId)
                    ?: next.typographyChoice.font,
            )
        }
    }

    /** Resolves an installed custom font to a [FontFamily] for UI previews. */
    fun customFontFamily(fontId: String): FontFamily? = customFontFamilyCache.fontFamilyFor(fontId)
}
