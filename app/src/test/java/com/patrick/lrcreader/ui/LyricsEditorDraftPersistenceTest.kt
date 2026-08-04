package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.LyricsViewMode
import com.patrick.lrcreader.core.parseLrc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LyricsEditorDraftPersistenceTest {

    @Test
    fun autosave_preservesActiveRawLyricsTextExactly() {
        val rawText = "Couplet 1\n\nRefrain\n \n\nPont\n"

        val restored = editorRawTextAfterPersistence(
            persistedMode = LyricsViewMode.LYRICS,
            activeMode = LyricsViewMode.LYRICS,
            activeRawText = rawText,
            lyricsDraftRawText = "normalized lyrics",
            chordsDraftRawText = "normalized chords"
        )

        assertEquals(rawText, restored)
    }

    @Test
    fun manualSave_preservesInactiveModeRawDraftExactly() {
        val lyricsDraft = "Couplet\n\nRefrain\n"
        val chordsDraft = "[Am]\n\n[F]\n"

        assertEquals(
            lyricsDraft,
            editorRawTextAfterPersistence(
                persistedMode = LyricsViewMode.LYRICS,
                activeMode = LyricsViewMode.CHORDS,
                activeRawText = chordsDraft,
                lyricsDraftRawText = lyricsDraft,
                chordsDraftRawText = chordsDraft
            )
        )
        assertEquals(
            chordsDraft,
            editorRawTextAfterPersistence(
                persistedMode = LyricsViewMode.CHORDS,
                activeMode = LyricsViewMode.LYRICS,
                activeRawText = lyricsDraft,
                lyricsDraftRawText = lyricsDraft,
                chordsDraftRawText = chordsDraft
            )
        )
    }

    @Test
    fun autosaveSignature_changesWhenOnlyBlankLinesChange() {
        val lines = listOf(LrcLine(timeMs = 1_000L, text = "Couplet"))

        assertNotEquals(
            lyricsPersistenceSignature("Couplet\nRefrain", lines),
            lyricsPersistenceSignature("Couplet\n\nRefrain", lines)
        )
    }

    @Test
    fun recreation_prefersPersistedRawTextExactly() {
        val rawText = "Couplet 1\nPremière ligne\n\nRefrain\n \nDeuxième ligne\n"

        assertEquals(
            rawText,
            editorRawTextForLoad(
                persistedRawText = rawText,
                fallbackText = "Couplet 1\nPremière ligne\nRefrain\nDeuxième ligne"
            )
        )
    }

    @Test
    fun legacySong_fallsBackToNormalizedLrcText() {
        val legacyLrcText = "[00:01.00] Couplet\n[00:05.00] Refrain"

        assertEquals(
            legacyLrcText,
            editorRawTextForLoad(persistedRawText = null, fallbackText = legacyLrcText)
        )
    }

    @Test
    fun lrcGeneration_canIgnoreBlankLinesWithoutMutatingRawText() {
        val rawText = "Couplet\n\nRefrain\n\n\nPont\n"

        assertEquals(listOf("Couplet", "Refrain", "Pont"), parseLrc(rawText).map { it.text })
        assertEquals("Couplet\n\nRefrain\n\n\nPont\n", rawText)
    }
}
