package com.patrick.lrcreader.core

import android.content.Context

object ChordPaletteStore {
    private const val PREF = "chord_palette_prefs"
    private const val KEY_PREFIX = "palette_"

    fun loadRaw(context: Context, lrcFileName: String): String {
        val key = buildKey(lrcFileName)
        if (key.isBlank()) return ""
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_PREFIX + key, "")
            .orEmpty()
    }

    fun saveRaw(context: Context, lrcFileName: String, raw: String) {
        val key = buildKey(lrcFileName)
        if (key.isBlank()) return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFIX + key, raw)
            .apply()
    }

    fun clear(context: Context, lrcFileName: String) {
        val key = buildKey(lrcFileName)
        if (key.isBlank()) return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PREFIX + key)
            .apply()
    }

    private fun buildKey(lrcFileName: String): String {
        return lrcFileName.trim().lowercase()
    }
}
