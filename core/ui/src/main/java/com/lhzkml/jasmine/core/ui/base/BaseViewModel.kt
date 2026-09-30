package com.lhzkml.jasmine.core.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * A base [ViewModel] that helps enforce the unidirectional data flow pattern and associated
 * responsibilities of a typical ViewModel:
 *
 * - Maintaining and emitting a current state (of type [S]) with the given `initialState`.
 * - Emitting one-shot events as needed (of type [E]). These should be rare and are typically
 *   reserved for things such as non-state based navigation.
 * - Receiving actions (of type [A]) that may induce changes in the current state, trigger an
 *   event emission, or both.
 *
 * Channel capacities are [Channel.UNLIMITED] on purpose: user intents must be
 * queued, never dropped — a lost tap corrupts UI state, a queued one is only
 * delayed. [handleAction] runs synchronously on a single consumer coroutine, so
 * the queue drains in microseconds and stays strict FIFO; a growing backlog
 * indicates a blocking handler bug, not a capacity problem. Do NOT switch to a
 * bounded capacity: a full bounded buffer would silently discard user intents.
 */
abstract class BaseViewModel<S, E, A>(
    initialState: S,
) : ViewModel() {
    protected val mutableStateFlow: MutableStateFlow<S> = MutableStateFlow(initialState)

    private val eventChannel: Channel<E> = Channel(capacity = Channel.UNLIMITED)
    private val internalActionChannel: Channel<A> = Channel(capacity = Channel.UNLIMITED)

    /**
     * A helper that returns the current state of the view model.
     *
     * `open`（P0 修复方案 D1）：子类可以把状态做成"基座状态 ⊕ 派生部分"的同步重建
     * （见 ChatViewModel 的每会话投影），handler 的读-判-写仍然同帧。
     */
    protected open val state: S get() = mutableStateFlow.value

    /**
     * A [StateFlow] representing state updates.
     *
     * `open`（D1）：子类可以暴露一条派生流（如 combine 全局状态与每会话投影）。覆写时
     * 必须与 [state] 用同一个投影函数，保证"handler 读到的"与"界面看到的"是同一份。
     */
    open val stateFlow: StateFlow<S> = mutableStateFlow.asStateFlow()

    /**
     * A [Flow] of one-shot events. These may be received and consumed by only a single consumer.
     * Any additional consumers will receive no events.
     */
    val eventFlow: Flow<E> = eventChannel.receiveAsFlow()

    /**
     * A [SendChannel] for sending actions to the ViewModel for processing.
     */
    val actionChannel: SendChannel<A> = internalActionChannel

    init {
        viewModelScope.launch {
            internalActionChannel
                .consumeAsFlow()
                .collect { action ->
                    handleAction(action)
                }
        }
    }

    /**
     * Handles the given [action] in a synchronous manner.
     *
     * Any changes to internal state that first require asynchronous work should post a follow-up
     * action that may be used to then update the state synchronously.
     */
    protected abstract fun handleAction(action: A)

    /**
     * Convenience method for sending an action to the [actionChannel].
     *
     * With the unlimited channel this fails only once the channel is closed,
     * i.e. after [ViewModel.onCleared]; such actions are dropped by design
     * because the owning screen is gone. Returns whether the action was
     * accepted, so the (deliberately rare) drop is observable to callers and
     * tests instead of being silently swallowed.
     */
    fun trySendAction(action: A): Boolean = actionChannel.trySend(action).isSuccess

    /**
     * Helper method for sending an internal action.
     */
    protected suspend fun sendAction(action: A) {
        actionChannel.send(action)
    }

    /**
     * Helper method for sending an event.
     *
     * 与状态更新**同帧入队**（不用 `launch` 转发）：handler 里 `updateState` 之后紧接着发的事件，
     * 其顺序就等于触发顺序 —— 界面收到事件时，它对应的状态一定已经落定。通道无界，唯一会失败
     * 的场景是 `onCleared` 之后通道已关闭，那时界面已经走了，丢弃是设计行为。
     */
    protected fun sendEvent(event: E) {
        eventChannel.trySend(event)
    }

    /**
     * 关闭两条通道（F4）。
     *
     * `viewModelScope` 被取消只让**消费**协程停下来，通道本身还开着 —— 于是 `onCleared` 之后
     * [trySendAction] 仍然返回 `true`（动作却永远没人处理），正是它自己文档里想避免的那种"静默
     * 吞掉"。关掉之后返回值才说真话：调用方与用例都能看出"这个动作不会有人接"。
     */
    override fun onCleared() {
        internalActionChannel.close()
        eventChannel.close()
        super.onCleared()
    }
}
