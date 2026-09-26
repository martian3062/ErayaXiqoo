package com.evolet.tachyon.twin

/**
 * F13: OWNER PROFILE + PEOPLE blocks prepended to the extraction prompt (INTEGRATIONSv2.md §5.2).
 * Only confirmed persona fields are used; empty sections are omitted so the base prompt is unchanged
 * for a fresh install. Pure Kotlin → unit-tested.
 */
object PromptContext {

    fun block(persona: Persona, people: List<Person>): String {
        val sb = StringBuilder()
        val o = persona.owner
        val ownerLines = buildList {
            if (o.name.isNotBlank() && o.name != "You") add("name: ${o.name}")
            if (o.role != null) add("role: ${o.role}")
            if (o.languages.isNotEmpty()) add("languages: ${o.languages.joinToString()}")
            if (persona.commit.underestimates.isNotEmpty()) add("often underestimates: ${persona.commit.underestimates.joinToString()}")
        }
        if (ownerLines.isNotEmpty()) {
            sb.append("OWNER PROFILE (the phone's owner; lines tagged [You] are the owner speaking)\n")
            ownerLines.forEach { sb.append("- ").append(it).append('\n') }
            sb.append('\n')
        }
        if (people.isNotEmpty()) {
            sb.append("PEOPLE (id | name | aliases | relation | register)\n")
            people.forEach { p ->
                sb.append("${p.id} | ${p.name} | ${p.aliases.joinToString("/").ifEmpty { "-" }} | ${p.relation.ifEmpty { "-" }} | ${p.register}\n")
            }
            sb.append("Set owner_person_id / to_person_id to a PEOPLE id when the person is clearly one of them, else null.\n")
            sb.append("Set owner_is_user true only when the owner of the commitment is the phone's owner ([You] or \"I\" said by [You]).\n\n")
        }
        return sb.toString()
    }
}
