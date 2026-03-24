package com.patrick.lrcreader.core.light

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightCueAutoGeneratorTest {

    @Test
    fun generate_allStylesHaveNoStrobeAndEndWithBlackout() {
        LightCueAutoGenerator.Style.entries.forEach { style ->
            val cues = LightCueAutoGenerator.generate(
                durationMs = 90_000L,
                style = style
            )

            assertFalse(cues.isEmpty())
            assertEquals(0L, cues.first().timeMs)
            assertTrue(cues.none { cue -> cue.action is LightAction.Strobe })
            assertTrue(cues.last().action is LightAction.Blackout)
            assertTrue(cues.zipWithNext().all { (left, right) -> left.timeMs < right.timeMs })
        }
    }

    @Test
    fun generate_isDeterministicForSameInputs() {
        val cues = LightCueAutoGenerator.generate(
            durationMs = 120_000L,
            style = LightCueAutoGenerator.Style.ENERGETIC
        )
        val repeated = LightCueAutoGenerator.generate(
            durationMs = 120_000L,
            style = LightCueAutoGenerator.Style.ENERGETIC
        )

        assertEquals(cues, repeated)
    }

    @Test
    fun generate_softAndEnergetic_reflectDifferentDynamics() {
        val softCues = LightCueAutoGenerator.generate(
            durationMs = 120_000L,
            style = LightCueAutoGenerator.Style.SOFT
        )
        val energeticCues = LightCueAutoGenerator.generate(
            durationMs = 120_000L,
            style = LightCueAutoGenerator.Style.ENERGETIC
        )

        val softColorCues = softCues.filter { cue -> cue.action is LightAction.Color }
        val energeticColorCues = energeticCues.filter { cue -> cue.action is LightAction.Color }

        assertTrue(softColorCues.all { cue -> cue.intensity <= 0.5f })
        assertTrue(energeticColorCues.any { cue -> cue.intensity >= 0.8f })
        assertTrue(softColorCues.any { cue -> cue.fadeMs >= 5_000L })
        assertTrue(energeticColorCues.any { cue -> cue.fadeMs <= 1_600L })
    }
}
