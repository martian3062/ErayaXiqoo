package com.evolet.tachyon.llm

import android.content.Context

/**
 * Tier 1 LLM on the Snapdragon NPU. NOT INTEGRATED YET, this is block B4.
 *
 * Rule 6: don't guess the SDK. Copy the real Gradle dependency, model-bundle layout and
 * init/generate calls from qualcomm/ai-hub-apps → apps/geniex_chat_android, then fill this in.
 * If GenieX has no schema-constrained decoding, SchemaValidator's parse → repair → retry covers it.
 */
class GenieXLlm(@Suppress("unused") private val context: Context) : LlmEngine {

    override val name = "GenieX·NPU"

    override suspend fun load() {
        throw UnsupportedOperationException("GenieX not integrated yet (B4). Switch LLM to CPU in Settings.")
    }

    override suspend fun complete(system: String, user: String, jsonSchema: String?, maxTokens: Int): LlmResult {
        throw UnsupportedOperationException("GenieX not integrated yet (B4)")
    }

    override fun close() = Unit
}
