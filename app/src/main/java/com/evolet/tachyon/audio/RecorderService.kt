package com.evolet.tachyon.audio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.evolet.tachyon.MainActivity
import com.evolet.tachyon.R
import com.evolet.tachyon.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground mic service (type=microphone). Reads 16 kHz mono PCM and hands 30 s chunks to
 * SessionController, which transcribes each one while recording carries on (F1, F2).
 */
class RecorderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Must happen within 5 s of startForegroundService.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopRecording() else startRecording()
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by the UI before starting the service
    private fun startRecording() {
        if (job != null) return
        val session = container.session
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, SAMPLE_RATE * 2), // ≥ 1 s of audio
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            session.onRecorderError("Microphone unavailable")
            shutdown()
            return
        }

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tachyon:recording")
            .apply { acquire(MAX_RECORDING_MS) }

        session.onRecordingStarted()
        job = scope.launch {
            val chunker = PcmChunker(SAMPLE_RATE, onChunk = session::onChunk)
            val frame = ShortArray(SAMPLE_RATE / 10) // 100 ms reads
            record.startRecording()
            try {
                while (isActive) {
                    val n = record.read(frame, 0, frame.size)
                    if (n > 0) {
                        chunker.push(frame, n)
                        session.onLevel(rmsLevel(frame, n))
                    } else if (n < 0) {
                        break
                    }
                }
            } finally {
                record.stop()
                record.release()
                chunker.flush()
                session.onRecordingStopped()
            }
        }
    }

    private fun stopRecording() {
        val running = job ?: return shutdown()
        scope.launch {
            running.cancelAndJoin()
            job = null
            shutdown()
        }
    }

    private fun shutdown() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecorderService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("Tachyon is listening")
            .setContentText("Audio stays on this phone")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    /** 0..1 loudness of one 100 ms frame, for the live waveform (sqrt ≈ perceived loudness). */
    private fun rmsLevel(frame: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) sum += frame[i].toDouble() * frame[i]
        return (kotlin.math.sqrt(kotlin.math.sqrt(sum / n) / 6000.0)).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val ACTION_STOP = "com.evolet.tachyon.STOP"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 42
        private const val MAX_RECORDING_MS = 30 * 60 * 1000L

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, RecorderService::class.java))

        fun stop(context: Context) {
            context.startService(Intent(context, RecorderService::class.java).setAction(ACTION_STOP))
        }
    }
}
