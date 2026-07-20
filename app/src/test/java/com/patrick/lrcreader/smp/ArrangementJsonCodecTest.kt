package com.patrick.lrcreader.smp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementJsonCodecTest {

    @Test
    fun decodeV1_createsIndependentStableEntriesWithoutChangingLegacyData() {
        val decoded = ArrangementJsonCodec.decode(
            JSONObject(
                """
                {
                  "version": 1,
                  "name": "Live",
                  "sourceSongId": "song_001",
                  "updatedAt": 123,
                  "segments": [
                    {"id":"intro","name":"Intro","startMs":0,"endMs":1000},
                    {"id":"chorus","name":"Refrain","startMs":1000,"endMs":3000}
                  ],
                  "structureSegmentIds": ["intro","chorus","intro"]
                }
                """.trimIndent()
            )
        )

        assertEquals(1, decoded.version)
        assertEquals(listOf("intro", "chorus", "intro"), decoded.structureSegmentIds)
        assertEquals(listOf("intro", "chorus", "intro__occurrence_2"), decoded.entries.map { it.entryId })
        assertTrue(decoded.entries.all { it.repeatCount == 1 && !it.muted && it.color == null })

        val encodedAgain = ArrangementJsonCodec.encode(decoded)
        assertFalse(encodedAgain.has("entries"))
        assertEquals(1, encodedAgain.getInt("version"))
        assertEquals("intro", encodedAgain.getJSONArray("structureSegmentIds").getString(0))
        assertEquals("intro", encodedAgain.getJSONArray("structureSegmentIds").getString(2))
    }

    @Test
    fun version2RoundTrip_preservesOccurrencesAndLegacyProjection() {
        val source = ArrangementData(
            version = 2,
            name = "Version scène",
            sourceSongId = "song_001",
            updatedAt = 456,
            segments = emptyList(),
            structureSegmentIds = emptyList(),
            entries = listOf(
                ArrangementEntryData(
                    entryId = "entry_intro",
                    name = "Intro courte",
                    startMs = 100,
                    endMs = 2100,
                    repeatCount = 2,
                    muted = false,
                    color = "#FFF59D"
                ),
                ArrangementEntryData(
                    entryId = "entry_bridge",
                    name = "Pont",
                    startMs = 5000,
                    endMs = 8000,
                    repeatCount = 1,
                    muted = true
                )
            )
        )

        val encoded = ArrangementJsonCodec.encode(source)
        assertEquals(listOf("entry_intro", "entry_bridge"), encoded.getJSONArray("structureSegmentIds").toStringList())
        assertEquals(listOf("entry_intro", "entry_bridge"), encoded.getJSONArray("segments").toIdList())

        val decoded = ArrangementJsonCodec.decode(encoded)
        assertEquals(2, decoded.version)
        assertEquals(source.entries, decoded.entries)
        assertEquals(listOf("entry_intro", "entry_bridge"), decoded.structureSegmentIds)
        assertEquals("#FFF59D", decoded.entries.first().color)
        assertNull(decoded.entries.last().color)
    }

    @Test
    fun legacySaveOverVersion2_preservesAdvancedMetadata() {
        val existing = ArrangementData(
            version = 2,
            name = "Live",
            sourceSongId = "song_001",
            segments = emptyList(),
            structureSegmentIds = emptyList(),
            entries = listOf(
                ArrangementEntryData(
                    entryId = "entry_a",
                    name = "A",
                    startMs = 0,
                    endMs = 1000,
                    repeatCount = 3,
                    muted = true,
                    color = "amber"
                ),
                ArrangementEntryData(
                    entryId = "entry_b",
                    name = "B",
                    startMs = 1000,
                    endMs = 2000,
                    color = "blue"
                )
            )
        )
        val legacyEdit = ArrangementData(
            name = "Live retouché",
            sourceSongId = "song_001",
            segments = listOf(
                ArrangementSegmentData("entry_a", "A retouché", 50, 950),
                ArrangementSegmentData("entry_b", "B", 1000, 2000)
            ),
            structureSegmentIds = listOf("entry_b", "entry_a")
        )

        val reconciled = ArrangementJsonCodec.preserveVersion2Metadata(existing, legacyEdit)

        assertEquals(2, reconciled.version)
        assertEquals(listOf("entry_b", "entry_a"), reconciled.entries.map { it.entryId })
        val entryA = reconciled.entries.last()
        assertEquals("A retouché", entryA.name)
        assertEquals(50, entryA.startMs)
        assertEquals(950, entryA.endMs)
        assertEquals(3, entryA.repeatCount)
        assertTrue(entryA.muted)
        assertEquals("amber", entryA.color)
    }

    private fun org.json.JSONArray.toStringList(): List<String> =
        (0 until length()).map { index -> getString(index) }

    private fun org.json.JSONArray.toIdList(): List<String> =
        (0 until length()).map { index -> getJSONObject(index).getString("id") }
}
