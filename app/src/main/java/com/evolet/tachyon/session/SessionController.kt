package com.evolet.tachyon.session

import android.content.Context
import android.util.Log
import com.evolet.tachyon.EngineRegistry
import com.evolet.tachyon.audio.PcmChunker
import com.evolet.tachyon.audio.RecorderService
import com.evolet.tachyon.audio.SampleAudio
import com.evolet.tachyon.data.AppDb
import com.evolet.tachyon.data.AppSettings
import com.evolet.tachyon.data.Commitment
import com.evolet.tachyon.data.LANG_AUTO
import com.evolet.tachyon.data.Session
import com.evolet.tachyon.data.Status
import com.evolet.tachyon.eraya.ExtractionAgent
import com.evolet.tachyon.eraya.ExtractionResult
import com.evolet.tachyon.eraya.Proposal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

enum class Phase { IDLE, RECORDING, TRANSCRIBING, EXTRACTING, REVIEW, ERROR }

data class SessionUi(
    val phase: Phase = Phase.IDLE,
    val sessionId: String? = null,
    val startedAt: Long = 0,
    val fromSample: Boolean = false,
    val transcript: List<String> = emptyList(),
    val pendingChunks: Int = 0,
    val asrMs: Long = 0,
    val llmMs: Long = 0,
    val asrEngine: String? = null,
    val llmEngine: String? = null,
    val tokensPerSec: Double? = null,
    val proposals: Int = 0,
    val dropped: Int = 0,
    val error: String? = null,
    val canRetry: Boolean = false,
) {
    val busy get() = phase == Phase.RECORDING || phase == Phase.TRANSCRIBING || phase == Phase.EXTRACTING
}

/**
 * The record → transcribe → extract pipeline. Chunks and the final "stop" go through one channel
 * and are handled in order, so extraction always sees the complete transcript.
 */
class SessionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engines: EngineRegistry,
    private val extraction: ExtractionAgent,
    private val db: AppDb,
    private val settings: AppSettings,
) {
    private val _ui = MutableStateFlow(SessionUi())
    val ui: StateFlow<SessionUi> = _ui.asStateFlow()

    private sealed interface Work {
        class Chunk(val pcm: ShortArray) : Work
        data object Finish : Work
    }

    private val work = Channel<Work>(Channel.UNLIMITED)
    private val samples = AtomicLong()

    init {
        scope.launch {
            for (w in work) when (w) {
                is Work.Chunk -> transcribe(w.pcm)
                Work.Finish -> finish()
            }
        }
    }

    // --- called by RecorderService (or runSample) ---

    fun onRecordingStarted(fromSample: Boolean = false) {
        samples.set(0)
        _ui.value = SessionUi(
            phase = Phase.RECORDING,
            sessionId = UUID.randomUUID().toString(),
            startedAt = System.currentTimeMillis(),
            fromSample = fromSample,
        )
    }

    fun onChunk(pcm: ShortArray) {
        samples.addAndGet(pcm.size.toLong())
        _ui.update { it.copy(pendingChunks = it.pendingChunks + 1) }
        work.trySend(Work.Chunk(pcm))
    }

    fun onRecordingStopped() {
        _ui.update { it.copy(phase = Phase.TRANSCRIBING) }
        work.trySend(Work.Finish)
    }

    fun onRecorderError(message: String) {
        _ui.update { it.copy(phase = Phase.ERROR, error = message, canRetry = false) }
    }

    // --- called by the UI ---

    fun startRecording() {
        if (!_ui.value.busy) RecorderService.start(context)
    }

    fun stopRecording() = RecorderService.stop(context)

    fun retryExtraction() {
        val s = _ui.value
        if (s.phase == Phase.ERROR && s.canRetry) work.trySend(Work.Finish)
    }

    fun reset() {
        if (!_ui.value.busy) _ui.value = SessionUi()
    }

    /** Backup path (§15): feeds the sample WAV through exactly the same pipeline. */
    fun runSample() {
        if (_ui.value.busy) return
        scope.launch(Dispatchers.IO) {
            val pcm = try {
                SampleAudio.load(context)
            } catch (e: Exception) {
                _ui.value = SessionUi(phase = Phase.ERROR, fromSample = true, error = e.message)
                return@launch
            }
            onRecordingStarted(fromSample = true)
            PcmChunker(RecorderService.SAMPLE_RATE, onChunk = ::onChunk).apply {
                push(pcm)
                flush()
            }
            onRecordingStopped()
        }
    }

    // --- pipeline steps (run sequentially on the work channel) ---

    private suspend fun transcribe(pcm: ShortArray) {
        val asr = engines.asr()
        val hint = settings.state.value.language.takeUnless { it == LANG_AUTO }
        try {
            // One retry: losing a chunk silently drops 30 s of speech (seen on-device when whisper-server restarted).
            val r = try {
                asr.transcribe(pcm, hint)
            } catch (e: java.io.IOException) {
                Log.w(TAG, "ASR chunk failed, retrying once", e)
                asr.transcribe(pcm, hint)
            }
            _ui.update {
                it.copy(
                    transcript = if (r.text.isBlank()) it.transcript else it.transcript + r.text,
                    asrMs = it.asrMs + r.latencyMs,
                    asrEngine = asr.name,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "ASR failed", e)
            _ui.update { it.copy(asrEngine = asr.name, error = "ASR (${asr.name}) failed: ${e.message}") }
        } finally {
            _ui.update { it.copy(pendingChunks = (it.pendingChunks - 1).coerceAtLeast(0)) }
        }
    }

    private suspend fun finish() {
        val s = _ui.value
        val transcript = s.transcript.joinToString(" ").trim()
        if (transcript.isEmpty()) {
            _ui.update { it.copy(phase = Phase.ERROR, error = it.error ?: "Nothing was transcribed.", canRetry = false) }
            return
        }
        _ui.update { it.copy(phase = Phase.EXTRACTING, error = null) }

        when (val r = extraction.extract(engines.llm(), transcript)) {
            is ExtractionResult.Failed -> _ui.update {
                it.copy(
                    phase = Phase.ERROR, llmMs = r.latencyMs, llmEngine = r.engine, canRetry = true,
                    error = "Couldn't extract — tap to retry\n(${r.reason})",
                )
            }
            is ExtractionResult.Ok -> try {
                val id = s.sessionId ?: UUID.randomUUID().toString()
                db.sessionDao().insert(
                    Session(
                        id = id, startedAt = s.startedAt,
                        durationSec = (samples.get() / RecorderService.SAMPLE_RATE).toInt(),
                        transcript = transcript,
                        asrEngine = s.asrEngine ?: engines.asr().name, llmEngine = r.engine,
                        asrMs = s.asrMs, llmMs = r.latencyMs,
                    ),
                )
                db.commitmentDao().insertAll(r.items.map { it.toEntity(id) })
                _ui.update {
                    it.copy(
                        phase = Phase.REVIEW, sessionId = id, llmMs = r.latencyMs, llmEngine = r.engine,
                        tokensPerSec = r.tokensPerSec, proposals = r.items.size, dropped = r.dropped,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "saving proposals failed", e)
                _ui.update { it.copy(phase = Phase.ERROR, error = "Couldn't save proposals: ${e.message}", canRetry = true) }
            }
        }
    }

    private fun Proposal.toEntity(sessionId: String) = Commitment(
        id = UUID.randomUUID().toString(),
        sessionId = sessionId,
        owner = owner, task = task, toWhom = toWhom,
        deadlineText = deadlineText, deadlineIso = deadlineIso,
        evidence = evidence, confidence = confidence,
        status = Status.PROPOSED,
        decidedAt = null,
    )

    private companion object {
        const val TAG = "SessionController"
    }
}
