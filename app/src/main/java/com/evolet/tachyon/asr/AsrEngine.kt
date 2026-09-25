package com.evolet.tachyon.asr

interface AsrEngine {
    val name: String                      // e.g. "WhisperKit·NPU", "whisper.cpp·CPU"
    suspend fun load()
    suspend fun transcribe(pcm16k: ShortArray, languageHint: String? = null): AsrResult
    fun close()
}

data class AsrResult(val text: String, val latencyMs: Long, val language: String?)
