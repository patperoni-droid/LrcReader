package com.patrick.lrcreader.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    data class LastPlayerState(
        val uri: String?,
        val playlist: String?,
        val positionMs: Int
    )

    /**
     * Exporte l’état logique de l’app (sans les fichiers audio).
     */
    fun exportState(
        context: Context,
        lastPlayer: LastPlayerState?,
        libraryFolders: List<String> = emptyList()
    ): String {
        val root = JSONObject()
        root.put("version", 1)

        // 1) playlists
        val allPlaylists = PlaylistRepository.getPlaylists()
        val playlistsArray = JSONArray()

        allPlaylists.forEach { plName ->
            val songs = PlaylistRepository.getSongsFor(plName)
            val songsArray = JSONArray()

            // ATTENTION : getSongsFor() te renvoie non-joués puis joués
            // c’est ce qu’on veut sauvegarder tel quel
            songs.forEach { uri ->
                val songObj = JSONObject()
                songObj.put("uri", uri)
                val played = PlaylistRepository.isSongPlayed(plName, uri)
                songObj.put("played", played)
                songsArray.put(songObj)
            }

            val plObj = JSONObject()
            plObj.put("name", plName)
            plObj.put("songs", songsArray)
            playlistsArray.put(plObj)
        }

        root.put("playlists", playlistsArray)

        // 2) dossiers de bibliothèque
        val foldersArray = JSONArray()
        libraryFolders.forEach { foldersArray.put(it) }
        root.put("libraryFolders", foldersArray)

        // 3) player
        val lastObj = JSONObject()
        lastObj.put("uri", lastPlayer?.uri)
        lastObj.put("playlist", lastPlayer?.playlist)
        lastObj.put("positionMs", lastPlayer?.positionMs ?: 0)
        root.put("lastPlayer", lastObj)

        return root.toString(2)
    }

    /**
     * Restaure l’état depuis un JSON.
     */
    fun importState(
        context: Context,
        json: String,
        onLastPlayerRestored: (LastPlayerState?) -> Unit
    ) {
        val root = JSONObject(json)

        // 1) playlists
        val playlistsArray = root.optJSONArray("playlists") ?: JSONArray()

        // on vide l’existant AVANT de remettre
        PlaylistRepository.clearAll()

        for (i in 0 until playlistsArray.length()) {
            val plObj = playlistsArray.getJSONObject(i)
            val name = plObj.getString("name")

            PlaylistRepository.createIfNotExists(name)

            val songsArray = plObj.optJSONArray("songs") ?: JSONArray()
            for (j in 0 until songsArray.length()) {
                val sObj = songsArray.getJSONObject(j)
                val uri = sObj.getString("uri")
                val played = sObj.optBoolean("played", false)

                PlaylistRepository.addSong(name, uri)
                if (played) {
                    PlaylistRepository.importMarkPlayed(name, uri)
                }
            }
        }

        // 2) dossiers de bibliothèque (à stocker où tu veux)
        val foldersArray = root.optJSONArray("libraryFolders") ?: JSONArray()
        val restoredFolders = mutableListOf<String>()
        for (i in 0 until foldersArray.length()) {
            restoredFolders += foldersArray.getString(i)
        }
        // ici tu peux faire : saveInPrefs(restoredFolders)

        // 3) player
        val lastObj = root.optJSONObject("lastPlayer")
        if (lastObj != null) {
            val lp = LastPlayerState(
                uri = lastObj.optString("uri", null),
                playlist = lastObj.optString("playlist", null),
                positionMs = lastObj.optInt("positionMs", 0)
            )
            onLastPlayerRestored(lp)
        } else {
            onLastPlayerRestored(null)
        }
    }
}