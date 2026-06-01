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
            title = "YÁLLAH",
            fullSongHash = "full-a"
        )
        val targetSong = song(
            songId = "song_b",
            title = "\uFEFFyallah",
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
                        title = "YÁLLAH",
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
        val songDiagnostic = diagnostics.modifiedSongs.single()
        assertEquals("songId différent", songDiagnostic.primaryReason)
        assertEquals("song_b", songDiagnostic.targetSongId)
        assertEquals("song_b", songDiagnostic.sameTitleDifferentSongId)
        assertEquals("yallah", diagnostics.sameTitleDifferentSongIds.single().sourceNormalizedTitle)
        assertEquals("\uFEFFyallah", diagnostics.sameTitleDifferentSongIds.single().targetTitle)
        assertEquals("audio", diagnostics.sameTitleDifferentSongIds.single().targetAudioHash)
        assertEquals("lyrics", diagnostics.sameTitleDifferentSongIds.single().targetLyricsHash)
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
            songIds = listOf("song_7fda1ffe81beda3fd443", "song_a"),
            itemKeys = listOf("song_7fda1ffe81beda3fd443", "group:Fiesta", "song_a")
        )
        val targetPlaylist = playlist(
            name = "Linda",
            itemsHash = "items-b",
            groupsHash = "groups-a",
            colorsHash = "colors-a",
            fullPlaylistHash = "full-b",
            songIds = listOf("song_7fda1ffe81beda3fd443", "song_b"),
            itemKeys = listOf("song_7fda1ffe81beda3fd443", "song_b", "group:Fiesta")
        )
        val sourceSong = song(
            songId = "song_a",
            title = "YÁLLAH",
            fullSongHash = "song-full-a"
        )
        val targetSong = song(
            songId = "song_b",
            title = "yallah",
            fullSongHash = "song-full-b"
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
            source = manifest(songs = listOf(sourceSong), playlists = listOf(sourcePlaylist)),
            target = manifest(songs = listOf(targetSong), playlists = listOf(targetPlaylist)),
            plan = plan
        )

        val playlistDiagnostic = diagnostics.modifiedPlaylists.single()
        assertEquals("Linda", playlistDiagnostic.playlistName)
        assertEquals("itemsHash", playlistDiagnostic.primaryReason)
        assertTrue(playlistDiagnostic.differentComponents.contains("itemsHash"))
        assertTrue(playlistDiagnostic.differentComponents.contains("itemKeys"))
        assertTrue(playlistDiagnostic.differentComponents.contains("fullPlaylistHash"))
        assertEquals(listOf("song_7fda1ffe81beda3fd443", "song_a"), playlistDiagnostic.sourceSongIds)
        assertEquals(3, playlistDiagnostic.sourceItemCount)
        assertEquals(3, playlistDiagnostic.targetItemCount)
        assertEquals("#2 A=group:Fiesta / B=song_b", playlistDiagnostic.firstDifferentItem)
        assertEquals("song_b", playlistDiagnostic.sameTitleDifferentSongIds.single().targetSongId)
    }

    @Test
    fun playlistDiagnostics_deduplicateSamePlaylistPlanItems() {
        val sourcePlaylist = playlist(
            name = "Linda",
            itemsHash = "items-a",
            groupsHash = null,
            colorsHash = null,
            fullPlaylistHash = "full-a",
            songIds = listOf("song_a"),
            itemKeys = listOf("song_a")
        )
        val targetPlaylist = playlist(
            name = "Linda",
            itemsHash = "items-b",
            groupsHash = null,
            colorsHash = null,
            fullPlaylistHash = "full-b",
            songIds = listOf("song_b"),
            itemKeys = listOf("song_b")
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
                ),
                SyncPlanItem(
                    action = SyncPlanAction.REVIEW_BROKEN_REFERENCE,
                    diff = SyncDiff(
                        entityType = SyncEntityType.PLAYLIST,
                        entityId = "Linda",
                        status = SyncDiffStatus.BROKEN_REFERENCE,
                        title = "Linda",
                        brokenReferenceIds = listOf("invalid:null")
                    )
                )
            )
        )

        val diagnostics = SmpSyncDiffDiagnosticsBuilder().build(
            source = manifest(playlists = listOf(sourcePlaylist)),
            target = manifest(playlists = listOf(targetPlaylist)),
            plan = plan
        )

        assertEquals(1, diagnostics.modifiedPlaylists.size)
        assertEquals(SyncDiffStatus.PLAYLIST_DIFFERENT, diagnostics.modifiedPlaylists.single().status)
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
        songIds: List<String>,
        itemKeys: List<String> = emptyList()
    ): SmpSyncPlaylistEntry {
        return SmpSyncPlaylistEntry(
            playlistName = name,
            songIds = songIds,
            itemCount = itemKeys.size.takeIf { it > 0 },
            itemKeys = itemKeys,
            itemsHash = itemsHash,
            groupsHash = groupsHash,
            colorsHash = colorsHash,
            fullPlaylistHash = fullPlaylistHash
        )
    }
}
