package com.lhzkml.jasmine.core.ui.base

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

/**
 * 出站命令执行器：UDF 里**唯一**允许"绕过 action 通道去碰边界"的出口（P0 修复方案 D3）。
 *
 * 与 action 通道同一套语义：无界排队（命令不丢）、单消费者（严格 FIFO）、同步 handler 只负责
 * 发命令。每条命令的结局都回流成 action —— 成功由 [perform] 的返回值带回（可空），失败由
 * [onFailure] 兜底（各自造 `*Rejected` / `*Failed`），状态仍然只在 `handleAction` 里改。
 *
 * 取消必须能穿透：[perform] 抛出的 [CancellationException] 原样重抛，不能被兜底吞成一次"失败"。
 *
 * 注意：命令**不保序于其它协程里的边界调用**。凡是必须与另一次边界调用严格有序的本地操作
 * （典型：释放一条会话的核心附着，它必须排在下一次附着之前），不要走这里 —— 那种操作是
 * 注册表维护，同步做、失败回流（见各 ViewModel 的 release/onCleared 注释）。
 */
class EffectRunner<F, A>(
    scope: CoroutineScope,
    private val sendAction: suspend (A) -> Unit,
    private val perform: suspend (F) -> A?,
    private val onFailure: (F, Throwable) -> A,
) {
    private val channel = Channel<F>(Channel.UNLIMITED)

    init {
        scope.launch {
            channel.consumeAsFlow().collect { effect ->
                val followUp = try {
                    perform(effect)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    onFailure(effect, error)
                }
                followUp?.let { sendAction(it) }
            }
        }
    }

    /**
     * 发射一条命令。无界通道只在作用域已取消时才拒收 —— 那时拥有它的界面已经走了，
     * 丢弃是设计行为（与 action 通道同一约定）。
     */
    fun send(effect: F) {
        channel.trySend(effect)
    }
}
