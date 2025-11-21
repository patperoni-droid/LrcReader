package com.patrick.lrcreader.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gère l’export / import de l’état de l’appli
 * (playlists + chansons jouées + dernier morceau + fond sonore + réglages d’édition
 *  + titres à revoir + couleurs de playlists)
 */
object BackupManager {

    data class LastPlayed(
        val uri: String,
        val playlistName: String?,
        val positionMs: Long
    )

    fun exportState(
        context: Context,
        lastPlayer: LastPlayed?,          // peut être null
        libraryFolders: List<String>
    ): String {
        val root = JSONObject()

        // 1) playlists : ordre complet des titres
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

        // 3) dossiers (ce que tu avais déjà)
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

        // 6) réglages d’édition
        run {
            val allEdits = EditPrefs.getAllEdits(context)
            if (allEdits.isNotEmpty()) {
                val editsJson = JSONObject()
                allEdits.forEach { (uriString, data) ->
                    val one = JSONObject().apply {
                        put("startMs", data.startMs)
                        put("endMs", data.endMs)
                    }
                    editsJson.put(uriString, one)
                }
                root.put("edits", editsJson)
            }
        }

        // 7) morceaux "à revoir"
        run {
            val reviewJson = JSONObject()
            PlaylistRepository.getPlaylists().forEach { plName ->
                val allSongs = PlaylistRepository.getAllSongsRaw(plName)
                val toReview = allSongs.filter { uri ->
                    PlaylistRepository.isSongToReview(plName, uri)
                }
                if (toReview.isNotEmpty()) {
                    reviewJson.put(plName, JSONArray(toReview))
                }
            }
            if (reviewJson.length() > 0) {
                root.put("review", reviewJson)
            }
        }

        // 8) couleurs de playlists
        run {
            val colorsJson = JSONObject()
            PlaylistRepository.getPlaylists().forEach { plName ->
                val colorLong = PlaylistRepository.getPlaylistColor(plName)
                colorsJson.put(plName, colorLong)
            }
            if (colorsJson.length() > 0) {
                root.put("colors", colorsJson)
            }
        }

        return root.toString(2)
    }

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
            val volume = fillerJson.optDouble("volume", 0.25).toFloat()
            if (uriStr.isNotBlank()) {
                try {
                    val uri = Uri.parse(uriStr)
                    FillerSoundPrefs.saveFillerUri(context, uri)
                    FillerSoundPrefs.saveFillerVolume(context, volume)

                    // on essaie de reprendre la permission
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (_: Exception) { }
                } catch (_: Exception) { }
            }
        }

        // 5) réglages d’édition
        val editsJson = root.optJSONObject("edits")
        if (editsJson != null) {
            // on repart propre
            EditPrefs.clearAll(context)

            val keys = editsJson.keys()
            while (keys.hasNext()) {
                val uriString = keys.next()
                val one = editsJson.getJSONObject(uriString)
                val startMs = one.optLong("startMs", 0L)
                val endMs = one.optLong("endMs", 0L)

                EditPrefs.saveEdit(
                    context,
                    uriString,
                    EditPrefs.EditData(startMs, endMs)
                )

                // bonus : on tente de reprendre la permission sur ce fichier-là aussi
                try {
                    val uri = Uri.parse(uriString)
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }
            }
        }

        // 6) morceaux "à revoir"
        val reviewJson = root.optJSONObject("review")
        if (reviewJson != null) {
            val names = reviewJson.keys()
            while (names.hasNext()) {
                val name = names.next()
                val arr = reviewJson.getJSONArray(name)

                // on nettoie d’abord les anciens flags de cette playlist
                PlaylistRepository.clearReviewForPlaylist(name)

                for (i in 0 until arr.length()) {
                    val uri = arr.getString(i)
                    PlaylistRepository.setSongToReview(name, uri, true)
                }
            }
        }

        // 7) couleurs de playlists
        val colorsJson = root.optJSONObject("colors")
        if (colorsJson != null) {
            val names = colorsJson.keys()
            while (names.hasNext()) {
                val name = names.next()
                val colorLong = colorsJson.optLong(name, 0xFFE86FFF)
                PlaylistRepository.setPlaylistColor(name, colorLong)
            }
        }
    }
}