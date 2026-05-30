package com.patrick.lrcreader.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPackageBuilderTest {

    @Test
    fun absentSongOnB_buildsFullSongPackageItem() {
        val song = song("song_001", "Bella Ciao", "song-hash")
        val plan = SyncPlan(
            items = listOf(
                SyncPlanItem(
                    action = SyncPlanAction.COPY_TO_B,
                    diff = SyncDiff(
                        entityType = SyncEntityType.SONG,
                        entityId = song.songId,
                        status = SyncDiffStatus.ABSENT_ON_B,
                        title = song.title,
                        aHash = song.fullSongHash
                    )
                )
            )
        )

        val syncPackage = SyncPackageBuilder(
            estimateFullSongBytes = { 123_456L }
        ).build(
            sourceManifest = manifest(songs = listOf(song)),
            plan = plan,
            generatedAt = 42L
        )

        assertEquals(1, syncPackage.itemCount)
        assertEquals(1, syncPackage.fullSongCount)
        assertEquals(123_456L, syncPackage.estimatedBytes)
        assertEquals(
            SmpSyncPackageItem(
                kind = SmpSyncPackageKind.SONG_FULL,
                entityId = "song_001",
                title = "Bella Ciao",
                sourceHash = "song-hash",
                estimatedBytes = 123_456L
            ),
            syncPackage.items.single()
        )
    }

    @Test
    fun modifiedSongOnA_buildsFullSongPackageItem() {
        val song = song("song_001", "Bella Ciao", "song-source")
        val plan = SyncPlan(
            items = listOf(
                SyncPlanItem(
                    action = SyncPlanAction.COPY_TO_B,
                    diff = SyncDiff(
                        entityType = SyncEntityType.SONG,
                        entityId = song.songId,
                        status = SyncDiffStatus.MODIFIED_ON_A,
                        aHash = "song-source",
                        bHash = "song-target"
                    )
                )
            )
        )

        val syncPackage = SyncPackageBuilder().build(
            sourceManifest = manifest(songs = listOf(song)),
            plan = plan
        )

        assertEquals(1, syncPackage.fullSongCount)
        assertEquals(SmpSyncPackageKind.SONG_FULL, syncPackage.items.single().kind)
        assertNull(syncPackage.estimatedBytes)
    }

    @Test
    fun playlistDifference_buildsPlaylistStateItem() {
        val playlist = playlist("playlist_001", "Set Fiesta", "playlist-hash")
        val plan = SyncPlan(
            items = listOf(
                SyncPlanItem(
                    action = SyncPlanAction.UPDATE_PLAYLIST_ON_B,
                    diff = SyncDiff(
                        entityType = SyncEntityType.PLAYLIST,
                        entityId = "playlist_001",
                        status = SyncDiffStatus.PLAYLIST_DIFFERENT,
                        title = "Set Fiesta",
                        aHash = "playlist-hash"
                    )
                )
            )
        )

        val syncPackage = SyncPackageBuilder().build(
            sourceManifest = manifest(playlists = listOf(playlist)),
            plan = plan
        )

        assertEquals(1, syncPackage.playlistStateCount)
        assertEquals(SmpSyncPackageKind.PLAYLIST_STATE, syncPackage.items.single().kind)
        assertEquals("Set Fiesta", syncPackage.items.single().title)
    }

    @Test
    fun familyDifference_buildsFamilyStateItem() {
        val family = family("family_001", "Flamenco", "family-hash")
        val plan = SyncPlan(
            items = listOf(
                SyncPlanItem(
                    action = SyncPlanAction.UPDATE_FAMILY_ON_B,
                    diff = SyncDiff(
                        entityType = SyncEntityType.FAMILY,
                        entityId = "family_001",
                        status = SyncDiffStatus.FAMILY_DIFFERENT,
                        title = "Flamenco",
                        aHash = "family-hash"
                    )
                )
            )
        )

        val syncPackage = SyncPackageBuilder().build(
            sourceManifest = manifest(families = listOf(family)),
            plan = plan
        )

        assertEquals(1, syncPackage.familyStateCount)
        assertEquals(SmpSyncPackageKind.FAMILY_STATE, syncPackage.items.single().kind)
        assertEquals("Flamenco", syncPackage.items.single().title)
    }

    @Test
    fun packageCountsItemsAndKnownSize() {
        val songs = listOf(
            song("song_001", "One", "hash-1"),
            song("song_002", "Two", "hash-2")
        )
        val plan = SyncPlan(
            items = songs.map { song ->
                SyncPlanItem(
                    action = SyncPlanAction.COPY_TO_B,
                    diff = SyncDiff(
                        entityType = SyncEntityType.SONG,
                        entityId = song.songId,
                        status = SyncDiffStatus.ABSENT_ON_B
                    )
                )
            }
        )

        val syncPackage = SyncPackageBuilder(
            estimateFullSongBytes = { song -> if (song.songId == "song_001") 10L else null }
        ).build(
            sourceManifest = manifest(songs = songs),
            plan = plan
        )

        assertEquals(2, syncPackage.itemCount)
        assertEquals(2, syncPackage.fullSongCount)
        assertEquals(10L, syncPackage.knownEstimatedBytes)
        assertNull(syncPackage.estimatedBytes)
        assertTrue(!syncPackage.hasCompleteSizeEstimate)
    }

    @Test
    fun absentOnA_doesNotCreateDeletionOrPackageItem() {
        val plan = SyncPlan(
            items = listOf(
                SyncPlanItem(
                    action = SyncPlanAction.KEEP,
                    diff = SyncDiff(
                        entityType = SyncEntityType.SONG,
                        entityId = "song_only_on_b",
                        status = SyncDiffStatus.ABSENT_ON_A,
                        bHash = "backup-hash"
                    )
                )
            )
        )

        val syncPackage = SyncPackageBuilder().build(
            sourceManifest = manifest(),
            plan = plan
        )

        assertTrue(syncPackage.items.isEmpty())
    }

    private fun manifest(
        songs: List<SmpSyncSongEntry> = emptyList(),
        playlists: List<SmpSyncPlaylistEntry> = emptyList(),
        families: List<SmpSyncFamilyEntry> = emptyList()
    ): SmpSyncManifest {
        return SmpSyncManifest(
            appVersion = "0.3-beta",
            deviceId = "device-a",
            generatedAt = 1L,
            songs = songs,
            playlists = playlists,
            families = families
        )
    }

    private fun song(
        songId: String,
        title: String,
        hash: String
    ): SmpSyncSongEntry {
        return SmpSyncSongEntry(
            songId = songId,
            title = title,
            fullSongHash = hash
        )
    }

    private fun playlist(
        playlistId: String,
        name: String,
        hash: String
    ): SmpSyncPlaylistEntry {
        return SmpSyncPlaylistEntry(
            playlistId = playlistId,
            playlistName = name,
            itemsHash = "$hash-items",
            fullPlaylistHash = hash
        )
    }

    private fun family(
        familyId: String,
        title: String,
        hash: String
    ): SmpSyncFamilyEntry {
        return SmpSyncFamilyEntry(
            familyId = familyId,
            title = title,
            songIds = emptyList(),
            hash = hash
        )
    }
}
