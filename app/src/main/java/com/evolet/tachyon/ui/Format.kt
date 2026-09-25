package com.evolet.tachyon.ui

import com.evolet.tachyon.data.Commitment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val DAY_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

/** "Friday evening · Fri 2 Oct, 18:00", or "No deadline". */
fun deadlineLabel(c: Commitment): String {
    val text = c.deadlineText.takeUnless { it.equals("none", ignoreCase = true) }
    val iso = c.deadlineIso?.let { prettyIso(it) }
    return listOfNotNull(text, iso).joinToString(" · ").ifEmpty { "No deadline" }
}

private fun prettyIso(iso: String): String? = runCatching {
    if (iso.length > 10) LocalDateTime.parse(iso).format(DAY_TIME) else LocalDate.parse(iso).format(DAY)
}.getOrNull()

fun ownerLine(c: Commitment): String = c.owner + (c.toWhom?.let { " → $it" } ?: "")

fun elapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
