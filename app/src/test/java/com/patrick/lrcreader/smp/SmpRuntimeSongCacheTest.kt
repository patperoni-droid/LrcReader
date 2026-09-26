package com.patrick.lrcreader.smp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SmpRuntimeSongCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun versionedSnapshotRoundTripPreservesInitialDisplayFields() {
        val file = temporaryFolder.newFile("runtime-cache.json")
        val song = song(
            id = "variant-1",
            title = "Fallback",
            arrangementSourceSongId = "parent-1"
        )
        val snapshot = SmpRuntimeSongCache.Snapshot(
            version = 2,
            workspaceKey = "content://workspace/a",
            songs = listOf(
                SmpRuntimeSongCache.CachedSong(
                    song = song,
                    displayTitle = "Alias visible",
                    volumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS,
                    volumeDb = -3,
                    lufsMeasured = -17.5f,
                    lufsTarget = -14f,
                    lufsAutoDb = 3.5f,
                    lufsManualDb = -1,
                    arrangementPlayable = true
                )
            )
        )

        SmpRuntimeSongCache.writeSnapshotAtomic(file, snapshot)
        val restored = SmpRuntimeSongCache.loadSnapshotFromFile(file)

        assertEquals("content://workspace/a", restored?.workspaceKey)
        assertTrue(restored!!.isForWorkspace("content://workspace/a"))
        assertFalse(restored.isForWorkspace("content://workspace/b"))
        val cached = restored.songs.single()
        assertEquals("variant-1", cached.song.id)
        assertEquals("parent-1", cached.song.arrangementSourceSongId)
        assertEquals("Alias visible", cached.displayTitle)
        assertEquals(-3, cached.volumeDb)
        assertEquals(-17.5f, cached.lufsMeasured)
        assertTrue(cached.arrangementPlayable)
    }

    @Test
    fun legacyArrayRemainsReadableWithoutInventingWorkspaceScope() {
        val file = temporaryFolder.newFile("legacy-cache.json")
        file.writeText(
            """[{"id":"legacy-1","title":"Ancien titre","storageFolder":"/runtime/legacy","audioPath":"/runtime/legacy/audio.mp3"}]"""
        )

        val restored = SmpRuntimeSongCache.loadSnapshotFromFile(file)

        assertTrue(restored!!.isLegacy)
        assertNull(restored.workspaceKey)
        assertFalse(restored.isForWorkspace("content://workspace/a"))
        assertEquals("legacy-1", restored.songs.single().song.id)
        assertEquals("Ancien titre", restored.songs.single().displayTitle)
    }

    @Test
    fun corruptedPublishedFileFallsBackToLastValidAtomicBackup() {
        val file = temporaryFolder.newFile("atomic-cache.json")
        val first = SmpRuntimeSongCache.Snapshot(
            version = 2,
            workspaceKey = "workspace",
            songs = listOf(SmpRuntimeSongCache.CachedSong(song("first", "Premier"), "Premier"))
        )
        val second = first.copy(
            songs = listOf(SmpRuntimeSongCache.CachedSong(song("second", "Second"), "Second"))
        )
        SmpRuntimeSongCache.writeSnapshotAtomic(file, first)
        SmpRuntimeSongCache.writeSnapshotAtomic(file, second)
        file.writeText("{interrupted")

        val restored = SmpRuntimeSongCache.loadSnapshotFromFile(file)

        assertEquals("first", restored?.songs?.single()?.song?.id)
    }

    private fun song(
        id: String,
        title: String,
        arrangementSourceSongId: String? = null
    ) = SongUnit(
        id = id,
        title = title,
        storageFolder = "/runtime/$id",
        audioPath = "/runtime/$id/audio.mp3",
        lyricsPath = "/runtime/$id/lyrics.lrc",
        chordsPath = null,
        annotationsPath = "/runtime/$id/annotations.json",
        midiPath = null,
        dmxPath = null,
        prompterPath = null,
        arrangementSourceSongId = arrangementSourceSongId
    )
}
