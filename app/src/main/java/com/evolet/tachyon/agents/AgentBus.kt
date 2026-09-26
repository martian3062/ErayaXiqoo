package com.evolet.tachyon.agents

import com.evolet.tachyon.eraya.Proposal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** One transcribed span. `speaker` stays null until voice-print attribution (F12) lands. */
data class Utterance(
    val text: String,
    val speaker: String?,
    val lang: String?,
    val t0Ms: Long,
    val t1Ms: Long,
)

enum class Decision { ACCEPT, REJECT, UNDO }

/**
 * In-process event bus between the ERAYA agents (INTEGRATIONSv2.md §3.1). Events for later features
 * (drafts, traits) are added together with the agent that emits them.
 */
sealed interface AgentEvent {
    data class UtteranceReady(val sessionId: String, val u: Utterance) : AgentEvent
    data class TranscriptClosed(val sessionId: String) : AgentEvent
    data class ProposalsReady(val sessionId: String, val items: List<Proposal>) : AgentEvent
    data class OwnerDecision(val proposalId: String, val decision: Decision, val editedText: String? = null) : AgentEvent
    data class EngineDegraded(val engine: String, val reason: String) : AgentEvent
    data class DraftReady(val commitmentId: String, val text: String) : AgentEvent
    /** F17 trait review: finalValue null = rejected; != proposedValue = edited. */
    data class TraitDecision(val field: String, val evidence: String, val proposedValue: String, val finalValue: String?) : AgentEvent
    data class DraftEdited(val commitmentId: String, val prompt: String, val original: String, val edited: String) : AgentEvent
}

class AgentBus {
    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    /** Never suspends; with 64 slots of buffer and fast collectors, dropping is not expected. */
    fun emit(e: AgentEvent) {
        _events.tryEmit(e)
    }
}
