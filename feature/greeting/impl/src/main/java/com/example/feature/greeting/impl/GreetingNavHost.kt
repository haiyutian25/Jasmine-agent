package com.example.feature.greeting.impl

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
import com.example.core.navigation.rememberAppNavigator
import com.example.core.ui.base.util.EventsEffect
import com.example.core.ui.theme.CssVariables
import com.example.feature.greeting.api.GreetingNavKey
import com.example.feature.greeting.impl.components.ProductionTopNavBar
import com.example.feature.greeting.impl.screens.FontScreen
import com.example.feature.greeting.impl.screens.FontSizeScreen
import com.example.feature.greeting.impl.screens.LanguageScreen
import com.example.feature.greeting.impl.screens.SettingsMenuScreen
import com.example.feature.greeting.impl.screens.SettingsScreen
import com.example.feature.greeting.impl.screens.SplashScreen

/**
 * Navigation 3 host of the greeting feature.
 *
 * The back stack is owned by [rememberAppNavigator]; keys come from the
 * feature's public contract ([GreetingNavKey]) so the app shell never needs
 * to know about internal destinations. The whole settings flow (menu ->
 * appearance / font / language -> font size) lives on this back stack, so the
 * system back gesture, predictive back and process-death restore are all
 * handled by Navigation 3 — no in-state navigation simulation.
 *
 * [state] is hoisted from the activity (the single stateFlow subscription
 * lives there); toast events and the CSS inspector sheet are hosted here,
 * above every destination, so they stay available on settings pages too.
 */
@Composable
fun GreetingNavHost(
    viewModel: GreetingViewModel,
    state: GreetingState,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberAppNavigator(GreetingNavKey.Splash)

    // Consume one-time UI events (toasts) exactly once, lifecycle-aware.
    val eventContext = LocalContext.current
    EventsEffect(viewModel = viewModel) { event ->
        when (event) {
            is GreetingEvent.ShowToast -> {
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
                entry<GreetingNavKey.Splash> {
                    SplashScreen(
                        currentTheme = state.theme,
                        onFinish = { navigator.replace(GreetingNavKey.Main) }
                    )
                }
                entry<GreetingNavKey.Main> {
                    MainScreen(
                        state = state,
                        onAction = viewModel::trySendAction,
                        onOpenSettings = {
                            viewModel.trySendAction(GreetingAction.SidebarClosed)
                            navigator.navigate(GreetingNavKey.SettingsMenu)
                        },
                    )
                }
                entry<GreetingNavKey.SettingsMenu> {
                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(R.string.settings_page_title),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        SettingsMenuScreen(
                            currentTheme = state.theme,
                            onOpenAppearance = { navigator.navigate(GreetingNavKey.AppearanceSettings) },
                            onOpenFont = { navigator.navigate(GreetingNavKey.FontSettings) },
                            onOpenLanguage = { navigator.navigate(GreetingNavKey.LanguageSettings) },
                            modifier = contentModifier,
                        )
                    }
                }
                entry<GreetingNavKey.AppearanceSettings> {
                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(R.string.settings_menu_appearance_title),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        SettingsScreen(
                            currentTheme = state.theme,
                            onThemeChange = { viewModel.trySendAction(GreetingAction.ThemeSelected(it)) },
                            colorMode = state.colorMode,
                            onColorModeChange = { viewModel.trySendAction(GreetingAction.ColorModeChanged(it)) },
                            modifier = contentModifier,
                        )
                    }
                }
                entry<GreetingNavKey.LanguageSettings> {
                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(R.string.language_title),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        LanguageScreen(
                            currentTheme = state.theme,
                            modifier = contentModifier,
                        )
                    }
                }
                entry<GreetingNavKey.FontSettings> {
                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(R.string.settings_menu_font_title),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        FontScreen(
                            currentTheme = state.theme,
                            selectedTypography = state.typographyChoice,
                            onTypographyChange = { viewModel.trySendAction(GreetingAction.TypographySelected(it)) },
                            fontScale = state.fontScale,
                            onOpenFontSize = { navigator.navigate(GreetingNavKey.FontSizeSettings) },
                            installedFonts = state.installedFonts,
                            activeCustomFontId = state.activeCustomFontId,
                            downloadProgress = state.downloadProgress,
                            fontFamilyFor = viewModel::customFontFamily,
                            onSelectCustomFont = { viewModel.trySendAction(GreetingAction.CustomFontSelected(it)) },
                            onDeleteCustomFont = { viewModel.trySendAction(GreetingAction.FontDeleteClicked(it)) },
                            onDownloadFont = { viewModel.trySendAction(GreetingAction.FontDownloadClicked(it)) },
                            onImportFont = { uri, name ->
                                viewModel.trySendAction(GreetingAction.FontImportRequested(uri, name))
                            },
                            modifier = contentModifier,
                        )
                    }
                }
                entry<GreetingNavKey.FontSizeSettings> {
                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(R.string.settings_font_size_label),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        FontSizeScreen(
                            currentTheme = state.theme,
                            fontScale = state.fontScale,
                            onSave = { viewModel.trySendAction(GreetingAction.FontScaleSaved(it)) },
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
 * (back button + centered title) as the main shell, so a settings page reads
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
