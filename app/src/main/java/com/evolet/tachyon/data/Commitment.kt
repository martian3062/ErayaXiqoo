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
    // F13 people-aware extraction (INTEGRATIONSv2.md §5.2)
    val ownerIsUser: Boolean = false,
    val ownerPersonId: String? = null,
    val toPersonId: String? = null,
    val riskNote: String? = null,
)

enum class Status { PROPOSED, ACCEPTED, REJECTED }
