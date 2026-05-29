package com.patrick.lrcreader.core.sync

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
