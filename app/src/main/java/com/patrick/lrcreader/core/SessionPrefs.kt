package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import java.io.File

object SessionPrefs {

    private const val PREFS_NAME = "session_prefs"
    private const val MODE = Context.MODE_PRIVATE

    private const val KEY_TAB = "tab"
    private const val KEY_QUICK_PLAYLIST = "quick_playlist"
    private const val KEY_OPENED_PLAYLIST = "opened_playlist"

    private const val KEY_LAST_TRACK_URI = "last_track_uri"
    private const val KEY_LAST_PLAYLIST_NAME = "last_playlist_name"
    private const val TAG = "BOOTSTEP"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, MODE)

    private fun prefsFile(context: Context): File {
        val dataDir = context.applicationInfo.dataDir ?: ""
        return File("$dataDir/shared_prefs/$PREFS_NAME.xml")
    }

    // -------- Onglet courant --------

    fun saveTab(context: Context, tabName: String) {
        runCatching {
            prefs(context)
                .edit()
                .putString(KEY_TAB, tabName)
                .apply()
            val file = prefsFile(context)
            Log.d(
                TAG,
                "SessionPrefs.saveTab prefs=$PREFS_NAME mode=$MODE file=${file.absolutePath} exists=${file.exists()} tab=$tabName"
            )
        }.onFailure {
            Log.e(TAG, "SessionPrefs.saveTab failed prefs=$PREFS_NAME tab=$tabName", it)
        }
    }

    fun getTab(context: Context): String? {
        val p = prefs(context)
        val v = p.getString(KEY_TAB, null)
        val file = prefsFile(context)
        Log.d(
            TAG,
            "SessionPrefs.getTab prefs=$PREFS_NAME mode=$MODE file=${file.absolutePath} fileExists=${file.exists()} keyExists=${p.contains(KEY_TAB)} value=$v"
        )
        return v
    }

    // -------- Quick playlist sélectionnée --------

    fun saveQuickPlaylist(context: Context, name: String?) {
        runCatching {
            prefs(context)
                .edit()
                .putString(KEY_QUICK_PLAYLIST, name)
                .apply()
            val file = prefsFile(context)
            Log.d(
                TAG,
                "SessionPrefs.saveQuick prefs=$PREFS_NAME mode=$MODE file=${file.absolutePath} exists=${file.exists()} quick=$name"
            )
        }.onFailure {
            Log.e(TAG, "SessionPrefs.saveQuick failed prefs=$PREFS_NAME quick=$name", it)
        }
    }

    fun getQuickPlaylist(context: Context): String? {
        val p = prefs(context)
        val v = p.getString(KEY_QUICK_PLAYLIST, null)
        val file = prefsFile(context)
        Log.d(
            TAG,
            "SessionPrefs.getQuick prefs=$PREFS_NAME mode=$MODE file=${file.absolutePath} fileExists=${file.exists()} keyExists=${p.contains(KEY_QUICK_PLAYLIST)} value=$v"
        )
        return v
    }

    // -------- Playlist "ouverte" (AllPlaylists) --------

    fun saveOpenedPlaylist(context: Context, name: String?) {
        runCatching {
            prefs(context)
                .edit()
                .putString(KEY_OPENED_PLAYLIST, name)
                .apply()
            val file = prefsFile(context)
            Log.d(
                TAG,
                "SessionPrefs.saveOpened prefs=$PREFS_NAME mode=$MODE file=${file.absolutePath} exists=${file.exists()} opened=$name"
            )
        }.onFailure {
            Log.e(TAG, "SessionPrefs.saveOpened failed prefs=$PREFS_NAME opened=$name", it)
        }
    }

    fun getOpenedPlaylist(context: Context): String? {
        val p = prefs(context)
        val v = p.getString(KEY_OPENED_PLAYLIST, null)
        val file = prefsFile(context)
        Log.d(
            TAG,
            "SessionPrefs.getOpened prefs=$PREFS_NAME mode=$MODE file=${file.absolutePath} fileExists=${file.exists()} keyExists=${p.contains(KEY_OPENED_PLAYLIST)} value=$v"
        )
        return v
    }

    // -------- DERNIÈRE SESSION (titre + playlist) --------

    fun saveLastSession(context: Context, trackUri: String?, playlistName: String?) {
        runCatching {
            prefs(context)
                .edit()
                .putString(KEY_LAST_TRACK_URI, trackUri)
                .putString(KEY_LAST_PLAYLIST_NAME, playlistName)
                .apply()
            val file = prefsFile(context)
            Log.d(
                TAG,
                "SessionPrefs.saveLast prefs=$PREFS_NAME mode=$MODE file=${file.absolutePath} exists=${file.exists()} uri=$trackUri playlist=$playlistName"
            )
        }.onFailure {
            Log.e(TAG, "SessionPrefs.saveLast failed prefs=$PREFS_NAME uri=$trackUri playlist=$playlistName", it)
        }
    }

    fun getLastSession(context: Context): Pair<String?, String?> {
        val p = prefs(context)
        val uri = p.getString(KEY_LAST_TRACK_URI, null)
        val name = p.getString(KEY_LAST_PLAYLIST_NAME, null)
        val file = prefsFile(context)
        Log.d(
            TAG,
            "SessionPrefs.getLast prefs=$PREFS_NAME mode=$MODE file=${file.absolutePath} fileExists=${file.exists()} hasUri=${p.contains(KEY_LAST_TRACK_URI)} hasPlaylist=${p.contains(KEY_LAST_PLAYLIST_NAME)} uri=$uri playlist=$name"
        )
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
