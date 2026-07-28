package com.patrick.lrcreader.core

import android.content.Context

object ArrangementPlaybackModePrefs {
    private const val PREFS_NAME = "arrangement_playback_mode_prefs"
    private const val KEY_COMPATIBILITY_MODE_ENABLED = "compatibility_mode_enabled"

    fun isCompatibilityModeEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMPATIBILITY_MODE_ENABLED, false)

    fun setCompatibilityModeEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPATIBILITY_MODE_ENABLED, enabled)
            .apply()
    }
}
