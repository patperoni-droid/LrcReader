package com.patrick.lrcreader.core

import androidx.compose.runtime.mutableStateOf

// petit repo en mémoire pour l’instant
object PlaylistRepository {

    // nom de playlist -> liste de chansons (Uri en string) dans l’ordre
    private val playlists: MutableMap<String, MutableList<String>> = linkedMapOf()

    // nom de playlist -> chansons déjà jouées
    private val playedSongs: MutableMap<String, MutableSet<String>> = mutableMapOf()

    // 👇 clé de rafraîchissement pour Compose
    var version = mutableStateOf(0)
        private set

    // ------------------ EXISTANT ------------------

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

    // ------------------ AJOUTS POUR SAUVEGARDE ------------------

    /**
     * Vide complètement le repo (playlists + titres joués).
     * Utile quand on importe une sauvegarde complète.
     */
    fun clearAll() {
        playlists.clear()
        playedSongs.clear()
        bump()
    }

    /**
     * Pour être sûr qu’une playlist existe (pratique à l’import).
     */
    fun createIfNotExists(name: String) {
        addPlaylist(name)
    }

    /**
     * Ajoute une chanson sans vérifier l’ordre “non joué / joué”.
     * On garde le même comportement que assignSongToPlaylist.
     */
    fun addSong(name: String, uri: String) {
        assignSongToPlaylist(name, uri)
    }

    /**
     * Marque une chanson comme jouée lors d’un import.
     * (c’est juste un alias lisible)
     */
    fun importMarkPlayed(playlistName: String, uri: String) {
        markSongPlayed(playlistName, uri)
    }

    /**
     * Pour debug / sauvegarde : renvoie une vue brute de ce qu’on a.
     */
    fun exportRaw(): Map<String, Pair<List<String>, Set<String>>> {
        return playlists.mapValues { (plName, list) ->
            val played = playedSongs[plName] ?: emptySet()
            list.toList() to played.toSet()
        }
    }

    private fun bump() {
        // on force la recomposition
        version.value = version.value + 1
    }
}