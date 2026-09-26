package com.evolet.tachyon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.twin.Person

@Composable
fun PeopleScreen(people: List<Person>, onSave: (List<Person>) -> Unit) {
    var editing by remember { mutableStateOf<Person?>(null) }
    var newPerson by remember { mutableStateOf<Person?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("People context", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
            Text("These are demo contacts, not your phone contacts. They stay in internal storage.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(people, key = { it.id }) { person ->
            ElevatedCard(onClick = { editing = person }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium)
                    Text("${person.relation.ifBlank { "No relationship" }} · ${person.register} · ${person.channel}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap to edit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Button(onClick = { newPerson = Person(id = "p_${System.currentTimeMillis()}", name = "") }, modifier = Modifier.fillMaxWidth()) { Text("Add person") }
            Spacer(Modifier.height(16.dp))
        }
    }

    val target = editing ?: newPerson
    if (target != null) {
        PersonEditor(
            person = target,
            onDismiss = { editing = null; newPerson = null },
            onSave = { updated ->
                val next = if (people.any { it.id == updated.id }) people.map { if (it.id == updated.id) updated else it } else people + updated
                onSave(next)
                editing = null
                newPerson = null
            },
        )
    }
}

@Composable
private fun PersonEditor(person: Person, onDismiss: () -> Unit, onSave: (Person) -> Unit) {
    var name by remember(person.id) { mutableStateOf(person.name) }
    var relation by remember(person.id) { mutableStateOf(person.relation) }
    var register by remember(person.id) { mutableStateOf(person.register) }
    var channel by remember(person.id) { mutableStateOf(person.channel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (person.name.isBlank()) "Add person" else "Edit ${person.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(relation, { relation = it }, label = { Text("Relationship") }, singleLine = true)
                OutlinedTextField(register, { register = it }, label = { Text("Register") }, supportingText = { Text("formal, neutral, casual_hinglish or family") }, singleLine = true)
                OutlinedTextField(channel, { channel = it }, label = { Text("Channel") }, supportingText = { Text("email, whatsapp or sms") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(person.copy(name = name.trim(), relation = relation.trim(), register = register.trim(), channel = channel.trim())) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
