package com.patrick.lrcreader.core

import android.content.Context
import androidx.core.content.edit

object UiEntryPrefs {
    private const val PREFS_NAME = "ui_entry_prefs"
    private const val KEY_SHOW_DJ_TAB = "show_dj_tab"
    private const val KEY_SHOW_MAIN_BUS_TAB = "show_main_bus_tab"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun showDjTab(context: Context): Boolean =
        if (EditionConfig.isLite) {
            true
        } else {
            prefs(context).getBoolean(KEY_SHOW_DJ_TAB, true)
        }

    fun setShowDjTab(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOW_DJ_TAB, value) }
    }

    fun showMainBusTab(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_MAIN_BUS_TAB, true)

    fun setShowMainBusTab(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOW_MAIN_BUS_TAB, value) }
    }
}
