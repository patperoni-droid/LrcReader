package com.patrick.lrcreader.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmpSyncModelsTest {

    @Test
    fun minimalManifest_preservesRequiredFields() {
        val manifest = SmpSyncManifest(
            appVersion = "0.3-beta",
            generatedAt = 1_700_000_000_000L
        )

        assertEquals(SMP_SYNC_MANIFEST_SCHEMA_VERSION, manifest.schemaVersion)
        assertEquals("0.3-beta", manifest.appVersion)
        assertEquals(1_700_000_000_000L, manifest.generatedAt)
        assertNull(manifest.deviceId)
        assertTrue(manifest.songs.isEmpty())
        assertTrue(manifest.playlists.isEmpty())
        assertTrue(manifest.families.isEmpty())
        assertNull(manifest.globalState)
    }

    @Test
    fun manifestJsonRoundTrip_preservesEntriesAndNullableFields() {
        val manifest = SmpSyncManifest(
            appVersion = "0.3-beta",
            deviceId = "device-a",
            generatedAt = 1_700_000_000_000L,
            libraryVersion = 42L,
            songs = listOf(
                SmpSyncSongEntry(
                    songId = "song_001",
                    title = "Bella Ciao Short",
                    updatedAt = 1_700_000_001_000L,
                    audioHash = "audio-hash",
                    lyricsHash = null,
                    fullSongHash = "song-hash"
                )
            ),
            playlists = listOf(
                SmpSyncPlaylistEntry(
                    playlistId = "playlist_001",
                    playlistName = "Set Fiesta",
                    itemsHash = "items-hash",
                    groupsHash = "groups-hash",
                    colorsHash = null,
                    fullPlaylistHash = "playlist-hash"
                )
            ),
            families = listOf(
                SmpSyncFamilyEntry(
                    familyId = "family_001",
                    title = "Flamenco",
                    songIds = listOf("song_001", "song_002"),
                    parentSongId = "song_001",
                    activeSongId = "song_002",
                    hash = "family-hash"
                )
            ),
            globalState = SmpSyncGlobalStateEntry(
                stateHash = "state-hash",
                updatedAt = null
            )
        )

        val restored = SmpSyncManifest.fromJsonOrNull(manifest.toJsonString())

        assertNotNull(restored)
        assertEquals(manifest, restored)
    }

    @Test
    fun syncPlan_groupsAddedModifiedConflictAndBrokenReferences() {
        val added = SyncPlanItem(
            action = SyncPlanAction.COPY_TO_B,
            diff = SyncDiff(
                entityType = SyncEntityType.SONG,
                entityId = "song_added",
                status = SyncDiffStatus.ABSENT_ON_B,
                title = "New Song",
                aHash = "hash-a"
            )
        )
        val modified = SyncPlanItem(
            action = SyncPlanAction.UPDATE_PLAYLIST_ON_B,
            diff = SyncDiff(
                entityType = SyncEntityType.PLAYLIST,
                entityId = "playlist_001",
                status = SyncDiffStatus.PLAYLIST_DIFFERENT,
                title = "Set Fiesta",
                aHash = "hash-a",
                bHash = "hash-b"
            )
        )
        val conflict = SyncPlanItem(
            action = SyncPlanAction.REVIEW_CONFLICT,
            diff = SyncDiff(
                entityType = SyncEntityType.SONG,
                entityId = "song_conflict",
                status = SyncDiffStatus.POSSIBLE_CONFLICT,
                aHash = "hash-a",
                bHash = "hash-b"
            )
        )
        val brokenReference = SyncPlanItem(
            action = SyncPlanAction.REVIEW_BROKEN_REFERENCE,
            diff = SyncDiff(
                entityType = SyncEntityType.FAMILY,
                entityId = "family_001",
                status = SyncDiffStatus.BROKEN_REFERENCE,
                brokenReferenceIds = listOf("missing_song")
            )
        )

        val plan = SyncPlan(
            items = listOf(added, modified, conflict, brokenReference)
        )

        assertEquals(listOf(added), plan.additions)
        assertEquals(listOf(modified), plan.modifications)
        assertEquals(listOf(conflict), plan.conflicts)
        assertEquals(listOf(brokenReference), plan.brokenReferences)
        assertTrue(plan.hasConflicts)
    }

    @Test
    fun absentHashAndEmptyHashRemainDistinctAfterJsonRoundTrip() {
        val absentHashSong = SmpSyncSongEntry(
            songId = "song_absent_hash",
            title = "No Hash",
            audioHash = null,
            fullSongHash = "full-a"
        )
        val emptyHashSong = SmpSyncSongEntry(
            songId = "song_empty_hash",
            title = "Empty Hash",
            audioHash = "",
            fullSongHash = "full-b"
        )
        val manifest = SmpSyncManifest(
            appVersion = "0.3-beta",
            generatedAt = 1L,
            songs = listOf(absentHashSong, emptyHashSong)
        )

        val restored = SmpSyncManifest.fromJson(manifest.toJsonString())

        assertNotEquals(restored.songs[0].audioHash, restored.songs[1].audioHash)
        assertNull(restored.songs[0].audioHash)
        assertEquals("", restored.songs[1].audioHash)
        assertFalse(restored.songs[0] == restored.songs[1])
    }

    @Test
    fun syncPackageJsonRoundTrip_preservesTransferMetadata() {
        val syncPackage = SmpSyncPackage(
            generatedAt = 42L,
            sourceDeviceId = "device-a",
            items = listOf(
                SmpSyncPackageItem(
                    kind = SmpSyncPackageKind.SONG_FULL,
                    entityId = "song_001",
                    title = "Bella Ciao",
                    sourceHash = "hash-a",
                    estimatedBytes = 123L,
                    contentEntry = "songs/song_001.smp",
                    diffStatus = SyncDiffStatus.MODIFIED_ON_A
                )
            )
        )

        val restored = SmpSyncPackage.fromJson(syncPackage.toJsonString())

        assertEquals(syncPackage, restored)
        assertEquals(123L, restored.estimatedBytes)
        assertEquals(1, restored.fullSongCount)
    }
}
