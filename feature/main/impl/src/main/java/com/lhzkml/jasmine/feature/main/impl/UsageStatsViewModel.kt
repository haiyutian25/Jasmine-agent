package com.lhzkml.jasmine.feature.main.impl

import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.core.agent.AppUsage
import com.lhzkml.jasmine.core.agent.ConversationStore
import com.lhzkml.jasmine.core.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
}

/**
 * 读用量统计并交给页面。
 *
 * 数据源就是会话文件本身 —— 没附着、没网络、没第二份存储，所以每次刷新都是一次真扫。
 */
@HiltViewModel
class UsageStatsViewModel @Inject constructor(
    private val conversationStore: ConversationStore,
) : BaseViewModel<UsageStatsState, Unit, UsageStatsAction>(UsageStatsState()) {

    init {
        load()
    }

    override fun handleAction(action: UsageStatsAction) {
        when (action) {
            UsageStatsAction.RefreshClicked -> load()
        }
    }

    /** 真扫一遍本月；结果回来才改状态，中途只把 [UsageStatsState.isLoading] 立起来。 */
    private fun load() {
        viewModelScope.launch {
            mutableStateFlow.update { it.copy(isLoading = true) }
            val usage = runCatching { conversationStore.usageStats() }.getOrDefault(AppUsage.Empty)
            mutableStateFlow.update { it.copy(usage = usage, isLoading = false) }
        }
    }
}
