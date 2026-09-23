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
        val body = model.buildRequest(fullRequest(), stream = false).bodyJson()

        assertEquals("gpt-test", body["model"]!!.jsonPrimitive.content)
        assertEquals("be nice", body["instructions"]!!.jsonPrimitive.content)
        assertEquals(0.5, body["temperature"]!!.jsonPrimitive.content.toDouble(), 0.0001)
        assertEquals("256", body["max_output_tokens"]!!.jsonPrimitive.content)
        assertFalse("stream must be omitted/false", body["stream"]?.jsonPrimitive?.boolean ?: false)

        // System prompt moved out of `messages` into top-level `instructions`.
        assertNull(body["messages"])
        // The Responses API has no `stop` parameter: ADK's stopSequences must not leak.
        assertNull(body["stop"])
    }

    @Test
    fun `responses request flattens function tools`() {
        val body = responsesModel().buildRequest(fullRequest(), stream = false).bodyJson()
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
                .buildRequest(LlmRequest(contents = contents), stream = false)
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
    fun `completed response maps text tool call usage and finish reason`() {
        val response = openAiJson.decodeFromString<ResponsesResponse>(COMPLETED_RESPONSE)
        val llm = response.toLlmResponse(partial = false)

        val parts = llm.content!!.parts
        assertEquals("Hello there", parts[0].text)
        val call = parts[1].functionCall!!
        assertEquals("get_time", call.name)
        assertEquals("call_1", call.id)
        assertEquals("Paris", call.args["city"])

        assertEquals(FinishReason.STOP, llm.finishReason)
        assertEquals(10, llm.usageMetadata!!.promptTokenCount)
        assertEquals(4, llm.usageMetadata!!.candidatesTokenCount)
        assertEquals(14, llm.usageMetadata!!.totalTokenCount)
        assertEquals(Role.MODEL, llm.content!!.role)
        assertFalse(llm.partial)
    }

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

    @Test
    fun `unknown output item types are ignored`() {
        val response = openAiJson.decodeFromString<ResponsesResponse>(
            """{"status":"completed","output":[{"type":"reasoning","id":"r1"},{"type":"message","content":[{"type":"refusal","refusal":"no"}]}]}"""
        )
        assertNull(response.toLlmResponse(partial = false).content)
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
                .buildRequest(fullRequest(), stream = false)
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

    // ── Fixtures ───────────────────────────────────────────────────────

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
