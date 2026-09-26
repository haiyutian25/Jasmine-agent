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
     * 只走流式：每个分片转成 `partial=true` 的 [LlmResponse] 交给 ADK 的
     * [StreamingResponseAggregator]，流结束后再发射一次聚合结果。
     *
     * 非流式分支已删除——App 的运行配置始终是 ADK 的 `StreamingMode.SSE`，
     * 那条路不可达。`stream` 参数是 ADK [Model] 契约的一部分，保留但不再判断。
     *
     * 整个 [Flow] 跑在注入的 IO 调度器上，因此内部可以安全地做阻塞式 HTTP 与
     * SSE 读取（与官方 `SpringAiModel` 用 `.flowOn(Dispatchers.IO)` 同理）。
     */
    @OptIn(FrameworkInternalApi::class)
    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> =
        flow {
                val aggregator = StreamingResponseAggregator()
                streamChat(request).collect { chunk ->
                    emit(aggregator.processResponse(chunk).withModelVersion())
                }
                // No stream events at all means the provider did not stream — typically it
                // answered with a plain JSON body (some gateways ignore `stream`). Say so
                // rather than ending the turn with nothing, which leaves the caller waiting
                // on a reply that will never come.
                val aggregated = aggregator.aggregate()
                    ?: throw IOException("The provider returned no stream events")
                emit(aggregated.withModelVersion())
            }
            .flowOn(ioDispatcher)

    /**
     * 把模型名标在响应上。
     *
     * ADK 的 `finalizeModelResponseEvent` 会把 `LlmResponse.modelVersion` 原样搬到事件上
     * （我们的响应由本类构造，所以只能在这里标）——不标的话事件里永远是 null，落库后就无从
     * 知道某条回复是哪个模型产生的，会话中途换过模型时界面只能猜会话记录里的那个。
     */
    private fun LlmResponse.withModelVersion(): LlmResponse = copy(modelVersion = modelId)

    // ── 传输：SSE 流式（手写，不依赖任何 OpenAI SDK） ────────────────────

    /**
     * 每个 `data:` 分片解析成一个增量 [LlmResponse]；单个分片解析失败只跳过该
     * 行，不让整条流中断（兼容供应商偶发的畸形帧）。
     *
     * 文本随分片即时发射，**工具调用不行**——它们是跨帧拼出来的，要等流结束后
     * 才能发射（原因见 [ToolCallFragments]）。
     */
    private fun streamChat(request: LlmRequest): Flow<LlmResponse> =
        flow {
            val toolCalls = ToolCallFragments()
            ssePayloads(httpClient, buildRequest(request)).collect { payload ->
                val chunk =
                    runCatching { openAiJson.decodeFromString<ChatResponse>(payload) }
                        .getOrNull() ?: return@collect
                chunk.error?.message?.let { throw IOException(it) }
                chunk.choices.firstOrNull()?.let { choice ->
                    (choice.delta ?: choice.message)?.toolCalls?.forEach(toolCalls::add)
                }
                emit(chunk.toLlmResponse())
            }

            val calls = toolCalls.complete()
            if (calls.isNotEmpty()) {
                emit(
                    LlmResponse(
                        content = Content(
                            role = Role.MODEL,
                            parts = calls.map { Part(functionCall = it) },
                        ),
                        partial = true,
                    )
                )
            }
        }

    /**
     * Always a streaming request: this adapter has no non-streaming shape.
     *
     * Internal rather than private so the wire shape can be asserted by unit tests.
     */
    internal fun buildRequest(request: LlmRequest): Request {
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
                // 只在带工具时声明：不带工具的请求里这个参数没有意义，也可能被个别网关嫌弃。
                parallelToolCalls = if (tools.isNullOrEmpty()) null else false,
                temperature = request.config.temperature,
                topP = request.config.topP,
                maxTokens = request.config.maxOutputTokens,
                stop = request.config.stopSequences,
                stream = true,
            )

        return Request.Builder()
            .url(endpoint())
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Accept", "text/event-stream")
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

    /**
     * 文本与元数据。**刻意不含工具调用**：流式下一次调用是跨帧拼出来的
     * （见 [ToolCallFragments]），分帧发射只会得到参数残缺的调用。
     */
    private fun ChatResponse.toLlmResponse(): LlmResponse {
        val choice = choices.firstOrNull()
        val text = choice?.message?.content ?: choice?.delta?.content
        return LlmResponse(
            content = text
                ?.takeIf { it.isNotEmpty() }
                ?.let { Content(role = Role.MODEL, parts = listOf(Part(text = it))) },
            finishReason = choice?.finishReason?.toAdkFinishReason(),
            usageMetadata = usage?.toAdkUsage(),
            partial = true,
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

/**
 * 把 Chat Completions 流式响应里的工具调用重新拼起来。
 *
 * 一次调用被协议拆在多个分片里：首片给出 `id` 与函数名，之后每片追加
 * `arguments` 这段 JSON 文本的一段。因此**单看一片，参数就是一段残缺 JSON**
 * （例如 `{"city"`），而参数解析器对残缺文本是容错的（返回空表而非报错），
 * 于是逐片翻译会静默地得到「参数全空」的工具调用。
 *
 * 分片按 OpenAI 在每个条目上打的 `index` 归并；**没有 `index` 的条目视为一次
 * 完整调用**——那正是不支持流式、在一帧里返回整个对象的网关的形状。
 *
 * `internal` 而非 `private`：拼装规则是纯数据变换，单元测试直接断言它，不必为
 * 此起一个 HTTP 服务端。
 */
internal class ToolCallFragments {

    private class Fragment {
        var name: String? = null
        var id: String? = null
        val arguments = StringBuilder()
    }

    private val fragments = linkedMapOf<Int, Fragment>()
    private var nextIndex = 0

    /** 记下一片。同一 `index` 的片属于同一次调用，参数按到达顺序相接。 */
    fun add(call: ChatToolCall) {
        val fragment = fragments.getOrPut(call.index ?: nextIndex++) { Fragment() }
        call.id?.takeIf { it.isNotEmpty() }?.let { fragment.id = it }
        call.function?.name?.takeIf { it.isNotEmpty() }?.let { fragment.name = it }
        call.function?.arguments?.let { fragment.arguments.append(it) }
    }

    /** 按调用开始的顺序给出完整调用；无名条目是无效帧，丢弃。 */
    fun complete(): List<FunctionCall> =
        fragments.values.mapNotNull { fragment ->
            val name = fragment.name ?: return@mapNotNull null
            FunctionCall(
                name = name,
                args = parseToolArguments(fragment.arguments.toString()),
                id = fragment.id,
            )
        }
}
