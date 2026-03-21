package com.patrick.lrcreader.core

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

object LegacyLibraryVisibilityPrefs {

    private const val PREFS_NAME = "legacy_library_visibility"
    private const val KEY_SHOW_OLD_WORLD = "show_old_world"

    val version = mutableIntStateOf(0)

    fun isOldWorldVisible(context: Context): Boolean {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_OLD_WORLD, true)
    }

    fun setOldWorldVisible(context: Context, visible: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previous = prefs.getBoolean(KEY_SHOW_OLD_WORLD, true)
        if (previous == visible) return

        prefs.edit().putBoolean(KEY_SHOW_OLD_WORLD, visible).apply()
        version.intValue += 1
    }
}
