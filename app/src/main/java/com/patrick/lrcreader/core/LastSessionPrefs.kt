package com.patrick.lrcreader.core

import android.content.Context

data class LastSession(
    val playlistName: String?,
    val trackUri: String?
)

object LastSessionPrefs {

    private const val PREFS_NAME = "live_in_pocket_last_session"
    private const val KEY_PLAYLIST_NAME = "playlist_name"
    private const val KEY_TRACK_URI = "track_uri"

    fun save(
        context: Context,
        playlistName: String?,
        trackUri: String?
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PLAYLIST_NAME, playlistName)
            .putString(KEY_TRACK_URI, trackUri)
            .apply()
    }

    fun load(context: Context): LastSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val playlistName = prefs.getString(KEY_PLAYLIST_NAME, null)
        val trackUri = prefs.getString(KEY_TRACK_URI, null)
        if (playlistName == null && trackUri == null) return null
        return LastSession(playlistName, trackUri)
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}