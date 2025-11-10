package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri

object DjFolderPrefs {
    private const val PREF = "dj_folder_prefs"
    private const val KEY_URI = "dj_folder_uri"

    fun save(context: Context, uri: Uri) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, uri.toString())
            .apply()
    }

    fun get(context: Context): Uri? {
        val s = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_URI, null)
        return s?.let { Uri.parse(it) }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_URI)
            .apply()
    }
}