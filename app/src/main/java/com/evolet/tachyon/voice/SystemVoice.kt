package com.evolet.tachyon.voice

import android.content.Context
import android.media.AudioFormat
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Voice Tier C (INTEGRATIONSv2.md §6.4): Android's own TTS with an Indian-English voice. Not a clone —
 * the UI labels it "AI voice" whenever it speaks. Works offline only if the voice data is installed
 * (Settings → Text-to-speech); if no engine is available, [available] stays false and callers skip it.
 */
class SystemVoice(
    context: Context,
    private val profile: () -> VoiceProfile? = { null },
) {
    @Volatile var available = false
        private set
    private val _speakingState = MutableStateFlow(false)
    val speakingState: StateFlow<Boolean> = _speakingState.asStateFlow()
    val speaking: Boolean get() = _speakingState.value
    private val _mouthLevel = MutableStateFlow(0f)
    val mouthLevel: StateFlow<Float> = _mouthLevel.asStateFlow()
    @Volatile private var synthesisFormat = AudioFormat.ENCODING_PCM_16BIT

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.forLanguageTag("en-IN"))
            available = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED ||
                tts.setLanguage(Locale.ENGLISH).let { it != TextToSpeech.LANG_MISSING_DATA && it != TextToSpeech.LANG_NOT_SUPPORTED }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    _speakingState.value = true
                    _mouthLevel.value = 0.12f
                }

                override fun onBeginSynthesis(id: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
                    synthesisFormat = audioFormat
                }

                override fun onAudioAvailable(id: String?, audio: ByteArray?) {
                    if (audio == null || audio.isEmpty()) return
                    _mouthLevel.value = audioLevel(audio, synthesisFormat)
                }

                override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                    if (_mouthLevel.value < 0.18f) _mouthLevel.value = 0.42f
                }

                override fun onDone(id: String?) = finishSpeaking()
                @Deprecated("required override") override fun onError(id: String?) = finishSpeaking()
                override fun onError(id: String?, errorCode: Int) = finishSpeaking()
                override fun onStop(id: String?, interrupted: Boolean) = finishSpeaking()
            })
        }
    }

    val name get() = if (profile() == null) {
        "SystemTTS·${tts.defaultEngine ?: "none"}"
    } else {
        "Personalized SystemTTS·${tts.defaultEngine ?: "none"}"
    }

    fun speak(text: String): Boolean {
        if (!available || text.isBlank()) return false
        profile()?.let {
            tts.setSpeechRate(it.systemSpeechRate.coerceIn(0.75f, 1.25f))
            tts.setPitch(it.systemPitch.coerceIn(0.85f, 1.15f))
        } ?: run {
            tts.setSpeechRate(1f)
            tts.setPitch(1f)
        }
        _speakingState.value = true
        _mouthLevel.value = 0.12f
        val accepted = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString()) != TextToSpeech.ERROR
        if (!accepted) finishSpeaking()
        return accepted
    }

    fun stop() {
        tts.stop()
        finishSpeaking()
    }

    fun shutdown() = tts.shutdown()

    private fun finishSpeaking() {
        _speakingState.value = false
        _mouthLevel.value = 0f
    }

    private fun audioLevel(audio: ByteArray, format: Int): Float {
        if (format != AudioFormat.ENCODING_PCM_16BIT || audio.size < 2) return 0.55f
        var sum = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < audio.size) {
            val sample = ((audio[i].toInt() and 0xff) or (audio[i + 1].toInt() shl 8)).toShort().toInt()
            sum += sample.toDouble() * sample
            samples++
            i += 2
        }
        if (samples == 0) return 0f
        val normalised = (sqrt(sum / samples) / 7_500.0).toFloat().coerceIn(0f, 1f)
        return sqrt(normalised).coerceIn(0.05f, 1f)
    }
}
