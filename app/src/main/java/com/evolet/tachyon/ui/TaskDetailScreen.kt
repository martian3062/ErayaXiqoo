package com.evolet.tachyon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.reminders.ReminderPlanner
import com.evolet.tachyon.twin.Person
import com.evolet.tachyon.twin.Persona
import com.evolet.tachyon.ui.components.PeopleRow
import com.evolet.tachyon.ui.components.GlassCard
import com.evolet.tachyon.ui.components.RiskChip
import com.evolet.tachyon.ui.components.TachyonIcons
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val reminderTime = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

@Composable
fun TaskDetailScreen(
    commitment: Commitment?,
    people: Map<String, Person>,
    persona: Persona,
    onAddToCalendar: (Commitment) -> Unit,
    onDraft: (Commitment) -> Unit,
) {
    if (commitment == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Commitment not found", style = MaterialTheme.typography.headlineSmall)
        }
        return
    }
    val to = commitment.toPersonId?.let { people[it] }
    val reminderPlan = ReminderPlanner.plan(commitment.deadlineIso, LocalDateTime.now(), persona)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(commitment.task, style = MaterialTheme.typography.headlineSmall)
        PeopleRow(
            owner = commitment.ownerPersonId?.let { people[it]?.name } ?: commitment.owner,
            ownerIsYou = commitment.ownerIsUser,
            to = to?.name ?: commitment.toWhom,
            relation = to?.relation,
        )
        Text("Due ${deadlineLabel(commitment)}", style = MaterialTheme.typography.titleMedium)
        if (commitment.riskNote != null) RiskChip(commitment.riskNote)

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Evidence", style = MaterialTheme.typography.titleMedium)
                Text("“${commitment.evidence}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                Text("Confidence ${(commitment.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Reminders", style = MaterialTheme.typography.titleMedium)
                when {
                    commitment.deadlineIso == null -> Text("No reminders: this commitment has no deadline.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    reminderPlan.isEmpty() -> Text("No upcoming reminder windows remain.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> reminderPlan.forEach { Text("• ${it.format(reminderTime)}", style = MaterialTheme.typography.bodyMedium) }
                }
                Text("ERAYA schedules at 24 h and 2 h before the deadline, adjusted for your quiet hours.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onAddToCalendar(commitment) }, modifier = Modifier.weight(1f)) {
                Icon(TachyonIcons.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Calendar")
            }
            Button(onClick = { onDraft(commitment) }, modifier = Modifier.weight(1f)) { Text("Draft follow-up") }
        }
        Spacer(Modifier.height(16.dp))
    }
}
