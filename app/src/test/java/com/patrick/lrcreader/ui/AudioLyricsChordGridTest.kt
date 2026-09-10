package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLyricsChordGridTest {

    @Test
    fun derivesOnlyChordsInSourceOrderAndKeepsRepeatedOccurrences() {
        val result = deriveAudioLyricsChordGrid(
            lyricsLines = listOf(
                LrcLine(12_500L, "Je [Am]pars [Am]ce [F]soir")
            ),
            transposeSemitones = 0
        )

        assertEquals(listOf(LrcLine(12_500L, "Am   Am   F")), result)
    }

    @Test
    fun excludesLyricsLinesWithoutRecognizedChordsAndIgnoresRichFormatting() {
        val result = deriveAudioLyricsChordGrid(
            lyricsLines = listOf(
                LrcLine(1_000L, "Je pars **ce soir**"),
                LrcLine(2_000L, "<c=red>Je</c> [G]pars"),
                LrcLine(3_000L, "[Couplet] texte")
            ),
            transposeSemitones = 0
        )

        assertEquals(listOf(LrcLine(2_000L, "G")), result)
    }

    @Test
    fun supportsChordOnlyAndUntimedLines() {
        val result = deriveAudioLyricsChordGrid(
            lyricsLines = listOf(LrcLine(0L, "[Am] [F] [G]")),
            transposeSemitones = 0
        )

        assertEquals(listOf(LrcLine(0L, "Am   F   G")), result)
        assertEquals(-1, findActiveAudioLyricsChordGridIndex(result, 50_000L))
    }

    @Test
    fun transposesDisplayWithoutChangingLyricsSource() {
        val source = listOf(LrcLine(4_000L, "[Am] [F]"))

        val result = deriveAudioLyricsChordGrid(source, transposeSemitones = 2)

        assertEquals(listOf(LrcLine(4_000L, "Bm   G")), result)
        assertEquals(listOf(LrcLine(4_000L, "[Am] [F]")), source)
    }

    @Test
    fun timedDerivedLinesUseTheirOwnTimelineAndIgnoreUntimedLines() {
        val lines = listOf(
            LrcLine(0L, "Am"),
            LrcLine(10_000L, "F"),
            LrcLine(0L, "G"),
            LrcLine(20_000L, "C")
        )

        assertEquals(-1, findActiveAudioLyricsChordGridIndex(lines, 9_999L))
        assertEquals(1, findActiveAudioLyricsChordGridIndex(lines, 10_000L))
        assertEquals(1, findActiveAudioLyricsChordGridIndex(lines, 19_999L))
        assertEquals(3, findActiveAudioLyricsChordGridIndex(lines, 20_000L))
    }

    @Test
    fun fallsBackToLegacyOnlyWhenNoDerivedChordExists() {
        val legacy = listOf(LrcLine(1_000L, "C"))

        val fallback = resolveAudioLyricsChordGrid(emptyList(), legacy)
        val derived = resolveAudioLyricsChordGrid(
            derivedLines = listOf(LrcLine(2_000L, "Am")),
            legacyLines = legacy
        )

        assertEquals(legacy, fallback.lines)
        assertFalse(fallback.usesDerivedLyrics)
        assertEquals(listOf(LrcLine(2_000L, "Am")), derived.lines)
        assertTrue(derived.usesDerivedLyrics)
    }
}
