package com.patrick.lrcreader.core

import android.content.Context

object LibraryFullModePolicy {
    private const val PREFS_NAME = "library_full_mode_prefs"
    private const val KEY_UNLOCKED = "library_full_mode_unlocked"

    fun isFullModeEnabled(context: Context): Boolean {
        if (PlaylistTrackLimitPolicy.isUnlimitedEdition(context)) return true
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_UNLOCKED, false)
    }

    fun enableLocalFullMode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_UNLOCKED, true)
            .apply()
    }
}
