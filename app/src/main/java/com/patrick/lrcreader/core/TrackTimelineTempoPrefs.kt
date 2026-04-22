package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import com.patrick.lrcreader.core.config.TrackSettingsStore

object TrackTimelineTempoPrefs {

    const val MIN_TEMPO_BPM = 20
    const val MAX_TEMPO_BPM = 400

    private const val PREF = "track_timeline_tempo_prefs"
    private const val TAG = "TrackTimelineTempoPrefs"

    fun getTempoBpm(context: Context, uri: String): Int? {
        val fromJson = TrackSettingsStore.getTimelineTempoBpmByUri(context, uri)
        if (fromJson != null) {
            return fromJson.coerceIn(MIN_TEMPO_BPM, MAX_TEMPO_BPM)
        }

        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return if (prefs.contains(uri)) {
            prefs.getInt(uri, MIN_TEMPO_BPM).coerceIn(MIN_TEMPO_BPM, MAX_TEMPO_BPM)
        } else {
            null
        }
    }

    fun saveTempoBpm(context: Context, uri: String, tempoBpm: Int): Boolean {
        val safeTempoBpm = tempoBpm.coerceIn(MIN_TEMPO_BPM, MAX_TEMPO_BPM)
        val jsonOk = TrackSettingsStore.saveTimelineTempoBpmByUri(context, uri, safeTempoBpm)
        if (!jsonOk) {
            Log.w(TAG, "saveTempoBpm: JSON write skipped/failed, fallback prefs only")
        }

        return runCatching {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putInt(uri, safeTempoBpm)
                .apply()
        }.fold(
            onSuccess = { true },
            onFailure = { error ->
                Log.w(TAG, "saveTempoBpm: prefs write failed", error)
                jsonOk
            }
        )
    }
}
