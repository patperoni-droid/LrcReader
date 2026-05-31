package com.patrick.lrcreader.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmpSyncDiffDiagnosticsTest {

    @Test
    fun modifiedSong_reportsDifferingComponentsAndFullSongReason() {
        val sourceSong = song(
            songId = "song_001",
            title = "Bella Ciao",
            settingsHash = "settings-a",
            fullSongHash = "full-a"
        )
        val targetSong = song(
            songId = "song_001",
            title = "Bella Ciao",
            settingsHash = "settings-b",
            fullSongHash = "full-b"
        )
        val plan = SyncPlan(
            items = listOf(
                SyncPlanItem(
                    action = SyncPlanAction.COPY_TO_B,
                    diff = SyncDiff(
                        entityType = SyncEntityType.SONG,
                        entityId = "song_001",
                        status = SyncDiffStatus.MODIFIED_ON_A,
                        title = "Bella Ciao",
                        aHash = "full-a",
                        bHash = "full-b"
                    )
                )
            )
        )

        val diagnostics = SmpSyncDiffDiagnosticsBuilder().build(
            source = manifest(songs = listOf(sourceSong)),
            target = manifest(songs = listOf(targetSong)),
            plan = plan
        )

        assertEquals(1, diagnostics.fullSongCount)
        assertEquals(1, diagnostics.fullSongReasonCounts["settingsHash"])
        val songDiagnostic = diagnostics.modifiedSongs.single()
        assertEquals("settingsHash", songDiagnostic.primaryReason)
        assertEquals(SmpSyncPackageKind.SONG_FULL, songDiagnostic.packageKind)
        assertTrue(songDiagnostic.differentComponents.contains("settingsHash"))
        assertTrue(songDiagnostic.differentComponents.contains("fullSongHash"))
    }

    @Test
    fun absentSongWithSameTitle_reportsDifferentSongId() {
        val sourceSong = song(
            songId = "song_a",
            title = "Bella Ciao",
            fullSongHash = "full-a"
        )
        val targetSong = song(
            songId = "song_b",
            title = "Bella Ciao",
            fullSongHash = "full-b"
        )
        val plan = SyncPlan(
            items = listOf(
                SyncPlanItem(
                    action = SyncPlanAction.COPY_TO_B,
                    diff = SyncDiff(
                        entityType = SyncEntityType.SONG,
                        entityId = "song_a",
                        status = SyncDiffStatus.ABSENT_ON_B,
                        title = "Bella Ciao",
                        aHash = "full-a"
                    )
                )
            )
        )

        val diagnostics = SmpSyncDiffDiagnosticsBuilder().build(
            source = manifest(songs = listOf(sourceSong)),
            target = manifest(songs = listOf(targetSong)),
            plan = plan
        )

        assertEquals(1, diagnostics.sameTitleDifferentSongIds.size)
        assertEquals("songId différent", diagnostics.modifiedSongs.single().primaryReason)
        assertEquals(1, diagnostics.fullSongReasonCounts["songId différent"])
    }

    @Test
    fun playlistDifference_reportsPlaylistComponents() {
        val sourcePlaylist = playlist(
            name = "Linda",
            itemsHash = "items-a",
            groupsHash = "groups-a",
            colorsHash = "colors-a",
            fullPlaylistHash = "full-a",
            songIds = listOf("song_7fda1ffe81beda3fd443")
        )
        val targetPlaylist = playlist(
            name = "Linda",
            itemsHash = "items-b",
            groupsHash = "groups-a",
            colorsHash = "colors-a",
            fullPlaylistHash = "full-b",
            songIds = listOf("song_7fda1ffe81beda3fd443")
        )
        val plan = SyncPlan(
            items = listOf(
                SyncPlanItem(
                    action = SyncPlanAction.UPDATE_PLAYLIST_ON_B,
                    diff = SyncDiff(
                        entityType = SyncEntityType.PLAYLIST,
                        entityId = "Linda",
                        status = SyncDiffStatus.PLAYLIST_DIFFERENT,
                        title = "Linda",
                        aHash = "full-a",
                        bHash = "full-b"
                    )
                )
            )
        )

        val diagnostics = SmpSyncDiffDiagnosticsBuilder().build(
            source = manifest(playlists = listOf(sourcePlaylist)),
            target = manifest(playlists = listOf(targetPlaylist)),
            plan = plan
        )

        val playlistDiagnostic = diagnostics.modifiedPlaylists.single()
        assertEquals("Linda", playlistDiagnostic.playlistName)
        assertEquals("itemsHash", playlistDiagnostic.primaryReason)
        assertTrue(playlistDiagnostic.differentComponents.contains("itemsHash"))
        assertTrue(playlistDiagnostic.differentComponents.contains("fullPlaylistHash"))
        assertEquals(listOf("song_7fda1ffe81beda3fd443"), playlistDiagnostic.sourceSongIds)
    }

    private fun manifest(
        songs: List<SmpSyncSongEntry> = emptyList(),
        playlists: List<SmpSyncPlaylistEntry> = emptyList()
    ): SmpSyncManifest {
        return SmpSyncManifest(
            appVersion = "test",
            generatedAt = 1L,
            songs = songs,
            playlists = playlists
        )
    }

    private fun song(
        songId: String,
        title: String,
        settingsHash: String? = null,
        fullSongHash: String
    ): SmpSyncSongEntry {
        return SmpSyncSongEntry(
            songId = songId,
            title = title,
            audioHash = "audio",
            lyricsHash = "lyrics",
            settingsHash = settingsHash,
            fullSongHash = fullSongHash
        )
    }

    private fun playlist(
        name: String,
        itemsHash: String,
        groupsHash: String?,
        colorsHash: String?,
        fullPlaylistHash: String,
        songIds: List<String>
    ): SmpSyncPlaylistEntry {
        return SmpSyncPlaylistEntry(
            playlistName = name,
            songIds = songIds,
            itemsHash = itemsHash,
            groupsHash = groupsHash,
            colorsHash = colorsHash,
            fullPlaylistHash = fullPlaylistHash
        )
    }
}
