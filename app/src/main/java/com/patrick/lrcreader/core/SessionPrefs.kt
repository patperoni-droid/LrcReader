package com.patrick.lrcreader.core

import android.content.Context

object SessionPrefs {

    private const val PREFS_NAME = "session_prefs"

    private const val KEY_TAB = "tab"
    private const val KEY_QUICK_PLAYLIST = "quick_playlist"
    private const val KEY_OPENED_PLAYLIST = "opened_playlist"

    private const val KEY_LAST_TRACK_URI = "last_track_uri"
    private const val KEY_LAST_PLAYLIST_NAME = "last_playlist_name"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -------- Onglet courant --------

    fun saveTab(context: Context, tabName: String) {
        prefs(context)
            .edit()
            .putString(KEY_TAB, tabName)
            .apply()
    }

    fun getTab(context: Context): String? {
        return prefs(context).getString(KEY_TAB, null)
    }

    // -------- Quick playlist sélectionnée --------

    fun saveQuickPlaylist(context: Context, name: String?) {
        prefs(context)
            .edit()
            .putString(KEY_QUICK_PLAYLIST, name)
            .apply()
    }

    fun getQuickPlaylist(context: Context): String? {
        return prefs(context).getString(KEY_QUICK_PLAYLIST, null)
    }

    // -------- Playlist "ouverte" (AllPlaylists) --------

    fun saveOpenedPlaylist(context: Context, name: String?) {
        prefs(context)
            .edit()
            .putString(KEY_OPENED_PLAYLIST, name)
            .apply()
    }

    fun getOpenedPlaylist(context: Context): String? {
        return prefs(context).getString(KEY_OPENED_PLAYLIST, null)
    }

    // -------- DERNIÈRE SESSION (titre + playlist) --------

    fun saveLastSession(context: Context, trackUri: String?, playlistName: String?) {
        prefs(context)
            .edit()
            .putString(KEY_LAST_TRACK_URI, trackUri)
            .putString(KEY_LAST_PLAYLIST_NAME, playlistName)
            .apply()
    }

    fun getLastSession(context: Context): Pair<String?, String?> {
        val p = prefs(context)
        val uri = p.getString(KEY_LAST_TRACK_URI, null)
        val name = p.getString(KEY_LAST_PLAYLIST_NAME, null)
        return uri to name
    }

    fun clearLastSession(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_LAST_TRACK_URI)
            .remove(KEY_LAST_PLAYLIST_NAME)
            .apply()
    }
}