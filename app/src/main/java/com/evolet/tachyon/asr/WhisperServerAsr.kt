package com.evolet.tachyon.asr

import android.os.SystemClock
import com.evolet.tachyon.audio.WavWriter
import com.evolet.tachyon.net.LocalHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Tier 2 ASR: whisper.cpp `whisper-server` running in Termux on 127.0.0.1:8082. */
class WhisperServerAsr(
    private val baseUrl: String = "http://127.0.0.1:8082",
    private val http: OkHttpClient = LocalHttp.client,
) : AsrEngine {

    override val name = "whisper.cpp·CPU"

    /** Any HTTP answer on "/" means the server is up (the model is loaded before it listens). */
    override suspend fun load() = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url("$baseUrl/").get().build()).execute().close()
        } catch (e: IOException) {
            throw IOException("whisper-server not reachable on 127.0.0.1:8082 — run termux/run_servers.sh", e)
        }
    }

    override suspend fun transcribe(pcm16k: ShortArray, languageHint: String?): AsrResult =
        withContext(Dispatchers.IO) {
            val wav = WavWriter.encode(pcm16k, 16_000)
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "chunk.wav", wav.toRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("response_format", "json")
                .addFormDataPart("temperature", "0.0")
                .apply { if (languageHint != null) addFormDataPart("language", languageHint) }
                .build()
            val request = Request.Builder().url("$baseUrl/inference").post(body).build()

            val t0 = SystemClock.elapsedRealtime()
            val text = http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("whisper-server HTTP ${resp.code}")
                val obj = LocalHttp.json.parseToJsonElement(resp.body.string()).jsonObject
                obj["text"]?.jsonPrimitive?.content.orEmpty()
            }
            AsrResult(cleanWhisperText(text), SystemClock.elapsedRealtime() - t0, languageHint)
        }

    override fun close() = Unit
}

/** Drops whisper's non-speech markers like "[BLANK_AUDIO]" or "(music)". */
internal fun cleanWhisperText(raw: String): String =
    raw.replace(Regex("""\[[A-Z_ ]+]|\((?:music|silence|noise|applause)\)""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
