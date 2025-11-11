package com.patrick.lrcreader.core

import android.content.Context

/**
 * Sauvegarde le réglage de niveau par morceau.
 * On stocke un float (dB) par URI de piste.
 */
object TrackVolumePrefs {
    private const val PREF = "track_volume_prefs"

    fun saveVolume(context: Context, uri: String, gainDb: Float) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putFloat(uri, gainDb)
            .apply()
    }

    fun getVolume(context: Context, uri: String): Float? {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return if (sp.contains(uri)) {
            sp.getFloat(uri, 0f)
        } else {
            null
        }
    }
}