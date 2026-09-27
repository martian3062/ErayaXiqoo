package com.evolet.tachyon.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.twin.InterviewController
import com.evolet.tachyon.twin.InterviewUi
import com.evolet.tachyon.twin.ReviewItem
import com.evolet.tachyon.twin.ReviewStatus
import com.evolet.tachyon.twin.Stage
import com.evolet.tachyon.ui.components.LiveWaveform
import com.evolet.tachyon.ui.components.PulseMicButton

/** F17: spoken behaviour interview + Trait Review (INTEGRATIONSv2.md §7). */
@Composable
fun InterviewScreen(controller: InterviewController, onClose: () -> Unit) {
    val ui by controller.ui.collectAsState()
    val level by controller.level.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (ui.stage == Stage.REVIEW) "Review your traits" else "Get to know you", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (ui.stage == Stage.REVIEW) controller.finish() else controller.cancel()
                onClose()
            }) { Text(if (ui.stage == Stage.REVIEW) "Done" else if (ui.stage == Stage.PAUSED) "Close" else "Stop") }
        }
        when (ui.stage) {
            Stage.PAUSED -> ResumeDraft(ui, controller, onClose)
            Stage.REVIEW -> TraitReview(ui, controller)
            Stage.SUMMARISING -> Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Turning your answers into traits, on this phone…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Stage.ASKING, Stage.RECORDING, Stage.TRANSCRIBING -> Asking(ui, level, controller)
            else -> Unit
        }
    }
}

@Composable
private fun ResumeDraft(ui: InterviewUi, controller: InterviewController, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Your private interview draft is saved", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Continue from question ${ui.index + 1} of ${ui.questions.size}. ${ui.answered} answers and ${ui.review.size} proposed traits are stored only on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = controller::resume, modifier = Modifier.fillMaxWidth()) { Text("Resume interview") }
        OutlinedButton(onClick = controller::startOver, modifier = Modifier.fillMaxWidth()) { Text("Start over") }
        TextButton(onClick = onClose) { Text("Not now") }
    }
}

@Composable
private fun Asking(ui: InterviewUi, level: Float, c: InterviewController) {
    val q = ui.current ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(progress = { (ui.index + 1f) / ui.questions.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${ui.sections[q.section] ?: "Section ${q.section}"} · ${ui.index + 1} of ${ui.questions.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (ui.voiceName != null) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                    Text("🔊 AI voice", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
        AnimatedContent(ui.prompt, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "q") { text ->
            Text(text.orEmpty(), style = MaterialTheme.typography.headlineSmall)
        }
        if (ui.followUp != null) Text("Follow-up (asked once, because the answer was short)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val recording = ui.stage == Stage.RECORDING
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PulseMicButton(recording = recording, enabled = ui.stage == Stage.ASKING || recording, level = level, onClick = { if (recording) c.stopRecordingNow() else c.record() }, size = 88.dp)
            LiveWaveform(level, recording)
            Text(
                when (ui.stage) {
                    Stage.RECORDING -> "Listening… stops after a short pause"
                    Stage.TRANSCRIBING -> "Transcribing…"
                    else -> "Tap to answer out loud, or type below"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedTextField(
            value = ui.answerText, onValueChange = c::onAnswerText,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            label = { Text("Your answer (edit freely)") },
            enabled = ui.stage == Stage.ASKING,
        )
        if (ui.error != null) Text(ui.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            OutlinedButton(onClick = c::skip, enabled = ui.stage == Stage.ASKING) { Text("Skip") }
            Button(onClick = c::next, enabled = ui.stage == Stage.ASKING) { Text("Next") }
        }
        Text("Answers stay on this phone. Only traits you confirm are saved.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TraitReview(ui: InterviewUi, c: InterviewController) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                "${ui.review.size} proposed from ${ui.answered} answers" + if (ui.droppedTraits > 0) " · ${ui.droppedTraits} dropped (no exact quote, low confidence, or a blocked topic)" else "",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ui.error != null) Text(ui.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        itemsIndexed(ui.review) { i, item -> TraitCard(item, onConfirm = { edited -> c.confirm(i, edited) }, onReject = { c.reject(i) }) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TraitCard(item: ReviewItem, onConfirm: (String?) -> Unit, onReject: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(item.proposal.value) }
    val bg = when (item.status) {
        ReviewStatus.CONFIRMED -> MaterialTheme.colorScheme.primaryContainer
        ReviewStatus.REJECTED -> MaterialTheme.colorScheme.surfaceContainerLowest
        ReviewStatus.PENDING -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.proposal.field.replace('_', ' '), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (editing && item.status == ReviewStatus.PENDING) {
                OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth())
            } else {
                Text(item.finalValue ?: item.proposal.value, style = MaterialTheme.typography.titleMedium)
            }
            Text("“${item.proposal.evidence}”", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
            when (item.status) {
                ReviewStatus.PENDING -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onReject) { Text("✗ Reject") }
                    TextButton(onClick = { editing = !editing }) { Text("✎ Edit") }
                    Button(onClick = { onConfirm(if (editing) value else null) }) { Text("✓ Confirm") }
                }
                ReviewStatus.CONFIRMED -> Text("✓ Saved to your profile", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                ReviewStatus.REJECTED -> Text("✗ Not saved", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
