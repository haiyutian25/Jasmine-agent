package com.lhzkml.jasmine.core.ui.base.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [collectEventsWhileResumed] 的语义（F5）：**停驻期间的事件是排队等着，不是被丢掉**。
 *
 * 这条以前用的是 `.filter { isAtLeast(RESUMED) }`，而事件流是 `receiveAsFlow()`（单消费者）——
 * 事件在 `filter` 那一步已经被取走，滤掉就是永久消失：屏幕停在 STARTED 时产生的失败提示会
 * 悄悄不见，与"失败必须可观察"正好相反。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventsEffectTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** `createUnsafe` 才允许在非主线程（单元测试）里推动生命周期。 */
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `an event raised while stopped is delivered once the screen resumes`() = runTest(dispatcher) {
        val owner = Owner()
        val events = Channel<String>(Channel.UNLIMITED)
        val received = mutableListOf<String>()
        owner.registry.currentState = Lifecycle.State.RESUMED

        val job = launch {
            collectEventsWhileResumed(events.receiveAsFlow(), owner.lifecycle) { received += it }
        }
        advanceUntilIdle()

        // 前台：当场收到。
        events.trySend("前台发生的")
        advanceUntilIdle()
        assertEquals(listOf("前台发生的"), received)

        // 退到后台：这一段里发生的事件不许丢。
        owner.registry.currentState = Lifecycle.State.STARTED
        advanceUntilIdle()
        events.trySend("后台发生的")
        advanceUntilIdle()
        assertEquals("停驻期间不消费，但也不许丢", listOf("前台发生的"), received)

        // 回到前台：排队的那件补上。
        owner.registry.currentState = Lifecycle.State.RESUMED
        advanceUntilIdle()
        assertEquals(listOf("前台发生的", "后台发生的"), received)

        job.cancel()
    }
}
