package com.evolet.tachyon.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Session(
    @PrimaryKey val id: String,
    val startedAt: Long, val durationSec: Int,
    val transcript: String,
    val asrEngine: String, val llmEngine: String,
    val asrMs: Long, val llmMs: Long,
)
