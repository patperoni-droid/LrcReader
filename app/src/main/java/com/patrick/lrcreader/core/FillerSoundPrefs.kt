package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri

object FillerSoundPrefs {

    private const val PREF_NAME = "filler_sound_prefs"
    private const val KEY_URI = "filler_uri"

    fun saveFillerUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, uri.toString())
            .apply()
    }

    fun getFillerUri(context: Context): Uri? {
        val s = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_URI, null) ?: return null
        return Uri.parse(s)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_URI)
            .apply()
    }
}