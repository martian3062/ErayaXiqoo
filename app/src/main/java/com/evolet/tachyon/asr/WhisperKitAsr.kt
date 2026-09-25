package com.evolet.tachyon.asr

import android.content.Context

/**
 * Tier 1 ASR on the NPU (WhisperKit Android, Qualcomm AI Hub). NOT INTEGRATED YET, this is block B4.
 *
 * Rule 6: mirror argmaxinc/WhisperKitAndroid's sample app exactly (Gradle dependency,
 * model download/paths, init and transcribe calls). Don't write this from memory.
 */
class WhisperKitAsr(@Suppress("unused") private val context: Context) : AsrEngine {

    override val name = "WhisperKit·NPU"

    override suspend fun load() {
        throw UnsupportedOperationException("WhisperKit not integrated yet (B4). Switch ASR to CPU in Settings.")
    }

    override suspend fun transcribe(pcm16k: ShortArray, languageHint: String?): AsrResult {
        throw UnsupportedOperationException("WhisperKit not integrated yet (B4)")
    }

    override fun close() = Unit
}
