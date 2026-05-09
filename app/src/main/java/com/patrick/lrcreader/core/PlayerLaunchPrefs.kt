package com.patrick.lrcreader.core

import android.content.Context

enum class PlayerLaunchMode(val storageValue: String) {
    ALWAYS("always"),
    NEVER("never"),
    AUTOMATIC("automatic");

    companion object {
        fun fromStorageValue(value: String?): PlayerLaunchMode {
            return entries.firstOrNull { it.storageValue == value } ?: ALWAYS
        }
    }
}

object PlayerLaunchPrefs {
    private const val PREFS_NAME = "player_launch_prefs"
    private const val KEY_MODE = "player_open_mode"

    fun getMode(context: Context): PlayerLaunchMode {
        val storedValue = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, PlayerLaunchMode.ALWAYS.storageValue)
        return PlayerLaunchMode.fromStorageValue(storedValue)
    }

    fun setMode(context: Context, mode: PlayerLaunchMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.storageValue)
            .apply()
    }
}
