package com.evolet.tachyon.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.EngineHealth
import com.evolet.tachyon.EngineState
import com.evolet.tachyon.EnginesStatus
import com.evolet.tachyon.session.Phase
import com.evolet.tachyon.session.SessionUi
import com.evolet.tachyon.ui.components.LatencyBadge
import com.evolet.tachyon.ui.components.LiveWaveform
import com.evolet.tachyon.ui.components.PipelineStepper
import com.evolet.tachyon.ui.components.PulseMicButton
import kotlinx.coroutines.delay

/** F1, F2: pulse mic with live waveform, pipeline stepper, transcript arriving in 30 s chunks. */
@Composable
fun RecordScreen(
    ui: SessionUi,
    level: Float,
    engines: EnginesStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetryExtraction: () -> Unit,
    onRetryEngines: () -> Unit,
    onOpenTermux: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.phase) {
        while (ui.phase == Phase.RECORDING) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val recording = ui.phase == Phase.RECORDING

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EngineStrip(engines, onRetryEngines, onOpenTermux)
        Spacer(Modifier.height(8.dp))
        PipelineStepper(ui.phase)

        PulseMicButton(recording = recording, enabled = recording || !ui.busy, level = level, onClick = if (recording) onStop else onStart)
        LiveWaveform(level, recording, Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(8.dp))

        AnimatedContent(statusLine(ui, now), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") { line ->
            Text(line, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        AnimatedVisibility(ui.phase == Phase.TRANSCRIBING || ui.phase == Phase.EXTRACTING || ui.pendingChunks > 0) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }

        if (ui.phase == Phase.ERROR) {
            Spacer(Modifier.height(8.dp))
            Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            if (ui.canRetry) Button(onClick = onRetryExtraction) { Text("Retry extraction") }
        } else if (ui.error != null) {
            Text(ui.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(8.dp))
        LatencyBadge(ui.asrMs, ui.llmMs, ui.tokensPerSec, listOf(ui.asrEngine, ui.llmEngine))
        Spacer(Modifier.height(8.dp))
        TranscriptCard(ui.transcript, Modifier.weight(1f).fillMaxWidth())
        Spacer(Modifier.height(12.dp))
    }
}

private fun statusLine(ui: SessionUi, now: Long): String = when (ui.phase) {
    Phase.IDLE -> "Tap to capture a conversation"
    Phase.RECORDING -> if (ui.fromSample) "Feeding sample recording…" else "Listening · ${elapsed(now - ui.startedAt)}"
    Phase.TRANSCRIBING -> "Transcribing the last chunk…"
    Phase.EXTRACTING -> "Agents are finding commitments…"
    Phase.REVIEW -> "${ui.proposals} proposals ready"
    Phase.ERROR -> "Something went wrong"
}

/** Compact when healthy, expands with fixes when an engine is down. */
@Composable
private fun EngineStrip(engines: EnginesStatus, onRetry: () -> Unit, onOpenTermux: () -> Unit) {
    val failed = engines.asr.state == EngineState.FAILED || engines.llm.state == EngineState.FAILED
    Surface(
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            EngineLine("ASR", engines.asr)
            EngineLine("LLM", engines.llm)
            AnimatedVisibility(failed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenTermux) { Text("Open Termux") }
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun EngineLine(label: String, h: EngineHealth) {
    val (mark, color) = when (h.state) {
        EngineState.READY -> "●" to MaterialTheme.colorScheme.primary
        EngineState.LOADING, EngineState.IDLE -> "○" to MaterialTheme.colorScheme.onSurfaceVariant
        EngineState.FAILED -> "✕" to MaterialTheme.colorScheme.error
    }
    Text("$mark $label  ${h.name}", color = color, style = MaterialTheme.typography.bodyMedium)
    if (h.error != null) Text(h.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
}

@Composable
private fun TranscriptCard(chunks: List<String>, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(chunks.size) { if (chunks.isNotEmpty()) listState.animateScrollToItem(chunks.size - 1) }
    ElevatedCard(modifier) {
        if (chunks.isEmpty()) {
            Text(
                "Live transcript appears here, 30 s at a time.\nAudio never leaves this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Spacer(Modifier.height(6.dp)) }
                itemsIndexed(chunks, key = { i, _ -> i }) { i, text ->
                    var shown by remember { mutableLongStateOf(0L) }
                    LaunchedEffect(Unit) { shown = 1L }
                    AnimatedVisibility(shown == 1L, enter = fadeIn() + slideInVertically { it / 3 }) {
                        Column {
                            Text("${elapsed(i * 30_000L)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                item { Spacer(Modifier.height(6.dp)) }
            }
        }
    }
}
