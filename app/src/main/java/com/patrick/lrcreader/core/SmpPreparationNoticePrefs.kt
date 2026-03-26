package com.patrick.lrcreader.core

import android.content.Context

object SmpPreparationNoticePrefs {
    private const val PREFS_NAME = "smp_preparation_notice_prefs"
    private const val KEY_SHOW_NOTICE = "show_notice"

    fun shouldShow(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_NOTICE, true)
    }

    fun setShouldShow(context: Context, show: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_NOTICE, show)
            .apply()
    }
}
