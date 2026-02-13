package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import com.patrick.lrcreader.core.config.TrackSettingsStore

/**
 * Sauvegarde / lecture de la tonalite (en demi-tons) par titre.
 *
 * Exemple : 0 = normal, +2 = +2 demi-tons, -3 = -3 demi-tons.
 */
object TrackPitchPrefs {

    private const val PREFS_NAME = "track_pitch_prefs"
    private const val KEY_PREFIX = "pitch_"
    private const val TAG = "TrackPitchPrefs"

    fun getSemi(context: Context, uri: String): Int? {
        val fromJson = TrackSettingsStore.getPitchSemiByUri(context, uri)
        if (fromJson != null) return fromJson

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + uri
        if (!prefs.contains(key)) return null
        return prefs.getInt(key, 0)
    }

    fun saveSemi(context: Context, uri: String, semi: Int) {
        val jsonOk = TrackSettingsStore.savePitchSemiByUri(context, uri, semi)
        if (!jsonOk) {
            Log.w(TAG, "saveSemi: JSON write skipped/failed, fallback prefs only")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_PREFIX + uri, semi)
            .apply()
    }

    fun clear(context: Context, uri: String) {
        val jsonOk = TrackSettingsStore.clearPitchSemiByUri(context, uri)
        if (!jsonOk) {
            Log.w(TAG, "clear: JSON clear skipped/failed, fallback prefs only")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PREFIX + uri)
            .apply()
    }
}
