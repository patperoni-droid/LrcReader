package com.patrick.lrcreader.core

import androidx.compose.runtime.mutableStateOf
import android.os.SystemClock

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
    // NOW PLAYING + "PLAYED" APRÈS 10s DE LECTURE RÉELLE
    // -------------------------------------------------

    private var nowPlayingPlaylist: String? = null
    private var nowPlayingUri: String? = null

    private var playbackAccumMs: Long = 0L
    private var lastTickElapsedMs: Long? = null
    private var playedTriggeredForCurrent: Boolean = false

    // ✅ garde-fou : moment exact où on a armé le suivi (pour bloquer un "played" trop tôt)
    private var nowPlayingArmedAtElapsedMs: Long? = null

    private const val PLAYED_DELAY_MS = 10_000L

    /**
     * À appeler au clic sur un titre (quand on ouvre le player).
     * Ne marque rien "played" ici : on arme juste le suivi.
     */
    fun setNowPlaying(playlistName: String, uri: String) {
        nowPlayingPlaylist = playlistName
        nowPlayingUri = uri
        playbackAccumMs = 0L
        lastTickElapsedMs = null
        playedTriggeredForCurrent = false
        nowPlayingArmedAtElapsedMs = SystemClock.elapsedRealtime() // ✅ armement
    }

    /** Optionnel : si tu sors du player ou si tu changes de contexte. */
    fun clearNowPlaying() {
        nowPlayingPlaylist = null
        nowPlayingUri = null
        playbackAccumMs = 0L
        lastTickElapsedMs = null
        playedTriggeredForCurrent = false
        nowPlayingArmedAtElapsedMs = null
    }

    /**
     * À appeler régulièrement depuis le player (ex: toutes les 200ms).
     * On cumule uniquement quand isPlaying == true.
     * Quand on dépasse 10s de lecture réelle => on marque "played" + on met à la fin.
     */
    fun onPlaybackTick(isPlaying: Boolean) {
        val pl = nowPlayingPlaylist ?: return
        val uri = nowPlayingUri ?: return

        // On ne déclenche qu'une fois par titre
        if (playedTriggeredForCurrent) return

        // Si déjà joué (ex: import, restore), inutile
        if (isSongPlayed(pl, uri)) {
            playedTriggeredForCurrent = true
            return
        }

        val now = SystemClock.elapsedRealtime()

        if (!isPlaying) {
            // pause => on stoppe l'accumulation
            lastTickElapsedMs = null
            return
        }

        val last = lastTickElapsedMs
        if (last == null) {
            lastTickElapsedMs = now
            return
        }

        val delta = (now - last).coerceAtLeast(0L)
        playbackAccumMs += delta
        lastTickElapsedMs = now

        if (playbackAccumMs >= PLAYED_DELAY_MS) {
            playedTriggeredForCurrent = true
            nowPlayingArmedAtElapsedMs = null // ✅ plus besoin du garde-fou

            // ✅ Marque joué + met à la fin, puis bump() une seule fois
            val set = playedSongs.getOrPut(pl) { mutableSetOf() }
            set.add(uri)

            val list = playlists[pl]
            if (list != null) {
                val idx = list.indexOf(uri)
                if (idx != -1) {
                    list.removeAt(idx)
                    list.add(uri)
                }
            }

            bump()
        }
    }

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
        // ✅ GARDE-FOU : si quelqu’un essaye de marquer "joué" trop tôt sur le titre armé
        if (playlistName == nowPlayingPlaylist && uri == nowPlayingUri) {
            val armedAt = nowPlayingArmedAtElapsedMs
            if (armedAt != null) {
                val elapsed = SystemClock.elapsedRealtime() - armedAt
                if (elapsed in 0 until PLAYED_DELAY_MS) {
                    // on ignore : c’est exactement ton bug "ça descend direct"
                    return
                }
            }
        }

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

    fun isSongToReview(playlistName: String, uri: String): Boolean {
        return reviewSongs[playlistName]?.contains(uri) == true
    }

    fun setSongToReview(playlistName: String, uri: String, toReview: Boolean) {
        val set = reviewSongs.getOrPut(playlistName) { mutableSetOf() }
        val changed = if (toReview) set.add(uri) else set.remove(uri)
        if (changed) bump()
    }

    fun clearReviewForPlaylist(playlistName: String) {
        reviewSongs.remove(playlistName)
        bump()
    }

    // -------------------------------------------------
    // TITRES PERSONNALISÉS (RENOMMAGE)
    // -------------------------------------------------

    fun clearCustomTitleEverywhere(uri: String) {
        customTitles.forEach { (_, map) -> map.remove(uri) }
        bump()
    }

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

    fun renameSongInPlaylist(playlistName: String, uri: String, newTitle: String) {
        val clean = newTitle.trim()
        val map = customTitles.getOrPut(playlistName) { mutableMapOf() }
        if (clean.isEmpty()) map.remove(uri) else map[uri] = clean
        bump()
    }

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

    fun setPlaylistColor(playlist: String, color: Long) {
        playlistColors[playlist] = color
        bump()
    }

    fun getPlaylistColor(playlist: String): Long {
        return playlistColors[playlist] ?: 0xFFE86FFF
    }

    // -------------------------------------------------
    // TOOLS POUR BACKUP / IMPORT
    // -------------------------------------------------

    fun clearAll() {
        playlists.clear()
        playedSongs.clear()
        reviewSongs.clear()
        customTitles.clear()
        playlistColors.clear()
        bump()
    }

    fun moveSongToEnd(playlistName: String, uri: String) {
        // ✅ GARDE-FOU : idem, on bloque le "descend direct" si ça arrive trop tôt
        if (playlistName == nowPlayingPlaylist && uri == nowPlayingUri) {
            val armedAt = nowPlayingArmedAtElapsedMs
            if (armedAt != null) {
                val elapsed = SystemClock.elapsedRealtime() - armedAt
                if (elapsed in 0 until PLAYED_DELAY_MS) return
            }
        }

        val list = playlists[playlistName] ?: return
        val idx = list.indexOf(uri)
        if (idx == -1) return
        list.removeAt(idx)
        list.add(uri)
        bump()
    }

    fun createIfNotExists(name: String) = addPlaylist(name)

    fun addSong(name: String, uri: String) = assignSongToPlaylist(name, uri)

    fun importMarkPlayed(playlistName: String, uri: String) = markSongPlayed(playlistName, uri)

    fun exportRaw(): Map<String, Pair<List<String>, Set<String>>> {
        return playlists.mapValues { (plName, list) ->
            val played = playedSongs[plName] ?: emptySet()
            list.toList() to played.toSet()
        }
    }

    fun renamePlaylist(oldName: String, newName: String): Boolean {
        val clean = newName.trim()
        if (clean.isEmpty()) return false
        if (!playlists.containsKey(oldName)) return false
        if (playlists.containsKey(clean)) return false

        val songs = playlists.remove(oldName) ?: mutableListOf()
        playlists[clean] = songs
        playedSongs[clean] = playedSongs.remove(oldName) ?: mutableSetOf()
        reviewSongs[clean] = reviewSongs.remove(oldName) ?: mutableSetOf()
        customTitles[clean] = customTitles.remove(oldName) ?: mutableMapOf()
        playlistColors[clean] = playlistColors.remove(oldName) ?: 0xFFE86FFF

        bump()
        return true
    }

    fun deletePlaylist(name: String) {
        if (!playlists.containsKey(name)) return
        playlists.remove(name)
        playedSongs.remove(name)
        reviewSongs.remove(name)
        customTitles.remove(name)
        playlistColors.remove(name)
        bump()
    }

    fun replaceSongUriEverywhere(oldUri: String, newUri: String) {
        if (oldUri == newUri) return

        playlists.forEach { (_, list) ->
            for (i in list.indices) if (list[i] == oldUri) list[i] = newUri
        }
        playedSongs.forEach { (_, set) -> if (set.remove(oldUri)) set.add(newUri) }
        reviewSongs.forEach { (_, set) -> if (set.remove(oldUri)) set.add(newUri) }
        customTitles.forEach { (_, map) ->
            val t = map.remove(oldUri)
            if (t != null) map[newUri] = t
        }

        bump()
    }

    // -------------------------------------------------
    // INTERNE
    // -------------------------------------------------

    private fun bump() {
        version.value = version.value + 1
    }

    fun touch() = bump()
}