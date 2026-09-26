package com.evolet.tachyon.reminders

import com.evolet.tachyon.twin.Persona
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * F19 reminder timing (INTEGRATIONSv2.md §12): T-24 h and T-2 h before a deadline, respecting the
 * owner's CONFIRMED rhythm — quiet hours (shift to when they end, if still before the deadline),
 * weekend policy (no work reminders on weekends → next Monday 09:00, if still in time) and
 * reminder tolerance (at most N). Pure → unit-tested.
 */
object ReminderPlanner {

    val OFFSETS_HOURS = listOf(24L, 2L)

    /** Date-only deadlines are treated as due at 18:00 (same convention as RiskScorer). */
    fun dueOf(iso: String): LocalDateTime? = runCatching {
        if (iso.length > 10) LocalDateTime.parse(iso) else LocalDate.parse(iso).atTime(18, 0)
    }.getOrNull()

    fun plan(deadlineIso: String?, now: LocalDateTime, persona: Persona): List<LocalDateTime> {
        val due = deadlineIso?.let(::dueOf) ?: return emptyList()
        if (!due.isAfter(now)) return emptyList()
        val quiet = persona.rhythm.quietHours?.let(::parseQuiet)
        val noWeekend = persona.rhythm.weekendPolicy?.lowercase()?.let { "no" in it || "don't" in it || "dont" in it || "never" in it } == true
        val max = persona.rhythm.reminderTolerance?.coerceAtLeast(1) ?: OFFSETS_HOURS.size

        return OFFSETS_HOURS.asSequence()
            .map { due.minusHours(it) }
            .map { t -> if (t.isBefore(now)) now.plusMinutes(1) else t }
            .map { t -> quiet?.let { q -> shiftOutOfQuiet(t, q) } ?: t }
            .map { t -> if (noWeekend && t.dayOfWeek in WEEKEND) nextMonday9(t) else t }
            .filter { it.isBefore(due) && it.isAfter(now) }
            .distinct()
            .sorted()
            .take(max)
            .toList()
    }

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    private fun nextMonday9(t: LocalDateTime): LocalDateTime {
        var d = t.toLocalDate()
        while (d.dayOfWeek != DayOfWeek.MONDAY) d = d.plusDays(1)
        return d.atTime(9, 0)
    }

    /** Quiet window [start, end), possibly crossing midnight. */
    data class Quiet(val start: LocalTime, val end: LocalTime) {
        fun contains(t: LocalTime) = if (start <= end) t >= start && t < end else t >= start || t < end
    }

    fun shiftOutOfQuiet(t: LocalDateTime, q: Quiet): LocalDateTime {
        if (!q.contains(t.toLocalTime())) return t
        val endToday = t.toLocalDate().atTime(q.end)
        return if (endToday.isAfter(t)) endToday else endToday.plusDays(1)
    }

    /** "22:00-08:00", "10 pm to 8 am", "after 10pm until 7am" → Quiet; null if not understood. */
    fun parseQuiet(text: String): Quiet? {
        val times = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE).findAll(text.lowercase())
            .mapNotNull { m ->
                var h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toIntOrNull() ?: 0
                when (m.groupValues[3]) {
                    "pm" -> if (h < 12) h += 12
                    "am" -> if (h == 12) h = 0
                }
                if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
            }.toList()
        return if (times.size >= 2) Quiet(times[0], times[1]) else null
    }
}
