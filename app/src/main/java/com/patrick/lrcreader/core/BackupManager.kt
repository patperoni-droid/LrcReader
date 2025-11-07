package com.patrick.lrcreader.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gère l’export / import de l’état de l’appli
 * (playlists + chansons jouées + dernier morceau + fond sonore)
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
        lastPlayer: LastPlayed?,          // peut être null
        libraryFolders: List<String>
    ): String {
        val root = JSONObject()

        // 1) playlists
        val playlistsJson = JSONObject()
        PlaylistRepository.getPlaylists().forEach { plName ->
            val songs = PlaylistRepository.getAllSongsRaw(plName)
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

        // 3) dossiers (optionnel / pour plus tard)
        root.put("libraryFolders", JSONArray(libraryFolders))

        // 4) dernier morceau
        if (lastPlayer != null) {
            val lp = JSONObject().apply {
                put("uri", lastPlayer.uri)
                put("playlistName", lastPlayer.playlistName ?: JSONObject.NULL)
                put("positionMs", lastPlayer.positionMs)
            }
            root.put("lastPlayed", lp)
        }

        // 5) fond sonore
        run {
            val uri = FillerSoundPrefs.getFillerUri(context)
            val vol = FillerSoundPrefs.getFillerVolume(context)
            if (uri != null) {
                val fillerJson = JSONObject().apply {
                    put("uri", uri.toString())
                    put("volume", vol)
                }
                root.put("fillerSound", fillerJson)
            }
        }

        return root.toString(2)
    }

    /**
     * Import depuis JSON
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
            val playlistName =
                if (lpJson.isNull("playlistName")) null else lpJson.optString("playlistName", null)
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

        // 4) fond sonore
        val fillerJson = root.optJSONObject("fillerSound")
        if (fillerJson != null) {
            val uriStr = fillerJson.optString("uri", "")
            val volume = fillerJson
                .optDouble("volume", 0.25)
                .toFloat()
                .coerceIn(0f, 1f)

            if (uriStr.isNotBlank()) {
                try {
                    val uri = Uri.parse(uriStr)
                    // on remet dans les prefs
                    FillerSoundPrefs.saveFillerUri(context, uri)
                    FillerSoundPrefs.saveFillerVolume(context, volume)

                    // on ESSAIE de reprendre la permission
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                        // si Android refuse, on n'explose pas
                    }
                } catch (_: Exception) {
                    // URI pas valide → on ignore
                }
            }
        }
    }
}