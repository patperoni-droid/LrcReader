package com.patrick.lrcreader.core

import android.content.Context

/**
 * Préférences pour le bloc-notes.
 * On stocke juste un gros String dans les SharedPreferences.
 */
object NotesPrefs {

    private const val PREFS_NAME = "notes_prefs"
    private const val KEY_TEXT = "notes_text"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TEXT, "") ?: ""
    }

    fun save(context: Context, text: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TEXT, text)
            .apply()
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_TEXT)
            .apply()
    }
}