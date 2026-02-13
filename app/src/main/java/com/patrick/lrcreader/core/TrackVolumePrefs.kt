package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import com.patrick.lrcreader.core.config.TrackSettingsStore

object TrackVolumePrefs {
    private const val PREF = "track_volume_prefs"
    private const val TAG = "TrackVolumePrefs"

    fun saveDb(context: Context, uri: String, db: Int) {
        val jsonOk = TrackSettingsStore.saveVolumeDbByUri(context, uri, db)
        if (!jsonOk) {
            Log.w(TAG, "saveDb: JSON write skipped/failed, fallback prefs only")
        }

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(uri, db)
            .apply()
    }

    fun getDb(context: Context, uri: String): Int? {
        val fromJson = TrackSettingsStore.getVolumeDbByUri(context, uri)
        if (fromJson != null) return fromJson

        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return if (sp.contains(uri)) sp.getInt(uri, 0) else null
    }
}
