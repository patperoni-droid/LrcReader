package com.patrick.lrcreader.smp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ArrangementVariantTimelineRoundTripTest {

    @Test
    fun familyRoundTrip_preservesEachVariantTimelineExactly() {
        val root = Files.createTempDirectory("variant_timeline_round_trip_").toFile()
        try {
            val runtimeRoot = root.resolve("runtime").apply { mkdirs() }
            val restoredRoot = root.resolve("restored").apply { mkdirs() }
            val timelines = linkedMapOf(
                "variant_a" to listOf(
                    TimelineMarker(500L, "Intro", TimelineMarkerKind.TEXT),
                    TimelineMarker(2_500L, "Programme 12", TimelineMarkerKind.MIDI),
                    TimelineMarker(4_000L, "Préparer le pont", TimelineMarkerKind.NOTE, 3_000L)
                ),
                "variant_b" to listOf(
                    TimelineMarker(750L, "Bleu", TimelineMarkerKind.DMX),
                    TimelineMarker(3_500L, "Solo", TimelineMarkerKind.NOTE, 8_000L),
                    TimelineMarker(9_000L, "Final", TimelineMarkerKind.TEXT)
                )
            )

            val sourceEntries = timelines.map { (variantId, markers) ->
                val variantDir = runtimeRoot.resolve(variantId).apply { mkdirs() }
                val timelineFile = variantDir.resolve(SmpTimelineStore.TIMELINE_FILE_NAME)
                assertTrue(SmpTimelineStore.write(timelineFile, markers))
                SmpTimelineStore.awaitIdle(timelineFile)
                ArrangementVariantArchiveEntry(
                    id = variantId,
                    title = variantId,
                    arrangement = arrangementFor(variantId),
                    timeline = timelineFile.readText(Charsets.UTF_8)
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
                    archivedTimeline = variant.timeline
                )
            }

            assertEquals(timelines.keys.toList().sorted(), decoded.variants.map { it.id })
            timelines.forEach { (variantId, expectedMarkers) ->
                val restoredMarkers = SmpTimelineStore.read(
                    File(restoredRoot.resolve(variantId), SmpTimelineStore.TIMELINE_FILE_NAME)
                )
                assertEquals(expectedMarkers, restoredMarkers)
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
                endMs = 10_000L
            )
        )
    )

    private companion object {
        const val PARENT_ID = "parent_song"
    }
}
