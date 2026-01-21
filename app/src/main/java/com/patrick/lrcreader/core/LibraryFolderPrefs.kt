package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri

object LibraryFolderPrefs {
    private const val PREFS_NAME = "library_folder_prefs"
    private const val KEY_TREE_URI = "library_tree_uri"

    fun save(context: Context, treeUri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE_URI, treeUri.toString())
            .apply()
    }

    fun get(context: Context): Uri? {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null) ?: return null
        return Uri.parse(s)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TREE_URI)
            .apply()
    }
}