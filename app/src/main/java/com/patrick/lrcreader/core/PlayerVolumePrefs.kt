package com.patrick.lrcreader.core

import android.content.Context
import androidx.core.content.edit

/**
 * Volume global du LECTEUR (bus principal).
 *
 * On travaille en 0f..1f "UI" (linéaire).
 * La courbe douce (u³) est appliquée côté PlayerScreen.
 */
object PlayerVolumePrefs {

    private const val PREFS_NAME = "player_volume_prefs"
    private const val KEY_VOLUME = "player_volume_ui" // 0f..1f

    /** Sauvegarde le niveau du fader LECTEUR (0f..1f) */
    fun save(context: Context, uiLevel: Float) {
        val clamped = uiLevel.coerceIn(0f, 1f)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putFloat(KEY_VOLUME, clamped)
        }
    }

    /** Charge le niveau du fader LECTEUR (0f..1f). Par défaut : 1f (plein pot). */
    fun load(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_VOLUME, 1f)
    }
}