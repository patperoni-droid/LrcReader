package com.patrick.lrcreader.smp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArrangementVariantGridInstrumentedTest {

    @Test
    fun familyRoundTrip_preservesTwoVariantGridsOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = context.cacheDir.resolve("variant_grid_instrumented_test").also {
            it.deleteRecursively()
            assertTrue(it.mkdirs())
        }
        try {
            val grids = linkedMapOf(
                "variant_a" to gridJson(96.5, 1_250L, 500L, 64_000L, 3, 4),
                "variant_b" to gridJson(132.0, 2_875L, 1_000L, 92_500L, 7, 8)
            )
            val archive = ArrangementVariantsArchive(
                sourceSongId = PARENT_ID,
                variants = grids.map { (variantId, grid) ->
                    ArrangementVariantArchiveEntry(
                        id = variantId,
                        title = variantId,
                        arrangement = arrangementFor(variantId),
                        grid = grid
                    )
                }
            )

            val decoded = ArrangementVariantsArchiveCodec.decode(
                ArrangementVariantsArchiveCodec.encode(archive)
            )
            decoded.variants.forEach { variant ->
                val targetDir = root.resolve(variant.id).apply { mkdirs() }
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
                val restoredFile = root.resolve(variantId).resolve(GRID_FILE_NAME)
                assertTrue(restoredFile.isFile)
                assertEquals(expectedGrid, restoredFile.readText(Charsets.UTF_8))
                assertEquals(
                    JSONObject(expectedGrid).toString(),
                    JSONObject(restoredFile.readText(Charsets.UTF_8)).toString()
                )
            }
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
