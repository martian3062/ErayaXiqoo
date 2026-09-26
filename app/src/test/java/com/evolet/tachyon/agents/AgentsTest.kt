package com.evolet.tachyon.agents

import com.evolet.tachyon.asr.AsrEngine
import com.evolet.tachyon.asr.AsrResult
import com.evolet.tachyon.eraya.ExtractionResult
import com.evolet.tachyon.eraya.Proposal
import com.evolet.tachyon.llm.LlmEngine
import com.evolet.tachyon.llm.LlmResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AgentsTest {

    private class FakeAsr(private val failures: Int) : AsrEngine {
        var calls = 0
        override val name = "fake-asr"
        override suspend fun load() = Unit
        override suspend fun transcribe(pcm16k: ShortArray, languageHint: String?): AsrResult {
            calls++
            if (calls <= failures) throw IOException("EOF")
            return AsrResult("hello there", 10, languageHint)
        }
        override fun close() = Unit
    }

    private object FakeLlm : LlmEngine {
        override val name = "fake-llm"
        override suspend fun load() = Unit
        override suspend fun complete(system: String, user: String, jsonSchema: String?, maxTokens: Int) = LlmResult("{}", 1, null)
        override fun close() = Unit
    }

    /** Collects every bus event while [block] runs. */
    private fun events(bus: AgentBus, block: suspend () -> Unit): List<AgentEvent> = runBlocking {
        val seen = mutableListOf<AgentEvent>()
        val job = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) { bus.events.collect { seen += it } }
        block()
        job.cancel()
        seen
    }

    @Test fun `perceiver retries one IO failure and emits the utterance with timing`() {
        val bus = AgentBus()
        val asr = FakeAsr(failures = 1)
        val seen = events(bus) {
            val r = Perceiver(bus) { asr }.perceive("s1", ShortArray(16_000), offsetMs = 30_000, sampleRate = 16_000, languageHint = "en")
            assertEquals("hello there", r.utterance.text)
            assertEquals(30_000, r.utterance.t0Ms)
            assertEquals(31_000, r.utterance.t1Ms)
        }
        assertEquals(2, asr.calls)
        assertTrue(seen.single() is AgentEvent.UtteranceReady)
    }

    @Test fun `perceiver reports degraded engine after the retry also fails`() {
        val bus = AgentBus()
        var threw = false
        val seen = events(bus) {
            try {
                Perceiver(bus) { FakeAsr(failures = 2) }.perceive("s1", ShortArray(10), 0, 16_000, null)
            } catch (e: IOException) {
                threw = true
            }
        }
        assertTrue(threw)
        assertEquals("fake-asr", (seen.single() as AgentEvent.EngineDegraded).engine)
    }

    @Test fun `planner emits transcript closed then proposals`() {
        val bus = AgentBus()
        val p = Proposal("A", "Send form", null, "Friday", null, "I'll send the form", 0.9)
        val seen = events(bus) {
            Planner(bus, { FakeLlm }, extract = { _, _ -> ExtractionResult.Ok(listOf(p), 0, null, 5, "fake-llm") }).plan("s1", "I'll send the form")
        }
        assertTrue(seen[0] is AgentEvent.TranscriptClosed)
        assertEquals(listOf(p), (seen[1] as AgentEvent.ProposalsReady).items)
    }

    @Test fun `recoverer re-probes at most once per gap`() {
        var t = 0L
        var probes = 0
        val r = Recoverer(AgentBus(), reprobe = { probes++ }, now = { t }, minGapMs = 5_000)
        val e = AgentEvent.EngineDegraded("x", "down")
        t = 10_000; assertTrue(r.onDegraded(e))
        t = 12_000; assertFalse(r.onDegraded(e))
        t = 15_001; assertTrue(r.onDegraded(e))
        assertEquals(2, probes)
    }

    private class ScriptedLlm(private val reply: String) : LlmEngine {
        override val name = "scripted"
        override suspend fun load() = Unit
        override suspend fun complete(system: String, user: String, jsonSchema: String?, maxTokens: Int) = LlmResult(reply, 7, null)
        override fun close() = Unit
    }

    private fun prop(ev: String) = Proposal("A", "t", null, "none", null, ev, 0.9)

    @Test fun `verifier drops items voted false`() = runBlocking {
        val r = Verifier().verify(ScriptedLlm("""{"verdicts":[true,false]}"""), "tx", listOf(prop("I'll send it"), prop("maybe later")))
        assertEquals(listOf("I'll send it"), r.kept.map { it.evidence })
        assertEquals(1, r.rejected)
    }

    @Test fun `verifier fails open on garbage or short verdicts`() = runBlocking {
        val items = listOf(prop("a"), prop("b"))
        assertEquals(2, Verifier().verify(ScriptedLlm("no idea"), "tx", items).kept.size)
        assertEquals(2, Verifier().verify(ScriptedLlm("""{"verdicts":[false]}"""), "tx", items).kept.size)
    }

    @Test fun `planner applies verifier and adds its latency`() {
        val bus = AgentBus()
        val ok = ExtractionResult.Ok(listOf(prop("keep"), prop("drop")), 0, null, 5, "scripted")
        val r = runBlocking {
            Planner(bus, { ScriptedLlm("""{"verdicts":[true,false]}""") }, { _, _ -> ok }, Verifier()).plan("s", "tx")
        } as ExtractionResult.Ok
        assertEquals(1, r.items.size)
        assertEquals(1, r.dropped)
        assertEquals(12, r.latencyMs)
    }
}
