package com.patrick.lrcreader.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gère l’export / import de l’état de l’appli (playlists + chansons jouées + dernier morceau)
 */
object BackupManager {

    data class LastPlayed(
        val uri: String,
        val playlistName: String?,
        val positionMs: Long
    )

    /**
     * Export complet vers JSON
     */
    fun exportState(
        context: Context,
        lastPlayer: LastPlayed?,          // 👈 nouveau
        libraryFolders: List<String>
    ): String {
        val root = JSONObject()

        // 1) playlists
        val playlistsJson = JSONObject()
        PlaylistRepository.getPlaylists().forEach { plName ->
            val songs = PlaylistRepository.getAllSongsRaw(plName)   // on ajoute un accès brut
            playlistsJson.put(plName, JSONArray(songs))
        }
        root.put("playlists", playlistsJson)

        // 2) songs joués
        val playedJson = JSONObject()
        PlaylistRepository.getPlaylists().forEach { plName ->
            val played = PlaylistRepository.getPlayedRaw(plName)
            playedJson.put(plName, JSONArray(played))
        }
        root.put("played", playedJson)

        // 3) dossiers (pour plus tard)
        root.put("libraryFolders", JSONArray(libraryFolders))

        // 4) 👇 dernier morceau
        if (lastPlayer != null) {
            val lp = JSONObject().apply {
                put("uri", lastPlayer.uri)
                put("playlistName", lastPlayer.playlistName ?: JSONObject.NULL)
                put("positionMs", lastPlayer.positionMs)
            }
            root.put("lastPlayed", lp)
        }

        return root.toString(2)
    }

    /**
     * Import depuis JSON
     * @param onLastPlayed retrouvé → on te la redonne
     */
    fun importState(
        context: Context,
        json: String,
        onLastPlayed: (LastPlayed?) -> Unit = {}
    ) {
        val root = JSONObject(json)

        // 1) playlists
        val playlistsJson = root.optJSONObject("playlists")
        if (playlistsJson != null) {
            // on purgera d'abord
            PlaylistRepository.clearAll()
            val names = playlistsJson.keys()
            while (names.hasNext()) {
                val name = names.next()
                PlaylistRepository.addPlaylist(name)
                val arr = playlistsJson.getJSONArray(name)
                for (i in 0 until arr.length()) {
                    val uri = arr.getString(i)
                    PlaylistRepository.assignSongToPlaylist(name, uri)
                }
            }
        }

        // 2) played
        val playedJson = root.optJSONObject("played")
        if (playedJson != null) {
            val names = playedJson.keys()
            while (names.hasNext()) {
                val name = names.next()
                val arr = playedJson.getJSONArray(name)
                for (i in 0 until arr.length()) {
                    val uri = arr.getString(i)
                    PlaylistRepository.markSongPlayed(name, uri)
                }
            }
        }

        // 3) lastPlayed
        val lpJson = root.optJSONObject("lastPlayed")
        if (lpJson != null) {
            val uri = lpJson.optString("uri", "")
            val playlistName = if (lpJson.isNull("playlistName")) null else lpJson.optString("playlistName", null)
            val pos = lpJson.optLong("positionMs", 0L)
            if (uri.isNotBlank()) {
                onLastPlayed(
                    LastPlayed(
                        uri = uri,
                        playlistName = playlistName,
                        positionMs = pos
                    )
                )
            } else {
                onLastPlayed(null)
            }
        } else {
            onLastPlayed(null)
        }
    }
}