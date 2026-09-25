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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.evolet.tachyon.ui.components.TachyonIcons
import kotlinx.coroutines.delay

/** F1, F2: big mic button, elapsed time, live transcript arriving in 30 s chunks. */
@Composable
fun RecordScreen(
    ui: SessionUi,
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

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EngineStrip(engines, onRetryEngines, onOpenTermux)
        Spacer(Modifier.height(24.dp))

        val recording = ui.phase == Phase.RECORDING
        Button(
            onClick = if (recording) onStop else onStart,
            enabled = recording || !ui.busy,
            shape = CircleShape,
            modifier = Modifier.size(120.dp),
            colors = if (recording) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
        ) {
            Icon(if (recording) TachyonIcons.Stop else TachyonIcons.Mic, contentDescription = if (recording) "Stop" else "Record", modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(statusLine(ui, now), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

        if (ui.phase == Phase.TRANSCRIBING || ui.phase == Phase.EXTRACTING || ui.pendingChunks > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (ui.phase == Phase.ERROR) {
            Spacer(Modifier.height(8.dp))
            Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            if (ui.canRetry) Button(onClick = onRetryExtraction) { Text("Retry extraction") }
        } else if (ui.error != null) {
            Text(ui.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(12.dp))
        LatencyBadge(ui.asrMs, ui.llmMs, ui.tokensPerSec, listOf(ui.asrEngine, ui.llmEngine))
        Spacer(Modifier.height(12.dp))

        TranscriptCard(ui.transcript, Modifier.weight(1f).fillMaxWidth())
        Spacer(Modifier.height(12.dp))
    }
}

private fun statusLine(ui: SessionUi, now: Long): String = when (ui.phase) {
    Phase.IDLE -> "Tap to record a conversation"
    Phase.RECORDING ->
        if (ui.fromSample) "Feeding sample recording…"
        else "Recording · ${elapsed(now - ui.startedAt)} · transcript every 30 s"
    Phase.TRANSCRIBING -> "Transcribing the last chunk…"
    Phase.EXTRACTING -> "Finding commitments on-device…"
    Phase.REVIEW -> "${ui.proposals} proposals ready"
    Phase.ERROR -> "Something went wrong"
}

@Composable
private fun EngineStrip(engines: EnginesStatus, onRetry: () -> Unit, onOpenTermux: () -> Unit) {
    val failed = engines.asr.state == EngineState.FAILED || engines.llm.state == EngineState.FAILED
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            EngineLine("ASR", engines.asr)
            EngineLine("LLM", engines.llm)
            if (failed) {
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
    if (h.error != null) Text(h.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun TranscriptCard(chunks: List<String>, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(chunks.size) { if (chunks.isNotEmpty()) listState.animateScrollToItem(chunks.size - 1) }
    Card(modifier) {
        if (chunks.isEmpty()) {
            Text(
                "Live transcript appears here. Audio never leaves this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Spacer(Modifier.height(8.dp)) }
                items(chunks) { Text(it, style = MaterialTheme.typography.bodyLarge) }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
