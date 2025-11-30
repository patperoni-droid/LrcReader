package com.patrick.lrcreader.core

import android.content.Context

/**
 * Préférence : retour automatique vers la playlist
 * quelques secondes avant la fin du morceau.
 *
 * Par défaut : activé (true) pour garder le comportement actuel.
 */
object AutoReturnPrefs {

    private const val PREFS_NAME = "auto_return_prefs"
    private const val KEY_ENABLED = "auto_return_playlist_enabled"

    fun isEnabled(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}