package com.lhzkml.jasmine.core.ui.base.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lhzkml.jasmine.core.ui.base.BaseViewModel
import kotlinx.coroutines.flow.Flow

/**
 * Convenience method for observing event flow from [BaseViewModel].
 *
 * 事件只在界面处于 RESUMED 时**消费**：这样停驻/后台的界面不会弹出重复的 Toast。
 *
 * **停驻期间产生的事件是排队等着，不是被丢掉**（F5）—— 见 [collectEventsWhileResumed]。
 * 这里以前用的是 `.filter { currentState.isAtLeast(RESUMED) }`，而 `eventFlow` 是
 * `receiveAsFlow()`（单消费者）：事件在 `filter` 那一步**已经被从通道取走**，滤掉就是永久丢失。
 * 屏幕停在 STARTED 时产生的失败提示会静默消失 —— 与"失败必须可观察"正好相反。
 */
@Composable
fun <E> EventsEffect(
    viewModel: BaseViewModel<*, E, *>,
    lifecycleOwner: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    handler: (E) -> Unit,
) {
    // 让重组合换掉的 handler 立刻生效，而不是把旧的那个锁进协程。
    val currentHandler by rememberUpdatedState(handler)
    LaunchedEffect(viewModel, lifecycleOwner) {
        collectEventsWhileResumed(viewModel.eventFlow, lifecycleOwner) { currentHandler(it) }
    }
}

/**
 * 把事件流接上生命周期：**停在 RESUMED 上**。
 *
 * 状态不够时 `repeatOnLifecycle` 会取消内层块、等回到 RESUMED 再重新订阅 —— 事件留在通道里
 * （无界），回来时一件不落地补上。这跟 `filter` 的差别就是"推迟"与"丢弃"的差别。
 *
 * 单独提出来是为了能测：它只依赖 [Flow] 与 [Lifecycle]，不需要 Composable。
 */
internal suspend fun <E> collectEventsWhileResumed(
    events: Flow<E>,
    lifecycle: Lifecycle,
    handler: suspend (E) -> Unit,
) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        events.collect { handler(it) }
    }
}
