package com.lhzkml.jasmine.feature.main.impl

import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.core.agent.AppUsage
import com.lhzkml.jasmine.core.agent.ConversationStore
import com.lhzkml.jasmine.core.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 用量页的状态。
 *
 * 没有时间范围可以选：**累计总数**是全部时间的，其余（活动格子图 / 模型用量 / 连续天数）固定是
 * **本月** —— 每个月翻篇时，上个月的记录会被核心删掉，那些数字自己就消失了。
 */
data class UsageStatsState(
    val usage: AppUsage = AppUsage.Empty,
    /** 第一次读还没回来（页面用它显示「正在统计中」）。 */
    val isLoading: Boolean = true,
)

/** 用量页的意图。 */
sealed interface UsageStatsAction {
    data object RefreshClicked : UsageStatsAction

    /**
     * 异步结果的回流口：真扫在协程里做，结果以这两条 action 回来，状态只在
     * [UsageStatsViewModel.handleAction] 里同步改（R2）。
     */
    sealed interface Internal : UsageStatsAction {
        data class Loaded(val usage: AppUsage) : Internal

        /** [message] 是原始异常文本（排查用）；界面只显示资源文案。 */
        data class LoadFailed(val message: String) : Internal
    }
}

/** 用量页的一次性效果（失败提示）；由 `MainNavHost` 里那处 `EventsEffect` 消费。 */
sealed interface UsageStatsEvent {
    data object ShowError : UsageStatsEvent
}

/**
 * 读用量统计并交给页面。
 *
 * 数据源就是会话文件本身 —— 没附着、没网络、没第二份存储，所以每次刷新都是一次真扫。
 */
@HiltViewModel
class UsageStatsViewModel @Inject constructor(
    private val conversationStore: ConversationStore,
) : BaseViewModel<UsageStatsState, UsageStatsEvent, UsageStatsAction>(UsageStatsState()) {

    init {
        // 与 UI 同一条路：让"首屏统计"也走 action 通道，不在 init 里直接改状态。
        trySendAction(UsageStatsAction.RefreshClicked)
    }

    override fun handleAction(action: UsageStatsAction) {
        when (action) {
            UsageStatsAction.RefreshClicked -> {
                // "正在统计中"是**意图**：同步落。真扫在协程里做，结果以 action 回流。
                updateState { copy(isLoading = true) }
                viewModelScope.launch {
                    val result = runCatching { conversationStore.usageStats() }
                    sendAction(
                        result.fold(
                            onSuccess = { UsageStatsAction.Internal.Loaded(it) },
                            onFailure = {
                                UsageStatsAction.Internal.LoadFailed(
                                    it.message ?: it::class.simpleName.orEmpty()
                                )
                            },
                        )
                    )
                }
            }
            is UsageStatsAction.Internal.Loaded ->
                updateState { copy(usage = action.usage, isLoading = false) }

            is UsageStatsAction.Internal.LoadFailed -> {
                // 读不出来就停在"不再加载"，并让界面知道（失败不再无声）。
                updateState { copy(isLoading = false) }
                sendEvent(UsageStatsEvent.ShowError)
            }
        }
    }

    /** Single mutation point of [mutableStateFlow]（与其它 ViewModel 同一个做法）。 */
    private inline fun updateState(block: UsageStatsState.() -> UsageStatsState) {
        mutableStateFlow.update(block)
    }
}
