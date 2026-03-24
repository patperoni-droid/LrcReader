package com.patrick.lrcreader.core.backup

import com.patrick.lrcreader.core.buildGroupHeader
import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.smp.SongUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupBundlePlannerTest {

    @Test
    fun collectReferencedSmpSongIds_deduplicatesAndIgnoresNonSmpItems() {
        val playlists = linkedMapOf(
            "Live" to listOf(
                "file:///music/intro.mp3",
                buildSmpItem("song_002"),
                "prompter://42",
                buildSmpItem("song_001"),
                buildGroupHeader("Bloc A")
            ),
            "Encore" to listOf(
                buildSmpItem("song_001"),
                buildSmpItem("song_003")
            )
        )

        val referencedSongIds = BackupBundlePlanner.collectReferencedSmpSongIds(playlists)

        assertEquals(
            listOf("song_002", "song_001", "song_003"),
            referencedSongIds
        )
    }

    @Test
    fun buildSmpExportPreflight_resolvesSongsInReferenceOrder() {
        val song1 = fakeSong("song_001")
        val song2 = fakeSong("song_002")
        val playlists = linkedMapOf(
            "Live" to listOf(
                buildSmpItem("song_002"),
                buildSmpItem("song_001")
            )
        )

        val preflight = BackupBundlePlanner.buildSmpExportPreflight(playlists) { songId ->
            when (songId) {
                "song_001" -> song1
                "song_002" -> song2
                else -> null
            }
        }

        assertTrue(preflight.isExportAllowed)
        assertEquals(listOf("song_002", "song_001"), preflight.referencedSongIds)
        assertEquals(listOf(song2, song1), preflight.resolvedSongs)
        assertEquals(emptyList<String>(), preflight.missingSongIds)
    }

    @Test
    fun buildSmpExportPreflight_blocksExportWhenReferencedSongIsMissing() {
        val playlists = linkedMapOf(
            "Live" to listOf(
                buildSmpItem("song_001"),
                buildSmpItem("song_404"),
                buildSmpItem("song_002")
            )
        )

        val preflight = BackupBundlePlanner.buildSmpExportPreflight(playlists) { songId ->
            when (songId) {
                "song_001" -> fakeSong("song_001")
                "song_002" -> fakeSong("song_002")
                else -> null
            }
        }

        assertFalse(preflight.isExportAllowed)
        assertEquals(listOf("song_404"), preflight.missingSongIds)
        assertEquals(listOf("song_001", "song_002"), preflight.resolvedSongs.map { it.id })
    }

    @Test
    fun buildSmpExportPreflight_allowsExportWhenNoSmpIsReferenced() {
        val playlists = linkedMapOf(
            "Live" to listOf(
                "file:///music/intro.mp3",
                "prompter://42"
            )
        )

        val preflight = BackupBundlePlanner.buildSmpExportPreflight(playlists) { null }

        assertTrue(preflight.isExportAllowed)
        assertEquals(emptyList<String>(), preflight.referencedSongIds)
        assertEquals(emptyList<SongUnit>(), preflight.resolvedSongs)
        assertEquals(emptyList<String>(), preflight.missingSongIds)
    }

    private fun fakeSong(songId: String): SongUnit {
        return SongUnit(
            id = songId,
            title = songId,
            storageFolder = "/tmp/$songId",
            audioPath = "/tmp/$songId/audio.mp3",
            lyricsPath = null,
            chordsPath = null,
            waveformPath = null,
            annotationsPath = null,
            midiPath = null,
            midiCues = emptyList(),
            dmxPath = null,
            prompterPath = null
        )
    }
}
