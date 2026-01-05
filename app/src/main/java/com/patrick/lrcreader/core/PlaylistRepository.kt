package com.patrick.lrcreader.core

import androidx.compose.runtime.mutableStateOf

/**
 * Petit repo en mémoire pour gérer :
 * - playlists
 * - ordre des titres
 * - titres joués
 * - titres marqués "à revoir"
 * - titres personnalisés (rename)
 * - couleur de playlist
 *
 * Tout est encore en RAM, mais prêt pour être sauvegardé par BackupManager.
 */
object PlaylistRepository {

    // nom de playlist -> liste de chansons (Uri en String) dans l’ordre
    private val playlists: MutableMap<String, MutableList<String>> = linkedMapOf()

    // nom de playlist -> chansons déjà jouées
    private val playedSongs: MutableMap<String, MutableSet<String>> = mutableMapOf()

    // nom de playlist -> chansons marquées "à revoir"
    private val reviewSongs: MutableMap<String, MutableSet<String>> = mutableMapOf()

    // nom de playlist -> (uri -> titre personnalisé)
    private val customTitles: MutableMap<String, MutableMap<String, String>> = mutableMapOf()

    // nom de playlist -> couleur (ARGB en Long)
    private val playlistColors: MutableMap<String, Long> = mutableMapOf()

    // clé de rafraîchissement pour Compose
    var version = mutableStateOf(0)
        private set

    // -------------------------------------------------
    // PLAYLISTS / CHANSONS
    // -------------------------------------------------

    fun getPlaylists(): List<String> = playlists.keys.toList()

    fun addPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        if (!playlists.containsKey(clean)) {
            playlists[clean] = mutableListOf()
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
     * Renvoie la liste pour affichage.
     * On garde l’ordre défini par la playlist, mais on met les titres joués en fin :
     * - d’abord tous les non joués
     * - puis les joués (toujours dans leur ordre relatif).
     *
     * Les titres "à revoir" ne changent pas l’ordre, ils sont juste stylés en rouge dans l’UI.
     */
    fun getSongsFor(playlistName: String): List<String> {
        val all = playlists[playlistName] ?: emptyList()
        val played = playedSongs[playlistName] ?: emptySet()
        val notPlayed = all.filter { it !in played }
        val alreadyPlayed = all.filter { it in played }
        return notPlayed + alreadyPlayed
    }

    /** Vue brute telle qu’elle est stockée, pour la sauvegarde. */
    fun getAllSongsRaw(playlistName: String): List<String> {
        return playlists[playlistName]?.toList() ?: emptyList()
    }

    /** Réordonne une playlist (drag & drop). */
    fun updatePlayListOrder(playlistName: String, newOrder: List<String>) {
        val current = playlists[playlistName] ?: return
        current.clear()
        current.addAll(newOrder)
        bump()
    }

    // -------------------------------------------------
    // ETAT "JOUÉ"
    // -------------------------------------------------

    fun isSongPlayed(playlistName: String, uri: String): Boolean {
        return playedSongs[playlistName]?.contains(uri) == true
    }

    fun markSongPlayed(playlistName: String, uri: String) {
        val set = playedSongs.getOrPut(playlistName) { mutableSetOf() }
        if (set.add(uri)) {
            bump()
        }
    }

    /** Pour la sauvegarde : liste brute des titres joués. */
    fun getPlayedRaw(playlistName: String): List<String> {
        return playedSongs[playlistName]?.toList() ?: emptyList()
    }

    /** Remet tous les titres de la playlist en "non joués". */
    fun resetPlayedFor(playlistName: String) {
        playedSongs.remove(playlistName)
        bump()
    }

    /** Remet tout le monde en "non joué". */
    fun resetAllPlayed() {
        playedSongs.clear()
        bump()
    }

    // -------------------------------------------------
    // TITRES "À REVOIR"
    // -------------------------------------------------

    /** Est-ce que ce titre est marqué "à revoir" pour cette playlist ? */
    fun isSongToReview(playlistName: String, uri: String): Boolean {
        return reviewSongs[playlistName]?.contains(uri) == true
    }

    /**
     * Marque / démarque un titre comme "à revoir".
     * toReview = true  -> on ajoute
     * toReview = false -> on enlève
     */
    fun setSongToReview(playlistName: String, uri: String, toReview: Boolean) {
        val set = reviewSongs.getOrPut(playlistName) { mutableSetOf() }
        val changed = if (toReview) set.add(uri) else set.remove(uri)
        if (changed) bump()
    }

    /** Pour plus tard si on veut vider les "à revoir" d’une playlist. */
    fun clearReviewForPlaylist(playlistName: String) {
        reviewSongs.remove(playlistName)
        bump()
    }

    // -------------------------------------------------
    // TITRES PERSONNALISÉS (RENOMMAGE)
    // -------------------------------------------------
    /** Supprime le titre personnalisé (customTitle) pour cet URI dans TOUTES les playlists. */
    fun clearCustomTitleEverywhere(uri: String) {
        customTitles.forEach { (_, map) ->
            map.remove(uri)
        }
        bump()
    }

    /** Récupère un titre custom si on en a un, sinon null. */
    fun getCustomTitle(playlistName: String, uri: String): String? {
        return customTitles[playlistName]?.get(uri)
    }

    fun getAnyCustomTitleForUri(uriString: String): String? {
        return runCatching {
            val pls = getPlaylists()
            for (pl in pls) {
                val t = getCustomTitle(pl, uriString)
                if (!t.isNullOrBlank()) return@runCatching t
            }
            null
        }.getOrNull()
    }
    /** Définit / change le titre affiché pour une chanson dans une playlist. */
    fun renameSongInPlaylist(playlistName: String, uri: String, newTitle: String) {
        val clean = newTitle.trim()
        val map = customTitles.getOrPut(playlistName) { mutableMapOf() }
        if (clean.isEmpty()) {
            map.remove(uri)
        } else {
            map[uri] = clean
        }
        bump()
    }

    /** Quand on retire un titre, on vire aussi son éventuel nom custom et son flag "à revoir". */
    fun removeSongFromPlaylist(playlistName: String, uri: String) {
        val list = playlists[playlistName] ?: return
        list.remove(uri)
        customTitles[playlistName]?.remove(uri)
        reviewSongs[playlistName]?.remove(uri)
        playedSongs[playlistName]?.remove(uri)
        bump()
    }

    // -------------------------------------------------
    // COULEURS DE PLAYLIST
    // -------------------------------------------------
    /**
     * Supprime le titre personnalisé (customTitle) pour cet URI dans TOUTES les playlists.
     * Utilisé quand on renomme réellement un fichier audio : on veut que la playlist affiche le nom réel.
     */

    fun setPlaylistColor(playlist: String, color: Long) {
        playlistColors[playlist] = color
        bump()
    }

    fun getPlaylistColor(playlist: String): Long {
        return playlistColors[playlist] ?: 0xFFE86FFF // rose par défaut
    }

    // -------------------------------------------------
    // TOOLS POUR BACKUP / IMPORT
    // -------------------------------------------------

    /** Vide complètement le repo (playlists + états). */
    fun clearAll() {
        playlists.clear()
        playedSongs.clear()
        reviewSongs.clear()
        customTitles.clear()
        playlistColors.clear()
        bump()
    }
    fun moveSongToEnd(playlistName: String, uri: String) {
        val list = playlists[playlistName] ?: return
        val idx = list.indexOf(uri)
        if (idx == -1) return

        // on enlève et on remet à la fin
        list.removeAt(idx)
        list.add(uri)
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

    /** Vue brute de tout pour debug éventuel. */
    fun exportRaw(): Map<String, Pair<List<String>, Set<String>>> {
        return playlists.mapValues { (plName, list) ->
            val played = playedSongs[plName] ?: emptySet()
            list.toList() to played.toSet()
        }
    }

    // -------------------------------------------------
    // RENOMMAGE DE PLAYLIST
    // -------------------------------------------------

    /**
     * Renomme une playlist.
     * On déplace aussi les infos "played", "review", les titres custom et la couleur.
     */
    fun renamePlaylist(oldName: String, newName: String): Boolean {
        val clean = newName.trim()
        if (clean.isEmpty()) return false
        if (!playlists.containsKey(oldName)) return false
        if (playlists.containsKey(clean)) return false

        // 1. déplacer la liste de chansons
        val songs = playlists.remove(oldName) ?: mutableListOf()
        playlists[clean] = songs

        // 2. déplacer l’état "joué"
        playedSongs[clean] = playedSongs.remove(oldName) ?: mutableSetOf()

        // 3. déplacer l’état "à revoir"
        reviewSongs[clean] = reviewSongs.remove(oldName) ?: mutableSetOf()

        // 4. déplacer les titres custom
        customTitles[clean] = customTitles.remove(oldName) ?: mutableMapOf()

        // 5. déplacer la couleur
        playlistColors[clean] = playlistColors.remove(oldName) ?: 0xFFE86FFF

        bump()
        return true
    }
    // -------------------------------------------------
    // SUPPRESSION DE PLAYLIST
    // -------------------------------------------------

    fun deletePlaylist(name: String) {
        // Si la playlist n'existe pas, on ne fait rien
        if (!playlists.containsKey(name)) return

        // 1. on enlève la liste de chansons
        playlists.remove(name)

        // 2. on enlève tous les états associés
        playedSongs.remove(name)
        reviewSongs.remove(name)
        customTitles.remove(name)
        playlistColors.remove(name)

        // 3. on force la recomposition
        bump()
    }
    // -------------------------------------------------
    // INTERNE
    // -------------------------------------------------
// -------------------------------------------------
// FIX: MIGRATION D'URI APRÈS RENOMMAGE D'UN FICHIER
// -------------------------------------------------

    /**
     * Quand un fichier est renommé dans la bibliothèque, Android peut changer son URI.
     * Les playlists (et états played/review + titres custom) pointent encore sur l'ancien URI,
     * donc: pas jouable + nom pas à jour.
     */
    fun replaceSongUriEverywhere(oldUri: String, newUri: String) {
        if (oldUri == newUri) return

        // 1) playlists: remplacer l'URI dans la liste
        playlists.forEach { (_, list) ->
            for (i in list.indices) {
                if (list[i] == oldUri) list[i] = newUri
            }
        }
        /**
         * Quand on renomme un fichier dans la bibliothèque, on veut que la playlist affiche le NOM RÉEL
         * (donc on vire les titres custom éventuels qui masqueraient le nouveau nom).
         */


        // 2) playedSongs: déplacer l'URI si présent
        playedSongs.forEach { (_, set) ->
            if (set.remove(oldUri)) set.add(newUri)
        }

        // 3) reviewSongs: déplacer l'URI si présent
        reviewSongs.forEach { (_, set) ->
            if (set.remove(oldUri)) set.add(newUri)
        }

        // 4) customTitles: déplacer la clé oldUri -> newUri (en gardant le titre custom)
        customTitles.forEach { (_, map) ->
            val t = map.remove(oldUri)
            if (t != null) map[newUri] = t
        }

        bump()
    }
    private fun bump() {
        version.value = version.value + 1
    }

    /** force une recomposition manuelle (utile après un import) */
    fun touch() = bump()
}




/**
 * À adapter si ton repo stocke déjà les customTitle différemment.
 * Ici je suppose que tu as déjà une fonction getCustomTitle(playlist, uri)
 * puisque tu as clearCustomTitleEverywhere().
 */
