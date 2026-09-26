package com.evolet.tachyon.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/** F15 learning loop (INTEGRATIONSv2.md §5.4): one DPO-style pair per owner decision or draft edit. */
@Entity
data class PreferencePair(
    @PrimaryKey val id: String,
    val kind: Kind,
    val prompt: String,        // context shown to the model
    val chosen: String,        // accepted / edited / confirmed version
    val rejected: String?,     // rejected / original / discarded version
    val createdAt: Long,
)

enum class Kind { PROPOSAL_DECISION, DRAFT_EDIT, TRAIT_DECISION }

@Dao
interface PreferenceDao {
    @Insert
    suspend fun insert(p: PreferencePair)

    @Query("SELECT * FROM PreferencePair ORDER BY createdAt")
    suspend fun all(): List<PreferencePair>

    @Query("SELECT COUNT(*) FROM PreferencePair")
    suspend fun count(): Int

    @Query("DELETE FROM PreferencePair")
    suspend fun clear()
}
