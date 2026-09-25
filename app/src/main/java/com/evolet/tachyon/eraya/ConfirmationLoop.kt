package com.evolet.tachyon.eraya

import com.evolet.tachyon.data.CommitmentDao
import com.evolet.tachyon.data.Status

/**
 * PROPOSED → ACCEPTED / REJECTED. The ONLY code path that changes a commitment's status,
 * and it's only wired to ✓ / ✗ / Undo buttons. Agents propose, humans commit.
 */
class ConfirmationLoop(private val dao: CommitmentDao, private val now: () -> Long = System::currentTimeMillis) {
    suspend fun accept(id: String) = dao.decide(id, Status.ACCEPTED, now())
    suspend fun reject(id: String) = dao.decide(id, Status.REJECTED, now())
    suspend fun undo(id: String) = dao.decide(id, Status.PROPOSED, null)
}
