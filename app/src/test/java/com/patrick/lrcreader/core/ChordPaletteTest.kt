package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordPaletteTest {

    @Test
    fun parseChordPaletteInput_parsesAndDeduplicates() {
        val palette = parseChordPaletteInput("Am, D, G, F, D")
        assertEquals(listOf("Am", "D", "G", "F"), palette)
    }

    @Test
    fun inferChordPaletteFromText_extractsUniqueChordTokens() {
        val inferred = inferChordPaletteFromText("Am D G\nF C/G D")
        assertEquals(listOf("Am", "D", "G", "F", "C/G"), inferred)
    }

    @Test
    fun insertChordAtCursor_insertsAtSelection() {
        val result = insertChordAtCursor(
            text = "Am  G",
            selectionStart = 3,
            selectionEnd = 3,
            chord = "D"
        )
        assertEquals("Am D G", result.text)
        assertEquals(4, result.cursor)
    }

    @Test
    fun sortChordPaletteByUsage_sortsByDescendingFrequency() {
        val sorted = sortChordPaletteByUsage(
            palette = listOf("Am", "D", "G", "F"),
            rawText = "G Am G D G F"
        )
        assertEquals(listOf("G", "Am", "D", "F"), sorted)
    }

    @Test
    fun sortChordPaletteByUsage_keepsStableOrderOnTie() {
        val sorted = sortChordPaletteByUsage(
            palette = listOf("Am", "D", "G"),
            rawText = "D Am"
        )
        assertEquals(listOf("Am", "D", "G"), sorted)
    }

    @Test
    fun sortChordPaletteByUsage_keepsInitialOrderWhenNoMatch() {
        val sorted = sortChordPaletteByUsage(
            palette = listOf("Am", "D", "G"),
            rawText = "C F Bb"
        )
        assertEquals(listOf("Am", "D", "G"), sorted)
    }

    @Test
    fun sortChordPaletteByUsage_handlesEmptyPalette() {
        val sorted = sortChordPaletteByUsage(
            palette = emptyList(),
            rawText = "Am D G"
        )
        assertEquals(emptyList<String>(), sorted)
    }

    @Test
    fun captureLiveChord_createsExpectedLrcLine() {
        val line = captureLiveChord(
            chord = "G",
            playerPositionMs = 12_400L
        )
        assertEquals(12_250L, line.timeMs)
        assertEquals("G", line.text)
        assertEquals("[00:12.25] G", formatCapturedLiveChordLine(line))
    }

    @Test
    fun captureLiveChord_appliesCompensationAndClampsToZero() {
        val line = captureLiveChord(
            chord = "Am",
            playerPositionMs = 100L
        )
        assertEquals(0L, line.timeMs)
        assertEquals("Am", line.text)
    }

    @Test
    fun appendCapturedChordLineSorted_sortsTimelineByTimestamp() {
        val current = listOf(
            LrcLine(timeMs = 2_000L, text = "D"),
            LrcLine(timeMs = 5_000L, text = "G")
        )
        val updated = appendCapturedChordLineSorted(
            current = current,
            newLine = LrcLine(timeMs = 3_000L, text = "Am")
        )
        assertEquals(listOf(2_000L, 3_000L, 5_000L), updated.map { it.timeMs })
        assertEquals(listOf("D", "Am", "G"), updated.map { it.text })
    }

    @Test
    fun isLiveCaptureAllowed_ignoresTooFastDoubleClick() {
        assertFalse(isLiveCaptureAllowed(nowElapsedMs = 1_050L, lastCaptureElapsedMs = 1_000L))
        assertTrue(isLiveCaptureAllowed(nowElapsedMs = 1_080L, lastCaptureElapsedMs = 1_000L))
    }
}
