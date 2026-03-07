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

    @Test
    fun liveCaptureSequence_filtersFastTapAndKeepsSortedTimeline() {
        var lastCaptureElapsedMs: Long? = null
        var timeline = emptyList<LrcLine>()

        fun captureTap(chord: String, elapsedMs: Long, playerPositionMs: Long) {
            if (!isLiveCaptureAllowed(elapsedMs, lastCaptureElapsedMs)) return
            lastCaptureElapsedMs = elapsedMs
            timeline = appendCapturedChordLineSorted(
                current = timeline,
                newLine = captureLiveChord(chord = chord, playerPositionMs = playerPositionMs)
            )
        }

        captureTap(chord = "G", elapsedMs = 1_000L, playerPositionMs = 5_000L)   // kept
        captureTap(chord = "Am", elapsedMs = 1_050L, playerPositionMs = 5_200L)  // ignored (<80ms)
        captureTap(chord = "F", elapsedMs = 1_080L, playerPositionMs = 5_100L)   // kept (==80ms)
        captureTap(chord = "C", elapsedMs = 1_200L, playerPositionMs = 4_900L)   // kept, inserted before

        assertEquals(listOf(4_750L, 4_850L, 4_950L), timeline.map { it.timeMs })
        assertEquals(listOf("C", "G", "F"), timeline.map { it.text })
    }
}
