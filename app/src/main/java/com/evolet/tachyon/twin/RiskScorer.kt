package com.evolet.tachyon.twin

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * F13 personal deadline risk — deterministic, not LLM (INTEGRATIONSv2.md §5.2).
 * Buckets the task by keyword, looks up how long the owner *said* that kind of task takes
 * (persona.commit.self_reported_durations, from confirmed interview traits), and flags the
 * commitment when the time left is shorter. No persona data → no chip (never invent a habit).
 */
object RiskScorer {

    val BUCKETS = linkedMapOf(
        "report" to listOf("report", "document", "doc", "write-up", "writeup", "summary"),
        "slides" to listOf("slides", "deck", "presentation", "ppt"),
        "send" to listOf("send", "mail", "email", "share", "bhej"),
        "review" to listOf("review", "check", "feedback"),
        "book" to listOf("book", "reserve", "schedule"),
        "call" to listOf("call", "phone", "ring"),
    )

    fun bucket(task: String): String? {
        val t = task.lowercase()
        return BUCKETS.entries.firstOrNull { (_, words) -> words.any { Regex("""\b${Regex.escape(it)}""").containsMatchIn(t) } }?.key
    }

    /** "2 days", "3 hours", "half a day", "1 din", "30 min" → Duration; null if unparseable. */
    fun parseDuration(text: String): Duration? {
        val t = text.lowercase().trim()
        if ("half a day" in t || "half day" in t) return Duration.ofHours(12)
        val m = Regex("""(\d+(?:\.\d+)?)\s*(min|minute|minutes|h|hr|hrs|hour|hours|ghante|ghanta|d|day|days|din|week|weeks|hafte|hafta)\b""").find(t)
            ?: return null
        val n = m.groupValues[1].toDouble()
        val minutes = when (m.groupValues[2]) {
            "min", "minute", "minutes" -> n
            "h", "hr", "hrs", "hour", "hours", "ghante", "ghanta" -> n * 60
            "d", "day", "days", "din" -> n * 60 * 24
            else -> n * 60 * 24 * 7
        }
        return Duration.ofMinutes(minutes.toLong())
    }

    /** Returns a one-line reason when the deadline is tighter than the owner's own estimate, else null. */
    fun risk(task: String, deadlineIso: String?, persona: Persona, now: LocalDateTime): String? {
        val iso = deadlineIso ?: return null
        val b = bucket(task) ?: return null
        val said = persona.commit.selfReportedDurations.entries
            .firstOrNull { (k, _) -> bucket(k) == b || k.lowercase() == b } ?: return null
        val need = parseDuration(said.value) ?: return null
        val due = runCatching {
            if (iso.length > 10) LocalDateTime.parse(iso) else LocalDate.parse(iso).atTime(18, 0)
        }.getOrNull() ?: return null
        val left = Duration.between(now, due)
        if (left >= need) return null
        return "Tight for you: you said a ${said.key} takes you ${said.value}; ${humanize(left)} left."
    }

    private fun humanize(d: Duration): String = when {
        d.isNegative -> "already past"
        d.toHours() < 1 -> "${d.toMinutes()} min"
        d.toHours() < 48 -> "${d.toHours()} h"
        else -> "${d.toDays()} days"
    }
}
