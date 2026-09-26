package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.annotations.FrameworkInternalApi
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.models.StreamingResponseAggregator
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * [ProviderApiType.RESPONSES] 的 ADK [Model] 实现（`POST /v1/responses`）。
 *
 * 与 [OpenAiChatCompletionsModel] 的分工：请求体、响应结构与流式事件都由
 * `OpenAiResponsesWire.kt` 描述，本类只负责编排——翻译请求、按事件类型推进流、
 * 把结果交给 ADK 的聚合器。
 *
 * 流式实现要点：Responses 的事件流没有「完整响应」的概念，文本是
 * `response.output_text.delta` 分片、工具调用要到 `response.output_item.done`
 * 才拿到完整 `arguments`。因此这里**只把文本作为增量喂给聚合器**，工具调用在
 * 拿到完整 item 后一次性喂入（聚合器会先 flush 已缓冲的文本，故顺序正确）。
 * 这样无需自行从 JSON 片段推断参数路径（ADK 的 `PartialArg` 机制所需），代价是
 * 工具参数不向 UI 逐字增量。
 */
class OpenAiResponsesModel(
    private val config: ProviderConfig,
    private val modelId: String,
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
) : Model {

    init {
        require(config.apiType == ProviderApiType.RESPONSES) {
            "OpenAiResponsesModel requires a RESPONSES provider, got ${config.apiType}"
        }
    }

    override val name: String = modelId

    /**
     * 只走事件流：分片交给 ADK 的 [StreamingResponseAggregator]，流结束后再发射一次
     * 聚合结果。非流式分支已删除——App 的运行配置始终是 ADK 的 `StreamingMode.SSE`。
     * `stream` 参数是 ADK [Model] 契约的一部分，保留但不再判断。
     */
    @OptIn(FrameworkInternalApi::class)
    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> =
        flow {
                val aggregator = StreamingResponseAggregator()
                streamResponse(request).collect { chunk ->
                    emit(aggregator.processResponse(chunk).withModelVersion())
                }
                // See the Chat Completions adapter: no stream events means the provider did
                // not stream, and that must surface rather than end the turn silently.
                val aggregated = aggregator.aggregate()
                    ?: throw IOException("The provider returned no stream events")
                emit(aggregated.withModelVersion())
            }
            .flowOn(ioDispatcher)

    /**
     * 把模型名标在响应上 —— 与 Chat Completions 适配器同一做法，原因见那里的注释：
     * ADK 只认 `LlmResponse.modelVersion`，不标就不会进事件、不会落库。
     */
    private fun LlmResponse.withModelVersion(): LlmResponse = copy(modelVersion = modelId)

    // ── 传输：事件流 ───────────────────────────────────────────────────

    /**
     * 按事件类型推进：文本分片与完整工具调用转成内容分片；结束事件只带
     * finishReason / usage（**不带 content**，否则聚合器会把同一份内容重复计入）。
     */
    private fun streamResponse(request: LlmRequest): Flow<LlmResponse> =
        flow {
            ssePayloads(httpClient, buildRequest(request)).collect { payload ->
                val event =
                    runCatching { openAiJson.decodeFromString<ResponsesStreamEvent>(payload) }
                        .getOrNull() ?: return@collect

                when (event.type) {
                    ResponsesWire.EVENT_ERROR ->
                        throw IOException(event.message ?: "Provider error")

                    ResponsesWire.EVENT_TEXT_DELTA ->
                        event.delta
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { emit(contentResponse(Part(text = it))) }

                    ResponsesWire.EVENT_OUTPUT_ITEM_DONE ->
                        event.item
                            ?.toFunctionCall()
                            ?.let { emit(contentResponse(Part(functionCall = it))) }

                    ResponsesWire.EVENT_COMPLETED,
                    ResponsesWire.EVENT_INCOMPLETE,
                    -> emit(event.response.terminalMetadata())

                    ResponsesWire.EVENT_FAILED ->
                        throw IOException(
                            event.response?.error?.message ?: "The model response failed"
                        )

                    else -> Unit
                }
            }
        }

    private fun contentResponse(part: Part): LlmResponse =
        LlmResponse(content = Content(role = Role.MODEL, parts = listOf(part)), partial = true)

    /**
     * 结束事件的元数据载体：只带 finishReason / usage，content 留空，供聚合器
     * 在 `aggregate()` 时合并进最终响应。
     */
    private fun ResponsesResponse?.terminalMetadata(): LlmResponse =
        LlmResponse(
            finishReason = this?.finishReason(),
            usageMetadata = this?.usage?.toAdkUsage(),
            errorMessage = this?.error?.message,
            partial = true,
        )

    // ── 请求构造 ───────────────────────────────────────────────────────

    /**
     * Always a streaming request: this adapter has no non-streaming shape.
     *
     * Internal rather than private so the wire shape can be asserted by unit tests.
     */
    internal fun buildRequest(request: LlmRequest): Request {
        val instructions =
            request.config.systemInstruction
                ?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("")
                ?.takeIf { it.isNotBlank() }
        val tools =
            request.config.tools
                ?.flatMap { it.functionDeclarations.orEmpty() }
                ?.map { it.toResponsesTool() }
                ?.takeIf { it.isNotEmpty() }

        val payload =
            ResponsesRequest(
                model = modelId,
                input = request.contents.toResponsesInput(),
                instructions = instructions,
                tools = tools,
                temperature = request.config.temperature,
                topP = request.config.topP,
                maxOutputTokens = request.config.maxOutputTokens,
                stream = true,
            )

        return Request.Builder()
            .url(endpoint())
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Accept", "text/event-stream")
            .post(openAiJson.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    /**
     * ADK 的对话历史 → Responses 的 `input` item 列表。
     *
     * 文本消息是普通 message item；工具结果与工具请求是各自独立的 item——这是
     * 与 Chat Completions 最大的结构差异（那边分别是 `role="tool"` 消息和挂在
     * assistant 消息上的 `tool_calls`）。
     */
    private fun List<Content>.toResponsesInput(): List<JsonElement> {
        val items = mutableListOf<JsonElement>()
        for (content in this) {
            content.parts.mapNotNull { it.functionResponse }.forEach { response ->
                items.add(
                    openAiFunctionCallOutputItem(
                        callId = response.id,
                        output = response.response.toJsonText(),
                    )
                )
            }

            val text = content.parts.mapNotNull { it.text }.joinToString("")
            if (text.isNotEmpty()) {
                items.add(openAiMessageItem(role = content.role.toOpenAiRole(), content = text))
            }

            content.parts.mapNotNull { it.functionCall }.forEach { call ->
                items.add(
                    openAiFunctionCallItem(
                        name = call.name,
                        callId = call.id,
                        arguments = call.args.toJsonText(),
                    )
                )
            }
        }
        return items
    }

    /** 与 `ProviderModelDataSource` 同一约定：baseUrl 以 `/v1` 结尾则不重复拼接。 */
    private fun endpoint(): String {
        val trimmed = config.baseUrl.trim().trimEnd('/')
        val base = if (trimmed.endsWith("/v1", ignoreCase = true)) trimmed else "$trimmed/v1"
        return "$base/responses"
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
