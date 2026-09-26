package com.evolet.tachyon.agents

import android.util.Log
import com.evolet.tachyon.eraya.Proposal
import com.evolet.tachyon.llm.LlmEngine
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Verifier agent: a second model pass that votes "firm promise?" on each proposal.
 * Bench on the iQOO 15 (Qwen3.5-2B, 5 fixtures): precision 0.85 → 1.00 at recall 1.00, +~4 s.
 * Fails open: if the verdict can't be parsed, every proposal is kept (the owner still confirms).
 */
class Verifier(private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }) {

    data class Result(val kept: List<Proposal>, val rejected: Int, val latencyMs: Long)

    suspend fun verify(llm: LlmEngine, transcript: String, items: List<Proposal>): Result {
        if (items.isEmpty()) return Result(items, 0, 0)
        val listing = items.mapIndexed { i, p -> "${i + 1}. \"${p.evidence}\"" }.joinToString("\n")
        return try {
            val r = llm.complete(SYSTEM, "Transcript:\n$transcript\n\nCandidates:\n$listing", SCHEMA, maxTokens = 64)
            val verdicts = parseVerdicts(r.text)
            if (verdicts == null || verdicts.size < items.size) {
                Log.w(TAG, "unparseable verdicts, keeping all: ${r.text.take(120)}")
                Result(items, 0, r.latencyMs)
            } else {
                val kept = items.filterIndexed { i, _ -> verdicts[i] }
                Result(kept, items.size - kept.size, r.latencyMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "verifier failed, keeping all", e)
            Result(items, 0, 0)
        }
    }

    internal fun parseVerdicts(text: String): List<Boolean>? = runCatching {
        val body = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)
        json.parseToJsonElement(body).jsonObject["verdicts"]!!.jsonArray.map { it.jsonPrimitive.booleanOrNull ?: true }
    }.getOrNull()

    companion object {
        private const val TAG = "Verifier"
        const val SYSTEM =
            "You check candidate commitments from a meeting transcript. For each numbered candidate, " +
                "answer true only if the quoted words are an explicit, firm promise by someone to do something " +
                "(not an idea, question, hope, condition, or something already done). Return JSON only."
        const val SCHEMA =
            """{"type":"object","properties":{"verdicts":{"type":"array","items":{"type":"boolean"}}},"required":["verdicts"]}"""
    }
}
