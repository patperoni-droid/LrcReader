package com.patrick.lrcreader.core

import android.content.Context

object TrackTempoPrefs {

    private const val PREF = "track_tempo_prefs"

    fun getTempo(context: Context, uri: String): Float? {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = sp.getFloat(uri, -1f)
        return if (raw < 0f) null else raw
    }

    fun saveTempo(context: Context, uri: String, tempo: Float) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putFloat(uri, tempo)
            .apply()
    }
}