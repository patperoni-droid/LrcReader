package com.patrick.lrcreader.smp

import com.patrick.lrcreader.core.notes.LiveNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ArrangementVariantAnnotationsRoundTripTest {

    @Test
    fun familyRoundTrip_preservesEachVariantAnnotationsExactly() {
        val root = Files.createTempDirectory("variant_annotations_round_trip_").toFile()
        try {
            val runtimeRoot = root.resolve("runtime").apply { mkdirs() }
            val restoredRoot = root.resolve("restored").apply { mkdirs() }
            val annotations = linkedMapOf(
                "variant_a" to listOf(
                    LiveNote(timeMs = 500L, durationMs = 2_000L, text = "Changer de guitare"),
                    LiveNote(timeMs = 3_000L, durationMs = 5_000L, text = "Parler au public"),
                    LiveNote(timeMs = 12_500L, durationMs = 1_500L, text = "Final court")
                ),
                "variant_b" to listOf(
                    LiveNote(timeMs = 750L, durationMs = 4_000L, text = "Entrée lumière"),
                    LiveNote(timeMs = 8_000L, durationMs = 6_000L, text = "Solo prolongé"),
                    LiveNote(timeMs = 18_000L, durationMs = 3_000L, text = "Saluer")
                )
            )

            val sourceEntries = annotations.map { (variantId, notes) ->
                val variantDir = runtimeRoot.resolve(variantId).apply { mkdirs() }
                val annotationsFile = variantDir.resolve(SmpAnnotationsStore.ANNOTATIONS_FILE_NAME)
                assertTrue(SmpAnnotationsStore.write(annotationsFile, notes))
                SmpAnnotationsStore.awaitIdle(annotationsFile)
                ArrangementVariantArchiveEntry(
                    id = variantId,
                    title = variantId,
                    arrangement = arrangementFor(variantId),
                    annotations = annotationsFile.readText(Charsets.UTF_8)
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
                    archivedAnnotations = variant.annotations
                )
            }

            assertEquals(annotations.keys.toList().sorted(), decoded.variants.map { it.id })
            annotations.forEach { (variantId, expectedNotes) ->
                val restoredNotes = SmpAnnotationsStore.read(
                    File(restoredRoot.resolve(variantId), SmpAnnotationsStore.ANNOTATIONS_FILE_NAME)
                )
                assertEquals(expectedNotes, restoredNotes)
            }
        } finally {
            root.deleteRecursively()
        }
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
    }
}
