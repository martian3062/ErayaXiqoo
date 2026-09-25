package com.evolet.tachyon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.data.Commitment

/** Accepted commitments only (F5), each with "Add to calendar" (F6). */
@Composable
fun TasksScreen(items: List<Commitment>, onAddToCalendar: (Commitment) -> Unit) {
    if (items.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("No confirmed commitments yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Things show up here only after you tap ✓ on a proposal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        items(items, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.task, style = MaterialTheme.typography.titleMedium)
                    Text(ownerLine(c), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("⏰ ${deadlineLabel(c)}", style = MaterialTheme.typography.bodyMedium)
                    Text("“${c.evidence}”", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                    FilledTonalButton(onClick = { onAddToCalendar(c) }) { Text("Add to calendar") }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
