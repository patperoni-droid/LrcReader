package com.patrick.lrcreader.core

// petit repo en mémoire pour l’instant
object PlaylistRepository {

    // nom de playlist -> liste de chansons (Uri en string)
    private val playlists: MutableMap<String, MutableList<String>> = linkedMapOf(
        // tu peux laisser vide
    )

    fun getPlaylists(): List<String> = playlists.keys.toList()

    fun addPlaylist(name: String) {
        if (name.isBlank()) return
        if (!playlists.containsKey(name)) {
            playlists[name] = mutableListOf()
        }
    }

    fun assignSongToPlaylist(playlistName: String, songUri: String) {
        val list = playlists.getOrPut(playlistName) { mutableListOf() }
        if (!list.contains(songUri)) {
            list.add(songUri)
        }
    }

    // 🔴👉 c’est celle qui te manque
    fun getSongsFor(playlistName: String): List<String> {
        return playlists[playlistName]?.toList() ?: emptyList()
    }
}