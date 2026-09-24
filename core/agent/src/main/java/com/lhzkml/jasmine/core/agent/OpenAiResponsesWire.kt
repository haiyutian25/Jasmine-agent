package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FinishReason
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.UsageMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * OpenAI Responses API（`POST /v1/responses`）的线上格式。
 *
 * 与 Chat Completions 的三处结构性差异，是本文件存在的原因：
 * 1. **系统提示**不在 `messages` 里，而是顶层的 `instructions` 字符串；
 * 2. **工具结果**不是 `role="tool"` 的消息，而是独立的 `function_call_output`
 *    item，工具的请求方（`function_call`）也是独立 item；
 * 3. **响应没有 `choices`**，而是 `output` 数组（item 以 `type` 判别）；流式也
 *    不是增量 delta，而是带 `type` 的事件流。
 *
 * 与 Chat 一致地保持宽容：未知键忽略、字段全给默认值。
 */
internal object ResponsesWire {
    const val ITEM_MESSAGE = "message"
    const val ITEM_FUNCTION_CALL = "function_call"
    const val ITEM_FUNCTION_CALL_OUTPUT = "function_call_output"
    const val CONTENT_OUTPUT_TEXT = "output_text"

    const val STATUS_COMPLETED = "completed"
    const val STATUS_INCOMPLETE = "incomplete"
    const val STATUS_FAILED = "failed"

    const val INCOMPLETE_MAX_OUTPUT_TOKENS = "max_output_tokens"
    const val INCOMPLETE_CONTENT_FILTER = "content_filter"

    const val EVENT_TEXT_DELTA = "response.output_text.delta"
    const val EVENT_OUTPUT_ITEM_DONE = "response.output_item.done"
    const val EVENT_COMPLETED = "response.completed"
    const val EVENT_INCOMPLETE = "response.incomplete"
    const val EVENT_FAILED = "response.failed"
    const val EVENT_ERROR = "error"
}

// ── 请求侧 ────────────────────────────────────────────────────────────

/**
 * 请求体。注意**没有 `stop`**：Responses API 不提供该参数（ADK 的
 * `stopSequences` 在此协议下被丢弃），发送它会被拒绝。
 */
@Serializable
internal data class ResponsesRequest(
    val model: String,
    val input: List<JsonElement>,
    val instructions: String? = null,
    val tools: List<ResponsesTool>? = null,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_output_tokens") val maxOutputTokens: Int? = null,
    val stream: Boolean = false,
)

/**
 * Responses 的 function 工具是**扁平**结构（`name`/`description`/`parameters`
 * 直接在顶层），不像 Chat Completions 那样嵌在 `function` 对象里。
 *
 * [type]、[parameters]、[strict] 都**不能带默认值**：`openAiJson` 关闭了
 * `encodeDefaults`，与默认值相同的属性不会被序列化，而这三个字段在
 * Responses 的 function 工具里是必填的。仅 [description] 可省略。
 */
@Serializable
internal data class ResponsesTool(
    val type: String,
    val name: String,
    val description: String = "",
    val parameters: JsonObject,
    val strict: Boolean,
)

internal fun FunctionDeclaration.toResponsesTool(): ResponsesTool =
    ResponsesTool(
        type = "function",
        name = name,
        description = description,
        parameters = toJsonSchemaObject(),
        strict = false,
    )

/**
 * `input` 的元素是判别联合（33 种 item 类型），这里只构造用得到的三类。用
 * [JsonObject] 手工拼装而不做多态序列化：条目形状差异大，而宽容解析比严格的
 * sealed 层级更适合对接参差不齐的兼容供应商。
 */
internal fun openAiMessageItem(role: String, content: String): JsonObject = buildJsonObject {
    put("role", role)
    put("content", content)
}

internal fun openAiFunctionCallItem(
    name: String,
    callId: String?,
    arguments: String,
): JsonObject = buildJsonObject {
    put("type", ResponsesWire.ITEM_FUNCTION_CALL)
    callId?.takeIf { it.isNotEmpty() }?.let { put("call_id", it) }
    put("name", name)
    put("arguments", arguments)
}

internal fun openAiFunctionCallOutputItem(callId: String?, output: String): JsonObject =
    buildJsonObject {
        put("type", ResponsesWire.ITEM_FUNCTION_CALL_OUTPUT)
        callId?.takeIf { it.isNotEmpty() }?.let { put("call_id", it) }
        put("output", output)
    }

// ── 响应侧 ────────────────────────────────────────────────────────────

@Serializable
internal data class ResponsesResponse(
    val id: String? = null,
    val status: String? = null,
    val output: List<ResponsesOutputItem> = emptyList(),
    val usage: ResponsesUsage? = null,
    val error: ResponsesError? = null,
    @SerialName("incomplete_details") val incompleteDetails: ResponsesIncompleteDetails? = null,
)

/**
 * 一个 `output` item。所有可能用到的字段平铺在一处并按 `type` 取用：message
 * 用 [content]，function_call 用 [callId]/[name]/[arguments]。
 */
@Serializable
internal data class ResponsesOutputItem(
    val type: String? = null,
    val id: String? = null,
    val role: String? = null,
    val content: List<ResponsesOutputContent>? = null,
    @SerialName("call_id") val callId: String? = null,
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
internal data class ResponsesOutputContent(
    val type: String? = null,
    val text: String? = null,
)

@Serializable
internal data class ResponsesUsage(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
)

@Serializable
internal data class ResponsesError(
    val code: String? = null,
    val message: String? = null,
)

@Serializable
internal data class ResponsesIncompleteDetails(
    val reason: String? = null,
)

/**
 * 流式事件信封。Responses 的流是**带 `type` 的事件流**（60 种），这里只声明
 * 用得到的字段，其余事件靠 `when` 的 `else` 分支忽略。
 */
@Serializable
internal data class ResponsesStreamEvent(
    val type: String? = null,
    val delta: String? = null,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("output_index") val outputIndex: Int? = null,
    val item: ResponsesOutputItem? = null,
    val response: ResponsesResponse? = null,
    val message: String? = null,
)

// ── 翻译：OpenAI → ADK ────────────────────────────────────────────────

internal fun ResponsesOutputItem.toFunctionCall(): FunctionCall? {
    val functionName = name?.takeIf { it.isNotEmpty() } ?: return null
    return FunctionCall(
        name = functionName,
        args = parseToolArguments(arguments),
        id = callId,
    )
}

/**
 * Responses 用 `status` + `incomplete_details.reason` 表达结束原因，需映射到
 * ADK 的 [FinishReason]。
 */
internal fun ResponsesResponse.finishReason(): FinishReason? =
    when (status) {
        ResponsesWire.STATUS_COMPLETED -> FinishReason.STOP
        ResponsesWire.STATUS_INCOMPLETE -> when (incompleteDetails?.reason) {
            ResponsesWire.INCOMPLETE_MAX_OUTPUT_TOKENS -> FinishReason.MAX_TOKENS
            ResponsesWire.INCOMPLETE_CONTENT_FILTER -> FinishReason.SAFETY
            else -> FinishReason.OTHER
        }
        ResponsesWire.STATUS_FAILED -> FinishReason.OTHER
        else -> null
    }

internal fun ResponsesUsage.toAdkUsage(): UsageMetadata =
    UsageMetadata(
        promptTokenCount = inputTokens,
        candidatesTokenCount = outputTokens,
        totalTokenCount = totalTokens,
    )
