package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri

object BackupFolderPrefsSaf {
    private const val PREFS_NAME = "backup_folder_prefs_saf"
    private const val KEY_SETUP_TREE_URI = "setup_tree_uri"
    private const val KEY_LIBRARY_ROOT_URI = "library_root_uri"

    fun saveSetupTreeUri(context: Context, treeUri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SETUP_TREE_URI, treeUri.toString())
            .apply()
    }

    fun getSetupTreeUri(context: Context): Uri? {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SETUP_TREE_URI, null) ?: return null
        return Uri.parse(s)
    }

    fun saveLibraryRootUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIBRARY_ROOT_URI, uri.toString())
            .apply()
    }

    fun getLibraryRootUri(context: Context): Uri? {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIBRARY_ROOT_URI, null) ?: return null
        return Uri.parse(s)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
