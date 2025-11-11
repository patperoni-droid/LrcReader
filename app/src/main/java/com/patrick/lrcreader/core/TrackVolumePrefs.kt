package com.patrick.lrcreader.core

import android.content.Context

object TrackVolumePrefs {
    private const val PREF = "track_volume_prefs"

    fun saveDb(context: Context, uri: String, db: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(uri, db)
            .apply()
    }

    fun getDb(context: Context, uri: String): Int? {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return if (sp.contains(uri)) sp.getInt(uri, 0) else null
    }
}