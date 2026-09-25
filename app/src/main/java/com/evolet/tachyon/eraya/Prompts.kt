package com.evolet.tachyon.eraya

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Loads the frozen prompt + schema from assets. For prompt tuning on the phone (B3), a file in
 * /sdcard/Android/data/com.evolet.tachyon/files/prompts/ overrides the asset without a rebuild.
 * It's read on every extraction, so edits apply to the next run.
 */
class Prompts(private val context: Context) {

    val overrideDir: File? get() = context.getExternalFilesDir("prompts")

    fun system(today: LocalDate): String = read(SYSTEM)
        .replace("{TODAY}", today.toString())
        .replace("{WEEKDAY}", today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))

    fun schema(): String = read(SCHEMA)

    private fun read(name: String): String {
        overrideDir?.let { File(it, name) }?.takeIf { it.isFile }?.let { return it.readText() }
        return context.assets.open("prompts/$name").bufferedReader().use { it.readText() }
    }

    private companion object {
        const val SYSTEM = "extract_system.txt"
        const val SCHEMA = "schema.json"
    }
}
