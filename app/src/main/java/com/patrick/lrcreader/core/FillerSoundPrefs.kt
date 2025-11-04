package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri

object FillerSoundPrefs {

    private const val PREFS_NAME = "filler_sound_prefs"
    private const val KEY_URI = "filler_uri"
    private const val KEY_VOLUME = "filler_volume"  // 0f..1f

    fun saveFillerUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, uri.toString())
            .apply()
    }

    fun getFillerUri(context: Context): Uri? {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_URI, null) ?: return null
        return Uri.parse(s)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_URI)
            .apply()
    }

    // ---------- volume ----------

    /** volume stocké 0f..1f, défaut 0.25f */
    fun getFillerVolume(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_VOLUME, 0.25f)
            .coerceIn(0f, 1f)
    }

    fun saveFillerVolume(context: Context, volume: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_VOLUME, volume.coerceIn(0f, 1f))
            .apply()
    }
}