package com.evolet.tachyon.twin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** INTEGRATIONSv2.md §8.1. Only CONFIRMED traits ever land here (TraitReview). */
@Serializable
data class Persona(
    val version: Int = 1,
    val owner: Owner = Owner(),
    val style: Style = Style(),
    val commit: CommitHabits = CommitHabits(),
    val rhythm: Rhythm = Rhythm(),
    val assistant: AssistantPrefs = AssistantPrefs(),
    val traits: List<Trait> = emptyList(),
) {
    @Serializable data class Owner(val name: String = "You", val languages: List<String> = emptyList(), val role: String? = null)

    @Serializable data class Style(
        val rules: List<String> = emptyList(),
        val examples: List<StyleExample> = emptyList(),
        @SerialName("sign_offs") val signOffs: List<String> = emptyList(),
    )

    @Serializable data class StyleExample(val register: String, val text: String)

    @Serializable data class CommitHabits(
        @SerialName("commit_style") val commitStyle: String? = null,
        val underestimates: List<String> = emptyList(),
        /** e.g. {"report": "2 days"} — feeds the "Tight for you" risk chip. */
        @SerialName("self_reported_durations") val selfReportedDurations: Map<String, String> = emptyMap(),
        @SerialName("slip_behaviour") val slipBehaviour: String? = null,
    )

    @Serializable data class Rhythm(
        @SerialName("peak_hours") val peakHours: String? = null,
        @SerialName("quiet_hours") val quietHours: String? = null,      // "22:00-08:00"
        @SerialName("reminder_tolerance") val reminderTolerance: Int? = null,
        @SerialName("weekend_policy") val weekendPolicy: String? = null,
    )

    @Serializable data class AssistantPrefs(
        val tone: String? = null,
        @SerialName("draft_formality") val draftFormality: String? = null,
        @SerialName("hard_limits") val hardLimits: List<String> = emptyList(),
    )
}

@Serializable
data class Trait(
    val field: String,
    val value: String,
    val evidence: String,
    val source: String = "interview",
    val confirmedAt: Long = 0,
)

/** INTEGRATIONSv2.md §8.2. At the hackathon: fictional people only (assets/twin/people.demo.json). */
@Serializable
data class Person(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val relation: String = "",
    val register: String = "neutral",                                 // formal | neutral | casual_hinglish | family
    val channel: String = "whatsapp",                                 // whatsapp | email | sms
    @SerialName("trust_tier") val trustTier: String = "WORK",         // WORK | PERSONAL
)
