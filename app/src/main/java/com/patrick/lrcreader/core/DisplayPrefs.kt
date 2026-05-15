// core/DisplayPrefs.kt
package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import androidx.core.content.edit

object DisplayPrefs {

    private const val PREFS_NAME = "display_prefs"
    private const val KEY_CONCERT_MODE = "concert_mode" // true = mode concert (dégradé), false = tout uniforme
    private const val KEY_LYRICS_READABILITY_MODE = "lyrics_readability_mode"
    private const val KEY_ACTIVE_LYRICS_LINE_COUNT = "active_lyrics_line_count"

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

    fun isLyricsReadabilityMode(ctx: Context): Boolean {
        return prefs(ctx).getBoolean(KEY_LYRICS_READABILITY_MODE, false)
    }

    fun setLyricsReadabilityMode(ctx: Context, value: Boolean) {
        prefs(ctx).edit {
            putBoolean(KEY_LYRICS_READABILITY_MODE, value)
        }
    }

    fun getActiveLyricsLineCount(ctx: Context): Int {
        val value = prefs(ctx).getInt(KEY_ACTIVE_LYRICS_LINE_COUNT, 1).coerceIn(1, 3)
        Log.d("LYRICS_ACTIVE_LINES_DIAG", "PREF_READ value=$value")
        return value
    }

    fun setActiveLyricsLineCount(ctx: Context, value: Int) {
        val safeValue = value.coerceIn(1, 3)
        Log.d("LYRICS_ACTIVE_LINES_DIAG", "PREF_SET value=$safeValue")
        prefs(ctx).edit {
            putInt(KEY_ACTIVE_LYRICS_LINE_COUNT, safeValue)
        }
    }
}
