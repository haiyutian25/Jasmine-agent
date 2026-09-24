package com.lhzkml.jasmine.core.agent

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FinishReason
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Tool
import com.google.adk.kt.types.Type
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wire-format tests for both OpenAI protocols.
 *
 * These assert the exact JSON that goes on the wire and the exact ADK types that
 * come back — the two places where a protocol mistake would otherwise only show
 * up against a live provider.
 */
class OpenAiWireTest {

    // ── Requests: Responses API ────────────────────────────────────────

    @Test
    fun `responses request uses the responses shape`() {
        val model = responsesModel()
        val body = model.buildRequest(fullRequest()).bodyJson()

        assertEquals("gpt-test", body["model"]!!.jsonPrimitive.content)
        assertEquals("be nice", body["instructions"]!!.jsonPrimitive.content)
        assertEquals(0.5, body["temperature"]!!.jsonPrimitive.content.toDouble(), 0.0001)
        assertEquals("256", body["max_output_tokens"]!!.jsonPrimitive.content)
        // The adapter has no non-streaming shape: every request asks the provider to stream.
        assertTrue("stream must be requested", body["stream"]?.jsonPrimitive?.boolean == true)

        // System prompt moved out of `messages` into top-level `instructions`.
        assertNull(body["messages"])
        // The Responses API has no `stop` parameter: ADK's stopSequences must not leak.
        assertNull(body["stop"])
    }

    @Test
    fun `responses request flattens function tools`() {
        val body = responsesModel().buildRequest(fullRequest()).bodyJson()
        val tool = body["tools"]!!.jsonArray.single().jsonObject

        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        assertEquals("get_time", tool["name"]!!.jsonPrimitive.content)
        assertEquals("Current time", tool["description"]!!.jsonPrimitive.content)
        assertEquals("object", tool["parameters"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        // `strict` is required by the Responses tool schema, so it must be sent.
        assertFalse(tool["strict"]!!.jsonPrimitive.boolean)
        // Flat shape: there is no nested `function` object (unlike Chat Completions).
        assertNull(tool["function"])
    }

    @Test
    fun `responses request maps tool results and calls to their own items`() {
        val contents =
            listOf(
                Content(
                    role = Role.MODEL,
                    parts = listOf(
                        Part(text = "let me check"),
                        Part(
                            functionCall = FunctionCall(
                                name = "get_time",
                                args = mapOf("city" to "Paris"),
                                id = "call_1",
                            )
                        ),
                    ),
                ),
                Content(
                    role = Role.USER,
                    parts = listOf(
                        Part(
                            functionResponse = FunctionResponse(
                                name = "get_time",
                                response = mapOf("time" to "10:30"),
                                id = "call_1",
                            )
                        )
                    ),
                ),
            )
        val body =
            responsesModel()
                .buildRequest(LlmRequest(contents = contents))
                .bodyJson()
        val input = body["input"]!!.jsonArray.map { it.jsonObject }

        assertEquals(3, input.size)
        assertEquals("message", input[0]["type"]?.jsonPrimitive?.content ?: "message")
        assertEquals("assistant", input[0]["role"]!!.jsonPrimitive.content)
        assertEquals("let me check", input[0]["content"]!!.jsonPrimitive.content)

        assertEquals("function_call", input[1]["type"]!!.jsonPrimitive.content)
        assertEquals("get_time", input[1]["name"]!!.jsonPrimitive.content)
        assertEquals("call_1", input[1]["call_id"]!!.jsonPrimitive.content)
        assertEquals("""{"city":"Paris"}""", input[1]["arguments"]!!.jsonPrimitive.content)

        assertEquals("function_call_output", input[2]["type"]!!.jsonPrimitive.content)
        assertEquals("call_1", input[2]["call_id"]!!.jsonPrimitive.content)
        assertEquals("""{"time":"10:30"}""", input[2]["output"]!!.jsonPrimitive.content)
    }

    // ── Responses: response → ADK ──────────────────────────────────────

    @Test
    fun `incomplete and failed statuses map to the right finish reason`() {
        assertEquals(
            FinishReason.MAX_TOKENS,
            openAiJson.decodeFromString<ResponsesResponse>(
                """{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"}}"""
            ).finishReason(),
        )
        assertEquals(
            FinishReason.SAFETY,
            openAiJson.decodeFromString<ResponsesResponse>(
                """{"status":"incomplete","incomplete_details":{"reason":"content_filter"}}"""
            ).finishReason(),
        )
        assertEquals(
            FinishReason.OTHER,
            openAiJson.decodeFromString<ResponsesResponse>("""{"status":"failed"}""").finishReason(),
        )
        assertNull(
            openAiJson.decodeFromString<ResponsesResponse>("""{"status":"in_progress"}""")
                .finishReason(),
        )
    }

    // ── Responses: stream events ───────────────────────────────────────

    @Test
    fun `text delta and completed events parse`() {
        val delta = openAiJson.decodeFromString<ResponsesStreamEvent>(
            """{"type":"response.output_text.delta","delta":"He","item_id":"i1","output_index":0,"sequence_number":1}"""
        )
        assertEquals(ResponsesWire.EVENT_TEXT_DELTA, delta.type)
        assertEquals("He", delta.delta)

        val completed = openAiJson.decodeFromString<ResponsesStreamEvent>(
            """{"type":"response.completed","sequence_number":9,"response":$COMPLETED_RESPONSE}"""
        )
        assertEquals(ResponsesWire.EVENT_COMPLETED, completed.type)
        assertEquals(FinishReason.STOP, completed.response!!.finishReason())
    }

    @Test
    fun `output item done carries the complete function call`() {
        val event = openAiJson.decodeFromString<ResponsesStreamEvent>(
            """{"type":"response.output_item.done","output_index":0,"sequence_number":3,"item":{"type":"function_call","call_id":"call_1","name":"get_time","arguments":"{\"city\":\"Paris\"}"}}"""
        )
        val call = event.item!!.toFunctionCall()!!
        assertEquals("get_time", call.name)
        assertEquals("call_1", call.id)
        assertEquals("Paris", call.args["city"])
    }

    // ── Chat Completions: regression guard for the shared-wire refactor ─

    @Test
    fun `chat request keeps the chat shape`() {
        val body =
            OpenAiChatCompletionsModel(
                config = provider(ProviderApiType.CHAT_COMPLETIONS),
                modelId = "gpt-test",
                httpClient = OkHttpClient(),
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )
                .buildRequest(fullRequest())
                .bodyJson()

        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals("system", messages[0]["role"]!!.jsonPrimitive.content)
        assertEquals("be nice", messages[0]["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1]["role"]!!.jsonPrimitive.content)
        // Chat Completions keeps `stop` and nests the tool under `function`.
        assertEquals("END", body["stop"]!!.jsonArray.single().jsonPrimitive.content)
        val tool = body["tools"]!!.jsonArray.single().jsonObject
        // Regression: `type` must survive the encodeDefaults=false JSON config.
        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        assertEquals(
            "get_time",
            tool["function"]!!.jsonObject["name"]!!.jsonPrimitive.content,
        )
    }

    // ── Chat Completions: stream frames ────────────────────────────────

    @Test
    fun `a stream frame after the opening one still parses`() {
        // Regression: `role` appears only in the opening frame of an OpenAI stream; the
        // frames that follow carry `content` alone. When the response reused the
        // request-side message type — where `role` is required — every later frame failed
        // to decode, and because a bad frame is skipped rather than fatal, a provider that
        // answered perfectly produced an empty reply that never completed.
        assertEquals(
            "assistant",
            chatFrame("""{"role":"assistant","content":""}""").delta!!.role,
        )
        assertEquals("pong", chatFrame("""{"content":"pong"}""").delta!!.content)
        // The closing frame carries neither role nor content.
        assertNull(chatFrame("""{}""").delta!!.content)
        // Tool-call frames arrive with no content at all.
        assertEquals(
            "get_time",
            chatFrame(
                """{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"get_time","arguments":""}}]}"""
            ).delta!!.toolCalls!!.single().function!!.name,
        )
    }

    // ── Chat Completions: stream frames ────────────────────────────────

    @Test
    fun `a tool call split across frames is reassembled`() {
        // OpenAI streams one call over several frames: the first carries the id and the
        // name, and each later frame appends a slice of the `arguments` JSON text. Reading
        // a frame on its own yields a call with no arguments at all — silently, because
        // the argument parser tolerates a broken prefix instead of failing.
        val fragments = ToolCallFragments()
        toolCallFrames().forEach(fragments::add)

        val call = fragments.complete().single()
        assertEquals("current_time", call.name)
        assertEquals("call_1", call.id)
        assertEquals("Paris", call.args["city"])
    }

    @Test
    fun `entries without an index are taken as whole calls`() {
        // A gateway that ignores `stream` answers with one complete object, whose entries
        // carry no index — the case that must not be mistaken for fragments of one call.
        val fragments = ToolCallFragments()
        fragments.add(toolCall("""{"id":"call_1","type":"function","function":{"name":"get_time","arguments":"{\"city\":\"Paris\"}"}}"""))
        fragments.add(toolCall("""{"id":"call_2","type":"function","function":{"name":"get_date","arguments":"{}"}}"""))

        assertEquals(listOf("get_time", "get_date"), fragments.complete().map { it.name })
        assertEquals("Paris", fragments.complete().first().args["city"])
    }

    @Test
    fun `an entry that never names a function is dropped`() {
        val fragments = ToolCallFragments()
        fragments.add(toolCall("""{"index":0,"function":{"arguments":"{}"}}"""))

        assertTrue(fragments.complete().isEmpty())
    }

    // ── Fixtures ───────────────────────────────────────────────────────

    private fun toolCall(json: String): ChatToolCall =
        openAiJson.decodeFromString<ChatToolCall>(json)

    private fun toolCallFrames(): List<ChatToolCall> = listOf(
        toolCall("""{"index":0,"id":"call_1","type":"function","function":{"name":"current_time","arguments":""}}"""),
        toolCall("""{"index":0,"function":{"arguments":"{\"city\":"}}"""),
        toolCall("""{"index":0,"function":{"arguments":"\"Paris\"}"}}"""),
    )

    private fun chatFrame(deltaJson: String): ChatChoice =
        openAiJson.decodeFromString<ChatResponse>(
            """{"id":"1","object":"chat.completion.chunk","choices":[{"index":0,"delta":$deltaJson,"finish_reason":null}]}"""
        ).choices.single()

    private fun responsesModel() = OpenAiResponsesModel(
        config = provider(ProviderApiType.RESPONSES),
        modelId = "gpt-test",
        httpClient = OkHttpClient(),
        ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
    )

    private fun provider(apiType: ProviderApiType) = ProviderConfig(
        id = "test",
        name = "Test",
        baseUrl = "https://api.example.com",
        apiKey = "sk-test",
        apiType = apiType,
    )

    private fun fullRequest() = LlmRequest(
        contents = listOf(
            Content(role = Role.USER, parts = listOf(Part(text = "hi")))
        ),
        config = GenerateContentConfig(
            systemInstruction = Content(role = Role.SYSTEM, parts = listOf(Part(text = "be nice"))),
            tools = listOf(
                Tool(
                    functionDeclarations = listOf(
                        FunctionDeclaration(
                            name = "get_time",
                            description = "Current time",
                            parameters = Schema(type = Type.OBJECT),
                        )
                    )
                )
            ),
            temperature = 0.5f,
            maxOutputTokens = 256,
            stopSequences = listOf("END"),
        ),
    )

    private fun Request.bodyJson(): JsonObject {
        val buffer = Buffer()
        checkNotNull(body).writeTo(buffer)
        return openAiJson.parseToJsonElement(buffer.readUtf8()).jsonObject
    }

    private companion object {
        const val COMPLETED_RESPONSE = """
        {
          "id": "resp_1",
          "object": "response",
          "status": "completed",
          "output": [
            {
              "type": "message",
              "id": "msg_1",
              "role": "assistant",
              "status": "completed",
              "content": [{"type": "output_text", "text": "Hello there", "annotations": []}]
            },
            {
              "type": "function_call",
              "id": "fc_1",
              "call_id": "call_1",
              "name": "get_time",
              "arguments": "{\"city\":\"Paris\"}",
              "status": "completed"
            }
          ],
          "usage": {"input_tokens": 10, "output_tokens": 4, "total_tokens": 14}
        }
        """
    }
}
