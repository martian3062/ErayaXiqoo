package com.evolet.tachyon.twin

import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.data.Status
import java.util.Locale
import kotlin.math.exp

/** A commitment cited by ERAYA's private recall answer. */
data class RecallSource(
    val id: String,
    val status: Status,
    val task: String,
    val person: String?,
    val deadline: String?,
    val evidence: String,
)

data class RecallResult(
    val answer: String,
    val sources: List<RecallSource>,
    val engine: String = "HybridMemory·keyword+recency",
)

/**
 * F18 private recall over the owner's confirmed commitment history.
 *
 * This is deliberately deterministic: intent routing, ownership filtering, ranking and citations
 * do not depend on the conversational model. It borrows the useful shape of the laptop twin's
 * memory layer (hybrid field matching, recency tilt and duplicate suppression) without copying or
 * importing any private corpus. The local LLM remains available for ordinary conversation.
 */
class CommitmentRecallEngine(
    private val records: suspend () -> List<Commitment>,
    private val personName: (String) -> String? = { null },
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun isRecallQuery(query: String): Boolean = RecallIntent.matches(query)

    suspend fun recall(query: String, limit: Int = 3): RecallResult? {
        if (!isRecallQuery(query)) return null
        val normalised = normalise(query)
        val scope = requestedStatus(normalised)
        val terms = tokens(normalised).filterNotTo(linkedSetOf()) { it in QUERY_STOP_WORDS }
        val eligible = records()
            .asSequence()
            .filter(::belongsToOwner)
            .filter { scope == null || it.status == scope }
            .filter { it.status == Status.ACCEPTED || it.status == Status.REJECTED }
            .toList()

        val scored = eligible.map { commitment ->
            val person = commitment.toPersonId?.let(personName) ?: commitment.toWhom
            Scored(
                commitment = commitment,
                person = person,
                score = score(commitment, person, terms),
            )
        }
        val minimum = if (terms.isEmpty()) 0.0 else 1.0
        val picked = scored
            .filter { it.score >= minimum }
            .sortedWith(compareByDescending<Scored> { it.score }.thenByDescending { it.commitment.decidedAt ?: 0L })
            .distinctBy { duplicateKey(it.commitment, it.person) }
            .take(limit.coerceIn(1, 5))
            .map { it.toSource() }

        return RecallResult(answer = answer(scope, picked), sources = picked)
    }

    private fun score(commitment: Commitment, person: String?, terms: Set<String>): Double {
        if (terms.isEmpty()) return recency(commitment.decidedAt)
        val task = tokens(commitment.task)
        val evidence = tokens(commitment.evidence)
        val people = tokens(listOfNotNull(person, commitment.toWhom).joinToString(" "))
        val deadline = tokens(listOf(commitment.deadlineText, commitment.deadlineIso.orEmpty()).joinToString(" "))
        var value = 0.0
        terms.forEach { term ->
            if (term in task) value += 3.0
            if (term in people) value += 4.5
            if (term in deadline) value += 2.5
            if (term in evidence) value += 1.0
        }
        val phrase = terms.joinToString(" ")
        if (phrase.length >= 4 && normalise(commitment.task).contains(phrase)) value += 2.0
        return value + recency(commitment.decidedAt)
    }

    /** Recency is a tiebreaker only; a fresh unrelated record cannot beat a relevant older one. */
    private fun recency(decidedAt: Long?): Double {
        val ageDays = ((now() - (decidedAt ?: 0L)).coerceAtLeast(0L) / 86_400_000.0)
        return 0.45 * exp(-ageDays / 180.0)
    }

    private fun answer(scope: Status?, sources: List<RecallSource>): String {
        if (sources.isEmpty()) {
            val kind = if (scope == Status.REJECTED) "rejected proposal" else "confirmed commitment"
            return "I couldn't find a $kind matching that in your private on-device records."
        }
        val kind = if (scope == Status.REJECTED) "rejected proposal" else "commitment"
        return buildString {
            append("I found ").append(sources.size).append(' ').append(kind)
            if (sources.size != 1) append('s')
            append(" in your private records. ")
            sources.forEachIndexed { index, source ->
                if (index > 0) append(' ')
                append('[').append(index + 1).append("; #").append(source.id.take(8)).append("] ")
                append(source.task.trim().trimEnd('.', '!', '?'))
                source.person?.takeIf(String::isNotBlank)?.let { append(" for ").append(it) }
                source.deadline?.takeIf(String::isNotBlank)?.let { append(", due ").append(it) }
                append('.')
            }
        }
    }

    private fun Scored.toSource() = RecallSource(
        id = commitment.id,
        status = commitment.status,
        task = commitment.task,
        person = person,
        deadline = commitment.deadlineText.takeIf(String::isNotBlank),
        evidence = commitment.evidence,
    )

    private data class Scored(val commitment: Commitment, val person: String?, val score: Double)

    private fun duplicateKey(commitment: Commitment, person: String?): String =
        "${normalise(commitment.task)}|${normalise(person.orEmpty())}|${commitment.status}"

    companion object {
        private val OWNER_NAMES = setOf("you", "i", "me", "myself", "evolet")
        private val QUERY_STOP_WORDS = setOf(
            "a", "about", "all", "any", "are", "did", "do", "find", "for", "have", "i", "in", "is",
            "list", "me", "my", "of", "please", "show", "tell", "that", "the", "to", "was", "were",
            "what", "when", "which", "who", "with", "you", "remember", "recall", "promise", "promised",
            "promises", "commitment", "commitments", "task", "tasks", "owe", "due", "accepted", "rejected",
            "proposal", "proposals", "maine", "mera", "meri", "mere", "kya", "kaun", "dikhao", "batao",
            "yaad", "waada", "vada", "kaam",
        )

        private fun belongsToOwner(commitment: Commitment): Boolean = commitment.ownerIsUser ||
            normalise(commitment.owner) in OWNER_NAMES

        private fun requestedStatus(query: String): Status? = when {
            Regex("\\b(reject|rejected|declined|discarded|cancelled)\\b").containsMatchIn(query) -> Status.REJECTED
            Regex("\\b(all|every|decisions?)\\b").containsMatchIn(query) -> null
            else -> Status.ACCEPTED
        }

        private fun normalise(value: String): String = value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

        private fun tokens(value: String): Set<String> = normalise(value)
            .split(' ')
            .filterTo(linkedSetOf()) { it.length >= 2 }
    }
}

internal object RecallIntent {
    private val direct = listOf(
        Regex("\\bwhat (?:did|have) i (?:promise|commit)\\b"),
        Regex("\\bwhat do i owe\\b"),
        Regex("\\b(?:show|list|find|recall) (?:my )?(?:commitments?|promises?|tasks?|deadlines?)\\b"),
        Regex("\\bwhat (?:was|did i) reject(?:ed)?\\b"),
        Regex("\\b(?:my|mine) (?:commitments?|promises?|deadlines?)\\b"),
        Regex("\\bmaine\\b.{0,48}\\b(?:promise|waada|vada|kaam)\\b"),
        Regex("\\b(?:mera|meri|mere)\\b.{0,32}\\b(?:promise|waada|vada|commitment|kaam)\\b"),
    )

    fun matches(query: String): Boolean {
        val text = query.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        if (direct.any { it.containsMatchIn(text) }) return true
        val hasMemoryNoun = Regex("\\b(commitments?|promises?|deadlines?|rejected proposals?|tasks? i accepted)\\b")
            .containsMatchIn(text)
        val asks = Regex("\\b(what|which|show|list|find|tell|when|who|remember|recall)\\b").containsMatchIn(text)
        return hasMemoryNoun && asks
    }
}
