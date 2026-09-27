package com.evolet.tachyon.agents

import android.util.Log
import com.evolet.tachyon.asr.AsrEngine
import java.io.IOException

/**
 * Perceiver: audio in, utterances out. Retries a chunk once on an I/O error, because losing a
 * chunk silently drops part of the live transcript (seen on-device when whisper-server restarted).
 */
class Perceiver(
    private val bus: AgentBus,
    private val asr: () -> AsrEngine,
) {
    data class Result(val utterance: Utterance, val latencyMs: Long, val engine: String)

    suspend fun perceive(sessionId: String, pcm: ShortArray, offsetMs: Long, sampleRate: Int, languageHint: String?): Result {
        val engine = asr()
        val r = try {
            engine.transcribe(pcm, languageHint)
        } catch (e: IOException) {
            Log.w(TAG, "chunk failed, retrying once", e)
            try {
                engine.transcribe(pcm, languageHint)
            } catch (e2: IOException) {
                bus.emit(AgentEvent.EngineDegraded(engine.name, e2.message ?: "I/O error"))
                throw e2
            }
        }
        val u = Utterance(
            text = r.text,
            speaker = null,
            lang = r.language,
            t0Ms = offsetMs,
            t1Ms = offsetMs + pcm.size * 1000L / sampleRate,
        )
        if (u.text.isNotBlank()) bus.emit(AgentEvent.UtteranceReady(sessionId, u))
        return Result(u, r.latencyMs, engine.name)
    }

    private companion object {
        const val TAG = "Perceiver"
    }
}
