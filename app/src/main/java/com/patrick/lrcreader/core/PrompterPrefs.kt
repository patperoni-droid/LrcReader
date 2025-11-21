package com.patrick.lrcreader.core

import android.content.Context

object PrompterPrefs {
    private const val PREF = "prompter_prefs"
    private const val KEY_SPEED = "scroll_speed"

    /** Récupère la vitesse de défilement mémorisée (0.2f à 2.0f). */
    fun getSpeed(context: Context): Float {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_SPEED, 1.0f) // 1.0 = vitesse normale
    }

    /** Sauvegarde la vitesse de défilement. */
    fun saveSpeed(context: Context, speed: Float) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_SPEED, speed.coerceIn(0.2f, 2.0f))
            .apply()
    }
}