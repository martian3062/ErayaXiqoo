package com.evolet.tachyon.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.evolet.tachyon.MainActivity
import com.evolet.tachyon.R
import com.evolet.tachyon.agents.AgentBus
import com.evolet.tachyon.agents.AgentEvent
import com.evolet.tachyon.agents.Decision
import com.evolet.tachyon.container
import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.data.Status
import com.evolet.tachyon.twin.Persona
import com.evolet.tachyon.twin.Person
import com.evolet.tachyon.voice.SystemVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Reminder wording in the owner's confirmed assistant tone (short, deterministic, offline). */
object ReminderText {
    private val WHEN = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.ENGLISH)

    fun build(c: Commitment, to: Person?, persona: Persona, now: LocalDateTime): String {
        val due = c.deadlineIso?.let(ReminderPlanner::dueOf)
        val whom = to?.name ?: c.toWhom
        val left = due?.let { Duration.between(now, it) }
        val whenText = when {
            due == null -> ""
            left!!.toHours() < 3 -> "in ${left.toMinutes().coerceAtLeast(1)} min"
            left.toHours() < 36 -> "by ${due.format(WHEN)}"
            else -> "on ${due.format(WHEN)}"
        }
        val promised = if (c.ownerIsUser || whom != null) "You promised${whom?.let { " $it" }.orEmpty()}" else "Reminder"
        val blunt = persona.assistant.tone?.lowercase()?.let { "blunt" in it || "direct" in it } == true
        return if (blunt) "$promised: ${c.task.lowercase()} — due $whenText. Do it now or tell them."
        else "$promised: ${c.task.lowercase()}, due $whenText.".replace(", due .", ".")
    }
}

/** Fires one reminder: notification + optional Tier C spoken line (labelled AI voice). */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.container
        val c = app.db.commitmentDao().get(inputData.getString(KEY_ID) ?: return Result.success()) ?: return Result.success()
        if (c.status != Status.ACCEPTED) return Result.success()
        val people = app.personaStore.people.value.associateBy { it.id }
        val text = ReminderText.build(c, c.toPersonId?.let { people[it] }, app.personaStore.persona.value, LocalDateTime.now())
        val speak = app.settings.state.value.speakReminders
        notify(applicationContext, c.id, text, spoken = speak)
        if (speak) speakTierC(text)
        return Result.success()
    }

    private suspend fun speakTierC(text: String) {
        val voice = SystemVoice(applicationContext)
        repeat(30) { if (!voice.available) delay(100) }       // TTS engine binds asynchronously
        if (voice.available) {
            voice.speak(text)
            repeat(200) { if (voice.speaking) delay(100) }    // up to 20 s
        }
        voice.shutdown()
    }

    companion object {
        const val KEY_ID = "commitmentId"
        private const val CHANNEL = "reminders"

        fun notify(context: Context, id: String, text: String, spoken: Boolean) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Commitment reminders", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_mic)
                .setContentTitle(if (spoken) "Tachyon · 🔊 AI voice" else "Tachyon reminder")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), n) } // needs POST_NOTIFICATIONS
        }
    }
}

/**
 * Schedules reminders when the owner taps ✓, cancels them on ✗ / Undo. Listens on the agent bus,
 * so nothing is scheduled without a human decision.
 */
class ReminderScheduler(
    private val context: Context,
    private val bus: AgentBus,
    private val lookup: suspend (String) -> Commitment?,
    private val persona: () -> Persona,
) {
    private val wm get() = WorkManager.getInstance(context)

    fun start(scope: CoroutineScope) {
        scope.launch {
            bus.events.filterIsInstance<AgentEvent.OwnerDecision>().collect { e ->
                when (e.decision) {
                    Decision.ACCEPT -> lookup(e.proposalId)?.let { schedule(it) }
                    Decision.REJECT, Decision.UNDO -> wm.cancelAllWorkByTag(tag(e.proposalId))
                }
            }
        }
    }

    fun schedule(c: Commitment, now: LocalDateTime = LocalDateTime.now()): List<LocalDateTime> {
        wm.cancelAllWorkByTag(tag(c.id))
        val times = ReminderPlanner.plan(c.deadlineIso, now, persona())
        times.forEachIndexed { i, t -> enqueue(c.id, "${tag(c.id)}_$i", Duration.between(now, t).toMillis()) }
        return times
    }

    /** Demo button: fire a reminder for [c] in a few seconds. */
    fun fireSoon(c: Commitment) = enqueue(c.id, "${tag(c.id)}_now", 3_000)

    private fun enqueue(id: String, name: String, delayMs: Long) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_ID to id))
            .addTag(tag(id))
            .build()
        wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req)
    }

    private fun tag(id: String) = "rem_$id"
}
