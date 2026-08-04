package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.LyricsViewMode
import org.junit.Assert.assertEquals
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
}
