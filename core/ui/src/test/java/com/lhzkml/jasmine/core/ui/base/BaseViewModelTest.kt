package com.lhzkml.jasmine.core.ui.base

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [BaseViewModel] 的地基语义：动作队列的收放与事件通道。
 *
 * 这些是所有 ViewModel 共用的东西，语义漂移会全线扩散，而门禁测试（只认 `updateState` 的正则）
 * 看不到这里。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 最小替身：收到的 action 记下来，好断言"是不是真被处理了"。 */
    private class Probe : BaseViewModel<Int, String, String>(0) {
        val handled = mutableListOf<String>()

        override fun handleAction(action: String) {
            handled += action
        }

        fun bump() {
            mutableStateFlow.value = state + 1
        }

        fun currentState(): Int = state

        fun emit(event: String) = sendEvent(event)

        fun clear() = onCleared()
    }

    /** 还活着时，动作按送入顺序被处理（无界通道 = 排队，不是丢弃）。 */
    @Test
    fun `actions are handled in the order they are sent`() = runTest(dispatcher) {
        val viewModel = Probe()

        assertTrue(viewModel.trySendAction("a"))
        assertTrue(viewModel.trySendAction("b"))
        assertTrue(viewModel.trySendAction("c"))

        assertEquals(listOf("a", "b", "c"), viewModel.handled)
    }

    /**
     * `onCleared` 之后 `trySendAction` 必须说真话（F4）。
     *
     * 以前只取消 `viewModelScope`（消费协程停了）、通道却一直开着，于是它返回 `true` 而动作永远
     * 没人处理 —— 正是这个方法自己的文档想避免的"静默吞掉"。
     */
    @Test
    fun `trySendAction reports the drop once the view model is cleared`() = runTest(dispatcher) {
        val viewModel = Probe()
        assertTrue("还活着时要收下", viewModel.trySendAction("a"))

        viewModel.clear()

        assertFalse("onCleared 之后必须返回 false", viewModel.trySendAction("b"))
        assertEquals("那条动作不该被处理", listOf("a"), viewModel.handled)
    }

    /**
     * 事件与状态更新**同帧入队**（C1）：界面收到事件时，它对应的状态已经落定。
     *
     * 这条是把 C1 那句"确定性本身可测"落成断言 —— 当时承诺过，但没有写。
     */
    @Test
    fun `an event arrives only after the state it belongs to has settled`() = runTest(dispatcher) {
        val viewModel = Probe()
        val seen = mutableListOf<Pair<String, Int>>()
        val job = launch { viewModel.eventFlow.collect { seen += it to viewModel.currentState() } }

        viewModel.bump()
        viewModel.emit("看到了")
        advanceUntilIdle()

        assertEquals(listOf("看到了" to 1), seen)
        job.cancel()
    }
}
