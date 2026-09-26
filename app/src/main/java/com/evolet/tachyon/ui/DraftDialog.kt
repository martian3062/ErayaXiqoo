package com.evolet.tachyon.ui

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.AppContainer
import com.evolet.tachyon.agents.AgentEvent
import com.evolet.tachyon.agents.Draft
import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.twin.Person
import com.evolet.tachyon.ui.components.Avatar

/**
 * F14: draft a follow-up in the owner's style for a chosen person. Editable; "Share" opens the
 * Android share sheet — Tachyon never sends anything itself. Edits become F15 preference pairs.
 */
@Composable
fun DraftDialog(container: AppContainer, commitment: Commitment, people: List<Person>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var to by remember { mutableStateOf(people.firstOrNull { it.id == commitment.toPersonId } ?: people.firstOrNull()) }
    var draft by remember { mutableStateOf<Draft?>(null) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(to) {
        draft = null
        error = null
        runCatching {
            container.twin.draft(container.engines.llm(), commitment, to, container.personaStore.persona.value)
        }.onSuccess {
            draft = it
            text = it.text
        }.onFailure { error = "Couldn't draft: ${it.message}" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draft follow-up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(commitment.task, style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    people.forEach { p ->
                        FilterChip(
                            selected = p == to,
                            onClick = { to = p },
                            label = { Text(p.name) },
                            leadingIcon = { Avatar(p.name, size = 20) },
                        )
                    }
                }
                to?.let { Text("${it.relation} · ${it.register.replace('_', ' ')} · ${it.channel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    draft == null -> {
                        Text("Writing in your style, on this phone…", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    else -> OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        label = { Text("Edit before sharing") },
                    )
                }
                Text("Nothing is sent automatically — Share opens your apps.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(enabled = draft != null && text.isNotBlank(), onClick = {
                draft?.let { d ->
                    if (text.trim() != d.text.trim()) {
                        container.bus.emit(AgentEvent.DraftEdited(commitment.id, d.prompt, d.text, text))
                    }
                }
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, "Share follow-up"))
                onDismiss()
            }) { Text("Share") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
