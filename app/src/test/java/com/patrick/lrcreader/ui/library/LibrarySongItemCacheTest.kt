package com.patrick.lrcreader.ui.library

import com.patrick.lrcreader.smp.SmpConfig
import com.patrick.lrcreader.smp.SmpRuntimeSongCache
import com.patrick.lrcreader.smp.SongUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySongItemCacheTest {
    @Test
    fun cachedRowsAreImmediatelyUsableAndSortedWithoutStorageReads() {
        val rows = buildLibrarySongItemsFromCache(
            listOf(
                cachedSong("b", "Zulu", volumeDb = -2),
                cachedSong("a", "Alpha", arrangementPlayable = true)
            )
        )

        assertEquals(listOf("a", "b"), rows.map(LibrarySongItem::songId))
        assertEquals("Alpha", rows.first().displayTitle)
        assertTrue(rows.first().arrangementPlayable)
        assertEquals(-2, rows.last().volumeDb)
        assertEquals("smp://b", rows.last().playbackItem)
    }

    private fun cachedSong(
        id: String,
        displayTitle: String,
        volumeDb: Int? = null,
        arrangementPlayable: Boolean = false
    ) = SmpRuntimeSongCache.CachedSong(
        song = SongUnit(
            id = id,
            title = "Fallback $id",
            audioPath = "/runtime/$id/audio.mp3",
            lyricsPath = null,
            chordsPath = null,
            annotationsPath = null,
            midiPath = null,
            dmxPath = null,
            prompterPath = null
        ),
        displayTitle = displayTitle,
        volumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL,
        volumeDb = volumeDb,
        arrangementPlayable = arrangementPlayable
    )
}
