package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri

object LyricsFolderPrefs {
    private const val PREF = "lyrics_folder_prefs"
    private const val KEY_URI = "lyrics_folder_uri"

    fun save(context: Context, uri: Uri) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, uri.toString())
            .apply()
    }

    fun get(context: Context): Uri? {
        val s = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_URI, null)
        return s?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY_URI).apply()
    }
}