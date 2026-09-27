package com.evolet.tachyon.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.twin.Person
import com.evolet.tachyon.twin.Persona
import com.evolet.tachyon.ui.components.NavigationRow
import com.evolet.tachyon.ui.components.GlassCard
import com.evolet.tachyon.ui.components.PageIntro
import com.evolet.tachyon.ui.components.TachyonIcons
import com.evolet.tachyon.voice.VoiceProfile

@Composable
fun YouScreen(
    persona: Persona,
    people: List<Person>,
    voiceProfile: VoiceProfile?,
    onProfile: () -> Unit,
    onPeople: () -> Unit,
    onReplica: () -> Unit,
    onVoice: () -> Unit,
    onEyes: () -> Unit,
    onHandshake: () -> Unit,
    onInterview: (Boolean) -> Unit,
    onPrivacy: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageIntro(
                eyebrow = "Your private self",
                title = persona.owner.name.ifBlank { "You" },
                supporting = "${persona.traits.size} confirmed traits · ${people.size} people · all stored on this phone",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item { NavigationRow("My profile", "Confirmed traits and the evidence behind them", TachyonIcons.Person, onProfile) }
        item { NavigationRow("Portrait Studio", "Private anime portrait, eye motion and lip-sync", TachyonIcons.Camera, onReplica) }
        item {
            NavigationRow(
                "My voice",
                if (voiceProfile == null) "Private 10-minute voice and accent setup" else "${voiceProfile.clipCount} private clips · personalized voice ready",
                TachyonIcons.Mic,
                onVoice,
            )
        }
        item { NavigationRow("People", "Names, relationships, register and channel", TachyonIcons.Person, onPeople) }
        item { NavigationRow("Eyes", "Photograph notes and propose commitments offline", TachyonIcons.Camera, onEyes) }
        item { NavigationRow("Trust handshake", "Two phones sign the same commitment offline", TachyonIcons.Shield, onHandshake) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Behaviour interview", style = MaterialTheme.typography.titleMedium)
                    Text("Answer out loud or type. Only traits you confirm are saved.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onInterview(true) }) { Text("Demo · 6 questions") }
                        TextButton(onClick = { onInterview(false) }) { Text("Full interview") }
                    }
                }
            }
        }
        item { NavigationRow("Privacy & export", "Training export, local-data boundary and delete", TachyonIcons.Settings, onPrivacy) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun ProfileScreen(persona: Persona, onDeleteTrait: (Int) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(persona.owner.name.ifBlank { "You" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                listOfNotNull(persona.owner.role, persona.owner.languages.takeIf { it.isNotEmpty() }?.joinToString()).joinToString(" · ").ifEmpty { "Profile builds only from confirmed answers." },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (persona.traits.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("No confirmed interview traits yet.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(persona.traits, key = { index, trait -> "${trait.field}:${trait.confirmedAt}:$index" }) { index, trait ->
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(trait.field.replace('_', ' '), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDeleteTrait(index) }) {
                            Icon(TachyonIcons.Delete, contentDescription = "Delete trait")
                        }
                    }
                    Text(trait.value, style = MaterialTheme.typography.titleMedium)
                    Text("“${trait.evidence}”", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun PrivacyScreen(
    persona: Persona,
    peopleCount: Int,
    voiceProfile: VoiceProfile?,
    onExportPrefs: (Boolean) -> Unit,
    onDeleteTwin: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("On-device by design", style = MaterialTheme.typography.headlineSmall)
        Text("No internet hosts, analytics or backup. The only network target is 127.0.0.1 for local Termux engines.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Stored twin data", style = MaterialTheme.typography.titleMedium)
                Text("${persona.traits.size} traits · ${persona.style.rules.size} style rules · $peopleCount people · ${voiceProfile?.clipCount ?: 0} voice clips")
                Text("Replica media, voice recordings, consent audio and voice biometrics are never included in exports.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = { onExportPrefs(false) }, modifier = Modifier.fillMaxWidth()) { Text("Export training pairs · names redacted") }
        OutlinedButton(onClick = { onExportPrefs(true) }, modifier = Modifier.fillMaxWidth()) { Text("Export including names") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDeleteTwin, modifier = Modifier.fillMaxWidth()) { Text("Delete my twin") }
        Text("Delete wipes persona, people, preference pairs, voice recordings/profile, Eyes captures, Portrait Studio media and handshake ledger from this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
