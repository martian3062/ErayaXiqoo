package com.evolet.tachyon.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Records one interview answer in the foreground (no service needed): starts on the first speech,
 * ends after [silenceMs] of quiet, at [maxMs], or when [stop] returns true. Energy-based VAD.
 * Returns null if nobody spoke — whisper hallucinates on silence.
 */
class AnswerRecorder(
    private val silenceMs: Long = 2_500,
    private val maxMs: Long = 90_000,
    private val speechRms: Double = 700.0,
) {
    @SuppressLint("MissingPermission") // RECORD_AUDIO is granted on first launch
    suspend fun record(
        onLevel: (Float) -> Unit,
        onElapsedMs: (Long) -> Unit = {},
        stop: () -> Boolean,
    ): ShortArray? = withContext(Dispatchers.IO) {
        val sr = RecorderService.SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, sr * 2))
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return@withContext null
        }
        val frame = ShortArray(sr / 10)
        val out = ArrayList<Short>(sr * 20)
        var spoke = false
        var quietMs = 0L
        var totalMs = 0L
        rec.startRecording()
        try {
            while (coroutineContext.isActive && totalMs < maxMs && !stop()) {
                val n = rec.read(frame, 0, frame.size)
                if (n <= 0) break
                var sum = 0.0
                for (i in 0 until n) { sum += frame[i].toDouble() * frame[i]; out.add(frame[i]) }
                val rms = sqrt(sum / n)
                onLevel((sqrt(rms / 6000.0)).toFloat().coerceIn(0f, 1f))
                totalMs += 100
                onElapsedMs(totalMs)
                if (rms > speechRms) { spoke = true; quietMs = 0 } else if (spoke) quietMs += 100
                if (spoke && quietMs >= silenceMs) break
            }
        } finally {
            rec.stop()
            rec.release()
            onLevel(0f)
        }
        if (!spoke) null else out.toShortArray()
    }
}
