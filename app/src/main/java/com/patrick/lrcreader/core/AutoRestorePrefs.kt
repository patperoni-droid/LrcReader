package com.patrick.lrcreader.core

import android.content.Context

object AutoRestorePrefs {
    private const val PREFS_NAME = "auto_restore_prefs"
    private const val KEY_ENABLED = "auto_restore_enabled"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}