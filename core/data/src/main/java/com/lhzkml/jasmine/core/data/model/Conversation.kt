package com.lhzkml.jasmine.core.data.model

/**
 * A persisted chat conversation (metadata only — the transcript itself is a list
 * of [TranscriptMessage]).
 *
 * [providerId] / [modelId] record which model produced it so the chat can resume
 * with the same one; [updatedAt] orders the history list.
 */
data class Conversation(
    val id: String,
    val title: String,
    val providerId: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One transcript message as persisted. Carries no id: the UI owns the identity it
 * needs for list keys, and nothing else refers to an individual message.
 */
data class TranscriptMessage(
    val role: ChatRole,
    val text: String,
    val isError: Boolean = false,
    /**
     * 执行痕迹（工具调用 / 工具返回）。非 null 时 [text] 为空，界面按工具条目渲染而不是气泡 ——
     * 这样「重新加载出来的转写」和「实时那一轮」是同一个形状。
     */
    val tool: TranscriptToolActivity? = null,
    /**
     * 这条消息的时间（epoch 毫秒，取自 ADK 事件的 `timestamp`，也就是 `StorageEvent.timestamp`）。
     *
     * 0 表示没有时间信息（老数据 / 造不出来源的条目），界面会直接不显示时间。
     */
    val timestamp: Long = 0L,
    /**
     * 产生这条消息的模型名（取自 ADK 事件的 `modelVersion`）。
     *
     * null 表示事件里没记（这份 ADK 移植版以前没填 `modelVersion`，所以旧数据都是 null）——
     * 界面这时回退到「会话记录里的模型」，会话中途换过模型的旧消息因此可能显示得不准。
     */
    val modelLabel: String? = null,
)

/**
 * 存进转写的一条工具活动 —— 界面上就是一张卡片。
 *
 * 一次调用的「问了什么」和「回了什么」放在同一条里：[detail] 是调用参数，[result] 是返回。
 * 返回可能是工具自己的输出，也可能是用户对提问的回答（ADK 把那种返回记成 author=user 的
 * functionResponse，见 `AdkConversationStore.toTranscriptMessage`）。
 */
data class TranscriptToolActivity(
    val name: String,
    /** 调用参数。只有返回、没有配对调用时为空字符串。 */
    val detail: String,
    /** 工具返回；null 表示还没有返回。 */
    val result: String? = null,
) {
    /** 没有配对的调用事件，只有返回 —— 界面标题画成「xxx 返回」。 */
    val isResultOnly: Boolean get() = detail.isEmpty()
}
