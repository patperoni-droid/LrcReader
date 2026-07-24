package com.patrick.lrcreader.smp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
