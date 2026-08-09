package com.patrick.lrcreader.smp

import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.ui.editorRawTextForLoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ArrangementVariantLyricsEditorRawRoundTripTest {

    @Test
    fun familyRoundTrip_preservesParentAndTwoVariantRawDraftsExactly() {
        val root = Files.createTempDirectory("lyrics_editor_family_round_trip_").toFile()
        try {
            val runtimeRoot = root.resolve("runtime").apply { mkdirs() }
            val restoredRoot = root.resolve("restored").apply { mkdirs() }
            val parentRaw = "\n  Parent, couplet  \n\n\n\tParent refrain\n"
            val variantARaw = " Variante A \n\n\n  ligne avec espaces  \n\tfin A\n"
            val variantBRaw = "\r\nÉté — variante B\r\n\tPont\tB\r\n\r\n"

            val parentDir = createSongDir(runtimeRoot, PARENT_ID).apply {
                resolve("lyrics.lrc").writeText("[00:01.00]Parent\n", Charsets.UTF_8)
                resolve(RAW_FILE_NAME).writeText(parentRaw, Charsets.UTF_8)
            }
            val variantADir = createSongDir(runtimeRoot, VARIANT_A_ID).apply {
                resolve("lyrics.lrc").writeText("", Charsets.UTF_8)
                resolve(RAW_FILE_NAME).writeText(variantARaw, Charsets.UTF_8)
            }
            val variantBDir = createSongDir(runtimeRoot, VARIANT_B_ID).apply {
                resolve(RAW_FILE_NAME).writeText(variantBRaw, Charsets.UTF_8)
            }

            val parent = songUnit(PARENT_ID, parentDir, parentDir.resolve("lyrics.lrc"))
            val parentRawPath = requireNotNull(SmpExporter.resolveLyricsEditorRawPathForExport(parent))
            val transportedParentRaw = File(parentRawPath).readText(Charsets.UTF_8)
            val archive = ArrangementVariantsArchiveCodec.decode(
                ArrangementVariantsArchiveCodec.encode(
                    ArrangementVariantsArchive(
                        sourceSongId = PARENT_ID,
                        variants = listOf(
                            archiveEntryFor(VARIANT_A_ID, variantADir),
                            archiveEntryFor(VARIANT_B_ID, variantBDir)
                        )
                    )
                )
            )

            runtimeRoot.deleteRecursively()

            val restoredParentDir = createSongDir(restoredRoot, PARENT_ID)
            restoredParentDir.resolve(RAW_FILE_NAME).writeText(transportedParentRaw, Charsets.UTF_8)
            archive.variants.forEach { variant ->
                val targetDir = createSongDir(restoredRoot, variant.id)
                ArrangementVariantStore.writeVariantFiles(
                    targetDir = targetDir,
                    variantId = variant.id,
                    title = variant.title,
                    sourceSongId = PARENT_ID,
                    arrangement = variant.arrangement,
                    archivedLyrics = variant.lyrics,
                    archivedLyricsEditorRaw = variant.lyricsEditorRaw
                )
            }

            assertRawEquals(parentRaw, restoredParentDir.resolve(RAW_FILE_NAME))
            assertRawEquals(variantARaw, restoredRoot.resolve(VARIANT_A_ID).resolve(RAW_FILE_NAME))
            assertRawEquals(variantBRaw, restoredRoot.resolve(VARIANT_B_ID).resolve(RAW_FILE_NAME))
            assertTrue(restoredRoot.resolve(VARIANT_A_ID).resolve("lyrics.lrc").isFile)
            assertEquals(0L, restoredRoot.resolve(VARIANT_A_ID).resolve("lyrics.lrc").length())
            assertFalse(restoredRoot.resolve(VARIANT_B_ID).resolve("lyrics.lrc").exists())
            assertNotEquals(parentRaw, variantARaw)
            assertNotEquals(variantARaw, variantBRaw)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun explicitlyEmptyRawDraft_remainsPresentAndWinsOverFallback() {
        val root = Files.createTempDirectory("lyrics_editor_empty_round_trip_").toFile()
        try {
            val decoded = ArrangementVariantsArchiveCodec.decode(
                ArrangementVariantsArchiveCodec.encode(
                    archiveWithRaw(rawText = "", lyrics = "[00:01.00]Fallback")
                )
            ).variants.single()

            ArrangementVariantStore.writeVariantFiles(
                targetDir = root,
                variantId = decoded.id,
                title = decoded.title,
                sourceSongId = PARENT_ID,
                arrangement = decoded.arrangement,
                archivedLyrics = decoded.lyrics,
                archivedLyricsEditorRaw = decoded.lyricsEditorRaw
            )

            val rawFile = root.resolve(RAW_FILE_NAME)
            assertTrue(rawFile.isFile)
            assertEquals(0L, rawFile.length())
            assertEquals("", LrcStorage.loadEditorRawFromSongDir(root))
            assertEquals("", editorRawTextForLoad("", "[00:01.00]Fallback"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun legacyVariantArchiveWithNewLyrics_removesStaleRawAndUsesFallback() {
        val root = Files.createTempDirectory("lyrics_editor_selective_replace_").toFile()
        try {
            val existing = root.resolve("existing").apply { mkdirs() }
            val target = root.resolve("target").apply { mkdirs() }
            existing.resolve(RAW_FILE_NAME).writeText("Ancien brouillon\n\nObsolète", Charsets.UTF_8)
            existing.resolve("lyrics.lrc").writeText("Anciennes paroles", Charsets.UTF_8)
            val importedLyrics = "[00:01.00]Nouvelles paroles"

            ArrangementVariantStore.writeVariantFiles(
                targetDir = target,
                variantId = VARIANT_A_ID,
                title = "Variante A",
                sourceSongId = PARENT_ID,
                arrangement = arrangementFor(VARIANT_A_ID),
                existingVariantDir = existing,
                archivedLyrics = importedLyrics
            )

            assertFalse(target.resolve(RAW_FILE_NAME).exists())
            assertEquals(importedLyrics, target.resolve("lyrics.lrc").readText(Charsets.UTF_8))
            assertEquals(importedLyrics, editorRawTextForLoad(null, importedLyrics))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun legacyVariantArchiveWithoutRawOrLyrics_preservesLocalDraft() {
        val root = Files.createTempDirectory("lyrics_editor_selective_preserve_").toFile()
        try {
            val existing = root.resolve("existing").apply { mkdirs() }
            val target = root.resolve("target").apply { mkdirs() }
            val localRaw = "  Brouillon local  \n\n"
            existing.resolve(RAW_FILE_NAME).writeText(localRaw, Charsets.UTF_8)

            ArrangementVariantStore.writeVariantFiles(
                targetDir = target,
                variantId = VARIANT_A_ID,
                title = "Variante A",
                sourceSongId = PARENT_ID,
                arrangement = arrangementFor(VARIANT_A_ID),
                existingVariantDir = existing
            )

            assertRawEquals(localRaw, target.resolve(RAW_FILE_NAME))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun parentLegacyReplacementWithoutRaw_removesStaleDraftAndFallsBackToImportedLyrics() {
        val root = Files.createTempDirectory("lyrics_editor_parent_legacy_replace_").toFile()
        try {
            val destination = root.resolve(PARENT_ID).apply { mkdirs() }
            destination.resolve(RAW_FILE_NAME).writeText("Ancien parent brut", Charsets.UTF_8)
            destination.resolve("lyrics.lrc").writeText("Ancien parent LRC", Charsets.UTF_8)
            val staging = root.resolve("staging").apply { mkdirs() }
            val importedLyrics = "[00:02.00]Parent importé"
            staging.resolve("lyrics.lrc").writeText(importedLyrics, Charsets.UTF_8)

            destination.deleteRecursively()
            assertTrue(staging.renameTo(destination))

            assertFalse(destination.resolve(RAW_FILE_NAME).exists())
            assertEquals(importedLyrics, editorRawTextForLoad(null, importedLyrics))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun targetedVariantRestore_doesNotModifySiblingRawDraft() {
        val root = Files.createTempDirectory("lyrics_editor_targeted_restore_").toFile()
        try {
            val selectedDir = root.resolve(VARIANT_A_ID).apply { mkdirs() }
            val siblingDir = root.resolve(VARIANT_B_ID).apply { mkdirs() }
            val selectedRaw = "Variante ciblée\n\n  exacte  \n"
            val siblingRaw = "Sœur locale\n\tinchangée\n"
            siblingDir.resolve(RAW_FILE_NAME).writeText(siblingRaw, Charsets.UTF_8)
            val decoded = ArrangementVariantsArchiveCodec.decode(
                ArrangementVariantsArchiveCodec.encode(
                    ArrangementVariantsArchive(
                        sourceSongId = PARENT_ID,
                        selectedVariantId = VARIANT_A_ID,
                        variants = listOf(
                            ArrangementVariantArchiveEntry(
                                id = VARIANT_A_ID,
                                title = "Variante A",
                                arrangement = arrangementFor(VARIANT_A_ID),
                                lyricsEditorRaw = selectedRaw
                            )
                        )
                    )
                )
            )

            decoded.variants.forEach { variant ->
                ArrangementVariantStore.writeVariantFiles(
                    targetDir = selectedDir,
                    variantId = variant.id,
                    title = variant.title,
                    sourceSongId = PARENT_ID,
                    arrangement = variant.arrangement,
                    archivedLyricsEditorRaw = variant.lyricsEditorRaw
                )
            }

            assertEquals(VARIANT_A_ID, decoded.selectedVariantId)
            assertRawEquals(selectedRaw, selectedDir.resolve(RAW_FILE_NAME))
            assertRawEquals(siblingRaw, siblingDir.resolve(RAW_FILE_NAME))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun parentRawDraft_isExportableWithoutLyricsLrc() {
        val root = Files.createTempDirectory("lyrics_editor_parent_without_lrc_").toFile()
        try {
            createSongDir(root, PARENT_ID).resolve(RAW_FILE_NAME).writeText(
                "Texte brut seul\n\n",
                Charsets.UTF_8
            )
            val parentDir = root.resolve(PARENT_ID)
            val parent = songUnit(PARENT_ID, parentDir, lyricsFile = null)

            val rawPath = SmpExporter.resolveLyricsEditorRawPathForExport(parent)

            assertEquals(parentDir.resolve(RAW_FILE_NAME).absolutePath, rawPath)
            assertNull(parent.lyricsPath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun codec_rejectsRawDraftOverSizeLimit() {
        val oversized = "é".repeat(ArrangementVariantsArchiveCodec.MAX_LYRICS_EDITOR_RAW_BYTES / 2 + 1)

        assertThrows(IllegalArgumentException::class.java) {
            ArrangementVariantsArchiveCodec.encode(archiveWithRaw(oversized, lyrics = null))
        }
    }

    @Test
    fun parentExporter_rejectsRawDraftOverSizeLimit() {
        val root = Files.createTempDirectory("lyrics_editor_parent_size_limit_").toFile()
        try {
            val parentDir = createSongDir(root, PARENT_ID)
            parentDir.resolve(RAW_FILE_NAME).writeText(
                "é".repeat(ArrangementVariantsArchiveCodec.MAX_LYRICS_EDITOR_RAW_BYTES / 2 + 1),
                Charsets.UTF_8
            )

            assertThrows(IllegalArgumentException::class.java) {
                SmpExporter.resolveLyricsEditorRawPathForExport(
                    songUnit(PARENT_ID, parentDir, lyricsFile = null)
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun archiveEntryFor(variantId: String, variantDir: File): ArrangementVariantArchiveEntry {
        val lyricsFile = variantDir.resolve("lyrics.lrc")
        val rawFile = SmpExporter.resolveLyricsEditorRawPathForExport(
            songUnit(variantId, variantDir, lyricsFile.takeIf(File::isFile))
        )?.let(::File)
        return ArrangementVariantArchiveEntry(
            id = variantId,
            title = variantId,
            arrangement = arrangementFor(variantId),
            lyrics = lyricsFile.takeIf(File::isFile)?.readText(Charsets.UTF_8),
            lyricsEditorRaw = rawFile?.readText(Charsets.UTF_8)
        )
    }

    private fun archiveWithRaw(rawText: String, lyrics: String?) = ArrangementVariantsArchive(
        sourceSongId = PARENT_ID,
        variants = listOf(
            ArrangementVariantArchiveEntry(
                id = VARIANT_A_ID,
                title = "Variante A",
                arrangement = arrangementFor(VARIANT_A_ID),
                lyrics = lyrics,
                lyricsEditorRaw = rawText
            )
        )
    )

    private fun createSongDir(root: File, songId: String): File =
        root.resolve(songId).apply { mkdirs() }

    private fun songUnit(id: String, directory: File, lyricsFile: File?) = SongUnit(
        id = id,
        title = id,
        storageFolder = directory.absolutePath,
        audioPath = null,
        lyricsPath = lyricsFile?.absolutePath,
        chordsPath = null,
        annotationsPath = null,
        midiPath = null,
        dmxPath = null,
        prompterPath = null,
        arrangementSourceSongId = id.takeIf { it != PARENT_ID }?.let { PARENT_ID }
    )

    private fun arrangementFor(variantId: String) = ArrangementData(
        version = 2,
        name = variantId,
        sourceSongId = PARENT_ID,
        updatedAt = 1234L,
        segments = emptyList(),
        structureSegmentIds = emptyList(),
        entries = listOf(
            ArrangementEntryData(
                entryId = "segment_$variantId",
                name = "Segment",
                startMs = 0L,
                endMs = 20_000L
            )
        )
    )

    private fun assertRawEquals(expected: String, actualFile: File) {
        assertTrue(actualFile.isFile)
        val actual = actualFile.readText(Charsets.UTF_8)
        assertEquals(expected, actual)
        assertEquals(expected.toByteArray(Charsets.UTF_8).size.toLong(), actualFile.length())
    }

    private companion object {
        const val PARENT_ID = "parent_song"
        const val VARIANT_A_ID = "variant_a"
        const val VARIANT_B_ID = "variant_b"
        const val RAW_FILE_NAME = "lyrics_editor.txt"
    }
}
