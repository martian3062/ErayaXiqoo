package com.evolet.tachyon.twin

import com.evolet.tachyon.agents.AgentBus
import com.evolet.tachyon.agents.AgentEvent
import com.evolet.tachyon.agents.Learner
import com.evolet.tachyon.data.Kind
import com.evolet.tachyon.data.PreferencePair
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterviewTest {
    private val q15 = Question("q15", 4, "How much time do you really need for a report?", listOf("self_reported_durations"), true, "Roughly how many hours or days?")
    private val q9 = Question("q9", 3, "Reply to your manager", listOf("style_examples_formal"))
    private val allowed = setOf("self_reported_durations", "underestimates", "goals")

    @Test fun `trait filter blocks sensitive categories even when the model proposes them`() {
        val bad = listOf(
            Triple("goals", "manage my anxiety better", "I want to manage my anxiety better"),
            Triple("goals", "save 5 lakh", "save 5 lakh this year"),
            Triple("goals", "pray more at the temple", "go to the temple more"),
            Triple("underestimates", "an INTJ planner", "I'm an INTJ"),
            Triple("goals", "earn more salary", "higher salary"),
        )
        bad.forEach { (f, v, e) -> assertEquals(v, TraitFilter.Reason.BLOCKED_CATEGORY, TraitFilter.check(f, v, e, allowed)) }
        assertEquals(TraitFilter.Reason.FIELD_NOT_ALLOWED, TraitFilter.check("religion", "x", "x", allowed))
    }

    @Test fun `trait filter lets ordinary answers through`() {
        assertNull(TraitFilter.check("self_reported_durations", "report: 2 days", "usually 2 days for a report", allowed))
        assertNull(TraitFilter.check("underestimates", "slides and emails", "aap log slides bana do, I always underestimate emails", allowed))
        assertNull(TraitFilter.check("goals", "ship a good product this year", "a good product", allowed))
    }

    @Test fun `one follow-up only for short answers`() {
        assertNotNull(InterviewEngine.followUpFor(q15, "depends", alreadyAsked = false))
        assertNull(InterviewEngine.followUpFor(q15, "depends", alreadyAsked = true))
        assertNull(InterviewEngine.followUpFor(q15, "honestly for a proper report I need about two full days with all the checks done", false))
        assertNull(InterviewEngine.followUpFor(q9, "ok", false))
    }

    @Test fun `trait validation needs an exact quote, confidence and a target field`() {
        val answers = listOf(Answer(q15, "depends", "usually 2 days for a proper report"))
        val raw = """{"traits":[
            {"field":"self_reported_durations","value":"report: 2 days","evidence":"usually 2 days for a proper report","confidence":0.9},
            {"field":"self_reported_durations","value":"slides: 1 day","evidence":"slides take one day","confidence":0.9},
            {"field":"self_reported_durations","value":"report: 3 days","evidence":"usually 2 days","confidence":0.3},
            {"field":"goals","value":"x","evidence":"depends","confidence":0.9}]}"""
        val r = InterviewEngine.validate(raw, answers, setOf("self_reported_durations"))
        assertEquals(listOf("report: 2 days"), r.proposals.map { it.value })
        assertEquals(3, r.dropped)
        assertTrue(InterviewEngine.schema(listOf("a", "b")).contains("\"enum\":[\"a\",\"b\"]"))
        assertTrue(InterviewEngine.prompt(listOf("goals"), answers).contains("Target fields: goals"))
    }

    @Test fun `confirmed traits drive behaviour fields`() {
        var p = Persona()
        p = PersonaMerger.merge(p, Trait("self_reported_durations", "report: 2 days", "usually 2 days for a proper report"))
        p = PersonaMerger.merge(p, Trait("style_examples_formal", "formal reply", "Sure, I'll share it by today evening."))
        p = PersonaMerger.merge(p, Trait("style_examples_casual", "casual reply", "hn bhai kr dunga aaj"))
        p = PersonaMerger.merge(p, Trait("name", "Evolet", "call me Evolet"))
        p = PersonaMerger.merge(p, Trait("reminder_tolerance", "2 reminders", "two, max 2"))
        assertEquals(mapOf("report" to "2 days"), p.commit.selfReportedDurations)
        assertEquals(listOf("formal", "casual_hinglish"), p.style.examples.map { it.register })
        assertEquals("Evolet", p.owner.name)
        assertEquals(2, p.rhythm.reminderTolerance)
        assertEquals(5, p.traits.size)
        assertEquals(listOf("en", "hi"), PersonaMerger.languageCodes("Hinglish mostly"))
    }

    @Test fun `confirmed duration trait turns on the Tight for you chip`() {
        val p = PersonaMerger.merge(Persona(), Trait("self_reported_durations", "report: 2 days", "2 days for a report"))
        assertNotNull(RiskScorer.risk("Send the stock report", "2026-09-27", p, java.time.LocalDateTime.of(2026, 9, 26, 10, 0)))
    }

    @Test fun `trait decisions become preference pairs`() = runBlocking {
        val saved = mutableListOf<PreferencePair>()
        val l = Learner(AgentBus(), lookup = { null }, save = { saved += it })
        l.handle(AgentEvent.TraitDecision("goals", "ev", "ship it", "ship it"))
        l.handle(AgentEvent.TraitDecision("goals", "ev", "ship it", "ship it this year"))
        l.handle(AgentEvent.TraitDecision("goals", "ev", "ship it", null))
        assertEquals(List(3) { Kind.TRAIT_DECISION }, saved.map { it.kind })
        assertEquals(listOf("ship it", "ship it this year", "(no trait)"), saved.map { it.chosen })
        assertEquals(listOf("(no trait)", "ship it", "ship it"), saved.map { it.rejected })
    }
}
