package com.lhzkml.jasmine.feature.main.impl

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.lhzkml.jasmine.core.navigation.rememberAppNavigator
import com.lhzkml.jasmine.core.ui.base.util.EventsEffect
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.main.api.MainNavKey
import com.lhzkml.jasmine.feature.main.impl.components.ProductionTopNavBar
import com.lhzkml.jasmine.feature.main.impl.screens.SplashScreen
import com.lhzkml.jasmine.feature.settings.api.SettingsNavKey
import com.lhzkml.jasmine.feature.settings.impl.R as SettingsR
import com.lhzkml.jasmine.feature.settings.impl.screens.FontScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.FontSizeScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.LanguageScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.SettingsMenuScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.SettingsScreen

/**
 * Navigation 3 host of the main feature.
 *
 * The back stack is owned by [rememberAppNavigator]; keys come from the
 * features' public contracts ([MainNavKey] and the settings module's
 * [SettingsNavKey]) so the app main never needs to know about internal
 * destinations. The whole settings flow (menu ->
 * appearance / font / language -> font size) lives on this back stack, so the
 * system back gesture, predictive back and process-death restore are all
 * handled by Navigation 3 — no in-state navigation simulation.
 *
 * [state] is hoisted from the activity (the single stateFlow subscription
 * lives there); toast events and the CSS inspector sheet are hosted here,
 * above every destination, so they stay available on settings pages too.
 */
@Composable
fun MainNavHost(
    viewModel: MainViewModel,
    state: MainState,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberAppNavigator(MainNavKey.Splash)

    // Consume one-time UI events (toasts) exactly once, lifecycle-aware.
    val eventContext = LocalContext.current
    EventsEffect(viewModel = viewModel) { event ->
        when (event) {
            is MainEvent.ShowToast -> {
                val message = if (event.formatArgs.isEmpty()) {
                    eventContext.getString(event.messageRes)
                } else {
                    eventContext.getString(event.messageRes, *event.formatArgs.toTypedArray())
                }
                Toast.makeText(eventContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavDisplay(
            backStack = navigator.navigationState,
            onBack = { navigator.goBack() },
            entryProvider = entryProvider {
                entry<MainNavKey.Splash> {
                    SplashScreen(
                        currentTheme = state.theme,
                        onFinish = { navigator.replace(MainNavKey.Main) }
                    )
                }
                entry<MainNavKey.Main> {
                    MainScreen(
                        state = state,
                        onAction = viewModel::trySendAction,
                        onOpenSettings = {
                            viewModel.trySendAction(MainAction.SidebarClosed)
                            navigator.navigate(SettingsNavKey.SettingsMenu)
                        },
                    )
                }
                entry<SettingsNavKey.SettingsMenu> {
                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(R.string.settings_page_title),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        SettingsMenuScreen(
                            currentTheme = state.theme,
                            onOpenAppearance = { navigator.navigate(SettingsNavKey.AppearanceSettings) },
                            onOpenFont = { navigator.navigate(SettingsNavKey.FontSettings) },
                            onOpenLanguage = { navigator.navigate(SettingsNavKey.LanguageSettings) },
                            modifier = contentModifier,
                        )
                    }
                }
                entry<SettingsNavKey.AppearanceSettings> {
                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(SettingsR.string.settings_menu_appearance_title),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        SettingsScreen(
                            currentTheme = state.theme,
                            onThemeChange = { viewModel.trySendAction(MainAction.ThemeSelected(it)) },
                            colorMode = state.colorMode,
                            onColorModeChange = { viewModel.trySendAction(MainAction.ColorModeChanged(it)) },
                            modifier = contentModifier,
                        )
                    }
                }
                entry<SettingsNavKey.LanguageSettings> {
                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(SettingsR.string.language_title),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        LanguageScreen(
                            currentTheme = state.theme,
                            modifier = contentModifier,
                        )
                    }
                }
                entry<SettingsNavKey.FontSettings> {
                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(SettingsR.string.settings_menu_font_title),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        FontScreen(
                            currentTheme = state.theme,
                            selectedTypography = state.typographyChoice,
                            onTypographyChange = { viewModel.trySendAction(MainAction.TypographySelected(it)) },
                            fontScale = state.fontScale,
                            onOpenFontSize = { navigator.navigate(SettingsNavKey.FontSizeSettings) },
                            installedFonts = state.installedFonts,
                            activeCustomFontId = state.activeCustomFontId,
                            downloadProgress = state.downloadProgress,
                            fontFamilyFor = viewModel::customFontFamily,
                            onSelectCustomFont = { viewModel.trySendAction(MainAction.CustomFontSelected(it)) },
                            onDeleteCustomFont = { viewModel.trySendAction(MainAction.FontDeleteClicked(it)) },
                            onDownloadFont = { viewModel.trySendAction(MainAction.FontDownloadClicked(it)) },
                            onImportFont = { uri, name ->
                                viewModel.trySendAction(MainAction.FontImportRequested(uri, name))
                            },
                            modifier = contentModifier,
                        )
                    }
                }
                entry<SettingsNavKey.FontSizeSettings> {
                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(SettingsR.string.settings_font_size_label),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        FontSizeScreen(
                            currentTheme = state.theme,
                            fontScale = state.fontScale,
                            onSave = { viewModel.trySendAction(MainAction.FontScaleSaved(it)) },
                            modifier = contentModifier,
                        )
                    }
                }
            }
        )
    }
}

/**
 * Shared chrome of the settings destinations: same background and top bar
 * (back button + centered title) as the main main, so a settings page reads
 * as a pushed page of the same surface.
 */
@Composable
private fun SettingsPage(
    currentTheme: CssVariables,
    title: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val animatedBg by animateColorAsState(
        targetValue = currentTheme.background,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "settings_page_bg"
    )
    Scaffold(
        containerColor = animatedBg,
        contentColor = currentTheme.foreground,
        topBar = {
            ProductionTopNavBar(
                currentTheme = currentTheme,
                pageTitle = title,
                onBack = onBack,
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}
