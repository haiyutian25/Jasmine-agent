package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.types.FunctionDeclaration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * OpenAI Chat Completions 协议的线上格式（请求 / 响应 / 增量分片）。
 *
 * 共用的 JSON 配置、SSE 传输、Schema 展平与工具参数解析见 `OpenAiWire.kt`；
 * Responses 协议见 `OpenAiResponsesWire.kt`。与它们一致：未知键忽略、不写入
 * null、响应字段全给默认值，以容忍兼容供应商之间的字段差异。
 */

// ── 请求侧 ────────────────────────────────────────────────────────────

@Serializable
internal data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ChatTool>? = null,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val stream: Boolean = false,
)

/**
 * 请求侧的消息：[role] **必填**，因为它只用于发出去的请求体。
 *
 * 响应侧刻意不复用本类——见 [ChatDelta]。
 */
@Serializable
internal data class ChatMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ChatToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

/**
 * 注意 [type] **不能带默认值**：`openAiJson` 关闭了 `encodeDefaults`，带默认值的
 * 属性在与默认值相同时不会被序列化，而 OpenAI 要求 `tools[].type` 必填——
 * 一旦省略，带工具的请求会被直接拒绝。
 */
@Serializable
internal data class ChatTool(
    val type: String,
    val function: ChatToolFunction,
)

@Serializable
internal data class ChatToolFunction(
    val name: String,
    val description: String = "",
    val parameters: JsonObject = EMPTY_JSON_OBJECT,
)

@Serializable
internal data class ChatToolCall(
    /**
     * Position of this call within the message. A stream repeats it on every frame of
     * the same call — which is the only thing that makes reassembly possible, since the
     * frames themselves carry no other clue that they belong together.
     */
    val index: Int? = null,
    val id: String? = null,
    val type: String? = null,
    val function: ChatToolCallFunction? = null,
)

@Serializable
internal data class ChatToolCallFunction(
    val name: String? = null,
    val arguments: String? = null,
)

// ── 响应侧 ────────────────────────────────────────────────────────────

@Serializable
internal data class ChatResponse(
    val id: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val usage: ChatUsage? = null,
    val error: ChatError? = null,
)

/**
 * 响应侧的消息 / 增量分片：[role] 在这里**必须可选**。
 *
 * 与请求侧的 [ChatMessage] 分开是必要的，不是重复定义：流式响应里 `role` 只在
 * **第一个**分片出现，之后每个分片只带 `content`；工具调用分片则只带
 * `tool_calls`。若让响应复用角色必填的 [ChatMessage]，除首片以外的每一片都会
 * 在反序列化时抛 `MissingFieldException`——而调用方按「单片解析失败只跳过该行」
 * 容忍，于是症状是**流看起来正常，却一个字都收不到**。
 */
@Serializable
internal data class ChatDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ChatToolCall>? = null,
)

@Serializable
internal data class ChatChoice(
    val index: Int? = null,
    val message: ChatDelta? = null,
    val delta: ChatDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class ChatUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
)

@Serializable
internal data class ChatError(
    val message: String? = null,
    val code: String? = null,
    val type: String? = null,
)

// ── 翻译：ADK 类型 → OpenAI 线上格式 ──────────────────────────────────

/** ADK 的函数声明 → Chat Completions 的 `tools` 条目（嵌在 `function` 里）。 */
internal fun FunctionDeclaration.toOpenAiTool(): ChatTool =
    ChatTool(
        type = "function",
        function =
            ChatToolFunction(
                name = name,
                description = description,
                parameters = toJsonSchemaObject(),
            ),
    )
