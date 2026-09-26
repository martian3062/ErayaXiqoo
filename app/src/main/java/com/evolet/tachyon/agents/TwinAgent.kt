package com.evolet.tachyon.agents

import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.llm.LlmEngine
import com.evolet.tachyon.twin.Persona
import com.evolet.tachyon.twin.Person
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class Channel { WHATSAPP, EMAIL, SMS }

data class Draft(val channel: Channel, val register: String, val text: String, val prompt: String)

/**
 * F14 Twin drafts (INTEGRATIONSv2.md §5.3), Engine B: same base model, persona few-shot prompt.
 * Output is only ever shown for editing and handed to the Android share sheet — never sent.
 */
class TwinAgent(private val bus: AgentBus? = null) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun draft(llm: LlmEngine, c: Commitment, to: Person?, persona: Persona): Draft {
        val system = systemPrompt(to, persona)
        val user = userPrompt(c, to, persona)
        val r = llm.complete(system, user, SCHEMA, maxTokens = 200)
        val text = limitWords(parseText(r.text) ?: r.text.trim(), MAX_WORDS)
        val d = Draft(channelOf(to), to?.register ?: "neutral", text, "$system\n\n$user")
        bus?.emit(AgentEvent.DraftReady(c.id, d.text))
        return d
    }

    internal fun parseText(raw: String): String? = runCatching {
        json.parseToJsonElement(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1)).jsonObject["text"]!!.jsonPrimitive.content.trim()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        const val MAX_WORDS = 60
        const val SCHEMA = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}"""

        private val REGISTERS = mapOf(
            "formal" to "Polite, professional English. Full sentences, no slang, no emoji.",
            "neutral" to "Friendly, plain English. Short sentences.",
            "casual_hinglish" to "Casual Hinglish (romanised Hindi mixed with English), like a WhatsApp message to a close friend.",
            "family" to "Warm and casual; Hinglish is fine.",
        )

        fun channelOf(to: Person?): Channel = when (to?.channel?.lowercase()) {
            "email" -> Channel.EMAIL
            "sms" -> Channel.SMS
            else -> Channel.WHATSAPP
        }

        fun systemPrompt(to: Person?, persona: Persona): String = buildString {
            val register = to?.register ?: "neutral"
            append("You write one short follow-up message from the phone's owner")
            if (to != null) append(" to ${to.name}${if (to.relation.isNotBlank()) " (${to.relation})" else ""}")
            append(" about a commitment.\n")
            append("Tone: ${REGISTERS[register] ?: REGISTERS.getValue("neutral")}\n")
            append("At most $MAX_WORDS words. Do not invent facts, dates or new promises beyond the commitment.\n")
            if (persona.style.rules.isNotEmpty()) append("Owner's style: ${persona.style.rules.joinToString("; ")}\n")
            val examples = persona.style.examples.filter { it.register == register }.ifEmpty { persona.style.examples }.take(3)
            if (examples.isNotEmpty()) {
                append("Examples of how the owner writes:\n")
                examples.forEach { append("- ").append(it.text).append('\n') }
            }
            append("Return JSON {\"text\": \"...\"} only.")
        }

        fun userPrompt(c: Commitment, to: Person?, persona: Persona): String = buildString {
            val who = when {
                c.ownerIsUser -> "The owner promised"
                c.owner.isBlank() || c.owner.equals("Speaker", true) || c.owner.equals("Unknown", true) -> "It was agreed"
                else -> "${c.owner} promised"
            }
            append("$who: ${c.task}.")
            val deadline = c.deadlineIso ?: c.deadlineText.takeUnless { it.equals("none", true) }
            if (deadline != null) append(" Deadline: $deadline.")
            append("\nOriginal words: \"${c.evidence}\"")
            append("\nChannel: ${channelOf(to).name.lowercase()}.")
            // Sign-offs are a work habit; casual/family messages end the owner's usual way (no sign-off).
            if ((to?.register ?: "neutral") in setOf("formal", "neutral")) {
                persona.style.signOffs.firstOrNull()?.let { append(" Sign off with \"$it\".") }
            } else {
                append(" No sign-off.")
            }
        }

        fun limitWords(text: String, max: Int): String {
            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            return if (words.size <= max) text.trim() else words.take(max).joinToString(" ") + "…"
        }
    }
}
