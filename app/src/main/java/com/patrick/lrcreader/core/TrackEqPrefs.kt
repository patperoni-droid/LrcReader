package com.patrick.lrcreader.core

import android.content.Context

data class TrackEqSettings(
    val low: Float,
    val mid: Float,
    val high: Float
)

object TrackEqPrefs {
    private const val PREF_NAME = "track_eq_prefs"
    private const val KEY_PREFIX = "eq_"

    fun load(context: Context, uri: String): TrackEqSettings? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PREFIX + uri, null) ?: return null
        val parts = raw.split(";")
        if (parts.size != 3) return null

        val low = parts[0].toFloatOrNull() ?: return null
        val mid = parts[1].toFloatOrNull() ?: return null
        val high = parts[2].toFloatOrNull() ?: return null

        return TrackEqSettings(low, mid, high)
    }

    fun save(context: Context, uri: String, settings: TrackEqSettings) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(
                KEY_PREFIX + uri,
                "${settings.low};${settings.mid};${settings.high}"
            )
            .apply()
    }
}