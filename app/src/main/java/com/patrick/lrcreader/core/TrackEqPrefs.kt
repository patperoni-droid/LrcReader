package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import com.patrick.lrcreader.core.config.TrackSettingsStore

data class TrackEqSettings(
    val low: Float,
    val mid: Float,
    val high: Float
)

object TrackEqPrefs {
    private const val PREF_NAME = "track_eq_prefs"
    private const val KEY_PREFIX = "eq_"
    private const val TAG = "TrackEqPrefs"

    fun load(context: Context, uri: String): TrackEqSettings? {
        val fromJson = TrackSettingsStore.getEqByUri(context, uri)
        if (fromJson != null) return fromJson

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
        val jsonOk = TrackSettingsStore.saveEqByUri(context, uri, settings)
        if (!jsonOk) {
            Log.w(TAG, "save: JSON write skipped/failed, fallback prefs only")
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(
                KEY_PREFIX + uri,
                "${settings.low};${settings.mid};${settings.high}"
            )
            .apply()
    }
}
