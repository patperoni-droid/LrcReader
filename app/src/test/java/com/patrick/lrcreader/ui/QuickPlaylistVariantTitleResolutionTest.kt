package com.patrick.lrcreader.ui

import com.patrick.lrcreader.smp.SongUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickPlaylistVariantTitleResolutionTest {

    @Test
    fun restoredVariantUsesRuntimeTitleWhilePlaylistIndexCatchesUp() {
        val variant = SongUnit(
            id = "arrangement_01",
            title = "Marina-AR01",
            storageFolder = "/tmp/arrangement_01",
            audioPath = null,
            lyricsPath = null,
            chordsPath = "/tmp/arrangement_01/chords.lrc",
            annotationsPath = null,
            midiPath = null,
            dmxPath = null,
            prompterPath = null,
            arrangementSourceSongId = "marina"
        )

        val title = resolveQuickPlaylistSmpLibraryTitle(
            songId = variant.id,
            indexedTitles = emptyMap(),
            indexedSongs = emptyMap(),
            runtimeSongs = mapOf(variant.id to variant)
        )

        assertEquals("Marina-AR01", title)
    }
}
