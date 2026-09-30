package com.lhzkml.jasmine.feature.main.impl.chat

import com.lhzkml.jasmine.core.agent.ContextUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 一条会话自己的聊天状态。
 *
 * 照 ZCode 的 `ConversationProjectionStore`：消息、发送中、提问、窗口/档位、用量这些**属于那条会话**
 * 的东西各自一份，不共用一个全局的"当前消息列表"。切换会话只是换显示哪一份，正在跑的那一轮照旧往
 * 它自己那份里写 —— 切回来看到的还是它，接着渲染。
 */
data class ConversationChatState(
    val messages: List<ChatMessage> = emptyList(),

    /**
     * 工具卡的展开态：**用户显式拨过**的那些行（键 = 那条消息的 id）。
     *
     * 键不存在 = 用户没拨过 —— 这时界面按运行状态自动开合（跑着展开、结果回来收起）。它是这条会话
     * 的东西，跟着这份状态一起存。
     */
    val toolRowOpen: Map<String, Boolean> = emptyMap(),
    val input: String = "",
    val isSending: Boolean = false,
    /** 上一回合被中断了，输入区的按钮因此是「继续」形态；见 [ChatViewModel.handleContinueClicked]。 */
    val canContinue: Boolean = false,
    /** 最近一轮回答之后核心报的上下文用量；一次都还没答过时为 null。 */
    val contextUsage: ContextUsage? = null,
    /**
     * 这条会话的上下文窗口（token 数）。
     *
     * 会话第一次附着时核心定下它，之后以核心报的值为准 —— 会话中途换模型不会变。
     */
    val contextWindow: Long = DEFAULT_CONTEXT_WINDOW_TOKENS,
    /**
     * 这条会话的推理档位（codex 那套值；空串 = 未设置）。第一次附着时核心按目录起点档抄一次，
     * 打开旧会话时用它自己文件里的记录填回来。
     */
    val reasoningEffort: String = "",
    /**
     * 已挂出、还没被回答的提问（按提问顺序排队）；队首就是界面正在问的那一个。
     *
     * 它属于**这条会话**：几条会话可以同时各挂着自己的问题（见 [ChatViewModel.Turn]），互不顶替。
     */
    val pendingPrompts: List<ChatUserPrompt> = emptyList(),
    /** 已经收到的回答（与提问顺序一一对应）；这一批问题全部答完后一起提交。 */
    val promptAnswers: List<String> = emptyList(),
    /**
     * 已发出中断请求、**且这一轮尚未收尾**（见 [ChatViewModel.handleStopClicked]）。
     *
     * 只在一轮的收尾（`TurnCompleted` / `TurnInterrupted`）或中断命令失败时清掉。
     */
    val isInterruptRequested: Boolean = false,
) {
    /** Set while the agent is blocked on a question; see [ChatUserPrompt]. */
    val pendingPrompt: ChatUserPrompt? get() = pendingPrompts.firstOrNull()
}


/**
 * 每一条会话自己的那份聊天状态。
 *
 * 键是会话 id；[NEW_CONVERSATION] 是"新建但还没发出第一条消息、因此还没有 id"的那一份。
 *
 * 照 ZCode 的做法：**谁在跑、写到哪一份**由那一轮自己带着（见 `ChatViewModel.Turn`），这里只管
 * 存这份状态 —— 与当前显示哪条无关。会话切走之后还热一阵（[KEEP_WARM_MS]），期间切回来直接复用
 * 内存里这份（已经流出去的片段都还在，接着渲染）；冷掉之后才回到"从核心读转写"。
 *
 * 修复方案 D1：这张表是 `StateFlow`，"当前显示哪一条"的投影由 `ChatViewModel` 用 combine 从
 * 这张表**派生** —— 写会话状态不再需要手动投影，"内存有、界面没"这一类 bug 在结构上消失。
 *
 * 写纪律不变（门禁 R8 守护）：[update] / [drop] / [rekey] 只许由同步 handler 调用。
 */
class ConversationChats {
    private val mutableFlow = MutableStateFlow<Map<String, ConversationChatState>>(emptyMap())

    /** 整张表的只读流：派生投影（combine）的输入。 */
    val flow: StateFlow<Map<String, ConversationChatState>> = mutableFlow.asStateFlow()

    /** 读一条会话的状态；没存过就是默认空态（**只读，不建条目** —— 建条目是 update 的事）。 */
    fun stateOf(key: String): ConversationChatState =
        flow.value[key] ?: ConversationChatState()

    fun has(key: String): Boolean = flow.value.containsKey(key)

    /** 改一条会话的状态（**只由 handler 同步调用**，与 `ChatViewModel.updateState` 同一条规矩）。 */
    fun update(key: String, transform: ConversationChatState.() -> ConversationChatState) {
        mutableFlow.update { map -> map + (key to stateOf(key).transform()) }
    }

    /** 丢掉一份会话状态（keep-warm 到期经 Internal 回流后，或这条会话被删掉）。 */
    fun drop(key: String) {
        mutableFlow.update { map -> map - key }
    }

    /**
     * 那条"还没有 id"的新会话拿到了真正的 id：把它这份状态**挪过去**。
     *
     * 正在跑的那一轮的写入口也跟着挪（`ChatViewModel.rekeyTurns`）—— 否则这一轮的输出会继续写到
     * 空键上，界面投影到新键时就是一片空白。
     */
    fun rekey(from: String, to: String) {
        if (from == to) return
        mutableFlow.update { map ->
            val entry = map[from] ?: return@update map
            map - from + (to to entry)
        }
    }

    /** 只给测试与调试看：现在留着几份。 */
    val size: Int get() = flow.value.size

    companion object {
        /** "新建但还没发第一条消息"那一份的键。 */
        const val NEW_CONVERSATION: String = ""

        /** 回合结束后，会话状态在内存里保留的时长（照 ZCode 的 keep-warm）。 */
        const val KEEP_WARM_MS: Long = 30_000
    }
}
