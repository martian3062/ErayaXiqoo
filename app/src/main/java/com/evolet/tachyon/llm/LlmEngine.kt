package com.evolet.tachyon.llm

interface LlmEngine {
    val name: String                      // e.g. "GenieX·NPU·Qwen3-4B", "llama.cpp·CPU·Qwen3.5-2B"
    suspend fun load()
    suspend fun complete(system: String, user: String, jsonSchema: String?, maxTokens: Int = 512): LlmResult
    fun close()
}

data class LlmResult(val text: String, val latencyMs: Long, val tokensPerSec: Double?)
