package com.patrick.lrcreader.core

import android.content.Context
import com.patrick.lrcreader.core.config.SongIdKeyResolver

object ChordPaletteStore {
    private const val PREF = "chord_palette_prefs"
    private const val KEY_PREFIX = "palette_"

    fun loadRaw(context: Context, songId: String): String {
        val key = buildKey(songId) ?: return ""
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_PREFIX + key, "")
            .orEmpty()
    }

    fun saveRaw(context: Context, songId: String, raw: String) {
        val key = buildKey(songId) ?: return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFIX + key, raw)
            .apply()
    }

    fun clear(context: Context, songId: String) {
        val key = buildKey(songId) ?: return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PREFIX + key)
            .apply()
    }

    private fun buildKey(songId: String): String? =
        SongIdKeyResolver.songScopedKey(songId)
}
