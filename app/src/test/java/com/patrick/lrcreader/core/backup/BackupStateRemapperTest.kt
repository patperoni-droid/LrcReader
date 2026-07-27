package com.patrick.lrcreader.core.backup

import com.patrick.lrcreader.core.buildGroupEnd
import com.patrick.lrcreader.core.buildGroupHeader
import com.patrick.lrcreader.core.getGroupUuid
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
    fun remapBundleStateJson_preservesGroupAroundParentAndVariantWithLegacyNullSongIds() {
        val parentDir = createImportedSongDir("parent")
        val variantDir = createImportedSongDir("variant")
        val header = buildGroupHeader("Concert")
        val end = buildGroupEnd(getGroupUuid(header)!!)
        try {
            val result = BackupStateRemapper.remapBundleStateJson(
                stateJson = JSONObject().apply {
                    put(
                        "playlists",
                        JSONObject().apply {
                            put(
                                "Live",
                                org.json.JSONArray().apply {
                                    put(JSONObject().put("uri", header).put("songId", "null"))
                                    put(JSONObject().put("uri", "smp://parent").put("songId", "parent"))
                                    put(JSONObject().put("uri", "smp://variant").put("songId", "variant"))
                                    put(JSONObject().put("uri", end).put("songId", "null"))
                                }
                            )
                        }
                    )
                }.toString(),
                importedSongs = listOf(
                    BackupBundleImportedSong(
                        bundleSongId = "parent",
                        importedSongId = "parent",
                        storageFolder = parentDir.absolutePath
                    ),
                    BackupBundleImportedSong(
                        bundleSongId = "variant",
                        importedSongId = "variant",
                        storageFolder = variantDir.absolutePath
                    )
                )
            )

            val success = result as BackupStateRemapResult.Success
            val items = JSONObject(success.stateJson)
                .getJSONObject("playlists")
                .getJSONArray("Live")

            assertEquals(4, items.length())
            assertEquals(header, items.getJSONObject(0).getString("uri"))
            assertFalse(items.getJSONObject(0).has("songId"))
            assertEquals("smp://parent", items.getJSONObject(1).getString("uri"))
            assertEquals("smp://variant", items.getJSONObject(2).getString("uri"))
            assertEquals(end, items.getJSONObject(3).getString("uri"))
            assertFalse(items.getJSONObject(3).has("songId"))
        } finally {
            parentDir.deleteRecursively()
            variantDir.deleteRecursively()
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
