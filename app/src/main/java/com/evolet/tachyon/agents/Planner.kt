package com.evolet.tachyon.agents

import com.evolet.tachyon.eraya.ExtractionResult
import com.evolet.tachyon.llm.LlmEngine

/**
 * Planner: closed transcript in, proposals out. Two agents in sequence: the ERAYA extractor
 * proposes, the [Verifier] votes out anything that isn't a firm promise.
 */
class Planner(
    private val bus: AgentBus,
    private val llm: () -> LlmEngine,
    private val extract: suspend (LlmEngine, String) -> ExtractionResult,
    private val verifier: Verifier? = null,
) {
    suspend fun plan(sessionId: String, transcript: String): ExtractionResult {
        bus.emit(AgentEvent.TranscriptClosed(sessionId))
        val engine = llm()
        var r = extract(engine, transcript)
        if (r is ExtractionResult.Ok && verifier != null) {
            val v = verifier.verify(engine, transcript, r.items)
            r = r.copy(items = v.kept, dropped = r.dropped + v.rejected, latencyMs = r.latencyMs + v.latencyMs)
        }
        when (r) {
            is ExtractionResult.Ok -> bus.emit(AgentEvent.ProposalsReady(sessionId, r.items))
            is ExtractionResult.Failed -> bus.emit(AgentEvent.EngineDegraded(r.engine, r.reason))
        }
        return r
    }
}
