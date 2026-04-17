package com.patrick.lrcreader.core.config

import org.json.JSONArray
import org.json.JSONObject

internal data class PlaylistStateEntry(
    val exists: Boolean = false,
    val items: List<PlaylistStateItem> = emptyList(),
    val manualOrder: List<String> = emptyList(),
    val originalOrder: List<String> = emptyList(),
    val updatedAt: Long = 0L
) {
    fun isEmpty(): Boolean = !exists && items.isEmpty() && manualOrder.isEmpty() && originalOrder.isEmpty()
}

internal data class PlaylistStateItem(
    val uri: String,
    val songId: String? = null,
    val customTitle: String? = null
)

internal data class PlaylistState(
    val schemaVersion: Int = SCHEMA_VERSION,
    val playlists: Map<String, PlaylistStateEntry> = emptyMap()
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("schemaVersion", schemaVersion)

        val playlistsObj = JSONObject()
        playlists.keys.sorted().forEach { playlistName ->
            val entry = playlists[playlistName] ?: return@forEach
            if (entry.isEmpty()) return@forEach

            val obj = JSONObject()
            obj.put("exists", entry.exists)
            obj.put(
                "items",
                JSONArray().apply {
                    entry.items.forEach { item ->
                        put(
                            JSONObject().apply {
                                put("uri", item.uri)
                                put("songId", item.songId ?: JSONObject.NULL)
                                put("customTitle", item.customTitle ?: JSONObject.NULL)
                            }
                        )
                    }
                }
            )
            obj.put("manualOrder", JSONArray().apply { entry.manualOrder.forEach { put(it) } })
            obj.put("originalOrder", JSONArray().apply { entry.originalOrder.forEach { put(it) } })
            obj.put("updatedAt", entry.updatedAt)
            playlistsObj.put(playlistName, obj)
        }

        root.put("playlists", playlistsObj)
        return root
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun empty(): PlaylistState = PlaylistState(
            schemaVersion = SCHEMA_VERSION,
            playlists = emptyMap()
        )

        fun fromJson(raw: String): PlaylistState {
            val root = JSONObject(raw)
            val schemaVersion = root.optInt("schemaVersion", SCHEMA_VERSION)
            val playlistsObj = root.optJSONObject("playlists") ?: JSONObject()

            val playlists = linkedMapOf<String, PlaylistStateEntry>()
            val keys = playlistsObj.keys().asSequence().toList().sorted()
            keys.forEach { playlistName ->
                val obj = playlistsObj.optJSONObject(playlistName) ?: return@forEach
                val exists = obj.optBoolean("exists", false)
                val items = parseItemsArray(obj.optJSONArray("items"))
                val manualOrder = parseStringArray(obj.optJSONArray("manualOrder"))
                val originalOrder = parseStringArray(obj.optJSONArray("originalOrder"))
                val updatedAt = obj.optLong("updatedAt", 0L)

                val entry = PlaylistStateEntry(
                    exists = exists,
                    items = items,
                    manualOrder = manualOrder,
                    originalOrder = originalOrder,
                    updatedAt = updatedAt
                )
                if (!entry.isEmpty()) {
                    playlists[playlistName] = entry
                }
            }

            return PlaylistState(
                schemaVersion = schemaVersion,
                playlists = playlists
            )
        }

        private fun parseStringArray(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val value = arr.optString(i, "").trim()
                if (value.isNotBlank()) out.add(value)
            }
            return out
        }

        private fun parseItemsArray(arr: JSONArray?): List<PlaylistStateItem> {
            if (arr == null) return emptyList()
            val out = mutableListOf<PlaylistStateItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val uri = obj.optString("uri", "").trim()
                if (uri.isEmpty()) continue
                val songId = obj.optString("songId", "").trim().ifBlank { null }
                val customTitle = obj.optString("customTitle", "").trim().ifBlank { null }
                out.add(PlaylistStateItem(uri = uri, songId = songId, customTitle = customTitle))
            }
            return out
        }
    }
}
