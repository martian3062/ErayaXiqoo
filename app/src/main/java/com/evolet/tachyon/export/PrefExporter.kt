package com.evolet.tachyon.export

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Writes the F15 JSONL to Downloads via MediaStore (no storage permission on API 29+). */
object PrefExporter {
    fun write(context: Context, jsonl: String): String {
        val name = "tachyon_prefs_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.jsonl"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/x-ndjson")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Downloads not available")
        resolver.openOutputStream(uri)!!.use { it.write(jsonl.toByteArray()) }
        return "Download/$name"
    }
}
