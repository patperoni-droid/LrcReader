package com.patrick.lrcreader.core

import androidx.compose.runtime.mutableStateOf

// petit repo en mémoire pour l’instant
object PlaylistRepository {

    // nom de playlist -> liste de chansons (Uri en string)
    private val playlists: MutableMap<String, MutableList<String>> = linkedMapOf()

    // nom de playlist -> chansons déjà jouées
    private val playedSongs: MutableMap<String, MutableSet<String>> = mutableMapOf()

    // 👇 clé de rafraîchissement pour Compose
    var version = mutableStateOf(0)
        private set

    fun getPlaylists(): List<String> = playlists.keys.toList()

    fun addPlaylist(name: String) {
        if (name.isBlank()) return
        if (!playlists.containsKey(name)) {
            playlists[name] = mutableListOf()
            bump()
        }
    }

    fun assignSongToPlaylist(playlistName: String, songUri: String) {
        val list = playlists.getOrPut(playlistName) { mutableListOf() }
        if (!list.contains(songUri)) {
            list.add(songUri)
            bump()
        }
    }

    /**
     * On renvoie d'abord les titres NON joués,
     * puis les titres joués.
     */
    fun getSongsFor(playlistName: String): List<String> {
        val all = playlists[playlistName] ?: emptyList()
        val played = playedSongs[playlistName] ?: emptySet()

        val notPlayed = all.filter { it !in played }
        val alreadyPlayed = all.filter { it in played }

        return notPlayed + alreadyPlayed
    }

    fun isSongPlayed(playlistName: String, uri: String): Boolean {
        return playedSongs[playlistName]?.contains(uri) == true
    }

    fun markSongPlayed(playlistName: String, uri: String) {
        val set = playedSongs.getOrPut(playlistName) { mutableSetOf() }
        if (set.add(uri)) {
            bump()
        }
    }

    private fun bump() {
        // on force la recomposition
        version.value = version.value + 1
    }
}