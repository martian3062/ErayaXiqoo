package com.evolet.tachyon.twin

import android.content.Context
import com.evolet.tachyon.eraya.SchemaValidator
import com.evolet.tachyon.llm.LlmEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable data class Section(val id: Int, val title: String, val demo: Boolean = false)

@Serializable data class Question(
    val id: String,
    val section: Int,
    val text: String,
    val targets: List<String>,
    val demo: Boolean = false,
    val followup: String? = null,
)

@Serializable data class QuestionBank(val sections: List<Section>, val questions: List<Question>) {
    fun plan(demo: Boolean): List<Question> = if (demo) questions.filter { it.demo } else questions

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun load(context: Context): QuestionBank =
            context.assets.open("twin/questions.json").bufferedReader().use { json.decodeFromString(serializer(), it.readText()) }
    }
}

data class Answer(val question: Question, val text: String, val followUpText: String? = null) {
    val full get() = listOfNotNull(text, followUpText).joinToString(" ").trim()
}

data class TraitProposal(val field: String, val value: String, val evidence: String, val confidence: Double)

/**
 * F17 behaviour interview logic (INTEGRATIONSv2.md §7). Pure except for the LLM call, so the
 * follow-up rule and trait validation are unit-tested.
 */
object InterviewEngine {

    /** At most one follow-up, only when the answer is short (< 12 words) and the question has one. */
    fun followUpFor(q: Question, answer: String, alreadyAsked: Boolean): String? =
        if (!alreadyAsked && q.followup != null && answer.split(Regex("\\s+")).count { it.isNotBlank() } < 12) q.followup else null

    /** §7.4 prompt, verbatim, with the section's target fields and answers filled in. */
    fun prompt(targets: List<String>, answers: List<Answer>): String = TRAIT_PROMPT
        .replace("{TARGET_FIELDS}", targets.joinToString(", "))
        .replace("{SECTION_ANSWERS}", answers.joinToString("\n") { "Q: ${it.question.text}\nA: ${it.full}" })

    private val TRAIT_PROMPT = """
        You build a personal profile from an interview section. Output JSON only.

        For each trait you can support, output:
          field (one of the target fields listed), value (short, plain words),
          evidence (EXACT quote from the answers), confidence 0.0–1.0.

        Rules:
        - Only state what the person actually said. Do not infer personality types,
          mental health, medical conditions, religion, caste, politics or finances.
        - If the answers do not support a field, omit it.
        - Keep values short (max 15 words) and in English; evidence stays in the
          original language.
        Target fields: {TARGET_FIELDS}
        Answers:
        {SECTION_ANSWERS}
    """.trimIndent()

    /** Schema with `field` constrained to the section's targets. */
    fun schema(targets: List<String>): String = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("traits") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("field") { put("type", "string"); put("enum", JsonArray(targets.map { JsonPrimitive(it) })) }
                        putJsonObject("value") { put("type", "string") }
                        putJsonObject("evidence") { put("type", "string") }
                        putJsonObject("confidence") { put("type", "number") }
                    }
                    putJsonArray("required") { add("field"); add("value"); add("evidence"); add("confidence") }
                }
            }
        }
        putJsonArray("required") { add("traits") }
    }.toString()

    @Serializable private data class RawTrait(val field: String = "", val value: String = "", val evidence: String = "", val confidence: Double = 0.0)
    @Serializable private data class RawTraits(val traits: List<RawTrait> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    data class Result(val proposals: List<TraitProposal>, val dropped: Int)

    /** §7.4 validation: evidence verbatim in the answers, confidence >= 0.5, allowed field, TraitFilter. */
    fun validate(raw: String, answers: List<Answer>, targets: Set<String>): Result {
        val body = SchemaValidator.extractJsonObject(raw) ?: return Result(emptyList(), 0)
        val parsed = runCatching { json.decodeFromString(RawTraits.serializer(), body) }.getOrNull() ?: return Result(emptyList(), 0)
        val hay = SchemaValidator.normalize(answers.joinToString(" ") { it.full })
        val kept = parsed.traits.filter { t ->
            t.value.isNotBlank() && t.confidence >= 0.5 &&
                SchemaValidator.evidenceFound(t.evidence, hay) &&
                TraitFilter.check(t.field, t.value, t.evidence, targets) == null
        }.distinctBy { it.field to it.value.lowercase() }
        return Result(kept.map { TraitProposal(it.field, it.value.trim().take(120), it.evidence.trim(), it.confidence.coerceIn(0.0, 1.0)) }, parsed.traits.size - kept.size)
    }

    /** Summarise one section (never the whole interview at once: context + latency, §7.2). */
    suspend fun extract(llm: LlmEngine, answers: List<Answer>): Result {
        if (answers.isEmpty()) return Result(emptyList(), 0)
        val targets = answers.flatMap { it.question.targets }.distinct()
        val r = llm.complete(prompt(targets, answers), "Extract the traits now.", schema(targets), maxTokens = 400)
        return validate(r.text, answers, targets.toSet())
    }
}
