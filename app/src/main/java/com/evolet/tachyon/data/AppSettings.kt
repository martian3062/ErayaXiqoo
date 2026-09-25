package com.evolet.tachyon.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Tier { NPU, FALLBACK }

data class SettingsState(
    val asrTier: Tier = Tier.FALLBACK,   // Tier 2 is the guarantee; switch to NPU once B4 lands
    val llmTier: Tier = Tier.FALLBACK,
    val language: String = LANG_AUTO,    // auto | en | hi → whisper language hint
)

const val LANG_AUTO = "auto"

/** Engine and language choices. SharedPreferences-backed so a switch applies without a restart (F8). */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("tachyon", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun update(transform: (SettingsState) -> SettingsState) {
        val next = transform(_state.value)
        prefs.edit {
            putString(K_ASR, next.asrTier.name)
            putString(K_LLM, next.llmTier.name)
            putString(K_LANG, next.language)
        }
        _state.value = next
    }

    private fun read() = SettingsState(
        asrTier = tier(prefs.getString(K_ASR, null)),
        llmTier = tier(prefs.getString(K_LLM, null)),
        language = prefs.getString(K_LANG, null) ?: LANG_AUTO,
    )

    private fun tier(v: String?) = Tier.entries.firstOrNull { it.name == v } ?: Tier.FALLBACK

    private companion object {
        const val K_ASR = "asr_tier"
        const val K_LLM = "llm_tier"
        const val K_LANG = "language"
    }
}
