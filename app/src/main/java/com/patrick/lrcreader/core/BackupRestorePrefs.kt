package com.patrick.lrcreader.core

import android.content.Context

object BackupRestorePrefs {

    private const val PREFS_NAME = "backup_restore_prefs"
    private const val KEY_RESTORED_ONCE = "restored_once"

    fun setRestoredOnce(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RESTORED_ONCE, value)
            .apply()
    }

    fun wasRestoredOnce(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RESTORED_ONCE, false)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}