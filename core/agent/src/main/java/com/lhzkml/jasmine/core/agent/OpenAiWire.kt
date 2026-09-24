package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 两种 OpenAI 线上格式（Chat Completions / Responses）共用的部分：JSON 配置、
 * SSE 传输、角色映射、JSON Schema 展平与工具参数解析。
 *
 * 刻意保持宽容：不同 OpenAI 协议兼容供应商的字段差异较大，因此 [openAiJson]
 * 忽略未知键、不写入 null，并且响应字段一律给默认值，避免个别供应商的字段
 * 差异导致整条解析失败。
 */
internal val openAiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

internal val EMPTY_JSON_OBJECT: JsonObject = JsonObject(emptyMap())

// ── 传输：SSE ─────────────────────────────────────────────────────────

/** SSE 流终止标记；Responses API 不发它，但兼容网关可能会发。 */
private const val SSE_DONE = "[DONE]"

/**
 * 按 SSE 规范逐行读取 [request] 的响应体，只发射 `data:` 行的载荷（去掉前缀、
 * 跳过空行）。**`[DONE]` 是流的终点**：OpenAI 协议的流由它结束，而发完它之后
 * 连接不一定关闭，若只跳过它继续读，就会一直阻塞在一次不会再有数据的读上。
 *
 * 单个分片的 JSON 解析留给调用方——两种协议的分片类型不同，解析策略也不同。
 *
 * 内部为阻塞式读取，调用方必须保证它跑在 IO 调度器上。
 */
internal fun ssePayloads(httpClient: OkHttpClient, request: Request): Flow<String> = flow {
    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code} ${response.message}")
        }
        val body = response.body ?: throw IOException("Empty response body")
        body.source().use { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                if (payload == SSE_DONE) break
                emit(payload)
            }
        }
    }
}

// ── 角色映射 ──────────────────────────────────────────────────────────

/**
 * ADK 的角色常量（[Role] 上是字符串常量而非枚举）→ OpenAI 消息角色。
 * ADK 只有 user / model / system 三种，其余一律按 user 处理。
 */
internal fun String?.toOpenAiRole(): String =
    when (this) {
        Role.MODEL -> "assistant"
        Role.SYSTEM -> "system"
        else -> "user"
    }

// ── 工具声明：ADK Schema → JSON Schema ────────────────────────────────

/**
 * [FunctionDeclaration.parameters] 是 ADK 的强类型 [Schema]，展平为 JSON Schema
 * 对象；两种协议的 `tools` 条目都直接复用它（Chat Completions 嵌在 `function`
 * 里，Responses 是扁平字段）。
 */
internal fun FunctionDeclaration.toJsonSchemaObject(): JsonObject =
    parameters?.toJsonSchema() ?: EMPTY_JSON_OBJECT

/**
 * ADK 的 [Type] 是大写枚举名，需转成 JSON Schema 的小写类型名，并跳过
 * `TYPE_UNSPECIFIED`（对供应商来说是非法的 type 值）。
 */
private fun Schema.toJsonSchema(): JsonObject = buildJsonObject {
    type?.wireNameOrNull()?.let { put("type", it) }
    description?.let { put("description", it) }
    format?.let { put("format", it) }
    nullable?.let { put("nullable", it) }
    default?.let { put("default", it.toJsonElement()) }
    pattern?.let { put("pattern", it) }
    minimum?.let { put("minimum", it) }
    maximum?.let { put("maximum", it) }
    minLength?.let { put("minLength", it) }
    maxLength?.let { put("maxLength", it) }
    minItems?.let { put("minItems", it) }
    maxItems?.let { put("maxItems", it) }
    enum?.takeIf { it.isNotEmpty() }?.let { values ->
        put("enum", JsonArray(values.map(::JsonPrimitive)))
    }
    required?.takeIf { it.isNotEmpty() }?.let { names ->
        put("required", JsonArray(names.map(::JsonPrimitive)))
    }
    properties?.takeIf { it.isNotEmpty() }?.let { props ->
        put("properties", JsonObject(props.mapValues { (_, schema) -> schema.toJsonSchema() }))
    }
    items?.let { put("items", it.toJsonSchema()) }
}

private fun Type.wireNameOrNull(): String? =
    when (this) {
        Type.STRING -> "string"
        Type.NUMBER -> "number"
        Type.INTEGER -> "integer"
        Type.BOOLEAN -> "boolean"
        Type.ARRAY -> "array"
        Type.OBJECT -> "object"
        Type.NULL -> "null"
        Type.TYPE_UNSPECIFIED -> null
    }

// ── Any? ↔ JsonElement ────────────────────────────────────────────────
// ADK 的工具参数/返回值是 `Map<String, Any?>`（运行时为 JSON 原生值），
// OpenAI 侧却是 JSON 字符串，两边都要转换。

internal fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Map<*, *> ->
            JsonObject(this.entries.associate { it.key.toString() to it.value.toJsonElement() })
        is Iterable<*> -> JsonArray(this.map { it.toJsonElement() })
        is Array<*> -> JsonArray(this.map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }

internal fun JsonElement.toPlainValue(): Any? =
    when (this) {
        JsonNull -> null
        is JsonPrimitive ->
            if (isString) {
                content
            } else {
                content.toLongOrNull()
                    ?: content.toDoubleOrNull()
                    ?: content.toBooleanStrictOrNull()
                    ?: content
            }
        is JsonArray -> map { it.toPlainValue() }
        is JsonObject -> entries.associate { it.key to it.value.toPlainValue() }
    }

/**
 * 把 OpenAI 的 `arguments`（一段 JSON 文本）解析成 ADK 需要的参数表。
 * 供应商偶发返回残缺/非对象 JSON，这里容错为空表而不是让整条流失败。
 */
internal fun parseToolArguments(raw: String?): Map<String, Any?> {
    if (raw.isNullOrBlank()) return emptyMap()
    val element =
        runCatching { openAiJson.parseToJsonElement(raw) }.getOrNull() ?: return emptyMap()
    val obj = runCatching { element.jsonObject }.getOrNull() ?: return emptyMap()
    return obj.entries.associate { it.key to it.value.toPlainValue() }
}

/** ADK 工具调用/返回值 → OpenAI 侧需要的 JSON 文本。 */
internal fun Map<String, Any?>.toJsonText(): String =
    JsonObject(mapValues { it.value.toJsonElement() }).toString()
