package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import com.patrick.lrcreader.core.config.TrackSettingsStore

object TrackTempoPrefs {

    private const val PREF = "track_tempo_prefs"
    private const val TAG = "TrackTempoPrefs"

    fun getTempo(context: Context, uri: String): Float? {
        val fromJson = TrackSettingsStore.getTempoByUri(context, uri)
        if (fromJson != null) return fromJson

        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = sp.getFloat(uri, -1f)
        return if (raw < 0f) null else raw
    }

    fun saveTempo(context: Context, uri: String, tempo: Float) {
        val jsonOk = TrackSettingsStore.saveTempoByUri(context, uri, tempo)
        if (!jsonOk) {
            Log.w(TAG, "saveTempo: JSON write skipped/failed, fallback prefs only")
        }

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putFloat(uri, tempo)
            .apply()
    }
}
