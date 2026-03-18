package com.patrick.lrcreader.smp

import android.util.Log
import org.json.JSONObject

data class SmpConfig(
    val title: String?,
    val id: String?
) {
    companion object {
        private const val TAG = "SmpConfig"

        fun fromJson(rawJson: String?): SmpConfig {
            return fromJsonOrNull(rawJson) ?: SmpConfig(title = null, id = null)
        }

        fun fromJsonOrNull(rawJson: String?): SmpConfig? {
            if (rawJson.isNullOrBlank()) {
                Log.e(TAG, "config.json absent ou vide")
                return null
            }

            return try {
                val json = JSONObject(rawJson)
                SmpConfig(
                    title = json.optString("title").trim().ifBlank { null },
                    id = json.optString("id").trim().ifBlank { null }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Impossible de parser config.json", e)
                null
            }
        }
    }
}
