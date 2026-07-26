package com.patrick.lrcreader.core.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrackSettingsVariantConfigTest {

    @Test
    fun lyricsColorsPreserveArrangementVariantMetadata() {
        val raw = JSONObject()
            .put("version", 1)
            .put("id", "variant")
            .put("title", "Variante")
            .put(
                "arrangementVariant",
                JSONObject().put("sourceSongId", "parent")
            )
            .put("futureMetadata", JSONObject().put("enabled", true))
            .toString()

        val mergedRaw = TrackSettingsStore.mergeSmpLyricsLineColors(
            rawJson = raw,
            lyricsLineColors = mapOf("1000|Ligne" to 123)
        )

        assertNotNull(mergedRaw)
        val merged = JSONObject(mergedRaw!!)
        assertEquals("parent", merged.getJSONObject("arrangementVariant").getString("sourceSongId"))
        assertEquals(true, merged.getJSONObject("futureMetadata").getBoolean("enabled"))
        assertEquals(123, merged.getJSONObject("lyricsLineColors").getInt("1000|Ligne"))
    }
}
