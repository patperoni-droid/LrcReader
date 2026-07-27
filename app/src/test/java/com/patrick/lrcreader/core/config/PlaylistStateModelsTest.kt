package com.patrick.lrcreader.core.config

import com.patrick.lrcreader.core.buildGroupEnd
import com.patrick.lrcreader.core.buildGroupHeader
import com.patrick.lrcreader.core.getGroupUuid
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistStateModelsTest {

    @Test
    fun fromJson_treatsJsonAndLiteralNullAsAbsentOptionalValues() {
        val header = buildGroupHeader("Concert")
        val end = buildGroupEnd(getGroupUuid(header)!!)
        val state = PlaylistState.fromJson(
            """
                {
                  "schemaVersion": 1,
                  "playlists": {
                    "Live": {
                      "exists": true,
                      "items": [
                        {"uri": "$header", "songId": null, "customTitle": null},
                        {"uri": "smp://parent", "songId": "parent", "customTitle": "Parent"},
                        {"uri": "smp://variant", "songId": "variant", "customTitle": "null"},
                        {"uri": "$end", "songId": "null", "customTitle": "null"}
                      ]
                    }
                  }
                }
            """.trimIndent()
        )

        val items = state.playlists.getValue("Live").items
        assertEquals(listOf(header, "smp://parent", "smp://variant", end), items.map { it.uri })
        assertNull(items[0].songId)
        assertNull(items[0].customTitle)
        assertEquals("parent", items[1].songId)
        assertEquals("Parent", items[1].customTitle)
        assertEquals("variant", items[2].songId)
        assertNull(items[2].customTitle)
        assertNull(items[3].songId)
        assertNull(items[3].customTitle)

        val serializedItems = JSONObject(state.toJson().toString())
            .getJSONObject("playlists")
            .getJSONObject("Live")
            .getJSONArray("items")
        assertEquals(JSONObject.NULL, serializedItems.getJSONObject(0).get("songId"))
        assertEquals(JSONObject.NULL, serializedItems.getJSONObject(3).get("songId"))
    }
}
