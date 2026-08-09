package com.patrick.lrcreader.smp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ArrangementVariantPrompterRoundTripTest {

    @Test
    fun familyRoundTrip_preservesParentAndTwoVariantPromptersExactly() {
        val root = Files.createTempDirectory("variant_prompter_round_trip_").toFile()
        try {
            val runtimeRoot = root.resolve("runtime").apply { mkdirs() }
            val restoredRoot = root.resolve("restored").apply { mkdirs() }
            val parentContent = "Parent — début\n\nParent — fin\n"
            val variantTxtContent = "Couplet A\n\nRefrain A\nÉté"
            val variantJsonContent = """
                {
                  "sections": [
                    {"name": "Intro", "text": "Départ"},
                    {"name": "Final", "text": "À bientôt"}
                  ]
                }

            """.trimIndent()

            val parentDir = runtimeRoot.resolve(PARENT_ID).apply { mkdirs() }
            val variantADir = runtimeRoot.resolve(VARIANT_A_ID).apply { mkdirs() }
            val variantBDir = runtimeRoot.resolve(VARIANT_B_ID).apply { mkdirs() }
            val parentFile = parentDir.resolve("prompteur.txt").apply {
                writeText(parentContent, Charsets.UTF_8)
            }
            val variantAFile = variantADir.resolve("prompteur.txt").apply {
                writeText(variantTxtContent, Charsets.UTF_8)
            }
            val variantBFile = variantBDir.resolve("prompteur.json").apply {
                writeText(variantJsonContent, Charsets.UTF_8)
            }

            val parent = songUnit(PARENT_ID, parentDir, parentFile.absolutePath)
            val variantA = songUnit(VARIANT_A_ID, variantADir, variantAFile.absolutePath)
            val variantB = songUnit(VARIANT_B_ID, variantBDir, variantBFile.absolutePath)
            val parentTransportName = SmpConfig.FilesConfig.fromSongUnit(parent)?.prompter
            val parentTransportContent = parentFile.readText(Charsets.UTF_8)
            val encoded = ArrangementVariantsArchiveCodec.encode(
                ArrangementVariantsArchive(
                    sourceSongId = PARENT_ID,
                    variants = listOf(variantA, variantB).map { variant ->
                        ArrangementVariantArchiveEntry(
                            id = variant.id,
                            title = variant.title,
                            arrangement = arrangementFor(variant.id),
                            prompter = SmpExporter.resolveVariantPrompterForExport(variant)
                        )
                    }
                )
            )

            runtimeRoot.deleteRecursively()

            val restoredParentDir = restoredRoot.resolve(PARENT_ID).apply { mkdirs() }
            restoredParentDir.resolve(requireNotNull(parentTransportName))
                .writeText(parentTransportContent, Charsets.UTF_8)
            val decoded = ArrangementVariantsArchiveCodec.decode(encoded)
            decoded.variants.forEach { variant ->
                val targetDir = restoredRoot.resolve(variant.id).apply { mkdirs() }
                ArrangementVariantStore.writeVariantFiles(
                    targetDir = targetDir,
                    variantId = variant.id,
                    title = variant.title,
                    sourceSongId = PARENT_ID,
                    arrangement = variant.arrangement,
                    archivedPrompter = variant.prompter
                )
            }

            assertEquals("prompter.txt", parentTransportName)
            assertEquals(
                parentContent,
                restoredParentDir.resolve("prompter.txt").readText(Charsets.UTF_8)
            )
            assertEquals(
                variantTxtContent,
                restoredRoot.resolve(VARIANT_A_ID).resolve("prompter.txt").readText(Charsets.UTF_8)
            )
            assertEquals(
                variantJsonContent,
                restoredRoot.resolve(VARIANT_B_ID).resolve("prompter.json").readText(Charsets.UTF_8)
            )
            assertEquals("txt", decoded.variants.single { it.id == VARIANT_A_ID }.prompter?.format)
            assertEquals("json", decoded.variants.single { it.id == VARIANT_B_ID }.prompter?.format)
            assertNotEquals(parentContent, variantTxtContent)
            assertNotEquals(variantTxtContent, variantJsonContent)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun archiveWithoutPrompter_preservesExistingHistoricalAlias() {
        val root = Files.createTempDirectory("variant_prompter_preserve_").toFile()
        try {
            val existing = root.resolve("existing").apply { mkdirs() }
            val target = root.resolve("target").apply { mkdirs() }
            val expected = "Prompteur local\n\nÀ conserver"
            existing.resolve("prompteur.txt").writeText(expected, Charsets.UTF_8)

            ArrangementVariantStore.writeVariantFiles(
                targetDir = target,
                variantId = VARIANT_A_ID,
                title = "Variante A",
                sourceSongId = PARENT_ID,
                arrangement = arrangementFor(VARIANT_A_ID),
                existingVariantDir = existing
            )

            assertEquals(expected, target.resolve("prompteur.txt").readText(Charsets.UTF_8))
            assertFalse(target.resolve("prompter.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun archivedPrompter_replacesEveryHistoricalAlias() {
        val root = Files.createTempDirectory("variant_prompter_replace_").toFile()
        try {
            val existing = root.resolve("existing").apply { mkdirs() }
            val target = root.resolve("target").apply { mkdirs() }
            PROMPTER_FILE_NAMES.forEach { fileName ->
                existing.resolve(fileName).writeText("ancien:$fileName", Charsets.UTF_8)
            }
            val replacement = "{\n  \"texte\": \"Nouveau — été\"\n}\n"

            ArrangementVariantStore.writeVariantFiles(
                targetDir = target,
                variantId = VARIANT_A_ID,
                title = "Variante A",
                sourceSongId = PARENT_ID,
                arrangement = arrangementFor(VARIANT_A_ID),
                existingVariantDir = existing,
                archivedPrompter = ArrangementVariantPrompterArchiveAsset(
                    format = "json",
                    content = replacement
                )
            )

            assertEquals(replacement, target.resolve("prompter.json").readText(Charsets.UTF_8))
            assertFalse(target.resolve("prompteur.txt").exists())
            assertFalse(target.resolve("prompteur.json").exists())
            assertFalse(target.resolve("prompter.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun newVariantWithoutPrompter_doesNotCreatePrompter() {
        val root = Files.createTempDirectory("variant_prompter_absent_").toFile()
        try {
            ArrangementVariantStore.writeVariantFiles(
                targetDir = root,
                variantId = VARIANT_A_ID,
                title = "Variante A",
                sourceSongId = PARENT_ID,
                arrangement = arrangementFor(VARIANT_A_ID)
            )

            assertTrue(PROMPTER_FILE_NAMES.none { root.resolve(it).exists() })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun targetedVariantRoundTrip_doesNotModifySiblingPrompter() {
        val root = Files.createTempDirectory("variant_prompter_targeted_").toFile()
        try {
            val restoredRoot = root.resolve("restored").apply { mkdirs() }
            val siblingDir = restoredRoot.resolve(VARIANT_B_ID).apply { mkdirs() }
            val siblingContent = "Sœur locale inchangée"
            siblingDir.resolve("prompteur.txt").writeText(siblingContent, Charsets.UTF_8)
            val selectedContent = "Variante ciblée\n\nSeulement"
            val archive = ArrangementVariantsArchiveCodec.decode(
                ArrangementVariantsArchiveCodec.encode(
                    ArrangementVariantsArchive(
                        sourceSongId = PARENT_ID,
                        selectedVariantId = VARIANT_A_ID,
                        variants = listOf(
                            ArrangementVariantArchiveEntry(
                                id = VARIANT_A_ID,
                                title = "Variante A",
                                arrangement = arrangementFor(VARIANT_A_ID),
                                prompter = ArrangementVariantPrompterArchiveAsset(
                                    format = "txt",
                                    content = selectedContent
                                )
                            )
                        )
                    )
                )
            )

            archive.variants.forEach { variant ->
                ArrangementVariantStore.writeVariantFiles(
                    targetDir = restoredRoot.resolve(variant.id).apply { mkdirs() },
                    variantId = variant.id,
                    title = variant.title,
                    sourceSongId = PARENT_ID,
                    arrangement = variant.arrangement,
                    archivedPrompter = variant.prompter
                )
            }

            assertEquals(VARIANT_A_ID, archive.selectedVariantId)
            assertEquals(
                selectedContent,
                restoredRoot.resolve(VARIANT_A_ID).resolve("prompter.txt").readText(Charsets.UTF_8)
            )
            assertEquals(siblingContent, siblingDir.resolve("prompteur.txt").readText(Charsets.UTF_8))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun codec_rejectsUnsupportedPrompterFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            ArrangementVariantsArchiveCodec.encode(
                archiveWithPrompter(
                    ArrangementVariantPrompterArchiveAsset(
                        format = "xml",
                        content = "<prompter/>"
                    )
                )
            )
        }
    }

    @Test
    fun codec_rejectsPrompterOverSizeLimit() {
        val oversized = "é".repeat(ArrangementVariantsArchiveCodec.MAX_PROMPTER_BYTES / 2 + 1)

        assertThrows(IllegalArgumentException::class.java) {
            ArrangementVariantsArchiveCodec.encode(
                archiveWithPrompter(
                    ArrangementVariantPrompterArchiveAsset(
                        format = "txt",
                        content = oversized
                    )
                )
            )
        }
    }

    @Test
    fun exporterReturnsNoAssetWhenVariantHasNoPrompter() {
        val root = Files.createTempDirectory("variant_prompter_export_absent_").toFile()
        try {
            assertNull(SmpExporter.resolveVariantPrompterForExport(songUnit(VARIANT_A_ID, root, null)))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun archiveWithPrompter(
        prompter: ArrangementVariantPrompterArchiveAsset
    ) = ArrangementVariantsArchive(
        sourceSongId = PARENT_ID,
        variants = listOf(
            ArrangementVariantArchiveEntry(
                id = VARIANT_A_ID,
                title = "Variante A",
                arrangement = arrangementFor(VARIANT_A_ID),
                prompter = prompter
            )
        )
    )

    private fun songUnit(id: String, directory: java.io.File, prompterPath: String?) = SongUnit(
        id = id,
        title = id,
        storageFolder = directory.absolutePath,
        audioPath = null,
        lyricsPath = null,
        chordsPath = null,
        annotationsPath = null,
        midiPath = null,
        dmxPath = null,
        prompterPath = prompterPath,
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

    private companion object {
        const val PARENT_ID = "parent_song"
        const val VARIANT_A_ID = "variant_a"
        const val VARIANT_B_ID = "variant_b"
        val PROMPTER_FILE_NAMES = listOf(
            "prompteur.txt",
            "prompteur.json",
            "prompter.txt",
            "prompter.json"
        )
    }
}
