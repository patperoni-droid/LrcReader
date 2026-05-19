// core/DisplayPrefs.kt
package com.patrick.lrcreader.core

import android.content.Context
import androidx.core.content.edit

object DisplayPrefs {

    private const val PREFS_NAME = "display_prefs"
    private const val KEY_CONCERT_MODE = "concert_mode" // true = mode concert (dégradé), false = tout uniforme
    private const val KEY_LYRICS_READABILITY_MODE = "lyrics_readability_mode"
    private const val KEY_GUIDED_READING_COLORS_ENABLED = "guided_reading_colors_enabled"
    private const val KEY_GUIDED_READING_COLOR_A = "guided_reading_color_a"
    private const val KEY_GUIDED_READING_COLOR_B = "guided_reading_color_b"
    private const val KEY_LYRICS_TEXT_SIZE = "lyrics_text_size"
    const val DEFAULT_GUIDED_READING_COLOR_A: Int = 0xFFFFFFFF.toInt()
    const val DEFAULT_GUIDED_READING_COLOR_B: Int = 0xFFFFF176.toInt()

    enum class LyricsTextSize {
        SMALL,
        NORMAL,
        LARGE,
        EXTRA_LARGE;

        companion object {
            fun fromStoredValue(value: String?): LyricsTextSize {
                return entries.firstOrNull { it.name == value } ?: NORMAL
            }
        }
    }

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

    fun isGuidedReadingColorsEnabled(ctx: Context): Boolean {
        return prefs(ctx).getBoolean(KEY_GUIDED_READING_COLORS_ENABLED, false)
    }

    fun setGuidedReadingColorsEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit {
            putBoolean(KEY_GUIDED_READING_COLORS_ENABLED, value)
        }
    }

    fun getGuidedReadingColorA(ctx: Context): Int {
        return prefs(ctx).getInt(KEY_GUIDED_READING_COLOR_A, DEFAULT_GUIDED_READING_COLOR_A)
    }

    fun setGuidedReadingColorA(ctx: Context, value: Int) {
        prefs(ctx).edit {
            putInt(KEY_GUIDED_READING_COLOR_A, value)
        }
    }

    fun getGuidedReadingColorB(ctx: Context): Int {
        return prefs(ctx).getInt(KEY_GUIDED_READING_COLOR_B, DEFAULT_GUIDED_READING_COLOR_B)
    }

    fun setGuidedReadingColorB(ctx: Context, value: Int) {
        prefs(ctx).edit {
            putInt(KEY_GUIDED_READING_COLOR_B, value)
        }
    }

    fun getLyricsTextSize(ctx: Context): LyricsTextSize {
        return LyricsTextSize.fromStoredValue(
            prefs(ctx).getString(KEY_LYRICS_TEXT_SIZE, LyricsTextSize.NORMAL.name)
        )
    }

    fun setLyricsTextSize(ctx: Context, value: LyricsTextSize) {
        prefs(ctx).edit {
            putString(KEY_LYRICS_TEXT_SIZE, value.name)
        }
    }
}
