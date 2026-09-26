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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.twin.Person
import com.evolet.tachyon.ui.components.PeopleRow
import com.evolet.tachyon.ui.components.RiskChip
import com.evolet.tachyon.ui.components.TachyonIcons
import java.time.LocalDate

/** Accepted commitments only (F5), grouped by when they're due, each with "Add to calendar" (F6). */
@Composable
fun TasksScreen(items: List<Commitment>, people: Map<String, Person>, onAddToCalendar: (Commitment) -> Unit) {
    if (items.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("No confirmed commitments yet", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Things show up here only after you tap ✓ on a proposal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val groups = items.groupBy { dueGroup(it.deadlineIso, LocalDate.now()) }.toSortedMap()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { (group, list) ->
            item(key = "h$group") {
                Text(group.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
            }
            items(list, key = { it.id }) { c -> TaskCard(c, people, onAddToCalendar) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TaskCard(c: Commitment, people: Map<String, Person>, onAddToCalendar: (Commitment) -> Unit) {
    val to = c.toPersonId?.let { people[it] }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.task, style = MaterialTheme.typography.titleMedium)
            PeopleRow(owner = c.ownerPersonId?.let { people[it]?.name } ?: c.owner, ownerIsYou = c.ownerIsUser, to = to?.name ?: c.toWhom, relation = to?.relation)
            Text("⏰ ${deadlineLabel(c)}", style = MaterialTheme.typography.bodyMedium)
            if (c.riskNote != null) RiskChip(c.riskNote)
            Text("“${c.evidence}”", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { onAddToCalendar(c) }) {
                    Icon(TachyonIcons.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Add to calendar")
                }
            }
        }
    }
}

enum class DueGroup(val label: String) { OVERDUE("Overdue"), TODAY("Today"), TOMORROW("Tomorrow"), WEEK("This week"), LATER("Later"), NONE("No deadline") }

fun dueGroup(iso: String?, today: LocalDate): DueGroup {
    val d = iso?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return DueGroup.NONE
    return when {
        d.isBefore(today) -> DueGroup.OVERDUE
        d == today -> DueGroup.TODAY
        d == today.plusDays(1) -> DueGroup.TOMORROW
        d.isBefore(today.plusDays(7)) -> DueGroup.WEEK
        else -> DueGroup.LATER
    }
}
