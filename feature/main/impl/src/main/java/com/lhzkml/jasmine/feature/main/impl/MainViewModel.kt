package com.lhzkml.jasmine.feature.main.impl

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.core.data.model.ColorMode
import com.lhzkml.jasmine.core.data.model.InstalledFont
import com.lhzkml.jasmine.core.data.model.PresetFont
import com.lhzkml.jasmine.core.data.model.AgentOutputLanguage
import com.lhzkml.jasmine.core.data.model.UserPreferences
import com.lhzkml.jasmine.core.data.repository.CustomFontRepository
import com.lhzkml.jasmine.core.data.repository.UserPreferencesRepository
import com.lhzkml.jasmine.core.ui.base.BaseViewModel
import com.lhzkml.jasmine.core.ui.base.EffectRunner
import com.lhzkml.jasmine.core.ui.theme.AppTypographyChoice
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.ThemeResolver
import com.lhzkml.jasmine.feature.main.impl.fonts.CustomFontFamilyCache
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
    /**
     * 已安装字体的**预览表**：字体 id → FontFamily（派生字段，与 [activeContentFont] 一处算）。
     *
     * 字体列表的每一行要一个 FontFamily 来画「Aa」预览。以前这是 `MainViewModel.customFontFamily(id)`
     * 这条**同步查询旁路**：不进状态、订阅关系断裂，界面输入被劈成"状态"与"另一次函数调用"两半
     * （P1-6）。并进状态后界面只有一份输入。解析只读内存（[CustomFontFamilyCache] 查仓库的内存快照），
     * 不碰磁盘；解析不出来（文件已不在）的 id 不进表，界面按缺省字体兜底。
     */
    val fontPreviews: Map<String, FontFamily> = emptyMap(),
    // Navigation / chrome
    val isSidebarOpen: Boolean,
    // Typography
    val typographyChoice: AppTypographyChoice,
    val fontScale: Float,
    val activeCustomFontId: String,
    val installedFonts: List<InstalledFont>,
    val downloadProgress: Map<String, Float>,
    // Agent（模型侧行为控制）
    /** 模型回复语言的设置值；见 [AgentOutputLanguage]。 */
    val agentOutputLanguage: String = AgentOutputLanguage.FOLLOW_INPUT,
    /**
     * 自动压缩的**全局默认**触发线（token）；`0` = 不设置。
     *
     * 口径见 [com.lhzkml.jasmine.core.data.model.ModelConfig.autoCompactTokenLimit]：
     * 它是**默认值**，模型自己填了就用模型的（合并发生在送进核心之前，
     * 见 `ChatViewModel.withCompactionDefaults`）。
     */
    val autoCompactTokenLimit: Int = 0,
    /** 自动压缩的**全局默认**窗口百分比；`0` = 不设置（核心按 95 走）。 */
    val effectiveContextWindowPercent: Int = 0,
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

    /** Agent 设置页里选了模型回复语言（[AgentOutputLanguage] 那五个取值之一）。 */
    data class AgentOutputLanguageSelected(val value: String) : MainAction

    /**
     * Agent 设置页改了**上下文压缩**的两个全局默认（任一项为 0 = 不设置）。
     *
     * 两个值一起送：它们是同一栏里的两条线，用户改哪条都该把当前这一对整体落笔。
     */
    data class CompactionSettingsChanged(
        val autoCompactTokenLimit: Int,
        val effectiveContextWindowPercent: Int,
    ) : MainAction

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

        /**
         * 落盘失败的回执（P0 修复方案 D4）：带回乐观值与回退值，handler 按**身份守卫**
         * 回滚 —— 当前状态仍等于乐观值才回退（连点竞态下，迟到的失败不能把新选择抹掉）。
         */
        data class ThemePersistRejected(val optimistic: String, val fallback: String) : Internal
        data class ColorModePersistRejected(val optimistic: ColorMode, val fallback: ColorMode) : Internal
        data class TypographyPersistRejected(
            val optimistic: AppTypographyChoice,
            val fallback: AppTypographyChoice,
            val fallbackFontId: String,
        ) : Internal
        data class CustomFontPersistRejected(val optimistic: String, val fallback: String) : Internal
        data class FontScalePersistRejected(val optimistic: Float, val fallback: Float) : Internal
        data class AgentOutputLanguagePersistRejected(val optimistic: String, val fallback: String) : Internal

        /** 压缩默认值落盘失败：把界面上的两条线退回 [fallback]（先比 [optimistic]，别覆盖用户后来的改动）。 */
        data class CompactionPersistRejected(
            val optimistic: Pair<Int, Int>,
            val fallback: Pair<Int, Int>,
        ) : Internal

        /** 字体删除完成（成功/失败）；列表本身由 installedVersion 回灌，无需回滚。 */
        data class FontDeleteCompleted(val success: Boolean) : Internal
    }
}

/**
 * 语言页之外所有主功能的出站命令（P0 修复方案 D3/D4）。
 *
 * 落盘类命令带 [optimistic]（界面正在显示的值）与回滚材料，失败由 EffectRunner 兜底成
 * `*Rejected` 回流。短命令走这里；**长任务**（字体下载/导入 —— 有自己的进度流与
 * Completed 回流）不占这条单消费者通道，留在各自的协程里。
 */
private sealed interface MainEffect {
    data class PersistTheme(val optimistic: String, val fallback: String) : MainEffect
    data class PersistColorMode(val optimistic: ColorMode, val fallback: ColorMode) : MainEffect
    data class PersistTypography(
        val optimistic: AppTypographyChoice,
        val fallback: AppTypographyChoice,
        val fallbackFontId: String,
    ) : MainEffect
    data class PersistCustomFont(val optimistic: String, val fallback: String) : MainEffect
    data class PersistFontScale(val optimistic: Float, val fallback: Float) : MainEffect
    data class PersistAgentOutputLanguage(val optimistic: String, val fallback: String) : MainEffect

    /** 把压缩的两个全局默认落进偏好仓库（失败经 `CompactionPersistRejected` 回流）。 */
    data class PersistCompactionSettings(
        val optimistic: Pair<Int, Int>,
        val fallback: Pair<Int, Int>,
    ) : MainEffect

    /** 删字体：缓存驱逐与删文件是同一事务的两半步，一起成功才算成功。 */
    data class DeleteFont(val fontId: String) : MainEffect
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
            isSidebarOpen = false,
            typographyChoice = AppTypographyChoice.EDITORIAL,
            fontScale = UserPreferences.DEFAULT.fontScale,
            activeCustomFontId = UserPreferences.DEFAULT.activeCustomFontId,
            agentOutputLanguage = UserPreferences.DEFAULT.agentOutputLanguage,
            autoCompactTokenLimit = UserPreferences.DEFAULT.autoCompactTokenLimit,
            effectiveContextWindowPercent = UserPreferences.DEFAULT.effectiveContextWindowPercent,
            installedFonts = emptyList(),
            downloadProgress = emptyMap(),
        )
    },
) {

    /** 出站命令执行器（D3）：落盘与缓存驱逐的唯一出口；失败兜底成 *Rejected / Completed(false)。 */
    private val effects = EffectRunner<MainEffect, MainAction>(
        scope = viewModelScope,
        sendAction = ::sendAction,
        perform = ::performEffect,
        onFailure = ::effectFailed,
    )

    /** 执行出站命令（suspend = 异步路径）。成功不需要回执：仓库 StateFlow 回灌即确认。 */
    private suspend fun performEffect(effect: MainEffect): MainAction? = when (effect) {
        is MainEffect.PersistTheme ->
            userPreferencesRepository.updateTheme(effect.optimistic).let { null }
        is MainEffect.PersistColorMode ->
            userPreferencesRepository.updateColorMode(effect.optimistic.id).let { null }
        is MainEffect.PersistTypography -> {
            userPreferencesRepository.updateTypography(effect.optimistic.name)
            userPreferencesRepository.updateActiveCustomFont("")
            null
        }
        is MainEffect.PersistCustomFont ->
            userPreferencesRepository.updateActiveCustomFont(effect.optimistic).let { null }
        is MainEffect.PersistFontScale ->
            userPreferencesRepository.updateFontScale(effect.optimistic).let { null }
        is MainEffect.PersistAgentOutputLanguage ->
            userPreferencesRepository.updateAgentOutputLanguage(effect.optimistic).let { null }
        is MainEffect.PersistCompactionSettings -> {
            userPreferencesRepository.updateAutoCompactTokenLimit(effect.optimistic.first)
            userPreferencesRepository.updateEffectiveContextWindowPercent(effect.optimistic.second)
            null
        }
        is MainEffect.DeleteFont -> {
            customFontFamilyCache.evict(effect.fontId)
            customFontRepository.deleteFont(effect.fontId)
            MainAction.Internal.FontDeleteCompleted(true)
        }
    }

    /** 失败兜底：把命令连同它的回滚材料回流成 *Rejected（D4）。 */
    private fun effectFailed(effect: MainEffect, error: Throwable): MainAction {
        Log.w(TAG, "effect $effect failed", error)
        return when (effect) {
            is MainEffect.PersistTheme ->
                MainAction.Internal.ThemePersistRejected(effect.optimistic, effect.fallback)
            is MainEffect.PersistColorMode ->
                MainAction.Internal.ColorModePersistRejected(effect.optimistic, effect.fallback)
            is MainEffect.PersistTypography ->
                MainAction.Internal.TypographyPersistRejected(
                    effect.optimistic,
                    effect.fallback,
                    effect.fallbackFontId,
                )
            is MainEffect.PersistCustomFont ->
                MainAction.Internal.CustomFontPersistRejected(effect.optimistic, effect.fallback)
            is MainEffect.PersistFontScale ->
                MainAction.Internal.FontScalePersistRejected(effect.optimistic, effect.fallback)
            is MainEffect.PersistAgentOutputLanguage ->
                MainAction.Internal.AgentOutputLanguagePersistRejected(effect.optimistic, effect.fallback)
            is MainEffect.PersistCompactionSettings ->
                MainAction.Internal.CompactionPersistRejected(effect.optimistic, effect.fallback)
            is MainEffect.DeleteFont -> MainAction.Internal.FontDeleteCompleted(false)
        }
    }

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
            is MainAction.Internal.FontDeleteCompleted -> {
                // 列表由 installedVersion 回灌（删除失败就没有回灌，列表本就未变）；失败只需可见。
                if (!action.success) sendEvent(MainEvent.ShowToast(R.string.font_delete_failed_toast))
            }

            is MainAction.Internal.ThemePersistRejected,
            is MainAction.Internal.ColorModePersistRejected,
            is MainAction.Internal.TypographyPersistRejected,
            is MainAction.Internal.CustomFontPersistRejected,
            is MainAction.Internal.FontScalePersistRejected,
            is MainAction.Internal.AgentOutputLanguagePersistRejected,
            -> handlePersistRejected(action)

            is MainAction.AgentOutputLanguageSelected ->
                handleAgentOutputLanguageSelected(action)
            is MainAction.CompactionSettingsChanged ->
                handleCompactionSettingsChanged(action)
            is MainAction.Internal.CompactionPersistRejected ->
                if (state.autoCompactTokenLimit to state.effectiveContextWindowPercent ==
                    action.optimistic
                ) {
                    updateState {
                        copy(
                            autoCompactTokenLimit = action.fallback.first,
                            effectiveContextWindowPercent = action.fallback.second,
                        )
                    }
                }
        }
    }

    // region Action handlers

    private fun handleThemeSelected(action: MainAction.ThemeSelected) {
        val fallback = state.themeId
        updateState { copy(themeId = action.palette.themeId) }
        effects.send(MainEffect.PersistTheme(optimistic = action.palette.themeId, fallback = fallback))
    }

    private fun handleColorModeChanged(action: MainAction.ColorModeChanged) {
        val fallback = state.colorMode
        updateState { copy(colorMode = action.mode) }
        effects.send(MainEffect.PersistColorMode(optimistic = action.mode, fallback = fallback))
    }

    private fun handleTypographySelected(action: MainAction.TypographySelected) {
        val fallbackChoice = state.typographyChoice
        val fallbackFontId = state.activeCustomFontId
        // Selecting a system engine clears any custom-font override.
        updateState { copy(typographyChoice = action.choice, activeCustomFontId = "") }
        effects.send(MainEffect.PersistTypography(action.choice, fallbackChoice, fallbackFontId))
    }

    private fun handleCustomFontSelected(action: MainAction.CustomFontSelected) {
        val fallback = state.activeCustomFontId
        updateState { copy(activeCustomFontId = action.fontId) }
        effects.send(MainEffect.PersistCustomFont(optimistic = action.fontId, fallback = fallback))
    }

    private fun handleFontDownloadClicked(action: MainAction.FontDownloadClicked) {
        viewModelScope.launch {
            val success = customFontRepository.downloadPreset(action.preset)
            sendAction(MainAction.Internal.FontDownloadCompleted(success))
        }
    }

    private fun handleFontDeleteClicked(action: MainAction.FontDeleteClicked) {
        if (state.activeCustomFontId == action.fontId) {
            val fallback = state.activeCustomFontId
            updateState { copy(activeCustomFontId = "") }
            effects.send(MainEffect.PersistCustomFont(optimistic = "", fallback = fallback))
        }
        // 缓存驱逐 + 删文件是出站命令（D3）：结果以 FontDeleteCompleted 回流。
        effects.send(MainEffect.DeleteFont(action.fontId))
        sendEvent(MainEvent.ShowToast(R.string.font_deleted_toast))
    }

    private fun handleFontImportRequested(action: MainAction.FontImportRequested) {
        viewModelScope.launch {
            val fontId = customFontRepository.importFont(action.uri, action.fallbackName)
            sendAction(MainAction.Internal.FontImportCompleted(fontId))
        }
    }

    private fun handleFontScaleSaved(action: MainAction.FontScaleSaved) {
        val fallback = state.fontScale
        updateState { copy(fontScale = action.scale) }
        effects.send(MainEffect.PersistFontScale(optimistic = action.scale, fallback = fallback))
        sendEvent(MainEvent.ShowToast(R.string.font_size_saved_toast))
    }

    /**
     * 模型回复语言：界面立刻跟上，落盘走 Effect（失败由 Rejected 回滚，D4）。它进的是**系统指令**，
     * 所以聊天那边在下一次附着会话时才会用上新的那句（聊天页自己会重挂会话，见 ChatViewModel）。
     */
    private fun handleAgentOutputLanguageSelected(action: MainAction.AgentOutputLanguageSelected) {
        val fallback = state.agentOutputLanguage
        updateState { copy(agentOutputLanguage = action.value) }
        effects.send(MainEffect.PersistAgentOutputLanguage(optimistic = action.value, fallback = fallback))
    }

    /**
     * 压缩的两个全局默认。
     *
     * 与语言那条同一条路子：先乐观落笔（界面立刻跟手），落盘走出站命令，
     * 失败经 [MainAction.Internal.CompactionPersistRejected] 回滚（D4）。
     */
    private fun handleCompactionSettingsChanged(action: MainAction.CompactionSettingsChanged) {
        val fallback = state.autoCompactTokenLimit to state.effectiveContextWindowPercent
        val optimistic = action.autoCompactTokenLimit to action.effectiveContextWindowPercent
        if (optimistic == fallback) return
        updateState {
            copy(
                autoCompactTokenLimit = action.autoCompactTokenLimit,
                effectiveContextWindowPercent = action.effectiveContextWindowPercent,
            )
        }
        effects.send(MainEffect.PersistCompactionSettings(optimistic = optimistic, fallback = fallback))
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
                agentOutputLanguage = prefs.agentOutputLanguage,
                autoCompactTokenLimit = prefs.autoCompactTokenLimit,
                effectiveContextWindowPercent = prefs.effectiveContextWindowPercent,
                // Sidebar-open state is intentionally NOT restored here — it is
                // session-transient and always starts closed after process death.
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
            val fallback = state.activeCustomFontId
            updateState { copy(activeCustomFontId = fontId) }
            effects.send(MainEffect.PersistCustomFont(optimistic = fontId, fallback = fallback))
            sendEvent(MainEvent.ShowToast(R.string.font_imported_toast))
        } else {
            sendEvent(MainEvent.ShowToast(R.string.font_import_failed_toast))
        }
    }

    /**
     * 落盘失败的统一归口（D4）：**身份守卫**回滚 —— 当前状态仍等于那条乐观值才回退
     * （用户可能已经又选了别的，迟到的失败不能把新选择抹掉），然后提示。
     */
    private fun handlePersistRejected(action: MainAction.Internal) {
        when (action) {
            is MainAction.Internal.ThemePersistRejected ->
                if (state.themeId == action.optimistic) updateState { copy(themeId = action.fallback) }
            is MainAction.Internal.ColorModePersistRejected ->
                if (state.colorMode == action.optimistic) updateState { copy(colorMode = action.fallback) }
            is MainAction.Internal.TypographyPersistRejected ->
                // 守卫两个字段都还是那条命令写下的样子：用户随后选了自定义字体的话，不回滚。
                if (state.typographyChoice == action.optimistic && state.activeCustomFontId.isEmpty()) {
                    updateState {
                        copy(
                            typographyChoice = action.fallback,
                            activeCustomFontId = action.fallbackFontId,
                        )
                    }
                }
            is MainAction.Internal.CustomFontPersistRejected ->
                if (state.activeCustomFontId == action.optimistic) {
                    updateState { copy(activeCustomFontId = action.fallback) }
                }
            is MainAction.Internal.FontScalePersistRejected ->
                if (state.fontScale == action.optimistic) updateState { copy(fontScale = action.fallback) }
            is MainAction.Internal.AgentOutputLanguagePersistRejected ->
                if (state.agentOutputLanguage == action.optimistic) {
                    updateState { copy(agentOutputLanguage = action.fallback) }
                }
            else -> return
        }
        sendEvent(MainEvent.ShowToast(R.string.setting_save_failed_toast))
    }

    // endregion

    /**
     * Updates [mutableStateFlow] and re-derives the derived fields ([MainState.theme],
     * [MainState.activeContentFont]) so they always stay consistent with the raw inputs.
     *
     * Note: sidebar-open state is deliberately NOT persisted to DataStore — it is
     * transient per-session UI position. The settings flow is not tracked here at all:
     * it lives on the Navigation 3 back stack, which is serialized and restored across
     * process death by the navigation library.
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
                activeContentFont = resolveContentFont(next.activeCustomFontId, next.typographyChoice.font),
                fontPreviews = resolveFontPreviews(next.installedFonts),
            )
        }
    }

    /**
     * 已安装字体 → [FontFamily] 的预览表。
     *
     * 纯内存读（[CustomFontFamilyCache] 只查仓库的内存快照 + 自己的 per-id 缓存），无 IO、无副作用 ——
     * 与 [resolveContentFont] 同属门禁 R11 的登记豁免。
     */
    private fun resolveFontPreviews(installedFonts: List<InstalledFont>): Map<String, FontFamily> =
        installedFonts.mapNotNull { font ->
            customFontFamilyCache.fontFamilyFor(font.id)?.let { family -> font.id to family }
        }.toMap()

    /**
     * 内存缓存读（非 IO、无副作用）。与 [resolveFontPreviews] 同属一类已登记豁免 —— 门禁 R11 白名单。
     */
    private fun resolveContentFont(customFontId: String, fallback: FontFamily): FontFamily =
        customFontFamilyCache.fontFamilyFor(customFontId) ?: fallback

    private companion object {
        const val TAG = "MainViewModel"
    }
}
