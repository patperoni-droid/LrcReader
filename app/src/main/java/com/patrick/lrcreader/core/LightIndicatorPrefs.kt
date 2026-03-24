package com.patrick.lrcreader.core

import android.content.Context

object LightIndicatorPrefs {

    private const val PREFS_NAME = "light_indicator_prefs"
    private const val KEY_ENABLED = "light_indicator_enabled"

    fun isEnabled(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
