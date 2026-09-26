package com.evolet.tachyon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.data.Session
import com.evolet.tachyon.data.Status
import com.evolet.tachyon.twin.Person
import com.evolet.tachyon.ui.components.LatencyBadge
import com.evolet.tachyon.ui.components.PeopleRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val sessionTime = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

@Composable
fun SessionsScreen(items: List<Session>, onOpen: (Session) -> Unit) {
    if (items.isEmpty()) {
        EmptySessions()
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Conversation history", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Transcripts and decisions stay on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(items, key = { it.id }) { session ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().clickable { onOpen(session) }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatSessionTime(session.startedAt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(elapsed(session.durationSec * 1_000L), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        session.transcript,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${session.asrEngine}  +  ${session.llmEngine}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun SessionDetailScreen(session: Session?, items: List<Commitment>, people: Map<String, Person>) {
    if (session == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Session not found", style = MaterialTheme.typography.headlineSmall)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(formatSessionTime(session.startedAt), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
            Text("${elapsed(session.durationSec * 1_000L)} conversation", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LatencyBadge(session.asrMs, session.llmMs, null, listOf(session.asrEngine, session.llmEngine))
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Transcript", style = MaterialTheme.typography.titleMedium)
                    Text(session.transcript, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            val accepted = items.count { it.status == Status.ACCEPTED }
            val rejected = items.count { it.status == Status.REJECTED }
            Text("Proposals", style = MaterialTheme.typography.titleLarge)
            Text("${items.size} found · $accepted accepted · $rejected rejected", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(items, key = { it.id }) { commitment ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(commitment.task, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        DecisionLabel(commitment.status)
                    }
                    PeopleRow(
                        owner = commitment.ownerPersonId?.let { people[it]?.name } ?: commitment.owner,
                        ownerIsYou = commitment.ownerIsUser,
                        to = commitment.toPersonId?.let { people[it]?.name } ?: commitment.toWhom,
                        relation = commitment.toPersonId?.let { people[it]?.relation },
                    )
                    Text("“${commitment.evidence}”", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DecisionLabel(status: Status) {
    val (label, color) = when (status) {
        Status.ACCEPTED -> "Accepted" to MaterialTheme.colorScheme.primaryContainer
        Status.REJECTED -> "Rejected" to MaterialTheme.colorScheme.errorContainer
        Status.PROPOSED -> "Not decided" to MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun EmptySessions() {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("No sessions yet", style = MaterialTheme.typography.headlineSmall)
        Text("Your completed conversations will appear here, stored only on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatSessionTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(sessionTime)
