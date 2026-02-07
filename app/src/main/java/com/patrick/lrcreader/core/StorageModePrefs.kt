package com.patrick.lrcreader.core

import android.content.Context

object StorageModePrefs {

    enum class Mode { SAF, INTERNAL }

    private const val PREFS = "spl_storage_mode"
    private const val KEY_MODE = "mode"

    fun get(context: Context): Mode {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = sp.getString(KEY_MODE, Mode.SAF.name) ?: Mode.SAF.name
        return runCatching { Mode.valueOf(v) }.getOrDefault(Mode.SAF)
    }

    fun set(context: Context, mode: Mode) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun clear(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
    }
}