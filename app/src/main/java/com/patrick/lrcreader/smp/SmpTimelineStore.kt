package com.patrick.lrcreader.smp

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SmpTimelineStore {

    const val TIMELINE_FILE_NAME = "timeline.json"
    private const val TAG = "SmpTimelineStore"

    fun read(timelineFile: File): List<TimelineMarker> {
        if (!timelineFile.isFile) {
            return emptyList()
        }

        return runCatching {
            val rawJson = timelineFile.readText(Charsets.UTF_8)
            markersFromJson(rawJson)
        }.getOrElse { error ->
            Log.e(TAG, "Lecture timeline.json impossible: ${timelineFile.absolutePath}", error)
            emptyList()
        }
    }

    fun write(timelineFile: File, markers: List<TimelineMarker>): Boolean {
        val songDir = timelineFile.parentFile ?: return false
        val tmpFile = File(songDir, "${timelineFile.name}.tmp")
        val normalized = markers
            .mapNotNull { marker ->
                val label = marker.label.trim()
                if (label.isEmpty()) {
                    null
                } else {
                    TimelineMarker(
                        timeMs = marker.timeMs.coerceAtLeast(0L),
                        label = label
                    )
                }
            }
            .sortedWith(
                compareBy<TimelineMarker> { it.timeMs }
                    .thenBy { it.label.lowercase() }
            )
        val rawJson = markersToJsonString(normalized)

        return runCatching {
            songDir.mkdirs()
            tmpFile.writeText(rawJson, Charsets.UTF_8)
            if (timelineFile.exists() && !timelineFile.delete()) {
                Log.w(TAG, "Suppression timeline.json impossible: ${timelineFile.absolutePath}")
            }
            if (!tmpFile.renameTo(timelineFile)) {
                tmpFile.writeText(rawJson, Charsets.UTF_8)
                timelineFile.writeText(rawJson, Charsets.UTF_8)
                tmpFile.delete()
            }
            true
        }.getOrElse { error ->
            Log.e(TAG, "Ecriture timeline.json impossible: ${timelineFile.absolutePath}", error)
            runCatching { tmpFile.delete() }
            false
        }
    }

    private fun markersToJsonString(markers: List<TimelineMarker>, indentSpaces: Int = 2): String {
        return JSONArray().apply {
            markers.forEach { marker ->
                put(
                    JSONObject().apply {
                        put("timeMs", marker.timeMs)
                        put("label", marker.label)
                    }
                )
            }
        }.toString(indentSpaces)
    }

    private fun markersFromJson(rawJson: String?): List<TimelineMarker> {
        if (rawJson.isNullOrBlank()) {
            return emptyList()
        }

        return runCatching {
            val jsonArray = JSONArray(rawJson)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(index) ?: continue
                    val hasTime = obj.has("timeMs") && !obj.isNull("timeMs")
                    val hasLabel = obj.has("label") && !obj.isNull("label")
                    if (!hasTime || !hasLabel) {
                        continue
                    }

                    val label = obj.optString("label").trim()
                    if (label.isEmpty()) {
                        continue
                    }

                    add(
                        TimelineMarker(
                            timeMs = obj.optLong("timeMs").coerceAtLeast(0L),
                            label = label
                        )
                    )
                }
            }.sortedWith(
                compareBy<TimelineMarker> { it.timeMs }
                    .thenBy { it.label.lowercase() }
            )
        }.getOrDefault(emptyList())
    }
}
