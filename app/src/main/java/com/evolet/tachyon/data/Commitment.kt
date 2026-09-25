package com.evolet.tachyon.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index("sessionId"), Index("status")])
data class Commitment(
    @PrimaryKey val id: String,
    val sessionId: String,
    val owner: String, val task: String, val toWhom: String?,
    val deadlineText: String, val deadlineIso: String?,
    val evidence: String, val confidence: Double,
    val status: Status,                 // PROPOSED, ACCEPTED, REJECTED
    val decidedAt: Long?,
)

enum class Status { PROPOSED, ACCEPTED, REJECTED }
