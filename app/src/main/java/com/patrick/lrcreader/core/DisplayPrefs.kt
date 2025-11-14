// core/DisplayPrefs.kt
package com.patrick.lrcreader.core

import android.content.Context
import androidx.core.content.edit

object DisplayPrefs {

    private const val PREFS_NAME = "display_prefs"
    private const val KEY_CONCERT_MODE = "concert_mode" // true = mode concert (dégradé), false = tout uniforme

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isConcertMode(ctx: Context): Boolean {
        // tu peux changer la valeur par défaut ici (true ou false)
        return prefs(ctx).getBoolean(KEY_CONCERT_MODE, true)
    }

    fun setConcertMode(ctx: Context, value: Boolean) {
        prefs(ctx).edit {
            putBoolean(KEY_CONCERT_MODE, value)
        }
    }
}