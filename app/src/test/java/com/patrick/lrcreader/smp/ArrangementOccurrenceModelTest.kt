package com.patrick.lrcreader.smp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementOccurrenceModelTest {

    @Test
    fun v1Projection_givesEachStructureOccurrenceAnIndependentIdentity() {
        val legacyData = ArrangementJsonCodec.decode(
            JSONObject(
                """
                {
                  "version": 1,
                  "name": "Live",
                  "sourceSongId": "song_001",
                  "segments": [
                    {"id":"chorus","name":"Refrain","startMs":1000,"endMs":3000},
                    {"id":"unused","name":"À conserver","startMs":4000,"endMs":5000}
                  ],
                  "structureSegmentIds": ["chorus","chorus"]
                }
                """.trimIndent()
            )
        )

        val projection = legacyData.toOccurrenceProjection()

        assertEquals(listOf("chorus", "chorus__occurrence_2"), projection.structureSegmentIds)
        assertEquals(listOf("chorus", "chorus__occurrence_2"), projection.segments.map { it.id })
        assertEquals(listOf("unused"), projection.preservedLegacySegments.map { it.id })
    }

    @Test
    fun reconcile_editingOneOccurrenceDoesNotChangeItsDuplicate() {
        val existingEntries = listOf(
            ArrangementEntryData(
                entryId = "chorus",
                name = "Refrain",
                startMs = 1_000,
                endMs = 3_000,
                repeatCount = 2,
                color = "amber"
            ),
            ArrangementEntryData(
                entryId = "chorus__occurrence_2",
                name = "Refrain",
                startMs = 1_000,
                endMs = 3_000,
                muted = true,
                color = "blue"
            )
        )
        val editedSegments = listOf(
            ArrangementSegmentData("chorus", "Refrain", 1_000, 3_000),
            ArrangementSegmentData("chorus__occurrence_2", "Refrain final", 1_400, 3_800)
        )

        val reconciled = reconcileArrangementEntries(
            segments = editedSegments,
            structureSegmentIds = listOf("chorus", "chorus__occurrence_2"),
            existingEntries = existingEntries
        )

        assertEquals(1_000, reconciled.first().startMs)
        assertEquals(3_000, reconciled.first().endMs)
        assertEquals(2, reconciled.first().repeatCount)
        assertEquals("Refrain final", reconciled.last().name)
        assertEquals(1_400, reconciled.last().startMs)
        assertEquals(3_800, reconciled.last().endMs)
        assertTrue(reconciled.last().muted)
        assertEquals("blue", reconciled.last().color)
    }

    @Test
    fun version2Persistence_preservesLegacySegmentsOutsideTheStructure() {
        val visibleSegments = listOf(
            ArrangementSegmentData("entry_a", "A", 0, 1_000),
            ArrangementSegmentData("entry_b", "B", 1_000, 2_500)
        )
        val existingEntries = listOf(
            ArrangementEntryData("entry_a", "A", 0, 1_000, color = "amber"),
            ArrangementEntryData("entry_b", "B", 1_000, 2_500, repeatCount = 3)
        )
        val legacySegment = ArrangementSegmentData("unused", "Archive", 3_000, 4_000)

        val data = buildArrangementDataForPersistence(
            useOccurrenceModel = true,
            name = "Live",
            sourceSongId = "song_001",
            segments = visibleSegments,
            structureSegmentIds = listOf("entry_b", "entry_a"),
            existingEntries = existingEntries,
            preservedLegacySegments = listOf(legacySegment)
        )

        assertEquals(2, data.version)
        assertEquals(listOf("entry_b", "entry_a"), data.entries.map { it.entryId })
        assertEquals(3, data.entries.first().repeatCount)
        assertEquals("amber", data.entries.last().color)
        assertEquals(listOf("entry_b", "entry_a", "unused"), data.segments.map { it.id })
    }

    @Test
    fun phonePersistence_keepsTheVersion1Shape() {
        val segment = ArrangementSegmentData("chorus", "Refrain", 1_000, 3_000)

        val data = buildArrangementDataForPersistence(
            useOccurrenceModel = false,
            name = "Live",
            sourceSongId = "song_001",
            segments = listOf(segment),
            structureSegmentIds = listOf("chorus", "chorus"),
            existingEntries = emptyList(),
            preservedLegacySegments = emptyList()
        )

        assertEquals(1, data.version)
        assertEquals(listOf("chorus", "chorus"), data.structureSegmentIds)
        assertTrue(data.entries.isEmpty())
    }

    @Test
    fun preparation_expandsRepeatsAndSkipsMutedOccurrences() {
        val segments = listOf(
            ArrangementSegmentData("intro", "Intro", 0, 1_000),
            ArrangementSegmentData("chorus", "Refrain", 1_000, 3_000),
            ArrangementSegmentData("outro", "Outro", 3_000, 4_000)
        )
        val entries = listOf(
            ArrangementEntryData("intro", "Intro", 0, 1_000, repeatCount = 2),
            ArrangementEntryData("chorus", "Refrain", 1_000, 3_000, muted = true),
            ArrangementEntryData("outro", "Outro", 3_000, 4_000)
        )

        val prepared = prepareArrangementOccurrences(
            segments = segments,
            structureSegmentIds = listOf("intro", "chorus", "outro"),
            entries = entries,
            useOccurrenceModel = true
        )

        assertEquals(listOf("intro", "intro", "outro"), prepared.map { it.segment.id })
        assertEquals(listOf(0, 0, 2), prepared.map { it.entryIndex })
        assertEquals(listOf(0, 1, 0), prepared.map { it.repeatIndex })
    }

    @Test
    fun phonePreparation_ignoresOccurrenceMetadata() {
        val segment = ArrangementSegmentData("chorus", "Refrain", 1_000, 3_000)

        val prepared = prepareArrangementOccurrences(
            segments = listOf(segment),
            structureSegmentIds = listOf("chorus", "chorus"),
            entries = listOf(
                ArrangementEntryData(
                    entryId = "chorus",
                    name = "Refrain",
                    startMs = 1_000,
                    endMs = 3_000,
                    repeatCount = 4,
                    muted = true
                )
            ),
            useOccurrenceModel = false
        )

        assertEquals(listOf("chorus", "chorus"), prepared.map { it.segment.id })
        assertEquals(listOf(0, 1), prepared.map { it.entryIndex })
    }
}
