package com.patrick.lrcreader.core.sync

import com.patrick.lrcreader.core.PlaylistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

class SmpSyncManifestGeneratorTest {

    private val hashing = SmpSyncHashing()

    @Test
    fun textFileHash_normalizesLineEndings() {
        val dir = Files.createTempDirectory("sync_hash_text_").toFile()
        try {
            val lf = File(dir, "lf.lrc").apply {
                writeText("[00:00.00]Line one\n[00:01.00]Line two\n", Charsets.UTF_8)
            }
            val crlf = File(dir, "crlf.lrc").apply {
                writeText("[00:00.00]Line one\r\n[00:01.00]Line two\r\n", Charsets.UTF_8)
            }

            assertEquals(
                hashing.hashFileOrNull(lf, SmpSyncHashing.FileHashMode.NORMALIZED_TEXT),
                hashing.hashFileOrNull(crlf, SmpSyncHashing.FileHashMode.NORMALIZED_TEXT)
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun textFileHash_ignoresBomTrailingSpacesAndFinalBlankLines() {
        val dir = Files.createTempDirectory("sync_hash_text_clean_").toFile()
        try {
            val clean = File(dir, "clean.lrc").apply {
                writeText("[00:00.00]Café\n[00:01.00]Line two", Charsets.UTF_8)
            }
            val decorated = File(dir, "decorated.lrc").apply {
                writeText("\uFEFF[00:00.00]Cafe\u0301  \r\n[00:01.00]Line two\t\r\n\r\n", Charsets.UTF_8)
            }

            assertEquals(
                hashing.hashFileOrNull(clean, SmpSyncHashing.FileHashMode.SYNC_LYRICS_TEXT),
                hashing.hashFileOrNull(decorated, SmpSyncHashing.FileHashMode.SYNC_LYRICS_TEXT)
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun textFileHash_detectsRealLyricsChange() {
        val dir = Files.createTempDirectory("sync_hash_text_change_").toFile()
        try {
            val first = File(dir, "first.lrc").apply {
                writeText("[00:00.00]First lyric", Charsets.UTF_8)
            }
            val second = File(dir, "second.lrc").apply {
                writeText("[00:00.00]Second lyric", Charsets.UTF_8)
            }

            assertNotEquals(
                hashing.hashFileOrNull(first, SmpSyncHashing.FileHashMode.SYNC_LYRICS_TEXT),
                hashing.hashFileOrNull(second, SmpSyncHashing.FileHashMode.SYNC_LYRICS_TEXT)
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun bytesFileHash_matchesSha256ForTextFile() {
        val dir = Files.createTempDirectory("sync_hash_bytes_text_").toFile()
        try {
            val content = "Audio-like bytes kept exact\nwith LF only"
            val file = File(dir, "audio.txt").apply {
                writeText(content, Charsets.UTF_8)
            }

            assertEquals(
                hashing.sha256(content.toByteArray(Charsets.UTF_8)),
                hashing.hashFileOrNull(file, SmpSyncHashing.FileHashMode.BYTES)
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun bytesFileHash_handlesLargeFileWithSameSha256() {
        val dir = Files.createTempDirectory("sync_hash_bytes_large_").toFile()
        try {
            val file = File(dir, "large-audio.bin")
            val digestBytes = mutableListOf<Byte>()
            file.outputStream().use { output ->
                repeat(96) { block ->
                    val bytes = ByteArray(32 * 1024) { index ->
                        ((block * 31 + index) and 0xFF).toByte()
                    }
                    output.write(bytes)
                    digestBytes.addAll(bytes.toList())
                }
            }

            assertEquals(
                hashing.sha256(digestBytes.toByteArray()),
                hashing.hashFileOrNull(file, SmpSyncHashing.FileHashMode.BYTES)
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun emptyFileHash_isDistinctFromAbsentComponent() {
        val dir = Files.createTempDirectory("sync_hash_empty_").toFile()
        try {
            val empty = File(dir, "empty.lrc").apply {
                writeText("", Charsets.UTF_8)
            }

            val emptyHash = hashing.hashFileOrNull(empty, SmpSyncHashing.FileHashMode.NORMALIZED_TEXT)
            val absentHash = hashing.hashFileOrNull(File(dir, "missing.lrc"), SmpSyncHashing.FileHashMode.NORMALIZED_TEXT)

            assertNotNull(emptyHash)
            assertNull(absentHash)
            assertNotEquals(emptyHash, absentHash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun emptyBytesFileHash_isDistinctFromAbsentComponent() {
        val dir = Files.createTempDirectory("sync_hash_empty_bytes_").toFile()
        try {
            val empty = File(dir, "empty-audio.bin").apply {
                writeBytes(byteArrayOf())
            }

            val emptyHash = hashing.hashFileOrNull(empty, SmpSyncHashing.FileHashMode.BYTES)
            val absentHash = hashing.hashFileOrNull(File(dir, "missing-audio.bin"), SmpSyncHashing.FileHashMode.BYTES)

            assertNotNull(emptyHash)
            assertNull(absentHash)
            assertNotEquals(emptyHash, absentHash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun generateFromSources_buildsMinimalManifestFromTempFolder() {
        val dir = Files.createTempDirectory("sync_manifest_song_").toFile()
        try {
            val audio = File(dir, "audio.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val lyrics = File(dir, "lyrics.lrc").apply {
                writeText("[00:00.00]Bella ciao\n", Charsets.UTF_8)
            }
            val config = File(dir, "config.json").apply {
                writeText("""{"playback":{"gainDb":-3},"title":"Bella Ciao"}""", Charsets.UTF_8)
            }

            val manifest = runBlocking {
                SmpSyncManifestGenerator(hashing).generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 123L,
                    songs = listOf(
                        SmpSyncSongManifestSource(
                            songId = "song_001",
                            title = "Bella Ciao",
                            audioFile = audio,
                            lyricsFile = lyrics,
                            settingsFile = config
                        )
                    )
                )
            }

            assertEquals("0.3-beta", manifest.appVersion)
            assertEquals(123L, manifest.generatedAt)
            assertEquals(1, manifest.songs.size)
            assertEquals("song_001", manifest.songs.first().songId)
            assertNotNull(manifest.songs.first().audioHash)
            assertNotNull(manifest.songs.first().lyricsHash)
            assertNotNull(manifest.songs.first().settingsHash)
            assertTrue(manifest.playlists.isEmpty())
            assertTrue(manifest.families.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun playlistReferences_areNormalizedForSyncManifest() {
        val manifest = runBlocking {
            SmpSyncManifestGenerator(hashing).generateFromSources(
                appVersion = "0.3-beta",
                generatedAt = 1L,
                playlists = listOf(
                    SmpSyncPlaylistManifestSource(
                        playlistName = "Linda",
                        items = listOf(
                            PlaylistItem(uri = "smp://song_001"),
                            PlaylistItem(uri = "song:song_002"),
                            PlaylistItem(uri = "smp://", songId = "null"),
                            PlaylistItem(uri = "content://legacy/audio", songId = "song:song_003")
                        )
                    )
                )
            )
        }

        val playlist = manifest.playlists.single()
        assertEquals(listOf("song_001", "song_002", "song_003"), playlist.songIds)
        assertEquals(listOf("song_001", "song_002", "song_003"), playlist.itemKeys)
        assertEquals(listOf("invalid:null"), playlist.invalidReferences)
        assertEquals(3, playlist.itemCount)
    }

    @Test
    fun changedLyrics_producesDifferentSongHash() {
        val dir = Files.createTempDirectory("sync_manifest_changed_").toFile()
        try {
            val lyrics = File(dir, "lyrics.lrc").apply {
                writeText("[00:00.00]First version\n", Charsets.UTF_8)
            }
            val generator = SmpSyncManifestGenerator(hashing)
            val source = SmpSyncSongManifestSource(
                songId = "song_001",
                title = "Bella Ciao",
                lyricsFile = lyrics
            )

            val before = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 1L,
                    songs = listOf(source)
                )
            }.songs.first()

            lyrics.writeText("[00:00.00]Second version\n", Charsets.UTF_8)

            val after = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 2L,
                    songs = listOf(source)
                )
            }.songs.first()

            assertNotEquals(before.lyricsHash, after.lyricsHash)
            assertNotEquals(before.fullSongHash, after.fullSongHash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun localOnlySettingsFields_doNotChangeSettingsHash() {
        val dir = Files.createTempDirectory("sync_manifest_settings_local_").toFile()
        try {
            val config = File(dir, "config.json").apply {
                writeText(
                    """
                    {
                      "id": "song_a",
                      "title": "Phone A title",
                      "files": { "audio": "/absolute/local/a.mp3" },
                      "playback": { "trimStartMs": 1200, "tempo": 1.1, "pitchSemi": -1, "volumeDb": -3 },
                      "ui": { "scroll": 42, "expanded": true },
                      "updatedAt": 111
                    }
                    """.trimIndent(),
                    Charsets.UTF_8
                )
            }
            val generator = SmpSyncManifestGenerator(hashing)
            val source = SmpSyncSongManifestSource(
                songId = "song_001",
                title = "Sync Title",
                settingsFile = config
            )
            val before = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 1L,
                    songs = listOf(source)
                )
            }.songs.first()

            config.writeText(
                """
                {
                  "id": "song_b",
                  "title": "Phone B title",
                  "files": { "audio": "/other/device/b.mp3" },
                  "playback": { "trimStartMs": 1200, "tempo": 1.1, "pitchSemi": -1, "volumeDb": -3 },
                  "ui": { "scroll": 999, "expanded": false },
                  "updatedAt": 999
                }
                """.trimIndent(),
                Charsets.UTF_8
            )

            val after = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 2L,
                    songs = listOf(source)
                )
            }.songs.first()

            assertEquals(before.settingsHash, after.settingsHash)
            assertEquals(before.fullSongHash, after.fullSongHash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun onlyLocalSettingsFields_produceNoSettingsHash() {
        val dir = Files.createTempDirectory("sync_manifest_settings_local_only_").toFile()
        try {
            val config = File(dir, "config.json").apply {
                writeText(
                    """{"id":"song_a","title":"Local title","files":{"audio":"/phone/a.mp3"},"ui":{"zoom":1.5},"updatedAt":123}""",
                    Charsets.UTF_8
                )
            }

            val manifest = runBlocking {
                SmpSyncManifestGenerator(hashing).generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 1L,
                    songs = listOf(
                        SmpSyncSongManifestSource(
                            songId = "song_001",
                            title = "Sync Title",
                            settingsFile = config
                        )
                    )
                )
            }

            assertNull(manifest.songs.first().settingsHash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun musicalPlaybackSettingChange_updatesSettingsHash() {
        val dir = Files.createTempDirectory("sync_manifest_settings_music_").toFile()
        try {
            val config = File(dir, "config.json").apply {
                writeText("""{"playback":{"trimStartMs":1200,"tempo":1.1,"pitchSemi":-1,"volumeDb":-3}}""", Charsets.UTF_8)
            }
            val generator = SmpSyncManifestGenerator(hashing)
            val source = SmpSyncSongManifestSource(
                songId = "song_001",
                title = "Sync Title",
                settingsFile = config
            )
            val before = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 1L,
                    songs = listOf(source)
                )
            }.songs.first()

            config.writeText("""{"playback":{"trimStartMs":1200,"tempo":1.2,"pitchSemi":-1,"volumeDb":-3}}""", Charsets.UTF_8)

            val after = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 2L,
                    songs = listOf(source)
                )
            }.songs.first()

            assertNotEquals(before.settingsHash, after.settingsHash)
            assertNotEquals(before.fullSongHash, after.fullSongHash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun lufsPlaybackSettingChange_updatesSettingsHash() {
        val dir = Files.createTempDirectory("sync_manifest_settings_lufs_").toFile()
        try {
            val config = File(dir, "config.json").apply {
                writeText(
                    """
                    {
                      "playback": {
                        "volumeDb": 4,
                        "volumeSource": "lufs",
                        "lufsMeasured": -18.2,
                        "lufsTarget": -14.0,
                        "lufsAutoDb": 4.2,
                        "lufsManualDb": 0
                      }
                    }
                    """.trimIndent(),
                    Charsets.UTF_8
                )
            }
            val generator = SmpSyncManifestGenerator(hashing)
            val source = SmpSyncSongManifestSource(
                songId = "song_001",
                title = "Sync Title",
                settingsFile = config
            )
            val before = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 1L,
                    songs = listOf(source)
                )
            }.songs.first()

            config.writeText(
                """
                {
                  "playback": {
                    "volumeDb": 6,
                    "volumeSource": "lufs",
                    "lufsMeasured": -18.2,
                    "lufsTarget": -14.0,
                    "lufsAutoDb": 4.2,
                    "lufsManualDb": 2
                  }
                }
                """.trimIndent(),
                Charsets.UTF_8
            )

            val after = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 2L,
                    songs = listOf(source)
                )
            }.songs.first()

            assertNotEquals(before.settingsHash, after.settingsHash)
            assertNotEquals(before.fullSongHash, after.fullSongHash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun arrangementUpdatedAtOnly_doesNotChangeArrangementHash() {
        val dir = Files.createTempDirectory("sync_manifest_arrangement_").toFile()
        try {
            val arrangement = File(dir, "arrangement.json").apply {
                writeText(
                    """{"version":1,"name":"Arrangement 1","sourceSongId":"song_001","updatedAt":111,"segments":[{"id":"a","name":"Intro","startMs":0,"endMs":1000}],"structureSegmentIds":["a"]}""",
                    Charsets.UTF_8
                )
            }
            val generator = SmpSyncManifestGenerator(hashing)
            val source = SmpSyncSongManifestSource(
                songId = "song_001",
                title = "Sync Title",
                arrangementFile = arrangement
            )
            val before = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 1L,
                    songs = listOf(source)
                )
            }.songs.first()

            arrangement.writeText(
                """{"version":1,"name":"Arrangement 1","sourceSongId":"song_001","updatedAt":999,"segments":[{"id":"a","name":"Intro","startMs":0,"endMs":1000}],"structureSegmentIds":["a"]}""",
                Charsets.UTF_8
            )

            val after = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 2L,
                    songs = listOf(source)
                )
            }.songs.first()

            assertEquals(before.arrangementHash, after.arrangementHash)
            assertEquals(before.fullSongHash, after.fullSongHash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun arrangementLocalFields_doNotChangeArrangementHash() {
        val dir = Files.createTempDirectory("sync_manifest_arrangement_local_").toFile()
        try {
            val arrangement = File(dir, "arrangement.json").apply {
                writeText(
                    """{"version":1,"name":"Arrangement 1","sourceSongId":"song_a","updatedAt":111,"selectedSegmentId":"a","segments":[{"id":"a","name":"Intro","startMs":0,"endMs":1000,"expanded":true,"cacheKey":"phone-a"}],"structureSegmentIds":["a"],"ui":{"zoom":1.2}}""",
                    Charsets.UTF_8
                )
            }
            val generator = SmpSyncManifestGenerator(hashing)
            val source = SmpSyncSongManifestSource(
                songId = "song_001",
                title = "Sync Title",
                arrangementFile = arrangement
            )
            val before = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 1L,
                    songs = listOf(source)
                )
            }.songs.first()

            arrangement.writeText(
                """{"version":9,"name":"Arrangement 1","sourceSongId":"song_b","updatedAt":999,"selectedSegmentId":"b","segments":[{"id":"a","name":"Intro","startMs":0,"endMs":1000,"expanded":false,"cacheKey":"phone-b"}],"structureSegmentIds":["a"],"ui":{"zoom":2.0}}""",
                Charsets.UTF_8
            )

            val after = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 2L,
                    songs = listOf(source)
                )
            }.songs.first()

            assertEquals(before.arrangementHash, after.arrangementHash)
            assertEquals(before.fullSongHash, after.fullSongHash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun arrangementMusicalChange_updatesArrangementHash() {
        val dir = Files.createTempDirectory("sync_manifest_arrangement_music_").toFile()
        try {
            val arrangement = File(dir, "arrangement.json").apply {
                writeText(
                    """{"name":"Arrangement 1","segments":[{"id":"a","name":"Intro","startMs":0,"endMs":1000}],"structureSegmentIds":["a"]}""",
                    Charsets.UTF_8
                )
            }
            val generator = SmpSyncManifestGenerator(hashing)
            val source = SmpSyncSongManifestSource(
                songId = "song_001",
                title = "Sync Title",
                arrangementFile = arrangement
            )
            val before = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 1L,
                    songs = listOf(source)
                )
            }.songs.first()

            arrangement.writeText(
                """{"name":"Arrangement 1","segments":[{"id":"a","name":"Intro","startMs":0,"endMs":1200}],"structureSegmentIds":["a"]}""",
                Charsets.UTF_8
            )

            val after = runBlocking {
                generator.generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 2L,
                    songs = listOf(source)
                )
            }.songs.first()

            assertNotEquals(before.arrangementHash, after.arrangementHash)
            assertNotEquals(before.fullSongHash, after.fullSongHash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun absentComponent_doesNotFailManifestGeneration() {
        val dir = Files.createTempDirectory("sync_manifest_absent_").toFile()
        try {
            val missingLyrics = File(dir, "missing.lrc")

            val manifest = runBlocking {
                SmpSyncManifestGenerator(hashing).generateFromSources(
                    appVersion = "0.3-beta",
                    generatedAt = 1L,
                    songs = listOf(
                        SmpSyncSongManifestSource(
                            songId = "song_001",
                            title = "Song Without Lyrics",
                            lyricsFile = missingLyrics
                        )
                    )
                )
            }

            val song = manifest.songs.first()
            assertNull(song.lyricsHash)
            assertNotNull(song.fullSongHash)
        } finally {
            dir.deleteRecursively()
        }
    }
}
