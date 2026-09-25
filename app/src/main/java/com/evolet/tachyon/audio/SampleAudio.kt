package com.evolet.tachyon.audio

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * The backup input for the demo. A file pushed to
 * /sdcard/Android/data/com.evolet.tachyon/files/demo/sample_en_hi.wav wins over the bundled asset,
 * so the sample can be swapped from the phone without rebuilding.
 */
object SampleAudio {
    const val FILE_NAME = "sample_en_hi.wav"

    fun externalFile(context: Context): File? = context.getExternalFilesDir("demo")?.let { File(it, FILE_NAME) }

    fun load(context: Context): ShortArray {
        val ext = externalFile(context)
        val bytes = if (ext != null && ext.isFile) {
            ext.readBytes()
        } else {
            try {
                context.assets.open("demo/$FILE_NAME").use { it.readBytes() }
            } catch (e: IOException) {
                throw IOException("No sample recording. Push one to ${ext?.path} or bundle assets/demo/$FILE_NAME", e)
            }
        }
        return WavReader.toPcm16kMono(bytes)
    }
}
