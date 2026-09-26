package com.evolet.tachyon.twin

/**
 * INTEGRATIONSv2.md §7.6, code-enforced (not just prompted): a trait is rejected if its field isn't
 * one the section asked about, or if field/value/evidence touches a blocked category —
 * health/diagnosis, personality-type labels, religion, caste, politics, sexuality, money.
 * Pure Kotlin → unit-tested with adversarial answers.
 */
object TraitFilter {

    enum class Reason { FIELD_NOT_ALLOWED, BLOCKED_CATEGORY }

    private val BLOCKED = listOf(
        // health / diagnosis / mental health
        "health", "medical", "diagnos", "disease", "illness", "disorder", "depress", "anxiety", "adhd", "autis", "bipolar",
        "ocd", "ptsd", "therapy", "therapist", "medicat", "medicine", "diabet", "cancer", "pregnan", "mental",
        // personality-type labels
        "mbti", "introvert", "extrovert", "enneagram", "personality type", "big five",
        // religion
        "religio", "hindu", "muslim", "islam", "sikh", "christian", "jain", "buddhis", "god", "prayer", "temple", "mosque", "church", "gurudwara",
        // caste
        "caste", "jaati", "dalit", "brahmin", "kshatriya", "vaishya", "shudra", "obc", "general category", "reservation",
        // politics
        "politic", "bjp", "congress party", "election", "vote", "leftist", "right-wing", "right wing",
        // sexuality
        "sexual", "gay", "lesbian", "bisexual", "queer", "sex",
        // money
        "salary", "income", "debt", "loan", "emi", "bank balance", "savings", "net worth", "lakh", "crore", "rupee", "₹", "ctc",
    )

    private val MBTI = Regex("""\b[IE][NS][TF][JP]\b""")

    fun check(field: String, value: String, evidence: String, allowedFields: Set<String>): Reason? {
        if (field !in allowedFields) return Reason.FIELD_NOT_ALLOWED
        val text = "$field $value $evidence"
        if (MBTI.containsMatchIn(text)) return Reason.BLOCKED_CATEGORY
        val lower = text.lowercase()
        // Stems (>= 5 chars, e.g. "depress", "religio") match as word prefixes; short words match whole-word only.
        val hit = BLOCKED.any { w ->
            if (!w.first().isLetter()) return@any lower.contains(w) // "₹"
            val tail = if (w.length >= 5) "" else """\b"""
            Regex("""\b${Regex.escape(w)}$tail""").containsMatchIn(lower)
        }
        return if (hit) Reason.BLOCKED_CATEGORY else null
    }
}
