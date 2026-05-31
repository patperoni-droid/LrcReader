package com.patrick.lrcreader.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmpSyncManifestComparatorTest {

    private val comparator = SmpSyncManifestComparator()

    @Test
    fun sameManifest_returnsEmptyPlan() {
        val manifest = manifest(
            songs = listOf(song("song_001", "hash-1")),
            playlists = listOf(playlist("Set Fiesta", "playlist-hash", songIds = listOf("song_001"))),
            families = listOf(family("family_001", "family-hash", songIds = listOf("song_001")))
        )

        val plan = comparator.compare(source = manifest, target = manifest)

        assertTrue(plan.items.isEmpty())
    }

    @Test
    fun songAbsentOnB_isCopiedToB() {
        val plan = comparator.compare(
            source = manifest(songs = listOf(song("song_001", "hash-1"))),
            target = manifest()
        )

        val item = plan.items.single()
        assertEquals(SyncEntityType.SONG, item.diff.entityType)
        assertEquals("song_001", item.diff.entityId)
        assertEquals(SyncDiffStatus.ABSENT_ON_B, item.diff.status)
        assertEquals(SyncPlanAction.COPY_TO_B, item.action)
    }

    @Test
    fun songHashDifferentWithoutBase_isModifiedOnA() {
        val plan = comparator.compare(
            source = manifest(songs = listOf(song("song_001", "hash-source"))),
            target = manifest(songs = listOf(song("song_001", "hash-target")))
        )

        val item = plan.items.single()
        assertEquals(SyncDiffStatus.MODIFIED_ON_A, item.diff.status)
        assertEquals(SyncPlanAction.COPY_TO_B, item.action)
        assertEquals("hash-source", item.diff.aHash)
        assertEquals("hash-target", item.diff.bHash)
    }

    @Test
    fun fullSongHashOnlyDifference_doesNotCopySong() {
        val plan = comparator.compare(
            source = manifest(
                songs = listOf(
                    song(
                        songId = "song_001",
                        hash = "full-source",
                        audioHash = "same-audio",
                        lyricsHash = "same-lyrics"
                    )
                )
            ),
            target = manifest(
                songs = listOf(
                    song(
                        songId = "song_001",
                        hash = "full-target",
                        audioHash = "same-audio",
                        lyricsHash = "same-lyrics"
                    )
                )
            )
        )

        assertTrue(plan.items.isEmpty())
    }

    @Test
    fun playlistDifferent_isDetected() {
        val plan = comparator.compare(
            source = manifest(playlists = listOf(playlist("Set Fiesta", "playlist-source"))),
            target = manifest(playlists = listOf(playlist("Set Fiesta", "playlist-target")))
        )

        val item = plan.items.single()
        assertEquals(SyncEntityType.PLAYLIST, item.diff.entityType)
        assertEquals(SyncDiffStatus.PLAYLIST_DIFFERENT, item.diff.status)
        assertEquals(SyncPlanAction.UPDATE_PLAYLIST_ON_B, item.action)
    }

    @Test
    fun familyDifferent_isDetected() {
        val plan = comparator.compare(
            source = manifest(families = listOf(family("family_001", "family-source"))),
            target = manifest(families = listOf(family("family_001", "family-target")))
        )

        val item = plan.items.single()
        assertEquals(SyncEntityType.FAMILY, item.diff.entityType)
        assertEquals(SyncDiffStatus.FAMILY_DIFFERENT, item.diff.status)
        assertEquals(SyncPlanAction.UPDATE_FAMILY_ON_B, item.action)
    }

    @Test
    fun brokenPlaylistReference_isDetected() {
        val source = manifest(
            playlists = listOf(
                playlist(
                    name = "Set Fiesta",
                    hash = "playlist-hash",
                    songIds = listOf("missing_song")
                )
            )
        )
        val target = source

        val plan = comparator.compare(source = source, target = target)

        val item = plan.items.single()
        assertEquals(SyncEntityType.PLAYLIST, item.diff.entityType)
        assertEquals(SyncDiffStatus.BROKEN_REFERENCE, item.diff.status)
        assertEquals(SyncPlanAction.REVIEW_BROKEN_REFERENCE, item.action)
        assertEquals(listOf("missing_song"), item.diff.brokenReferenceIds)
    }

    @Test
    fun brokenFamilyReference_isDetected() {
        val source = manifest(
            families = listOf(
                family(
                    id = "family_001",
                    hash = "family-hash",
                    songIds = listOf("missing_song"),
                    activeSongId = "missing_song"
                )
            )
        )
        val target = source

        val plan = comparator.compare(source = source, target = target)

        val item = plan.items.single()
        assertEquals(SyncEntityType.FAMILY, item.diff.entityType)
        assertEquals(SyncDiffStatus.BROKEN_REFERENCE, item.diff.status)
        assertEquals(SyncPlanAction.REVIEW_BROKEN_REFERENCE, item.action)
        assertEquals(listOf("missing_song"), item.diff.brokenReferenceIds)
    }

    @Test
    fun absentOnA_keepsTargetDataWithoutDeletion() {
        val plan = comparator.compare(
            source = manifest(),
            target = manifest(songs = listOf(song("song_on_b", "hash-b")))
        )

        val item = plan.items.single()
        assertEquals(SyncDiffStatus.ABSENT_ON_A, item.diff.status)
        assertEquals(SyncPlanAction.KEEP, item.action)
        assertEquals("hash-b", item.diff.bHash)
    }

    @Test
    fun divergentChangesFromBase_arePossibleConflict() {
        val base = manifest(songs = listOf(song("song_001", "hash-base")))
        val source = manifest(songs = listOf(song("song_001", "hash-source")))
        val target = manifest(songs = listOf(song("song_001", "hash-target")))

        val plan = comparator.compare(source = source, target = target, base = base)

        val item = plan.items.single()
        assertEquals(SyncDiffStatus.POSSIBLE_CONFLICT, item.diff.status)
        assertEquals(SyncPlanAction.REVIEW_CONFLICT, item.action)
        assertTrue(plan.hasConflicts)
    }

    private fun manifest(
        songs: List<SmpSyncSongEntry> = emptyList(),
        playlists: List<SmpSyncPlaylistEntry> = emptyList(),
        families: List<SmpSyncFamilyEntry> = emptyList()
    ): SmpSyncManifest {
        return SmpSyncManifest(
            appVersion = "0.3-beta",
            generatedAt = 1L,
            songs = songs,
            playlists = playlists,
            families = families
        )
    }

    private fun song(
        songId: String,
        hash: String,
        audioHash: String? = hash,
        lyricsHash: String? = null
    ): SmpSyncSongEntry {
        return SmpSyncSongEntry(
            songId = songId,
            title = songId,
            audioHash = audioHash,
            lyricsHash = lyricsHash,
            fullSongHash = hash
        )
    }

    private fun playlist(
        name: String,
        hash: String,
        songIds: List<String> = emptyList()
    ): SmpSyncPlaylistEntry {
        return SmpSyncPlaylistEntry(
            playlistName = name,
            songIds = songIds,
            itemsHash = "$hash-items",
            fullPlaylistHash = hash
        )
    }

    private fun family(
        id: String,
        hash: String,
        songIds: List<String> = emptyList(),
        activeSongId: String? = null
    ): SmpSyncFamilyEntry {
        return SmpSyncFamilyEntry(
            familyId = id,
            title = id,
            songIds = songIds,
            activeSongId = activeSongId,
            hash = hash
        )
    }
}
