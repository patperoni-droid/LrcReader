package com.patrick.lrcreader.smp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ArrangementVariantGridRoundTripTest {

    @Test
    fun familyRoundTrip_preservesEachVariantGridExactly() {
        val root = Files.createTempDirectory("variant_grid_round_trip_").toFile()
        try {
            val runtimeRoot = root.resolve("runtime").apply { mkdirs() }
            val restoredRoot = root.resolve("restored").apply { mkdirs() }
            val grids = linkedMapOf(
                "variant_a" to gridJson(
                    tempoBpm = 96.5,
                    syncPointMs = 1_250L,
                    inMs = 500L,
                    outMs = 64_000L,
                    numerator = 3,
                    denominator = 4
                ),
                "variant_b" to gridJson(
                    tempoBpm = 132.0,
                    syncPointMs = 2_875L,
                    inMs = 1_000L,
                    outMs = 92_500L,
                    numerator = 7,
                    denominator = 8
                )
            )

            val sourceEntries = grids.map { (variantId, grid) ->
                val variantDir = runtimeRoot.resolve(variantId).apply { mkdirs() }
                variantDir.resolve(GRID_FILE_NAME).writeText(grid, Charsets.UTF_8)
                ArrangementVariantArchiveEntry(
                    id = variantId,
                    title = variantId,
                    arrangement = arrangementFor(variantId),
                    grid = variantDir.resolve(GRID_FILE_NAME).readText(Charsets.UTF_8)
                )
            }
            val encoded = ArrangementVariantsArchiveCodec.encode(
                ArrangementVariantsArchive(
                    sourceSongId = PARENT_ID,
                    variants = sourceEntries
                )
            )

            runtimeRoot.deleteRecursively()

            val decoded = ArrangementVariantsArchiveCodec.decode(encoded)
            decoded.variants.forEach { variant ->
                val targetDir = restoredRoot.resolve(variant.id).apply { mkdirs() }
                ArrangementVariantStore.writeVariantFiles(
                    targetDir = targetDir,
                    variantId = variant.id,
                    title = variant.title,
                    sourceSongId = PARENT_ID,
                    arrangement = variant.arrangement,
                    archivedGrid = variant.grid
                )
            }

            assertEquals(2, decoded.variants.size)
            assertEquals(grids.keys.toList().sorted(), decoded.variants.map { it.id })
            grids.forEach { (variantId, expectedGrid) ->
                val restoredFile = restoredRoot.resolve(variantId).resolve(GRID_FILE_NAME)
                assertTrue(restoredFile.isFile)
                assertEquals(expectedGrid, restoredFile.readText(Charsets.UTF_8))
                assertGridPropertiesEqual(
                    expected = JSONObject(expectedGrid),
                    actual = JSONObject(restoredFile.readText(Charsets.UTF_8))
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun archiveWithoutGrid_preservesExistingVariantGrid() {
        val root = Files.createTempDirectory("variant_grid_selective_restore_").toFile()
        try {
            val existing = root.resolve("existing").apply { mkdirs() }
            val target = root.resolve("target").apply { mkdirs() }
            val grid = gridJson(
                tempoBpm = 110.0,
                syncPointMs = 750L,
                inMs = 250L,
                outMs = 48_000L,
                numerator = 5,
                denominator = 4
            )
            existing.resolve(GRID_FILE_NAME).writeText(grid, Charsets.UTF_8)

            ArrangementVariantStore.writeVariantFiles(
                targetDir = target,
                variantId = "variant",
                title = "Variante",
                sourceSongId = PARENT_ID,
                arrangement = arrangementFor("variant"),
                existingVariantDir = existing
            )

            assertEquals(
                grid,
                target.resolve(GRID_FILE_NAME).readText(Charsets.UTF_8)
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun gridJson(
        tempoBpm: Double,
        syncPointMs: Long,
        inMs: Long,
        outMs: Long,
        numerator: Int,
        denominator: Int
    ): String = JSONObject()
        .put("tempoBpm", tempoBpm)
        .put("syncPointMs", syncPointMs)
        .put("inMs", inMs)
        .put("outMs", outMs)
        .put("timeSignatureNumerator", numerator)
        .put("timeSignatureDenominator", denominator)
        .toString(2)

    private fun assertGridPropertiesEqual(expected: JSONObject, actual: JSONObject) {
        assertEquals(expected.length(), actual.length())
        assertEquals(expected.getDouble("tempoBpm"), actual.getDouble("tempoBpm"), 0.0)
        assertEquals(expected.getLong("syncPointMs"), actual.getLong("syncPointMs"))
        assertEquals(expected.getLong("inMs"), actual.getLong("inMs"))
        assertEquals(expected.getLong("outMs"), actual.getLong("outMs"))
        assertEquals(
            expected.getInt("timeSignatureNumerator"),
            actual.getInt("timeSignatureNumerator")
        )
        assertEquals(
            expected.getInt("timeSignatureDenominator"),
            actual.getInt("timeSignatureDenominator")
        )
    }

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
        const val GRID_FILE_NAME = "grid.json"
    }
}
