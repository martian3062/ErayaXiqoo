package com.evolet.tachyon.twin

import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.data.Status
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitmentRecallTest {
    private val now = 1_800_000_000_000L

    @Test
    fun routesExplicitRecallButLeavesOrdinaryChatForTheLlm() {
        val engine = engine(emptyList())

        assertTrue(engine.isRecallQuery("What did I promise Alex?"))
        assertTrue(engine.isRecallQuery("Show my rejected proposals"))
        assertTrue(engine.isRecallQuery("Maine Sam ko kya waada kiya tha?"))
        assertFalse(engine.isRecallQuery("Help me plan a focused morning"))
        assertFalse(engine.isRecallQuery("Write a message to Alex"))
    }

    @Test
    fun personMatchBeatsNewerUnrelatedCommitmentAndCitesTheRecord() = runBlocking {
        val records = listOf(
            commitment("alex-form-123", "Send the revised form", "Alex", now - 30L * DAY),
            commitment("maya-slides-9", "Share the presentation", "Maya", now - DAY),
        )

        val result = engine(records).recall("What did I promise Alex?")!!

        assertEquals(listOf("alex-form-123"), result.sources.map { it.id })
        assertTrue(result.answer.contains("#alex-for"))
        assertTrue(result.answer.contains("Send the revised form"))
    }

    @Test
    fun defaultsToConfirmedOwnerRecordsOnly() = runBlocking {
        val records = listOf(
            commitment("mine", "Call the supplier", "Alex", now - DAY),
            commitment("rejected", "Buy event tickets", "Alex", now, status = Status.REJECTED),
            commitment("theirs", "Prepare venue", "Alex", now, owner = "Jordan", ownerIsUser = false),
        )

        val result = engine(records).recall("List my commitments")!!

        assertEquals(listOf("mine"), result.sources.map { it.id })
    }

    @Test
    fun rejectedQueryUsesRejectedScope() = runBlocking {
        val records = listOf(
            commitment("accepted", "Send the contract", "Alex", now, status = Status.ACCEPTED),
            commitment("rejected", "Send the draft", "Alex", now, status = Status.REJECTED),
        )

        val result = engine(records).recall("What did I reject for Alex?")!!

        assertEquals(listOf("rejected"), result.sources.map { it.id })
        assertTrue(result.answer.contains("rejected proposal"))
    }

    @Test
    fun unrelatedNamedQueryReturnsHonestNoMatch() = runBlocking {
        val result = engine(listOf(commitment("one", "Send the report", "Alex", now)))
            .recall("What did I promise Priya?")!!

        assertTrue(result.sources.isEmpty())
        assertTrue(result.answer.contains("couldn't find"))
    }

    private fun engine(records: List<Commitment>) = CommitmentRecallEngine(
        records = { records },
        now = { now },
    )

    private fun commitment(
        id: String,
        task: String,
        to: String,
        decidedAt: Long,
        status: Status = Status.ACCEPTED,
        owner: String = "You",
        ownerIsUser: Boolean = true,
    ) = Commitment(
        id = id,
        sessionId = "session",
        owner = owner,
        task = task,
        toWhom = to,
        deadlineText = "Friday",
        deadlineIso = "2026-10-02T17:00:00",
        evidence = "I will $task for $to by Friday",
        confidence = 0.92,
        status = status,
        decidedAt = decidedAt,
        ownerIsUser = ownerIsUser,
    )

    companion object {
        private const val DAY = 86_400_000L
    }
}
