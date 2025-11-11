package com.patrick.lrcreader.core

import android.content.Context

/**
 * Stocke un niveau en dB par URI de piste.
 * On garde un Int (par ex. -12..+12).
 */
object TrackVolumePrefs {
    private const val PREF = "track_volume_prefs_db"

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