package com.evolet.tachyon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.ui.components.GlassCard
import com.evolet.tachyon.ui.components.GradientAction
import com.evolet.tachyon.ui.components.PageIntro
import com.evolet.tachyon.ui.components.StatusPill
import com.evolet.tachyon.voice.VoiceEnrollmentController
import com.evolet.tachyon.voice.VoiceEnrollmentStage
import java.util.Locale

@Composable
fun VoiceEnrollmentScreen(controller: VoiceEnrollmentController) {
    val ui by controller.ui.collectAsState()
    var ownershipConfirmed by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Replace your voice profile?") },
            text = { Text("This deletes the existing consent clip, recordings, transcripts and voice settings before starting again.") },
            confirmButton = {
                Button(onClick = { confirmRestart = false; controller.restart() }) { Text("Delete and restart") }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Keep current") } },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageIntro(
                eyebrow = "Private voice",
                title = if (ui.profile == null) "Teach ERAYA your voice" else "Your voice profile",
                supporting = "A guided 10-minute recording for your rhythm, accent and future local voice clone.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        when (ui.stage) {
            VoiceEnrollmentStage.INTRO -> {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("Before recording", style = MaterialTheme.typography.titleMedium)
                            Text("• Record only your own voice.\n• Audio stays in ERAYA's internal storage and is excluded from exports and backup.\n• You can delete it from Privacy or this screen.\n• ERAYA will not impersonate you or send speech to other people.")
                            Text("A real local clone is enabled only after a compatible on-device model is installed. Until then, ERAYA uses a clearly labelled personalized system voice.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = ownershipConfirmed, onCheckedChange = { ownershipConfirmed = it })
                        Text("I confirm this is my own voice and I consent to creating a private voice profile on this phone.")
                    }
                }
                item {
                    GradientAction("Begin private voice setup", controller::begin, Modifier.fillMaxWidth(), enabled = ownershipConfirmed)
                }
            }

            VoiceEnrollmentStage.CONSENT,
            VoiceEnrollmentStage.RECORDING_CONSENT,
            VoiceEnrollmentStage.VERIFYING_CONSENT -> {
                item {
                    StepHeader("Step 1 of 2", "Read the consent sentence exactly")
                }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("“${ui.script?.consent.orEmpty()}”", style = MaterialTheme.typography.titleMedium)
                            Text("This exact recording becomes the reference clip. Speak naturally in a quiet room.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                when (ui.stage) {
                    VoiceEnrollmentStage.CONSENT -> item {
                        GradientAction("Record consent", controller::recordConsent, Modifier.fillMaxWidth())
                    }
                    VoiceEnrollmentStage.RECORDING_CONSENT -> item {
                        RecordingPanel(ui.level, ui.liveDurationMs, "Reading consent")
                        OutlinedButton(controller::stopRecording, Modifier.fillMaxWidth()) { Text("Finish sentence") }
                    }
                    VoiceEnrollmentStage.VERIFYING_CONSENT -> item { BusyPanel("Checking the words locally…") }
                    else -> Unit
                }
            }

            VoiceEnrollmentStage.QUESTIONS,
            VoiceEnrollmentStage.RECORDING_ANSWER,
            VoiceEnrollmentStage.TRANSCRIBING_ANSWER -> {
                item {
                    StepHeader("Step 2 of 2", "Talk naturally across different topics")
                    VoiceProgress(ui.totalDurationMs + ui.liveDurationMs, ui.targetDurationMs, ui.completedPrompts)
                }
                item {
                    val prompt = ui.currentPrompt
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill("QUESTION ${ui.completedPrompts + 1}", active = true)
                            Text(prompt?.question.orEmpty(), style = MaterialTheme.typography.titleLarge)
                            Text(prompt?.cue.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Aim for about ${prompt?.targetSeconds ?: 40} seconds. Your natural pauses and mixed-language accent are useful.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                when (ui.stage) {
                    VoiceEnrollmentStage.QUESTIONS -> item {
                        GradientAction("Record this answer", controller::recordAnswer, Modifier.fillMaxWidth())
                    }
                    VoiceEnrollmentStage.RECORDING_ANSWER -> item {
                        RecordingPanel(ui.level, ui.liveDurationMs, "Capturing your natural voice")
                        OutlinedButton(controller::stopRecording, Modifier.fillMaxWidth()) { Text("Finish answer") }
                    }
                    VoiceEnrollmentStage.TRANSCRIBING_ANSWER -> item { BusyPanel("Aligning audio and transcript locally…") }
                    else -> Unit
                }
                if (ui.lastTranscript.isNotBlank()) item {
                    Text("Last transcript: ${ui.lastTranscript}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            VoiceEnrollmentStage.READY_TO_BUILD -> {
                item {
                    StepHeader("Recording complete", "Create the private voice profile")
                    VoiceProgress(ui.totalDurationMs, ui.targetDurationMs, ui.completedPrompts)
                }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Ready on this phone", style = MaterialTheme.typography.titleMedium)
                            Text("${ui.completedPrompts} aligned clips and the verified consent reference are ready. Building stores speaking-rate and pitch defaults and marks the reference for an offline clone engine.")
                        }
                    }
                }
                item { GradientAction("Build my voice profile", controller::buildProfile, Modifier.fillMaxWidth()) }
            }

            VoiceEnrollmentStage.COMPLETE -> {
                val profile = ui.profile
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill("VOICE REFERENCE READY", active = true)
                            Text("Private enrollment complete", style = MaterialTheme.typography.titleLarge)
                            Text("${profile?.clipCount ?: 0} clips · ${duration(profile?.totalDurationMs ?: 0)} · ${profile?.wordsPerMinute?.toInt() ?: 0} words/min")
                            Text("Current output: personalized offline Android voice. True voice cloning remains off until the local ZipVoice model pack is installed and verified.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Button(controller::preview, Modifier.fillMaxWidth()) { Text("Preview current voice") } }
                item { OutlinedButton(onClick = { confirmRestart = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete and record again") } }
            }

            VoiceEnrollmentStage.ERROR -> {
                item { ErrorPanel(ui.error ?: "Voice setup stopped.") }
                item { OutlinedButton(controller::restart, Modifier.fillMaxWidth()) { Text("Restart voice setup") } }
            }
        }

        ui.error?.takeIf { ui.stage != VoiceEnrollmentStage.ERROR }?.let { message ->
            item { ErrorPanel(message) }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun StepHeader(eyebrow: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun VoiceProgress(currentMs: Long, targetMs: Long, clips: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { (currentMs.toFloat() / targetMs.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("${duration(currentMs)} / ${duration(targetMs)} · $clips clips", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RecordingPanel(level: Float, elapsedMs: Long, label: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size((20 + level * 22).dp).background(Color(0xFFE73877).copy(alpha = 0.55f + level * 0.45f), CircleShape),
            )
            Column {
                Text(label, fontWeight = FontWeight.Bold)
                Text(duration(elapsedMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BusyPanel(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(label, Modifier.padding(start = 12.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorPanel(message: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
    }
}

private fun duration(ms: Long): String {
    val seconds = (ms / 1_000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}
