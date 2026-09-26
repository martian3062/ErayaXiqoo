package com.evolet.tachyon.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Voice Tier C (INTEGRATIONSv2.md §6.4): Android's own TTS with an Indian-English voice. Not a clone —
 * the UI labels it "AI voice" whenever it speaks. Works offline only if the voice data is installed
 * (Settings → Text-to-speech); if no engine is available, [available] stays false and callers skip it.
 */
class SystemVoice(context: Context) {
    @Volatile var available = false
        private set
    @Volatile var speaking = false
        private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.forLanguageTag("en-IN"))
            available = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED ||
                tts.setLanguage(Locale.ENGLISH).let { it != TextToSpeech.LANG_MISSING_DATA && it != TextToSpeech.LANG_NOT_SUPPORTED }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { speaking = true }
                override fun onDone(id: String?) { speaking = false }
                @Deprecated("required override") override fun onError(id: String?) { speaking = false }
            })
        }
    }

    val name get() = "SystemTTS·${tts.defaultEngine ?: "none"}"

    fun speak(text: String) {
        if (!available) return
        speaking = true
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts.stop()
        speaking = false
    }

    fun shutdown() = tts.shutdown()
}
