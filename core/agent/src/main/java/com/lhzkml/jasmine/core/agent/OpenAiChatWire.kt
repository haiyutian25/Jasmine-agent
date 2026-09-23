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

@Serializable
internal data class ChatChoice(
    val index: Int? = null,
    val message: ChatMessage? = null,
    val delta: ChatMessage? = null,
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
