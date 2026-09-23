package com.lhzkml.jasmine.core.data.datasource

import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManager
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches the model catalog of an OpenAI-protocol provider.
 *
 * Providers carry their own dynamic base URL, so this rides a plain
 * [OkHttpClient] call instead of a Retrofit service (whose baseUrl is fixed
 * at graph construction). `GET {baseUrl}/v1/models` with a Bearer key; the
 * response is parsed tolerantly:
 * - OpenAI shape: `{ "data": [ { "id": ... } ] }`
 * - DeepSeek shape: `{ "models": [ { "id" | "model_name": ... } ] }
 *
 * A baseUrl already ending in `/v1` is not doubled.
 */
@Singleton
class ProviderModelDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dispatcherManager: DispatcherManager,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns the distinct model ids advertised by the provider.
     *
     * @throws IOException on transport errors, non-2xx responses or an
     * unparsable/empty catalog — callers surface the failure to the UI.
     */
    suspend fun fetchModelIds(provider: ProviderConfig): List<String> =
        withContext(dispatcherManager.io) {
            val request = Request.Builder()
                .url(modelsUrl(provider.baseUrl))
                .header("Authorization", "Bearer ${provider.apiKey}")
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body?.string()
                    ?: throw IOException("Empty response body")
                parseModelIds(body)
            }
        }

    private fun modelsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/v1")) "$trimmed/models" else "$trimmed/v1/models"
    }

    private fun parseModelIds(body: String): List<String> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IOException("Malformed model list JSON") }
        val array = (root["data"] ?: root["models"])?.jsonArray
            ?: throw IOException("Malformed model list JSON")
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            (obj["id"] ?: obj["model_name"])?.jsonPrimitive?.contentOrNull
        }.filter { it.isNotBlank() }.distinct()
    }
}
