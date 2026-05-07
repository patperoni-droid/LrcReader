package com.patrick.lrcreader.core.config

import org.json.JSONObject

internal data class TrackSettingsEq(
    val low: Float,
    val mid: Float,
    val high: Float
)

internal data class TrackSettingsEntry(
    val volumeDb: Int? = null,
    val tempo: Float? = null,
    val timelineTempoBpm: Int? = null,
    val pitchSemi: Int? = null,
    val eq: TrackSettingsEq? = null,
    val titleColorByPlaylist: Map<String, Int> = emptyMap(),
    val lyricsLineColors: Map<String, Int> = emptyMap()
) {
    fun isEmpty(): Boolean {
        return volumeDb == null &&
            tempo == null &&
            timelineTempoBpm == null &&
            pitchSemi == null &&
            eq == null &&
            titleColorByPlaylist.isEmpty() &&
            lyricsLineColors.isEmpty()
    }
}

internal data class TrackSettingsState(
    val schemaVersion: Int = 1,
    val tracks: Map<String, TrackSettingsEntry> = emptyMap()
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("schemaVersion", schemaVersion)

        val tracksObj = JSONObject()
        tracks.keys.sorted().forEach { relPath ->
            val entry = tracks[relPath] ?: return@forEach
            val trackObj = JSONObject()

            entry.volumeDb?.let { trackObj.put("volumeDb", it) }
            entry.tempo?.let { trackObj.put("tempo", it.toDouble()) }
            entry.timelineTempoBpm?.let { trackObj.put("timelineTempoBpm", it) }
            entry.pitchSemi?.let { trackObj.put("pitchSemi", it) }
            entry.eq?.let {
                val eqObj = JSONObject()
                eqObj.put("low", it.low.toDouble())
                eqObj.put("mid", it.mid.toDouble())
                eqObj.put("high", it.high.toDouble())
                trackObj.put("eq", eqObj)
            }

            if (entry.titleColorByPlaylist.isNotEmpty()) {
                val colorsObj = JSONObject()
                entry.titleColorByPlaylist.keys.sorted().forEach { playlistName ->
                    colorsObj.put(playlistName, entry.titleColorByPlaylist[playlistName])
                }
                trackObj.put("titleColorByPlaylist", colorsObj)
            }

            if (entry.lyricsLineColors.isNotEmpty()) {
                val lyricsColorsObj = JSONObject()
                entry.lyricsLineColors.keys.sorted().forEach { lineKey ->
                    lyricsColorsObj.put(lineKey, entry.lyricsLineColors[lineKey])
                }
                trackObj.put("lyricsLineColors", lyricsColorsObj)
            }

            if (trackObj.length() > 0) {
                tracksObj.put(relPath, trackObj)
            }
        }

        root.put("tracks", tracksObj)
        return root
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun empty(): TrackSettingsState = TrackSettingsState(
            schemaVersion = SCHEMA_VERSION,
            tracks = emptyMap()
        )

        fun fromJson(raw: String): TrackSettingsState {
            val root = JSONObject(raw)
            val schemaVersion = root.optInt("schemaVersion", SCHEMA_VERSION)
            val tracksObj = root.optJSONObject("tracks") ?: JSONObject()

            val tracks = linkedMapOf<String, TrackSettingsEntry>()
            val keys = tracksObj.keys().asSequence().toList().sorted()
            keys.forEach { relPath ->
                val obj = tracksObj.optJSONObject(relPath) ?: return@forEach

                val hasVolume = obj.has("volumeDb") && !obj.isNull("volumeDb")
                val volumeDb = if (hasVolume) obj.optInt("volumeDb") else null

                val hasTempo = obj.has("tempo") && !obj.isNull("tempo")
                val tempo = if (hasTempo) obj.optDouble("tempo").toFloat() else null

                val hasTimelineTempoBpm = obj.has("timelineTempoBpm") && !obj.isNull("timelineTempoBpm")
                val timelineTempoBpm = if (hasTimelineTempoBpm) obj.optInt("timelineTempoBpm") else null

                val hasPitch = obj.has("pitchSemi") && !obj.isNull("pitchSemi")
                val pitchSemi = if (hasPitch) obj.optInt("pitchSemi") else null

                val eqObj = obj.optJSONObject("eq")
                val eq = if (eqObj != null &&
                    eqObj.has("low") && !eqObj.isNull("low") &&
                    eqObj.has("mid") && !eqObj.isNull("mid") &&
                    eqObj.has("high") && !eqObj.isNull("high")
                ) {
                    TrackSettingsEq(
                        low = eqObj.optDouble("low").toFloat(),
                        mid = eqObj.optDouble("mid").toFloat(),
                        high = eqObj.optDouble("high").toFloat()
                    )
                } else {
                    null
                }

                val colorsObj = obj.optJSONObject("titleColorByPlaylist")
                val colors = linkedMapOf<String, Int>()
                if (colorsObj != null) {
                    val colorKeys = colorsObj.keys().asSequence().toList().sorted()
                    colorKeys.forEach { playlist ->
                        if (colorsObj.has(playlist) && !colorsObj.isNull(playlist)) {
                            colors[playlist] = colorsObj.optInt(playlist)
                        }
                    }
                }

                val lyricsColorsObj = obj.optJSONObject("lyricsLineColors")
                val lyricsLineColors = linkedMapOf<String, Int>()
                if (lyricsColorsObj != null) {
                    val lineKeys = lyricsColorsObj.keys().asSequence().toList().sorted()
                    lineKeys.forEach { lineKey ->
                        if (lyricsColorsObj.has(lineKey) && !lyricsColorsObj.isNull(lineKey)) {
                            lyricsLineColors[lineKey] = lyricsColorsObj.optInt(lineKey)
                        }
                    }
                }

                val entry = TrackSettingsEntry(
                    volumeDb = volumeDb,
                    tempo = tempo,
                    timelineTempoBpm = timelineTempoBpm,
                    pitchSemi = pitchSemi,
                    eq = eq,
                    titleColorByPlaylist = colors,
                    lyricsLineColors = lyricsLineColors
                )

                if (!entry.isEmpty()) {
                    tracks[relPath] = entry
                }
            }

            return TrackSettingsState(
                schemaVersion = schemaVersion,
                tracks = tracks
            )
        }
    }
}
