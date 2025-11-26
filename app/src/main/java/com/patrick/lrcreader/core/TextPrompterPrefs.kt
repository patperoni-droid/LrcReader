package com.patrick.lrcreader.core

import android.content.Context

object TextPrompterPrefs {

    private const val PREFS_NAME = "text_prompter_prefs"
    private const val KEY_SPEED_PREFIX = "speed_"

    fun getSpeed(context: Context, id: String): Float? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_SPEED_PREFIX + id
        if (!prefs.contains(key)) return null
        return prefs.getFloat(key, 1f)
    }

    fun saveSpeed(context: Context, id: String, speed: Float) {
        val clamped = speed.coerceIn(0.3f, 3f)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_SPEED_PREFIX + id, clamped)
            .apply()
    }
}