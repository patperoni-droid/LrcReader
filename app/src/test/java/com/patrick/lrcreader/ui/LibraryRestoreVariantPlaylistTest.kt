package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.backup.BackupBundleImportedSong
import com.patrick.lrcreader.core.backup.BackupStateRemapResult
import com.patrick.lrcreader.core.backup.BackupStateRemapper
import com.patrick.lrcreader.smp.SongUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRestoreVariantPlaylistTest {

    @Test
    fun restoredVariantIsRegisteredForPlaylistRemapping() {
        val parent = song(id = "parent")
        val variant = song(id = "variant", sourceSongId = parent.id)
        val unrelatedVariant = song(id = "unrelated_variant", sourceSongId = "another_parent")
        val mappings = buildLibraryRestoreSongMappings(
            importedSongs = listOf(
                BackupBundleImportedSong(
                    bundleSongId = parent.id,
                    importedSongId = parent.id,
                    storageFolder = parent.storageFolder
                )
            ),
            runtimeSongs = listOf(parent, variant, unrelatedVariant),
            restoredParentSongIds = setOf(parent.id)
        )

        assertEquals(setOf(parent.id, variant.id), mappings.map { it.bundleSongId }.toSet())
        assertFalse(mappings.any { it.bundleSongId == unrelatedVariant.id })

        val remapResult = BackupStateRemapper.remapBundleStateJson(
            stateJson = """
                {
                  "playlists": {
                    "TEST_VARIANTE": [
                      {"uri":"smp://variant","songId":"variant"}
                    ]
                  }
                }
            """.trimIndent(),
            importedSongs = mappings
        )

        assertTrue(remapResult is BackupStateRemapResult.Success)
        val remappedState = JSONObject((remapResult as BackupStateRemapResult.Success).stateJson)
        val restoredEntry = remappedState
            .getJSONObject("playlists")
            .getJSONArray("TEST_VARIANTE")
            .getJSONObject(0)
        assertEquals("variant", restoredEntry.getString("songId"))
        assertEquals("smp://variant", restoredEntry.getString("uri"))
    }

    private fun song(id: String, sourceSongId: String? = null): SongUnit {
        return SongUnit(
            id = id,
            title = id,
            storageFolder = "/tmp/$id",
            audioPath = if (sourceSongId == null) "/tmp/$id/audio.mp3" else null,
            lyricsPath = null,
            chordsPath = null,
            annotationsPath = null,
            midiPath = null,
            dmxPath = null,
            prompterPath = null,
            arrangementSourceSongId = sourceSongId
        )
    }
}
