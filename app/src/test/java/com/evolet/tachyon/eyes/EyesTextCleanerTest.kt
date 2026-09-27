package com.evolet.tachyon.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EyesTextCleanerTest {
    @Test
    fun normalizesWhitespaceAndDropsAdjacentDuplicateLines() {
        val cleaned = EyesTextCleaner.clean(
            listOf("  Send   the report Friday  ", "send the report friday", "Call Alex at 4 PM"),
        )

        assertEquals("Send the report Friday\nCall Alex at 4 PM", cleaned)
    }

    @Test
    fun keepsSeparateEvidenceLinesAndCapsPromptSize() {
        val cleaned = EyesTextCleaner.clean(List(2_000) { "line-$it ${"x".repeat(20)}" })

        assertFalse(cleaned.contains("\t"))
        assertEquals(EyesController.MAX_TEXT_CHARS, cleaned.length)
    }
}
