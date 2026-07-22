package com.patrick.lrcreader.smp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArrangementVariantsArchiveCodecTest {

    @Test
    fun roundTrip_preservesVirtualVariantsWithoutAudioData() {
        val arrangement = ArrangementData(
            version = 2,
            name = "Marina-AR01",
            sourceSongId = "song_marina",
            updatedAt = 1234L,
            segments = emptyList(),
            structureSegmentIds = emptyList(),
            entries = listOf(
                ArrangementEntryData(
                    entryId = "intro",
                    name = "Intro courte",
                    startMs = 100L,
                    endMs = 2_000L,
                    repeatCount = 2,
                    color = "amber"
                )
            )
        )
        val archive = ArrangementVariantsArchive(
            sourceSongId = "song_marina",
            variants = listOf(
                ArrangementVariantArchiveEntry(
                    id = "arrangement_01",
                    title = "Marina-AR01",
                    arrangement = arrangement
                )
            )
        )

        val encoded = ArrangementVariantsArchiveCodec.encode(archive)
        val decoded = ArrangementVariantsArchiveCodec.decode(encoded)

        assertEquals("song_marina", decoded.sourceSongId)
        assertEquals("arrangement_01", decoded.variants.single().id)
        assertEquals("Marina-AR01", decoded.variants.single().title)
        val decodedArrangement = decoded.variants.single().arrangement
        assertEquals(arrangement.version, decodedArrangement.version)
        assertEquals(arrangement.name, decodedArrangement.name)
        assertEquals(arrangement.sourceSongId, decodedArrangement.sourceSongId)
        assertEquals(arrangement.updatedAt, decodedArrangement.updatedAt)
        assertEquals(arrangement.entries, decodedArrangement.entries)
    }

    @Test
    fun decode_rejectsVariantWhoseStructureTargetsAnotherParent() {
        val rawJson = validManifest().apply {
            getJSONArray("variants")
                .getJSONObject(0)
                .getJSONObject("arrangement")
                .put("sourceSongId", "another_song")
        }

        assertThrows(IllegalArgumentException::class.java) {
            ArrangementVariantsArchiveCodec.decode(rawJson)
        }
    }

    @Test
    fun decode_rejectsDuplicateVariantIds() {
        val rawJson = validManifest()
        val variants = rawJson.getJSONArray("variants")
        variants.put(JSONObject(variants.getJSONObject(0).toString()))

        assertThrows(IllegalArgumentException::class.java) {
            ArrangementVariantsArchiveCodec.decode(rawJson)
        }
    }

    private fun validManifest(): JSONObject = JSONObject(
        """
        {
          "format": "smp_arrangement_variants",
          "version": 1,
          "sourceSongId": "song_marina",
          "variants": [
            {
              "id": "arrangement_01",
              "title": "Marina-AR01",
              "arrangement": {
                "version": 1,
                "name": "Marina-AR01",
                "sourceSongId": "song_marina",
                "updatedAt": 1234,
                "segments": [
                  {"id":"intro","name":"Intro","startMs":0,"endMs":1000}
                ],
                "structureSegmentIds": ["intro"]
              }
            }
          ]
        }
        """.trimIndent()
    )
}
