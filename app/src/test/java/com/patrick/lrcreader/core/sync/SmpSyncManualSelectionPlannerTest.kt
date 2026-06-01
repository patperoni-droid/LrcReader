package com.patrick.lrcreader.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SmpSyncManualSelectionPlannerTest {

    @Test
    fun selectedSongsAndPlaylists_buildManualSyncPlanOnlyForSelection() {
        val manifest = SmpSyncManifest(
            appVersion = "test",
            generatedAt = 1L,
            songs = listOf(
                song("song_001", "Yallah", "song-hash-1"),
                song("song_002", "Americano", "song-hash-2")
            ),
            playlists = listOf(
                playlist("Linda", "playlist-hash-1"),
                playlist("Fiesta", "playlist-hash-2")
            )
        )

        val plan = SmpSyncManualSelectionPlanner().buildPlan(
            sourceManifest = manifest,
            selectedSongIds = setOf("song_001"),
            selectedPlaylistIds = setOf("Linda")
        )

        assertEquals(2, plan.items.size)
        val songItem = plan.items[0]
        assertEquals(SyncEntityType.SONG, songItem.diff.entityType)
        assertEquals("song_001", songItem.diff.entityId)
        assertEquals(SyncDiffStatus.MODIFIED_ON_A, songItem.diff.status)
        assertEquals(SyncPlanAction.COPY_TO_B, songItem.action)

        val playlistItem = plan.items[1]
        assertEquals(SyncEntityType.PLAYLIST, playlistItem.diff.entityType)
        assertEquals("Linda", playlistItem.diff.entityId)
        assertEquals(SyncDiffStatus.PLAYLIST_DIFFERENT, playlistItem.diff.status)
        assertEquals(SyncPlanAction.UPDATE_PLAYLIST_ON_B, playlistItem.action)
    }

    @Test
    fun emptySelection_buildsEmptyPlan() {
        val plan = SmpSyncManualSelectionPlanner().buildPlan(
            sourceManifest = SmpSyncManifest(
                appVersion = "test",
                generatedAt = 1L,
                songs = listOf(song("song_001", "Yallah", "song-hash-1"))
            ),
            selectedSongIds = emptySet(),
            selectedPlaylistIds = emptySet()
        )

        assertEquals(0, plan.items.size)
    }

    private fun song(songId: String, title: String, hash: String): SmpSyncSongEntry {
        return SmpSyncSongEntry(
            songId = songId,
            title = title,
            fullSongHash = hash
        )
    }

    private fun playlist(name: String, hash: String): SmpSyncPlaylistEntry {
        return SmpSyncPlaylistEntry(
            playlistName = name,
            itemsHash = "$hash-items",
            fullPlaylistHash = hash
        )
    }
}
