package com.patrick.lrcreader.smp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SmpWorkspaceArchiveStoreTest {

    @Test
    fun buildDurableArchiveFileName_usesReadableTitleAndKeepsSongIdSuffix() {
        val fileName = SmpWorkspaceArchiveStore.buildDurableArchiveFileName(
            songUnit(id = "song_001", title = "Live:Demo/01")
        )

        assertEquals("Live_Demo_01 [song_001].smp", fileName)
    }

    @Test
    fun buildDurableArchiveFileName_fallsBackToSongIdWhenTitleIsBlank() {
        val fileName = SmpWorkspaceArchiveStore.buildDurableArchiveFileName(
            songUnit(id = "song_001", title = "   ")
        )

        assertEquals("song_001.smp", fileName)
    }

    @Test
    fun isSupportedArchiveFileName_acceptsSmpAndLegacySmpZip() {
        assertTrue(SmpWorkspaceArchiveStore.isSupportedArchiveFileName("song.smp"))
        assertTrue(SmpWorkspaceArchiveStore.isSupportedArchiveFileName("song.smp.zip"))
        assertFalse(SmpWorkspaceArchiveStore.isSupportedArchiveFileName("song.zip"))
    }

    @Test
    fun writeArchiveToFileDir_replacesLegacySongIdArchiveWithReadableName() {
        withTempDir { targetDir ->
            val legacyFile = createArchiveWithSongId(
                targetDir = targetDir,
                fileName = "song_001.smp",
                songId = "song_001"
            )
            val tempArchive = File.createTempFile("new_archive_", ".tmp", targetDir).apply {
                writeBytes(byteArrayOf(1, 2, 3))
            }

            val result = SmpWorkspaceArchiveStore.writeArchiveToFileDirInternal(
                targetDir = targetDir,
                songId = "song_001",
                targetName = "Readable Title [song_001].smp",
                tempArchive = tempArchive
            )

            val canonical = File(targetDir, "Readable Title [song_001].smp")
            assertNotNull(result.archiveFile)
            assertTrue(canonical.isFile)
            assertFalse(legacyFile.exists())
            assertArrayEquals(byteArrayOf(1, 2, 3), canonical.readBytes())
        }
    }

    @Test
    fun writeArchiveToFileDir_replacesPreviousReadableArchiveForSameSongId() {
        withTempDir { targetDir ->
            val oldCanonical = createArchiveWithSongId(
                targetDir = targetDir,
                fileName = "Ancien titre [song_001].smp",
                songId = "song_001"
            )
            val tempArchive = File.createTempFile("new_archive_", ".tmp", targetDir).apply {
                writeBytes(byteArrayOf(4, 5, 6))
            }

            val result = SmpWorkspaceArchiveStore.writeArchiveToFileDirInternal(
                targetDir = targetDir,
                songId = "song_001",
                targetName = "Nouveau titre [song_001].smp",
                tempArchive = tempArchive
            )

            val canonical = File(targetDir, "Nouveau titre [song_001].smp")
            assertNotNull(result.archiveFile)
            assertTrue(canonical.isFile)
            assertFalse(oldCanonical.exists())
            assertArrayEquals(byteArrayOf(4, 5, 6), canonical.readBytes())
        }
    }

    @Test
    fun writeArchiveToFileDir_overwritesExistingCanonicalArchiveWithoutDuplicate() {
        withTempDir { targetDir ->
            val canonical = createArchiveWithSongId(
                targetDir = targetDir,
                fileName = "Readable Title [song_001].smp",
                songId = "song_001"
            )
            val tempArchive = File.createTempFile("new_archive_", ".tmp", targetDir).apply {
                writeBytes(byteArrayOf(7, 8, 9))
            }

            val result = SmpWorkspaceArchiveStore.writeArchiveToFileDirInternal(
                targetDir = targetDir,
                songId = "song_001",
                targetName = canonical.name,
                tempArchive = tempArchive
            )

            assertNotNull(result.archiveFile)
            assertTrue(canonical.isFile)
            assertArrayEquals(byteArrayOf(7, 8, 9), canonical.readBytes())
            assertEquals(
                listOf(canonical.name),
                targetDir.listFiles()
                    .orEmpty()
                    .filter { it.extension.equals("smp", ignoreCase = true) }
                    .map { it.name }
            )
        }
    }

    @Test
    fun writeArchiveToFileDir_removesExoticArchivesWithSameSongId() {
        withTempDir { targetDir ->
            val plainTitle = createArchiveWithSongId(
                targetDir = targetDir,
                fileName = "Readable Title.smp",
                songId = "song_001"
            )
            val numberedTitle = createArchiveWithSongId(
                targetDir = targetDir,
                fileName = "Readable Title (1).smp",
                songId = "song_001"
            )
            val legacyZipName = createArchiveWithSongId(
                targetDir = targetDir,
                fileName = "Readable Title [song_001].smp.zip",
                songId = "song_001"
            )
            createArchiveWithSongId(
                targetDir = targetDir,
                fileName = "Other Song.smp",
                songId = "song_999"
            )
            val tempArchive = File.createTempFile("new_archive_", ".tmp", targetDir).apply {
                writeBytes(byteArrayOf(10, 11, 12))
            }

            val result = SmpWorkspaceArchiveStore.writeArchiveToFileDirInternal(
                targetDir = targetDir,
                songId = "song_001",
                targetName = "Readable Title [song_001].smp",
                tempArchive = tempArchive
            )

            val canonical = File(targetDir, "Readable Title [song_001].smp")
            assertNotNull(result.archiveFile)
            assertTrue(canonical.isFile)
            assertFalse(plainTitle.exists())
            assertFalse(numberedTitle.exists())
            assertFalse(legacyZipName.exists())
            assertEquals(
                listOf("Other Song.smp", canonical.name).sorted(),
                targetDir.listFiles()
                    .orEmpty()
                    .filter { SmpWorkspaceArchiveStore.isSupportedArchiveFileName(it.name) }
                    .map { it.name }
                    .sorted()
            )
        }
    }

    @Test
    fun writeArchiveToFileDir_keepsUnresolvedArchives() {
        withTempDir { targetDir ->
            val unresolved = File(targetDir, "Readable Title.smp").apply { writeText("broken") }
            val tempArchive = File.createTempFile("new_archive_", ".tmp", targetDir).apply {
                writeBytes(byteArrayOf(13, 14, 15))
            }

            val result = SmpWorkspaceArchiveStore.writeArchiveToFileDirInternal(
                targetDir = targetDir,
                songId = "song_001",
                targetName = "Readable Title [song_001].smp",
                tempArchive = tempArchive
            )

            val canonical = File(targetDir, "Readable Title [song_001].smp")
            assertNotNull(result.archiveFile)
            assertTrue(canonical.isFile)
            assertTrue(unresolved.exists())
            assertEquals(
                listOf(canonical.name, unresolved.name).sorted(),
                targetDir.listFiles()
                    .orEmpty()
                    .filter { it.extension.equals("smp", ignoreCase = true) }
                    .map { it.name }
                    .sorted()
            )
        }
    }

    private fun songUnit(id: String, title: String) = SongUnit(
        id = id,
        title = title,
        audioPath = null,
        lyricsPath = null,
        chordsPath = null,
        annotationsPath = null,
        midiPath = null,
        dmxPath = null,
        prompterPath = null
    )

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("smp_archive_store_test_").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun createArchiveWithSongId(
        targetDir: File,
        fileName: String,
        songId: String
    ): File {
        val archive = File(targetDir, fileName)
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("config.json"))
            zip.write("""{"id":"$songId"}""".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return archive
    }
}
