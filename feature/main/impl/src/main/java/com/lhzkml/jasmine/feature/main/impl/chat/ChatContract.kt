package com.lhzkml.jasmine.feature.main.impl.chat

import androidx.annotation.StringRes
import com.lhzkml.jasmine.core.agent.ChatFailureKind
import com.lhzkml.jasmine.core.agent.ContextUsage
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.markdown.model.MarkdownBlock
import com.lhzkml.jasmine.core.markdown.model.MarkdownUpdate
import kotlinx.coroutines.CompletableDeferred

/**
 * Actions sent from the UI to [ChatViewModel].
 */
sealed interface ChatAction {
    data class InputChanged(val value: String) : ChatAction
    data object SendClicked : ChatAction

    /** 停止正在进行的回复：让核心收手，见 [handleStopClicked]。 */
    data object StopClicked : ChatAction

    /** 继续被中断的回合：不加用户消息，接着采样，见 [handleContinueClicked]。 */
    data object ContinueClicked : ChatAction
    data object NewConversationClicked : ChatAction
    data object ModelPickerOpened : ChatAction
    data object ModelPickerDismissed : ChatAction

    /** 输入框左侧的环形入口：点一下弹出上下文容量面板。 */
    data object ContextPanelOpened : ChatAction
    data object ContextPanelDismissed : ChatAction
    data class ModelSelected(val providerId: String, val modelId: String) : ChatAction

    /** 模型面板里改了当前会话的上下文窗口（token 数）。 */
    data class ContextWindowSelected(val tokens: Long) : ChatAction

    /** 用户点了工具卡头：[expanded] 是他要的状态（不是"翻转"，界面已经算好了）。 */
    data class ToolRowToggled(val rowKey: String, val expanded: Boolean) : ChatAction

    /**
     * 输入框里的「推理强度」切了一档：改的是**当前模型**的配置（codex 的 `ReasoningEffort` 那四档，
     * 空串 = 未设置 = 请求里不发推理字段），见 [handleThoughtLevelSelected]。
     */
    data class ThoughtLevelSelected(val value: String) : ChatAction
    data class ConversationSelected(val id: String) : ChatAction
    data class ConversationDeleted(val id: String) : ChatAction

    /** The user's answer to the pending question; resumes the paused turn. */
    data class PromptAnswered(val answer: String) : ChatAction

    /**
     * Internal actions: results of asynchronous work posted back onto the action
     * channel so that all state mutations stay synchronous inside [handleAction].
     */
    sealed interface Internal : ChatAction {
        data class ProvidersReceived(val providers: List<ProviderConfig>) : Internal
        data class ConversationsReceived(val conversations: List<Conversation>) : Internal
        data class ActiveModelReceived(val providerId: String, val modelId: String) : Internal

        /** 偏好里的"模型回复语言"（[com.lhzkml.jasmine.core.data.model.AgentOutputLanguage]）。 */
        data class LanguagePreferenceReceived(val value: String) : Internal
        /**
         * 启动时那条"最近改过"的会话回来了。消息**已经解析好**（正文要过 native，见
         * [restoreLatestConversation]）—— handler 只落状态，主线程不等几百次 JNI 往返。
         */
        data class TranscriptRestored(
            val conversationId: String,
            val messages: List<ChatMessage>,
        ) : Internal
        // ── 回合输出：都带 [turnId] —— 落到**它的**那一轮上（见 [ChatViewModel.Turn]）──

        data class ReplyChunk(val turnId: String, val text: String) : Internal
        data class ReasoningChunk(val turnId: String, val text: String) : Internal
        data class ToolCalled(val turnId: String, val name: String, val arguments: String) : Internal
        data class ToolReturned(val turnId: String, val name: String, val result: String) : Internal
        data class PromptRequested(
            val turnId: String,
            val prompt: String,
            val options: List<String>,
        ) : Internal
        data class TurnFailed(
            val turnId: String,
            val detail: String,
            /** 失败的**分型**（核心跨边界报的）；本地异常没有分型时为 [ChatFailureKind.UNKNOWN]。 */
            val kind: ChatFailureKind = ChatFailureKind.UNKNOWN,
        ) : Internal
        data class TurnCompleted(val turnId: String) : Internal
        data class TurnInterrupted(val turnId: String, val durationMs: Long) : Internal
        data class UsageReceived(val turnId: String, val usage: ContextUsage) : Internal

        // ── 异步结果的回流口（修复方案 §2.3）：每一类异步工作都有自己的一条 action ──

        /** 这条会话是不是有一个没写完的回合（读会话文件之后回流；见 [handleTranscriptRestored]）。 */
        data class CanContinueResolved(
            val conversationId: String,
            val canContinue: Boolean,
        ) : Internal

        /** 打开/恢复一条会话时，它自己的窗口、用量、档位（见 [readConversationFacts]）。 */
        data class ConversationFactsLoaded(val facts: ConversationFacts) : Internal

        /** 一条会话的消息列表读回来了（守卫在 handler 里做）；消息已经解析好，同 [TranscriptRestored]。 */
        data class TranscriptLoaded(
            val conversationId: String,
            val messages: List<ChatMessage>,
        ) : Internal

        /** 上下文窗口写进核心成功。 */
        data class ContextWindowApplied(val conversationId: String, val tokens: Long) : Internal

        /** 上下文窗口写失败：带回核心的当前值，界面照它回退（null = 读不到，不写）。 */
        data class ContextWindowRejected(
            val conversationId: String,
            val coreValue: Long?,
            val message: String,
        ) : Internal

        /** 推理档位写进核心成功（[value] 是核心读回来的权威值）。 */
        data class ReasoningEffortApplied(
            val conversationId: String,
            val value: String,
        ) : Internal

        /** 推理档位写失败：带回核心的当前值（null = 读不到，不写）。 */
        data class ReasoningEffortRejected(
            val conversationId: String,
            val coreValue: String?,
            val message: String,
        ) : Internal

        /** 当前模型允许的档位（核心目录）读回来了。 */
        data class AllowedEffortsLoaded(
            val providerId: String,
            val modelId: String,
            val levels: List<String>,
        ) : Internal

        /** 附着会话之后，从核心读回来的档位。 */
        data class ReasoningEffortSynced(
            val conversationId: String,
            val value: String,
        ) : Internal

        /** 附着会话之后，从核心读回来的窗口；null = 核心没记过（保持原值不写）。 */
        data class ContextWindowSynced(
            val conversationId: String,
            val value: Long?,
        ) : Internal

        /** 会话行创建完成；[epoch] 与当前代次不符就丢弃（见 [conversationEpoch]）。 */
        data class ConversationCreated(val epoch: Long, val id: String) : Internal

        /**
         * 一条会话附着完成（核心那边已确认）：[key] = `会话|provider|model`。
         *
         * `attachedKeys` 的**唯一**异步回流入口 —— 注册表写在 handler 里落（修复方案 D2），
         * 协程（`runTurn` / `runContinuedTurn`）只发这条 action，自己不动注册表。
         */
        data class ConversationAttached(val conversationId: String, val key: String) : Internal

        /**
         * 附着时待补交的窗口值已经交给 Effect：回 handler 清除 pending。
         *
         * 清除带**等值守卫**（`if (pending == value) pending = null`）：补交在飞、用户又选了一个
         * 新值时，迟到的这条回流不能把新值抹掉。
         */
        data class PendingContextWindowConsumed(val value: Long) : Internal

        /** 附着时待补交的档位已交给 Effect：同 [PendingContextWindowConsumed] 的等值守卫。 */
        data class PendingReasoningEffortConsumed(val value: String) : Internal

        /**
         * 一条会话的 keep-warm 到期。
         *
         * 到期**判定**（有没有新一轮在跑、是不是正显示着）必须在 handler 这一帧里做 ——
         * delay 之后协程里直接 `chats.drop` 是异步写影子状态（修复方案 D2 收编的口子）。
         */
        data class KeepWarmExpired(val key: String) : Internal

        /**
         * 一次解析结果（见 [deliverParsed]）。
         *
         * 贴完之后**必须** `ack.complete(Unit)`：worker 正挂在 [deliverParsed] 的 `ack.await()` 上等这一句，
         * 漏了就永远等下去、回合 `join()` 不回来。
         */
        data class StreamParsed(
            val turnId: String,
            val targetId: String,
            val update: MarkdownUpdate?,
            val trailingBlock: MarkdownBlock?,
            val ack: CompletableDeferred<Unit>,
        ) : Internal

        /**
         * 这一轮走完了：把它的登记撤掉（见 [launchTurn]）。
         *
         * 走 action 通道、而不是在回合那个协程里直接删：这一轮**最后那批输出也是排队过来的**，直接
         * 删掉的话排在后面的动作就找不到轮次了（它那条会话与游标随之丢掉，最后几段就白流了）。排在
         * 它们后面发，就没有这个缝。
         */
        data class TurnRetired(val turnId: String) : Internal

        /** Effect 的失败兜底（没有乐观写入的命令走这条，见 [ChatEffect]）。 */
        data class EffectFailed(
            val tag: String,
            val message: String,
            val conversationId: String? = null,
        ) : Internal

        /**
         * 模型选择落盘失败（修复方案 D4）：带回乐观值与回退值 —— handler 只在当前选择仍等于
         * 乐观值时回滚（用户可能已经又选了别的）。
         */
        data class ActiveModelPersistRejected(
            val optimisticProviderId: String,
            val optimisticModelId: String,
            val fallbackProviderId: String,
            val fallbackModelId: String,
            val message: String,
        ) : Internal
    }
}

/**
 * 这个 ViewModel 的一次性 UI 效果（即 `BaseViewModel` 的 `E`）。
 *
 * 名字**不能**叫 `ChatEvent`：`core:agent` 已经占用了那个名字，本文件在用（见文件头的 import 与
 * [collectEvents] 里那 9 处 `ChatEvent.*`），同文件再声明一个会遮蔽那条 import。
 */
sealed interface ChatUiEvent {
    data class ShowToast(@StringRes val messageRes: Int) : ChatUiEvent

    /**
     * 可读的错误提示：文案走字符串资源（EN/ZH 双语），原始异常文本只进 [detail]（日志与排查用）。
     */
    data class ShowError(@StringRes val messageRes: Int, val detail: String = "") : ChatUiEvent
}

/**
 * 出站命令：唯一允许"绕过 action 通道去碰边界"的出口，但每条都带回执或失败回流。
 *
 * 命令不是状态 —— 由 EffectRunner（单消费者，见类的 init）执行；成功/失败一律以
 * [ChatAction.Internal] 回流，状态仍然只在 `handleAction` 里改。
 */
sealed interface ChatEffect {
    data class SetContextWindow(override val conversationId: String, val tokens: Long) : ChatEffect
    data class SetReasoningEffort(
        override val conversationId: String,
        val value: String,
    ) : ChatEffect

    /** 停掉 [conversationId] 那条会话正在跑的那一轮（别的会话不受影响）。 */
    data class Interrupt(override val conversationId: String) : ChatEffect
    data class DeleteConversation(val id: String) : ChatEffect
    data object RefreshConversations : ChatEffect

    /**
     * 把当前模型选择落盘（修复方案 D4）。失败回流 [ChatAction.Internal.ActiveModelPersistRejected]，
     * 带回乐观值与回退值做身份守卫回滚。
     */
    data class PersistActiveModel(
        val providerId: String,
        val modelId: String,
        val fallbackProviderId: String,
        val fallbackModelId: String,
    ) : ChatEffect

    /**
     * 这条命令冲着哪条会话去的；没有会话归属的（删除 / 刷新）为 null。
     *
     * 失败兜底按它把"这条命令的后果"落回**它自己的**会话：用户可能在命令飞出去的这段时间里
     * 已经切走了，回退与状态位都不该写到界面上现在那条会话上。
     */
    val conversationId: String? get() = null

    /** 失败兜底用的标签（日志 + [ChatAction.Internal.EffectFailed] 的 `tag`）。 */
    fun tag(): String = javaClass.simpleName
}
