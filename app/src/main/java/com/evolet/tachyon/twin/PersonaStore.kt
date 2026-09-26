package com.evolet.tachyon.twin

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * persona.json + people.json in internal storage (files/twin/, never shared storage).
 * people.json is seeded from the fictional assets/twin/people.demo.json on first run.
 */
class PersonaStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true; encodeDefaults = true }
    private val dir get() = File(context.filesDir, "twin").apply { mkdirs() }
    private val personaFile get() = File(dir, "persona.json")
    private val peopleFile get() = File(dir, "people.json")

    private val _persona = MutableStateFlow(loadPersona())
    val persona: StateFlow<Persona> = _persona.asStateFlow()

    private val _people = MutableStateFlow(loadPeople())
    val people: StateFlow<List<Person>> = _people.asStateFlow()

    fun savePersona(p: Persona) {
        personaFile.writeText(json.encodeToString(Persona.serializer(), p))
        _persona.value = p
    }

    fun savePeople(list: List<Person>) {
        peopleFile.writeText(json.encodeToString(ListSerializer(Person.serializer()), list))
        _people.value = list
    }

    /** "Delete my twin" (§10.1): persona + people + everything else under files/twin. */
    fun wipe(): List<String> {
        val deleted = dir.walkBottomUp().filter { it != dir }.map { it.name }.toList()
        dir.deleteRecursively()
        _persona.value = Persona()
        _people.value = emptyList()
        return deleted
    }

    fun reseedDemoPeople() = savePeople(readDemoPeople())

    private fun loadPersona(): Persona =
        runCatching { json.decodeFromString(Persona.serializer(), personaFile.readText()) }.getOrDefault(Persona())

    private fun loadPeople(): List<Person> {
        if (peopleFile.isFile) {
            runCatching { return json.decodeFromString(ListSerializer(Person.serializer()), peopleFile.readText()) }
        }
        return readDemoPeople().also { if (it.isNotEmpty()) savePeople(it) }
    }

    private fun readDemoPeople(): List<Person> = runCatching {
        context.assets.open("twin/people.demo.json").bufferedReader().use {
            json.decodeFromString(ListSerializer(Person.serializer()), it.readText())
        }
    }.getOrDefault(emptyList())
}
