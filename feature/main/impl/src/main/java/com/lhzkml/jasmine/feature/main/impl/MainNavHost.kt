package com.lhzkml.jasmine.feature.main.impl

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import com.lhzkml.jasmine.core.widgets.scaffold.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.lhzkml.jasmine.core.navigation.rememberAppNavigator
import com.lhzkml.jasmine.core.ui.base.util.EventsEffect
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.main.api.MainNavKey
import com.lhzkml.jasmine.feature.main.impl.screens.SplashScreen
import com.lhzkml.jasmine.feature.provider.api.ProviderNavKey
import com.lhzkml.jasmine.feature.provider.impl.ProviderAction
import com.lhzkml.jasmine.feature.provider.impl.ProviderEvent
import com.lhzkml.jasmine.feature.provider.impl.ProviderScreen
import com.lhzkml.jasmine.feature.provider.impl.ProviderViewModel
import com.lhzkml.jasmine.feature.settings.api.SettingsNavKey
import com.lhzkml.jasmine.feature.settings.impl.LanguageViewModel
import com.lhzkml.jasmine.feature.settings.impl.R as SettingsR
import com.lhzkml.jasmine.feature.settings.impl.screens.BehaviourAndPermissionsScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugComponentsScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugBadgeScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugBottomBarScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugCardScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugCheckboxScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugDialogScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugFabMenuScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugMenuScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugProgressScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugRadioButtonScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugSearchBarScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugSidebarScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugTabsScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugSliderScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.DebugTopAppBarScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.FontScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.FontSizeScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.LanguageScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.SettingsMenuScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.SettingsScreen
import com.lhzkml.jasmine.feature.settings.impl.screens.UsageStatsScreen
import com.lhzkml.jasmine.feature.provider.impl.R as ProviderR

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
            // 一旦显式传入装饰器列表，NavDisplay 的默认列表即被覆盖，因此
            // 必须手动保留默认的状态保存装饰器，再叠加条目级 ViewModel store：
            entryDecorators = listOf(
                // 默认装饰器：管理场景与保存状态（进程死亡/配置变更恢复）。
                rememberSaveableStateHolderNavEntryDecorator(),
                // 为每个 NavEntry 提供独立的 ViewModelStoreOwner：条目弹出时
                // 清除其 ViewModel，使 entry 内 hiltViewModel() 的作用域限定
                // 到该条目（而非 Activity）。
                rememberViewModelStoreNavEntryDecorator(),
            ),
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
                            onOpenBehaviourAndPermissions = {
                                navigator.navigate(SettingsNavKey.BehaviourAndPermissions)
                            },
                            onOpenProviders = { navigator.navigate(ProviderNavKey.ProviderList) },
                            onOpenUsageStats = { navigator.navigate(SettingsNavKey.UsageStats) },
                            onOpenDebug = { navigator.navigate(SettingsNavKey.Debug) },
                            modifier = contentModifier,
                        )
                    }
                }
                entry<SettingsNavKey.BehaviourAndPermissions> {
                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(SettingsR.string.settings_menu_agent_title),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        BehaviourAndPermissionsScreen(
                            currentTheme = state.theme,
                            selected = state.agentOutputLanguage,
                            onSelect = {
                                viewModel.trySendAction(MainAction.AgentOutputLanguageSelected(it))
                            },
                            autoCompactTokenLimit = state.autoCompactTokenLimit,
                            effectiveContextWindowPercent = state.effectiveContextWindowPercent,
                            onCompactionChanged = { limit, percent ->
                                viewModel.trySendAction(
                                    MainAction.CompactionSettingsChanged(limit, percent),
                                )
                            },
                            modifier = contentModifier,
                        )
                    }
                }
                entry<ProviderNavKey.ProviderList> {
                    // 条目作用域的 ViewModel（由上方 ViewModelStoreNavEntryDecorator
                    // 提供 owner）：离开提供商页即销毁，编辑器草稿不再残留。
                    // 状态收集同步下沉到此处，避免表单击键触发 NavHost 全树重组。
                    val providerViewModel: ProviderViewModel = hiltViewModel()
                    val providerState by providerViewModel.stateFlow.collectAsStateWithLifecycle()

                    // 一次性事件（toast）在该条目内消费，生命周期感知。
                    EventsEffect(viewModel = providerViewModel) { event ->
                        when (event) {
                            is ProviderEvent.ShowToast -> {
                                val message = if (event.formatArgs.isEmpty()) {
                                    eventContext.getString(event.messageRes)
                                } else {
                                    eventContext.getString(
                                        event.messageRes,
                                        *event.formatArgs.toTypedArray()
                                    )
                                }
                                Toast.makeText(eventContext, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    // 系统返回与顶栏返回走**同一条规则**：编辑态先关表单、不丢草稿。
                    // 规则本身在 `ProviderState.canNavigateBack` 里（业务规则属于 ViewModel 的状态），
                    // 这里只做"把系统返回拦下来交给它"这一件事 —— 以前系统返回直接弹栈，
                    // 编辑中的草稿会连页面一起丢掉。
                    BackHandler(enabled = !providerState.canNavigateBack) {
                        providerViewModel.trySendAction(ProviderAction.CancelClicked)
                    }

                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(ProviderR.string.provider_page_title),
                        onBack = {
                            if (providerState.canNavigateBack) {
                                navigator.goBack()
                            } else {
                                providerViewModel.trySendAction(ProviderAction.CancelClicked)
                            }
                        },
                    ) { contentModifier ->
                        ProviderScreen(
                            state = providerState,
                            onAction = providerViewModel::trySendAction,
                            currentTheme = state.theme,
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
                    val languageViewModel: LanguageViewModel = hiltViewModel()
                    val languageState by languageViewModel.stateFlow.collectAsStateWithLifecycle()

                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(SettingsR.string.language_title),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        LanguageScreen(
                            state = languageState,
                            onAction = languageViewModel::trySendAction,
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
                            // 预览表是**状态的一部分**（P1-6）：界面读 state，不再走 ViewModel 的查询方法。
                            fontFamilyFor = { fontId -> state.fontPreviews[fontId] },
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
                entry<SettingsNavKey.UsageStats> {
                    // 条目作用域的 ViewModel：离开用量页就销毁，下次进来重新扫一遍会话文件。
                    val usageViewModel: UsageStatsViewModel = hiltViewModel()
                    val usageState by usageViewModel.stateFlow.collectAsStateWithLifecycle()

                    // 一次性效果（读统计失败）：本条目内消费，生命周期感知 —— 与主屏同一条做法。
                    val usageEventContext = LocalContext.current
                    EventsEffect(viewModel = usageViewModel) { event ->
                        when (event) {
                            UsageStatsEvent.ShowError -> Toast.makeText(
                                usageEventContext,
                                usageEventContext.getString(R.string.chat_usage_stats_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }

                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(SettingsR.string.usage_stats_title),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        UsageStatsScreen(
                            usage = usageState.usage,
                            isLoading = usageState.isLoading,
                            onRefresh = {
                                usageViewModel.trySendAction(UsageStatsAction.RefreshClicked)
                            },
                            currentTheme = state.theme,
                            modifier = contentModifier,
                        )
                    }
                }
                // 组件层调试页：独立试验场，除主题外不接任何状态，也不改任何东西。
                entry<SettingsNavKey.Debug> {
                    SettingsPage(
                        currentTheme = state.theme,
                        title = stringResource(SettingsR.string.settings_menu_debug_title),
                        onBack = { navigator.goBack() },
                    ) { contentModifier ->
                        DebugComponentsScreen(
                            currentTheme = state.theme,
                            onOpenTopAppBar = {
                                navigator.navigate(SettingsNavKey.DebugTopAppBar(it))
                            },
                            onOpenSidebar = {
                                navigator.navigate(SettingsNavKey.DebugSidebar(it))
                            },
                            onOpenBottomBar = { navigator.navigate(SettingsNavKey.DebugBottomBar) },
                            onOpenSlider = { navigator.navigate(SettingsNavKey.DebugSlider) },
                            onOpenSearchBar = {
                                navigator.navigate(SettingsNavKey.DebugSearchBar(it))
                            },
                            onOpenFabMenu = {
                                navigator.navigate(SettingsNavKey.DebugFabMenu(it))
                            },
                            onOpenDialog = {
                                navigator.navigate(SettingsNavKey.DebugDialog(it))
                            },
                            onOpenBadge = {
                                navigator.navigate(SettingsNavKey.DebugBadge(it))
                            },
                            onOpenCheckbox = {
                                navigator.navigate(SettingsNavKey.DebugCheckbox(it))
                            },
                            onOpenProgress = {
                                navigator.navigate(SettingsNavKey.DebugProgress(it))
                            },
                            onOpenRadioButton = {
                                navigator.navigate(SettingsNavKey.DebugRadioButton(it))
                            },
                            onOpenMenu = {
                                navigator.navigate(SettingsNavKey.DebugMenu(it))
                            },
                            onOpenTabs = {
                                navigator.navigate(SettingsNavKey.DebugTabs(it))
                            },
                            onOpenCard = {
                                navigator.navigate(SettingsNavKey.DebugCard(it))
                            },
                            modifier = contentModifier,
                        )
                    }
                }
                // 顶部栏变体预览：**独立页面**，它自己的顶栏就是 core:widgets 的 TopAppBar 本体，
                // 所以这里刻意不套 SettingsPage（那是设置流自己的顶栏）。
                entry<SettingsNavKey.DebugTopAppBar> { key ->
                    DebugTopAppBarScreen(
                        variant = key.variant,
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
                }
                // 侧边栏变体预览：同样是不套 SettingsPage 的独立页面。
                entry<SettingsNavKey.DebugSidebar> { key ->
                    DebugSidebarScreen(
                        variant = key.variant,
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
                }
                // 底部导航栏预览：同样是不套 SettingsPage 的独立页面。
                entry<SettingsNavKey.DebugBottomBar> {
                    DebugBottomBarScreen(
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
                }
                // 滑块预览：同样是不套 SettingsPage 的独立页面。
                entry<SettingsNavKey.DebugSlider> {
                    DebugSliderScreen(
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
                }
                // 搜索框变体预览：同样是不套 SettingsPage 的独立页面（页内顶栏是我们的小号 TopAppBar）。
                entry<SettingsNavKey.DebugSearchBar> { key ->
                    DebugSearchBarScreen(
                        variant = key.variant,
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
                }
                // FAB 菜单预览：同样是不套 SettingsPage 的独立页面。
                entry<SettingsNavKey.DebugFabMenu> { key ->
                    DebugFabMenuScreen(
                        variant = key.variant,
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
                }
                // 对话框预览：同样是不套 SettingsPage 的独立页面。
                entry<SettingsNavKey.DebugDialog> { key ->
                    DebugDialogScreen(
                        variant = key.variant,
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
                }
                // 徽标预览：同样是不套 SettingsPage 的独立页面。
                entry<SettingsNavKey.DebugBadge> { key ->
                    DebugBadgeScreen(
                        variant = key.variant,
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
                }
                // 复选框预览：同样是不套 SettingsPage 的独立页面。
                entry<SettingsNavKey.DebugCheckbox> { key ->
                    DebugCheckboxScreen(
                        variant = key.variant,
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
                }
                // 进度与下拉刷新预览：同样是不套 SettingsPage 的独立页面。
                entry<SettingsNavKey.DebugProgress> { key ->
                    DebugProgressScreen(
                        variant = key.variant,
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
                }
                // 单选按钮预览：同样是不套 SettingsPage 的独立页面。
                entry<SettingsNavKey.DebugRadioButton> { key ->
                    DebugRadioButtonScreen(
                        variant = key.variant,
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
                }
                // 菜单预览：同样是不套 SettingsPage 的独立页面。
                entry<SettingsNavKey.DebugMenu> { key ->
                    DebugMenuScreen(
                        variant = key.variant,
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
                }
                // 标签页预览：同样是不套 SettingsPage 的独立页面。
                entry<SettingsNavKey.DebugTabs> { key ->
                    DebugTabsScreen(
                        variant = key.variant,
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
                }
                // 卡片预览：同样是不套 SettingsPage 的独立页面。
                entry<SettingsNavKey.DebugCard> { key ->
                    DebugCardScreen(
                        variant = key.variant,
                        currentTheme = state.theme,
                        onBack = { navigator.goBack() },
                    )
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
        // A settings page owns the whole viewport, so it is the level that has to
        // clear the keyboard — these pages hold text fields and no bottom bar.
        // The IME inset therefore goes into `innerPadding` for the content.
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}
