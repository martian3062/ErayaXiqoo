package com.evolet.tachyon.twin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class TwinTest {

    private val persona = Persona(commit = Persona.CommitHabits(selfReportedDurations = mapOf("report" to "2 days", "slides" to "1 day")))
    private val sat10 = LocalDateTime.of(2026, 9, 26, 10, 0)

    @Test fun `buckets tasks by keyword`() {
        assertEquals("report", RiskScorer.bucket("Send the pharmacy stock report"))
        assertEquals("slides", RiskScorer.bucket("Prepare the deck"))
        assertEquals("book", RiskScorer.bucket("Book the training room"))
        assertNull(RiskScorer.bucket("Water the plants"))
    }

    @Test fun `parses spoken durations`() {
        assertEquals(Duration.ofDays(2), RiskScorer.parseDuration("2 days"))
        assertEquals(Duration.ofHours(3), RiskScorer.parseDuration("about 3 hours"))
        assertEquals(Duration.ofDays(1), RiskScorer.parseDuration("1 din"))
        assertEquals(Duration.ofHours(12), RiskScorer.parseDuration("half a day"))
        assertNull(RiskScorer.parseDuration("a while"))
    }

    @Test fun `flags a report due tomorrow when the owner needs two days`() {
        val note = RiskScorer.risk("Send the stock report", "2026-09-27", persona, sat10)
        assertTrue(note!!.startsWith("Tight for you"))
    }

    @Test fun `no chip when there is enough time or no self-report`() {
        assertNull(RiskScorer.risk("Send the stock report", "2026-10-02", persona, sat10))
        assertNull(RiskScorer.risk("Call the landlord", "2026-09-26T11:00", persona, sat10))
        assertNull(RiskScorer.risk("Send the stock report", "2026-09-27", Persona(), sat10))
        assertNull(RiskScorer.risk("Send the stock report", null, persona, sat10))
    }

    @Test fun `prompt context lists people and omits empty owner profile`() {
        val block = PromptContext.block(Persona(), listOf(Person("p_alex", "Alex", listOf("Alex sir"), "manager", "formal")))
        assertTrue(block.contains("p_alex | Alex | Alex sir | manager | formal"))
        assertTrue(!block.contains("OWNER PROFILE"))
        assertEquals("", PromptContext.block(Persona(), emptyList()))
    }
}
