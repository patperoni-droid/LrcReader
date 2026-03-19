package com.patrick.lrcreader.smp

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class MidiCue(
    val time: Double,
    val type: String,
    val value: Int,
    val channel: Int
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("time", time)
            put("type", type.uppercase(Locale.ROOT))
            put("value", value)
            put("channel", channel)
        }
    }

    companion object {
        private val SUPPORTED_TYPES = setOf("PC", "CC")

        fun toJsonArray(cues: List<MidiCue>): JSONArray {
            return JSONArray().apply {
                cues.forEach { put(it.toJson()) }
            }
        }

        fun toJsonString(cues: List<MidiCue>, indentSpaces: Int = 2): String {
            return toJsonArray(cues).toString(indentSpaces)
        }

        fun listFromJsonOrEmpty(rawJson: String?): List<MidiCue> {
            if (rawJson.isNullOrBlank()) {
                return emptyList()
            }

            return runCatching {
                val jsonArray = JSONArray(rawJson)
                buildList {
                    for (index in 0 until jsonArray.length()) {
                        fromJsonOrNull(jsonArray.optJSONObject(index))?.let(::add)
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun fromJsonOrNull(json: JSONObject?): MidiCue? {
            if (json == null) {
                return null
            }

            val hasTime = json.has("time") && !json.isNull("time")
            val hasType = json.has("type") && !json.isNull("type")
            val hasValue = json.has("value") && !json.isNull("value")
            val hasChannel = json.has("channel") && !json.isNull("channel")
            if (!hasTime || !hasType || !hasValue || !hasChannel) {
                return null
            }

            val normalizedType = json.optString("type").trim().uppercase(Locale.ROOT)
            if (normalizedType !in SUPPORTED_TYPES) {
                return null
            }

            return MidiCue(
                time = json.optDouble("time"),
                type = normalizedType,
                value = json.optInt("value"),
                channel = json.optInt("channel")
            )
        }
    }
}
