package com.evolet.tachyon.trust

import com.evolet.tachyon.agents.AgentBus
import com.evolet.tachyon.agents.AgentEvent
import com.evolet.tachyon.agents.Decision
import com.evolet.tachyon.agents.Learner
import com.evolet.tachyon.agents.TwinAgent
import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.data.Kind
import com.evolet.tachyon.data.PreferencePair
import com.evolet.tachyon.data.Status
import com.evolet.tachyon.twin.Persona
import com.evolet.tachyon.twin.Person
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F14F15Test {
    private val alex = Person("p_alex", "Alex", listOf("Alex sir"), "manager", "formal", "email")
    private val sam = Person("p_sam", "Sam", listOf("Sammy"), "friend", "casual_hinglish", "whatsapp")
    private val c = Commitment("c1", "s1", "Speaker", "Send the revised intake form", "Alex", "Friday evening", "2026-10-02T18:00",
        "I'll send you the revised intake form by Friday evening", 0.9, Status.ACCEPTED, 1L, ownerIsUser = true, toPersonId = "p_alex")

    @Test fun `draft prompt follows the recipient register`() {
        val persona = Persona(style = Persona.Style(examples = listOf(Persona.StyleExample("casual_hinglish", "oye kr dunga"), Persona.StyleExample("formal", "Hi, will do. Thanks"))))
        val formal = TwinAgent.systemPrompt(alex, persona)
        val casual = TwinAgent.systemPrompt(sam, persona)
        assertTrue(formal.contains("professional") && formal.contains("Hi, will do. Thanks") && !formal.contains("oye kr dunga"))
        assertTrue(casual.contains("Hinglish") && casual.contains("oye kr dunga"))
        assertTrue(TwinAgent.userPrompt(c, alex, persona).startsWith("The owner promised"))
        assertEquals(TwinAgent.channelOf(alex).name, "EMAIL")
        val withSignOff = Persona(style = Persona.Style(signOffs = listOf("Thanks")))
        assertTrue(TwinAgent.userPrompt(c, alex, withSignOff).contains("Sign off with \"Thanks\""))
        assertTrue(TwinAgent.userPrompt(c, sam, withSignOff).contains("No sign-off"))
    }

    @Test fun `drafts are capped at 60 words`() {
        val long = (1..80).joinToString(" ") { "w$it" }
        assertEquals(60, TwinAgent.limitWords(long, 60).split(" ").size)
        assertTrue(TwinAgent.limitWords(long, 60).endsWith("…"))
        assertEquals("short one", TwinAgent.limitWords("short one", 60))
    }

    @Test fun `twin parses json text`() {
        assertEquals("Hi Alex", TwinAgent().parseText("""{"text":"Hi Alex"}"""))
        assertEquals(null, TwinAgent().parseText("garbage"))
    }

    @Test fun `learner records decisions and real edits only`() = runBlocking {
        val saved = mutableListOf<PreferencePair>()
        val l = Learner(AgentBus(), lookup = { if (it == "c1") c else null }, save = { saved += it }, now = { 5L })
        l.handle(AgentEvent.OwnerDecision("c1", Decision.ACCEPT))
        l.handle(AgentEvent.OwnerDecision("c1", Decision.UNDO))
        l.handle(AgentEvent.OwnerDecision("missing", Decision.REJECT))
        l.handle(AgentEvent.DraftEdited("c1", "p", "same", "same "))
        l.handle(AgentEvent.DraftEdited("c1", "p", "original", "edited"))
        assertEquals(listOf(Kind.PROPOSAL_DECISION, Kind.DRAFT_EDIT), saved.map { it.kind })
        assertTrue(saved[0].chosen.contains("intake form"))
        assertEquals("edited", saved[1].chosen)
    }

    @Test fun `guardian redacts names and aliases in exports unless opted in`() {
        val pairs = listOf(PreferencePair("1", Kind.DRAFT_EDIT, "to Alex sir", "Hi Alex, done", "hey sammy", 1))
        val redacted = TrustPolicy.exportJsonl(pairs, listOf(alex, sam), includeNames = false)
        assertFalse(redacted.contains("Alex") || redacted.contains("sammy", ignoreCase = true))
        assertTrue(redacted.contains("[PERSON]") && redacted.contains("\"prompt\""))
        assertTrue(TrustPolicy.exportJsonl(pairs, listOf(alex), includeNames = true).contains("Alex sir"))
    }
}
