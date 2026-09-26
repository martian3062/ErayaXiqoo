package com.evolet.tachyon.agents

import android.util.Log
import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.data.Kind
import com.evolet.tachyon.data.PreferencePair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Learner: turns owner taps and draft edits into preference pairs (F15). The phone never trains;
 * pairs are exported as JSONL for the laptop-side DPO run.
 */
class Learner(
    private val bus: AgentBus,
    private val lookup: suspend (String) -> Commitment?,
    private val save: suspend (PreferencePair) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            bus.events.collect { e ->
                runCatching { handle(e) }.onFailure { Log.w("Learner", "could not record pair", it) }
            }
        }
    }

    internal suspend fun handle(e: AgentEvent) {
        when (e) {
            is AgentEvent.OwnerDecision -> {
                if (e.decision == Decision.UNDO) return
                val c = lookup(e.proposalId) ?: return
                save(decisionPair(c, e.decision == Decision.ACCEPT))
            }
            is AgentEvent.DraftEdited -> {
                if (e.edited.trim() == e.original.trim()) return
                save(PreferencePair(UUID.randomUUID().toString(), Kind.DRAFT_EDIT, e.prompt, e.edited, e.original, now()))
            }
            else -> Unit
        }
    }

    /** Prompt = the evidence; chosen/rejected = whether it was a real commitment, as the model should answer. */
    internal fun decisionPair(c: Commitment, accepted: Boolean): PreferencePair {
        val prompt = "Is this a commitment? Quote: \"${c.evidence}\""
        val asCommitment = """{"task":"${c.task.replace("\"", "'")}","deadline_text":"${c.deadlineText.replace("\"", "'")}"}"""
        val notCommitment = """{"commitments":[]}"""
        return PreferencePair(
            UUID.randomUUID().toString(), Kind.PROPOSAL_DECISION, prompt,
            chosen = if (accepted) asCommitment else notCommitment,
            rejected = if (accepted) notCommitment else asCommitment,
            createdAt = now(),
        )
    }
}
