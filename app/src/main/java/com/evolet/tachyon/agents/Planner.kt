package com.evolet.tachyon.agents

import com.evolet.tachyon.eraya.ExtractionResult
import com.evolet.tachyon.llm.LlmEngine

/** Planner: closed transcript in, proposals out (wraps the ERAYA ExtractionAgent). */
class Planner(
    private val bus: AgentBus,
    private val llm: () -> LlmEngine,
    private val extract: suspend (LlmEngine, String) -> ExtractionResult,
) {
    suspend fun plan(sessionId: String, transcript: String): ExtractionResult {
        bus.emit(AgentEvent.TranscriptClosed(sessionId))
        val r = extract(llm(), transcript)
        when (r) {
            is ExtractionResult.Ok -> bus.emit(AgentEvent.ProposalsReady(sessionId, r.items))
            is ExtractionResult.Failed -> bus.emit(AgentEvent.EngineDegraded(r.engine, r.reason))
        }
        return r
    }
}
