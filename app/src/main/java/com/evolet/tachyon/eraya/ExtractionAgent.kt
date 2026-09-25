package com.evolet.tachyon.eraya

import android.util.Log
import com.evolet.tachyon.llm.LlmEngine
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

/** One commitment the model found. Stays a proposal until a human taps ✓ (ConfirmationLoop). */
data class Proposal(
    val owner: String,
    val task: String,
    val toWhom: String?,
    val deadlineText: String,
    val deadlineIso: String?,
    val evidence: String,
    val confidence: Double,
)

sealed interface ExtractionResult {
    val latencyMs: Long
    val engine: String

    data class Ok(
        val items: List<Proposal>,
        val dropped: Int,
        val tokensPerSec: Double?,
        override val latencyMs: Long,
        override val engine: String,
    ) : ExtractionResult

    data class Failed(val reason: String, override val latencyMs: Long, override val engine: String) : ExtractionResult
}

/** ERAYA extraction: transcript → schema-checked, evidence-grounded proposals. */
class ExtractionAgent(
    private val prompts: Prompts,
    private val validator: SchemaValidator = SchemaValidator(),
    private val deadlines: DeadlineResolver = DeadlineResolver(),
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    suspend fun extract(llm: LlmEngine, transcript: String): ExtractionResult {
        val date = today()
        var latency = 0L
        return try {
            val system = prompts.system(date)
            val schema = prompts.schema()

            var result = llm.complete(system, transcript, schema, MAX_TOKENS)
            latency += result.latencyMs
            var validation = validator.validate(result.text, transcript)

            if (validation is Validation.Invalid) {
                Log.w(TAG, "invalid output, retrying once: ${validation.reason}")
                result = llm.complete(system, "$transcript\n\n$RETRY_INSTRUCTION", schema, MAX_TOKENS)
                latency += result.latencyMs
                validation = validator.validate(result.text, transcript)
            }

            when (validation) {
                is Validation.Invalid -> ExtractionResult.Failed(validation.reason, latency, llm.name)
                is Validation.Valid -> {
                    Log.d(TAG, "kept=${validation.items.size} droppedNoEvidence=${validation.droppedNoEvidence} droppedLowConfidence=${validation.droppedLowConfidence}")
                    ExtractionResult.Ok(
                        items = validation.items.map { it.toProposal(date) },
                        dropped = validation.droppedNoEvidence + validation.droppedLowConfidence,
                        tokensPerSec = result.tokensPerSec,
                        latencyMs = latency,
                        engine = llm.name,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "extraction failed", e)
            ExtractionResult.Failed(e.message ?: e.javaClass.simpleName, latency, llm.name)
        }
    }

    private fun RawCommitment.toProposal(date: LocalDate) = Proposal(
        owner = owner.trim().ifEmpty { "Unknown" },
        task = task.trim(),
        toWhom = toWhom?.trim()?.takeUnless { it.isEmpty() || it.equals("none", true) || it.equals("null", true) },
        deadlineText = deadlineText.trim().ifEmpty { "none" },
        deadlineIso = deadlines.resolve(deadlineText, deadlineIso, date),
        evidence = evidence.trim(),
        confidence = confidence,
    )

    private companion object {
        const val TAG = "ExtractionAgent"
        const val MAX_TOKENS = 512
        const val RETRY_INSTRUCTION = "Your last output was invalid JSON. Return only valid JSON matching the schema."
    }
}
