package com.evolet.tachyon.twin

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class InterviewDraftPhase { IN_PROGRESS, SUMMARISING, REVIEW }

@Serializable
data class InterviewDraftAnswer(
    val questionId: String,
    val text: String,
    val followUpText: String? = null,
)

@Serializable
data class InterviewDraftReview(
    val field: String,
    val value: String,
    val evidence: String,
    val confidence: Double,
    val status: ReviewStatus = ReviewStatus.PENDING,
    val finalValue: String? = null,
)

/** Versioned, private checkpoint for the behaviour interview. */
@Serializable
data class InterviewDraft(
    val version: Int = 1,
    val demo: Boolean,
    val questionIds: List<String>,
    val index: Int,
    val followUp: String? = null,
    val pendingMain: String? = null,
    val answerText: String = "",
    val answers: List<InterviewDraftAnswer> = emptyList(),
    val review: List<InterviewDraftReview> = emptyList(),
    val summarizedSections: Set<Int> = emptySet(),
    val droppedTraits: Int = 0,
    val answered: Int = 0,
    val phase: InterviewDraftPhase = InterviewDraftPhase.IN_PROGRESS,
)

/** Kept separate from Android I/O so draft compatibility is covered by local unit tests. */
object InterviewDraftCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun encode(draft: InterviewDraft): String = json.encodeToString(InterviewDraft.serializer(), draft)

    fun decode(raw: String): InterviewDraft? = runCatching {
        json.decodeFromString(InterviewDraft.serializer(), raw)
    }.getOrNull()?.takeIf { it.version == 1 }
}

/** Atomic internal-storage I/O. This file is removed by the existing Delete my twin action. */
class InterviewDraftStore(context: Context) {
    private val file = File(File(context.filesDir, "twin").apply { mkdirs() }, "interview_draft.json")
    private val atomic = AtomicFile(file)

    @Synchronized
    fun load(): InterviewDraft? = if (!file.isFile) null else runCatching {
        atomic.openRead().bufferedReader().use { InterviewDraftCodec.decode(it.readText()) }
    }.getOrNull()

    @Synchronized
    fun save(draft: InterviewDraft) {
        val stream = atomic.startWrite()
        try {
            stream.write(InterviewDraftCodec.encode(draft).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: Throwable) {
            atomic.failWrite(stream)
            throw e
        }
    }

    @Synchronized
    fun clear() {
        atomic.delete()
    }
}
