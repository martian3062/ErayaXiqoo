package com.evolet.tachyon.eraya

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class RawCommitment(
    val owner: String = "",
    val task: String = "",
    @SerialName("to_whom") val toWhom: String? = null,
    @SerialName("deadline_text") val deadlineText: String = "none",
    @SerialName("deadline_iso") val deadlineIso: String? = null,
    val evidence: String = "",
    val confidence: Double = 0.0,
)

@Serializable
data class RawExtraction(val commitments: List<RawCommitment> = emptyList())

sealed interface Validation {
    data class Valid(
        val items: List<RawCommitment>,
        val droppedNoEvidence: Int,
        val droppedLowConfidence: Int,
        val droppedHypothetical: Int = 0,
    ) : Validation

    data class Invalid(val reason: String) : Validation
}

/**
 * CLAUDE.md §8.3: strip → parse → drop items whose evidence isn't in the transcript
 * (anti-hallucination) → drop hedged/hypothetical evidence ("maybe", "someday"; enforced in code
 * because the 2B model sometimes proposes them) → drop confidence < 0.4. The retry lives in ExtractionAgent.
 * Pure Kotlin (no Android), so it's unit-tested on the laptop.
 */
class SchemaValidator(private val minConfidence: Double = 0.4) {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        allowTrailingComma = true
    }

    fun validate(raw: String, transcript: String): Validation {
        val body = extractJsonObject(raw) ?: return Validation.Invalid("no JSON object in model output")
        val parsed = try {
            json.decodeFromString<RawExtraction>(body)
        } catch (e: SerializationException) {
            return Validation.Invalid("invalid JSON: ${e.message?.take(160)}")
        } catch (e: IllegalArgumentException) {
            return Validation.Invalid("invalid JSON: ${e.message?.take(160)}")
        }

        val haystack = normalize(transcript)
        var noEvidence = 0
        var lowConfidence = 0
        var hypothetical = 0
        val kept = parsed.commitments.filter { c ->
            when {
                c.task.isBlank() || !evidenceFound(c.evidence, haystack) -> { noEvidence++; false }
                isHedged(c.evidence) -> { hypothetical++; false }
                c.confidence < minConfidence -> { lowConfidence++; false }
                else -> true
            }
        }
        return Validation.Valid(kept.map { it.copy(confidence = it.confidence.coerceIn(0.0, 1.0)) }, noEvidence, lowConfidence, hypothetical)
    }

    companion object {
        /** Hedge words that mark an idea, not a promise (English + romanised/Devanagari Hindi). */
        private val HEDGES = Regex(
            """\b(maybe|perhaps|someday|some day|might|we could|we should|should we|could we|what if|shayad|sochte hain)\b|शायद""",
        )

        fun isHedged(evidence: String): Boolean = HEDGES.containsMatchIn(normalize(evidence))

        /** Rule 1 (strip before first `{`, after last `}`), plus repair of a bare top-level array. */
        fun extractJsonObject(raw: String): String? {
            val obj = raw.indexOf('{')
            val arr = raw.indexOf('[')
            if (arr >= 0 && (obj < 0 || arr < obj)) {
                val end = raw.lastIndexOf(']')
                if (end > arr) return """{"commitments":${raw.substring(arr, end + 1)}}"""
            }
            val end = raw.lastIndexOf('}')
            return if (obj >= 0 && end > obj) raw.substring(obj, end + 1) else null
        }

        /**
         * Case-, whitespace- and punctuation-insensitive form. Keeps letters, combining marks
         * (Devanagari matras) and digits, so Hindi evidence still matches.
         */
        fun normalize(s: String): String =
            s.lowercase().replace(Regex("""[^\p{L}\p{M}\p{N}]+"""), " ").trim()

        fun evidenceFound(evidence: String, normalizedTranscript: String): Boolean {
            val e = normalize(evidence)
            return e.isNotEmpty() && " $normalizedTranscript ".contains(" $e ")
        }
    }
}
