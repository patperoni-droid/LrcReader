package com.patrick.lrcreader.core

import android.content.Context

object TrackVolumePrefs {
    private const val PREF = "track_volume_prefs"

    // on stocke un volume 0f..1f par URI
    fun saveVolume(context: Context, uri: String, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putFloat(uri, v)
            .apply()
    }

    fun getVolume(context: Context, uri: String): Float? {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return if (sp.contains(uri)) {
            sp.getFloat(uri, 1f)
        } else {
            null
        }
    }
}