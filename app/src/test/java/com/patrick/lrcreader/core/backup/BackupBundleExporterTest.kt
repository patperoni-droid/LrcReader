package com.patrick.lrcreader.core.backup

import com.patrick.lrcreader.smp.SongUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupBundleExporterTest {

    @Test
    fun buildManualBundlePayload_blocksWhenReferencedSongIsMissing() {
        val preflight = BackupBundleSmpExportPreflight(
            referencedSongIds = listOf("song_001", "song_404"),
            resolvedSongs = listOf(fakeSong("song_001")),
            missingSongIds = listOf("song_404")
        )

        val result = BackupBundleExporter.buildManualBundlePayload(
            stateJson = """{"playlists":{"Live":["smp://song_001"]}}""",
            preflight = preflight
        ) { error("Should not export when preflight is blocked") }

        val blocked = result as BackupBundleExportBuildResult.MissingReferencedSongs
        assertEquals(listOf("song_404"), blocked.songIds)
    }

    @Test
    fun buildManualBundlePayload_returnsPayloadWithSelectedSmpEntries() {
        val preflight = BackupBundleSmpExportPreflight(
            referencedSongIds = listOf("song_002", "song_001"),
            resolvedSongs = listOf(fakeSong("song_002"), fakeSong("song_001")),
            missingSongIds = emptyList()
        )

        val result = BackupBundleExporter.buildManualBundlePayload(
            stateJson = """{"playlists":{"Live":["smp://song_002","smp://song_001"]}}""",
            preflight = preflight
        ) { song ->
            "bundle-${song.id}".toByteArray(Charsets.UTF_8)
        }

        val success = result as BackupBundleExportBuildResult.Success
        assertEquals(2, success.payload.smpFiles.size)
        assertEquals("smp/song_002.smp", success.payload.smpFiles[0].entryName)
        assertEquals("smp/song_001.smp", success.payload.smpFiles[1].entryName)

        val output = ByteArrayOutputStream()
        BackupBundleIo.write(output, success.payload)
        val restored = BackupBundleIo.readOrNull(ByteArrayInputStream(output.toByteArray()))

        assertEquals(success.payload.stateJson, restored?.stateJson)
        assertEquals(listOf("song_001", "song_002"), restored?.manifest?.songs?.map { it.songId }?.sorted())
    }

    @Test
    fun buildManualBundlePayload_failsWhenResolvedSongCannotBeExported() {
        val preflight = BackupBundleSmpExportPreflight(
            referencedSongIds = listOf("song_001", "song_002"),
            resolvedSongs = listOf(fakeSong("song_001"), fakeSong("song_002")),
            missingSongIds = emptyList()
        )

        val result = BackupBundleExporter.buildManualBundlePayload(
            stateJson = """{"playlists":{"Live":["smp://song_001","smp://song_002"]}}""",
            preflight = preflight
        ) { song ->
            if (song.id == "song_002") null else byteArrayOf(1, 2, 3)
        }

        val failed = result as BackupBundleExportBuildResult.SongExportFailed
        assertTrue(failed.songIds.contains("song_002"))
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
