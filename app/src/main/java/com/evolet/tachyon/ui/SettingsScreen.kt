package com.evolet.tachyon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.EngineHealth
import com.evolet.tachyon.EngineState
import com.evolet.tachyon.EnginesStatus
import com.evolet.tachyon.data.LANG_AUTO
import com.evolet.tachyon.data.SettingsState
import com.evolet.tachyon.data.Tier
import com.evolet.tachyon.ui.components.NavigationRow
import com.evolet.tachyon.ui.components.TachyonIcons

@Composable
fun SettingsHomeScreen(
    settings: SettingsState,
    onEngines: () -> Unit,
    onLanguage: () -> Unit,
    onReminders: () -> Unit,
    onDemoTools: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NavigationRow("Engines", "${settings.asrTier.label()} speech · ${settings.llmTier.label()} language model", TachyonIcons.Sliders, onEngines)
        NavigationRow("Language", languageLabel(settings.language), TachyonIcons.Person, onLanguage)
        NavigationRow("Reminders", if (settings.speakReminders) "Notifications + spoken AI voice" else "Notifications only", TachyonIcons.Event, onReminders)
        NavigationRow("Demo tools", "Sample recording, Termux and battery setup", TachyonIcons.Mic, onDemoTools)
        NavigationRow("About", "ERAYA disclosure and on-device privacy", TachyonIcons.Settings, onAbout)
    }
}

@Composable
fun EnginesSettingsScreen(
    settings: SettingsState,
    engines: EnginesStatus,
    onChange: ((SettingsState) -> SettingsState) -> Unit,
    modelsDir: String,
    modelFiles: List<String>,
    onOpenTermux: () -> Unit,
) {
    SettingsColumn {
        Text("Speech recognition", style = MaterialTheme.typography.titleMedium)
        EngineHealthCard("ASR", engines.asr)
        Choice("NPU · WhisperKit", settings.asrTier == Tier.NPU) { onChange { it.copy(asrTier = Tier.NPU) } }
        Choice("CPU · whisper.cpp on 127.0.0.1:8082", settings.asrTier == Tier.FALLBACK) { onChange { it.copy(asrTier = Tier.FALLBACK) } }
        Text("Language model", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        EngineHealthCard("LLM", engines.llm)
        Choice("NPU · GenieX", settings.llmTier == Tier.NPU) { onChange { it.copy(llmTier = Tier.NPU) } }
        Choice("CPU · llama.cpp on 127.0.0.1:8081", settings.llmTier == Tier.FALLBACK) { onChange { it.copy(llmTier = Tier.FALLBACK) } }
        OutlinedButton(onClick = onOpenTermux, modifier = Modifier.fillMaxWidth()) { Text("Open Termux") }
        Hint("Models: $modelsDir")
        if (modelFiles.isEmpty()) Hint("No model files found") else modelFiles.forEach { Hint("• $it") }
    }
}

@Composable
fun LanguageSettingsScreen(settings: SettingsState, onChange: ((SettingsState) -> SettingsState) -> Unit) {
    SettingsColumn {
        Text("Transcript language", style = MaterialTheme.typography.headlineSmall)
        Text("This is a hint for local speech recognition. Evidence always stays in the language that was spoken.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(LANG_AUTO to "Auto-detect", "en" to "English", "hi" to "Hindi").forEach { (code, label) ->
            Choice(label, settings.language == code) { onChange { it.copy(language = code) } }
        }
    }
}

@Composable
fun ReminderSettingsScreen(
    settings: SettingsState,
    onChange: ((SettingsState) -> SettingsState) -> Unit,
    onTestReminder: () -> Unit,
) {
    SettingsColumn {
        Text("Deadline reminders", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Speak reminders aloud", style = MaterialTheme.typography.titleMedium)
                Hint("Uses an offline Android system voice and is always labelled AI voice.")
            }
            Switch(checked = settings.speakReminders, onCheckedChange = { enabled -> onChange { it.copy(speakReminders = enabled) } })
        }
        Button(onClick = onTestReminder, modifier = Modifier.fillMaxWidth()) { Text("Test latest accepted task") }
        Hint("Accepted tasks are scheduled at 24 h and 2 h before their deadline, adjusted for confirmed quiet hours, weekend policy and reminder tolerance.")
    }
}

@Composable
fun DemoToolsScreen(
    sampleWavPath: String,
    promptsDir: String,
    onUseSample: () -> Unit,
    onOpenTermux: () -> Unit,
    onBatterySettings: () -> Unit,
) {
    SettingsColumn {
        Text("Offline fallback", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onUseSample, modifier = Modifier.fillMaxWidth()) { Text("Use sample recording") }
        Hint("Reads $sampleWavPath, falling back to the bundled asset.")
        OutlinedButton(onClick = onOpenTermux, modifier = Modifier.fillMaxWidth()) { Text("Open Termux · restart servers") }
        OutlinedButton(onClick = onBatterySettings, modifier = Modifier.fillMaxWidth()) { Text("Allow background activity") }
        Hint("OriginOS may stop background engines. Set Tachyon and Termux to no restrictions.")
        Hint("Prompt override: $promptsDir/extract_system.txt and schema.json")
    }
}

@Composable
fun AboutScreen() {
    SettingsColumn {
        Text("Tachyon", style = MaterialTheme.typography.headlineMedium)
        Text("Built on ERAYA", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("Agents propose. Humans commit.", style = MaterialTheme.typography.headlineSmall)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Privacy boundary", style = MaterialTheme.typography.titleMedium)
                Text("Audio, transcripts, commitments and twin data stay on this phone. The app has no cloud API, analytics or backup.")
            }
        }
        Text("ERAYA's propose-then-confirm pattern and Self twin work are pre-existing. Tachyon's on-device Android implementation was built for the iQOO Hackathon; the pre-event scaffold is tagged in Git.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun EngineHealthCard(label: String, health: EngineHealth) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("$label · ${health.state.name.lowercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(health.name, style = MaterialTheme.typography.bodyMedium)
            if (health.error != null) Text(health.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun Tier.label() = if (this == Tier.NPU) "NPU" else "CPU fallback"
private fun languageLabel(code: String) = when (code) { "en" -> "English"; "hi" -> "Hindi"; else -> "Auto-detect" }
