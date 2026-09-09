package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsEditorChordProTimingMergeTest {

    @Test
    fun unchangedSynchronizedLineKeepsTimingAndMetadata() {
        val oldLine = LrcLine(timeMs = 1_250L, text = "Je pars ce soir", colorArgb = 0x123456)

        val result = mergeLyricsWithOldTimings(
            newLines = listOf("Je pars ce soir"),
            oldLines = listOf(oldLine)
        )

        assertEquals(listOf(oldLine), result)
    }

    @Test
    fun addingChordKeepsExistingTiming() {
        val result = mergeLyricsWithOldTimings(
            newLines = listOf("Je [Am]pars ce soir"),
            oldLines = listOf(LrcLine(timeMs = 2_000L, text = "Je pars ce soir"))
        )

        assertEquals(2_000L, result.single().timeMs)
        assertEquals("Je [Am]pars ce soir", result.single().text)
    }

    @Test
    fun replacingChordKeepsExistingTiming() {
        val result = mergeLyricsWithOldTimings(
            newLines = listOf("Je [G]pars ce soir"),
            oldLines = listOf(LrcLine(timeMs = 3_000L, text = "Je [Am]pars ce soir"))
        )

        assertEquals(3_000L, result.single().timeMs)
        assertEquals("Je [G]pars ce soir", result.single().text)
    }

    @Test
    fun removingChordKeepsExistingTiming() {
        val result = mergeLyricsWithOldTimings(
            newLines = listOf("Je pars ce soir"),
            oldLines = listOf(LrcLine(timeMs = 4_000L, text = "Je [Am]pars ce soir"))
        )

        assertEquals(4_000L, result.single().timeMs)
        assertEquals("Je pars ce soir", result.single().text)
    }

    @Test
    fun changingMultipleChordsKeepsExistingTiming() {
        val result = mergeLyricsWithOldTimings(
            newLines = listOf("Je [G]pars [D]ce soir"),
            oldLines = listOf(LrcLine(timeMs = 5_000L, text = "Je [Am]pars [F]ce soir"))
        )

        assertEquals(5_000L, result.single().timeMs)
        assertEquals("Je [G]pars [D]ce soir", result.single().text)
    }

    @Test
    fun plainLyricsKeepHistoricalTextMatchingBehavior() {
        val result = mergeLyricsWithOldTimings(
            newLines = listOf("Deuxième ligne", "Première ligne"),
            oldLines = listOf(
                LrcLine(timeMs = 1_000L, text = "Première ligne"),
                LrcLine(timeMs = 2_000L, text = "Deuxième ligne")
            )
        )

        assertEquals(listOf(2_000L, 1_000L), result.map { it.timeMs })
    }

    @Test
    fun identicalLinesKeepDistinctTimingsInOccurrenceOrder() {
        val result = mergeLyricsWithOldTimings(
            newLines = listOf("[Am]Refrain", "[G]Refrain"),
            oldLines = listOf(
                LrcLine(timeMs = 6_000L, text = "Refrain"),
                LrcLine(timeMs = 12_000L, text = "Refrain")
            )
        )

        assertEquals(listOf(6_000L, 12_000L), result.map { it.timeMs })
    }

    @Test
    fun genuinelyNewLineGetsNoTiming() {
        val result = mergeLyricsWithOldTimings(
            newLines = listOf("Première ligne", "Nouvelle ligne", "Dernière ligne"),
            oldLines = listOf(
                LrcLine(timeMs = 1_000L, text = "Première ligne"),
                LrcLine(timeMs = 3_000L, text = "Dernière ligne")
            )
        )

        assertEquals(listOf(1_000L, 0L, 3_000L), result.map { it.timeMs })
    }

    @Test
    fun genuinelyDeletedLineDoesNotShiftRemainingTimings() {
        val result = mergeLyricsWithOldTimings(
            newLines = listOf("Première ligne", "Dernière ligne"),
            oldLines = listOf(
                LrcLine(timeMs = 1_000L, text = "Première ligne"),
                LrcLine(timeMs = 2_000L, text = "Ligne supprimée"),
                LrcLine(timeMs = 3_000L, text = "Dernière ligne")
            )
        )

        assertEquals(listOf(1_000L, 3_000L), result.map { it.timeMs })
    }

    @Test
    fun ordinaryBracketsAreNotTreatedAsChords() {
        val result = mergeLyricsWithOldTimings(
            newLines = listOf("[Couplet] Je pars ce soir"),
            oldLines = listOf(LrcLine(timeMs = 7_000L, text = "[Refrain] Je pars ce soir"))
        )

        assertEquals(0L, result.single().timeMs)
        assertEquals("[Couplet] Je pars ce soir", result.single().text)
    }

    @Test
    fun addingInlineFormattingKeepsExistingTiming() {
        val result = mergeLyricsWithOldTimings(
            newLines = listOf("Je **pars** ce *soir*"),
            oldLines = listOf(LrcLine(timeMs = 8_000L, text = "Je pars ce soir"))
        )

        assertEquals(8_000L, result.single().timeMs)
        assertEquals("Je **pars** ce *soir*", result.single().text)
    }
}
