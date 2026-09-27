package com.evolet.tachyon.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceProfileTest {
    @Test
    fun consentSimilarity_acceptsPunctuationAndSmallAsrDifference() {
        val expected = "I am recording my own voice to create a private voice model for my personal use on this phone."
        val actual = "I am recording my own voice to create a private voice model for personal use on this phone"

        assertTrue(ConsentVerifier.accepted(expected, actual))
        assertTrue(ConsentVerifier.similarity(expected, actual) >= 0.8f)
    }

    @Test
    fun consentSimilarity_rejectsUnrelatedSentence() {
        assertFalse(ConsentVerifier.accepted("I consent to recording my own voice", "The weather is warm today"))
    }

    @Test
    fun pitchEstimator_tracksSimpleVoicedSignal() {
        val pitch = VoiceProfileAnalyzer.medianPitch(VoiceProfileAnalyzer.sineWave(180f, 1.2f))

        assertTrue(pitch != null)
        assertEquals(180f, pitch!!, 8f)
    }

    @Test
    fun profileUsesOnlyRecordedClipStatistics() {
        val clips = listOf(
            VoiceClip("a", "clips/a.wav", "A", "one two three four five", 2_000, 150f),
            VoiceClip("b", "clips/b.wav", "B", "six seven eight nine ten", 2_000, 170f),
        )

        val profile = VoiceProfileAnalyzer.create(clips, "consent", "consent", 1f, enrolledAt = 123L)

        assertEquals(4_000, profile.totalDurationMs)
        assertEquals(2, profile.clipCount)
        assertEquals(150f, profile.wordsPerMinute, 0.1f)
        assertEquals(170f, profile.medianPitchHz!!, 0.1f)
        assertEquals("consent.wav", profile.referenceAudio)
    }
}
