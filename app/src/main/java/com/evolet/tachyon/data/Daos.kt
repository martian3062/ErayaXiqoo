package com.evolet.tachyon.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: Session)

    @Query("SELECT * FROM Session ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<Session>>

    @Query("SELECT * FROM Session WHERE id = :id")
    fun observe(id: String): Flow<Session?>
}

@Dao
interface CommitmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Commitment>)

    @Query("SELECT * FROM Commitment WHERE id = :id")
    suspend fun get(id: String): Commitment?

    @Query("SELECT * FROM Commitment WHERE id = :id")
    fun observeById(id: String): Flow<Commitment?>

    @Query("SELECT * FROM Commitment WHERE sessionId = :sessionId ORDER BY confidence DESC")
    fun observeSession(sessionId: String): Flow<List<Commitment>>

    @Query("SELECT * FROM Commitment WHERE status = :status ORDER BY decidedAt DESC")
    fun observeByStatus(status: Status): Flow<List<Commitment>>

    /** Only ever called from a user tap (ConfirmationLoop). */
    @Query("UPDATE Commitment SET status = :status, decidedAt = :decidedAt WHERE id = :id")
    suspend fun decide(id: String, status: Status, decidedAt: Long?)
}
