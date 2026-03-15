package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcTimelineTest {

    @Test
    fun resolveLyricsViewMode_keepsChordsWhenAvailable() {
        val mode = resolveLyricsViewMode(
            current = LyricsViewMode.CHORDS,
            hasLyrics = true,
            hasChords = true
        )
        assertEquals(LyricsViewMode.CHORDS, mode)
    }

    @Test
    fun resolveLyricsViewMode_fallsBackWhenChordsMissing() {
        val mode = resolveLyricsViewMode(
            current = LyricsViewMode.CHORDS,
            hasLyrics = true,
            hasChords = false
        )
        assertEquals(LyricsViewMode.LYRICS, mode)
    }

    @Test
    fun resolveLyricsViewMode_defaultsToLyricsWhenNothingAvailable() {
        val mode = resolveLyricsViewMode(
            current = LyricsViewMode.LYRICS,
            hasLyrics = false,
            hasChords = false
        )
        assertEquals(LyricsViewMode.LYRICS, mode)
    }

    @Test
    fun computeLyricsChordsUiState_hidesToggleAndShowsMissingMessageWhenNoChords() {
        val ui = computeLyricsChordsUiState(hasLyrics = true, hasChords = false)
        assertEquals(false, ui.showToggle)
        assertEquals(true, ui.showMissingChordsMessage)
    }

    @Test
    fun resolveChordsLookupFileName_prefersExactLyricsFileName() {
        val fileName = resolveChordsLookupFileName(
            exactLyricsFileName = "Titre-abc123.lrc",
            fallbackBaseName = "Titre"
        )
        assertEquals("Titre-abc123.lrc", fileName)
    }

    @Test
    fun resolveChordsLookupFileName_fallsBackToAudioBaseWhenMissing() {
        val fileName = resolveChordsLookupFileName(
            exactLyricsFileName = null,
            fallbackBaseName = "Titre"
        )
        assertEquals("Titre.lrc", fileName)
    }

    @Test
    fun decideLrcAutoCreate_openChordsWithoutChordsFile_createsPrimary() {
        val decision = decideLrcAutoCreate(
            trigger = LrcAutoCreateTrigger.OPEN_CHORDS,
            primaryExists = false,
            twinExists = true
        )
        assertEquals(true, decision.createPrimaryFile)
        assertEquals(false, decision.createTwinFile)
    }

    @Test
    fun decideLrcAutoCreate_saveLyrics_createsMissingChordsTwin() {
        val decision = decideLrcAutoCreate(
            trigger = LrcAutoCreateTrigger.SAVE_LYRICS,
            primaryExists = true,
            twinExists = false
        )
        assertEquals(false, decision.createPrimaryFile)
        assertEquals(true, decision.createTwinFile)
    }

    @Test
    fun decideLrcAutoCreate_saveChords_createsMissingLyricsTwin() {
        val decision = decideLrcAutoCreate(
            trigger = LrcAutoCreateTrigger.SAVE_CHORDS,
            primaryExists = true,
            twinExists = false
        )
        assertEquals(false, decision.createPrimaryFile)
        assertEquals(true, decision.createTwinFile)
    }

    @Test
    fun decideLrcAutoCreate_saveChords_whenBothMissing_createsBoth() {
        val decision = decideLrcAutoCreate(
            trigger = LrcAutoCreateTrigger.SAVE_CHORDS,
            primaryExists = false,
            twinExists = false
        )

        val targetName = resolveChordsLookupFileName(
            exactLyricsFileName = null,
            fallbackBaseName = "MySong"
        )

        assertEquals(true, decision.createPrimaryFile)
        assertEquals(true, decision.createTwinFile)
        assertEquals("MySong.lrc", targetName)
    }

    @Test
    fun decideLrcAutoCreate_saveLyrics_whenBothMissing_createsBoth() {
        val decision = decideLrcAutoCreate(
            trigger = LrcAutoCreateTrigger.SAVE_LYRICS,
            primaryExists = false,
            twinExists = false
        )

        val targetName = resolveChordsLookupFileName(
            exactLyricsFileName = null,
            fallbackBaseName = "MySong"
        )

        assertEquals(true, decision.createPrimaryFile)
        assertEquals(true, decision.createTwinFile)
        assertEquals("MySong.lrc", targetName)
    }

    @Test
    fun decideLrcAutoCreate_doesNotCreateTwinWhenAlreadyPresent() {
        val decision = decideLrcAutoCreate(
            trigger = LrcAutoCreateTrigger.SAVE_LYRICS,
            primaryExists = true,
            twinExists = true
        )
        assertEquals(false, decision.createPrimaryFile)
        assertEquals(false, decision.createTwinFile)
    }

    @Test
    fun findActiveLrcIndex_returnsLastTaggedBeforePosition() {
        val lines = listOf(
            LrcLine(timeMs = 1_000L, text = "A"),
            LrcLine(timeMs = 2_000L, text = "B"),
            LrcLine(timeMs = 3_000L, text = "C")
        )

        assertEquals(1, findActiveLrcIndex(lines, positionMs = 2_500L))
    }

    @Test
    fun findActiveLrcIndex_returnsMinusOneBeforeFirstTag() {
        val lines = listOf(
            LrcLine(timeMs = 1_000L, text = "A"),
            LrcLine(timeMs = 2_000L, text = "B")
        )

        assertEquals(-1, findActiveLrcIndex(lines, positionMs = 800L))
    }

    @Test
    fun findActiveLrcIndex_returnsZeroWhenNoTags() {
        val lines = listOf(
            LrcLine(timeMs = 0L, text = "A"),
            LrcLine(timeMs = 0L, text = "B")
        )

        assertEquals(0, findActiveLrcIndex(lines, positionMs = 5_000L))
    }

    @Test
    fun buildChordsWindow_returnsExpectedSlices() {
        val lines = listOf(
            LrcLine(timeMs = 1_000L, text = "Am"),
            LrcLine(timeMs = 2_000L, text = "F"),
            LrcLine(timeMs = 3_000L, text = "C"),
            LrcLine(timeMs = 4_000L, text = "G")
        )

        val window = buildChordsWindow(lines, activeIndex = 1, nextCount = 3)

        assertEquals("Am", window.previous?.text)
        assertEquals("F", window.current?.text)
        assertEquals(listOf("C", "G"), window.next.map { it.text })
    }

    @Test
    fun buildChordsWindow_handlesEmptyList() {
        val window = buildChordsWindow(emptyList(), activeIndex = 0, nextCount = 3)
        assertNull(window.previous)
        assertNull(window.current)
        assertEquals(emptyList<LrcLine>(), window.next)
    }

    @Test
    fun clearAllChords_keepsPalette_and_keepsLyrics() {
        val palette = listOf("Am", "F", "C")
        val lyrics = listOf(
            LrcLine(timeMs = 1_000L, text = "Line 1"),
            LrcLine(timeMs = 2_000L, text = "Line 2")
        )

        val result = clearAllChordsKeepingPaletteAndLyrics(
            palette = palette,
            lyrics = lyrics
        )

        assertEquals(emptyList<LrcLine>(), result.clearedChordLines)
        assertEquals("", result.clearedChordRawText)
        assertEquals(palette, result.keptPalette)
        assertEquals(lyrics, result.keptLyrics)
    }
}
