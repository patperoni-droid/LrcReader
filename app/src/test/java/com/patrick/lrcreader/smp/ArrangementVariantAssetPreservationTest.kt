package com.patrick.lrcreader.smp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files

class ArrangementVariantAssetPreservationTest {

    @Test
    fun writeVariantFilesPreservesOwnedAssetsAndConfigMetadata() {
        val root = Files.createTempDirectory("arrangement_variant_assets_").toFile()
        try {
            val existing = root.resolve("existing").apply { mkdirs() }
            val target = root.resolve("target").apply { mkdirs() }
            existing.resolve("lyrics.lrc").writeText("[00:01.00] Ligne", Charsets.UTF_8)
            existing.resolve("chords.lrc").writeText("[00:01.00] Am", Charsets.UTF_8)
            existing.resolve("config.json").writeText(
                JSONObject()
                    .put("version", 1)
                    .put("id", "variant")
                    .put("title", "Ancien titre")
                    .put("lyricsLineColors", JSONObject().put("1000|Ligne", 123))
                    .put("customFutureMetadata", "preserved")
                    .toString(),
                Charsets.UTF_8
            )
            existing.resolve("arrangement.json").writeText("old", Charsets.UTF_8)

            ArrangementVariantStore.writeVariantFiles(
                targetDir = target,
                variantId = "variant",
                title = "Nouveau titre",
                sourceSongId = "parent",
                arrangement = ArrangementData(
                    sourceSongId = "parent",
                    segments = emptyList(),
                    structureSegmentIds = emptyList()
                ),
                existingVariantDir = existing
            )

            assertEquals("[00:01.00] Ligne", target.resolve("lyrics.lrc").readText(Charsets.UTF_8))
            assertEquals("[00:01.00] Am", target.resolve("chords.lrc").readText(Charsets.UTF_8))
            assertTrue(target.resolve("arrangement.json").readText().contains("\"sourceSongId\": \"parent\""))
            val config = JSONObject(target.resolve("config.json").readText(Charsets.UTF_8))
            assertEquals("Nouveau titre", config.getString("title"))
            assertEquals("parent", config.getJSONObject("arrangementVariant").getString("sourceSongId"))
            assertEquals("preserved", config.getString("customFutureMetadata"))
            assertEquals(123, config.getJSONObject("lyricsLineColors").getInt("1000|Ligne"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun writeVariantFilesRestoresArchivedLyricsAndColors() {
        val root = Files.createTempDirectory("arrangement_variant_restore_").toFile()
        try {
            val target = root.resolve("target").apply { mkdirs() }

            ArrangementVariantStore.writeVariantFiles(
                targetDir = target,
                variantId = "variant",
                title = "Variante restaurée",
                sourceSongId = "parent",
                arrangement = ArrangementData(
                    sourceSongId = "parent",
                    segments = emptyList(),
                    structureSegmentIds = emptyList()
                ),
                archivedLyrics = "[00:02.00] Paroles restaurées",
                archivedLyricsLineColors = mapOf("2000|Paroles restaurées" to 456)
            )

            assertEquals(
                "[00:02.00] Paroles restaurées",
                target.resolve("lyrics.lrc").readText(Charsets.UTF_8)
            )
            val config = JSONObject(target.resolve("config.json").readText(Charsets.UTF_8))
            assertEquals(
                456,
                config.getJSONObject("lyricsLineColors").getInt("2000|Paroles restaurées")
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun writeVariantFilesUpdatesArchivedAssetsAndPreservesUntransportedAssets() {
        val root = Files.createTempDirectory("arrangement_variant_selective_restore_").toFile()
        try {
            val existing = root.resolve("existing").apply { mkdirs() }
            val target = root.resolve("target").apply { mkdirs() }
            existing.resolve("lyrics.lrc").writeText("Anciennes paroles", Charsets.UTF_8)
            existing.resolve("chords.lrc").writeText("Accords locaux", Charsets.UTF_8)
            existing.resolve("annotations.json").writeText("""{"local":true}""", Charsets.UTF_8)
            existing.resolve("config.json").writeText(
                JSONObject()
                    .put("version", 1)
                    .put("id", "variant")
                    .put("title", "Ancien titre")
                    .put("lyricsLineColors", JSONObject().put("old", 1))
                    .put("futureMetadata", "conservée")
                    .toString(),
                Charsets.UTF_8
            )
            existing.resolve("arrangement.json").writeText("old", Charsets.UTF_8)

            ArrangementVariantStore.writeVariantFiles(
                targetDir = target,
                variantId = "variant",
                title = "Titre importé",
                sourceSongId = "parent",
                arrangement = ArrangementData(
                    sourceSongId = "parent",
                    segments = emptyList(),
                    structureSegmentIds = emptyList()
                ),
                existingVariantDir = existing,
                archivedLyrics = "Paroles importées",
                archivedLyricsLineColors = mapOf("new" to 2)
            )

            assertEquals("Paroles importées", target.resolve("lyrics.lrc").readText(Charsets.UTF_8))
            assertEquals("Accords locaux", target.resolve("chords.lrc").readText(Charsets.UTF_8))
            assertEquals(
                """{"local":true}""",
                target.resolve("annotations.json").readText(Charsets.UTF_8)
            )
            val config = JSONObject(target.resolve("config.json").readText(Charsets.UTF_8))
            assertEquals("conservée", config.getString("futureMetadata"))
            assertEquals(2, config.getJSONObject("lyricsLineColors").getInt("new"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun validateExistingVariantParentAcceptsSameParent() {
        ArrangementVariantStore.validateExistingVariantParent(
            existingArrangement = ArrangementData(
                sourceSongId = "parent",
                segments = emptyList(),
                structureSegmentIds = emptyList()
            ),
            variantId = "variant",
            expectedSourceSongId = "parent"
        )
    }

    @Test
    fun validateExistingVariantParentRejectsAnotherParent() {
        try {
            ArrangementVariantStore.validateExistingVariantParent(
                existingArrangement = ArrangementData(
                    sourceSongId = "other_parent",
                    segments = emptyList(),
                    structureSegmentIds = emptyList()
                ),
                variantId = "variant",
                expectedSourceSongId = "parent"
            )
            fail("Expected an immutable parent conflict")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("variantId=variant"))
            assertTrue(error.message.orEmpty().contains("existingParent=other_parent"))
            assertTrue(error.message.orEmpty().contains("incomingParent=parent"))
        }
    }
}
