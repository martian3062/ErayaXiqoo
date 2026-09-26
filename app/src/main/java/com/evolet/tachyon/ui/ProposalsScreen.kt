package com.evolet.tachyon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.data.Status
import com.evolet.tachyon.session.SessionUi
import com.evolet.tachyon.ui.components.LatencyBadge
import com.evolet.tachyon.ui.components.TachyonIcons
import kotlin.math.roundToInt

/** F4, F5: each proposal with its evidence quote. Nothing is saved as a task until ✓ is tapped. */
@Composable
fun ProposalsScreen(
    ui: SessionUi,
    items: List<Commitment>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onUndo: (String) -> Unit,
    onDone: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                if (items.isEmpty()) "No commitments found" else "${items.size} proposed — nothing is saved until you tap",
                style = MaterialTheme.typography.titleMedium,
            )
            if (ui.dropped > 0) {
                Text(
                    "${ui.dropped} dropped by the checks (evidence, hedge words, verifier)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LatencyBadge(ui.asrMs, ui.llmMs, ui.tokensPerSec, listOf(ui.asrEngine, ui.llmEngine))
        }
        items(items, key = { it.id }) { c ->
            ProposalCard(c, onAccept = { onAccept(c.id) }, onReject = { onReject(c.id) }, onUndo = { onUndo(c.id) })
        }
        item {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProposalCard(c: Commitment, onAccept: () -> Unit, onReject: () -> Unit, onUndo: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.task, style = MaterialTheme.typography.titleMedium)
            Text(ownerLine(c), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏰ ${deadlineLabel(c)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${(c.confidence * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
            EvidenceQuote(c.evidence)
            when (c.status) {
                Status.PROPOSED -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    OutlinedButton(onClick = onReject) {
                        Icon(TachyonIcons.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Reject")
                    }
                    Button(onClick = onAccept) {
                        Icon(TachyonIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Accept")
                    }
                }
                Status.ACCEPTED -> Decided("✓ Accepted", MaterialTheme.colorScheme.primary, onUndo)
                Status.REJECTED -> Decided("✕ Rejected", MaterialTheme.colorScheme.error, onUndo)
            }
        }
    }
}

@Composable
private fun EvidenceQuote(evidence: String) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        Text(
            "“$evidence”",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun Decided(label: String, color: androidx.compose.ui.graphics.Color, onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onUndo) { Text("Undo") }
    }
}
