package com.evolet.tachyon.twin

/**
 * Writes a CONFIRMED trait into the persona (§7.5): always appended to `traits` with its evidence,
 * and mapped onto the structured field that drives behaviour (durations → "Tight for you",
 * reply examples → drafts, quiet hours → reminders). Pure → unit-tested.
 */
object PersonaMerger {

    private val HINGLISH = Regex("""\b(haan|hn|nahi|ni|kar|kr|dunga|bhej|yaar|bhai|arre|oye|bss|bas|thik|theek|kal|abhi|kya|hai|h)\b""", RegexOption.IGNORE_CASE)
    private val DURATION_TEXT = Regex("""\b(half a day|half day|\d+(?:\.\d+)?\s*(?:min|minutes?|h|hrs?|hours?|ghante|ghanta|d|days?|din|weeks?|hafte|hafta))\b""", RegexOption.IGNORE_CASE)

    fun merge(p: Persona, t: Trait): Persona {
        val base = p.copy(traits = p.traits.filterNot { it.field == t.field && it.value.equals(t.value, true) } + t)
        return when (t.field) {
            "name" -> base.copy(owner = base.owner.copy(name = t.value))
            "role", "current_focus" -> base.copy(owner = base.owner.copy(role = t.value))
            "languages" -> base.copy(owner = base.owner.copy(languages = (base.owner.languages + languageCodes(t.value)).distinct()))
            "style_examples_formal" -> base.withExample("formal", t.evidence)
            "style_examples_casual" -> base.withExample(if (HINGLISH.containsMatchIn(t.evidence)) "casual_hinglish" else "neutral", t.evidence)
            "sign_offs" -> base.copy(style = base.style.copy(signOffs = (listOf(t.value) + base.style.signOffs).distinct()))
            "commit_style" -> base.copy(commit = base.commit.copy(commitStyle = t.value))
            "slip_behaviour" -> base.copy(commit = base.commit.copy(slipBehaviour = t.value))
            "underestimates" -> base.copy(commit = base.commit.copy(underestimates = (base.commit.underestimates + t.value).distinct()))
            "self_reported_durations" -> {
                val dur = DURATION_TEXT.find(t.value)?.value ?: DURATION_TEXT.find(t.evidence)?.value ?: return base
                val bucket = RiskScorer.bucket(t.value) ?: RiskScorer.bucket(t.evidence) ?: "report"
                base.copy(commit = base.commit.copy(selfReportedDurations = base.commit.selfReportedDurations + (bucket to dur)))
            }
            "peak_hours" -> base.copy(rhythm = base.rhythm.copy(peakHours = t.value))
            "quiet_hours" -> base.copy(rhythm = base.rhythm.copy(quietHours = t.value))
            "weekend_policy" -> base.copy(rhythm = base.rhythm.copy(weekendPolicy = t.value))
            "reminder_tolerance" -> base.copy(rhythm = base.rhythm.copy(reminderTolerance = Regex("""\d+""").find(t.value)?.value?.toIntOrNull() ?: base.rhythm.reminderTolerance))
            "assistant_tone" -> base.copy(assistant = base.assistant.copy(tone = t.value))
            "draft_formality" -> base.copy(assistant = base.assistant.copy(draftFormality = t.value))
            "hard_limits" -> base.copy(assistant = base.assistant.copy(hardLimits = (base.assistant.hardLimits + t.value).distinct()))
            else -> base // priorities, goals, … stay as evidence-backed traits only
        }
    }

    private fun Persona.withExample(register: String, text: String) =
        copy(style = style.copy(examples = (style.examples + Persona.StyleExample(register, text)).distinctBy { it.text }))

    fun languageCodes(value: String): List<String> {
        val v = value.lowercase()
        return buildList {
            if ("english" in v || "hinglish" in v) add("en")
            if ("hindi" in v || "hinglish" in v) add("hi")
            if ("punjabi" in v) add("pa")
            if ("telugu" in v) add("te")
            if ("tamil" in v) add("ta")
        }
    }
}
