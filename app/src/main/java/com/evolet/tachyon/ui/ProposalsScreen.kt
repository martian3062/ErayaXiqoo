package com.evolet.tachyon.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.data.Status
import com.evolet.tachyon.session.SessionUi
import com.evolet.tachyon.twin.Person
import com.evolet.tachyon.ui.components.ConfidenceRing
import com.evolet.tachyon.ui.components.LatencyBadge
import com.evolet.tachyon.ui.components.PeopleRow
import com.evolet.tachyon.ui.components.RiskChip
import com.evolet.tachyon.ui.components.TachyonIcons

/** F4, F5 (+F13 people and risk): nothing is saved as a task until ✓ is tapped. */
@Composable
fun ProposalsScreen(
    ui: SessionUi,
    items: List<Commitment>,
    people: Map<String, Person>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onUndo: (String) -> Unit,
    onDone: () -> Unit,
) {
    val decided = items.count { it.status != Status.PROPOSED }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                if (items.isEmpty()) "No commitments found" else "${items.size} proposed · $decided decided",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Agents propose. You commit — nothing is saved until you tap.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            ProposalCard(
                c, people,
                onAccept = { onAccept(c.id) }, onReject = { onReject(c.id) }, onUndo = { onUndo(c.id) },
                modifier = Modifier.animateItem(),
            )
        }
        item {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProposalCard(
    c: Commitment,
    people: Map<String, Person>,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val tint by animateColorAsState(
        when (c.status) {
            Status.ACCEPTED -> MaterialTheme.colorScheme.primaryContainer
            Status.REJECTED -> MaterialTheme.colorScheme.surfaceContainerLowest
            Status.PROPOSED -> MaterialTheme.colorScheme.surfaceContainerHigh
        }, label = "tint",
    )
    val toPerson = c.toPersonId?.let { people[it] }
    val ownerPerson = c.ownerPersonId?.let { people[it] }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = tint)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    c.task, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    color = if (c.status == Status.REJECTED) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
                ConfidenceRing(c.confidence)
            }
            PeopleRow(
                owner = ownerPerson?.name ?: c.owner,
                ownerIsYou = c.ownerIsUser,
                to = toPerson?.name ?: c.toWhom,
                relation = toPerson?.relation,
            )
            Text("⏰ ${deadlineLabel(c)}", style = MaterialTheme.typography.bodyMedium)
            if (c.riskNote != null) RiskChip(c.riskNote)
            EvidenceQuote(c.evidence)
            AnimatedContent(c.status, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() }, label = "decision") { status ->
                when (status) {
                    Status.PROPOSED -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        OutlinedButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            onReject()
                        }) {
                            Icon(TachyonIcons.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Reject")
                        }
                        Button(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onAccept()
                        }) {
                            Icon(TachyonIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Accept")
                        }
                    }
                    Status.ACCEPTED -> Decided("✓ Committed", MaterialTheme.colorScheme.primary, onUndo)
                    Status.REJECTED -> Decided("✕ Rejected", MaterialTheme.colorScheme.error, onUndo)
                }
            }
        }
    }
}

@Composable
private fun EvidenceQuote(evidence: String) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
        Text(
            "“$evidence”",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun Decided(label: String, color: Color, onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onUndo) {
            Icon(TachyonIcons.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(" Undo")
        }
    }
}
