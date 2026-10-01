package com.lhzkml.jasmine.core.ui.base

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EffectRunner] 的三条不变量（G6；C1 那批"承诺过但没写"的验收测试）。
 *
 * 它是 UDF 里**唯一**允许绕过 action 通道去碰边界的出口，全工程六条乐观写都走它：
 * 排错一次就是"落盘顺序乱了 / 失败没人回滚 / 取消被当成失败弹个红字"。这三条都是纯 JVM 可测的。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EffectRunnerTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /**
     * 单消费者：一条命令在跑时，下一条**不许**开始。
     *
     * 落盘这类命令有先后依赖（先写 A 再写 B 与先 B 后 A 结果不同），并发跑会让最终落盘的
     * 是哪一个变成运气。
     */
    @Test
    fun `commands run one at a time in the order they were sent`() = runTest(dispatcher) {
        val running = mutableListOf<String>()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val runner = EffectRunner<String, String>(
            scope = CoroutineScope(SupervisorJob() + dispatcher),
            sendAction = {},
            perform = { effect ->
                running += effect
                if (effect == "a") {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                null
            },
            onFailure = { effect, _ -> "unexpected-failure:$effect" },
        )

        runner.send("a")
        runner.send("b")
        advanceUntilIdle()

        assertTrue("第一条要真的跑起来了", firstStarted.isCompleted)
        assertEquals("第二条必须等第一条跑完", listOf("a"), running)

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), running)
    }

    /** 成功由 [EffectRunner] 的 `perform` 返回值回流；失败由 `onFailure` 造一条回流 —— 两条路都要落到同一个 action 通道上。 */
    @Test
    fun `both outcomes reflow as follow-up actions`() = runTest(dispatcher) {
        val reflows = mutableListOf<String>()
        val runner = EffectRunner<String, String>(
            scope = CoroutineScope(SupervisorJob() + dispatcher),
            sendAction = { reflows += it },
            perform = { effect ->
                if (effect == "boom") error("落盘失败")
                "ok:$effect"
            },
            onFailure = { effect, error -> "failed:$effect:${error.message}" },
        )

        runner.send("write")
        runner.send("boom")
        advanceUntilIdle()

        assertEquals(
            listOf("ok:write", "failed:boom:落盘失败"),
            reflows,
        )
    }

    /**
     * 取消**穿透**，不被兜底成一次失败。
     *
     * `perform` 抛的 [CancellationException] 是"这个 ViewModel 被销毁了"，不是"这条命令失败了"：
     * 当成失败就会回滚状态并弹一个"保存失败"的红字 —— 界面都已经走了。这条正是 `catch
     * (cancellation) { throw cancellation }` 那一行的存在理由。
     */
    @Test
    fun `a cancelled command is not reported as a failure`() = runTest(dispatcher) {
        val reflows = mutableListOf<String>()
        val started = CompletableDeferred<Unit>()
        val runner = EffectRunner<String, String>(
            scope = CoroutineScope(SupervisorJob() + dispatcher),
            sendAction = { reflows += it },
            perform = { effect ->
                started.complete(Unit)
                throw CancellationException("$effect 被取消")
            },
            onFailure = { effect, _ -> "failed:$effect" },
        )

        runner.send("write")
        advanceUntilIdle()

        assertTrue("命令要真的跑到", started.isCompleted)
        assertEquals("取消不许被兜成 *Rejected", emptyList<String>(), reflows)
    }

    /** 作用域取消之后 `send` 是安全的 no-op（拥有它的界面已经走了），不会抛。 */
    @Test
    fun `sending into a cancelled scope is a no-op`() = runTest(dispatcher) {
        val performed = AtomicInteger(0)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val runner = EffectRunner<String, String>(
            scope = scope,
            sendAction = {},
            perform = {
                performed.incrementAndGet()
                null
            },
            onFailure = { effect, _ -> "failed:$effect" },
        )

        scope.cancel()
        runner.send("write")
        advanceUntilIdle()

        assertEquals(0, performed.get())
    }
}
