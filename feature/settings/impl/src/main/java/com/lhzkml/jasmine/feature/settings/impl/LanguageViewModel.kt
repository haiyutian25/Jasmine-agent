package com.lhzkml.jasmine.feature.settings.impl

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.core.data.repository.AppLanguage
import com.lhzkml.jasmine.core.data.repository.AppLanguageRepository
import com.lhzkml.jasmine.core.ui.base.BaseViewModel
import com.lhzkml.jasmine.core.ui.base.EffectRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 语言页的状态。
 *
 * 只有一个值：**当前生效的选择**。平台（AppCompat 的 per-app locales）是它的唯一来源，
 * 界面不再自己去问平台要（UDF：组合期不读平台状态）。
 */
data class LanguageState(
    val selected: AppLanguage = AppLanguage.SYSTEM,
)

/** 语言页的意图。 */
sealed interface LanguageAction {
    /** 用户点了某项。 */
    data class Selected(val language: AppLanguage) : LanguageAction

    /**
     * 界面发现组合配置换了（应用语言会重建 Activity）。
     *
     * 状态这时回到平台的权威值：**重复点选同一个语言不会重建**，那一刻界面没有任何东西会动，
     * 勾要立刻跟手就得先乐观落笔，再由这条把它落定。
     */
    data object SystemLocaleSettled : LanguageAction

    /**
     * 异步/平台结果的回流口：读平台当前值在协程里做，结果以 action 回来，
     * 状态只在 [LanguageViewModel.handleAction] 里同步改。
     */
    sealed interface Internal : LanguageAction {
        data class CurrentResolved(val language: AppLanguage) : Internal

        /** 把选择写进平台失败：handler 重新读平台权威值，把勾纠正回去。 */
        data object ApplyFailed : Internal
    }
}

/** 语言页的一次性效果（目前没有：切换由重建 Activity 呈现）。 */
sealed interface LanguageEvent

/**
 * 语言页的出站命令（P0 修复方案 D3）：应用语言是平台调用，必须走 Effect 通道 ——
 * handler 保持纯净（不同步调平台），失败经 [LanguageAction.Internal.ApplyFailed] 回流。
 */
private sealed interface LanguageEffect {
    data class ApplyLanguage(val language: AppLanguage) : LanguageEffect
}

/**
 * 语言页的 ViewModel。
 *
 * 平台 API 一律经 [AppLanguageRepository] —— 这个类不碰 AppCompat，界面更不碰（那条路正是
 * 之前"View 层直接调平台、还在组合期读平台状态"的地方）。写平台走 [effects]（D3）；
 * 读平台在协程里做、结果经 [LanguageAction.Internal.CurrentResolved] 回流。
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val appLanguageRepository: AppLanguageRepository,
) : BaseViewModel<LanguageState, LanguageEvent, LanguageAction>(LanguageState()) {

    /** 出站命令执行器：应用语言的唯一出口。 */
    private val effects = EffectRunner<LanguageEffect, LanguageAction>(
        scope = viewModelScope,
        sendAction = ::sendAction,
        perform = ::performEffect,
        onFailure = { _, _ -> LanguageAction.Internal.ApplyFailed },
    )

    /** 执行出站命令（suspend = 异步路径，handler 永不直调平台）。 */
    private suspend fun performEffect(effect: LanguageEffect): LanguageAction? = when (effect) {
        is LanguageEffect.ApplyLanguage -> {
            appLanguageRepository.apply(effect.language)
            null
        }
    }

    init {
        // 首屏读当前值也走 action 通道，不在 init 里直接改状态。
        viewModelScope.launch {
            sendAction(LanguageAction.Internal.CurrentResolved(appLanguageRepository.current()))
        }
    }

    override fun handleAction(action: LanguageAction) {
        when (action) {
            is LanguageAction.Selected -> {
                // 乐观落笔在前，应用在后：生效语言不变（不重建）时勾也要跟着手指走。
                updateState { copy(selected = action.language) }
                effects.send(LanguageEffect.ApplyLanguage(action.language))
            }

            LanguageAction.SystemLocaleSettled ->
                // 平台当前值在协程里读，handler 不碰平台（D3）。
                viewModelScope.launch {
                    sendAction(
                        LanguageAction.Internal.CurrentResolved(appLanguageRepository.current())
                    )
                }

            is LanguageAction.Internal.CurrentResolved ->
                updateState { copy(selected = action.language) }

            LanguageAction.Internal.ApplyFailed -> {
                // 写平台失败：勾回到平台权威值（可观察的自我纠正），细节进日志。
                Log.w(TAG, "apply language failed; reverting to the platform value")
                viewModelScope.launch {
                    sendAction(
                        LanguageAction.Internal.CurrentResolved(appLanguageRepository.current())
                    )
                }
            }
        }
    }

    /** Single mutation point of [mutableStateFlow]（与其它 ViewModel 同一个做法）。 */
    private inline fun updateState(block: LanguageState.() -> LanguageState) {
        mutableStateFlow.update(block)
    }

    private companion object {
        const val TAG = "LanguageViewModel"
    }
}
