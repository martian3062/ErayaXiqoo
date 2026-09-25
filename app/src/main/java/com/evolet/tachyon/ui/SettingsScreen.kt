package com.evolet.tachyon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.data.LANG_AUTO
import com.evolet.tachyon.data.SettingsState
import com.evolet.tachyon.data.Tier

/** F8: runtime engine switch, plus the demo backups (sample WAV, Termux, battery exemption). */
@Composable
fun SettingsScreen(
    settings: SettingsState,
    onChange: ((SettingsState) -> SettingsState) -> Unit,
    modelsDir: String,
    modelFiles: List<String>,
    promptsDir: String,
    sampleWavPath: String,
    onUseSample: () -> Unit,
    onOpenTermux: () -> Unit,
    onBatterySettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Section("Speech recognition")
        Choice("NPU · WhisperKit", settings.asrTier == Tier.NPU) { onChange { it.copy(asrTier = Tier.NPU) } }
        Choice("CPU · whisper.cpp in Termux (127.0.0.1:8082)", settings.asrTier == Tier.FALLBACK) { onChange { it.copy(asrTier = Tier.FALLBACK) } }

        Section("Language model")
        Choice("NPU · GenieX", settings.llmTier == Tier.NPU) { onChange { it.copy(llmTier = Tier.NPU) } }
        Choice("CPU · llama.cpp in Termux (127.0.0.1:8081)", settings.llmTier == Tier.FALLBACK) { onChange { it.copy(llmTier = Tier.FALLBACK) } }

        Section("Language hint")
        listOf(LANG_AUTO to "Auto-detect", "en" to "English", "hi" to "Hindi").forEach { (code, label) ->
            Choice(label, settings.language == code) { onChange { it.copy(language = code) } }
        }

        Section("Demo backup")
        Button(onClick = onUseSample, modifier = Modifier.fillMaxWidth()) { Text("Use sample recording") }
        Hint("Reads $sampleWavPath, falling back to the bundled asset")
        OutlinedButton(onClick = onOpenTermux, modifier = Modifier.fillMaxWidth()) { Text("Open Termux (restart servers)") }
        OutlinedButton(onClick = onBatterySettings, modifier = Modifier.fillMaxWidth()) { Text("Battery: allow background activity") }
        Hint("OriginOS kills background apps. Set Tachyon and Termux to \"no restrictions\".")

        Section("Files on this phone")
        Hint("Models: $modelsDir")
        if (modelFiles.isEmpty()) Hint("  (no model files yet)") else modelFiles.forEach { Hint("  • $it") }
        Hint("Prompt override: $promptsDir/extract_system.txt, schema.json")

        Section("Privacy")
        Hint("No internet hosts. The only network target is 127.0.0.1. No analytics, no backup.")
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
