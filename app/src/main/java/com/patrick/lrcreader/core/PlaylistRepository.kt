package com.patrick.lrcreader.core

import androidx.compose.runtime.mutableStateOf

// petit repo en mémoire pour l’instant
object PlaylistRepository {

    // nom de playlist -> liste de chansons (Uri en string) dans l’ordre
    private val playlists: MutableMap<String, MutableList<String>> = linkedMapOf()

    // nom de playlist -> chansons déjà jouées
    private val playedSongs: MutableMap<String, MutableSet<String>> = mutableMapOf()

    // nom de playlist -> (uri -> titre personnalisé)
    private val customTitles: MutableMap<String, MutableMap<String, String>> = mutableMapOf()

    // clé de rafraîchissement pour Compose
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
     * On renvoie d'abord les titres NON joués, puis les titres joués.
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

    /** Vide complètement le repo (playlists + titres joués). */
    fun clearAll() {
        playlists.clear()
        playedSongs.clear()
        customTitles.clear()
        bump()
    }

    /** Pour être sûr qu’une playlist existe (pratique à l’import). */
    fun createIfNotExists(name: String) {
        addPlaylist(name)
    }

    /** Ajoute une chanson sans logique d’affichage (utilisé à l’import). */
    fun addSong(name: String, uri: String) {
        assignSongToPlaylist(name, uri)
    }

    /** Alias lisible pour l’import. */
    fun importMarkPlayed(playlistName: String, uri: String) {
        markSongPlayed(playlistName, uri)
    }

    /** Vue brute de tout le bazar (debug). */
    fun exportRaw(): Map<String, Pair<List<String>, Set<String>>> {
        return playlists.mapValues { (plName, list) ->
            val played = playedSongs[plName] ?: emptySet()
            list.toList() to played.toSet()
        }
    }

    /** Récupère la liste brute telle qu’elle est stockée (pour BackupManager). */
    fun getAllSongsRaw(playlistName: String): List<String> {
        return playlists[playlistName]?.toList() ?: emptyList()
    }

    /** Récupère les morceaux joués bruts (pour BackupManager). */
    fun getPlayedRaw(playlistName: String): List<String> {
        return playedSongs[playlistName]?.toList() ?: emptyList()
    }

    // ------------------ INTERNE ------------------

    private fun bump() {
        version.value = version.value + 1
    }

    /** force une recomposition manuelle (utile après un import) */
    fun touch() = bump()

    fun updatePlayListOrder(playlistName: String, newOrder: List<String>) {
        val current = playlists[playlistName] ?: return
        current.clear()
        current.addAll(newOrder)
        bump()
    }

    // ------------------ NOUVEAU : titres personnalisés ------------------

    /** Récupère un titre custom si on en a mis un pour cette playlist. */
    fun getCustomTitle(playlistName: String, uri: String): String? {
        return customTitles[playlistName]?.get(uri)
    }

    /** Définit / change le titre affiché pour une chanson dans une playlist. */
    fun renameSongInPlaylist(playlistName: String, uri: String, newTitle: String) {
        val map = customTitles.getOrPut(playlistName) { mutableMapOf() }
        map[uri] = newTitle
        bump()
    }

    /** Quand on retire un titre, on vire aussi son éventuel nom custom. */
    fun removeSongFromPlaylist(playlistName: String, uri: String) {
        val list = playlists[playlistName] ?: return
        list.remove(uri)
        customTitles[playlistName]?.remove(uri)
        bump()
    }

    /** Remet tous les titres de la playlist en "non joués" */
    fun resetPlayedFor(playlistName: String) {
        playedSongs.remove(playlistName)
        bump()
    }

    /** (optionnel) remet tous les titres de toutes les playlists en "non joués" */
    fun resetAllPlayed() {
        playedSongs.clear()
        bump()
    }

    // ------------------ NOUVEAU : renommer une playlist ------------------

    /**
     * Renomme une playlist.
     * On déplace aussi les infos "played" et les titres custom.
     * Retourne true si ok, false si le nouveau nom existe déjà ou si l’ancien n’existe pas.
     */
    fun renamePlaylist(oldName: String, newName: String): Boolean {
        val clean = newName.trim()
        if (clean.isEmpty()) return false
        if (!playlists.containsKey(oldName)) return false
        if (playlists.containsKey(clean)) return false  // on ne veut pas écraser une autre

        // 1. déplacer la liste de chansons
        val songs = playlists.remove(oldName) ?: mutableListOf()
        playlists[clean] = songs

        // 2. déplacer l’état "joué"
        val played = playedSongs.remove(oldName)
        if (played != null) {
            playedSongs[clean] = played
        }

        // 3. déplacer les titres custom
        val custom = customTitles.remove(oldName)
        if (custom != null) {
            customTitles[clean] = custom
        }

        bump()
        return true
    }
}