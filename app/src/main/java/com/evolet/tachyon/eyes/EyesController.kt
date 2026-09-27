package com.evolet.tachyon.eyes

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class EyesStage { READY, SCANNING, REVIEW, ERROR }

data class EyesUi(
    val stage: EyesStage = EyesStage.READY,
    val imageFile: File? = null,
    val text: String = "",
    val detectedLines: Int = 0,
    val error: String? = null,
)

/**
 * F11 Eyes: a private one-shot photo is OCR'd with the bundled on-device Latin model.
 * Nothing is submitted to the commitment pipeline until the owner reviews and taps Propose.
 */
class EyesController(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "eyes").apply { mkdirs() }
    private val mutable = MutableStateFlow(EyesUi())
    val ui: StateFlow<EyesUi> = mutable.asStateFlow()

    fun newCapture(): Pair<File, Uri> {
        discardFiles()
        val file = File(directory, "note-${System.currentTimeMillis()}.jpg").apply { createNewFile() }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
        mutable.value = EyesUi(imageFile = file)
        return file to uri
    }

    fun scan(file: File) {
        if (mutable.value.stage == EyesStage.SCANNING) return
        scope.launch {
            mutable.value = EyesUi(stage = EyesStage.SCANNING, imageFile = file)
            try {
                require(file.canonicalFile.parentFile == directory.canonicalFile && file.isFile && file.length() > 0L) {
                    "The camera did not return a usable photo"
                }
                val lines = recognize(file)
                val clean = EyesTextCleaner.clean(lines)
                require(clean.isNotBlank()) {
                    "No readable text was found. Fill the frame, use even light, and retake the photo."
                }
                mutable.value = EyesUi(
                    stage = EyesStage.REVIEW,
                    imageFile = file,
                    text = clean,
                    detectedLines = clean.lineSequence().count(),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutable.value = EyesUi(
                    stage = EyesStage.ERROR,
                    imageFile = file.takeIf(File::isFile),
                    error = error.message ?: "Text recognition failed",
                )
            }
        }
    }

    fun updateText(value: String) {
        val current = mutable.value
        if (current.stage == EyesStage.REVIEW) {
            mutable.value = current.copy(text = value.take(MAX_TEXT_CHARS), error = null)
        }
    }

    /** Returns owner-reviewed text and immediately removes the source photo. */
    fun consumeForProposal(): String? {
        val current = mutable.value
        if (current.stage != EyesStage.REVIEW) return null
        val clean = EyesTextCleaner.clean(current.text.lineSequence().toList())
        if (clean.isBlank()) {
            mutable.value = current.copy(error = "Keep at least one readable line before proposing commitments.")
            return null
        }
        discardFiles()
        mutable.value = EyesUi()
        return clean
    }

    fun discard() {
        discardFiles()
        mutable.value = EyesUi()
    }

    fun wipe(): Int {
        val count = directory.listFiles()?.size ?: 0
        directory.deleteRecursively()
        directory.mkdirs()
        mutable.value = EyesUi()
        return count
    }

    private suspend fun recognize(file: File): List<String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            val image = runCatching { InputImage.fromFilePath(appContext, Uri.fromFile(file)) }.getOrElse {
                recognizer.close()
                continuation.resumeWithException(it)
                return@suspendCancellableCoroutine
            }
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        continuation.resume(result.textBlocks.flatMap { block -> block.lines.map { it.text } })
                    }
                }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                .addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
                .addOnCompleteListener { recognizer.close() }
            continuation.invokeOnCancellation { recognizer.close() }
        }
    }

    private fun discardFiles() {
        directory.listFiles()?.filter { it.isFile }?.forEach(File::delete)
    }

    companion object {
        const val MAX_TEXT_CHARS = 12_000
    }
}

/** Deterministic cleanup keeps OCR evidence legible and prevents repeated adjacent lines. */
object EyesTextCleaner {
    fun clean(lines: List<String>): String {
        val output = ArrayList<String>()
        for (raw in lines) {
            val line = raw
                .replace(Regex("[\\t\\r ]+"), " ")
                .trim()
                .takeIf(String::isNotBlank)
                ?: continue
            if (!line.equals(output.lastOrNull(), ignoreCase = true)) output += line
            if (output.sumOf { it.length + 1 } >= EyesController.MAX_TEXT_CHARS) break
        }
        return output.joinToString("\n").take(EyesController.MAX_TEXT_CHARS).trim()
    }
}
