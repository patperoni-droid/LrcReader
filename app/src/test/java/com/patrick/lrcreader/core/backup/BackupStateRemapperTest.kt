package com.patrick.lrcreader.core.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BackupStateRemapperTest {

    @Test
    fun remapBundleStateJson_remapsPlaylistMarkersAndRuntimeUris() {
        val songDir = createImportedSongDir("song_new")
        try {
            val oldRuntimeUri = File("/old/device/files/tracks/song_old/audio.mp3").toURI().toString()
            val newRuntimeUri = File(songDir, "audio.mp3").toURI().toString()
            val stateJson = """
                {
                  "playlists": {
                    "Live": ["smp://song_old", "file:///music/direct.mp3"]
                  },
                  "played": {
                    "Live": ["smp://song_old"]
                  },
                  "review": {
                    "Live": ["smp://song_old"]
                  },
                  "lastPlayed": {
                    "uri": "$oldRuntimeUri",
                    "playlistName": "Live",
                    "positionMs": 1234
                  },
                  "fillerSound": {
                    "uri": "$oldRuntimeUri",
                    "volume": 0.5
                  },
                  "edits": {
                    "$oldRuntimeUri": {
                      "startMs": 100,
                      "endMs": 200
                    }
                  }
                }
            """.trimIndent()

            val result = BackupStateRemapper.remapBundleStateJson(
                stateJson = stateJson,
                importedSongs = listOf(
                    BackupBundleImportedSong(
                        bundleSongId = "song_old",
                        importedSongId = "song_new",
                        storageFolder = songDir.absolutePath
                    )
                )
            )

            val success = result as BackupStateRemapResult.Success
            assertTrue(success.warnings.isEmpty())

            val root = JSONObject(success.stateJson)
            assertEquals(
                "smp://song_new",
                root.getJSONObject("playlists").getJSONArray("Live").getString(0)
            )
            assertEquals(
                "file:///music/direct.mp3",
                root.getJSONObject("playlists").getJSONArray("Live").getString(1)
            )
            assertEquals(
                "smp://song_new",
                root.getJSONObject("played").getJSONArray("Live").getString(0)
            )
            assertEquals(
                "smp://song_new",
                root.getJSONObject("review").getJSONArray("Live").getString(0)
            )
            assertEquals(
                newRuntimeUri,
                root.getJSONObject("lastPlayed").getString("uri")
            )
            assertEquals(
                newRuntimeUri,
                root.getJSONObject("fillerSound").getString("uri")
            )
            assertTrue(root.getJSONObject("edits").has(newRuntimeUri))
            assertFalse(root.getJSONObject("edits").has(oldRuntimeUri))
        } finally {
            songDir.deleteRecursively()
        }
    }

    @Test
    fun remapBundleStateJson_keepsSmpMarkerUnchangedWhenSongIdDidNotChange() {
        val songDir = createImportedSongDir("song_same")
        try {
            val result = BackupStateRemapper.remapBundleStateJson(
                stateJson = """
                    {
                      "playlists": {
                        "Live": ["smp://song_same"]
                      }
                    }
                """.trimIndent(),
                importedSongs = listOf(
                    BackupBundleImportedSong(
                        bundleSongId = "song_same",
                        importedSongId = "song_same",
                        storageFolder = songDir.absolutePath
                    )
                )
            )

            val success = result as BackupStateRemapResult.Success
            val root = JSONObject(success.stateJson)
            assertEquals(
                "smp://song_same",
                root.getJSONObject("playlists").getJSONArray("Live").getString(0)
            )
        } finally {
            songDir.deleteRecursively()
        }
    }

    @Test
    fun remapBundleStateJson_dropsOptionalRuntimeRefsWhenSongWasNotImported() {
        val missingRuntimeUri = File("/old/device/files/tracks/song_missing/audio.mp3").toURI().toString()
        val result = BackupStateRemapper.remapBundleStateJson(
            stateJson = """
                {
                  "lastPlayed": {
                    "uri": "$missingRuntimeUri",
                    "playlistName": "Live",
                    "positionMs": 55
                  },
                  "fillerSound": {
                    "uri": "$missingRuntimeUri",
                    "volume": 0.5
                  },
                  "edits": {
                    "$missingRuntimeUri": {
                      "startMs": 10,
                      "endMs": 20
                    }
                  }
                }
            """.trimIndent(),
            importedSongs = emptyList()
        )

        val success = result as BackupStateRemapResult.Success
        val root = JSONObject(success.stateJson)
        assertFalse(root.has("lastPlayed"))
        assertFalse(root.has("fillerSound"))
        assertFalse(root.has("edits"))
        assertEquals(3, success.warnings.size)
    }

    @Test
    fun remapBundleStateJson_failsWhenPlaylistSmpReferenceCannotBeRemapped() {
        val result = BackupStateRemapper.remapBundleStateJson(
            stateJson = """
                {
                  "playlists": {
                    "Live": ["smp://song_missing"]
                  }
                }
            """.trimIndent(),
            importedSongs = emptyList()
        )

        val failure = result as BackupStateRemapResult.Failure
        assertEquals(1, failure.failures.size)
        assertEquals("playlists.Live[0]", failure.failures.first().path)
        assertEquals("smp://song_missing", failure.failures.first().value)
    }

    private fun createImportedSongDir(songId: String): File {
        val songDir = Files.createTempDirectory(songId).toFile()
        File(songDir, "audio.mp3").writeText("audio")
        return songDir
    }
}
