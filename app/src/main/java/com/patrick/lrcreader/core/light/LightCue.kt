package com.patrick.lrcreader.core.light

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class LightCue(
    val timeMs: Long,
    val action: LightAction,
    val intensity: Float = 1f,
    val fadeMs: Long = 0L
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("timeMs", timeMs.coerceAtLeast(0L))
            put("action", action.storageType)
            put("intensity", intensity.coerceIn(0f, 1f).toDouble())
            put("fadeMs", fadeMs.coerceAtLeast(0L))
            when (action) {
                is LightAction.Color -> put("argb", action.toStorageArgb())
                LightAction.Blackout -> Unit
                is LightAction.Strobe -> put("hz", action.hz.coerceAtLeast(0.1f).toDouble())
            }
        }
    }

    companion object {
        fun toJsonArray(cues: List<LightCue>): JSONArray {
            return JSONArray().apply {
                cues.forEach { cue -> put(cue.toJson()) }
            }
        }

        fun toJsonString(cues: List<LightCue>, indentSpaces: Int = 2): String {
            return toJsonArray(cues).toString(indentSpaces)
        }

        fun listFromJsonOrEmpty(rawJson: String?): List<LightCue> {
            if (rawJson.isNullOrBlank()) {
                return emptyList()
            }

            return runCatching {
                val jsonArray = JSONArray(rawJson)
                buildList {
                    for (index in 0 until jsonArray.length()) {
                        fromJsonOrNull(jsonArray.optJSONObject(index))?.let(::add)
                    }
                }.sortedBy { cue -> cue.timeMs }
            }.getOrDefault(emptyList())
        }

        fun fromJsonOrNull(json: JSONObject?): LightCue? {
            if (json == null) {
                return null
            }

            val hasTime = json.has("timeMs") && !json.isNull("timeMs")
            val hasAction = json.has("action") && !json.isNull("action")
            if (!hasTime || !hasAction) {
                return null
            }

            val action = LightAction.fromJson(json) ?: return null
            return LightCue(
                timeMs = json.optLong("timeMs").coerceAtLeast(0L),
                action = action,
                intensity = json.optDouble("intensity", 1.0).toFloat().coerceIn(0f, 1f),
                fadeMs = json.optLong("fadeMs").coerceAtLeast(0L)
            )
        }
    }
}

sealed interface LightAction {
    val storageType: String

    data class Color(val argb: Long) : LightAction {
        override val storageType: String = TYPE_COLOR
    }

    data object Blackout : LightAction {
        override val storageType: String = TYPE_BLACKOUT
    }

    data class Strobe(val hz: Float) : LightAction {
        override val storageType: String = TYPE_STROBE
    }

    companion object {
        private const val TYPE_COLOR = "COLOR"
        private const val TYPE_BLACKOUT = "BLACKOUT"
        private const val TYPE_STROBE = "STROBE"

        fun fromJson(json: JSONObject): LightAction? {
            return when (json.optString("action").trim().uppercase(Locale.ROOT)) {
                TYPE_COLOR -> parseColorAction(json)
                TYPE_BLACKOUT -> Blackout
                TYPE_STROBE -> {
                    val hz = json.optDouble("hz", 8.0).toFloat().coerceAtLeast(0.1f)
                    Strobe(hz = hz)
                }
                else -> null
            }
        }

        private fun parseColorAction(json: JSONObject): LightAction? {
            val rawArgb = when {
                json.has("argb") && !json.isNull("argb") -> json.optString("argb")
                json.has("color") && !json.isNull("color") -> json.optString("color")
                else -> null
            } ?: return null

            val normalized = rawArgb.trim()
            if (normalized.isEmpty()) {
                return null
            }

            val hex = normalized.removePrefix("#")
            if (hex.length != 8) {
                return null
            }

            val parsed = hex.toLongOrNull(16) ?: return null
            return Color(argb = parsed)
        }
    }
}

private fun LightAction.Color.toStorageArgb(): String {
    return "#%08X".format(argb.coerceIn(0L, 0xFFFFFFFFL))
}
