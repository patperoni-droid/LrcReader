package com.patrick.lrcreader.core

import android.content.Context
import androidx.core.content.edit

/** Local device preference, like PlayerVolumePrefs. Only PadsBusController writes it. */
object PadsVolumePrefs {
    internal const val PREFS_NAME = "pads_volume_prefs"
    internal const val KEY_VOLUME = "pads_volume_ui"
    const val DEFAULT_UI_LEVEL = 0.5f // Preserve the qualified prototype's initial gain (0.125).

    fun load(context: Context): Float {
        val level = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_VOLUME, DEFAULT_UI_LEVEL)
        return if (level.isFinite()) level.coerceIn(0f, 1f) else DEFAULT_UI_LEVEL
    }

    internal fun save(context: Context, uiLevel: Float) {
        require(uiLevel.isFinite())
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putFloat(KEY_VOLUME, uiLevel.coerceIn(0f, 1f))
        }
    }
}
