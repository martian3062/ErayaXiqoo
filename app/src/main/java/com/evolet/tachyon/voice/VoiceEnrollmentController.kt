package com.evolet.tachyon.voice

import android.content.Context
import com.evolet.tachyon.EngineRegistry
import com.evolet.tachyon.audio.AnswerRecorder
import com.evolet.tachyon.audio.RecorderService
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VoiceEnrollmentStage {
    INTRO,
    CONSENT,
    RECORDING_CONSENT,
    VERIFYING_CONSENT,
    QUESTIONS,
    RECORDING_ANSWER,
    TRANSCRIBING_ANSWER,
    READY_TO_BUILD,
    COMPLETE,
    ERROR,
}

data class VoiceEnrollmentUi(
    val stage: VoiceEnrollmentStage = VoiceEnrollmentStage.INTRO,
    val script: VoiceEnrollmentScript? = null,
    val promptIndex: Int = 0,
    val completedPrompts: Int = 0,
    val totalDurationMs: Long = 0,
    val liveDurationMs: Long = 0,
    val level: Float = 0f,
    val lastTranscript: String = "",
    val consentTranscript: String = "",
    val consentSimilarity: Float = 0f,
    val profile: VoiceProfile? = null,
    val error: String? = null,
) {
    val targetDurationMs: Long get() = (script?.targetMinutes ?: 10) * 60_000L
    val currentPrompt: VoiceEnrollmentPrompt?
        get() = script?.prompts?.getOrNull(promptIndex)
            ?: script?.prompts?.getOrNull((promptIndex - script.prompts.size).mod(script.prompts.size))
    val progress: Float get() = (totalDurationMs.toFloat() / targetDurationMs).coerceIn(0f, 1f)
}

/**
 * Records the owner's own voice in aligned, private WAV/transcript pairs. A real clone is never
 * enabled merely because audio exists: the profile records that a reference is ready, while the
 * output engine remains the explicitly labelled system fallback until a local clone model exists.
 */
class VoiceEnrollmentController(
    context: Context,
    private val scope: CoroutineScope,
    private val engines: EngineRegistry,
    private val store: VoiceProfileStore,
    private val voice: SystemVoice,
) {
    private val script = VoiceEnrollmentScript.load(context)
    private val consentRecorder = AnswerRecorder(silenceMs = 2_000, maxMs = 30_000)
    private val answerRecorder = AnswerRecorder(silenceMs = 3_000, maxMs = 75_000)
    private val mutable = MutableStateFlow(
        VoiceEnrollmentUi(
            stage = if (store.state.value == null) VoiceEnrollmentStage.INTRO else VoiceEnrollmentStage.COMPLETE,
            script = script,
            profile = store.state.value,
        ),
    )
    val ui: StateFlow<VoiceEnrollmentUi> = mutable.asStateFlow()

    private val clips = mutableListOf<VoiceClip>()
    @Volatile private var stopRequested = false

    fun begin() {
        if (mutable.value.stage != VoiceEnrollmentStage.INTRO && mutable.value.stage != VoiceEnrollmentStage.ERROR) return
        clips.clear()
        mutable.value = VoiceEnrollmentUi(stage = VoiceEnrollmentStage.CONSENT, script = script)
    }

    fun recordConsent() {
        if (mutable.value.stage != VoiceEnrollmentStage.CONSENT) return
        voice.stop()
        stopRequested = false
        mutable.update { it.copy(stage = VoiceEnrollmentStage.RECORDING_CONSENT, error = null, liveDurationMs = 0, level = 0f) }
        scope.launch {
            val pcm = consentRecorder.record(
                onLevel = { level -> mutable.update { it.copy(level = level) } },
                onElapsedMs = { elapsed -> mutable.update { it.copy(liveDurationMs = elapsed) } },
                stop = { stopRequested },
            )
            mutable.update { it.copy(level = 0f) }
            if (pcm == null) {
                mutable.update { it.copy(stage = VoiceEnrollmentStage.CONSENT, error = "I couldn't hear the consent sentence. Please try again close to the microphone.") }
                return@launch
            }
            mutable.update { it.copy(stage = VoiceEnrollmentStage.VERIFYING_CONSENT) }
            val transcript = try {
                engines.asr().transcribe(pcm, "en").text.trim()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(stage = VoiceEnrollmentStage.CONSENT, error = "Consent transcription failed: ${e.message}") }
                return@launch
            }
            val similarity = ConsentVerifier.similarity(script.consent, transcript)
            if (similarity < CONSENT_THRESHOLD) {
                mutable.update {
                    it.copy(
                        stage = VoiceEnrollmentStage.CONSENT,
                        consentTranscript = transcript,
                        consentSimilarity = similarity,
                        error = "The sentence did not match closely enough (${(similarity * 100).toInt()}%). Read the displayed sentence exactly.",
                    )
                }
                return@launch
            }
            store.writeConsent(pcm)
            mutable.update {
                it.copy(
                    stage = VoiceEnrollmentStage.QUESTIONS,
                    consentTranscript = transcript,
                    consentSimilarity = similarity,
                    liveDurationMs = 0,
                    error = null,
                )
            }
        }
    }

    fun recordAnswer() {
        val state = mutable.value
        if (state.stage != VoiceEnrollmentStage.QUESTIONS) return
        val prompt = state.currentPrompt ?: return
        voice.stop()
        stopRequested = false
        mutable.update { it.copy(stage = VoiceEnrollmentStage.RECORDING_ANSWER, error = null, liveDurationMs = 0, level = 0f) }
        scope.launch {
            val pcm = answerRecorder.record(
                onLevel = { level -> mutable.update { it.copy(level = level) } },
                onElapsedMs = { elapsed -> mutable.update { it.copy(liveDurationMs = elapsed) } },
                stop = { stopRequested },
            )
            mutable.update { it.copy(level = 0f) }
            if (pcm == null) {
                mutable.update { it.copy(stage = VoiceEnrollmentStage.QUESTIONS, error = "No clear speech was captured. Try again in a quieter place.") }
                return@launch
            }
            mutable.update { it.copy(stage = VoiceEnrollmentStage.TRANSCRIBING_ANSWER) }
            val transcript = try {
                engines.asr().transcribe(pcm, null).text.trim()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(stage = VoiceEnrollmentStage.QUESTIONS, error = "Transcription failed: ${e.message}") }
                return@launch
            }
            if (transcript.isBlank()) {
                mutable.update { it.copy(stage = VoiceEnrollmentStage.QUESTIONS, error = "The recording had no usable transcript. Please repeat this answer.") }
                return@launch
            }
            val index = clips.size
            val file = store.writeClip(index, prompt.id, pcm)
            val analysis = VoiceProfileAnalyzer.analyze(pcm, transcript)
            clips += VoiceClip(
                id = prompt.id,
                file = relativeVoicePath(file),
                question = prompt.question,
                transcript = transcript,
                durationMs = analysis.durationMs,
                pitchHz = analysis.pitchHz,
            )
            val newDuration = clips.sumOf(VoiceClip::durationMs)
            val targetReached = newDuration >= state.targetDurationMs && clips.size >= MIN_CLIPS
            mutable.update {
                it.copy(
                    stage = if (targetReached) VoiceEnrollmentStage.READY_TO_BUILD else VoiceEnrollmentStage.QUESTIONS,
                    promptIndex = it.promptIndex + 1,
                    completedPrompts = clips.size,
                    totalDurationMs = newDuration,
                    liveDurationMs = 0,
                    lastTranscript = transcript,
                    error = null,
                )
            }
        }
    }

    fun stopRecording() {
        stopRequested = true
    }

    fun buildProfile() {
        val state = mutable.value
        if (state.stage != VoiceEnrollmentStage.READY_TO_BUILD || clips.size < MIN_CLIPS || state.totalDurationMs < state.targetDurationMs) return
        val profile = VoiceProfileAnalyzer.create(
            clips = clips,
            consentText = script.consent,
            consentTranscript = state.consentTranscript,
            consentSimilarity = state.consentSimilarity,
        )
        runCatching { store.save(profile, clips) }
            .onSuccess {
                mutable.update { current -> current.copy(stage = VoiceEnrollmentStage.COMPLETE, profile = profile, error = null) }
            }
            .onFailure { error ->
                mutable.update { current -> current.copy(stage = VoiceEnrollmentStage.ERROR, error = "Could not save voice profile: ${error.message}") }
            }
    }

    fun preview() {
        voice.speak("Hi. This is ERAYA using the private voice settings learned from your recording.")
    }

    fun restart() {
        stopRequested = true
        voice.stop()
        store.wipe()
        clips.clear()
        mutable.value = VoiceEnrollmentUi(stage = VoiceEnrollmentStage.INTRO, script = script)
    }

    fun cancelRecording() {
        stopRequested = true
        val next = if (store.state.value == null) VoiceEnrollmentStage.INTRO else VoiceEnrollmentStage.COMPLETE
        mutable.update { it.copy(stage = next, level = 0f, liveDurationMs = 0, profile = store.state.value) }
    }

    fun refreshAfterExternalWipe() {
        store.refresh()
        clips.clear()
        mutable.value = VoiceEnrollmentUi(
            stage = if (store.state.value == null) VoiceEnrollmentStage.INTRO else VoiceEnrollmentStage.COMPLETE,
            script = script,
            profile = store.state.value,
        )
    }

    private fun relativeVoicePath(file: File): String = "clips/${file.name}"

    companion object {
        const val CONSENT_THRESHOLD = 0.8f
        private const val MIN_CLIPS = 8
    }
}
