package com.evolet.tachyon.llm

import android.os.SystemClock
import com.evolet.tachyon.net.LocalHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Tier 2 LLM: llama.cpp `llama-server` in Termux on 127.0.0.1:8081.
 * Uses /completion with a ChatML prompt (Qwen) and `json_schema`, so the grammar forces valid JSON.
 */
class LlamaServerLlm(
    private val baseUrl: String = "http://127.0.0.1:8081",
    private val http: OkHttpClient = LocalHttp.client,
) : LlmEngine {

    @Volatile private var modelLabel: String? = null

    override val name: String
        get() = listOfNotNull("llama.cpp·CPU", modelLabel).joinToString("·")

    override suspend fun load() = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url("$baseUrl/health").get().build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("llama-server still loading (HTTP ${resp.code})")
            }
        } catch (e: IOException) {
            throw IOException("llama-server not ready on 127.0.0.1:8081 — ${e.message}", e)
        }
        modelLabel = runCatching { fetchModelLabel() }.getOrNull()
    }

    override suspend fun complete(system: String, user: String, jsonSchema: String?, maxTokens: Int): LlmResult =
        withContext(Dispatchers.IO) {
            val prompt = "<|im_start|>system\n$system<|im_end|>\n" +
                "<|im_start|>user\n$user<|im_end|>\n" +
                "<|im_start|>assistant\n"
            val payload = buildJsonObject {
                put("prompt", prompt)
                put("n_predict", maxTokens)
                put("temperature", 0.1)
                put("cache_prompt", true)
                if (jsonSchema != null) put("json_schema", LocalHttp.json.parseToJsonElement(jsonSchema))
            }
            val request = Request.Builder()
                .url("$baseUrl/completion")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val t0 = SystemClock.elapsedRealtime()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("llama-server HTTP ${resp.code}")
                val obj = LocalHttp.json.parseToJsonElement(resp.body.string()).jsonObject
                val tps = obj["timings"]?.jsonObject?.get("predicted_per_second")?.jsonPrimitive?.doubleOrNull
                LlmResult(
                    text = obj["content"]?.jsonPrimitive?.content.orEmpty(),
                    latencyMs = SystemClock.elapsedRealtime() - t0,
                    tokensPerSec = tps,
                )
            }
        }

    override fun close() = Unit

    /** "/models/qwen3.5-2b-q4_0.gguf" -> "qwen3.5-2b-q4_0" for the latency badge. */
    private fun fetchModelLabel(): String? =
        http.newCall(Request.Builder().url("$baseUrl/v1/models").get().build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val id = LocalHttp.json.parseToJsonElement(resp.body.string()).jsonObject["data"]
                ?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content ?: return null
            id.substringAfterLast('/').substringAfterLast('\\').removeSuffix(".gguf")
        }
}
