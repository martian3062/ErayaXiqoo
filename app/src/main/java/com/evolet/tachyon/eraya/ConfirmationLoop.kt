package com.evolet.tachyon.eraya

import com.evolet.tachyon.agents.AgentBus
import com.evolet.tachyon.agents.AgentEvent
import com.evolet.tachyon.agents.Decision
import com.evolet.tachyon.data.CommitmentDao
import com.evolet.tachyon.data.Status

/**
 * PROPOSED → ACCEPTED / REJECTED. The ONLY code path that changes a commitment's status,
 * and it's only wired to ✓ / ✗ / Undo buttons. Agents propose, humans commit.
 */
class ConfirmationLoop(
    private val dao: CommitmentDao,
    private val bus: AgentBus? = null,               // OwnerDecision events feed the Learner (F15)
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun accept(id: String) = decide(id, Status.ACCEPTED, Decision.ACCEPT)
    suspend fun reject(id: String) = decide(id, Status.REJECTED, Decision.REJECT)
    suspend fun undo(id: String) = decide(id, Status.PROPOSED, Decision.UNDO)

    private suspend fun decide(id: String, status: Status, decision: Decision) {
        dao.decide(id, status, if (status == Status.PROPOSED) null else now())
        bus?.emit(AgentEvent.OwnerDecision(id, decision))
    }
}
