package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import com.patrick.lrcreader.core.config.ConfigJsonAtomicFileIo
import org.json.JSONObject
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
    private const val CONFIG_FILE_NAME = "session_state.json"
    private const val JSON_SCHEMA_VERSION = 1
    private const val JSON_KEY_SCHEMA_VERSION = "schemaVersion"
    private const val JSON_KEY_TAB = "tab"
    private const val JSON_KEY_QUICK_PLAYLIST = "quickPlaylist"
    private const val JSON_KEY_OPENED_PLAYLIST = "openedPlaylist"
    private const val JSON_KEY_LAST_TRACK_URI = "lastTrackUri"
    private const val JSON_KEY_LAST_PLAYLIST_NAME = "lastPlaylistName"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, MODE)

    fun ensureInitialized(context: Context): Boolean {
        return ConfigJsonAtomicFileIo.ensureInitialized(
            context = context,
            fileName = CONFIG_FILE_NAME,
            defaultRawJson = defaultJsonState().toJson().toString(2),
            tag = TAG
        )
    }

    private fun prefsFile(context: Context): File {
        val dataDir = context.applicationInfo.dataDir ?: ""
        return File("$dataDir/shared_prefs/$PREFS_NAME.xml")
    }

    private data class JsonSessionState(
        val tab: String?,
        val quickPlaylist: String?,
        val openedPlaylist: String?,
        val lastTrackUri: String?,
        val lastPlaylistName: String?
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put(JSON_KEY_SCHEMA_VERSION, JSON_SCHEMA_VERSION)
            put(JSON_KEY_TAB, tab ?: JSONObject.NULL)
            put(JSON_KEY_QUICK_PLAYLIST, quickPlaylist ?: JSONObject.NULL)
            put(JSON_KEY_OPENED_PLAYLIST, openedPlaylist ?: JSONObject.NULL)
            put(JSON_KEY_LAST_TRACK_URI, lastTrackUri ?: JSONObject.NULL)
            put(JSON_KEY_LAST_PLAYLIST_NAME, lastPlaylistName ?: JSONObject.NULL)
        }
    }

    private fun defaultJsonState(): JsonSessionState =
        JsonSessionState(
            tab = null,
            quickPlaylist = null,
            openedPlaylist = null,
            lastTrackUri = null,
            lastPlaylistName = null
        )

    private fun readJsonState(context: Context): JsonSessionState? {
        val raw = ConfigJsonAtomicFileIo.readRaw(
            context = context,
            fileName = CONFIG_FILE_NAME,
            tag = TAG,
            defaultRawJson = defaultJsonState().toJson().toString(2)
        ) ?: return null

        return runCatching {
            val root = JSONObject(raw)
            JsonSessionState(
                tab = root.optNullableString(JSON_KEY_TAB),
                quickPlaylist = root.optNullableString(JSON_KEY_QUICK_PLAYLIST),
                openedPlaylist = root.optNullableString(JSON_KEY_OPENED_PLAYLIST),
                lastTrackUri = root.optNullableString(JSON_KEY_LAST_TRACK_URI),
                lastPlaylistName = root.optNullableString(JSON_KEY_LAST_PLAYLIST_NAME)
            )
        }.onFailure {
            Log.w(TAG, "SessionPrefs.readJsonState parse failed", it)
        }.getOrNull()
    }

    private fun writeJsonState(context: Context, state: JsonSessionState) {
        val ok = ConfigJsonAtomicFileIo.writeRawAtomic(
            context = context,
            fileName = CONFIG_FILE_NAME,
            rawJson = state.toJson().toString(2),
            tag = TAG,
            defaultRawJson = defaultJsonState().toJson().toString(2)
        )
        if (!ok) {
            Log.w(TAG, "SessionPrefs.writeJsonState skipped/failed file=$CONFIG_FILE_NAME")
        }
    }

    private fun mutateJsonState(
        context: Context,
        mutate: (JsonSessionState) -> JsonSessionState
    ) {
        val current = readJsonState(context) ?: JsonSessionState(
            tab = prefs(context).getString(KEY_TAB, null),
            quickPlaylist = prefs(context).getString(KEY_QUICK_PLAYLIST, null),
            openedPlaylist = prefs(context).getString(KEY_OPENED_PLAYLIST, null),
            lastTrackUri = prefs(context).getString(KEY_LAST_TRACK_URI, null),
            lastPlaylistName = prefs(context).getString(KEY_LAST_PLAYLIST_NAME, null)
        )
        writeJsonState(context, mutate(current))
    }

    // -------- Onglet courant --------

    fun saveTab(context: Context, tabName: String) {
        runCatching {
            prefs(context)
                .edit()
                .putString(KEY_TAB, tabName)
                .apply()
            mutateJsonState(context) { it.copy(tab = tabName) }
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
        val v = readJsonState(context)?.tab ?: p.getString(KEY_TAB, null)
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
            mutateJsonState(context) { it.copy(quickPlaylist = name) }
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
        val v = readJsonState(context)?.quickPlaylist ?: p.getString(KEY_QUICK_PLAYLIST, null)
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
            mutateJsonState(context) { it.copy(openedPlaylist = name) }
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
        val v = readJsonState(context)?.openedPlaylist ?: p.getString(KEY_OPENED_PLAYLIST, null)
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
            mutateJsonState(context) {
                it.copy(lastTrackUri = trackUri, lastPlaylistName = playlistName)
            }
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
        val json = readJsonState(context)
        val uri = json?.lastTrackUri ?: p.getString(KEY_LAST_TRACK_URI, null)
        val name = json?.lastPlaylistName ?: p.getString(KEY_LAST_PLAYLIST_NAME, null)
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
        mutateJsonState(context) {
            it.copy(lastTrackUri = null, lastPlaylistName = null)
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.takeIf { it.isNotBlank() }
    }
}
