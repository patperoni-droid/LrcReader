package com.patrick.lrcreader.core

// petit repo en mémoire pour l’instant
object PlaylistRepository {

    // nom de playlist -> liste de chansons (Uri en string)
    private val playlists: MutableMap<String, MutableList<String>> = linkedMapOf()

    // nom de playlist -> chansons déjà jouées (URIs en string)
    private val playedSongs: MutableMap<String, MutableSet<String>> = mutableMapOf()

    // 👇 observable très simple
    var version: Int = 0
        private set

    fun getPlaylists(): List<String> = playlists.keys.toList()

    fun addPlaylist(name: String) {
        if (name.isBlank()) return
        if (!playlists.containsKey(name)) {
            playlists[name] = mutableListOf()
            version++          // 👈 on prévient
        }
    }

    fun assignSongToPlaylist(playlistName: String, songUri: String) {
        val list = playlists.getOrPut(playlistName) { mutableListOf() }
        if (!list.contains(songUri)) {
            list.add(songUri)
            version++          // 👈 on prévient
        }
    }

    /**
     * On renvoie d'abord les titres NON joués,
     * puis les titres joués. Comme ça l’écran les a déjà dans le bon ordre.
     */
    fun getSongsFor(playlistName: String): List<String> {
        val all = playlists[playlistName] ?: emptyList()
        val played = playedSongs[playlistName] ?: emptySet()

        val notPlayed = all.filter { it !in played }
        val alreadyPlayed = all.filter { it in played }

        return notPlayed + alreadyPlayed
    }

    // --- nouvelles fonctions ---

    fun markSongPlayed(playlistName: String, songUri: String) {
        val set = playedSongs.getOrPut(playlistName) { mutableSetOf() }
        if (set.add(songUri)) {
            // si c’est vraiment nouveau → on incrémente
            version++
        }
    }

    fun isSongPlayed(playlistName: String, songUri: String): Boolean {
        return playedSongs[playlistName]?.contains(songUri) == true
    }
}