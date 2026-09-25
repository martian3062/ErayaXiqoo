package com.evolet.tachyon.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.evolet.tachyon.data.Commitment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** F6: opens the calendar app pre-filled via ACTION_INSERT. Needs no calendar permission. */
object CalendarBridge {

    fun open(context: Context, c: Commitment): Boolean = try {
        context.startActivity(insertIntent(c))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    fun insertIntent(c: Commitment): Intent {
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, c.task + (c.toWhom?.let { " → $it" } ?: ""))
            .putExtra(
                CalendarContract.Events.DESCRIPTION,
                "Commitment by ${c.owner}.\nEvidence: “${c.evidence}”\n\nConfirmed in Tachyon, on-device.",
            )
        val iso = c.deadlineIso ?: return intent
        val zone = ZoneId.systemDefault()
        if (iso.length > 10) {
            val start = runCatching { LocalDateTime.parse(iso) }.getOrNull() ?: return intent
            val begin = start.atZone(zone).toInstant().toEpochMilli()
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 30 * 60 * 1000)
        } else {
            val day = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return intent
            intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, day.atStartOfDay(zone).toInstant().toEpochMilli())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        }
        return intent
    }
}
