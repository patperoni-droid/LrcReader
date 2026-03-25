package com.patrick.lrcreader.core.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupBundleRestorePreparerTest {

    @Test
    fun prepareStateJsonForRestore_passesThroughNonSuccessImportFailures() {
        assertEquals(
            BackupBundleRestorePreparationResult.NotBundle,
            BackupBundleRestorePreparer.prepareStateJsonForRestore(
                BackupBundleImportResult.NotBundle
            )
        )
        assertEquals(
            BackupBundleRestorePreparationResult.InvalidBundle,
            BackupBundleRestorePreparer.prepareStateJsonForRestore(
                BackupBundleImportResult.InvalidBundle
            )
        )

        val failed = BackupBundleRestorePreparer.prepareStateJsonForRestore(
            BackupBundleImportResult.SmpImportFailed(
                songId = "song-1",
                reason = "crc error"
            )
        ) as BackupBundleRestorePreparationResult.SmpImportFailed

        assertEquals("song-1", failed.songId)
        assertEquals("crc error", failed.reason)
    }

    @Test
    fun prepareStateJsonForRestore_returnsRemappedStateOnSuccess() {
        val result = BackupBundleRestorePreparer.prepareStateJsonForRestore(
            BackupBundleImportResult.Success(
                importedSongs = listOf(
                    BackupBundleImportedSong(
                        bundleSongId = "bundle-song",
                        importedSongId = "runtime-song",
                        storageFolder = "/storage/runtime-song"
                    )
                ),
                stateJson = """
                    {
                      "playlists": {
                        "Set": ["smp://bundle-song"]
                      },
                      "lastPlayed": {
                        "uri": "file:/data/user/0/app/files/tracks/bundle-song/audio.mp3",
                        "playlistName": "Set",
                        "positionMs": 1200
                      }
                    }
                """.trimIndent()
            )
        )

        val success = result as BackupBundleRestorePreparationResult.Success
        val root = JSONObject(success.stateJson)
        assertEquals(
            "smp://runtime-song",
            root.getJSONObject("playlists").getJSONArray("Set").getString(0)
        )
        assertEquals(
            "file:/storage/runtime-song/audio.mp3",
            root.getJSONObject("lastPlayed").getString("uri")
        )
        assertTrue(success.warnings.isEmpty())
    }

    @Test
    fun prepareStateJsonForRestore_failsWhenRemapFails() {
        val result = BackupBundleRestorePreparer.prepareStateJsonForRestore(
            BackupBundleImportResult.Success(
                importedSongs = emptyList(),
                stateJson = """
                    {
                      "playlists": {
                        "Set": ["smp://missing-song"]
                      }
                    }
                """.trimIndent()
            )
        )

        val failure = result as BackupBundleRestorePreparationResult.RemapFailed
        assertEquals(1, failure.failures.size)
        assertTrue(failure.failures.first().reason.contains("missing-song"))
    }
}
