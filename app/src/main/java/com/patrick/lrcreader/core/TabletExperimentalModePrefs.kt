package com.patrick.lrcreader.core

import android.content.Context

object TabletExperimentalModePrefs {
    private const val PREFS_NAME = "tablet_experimental_mode_prefs"
    private const val KEY_ENABLED = "tablet_experimental_mode_enabled"

    fun isEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun hasSavedValue(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_ENABLED)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
