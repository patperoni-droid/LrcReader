package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri

/**
 * Stocke le dossier choisi via OpenDocumentTree pour les sauvegardes.
 */
object BackupFolderPrefs {

    private const val PREFS_NAME = "backup_folder_prefs"
    private const val KEY_URI = "folder_uri"

    fun save(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, uri.toString())
            .apply()
    }

    fun get(context: Context): Uri? {
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
}