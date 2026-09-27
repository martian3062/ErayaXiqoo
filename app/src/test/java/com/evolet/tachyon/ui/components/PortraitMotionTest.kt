package com.evolet.tachyon.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitMotionTest {
    @Test
    fun portraitMouthStaysClosedWhenNotTalking() {
        assertEquals(0f, PortraitMotion.lipAmount(talking = false, speechLevel = 1f, syllablePulse = 1f))
    }

    @Test
    fun louderSpeechProducesMoreMouthMovement() {
        val quiet = PortraitMotion.lipAmount(talking = true, speechLevel = 0.1f, syllablePulse = 0.2f)
        val loud = PortraitMotion.lipAmount(talking = true, speechLevel = 0.9f, syllablePulse = 0.2f)
        assertTrue(loud > quiet)
    }

    @Test
    fun mouthMovementIsClampedToSafeRange() {
        assertEquals(0.08f, PortraitMotion.lipAmount(talking = true, speechLevel = -4f, syllablePulse = -2f))
        assertEquals(1f, PortraitMotion.lipAmount(talking = true, speechLevel = 4f, syllablePulse = 3f))
    }
}
