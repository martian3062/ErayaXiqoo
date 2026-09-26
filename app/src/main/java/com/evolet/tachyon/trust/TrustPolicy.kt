package com.evolet.tachyon.trust

import com.evolet.tachyon.data.PreferencePair
import com.evolet.tachyon.twin.Person
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Guardian (INTEGRATIONSv2.md §9): everything that leaves the app boundary goes through here.
 * Tiers: PUBLIC_COMMITMENT · WORK · PERSONAL · BIOMETRIC (never exported). Pure Kotlin → unit-tested.
 */
object TrustPolicy {

    /** Replaces every known person name/alias with [PERSON] (whole words, case-insensitive). */
    fun redactPeople(text: String, people: List<Person>): String {
        val names = people.flatMap { listOf(it.name) + it.aliases }.filter { it.isNotBlank() }.sortedByDescending { it.length }
        return names.fold(text) { acc, n -> acc.replace(Regex("""\b${Regex.escape(n)}\b""", RegexOption.IGNORE_CASE), "[PERSON]") }
    }

    /** F15 export: TRL DPO JSONL ({"prompt","chosen","rejected"}), names stripped unless the owner opts in. */
    fun exportJsonl(pairs: List<PreferencePair>, people: List<Person>, includeNames: Boolean): String =
        pairs.filter { it.rejected != null }.joinToString("\n", postfix = if (pairs.isEmpty()) "" else "\n") { p ->
            fun f(s: String) = if (includeNames) s else redactPeople(s, people)
            buildJsonObject {
                put("prompt", f(p.prompt))
                put("chosen", f(p.chosen))
                put("rejected", f(p.rejected!!))
                put("kind", p.kind.name)
            }.toString()
        }
}
