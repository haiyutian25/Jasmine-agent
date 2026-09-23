package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.annotations.FrameworkInternalApi
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.models.StreamingResponseAggregator
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FinishReason
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.UsageMetadata
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * [ProviderApiType.CHAT_COMPLETIONS] 的 ADK [Model] 实现，对接任意 OpenAI 协议
 * 兼容供应商（DeepSeek、OpenAI、自建网关…）。
 *
 * ADK 只认 [Model] 这一个契约（`name` + [generateContent]），它并不知道 OpenAI
 * 协议的存在，所以「怎么跟供应商说话」全部落在这一层：请求翻译、SSE 解析、
 * 响应翻译。工具调用只做搬运——把模型的工具请求放进 [Part.functionCall]，
 * 由 ADK 负责派发和执行，本类绝不自己执行工具（这也是官方三个后端共同遵守的
 * 约定，参见 LiteRT-LM 的 `automaticToolCalling = false`）。
 *
 * 协议的另一半（Responses API）见 [OpenAiResponsesModel]；共用的 JSON 配置、
 * SSE 传输与角色映射见 `OpenAiWire.kt`。
 *
 * 供应商配置直接复用项目已有的 [ProviderConfig]（baseUrl / apiKey / apiType），
 * 无需额外的连接配置。调度器由调用方注入（与数据层同一约定：不直接引用
 * `Dispatchers`），因为本类需要执行阻塞式 HTTP。
 */
class OpenAiChatCompletionsModel(
    private val config: ProviderConfig,
    private val modelId: String,
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
) : Model {

    init {
        require(config.apiType == ProviderApiType.CHAT_COMPLETIONS) {
            "OpenAiChatCompletionsModel requires a CHAT_COMPLETIONS provider, got ${config.apiType}"
        }
    }

    override val name: String = modelId

    /**
     * 流式走 ADK 的 [StreamingResponseAggregator]：每个分片转成 `partial=true`
     * 的 [LlmResponse] 交给它处理，流结束后再发射一次聚合结果。
     *
     * 整个 [Flow] 跑在注入的 IO 调度器上，因此内部可以安全地做阻塞式 HTTP 与
     * SSE 读取（与官方 `SpringAiModel` 用 `.flowOn(Dispatchers.IO)` 同理）。
     */
    @OptIn(FrameworkInternalApi::class)
    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> =
        flow {
                if (stream) {
                    val aggregator = StreamingResponseAggregator()
                    streamChat(request).collect { chunk -> emit(aggregator.processResponse(chunk)) }
                    aggregator.aggregate()?.let { emit(it) }
                } else {
                    emit(callChat(request))
                }
            }
            .flowOn(ioDispatcher)

    // ── 传输：非流式 ───────────────────────────────────────────────────

    private fun callChat(request: LlmRequest): LlmResponse {
        val httpRequest = buildRequest(request, stream = false)
        httpClient.newCall(httpRequest).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $text")
            val parsed = openAiJson.decodeFromString<ChatResponse>(text)
            parsed.error?.message?.let { throw IOException(it) }
            return parsed.toLlmResponse(partial = false)
        }
    }

    // ── 传输：SSE 流式（手写，不依赖任何 OpenAI SDK） ────────────────────

    /**
     * 每个 `data:` 分片解析成一个增量 [LlmResponse]；单个分片解析失败只跳过该
     * 行，不让整条流中断（兼容供应商偶发的畸形帧）。
     */
    private fun streamChat(request: LlmRequest): Flow<LlmResponse> =
        flow {
            ssePayloads(httpClient, buildRequest(request, stream = true)).collect { payload ->
                val chunk =
                    runCatching { openAiJson.decodeFromString<ChatResponse>(payload) }
                        .getOrNull() ?: return@collect
                chunk.error?.message?.let { throw IOException(it) }
                emit(chunk.toLlmResponse(partial = true))
            }
        }

    /** Internal rather than private so the wire shape can be asserted by unit tests. */
    internal fun buildRequest(request: LlmRequest, stream: Boolean): Request {
        val messages =
            buildList {
                request.config.systemInstruction
                    ?.parts
                    ?.mapNotNull { it.text }
                    ?.joinToString("")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(ChatMessage(role = "system", content = it)) }
                addAll(request.contents.toOpenAiMessages())
            }
        val tools =
            request.config.tools
                ?.flatMap { it.functionDeclarations.orEmpty() }
                ?.map { it.toOpenAiTool() }
                ?.takeIf { it.isNotEmpty() }

        val payload =
            ChatRequest(
                model = modelId,
                messages = messages,
                tools = tools,
                temperature = request.config.temperature,
                topP = request.config.topP,
                maxTokens = request.config.maxOutputTokens,
                stop = request.config.stopSequences,
                stream = stream,
            )

        return Request.Builder()
            .url(endpoint())
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Accept", if (stream) "text/event-stream" else "application/json")
            .post(openAiJson.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    /** 与 `ProviderModelDataSource` 同一约定：baseUrl 以 `/v1` 结尾则不重复拼接。 */
    private fun endpoint(): String {
        val trimmed = config.baseUrl.trim().trimEnd('/')
        val base = if (trimmed.endsWith("/v1", ignoreCase = true)) trimmed else "$trimmed/v1"
        return "$base/chat/completions"
    }

    // ── 翻译：ADK → OpenAI ─────────────────────────────────────────────

    private fun List<Content>.toOpenAiMessages(): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        for (content in this) {
            val text = content.parts.mapNotNull { it.text }.joinToString("")
            val toolCalls = content.parts.mapNotNull { it.functionCall }.map { it.toOpenAiToolCall() }

            // 工具结果在 Chat Completions 里是独立的 role="tool" 消息，必须单独成条。
            content.parts.mapNotNull { it.functionResponse }.forEach { response ->
                result.add(
                    ChatMessage(
                        role = "tool",
                        toolCallId = response.id,
                        name = response.name,
                        content = response.response.toJsonText(),
                    )
                )
            }

            if (text.isNotEmpty() || toolCalls.isNotEmpty()) {
                result.add(
                    ChatMessage(
                        role = content.role.toOpenAiRole(),
                        content = text.takeIf { it.isNotEmpty() },
                        toolCalls = toolCalls.takeIf { it.isNotEmpty() },
                    )
                )
            }
        }
        return result
    }

    private fun FunctionCall.toOpenAiToolCall(): ChatToolCall =
        ChatToolCall(
            id = id,
            type = "function",
            function =
                ChatToolCallFunction(
                    name = name,
                    arguments = args.toJsonText(),
                ),
        )

    // ── 翻译：OpenAI → ADK ─────────────────────────────────────────────

    private fun ChatResponse.toLlmResponse(partial: Boolean): LlmResponse {
        val choice = choices.firstOrNull()
        val message = choice?.message ?: choice?.delta
        val parts =
            buildList {
                message?.content?.takeIf { it.isNotEmpty() }?.let { add(Part(text = it)) }
                message?.toolCalls
                    ?.mapNotNull { it.toAdkFunctionCall() }
                    ?.forEach { add(Part(functionCall = it)) }
            }
        return LlmResponse(
            content = parts.takeIf { it.isNotEmpty() }?.let { Content(role = Role.MODEL, parts = it) },
            finishReason = choice?.finishReason?.toAdkFinishReason(),
            usageMetadata = usage?.toAdkUsage(),
            partial = partial,
        )
    }

    private fun ChatToolCall.toAdkFunctionCall(): FunctionCall? {
        val functionName = function?.name?.takeIf { it.isNotEmpty() } ?: return null
        return FunctionCall(
            name = functionName,
            args = parseToolArguments(function.arguments),
            id = id,
        )
    }

    /** OpenAI 的 finish_reason 是小写，ADK 是大写枚举名，需显式映射。 */
    private fun String?.toAdkFinishReason(): FinishReason? =
        when (this) {
            null -> null
            "stop" -> FinishReason.STOP
            "length" -> FinishReason.MAX_TOKENS
            "tool_calls", "function_call" -> FinishReason.STOP
            "content_filter" -> FinishReason.SAFETY
            else -> FinishReason.OTHER
        }

    private fun ChatUsage.toAdkUsage(): UsageMetadata =
        UsageMetadata(
            promptTokenCount = promptTokens,
            candidatesTokenCount = completionTokens,
            totalTokenCount = totalTokens,
        )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
