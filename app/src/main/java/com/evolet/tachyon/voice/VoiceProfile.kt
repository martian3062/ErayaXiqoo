package com.evolet.tachyon.voice

import android.content.Context
import com.evolet.tachyon.audio.RecorderService
import com.evolet.tachyon.audio.WavWriter
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class VoiceEnrollmentPrompt(
    val id: String,
    val question: String,
    val cue: String,
    @SerialName("target_seconds") val targetSeconds: Int = 40,
)

@Serializable
data class VoiceEnrollmentScript(
    val consent: String,
    @SerialName("target_minutes") val targetMinutes: Int = 10,
    val prompts: List<VoiceEnrollmentPrompt>,
) {
    companion object {
        fun load(context: Context): VoiceEnrollmentScript = context.assets
            .open("twin/voice_enrolment.json")
            .bufferedReader()
            .use { Json { ignoreUnknownKeys = true }.decodeFromString(serializer(), it.readText()) }
    }
}

@Serializable
data class VoiceClip(
    val id: String,
    val file: String,
    val question: String,
    val transcript: String,
    val durationMs: Long,
    val pitchHz: Float?,
)

@Serializable
data class VoiceProfile(
    val schema: Int = 1,
    val enrolledAt: Long,
    val totalDurationMs: Long,
    val clipCount: Int,
    val wordsPerMinute: Float,
    val medianPitchHz: Float?,
    val systemSpeechRate: Float,
    val systemPitch: Float,
    val consentAt: Long,
    val consentText: String,
    val consentTranscript: String,
    val consentSimilarity: Float,
    val referenceAudio: String,
    val referenceText: String,
    val cloneEngine: String? = null,
    val cloneStatus: String = "reference_ready",
)

/** Internal-only BIOMETRIC storage. Nothing here is included in app exports or Android backup. */
class VoiceProfileStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val directory = File(context.filesDir, "twin/voice").apply { mkdirs() }
    private val clipsDirectory = File(directory, "clips").apply { mkdirs() }
    private val profileFile = File(directory, "voice_profile.json")
    private val transcriptFile = File(directory, "transcript.jsonl")
    private val mutable = MutableStateFlow(load())

    val state: StateFlow<VoiceProfile?> = mutable.asStateFlow()

    fun consentFile(): File = File(directory, "consent.wav")

    fun writeConsent(pcm: ShortArray) {
        writeWav(consentFile(), pcm)
    }

    fun writeClip(index: Int, id: String, pcm: ShortArray): File {
        val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(clipsDirectory, "%02d_%s.wav".format(index + 1, safeId)).also { writeWav(it, pcm) }
    }

    fun save(profile: VoiceProfile, clips: List<VoiceClip>) {
        directory.mkdirs()
        clipsDirectory.mkdirs()
        transcriptFile.writeText(clips.joinToString("\n") { json.encodeToString(VoiceClip.serializer(), it) } + "\n")
        val pending = File(directory, "voice_profile.json.tmp")
        pending.writeText(json.encodeToString(VoiceProfile.serializer(), profile))
        if (profileFile.exists()) profileFile.delete()
        check(pending.renameTo(profileFile) || runCatching {
            pending.copyTo(profileFile, overwrite = true)
            pending.delete()
            true
        }.getOrDefault(false)) { "Could not store voice profile" }
        mutable.value = profile
    }

    fun wipe(): Int {
        val count = directory.takeIf(File::exists)?.walkTopDown()?.count { it.isFile } ?: 0
        directory.deleteRecursively()
        directory.mkdirs()
        clipsDirectory.mkdirs()
        mutable.value = null
        return count
    }

    fun refresh() {
        mutable.value = load()
    }

    private fun load(): VoiceProfile? = runCatching {
        json.decodeFromString(VoiceProfile.serializer(), profileFile.readText())
    }.getOrNull()

    private fun writeWav(file: File, pcm: ShortArray) {
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, "${file.name}.tmp")
        pending.writeBytes(WavWriter.encode(pcm, RecorderService.SAMPLE_RATE))
        if (file.exists()) file.delete()
        check(pending.renameTo(file) || runCatching {
            pending.copyTo(file, overwrite = true)
            pending.delete()
            true
        }.getOrDefault(false)) { "Could not store voice recording" }
    }
}

object ConsentVerifier {
    fun similarity(expected: String, actual: String): Float {
        val a = words(expected)
        val b = words(actual)
        if (a.isEmpty() || b.isEmpty()) return 0f
        val previous = IntArray(b.size + 1) { it }
        for (i in a.indices) {
            var diagonal = previous[0]
            previous[0] = i + 1
            for (j in b.indices) {
                val above = previous[j + 1]
                previous[j + 1] = minOf(
                    previous[j + 1] + 1,
                    previous[j] + 1,
                    diagonal + if (a[i] == b[j]) 0 else 1,
                )
                diagonal = above
            }
        }
        return (1f - previous[b.size].toFloat() / maxOf(a.size, b.size)).coerceIn(0f, 1f)
    }

    fun accepted(expected: String, actual: String, threshold: Float = 0.8f): Boolean =
        similarity(expected, actual) >= threshold

    private fun words(text: String): List<String> = text
        .lowercase()
        .replace(Regex("[^a-z0-9']+"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
}

data class VoiceClipAnalysis(val durationMs: Long, val wordCount: Int, val pitchHz: Float?)

object VoiceProfileAnalyzer {
    private const val SAMPLE_RATE = 16_000

    fun analyze(pcm: ShortArray, transcript: String): VoiceClipAnalysis = VoiceClipAnalysis(
        durationMs = pcm.size * 1_000L / SAMPLE_RATE,
        wordCount = transcript.trim().split(Regex("\\s+")).count(String::isNotBlank),
        pitchHz = medianPitch(pcm),
    )

    fun create(
        clips: List<VoiceClip>,
        consentText: String,
        consentTranscript: String,
        consentSimilarity: Float,
        enrolledAt: Long = System.currentTimeMillis(),
    ): VoiceProfile {
        val totalMs = clips.sumOf(VoiceClip::durationMs)
        val wordCount = clips.sumOf { it.transcript.trim().split(Regex("\\s+")).count(String::isNotBlank) }
        val wpm = if (totalMs > 0) wordCount * 60_000f / totalMs else 0f
        val pitches = clips.mapNotNull(VoiceClip::pitchHz).sorted()
        val median = pitches.takeIf(List<Float>::isNotEmpty)?.let { it[it.size / 2] }
        val rate = (wpm / 145f).coerceIn(0.82f, 1.18f).takeIf { wpm > 0f } ?: 1f
        val pitch = median?.let { (it / 165f).coerceIn(0.90f, 1.10f) } ?: 1f
        return VoiceProfile(
            enrolledAt = enrolledAt,
            totalDurationMs = totalMs,
            clipCount = clips.size,
            wordsPerMinute = wpm,
            medianPitchHz = median,
            systemSpeechRate = rate,
            systemPitch = pitch,
            consentAt = enrolledAt,
            consentText = consentText,
            consentTranscript = consentTranscript,
            consentSimilarity = consentSimilarity,
            referenceAudio = "consent.wav",
            referenceText = consentText,
        )
    }

    /** Lightweight autocorrelation estimate used only for TTS prosody defaults, not identification. */
    internal fun medianPitch(pcm: ShortArray): Float? {
        if (pcm.size < SAMPLE_RATE / 2) return null
        val frameSize = 640
        val hop = 320
        val minLag = SAMPLE_RATE / 350
        val maxLag = SAMPLE_RATE / 75
        val estimates = ArrayList<Float>()
        var start = 0
        while (start + frameSize <= pcm.size && estimates.size < 200) {
            var energy = 0.0
            for (i in 0 until frameSize) {
                val window = 0.5 - 0.5 * cos(2.0 * PI * i / (frameSize - 1))
                val sample = pcm[start + i] * window
                energy += sample * sample
            }
            if (energy / frameSize > 250_000.0) {
                var bestLag = 0
                var best = 0.0
                for (lag in minLag..maxLag) {
                    var corr = 0.0
                    var left = 0.0
                    var right = 0.0
                    for (i in lag until frameSize) {
                        val a = pcm[start + i].toDouble()
                        val b = pcm[start + i - lag].toDouble()
                        corr += a * b
                        left += a * a
                        right += b * b
                    }
                    val normalised = corr / kotlin.math.sqrt(left * right).coerceAtLeast(1.0)
                    if (normalised > best) {
                        best = normalised
                        bestLag = lag
                    }
                }
                if (bestLag > 0 && best >= 0.55) estimates += SAMPLE_RATE.toFloat() / bestLag
            }
            start += hop
        }
        if (estimates.isEmpty()) return null
        estimates.sort()
        return estimates[estimates.size / 2]
    }

    internal fun sineWave(hz: Float, seconds: Float = 1f): ShortArray = ShortArray((SAMPLE_RATE * seconds).roundToInt()) { i ->
        (sin(2.0 * PI * hz * i / SAMPLE_RATE) * 8_000).roundToInt().toShort()
    }
}
