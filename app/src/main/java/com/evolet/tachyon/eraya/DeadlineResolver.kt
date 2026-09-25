package com.evolet.tachyon.eraya

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Sanity-checks the model's deadline_iso against today. Small models get date arithmetic wrong,
 * so when deadline_text names a day we understand ("Friday", "kal", "today itself"),
 * the rule-based date wins. The model's date is used only when the rules find nothing.
 */
class DeadlineResolver {

    private data class Deadline(val date: LocalDate, val time: LocalTime?) {
        fun iso(): String = if (time == null) date.toString() else LocalDateTime.of(date, time).format(ISO_MINUTE)
    }

    fun resolve(deadlineText: String, deadlineIso: String?, today: LocalDate): String? {
        val text = deadlineText.trim().lowercase()
        if (text.isEmpty() || text == "none" || text == "null") return null

        val ruled = fromText(text, today)
        val model = parseIso(deadlineIso)?.takeIf { it.date in today..today.plusDays(366) }

        return when {
            ruled != null && model != null && ruled.date == model.date -> (if (ruled.time != null) ruled else model).iso()
            ruled != null -> ruled.iso()
            else -> model?.iso()
        }
    }

    private fun fromText(text: String, today: LocalDate): Deadline? {
        val date = when {
            has(text, "today", "aaj", "tonight", "end of day", "eod", "आज") -> today
            has(text, "day after tomorrow", "parso", "परसों") -> today.plusDays(2)
            has(text, "tomorrow", "kal", "कल") -> today.plusDays(1)
            else -> WEEKDAYS.entries.firstOrNull { (_, names) -> names.any { has(text, it) } }
                ?.let { (day, _) -> nextOccurrence(today, day) }
        } ?: return null
        return Deadline(date, timeOf(text))
    }

    private fun timeOf(text: String): LocalTime? {
        Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""").find(text)?.let { m ->
            val h = m.groupValues[1].toInt() % 12 + if (m.groupValues[3] == "pm") 12 else 0
            val min = m.groupValues[2].toIntOrNull() ?: 0
            if (h in 0..23 && min in 0..59) return LocalTime.of(h, min)
        }
        return when {
            has(text, "morning", "subah", "सुबह") -> LocalTime.of(9, 0)
            has(text, "noon", "dopahar", "दोपहर") -> LocalTime.of(12, 0)
            has(text, "afternoon") -> LocalTime.of(14, 0)
            has(text, "evening", "shaam", "sham", "शाम", "end of day", "eod") -> LocalTime.of(18, 0)
            has(text, "night", "tonight", "raat", "रात") -> LocalTime.of(21, 0)
            else -> null
        }
    }

    /** "by Friday" said on a Friday means next Friday. */
    private fun nextOccurrence(today: LocalDate, day: DayOfWeek): LocalDate =
        today.with(TemporalAdjusters.next(day))

    private fun parseIso(iso: String?): Deadline? {
        val s = iso?.trim().orEmpty()
        if (s.isEmpty() || s == "null") return null
        runCatching { return LocalDateTime.parse(s.take(16), ISO_MINUTE).let { Deadline(it.toLocalDate(), it.toLocalTime()) } }
        runCatching { return Deadline(LocalDate.parse(s.take(10)), null) }
        return null
    }

    /** Whole-word match for Latin words; plain substring for Devanagari (no \b there). */
    private fun has(text: String, vararg words: String) = words.any { w ->
        if (w.first().code < 128) Regex("""\b${Regex.escape(w)}\b""").containsMatchIn(text) else text.contains(w)
    }

    private companion object {
        val ISO_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val WEEKDAYS = mapOf(
            DayOfWeek.MONDAY to listOf("monday", "somvar", "somvaar", "सोमवार"),
            DayOfWeek.TUESDAY to listOf("tuesday", "mangalvar", "mangalvaar", "मंगलवार"),
            DayOfWeek.WEDNESDAY to listOf("wednesday", "budhvar", "budhvaar", "बुधवार"),
            DayOfWeek.THURSDAY to listOf("thursday", "guruvar", "guruvaar", "brihaspativar", "गुरुवार", "बृहस्पतिवार"),
            DayOfWeek.FRIDAY to listOf("friday", "shukravar", "shukravaar", "शुक्रवार"),
            DayOfWeek.SATURDAY to listOf("saturday", "shanivar", "shanivaar", "शनिवार"),
            DayOfWeek.SUNDAY to listOf("sunday", "ravivar", "ravivaar", "itvaar", "रविवार"),
        )
    }
}
