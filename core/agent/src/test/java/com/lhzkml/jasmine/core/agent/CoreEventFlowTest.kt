package com.lhzkml.jasmine.core.agent

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.jasmine_protocol.ChatEvent as CoreChatEvent

/**
 * [coreEventFlow] 的不变量（F3）。
 *
 * 这个函数是从 `RustAgentChat` 里提出来专为可测的：它只依赖 `EventListener` 与 `ChatEvent`，
 * 不需要真的核心句柄。
 *
 * **每个用例都带超时**：这里要防的 bug 是"通道没收口"，它的表现是**永久挂起**而不是报错 ——
 * 没有超时的话，回归时测试会卡死而不是变红。
 */
class CoreEventFlowTest {

    /**
     * 非 [AgentFailure] 的异常也必须兜住、并且让 Flow 收口。
     *
     * `AgentChat.send` 的契约写明会话没附着时会抛 `IllegalStateException`，而生成绑定里这些方法
     * 并不声明 `AgentFailure` —— 只兜 `AgentFailure` 的话，这个异常会逃到 `CoroutineScope(IO)`
     * 这个没有 handler 的根作用域，而且跳过收口：收集方永久挂起、界面永远停在"正在生成"。
     */
    @Test
    fun `a failure that is not an AgentFailure still closes the flow`() = runBlocking {
        val events = withTimeout(5_000) {
            coreEventFlow(run = { throw IllegalStateException("尚未附着会话") }).toList()
        }

        assertEquals("应该正好一条失败事件", 1, events.size)
        val failed = events.single()
        assertTrue("应该是失败事件，实际是 $failed", failed is ChatEvent.Failed)
        failed as ChatEvent.Failed
        assertEquals("本地抛的调用时序问题算内部错误", ChatFailureKind.INTERNAL, failed.kind)
        assertTrue("原文要带上，别丢掉原因", failed.detail.contains("尚未附着会话"))
    }

    /** 正常路径：事件按顺序出去，终态之后 Flow 收口。 */
    @Test
    fun `a call that reports and returns closes the flow after its events`() = runBlocking {
        val events = withTimeout(5_000) {
            coreEventFlow(
                run = { listener ->
                    listener.onEvent(CoreChatEvent.Text("你好"))
                    listener.onEvent(CoreChatEvent.Completed)
                },
            ).toList()
        }

        assertEquals(listOf(ChatEvent.Text("你好"), ChatEvent.Completed), events)
    }

    /**
     * 收集方中途走人、而核心还在采样：通报一次"收手"。
     *
     * 这是通道覆盖不到的那种情况 —— 取消收集协程够不到核心自己的线程，所以反过来请核心停下
     * （见 `RustAgentChat.turn` 的说明）。
     */
    @Test
    fun `an abandoned collection asks the core to stop once`() = runBlocking {
        val abandoned = AtomicInteger(0)
        val emitted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.IO) {
            coreEventFlow(
                run = { listener ->
                    listener.onEvent(CoreChatEvent.Text("第一段"))
                    emitted.complete(Unit)
                    // 核心还在采样：不返回，也不报终态。
                    runBlocking { release.await() }
                },
                onAbandoned = { abandoned.incrementAndGet() },
            ).collect { }
        }

        withTimeout(5_000) {
            while (!emitted.isCompleted) delay(10)
        }
        job.cancelAndJoin()
        release.complete(Unit)

        assertEquals("收手只通报一次", 1, abandoned.get())
    }
}
