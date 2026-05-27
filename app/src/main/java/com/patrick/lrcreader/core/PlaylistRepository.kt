package com.patrick.lrcreader.core

import androidx.compose.runtime.mutableStateOf
import android.net.Uri
import android.os.SystemClock
import android.util.Log

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
    private const val PERSIST_LOG_TAG = "PLAYLIST_PERSIST"
    private const val DEMO_TITLES_TAG = "DEMO_TITLES"

    // nom de playlist -> liste de chansons (Uri en String) dans l’ordre
    private val playlists: MutableMap<String, MutableList<String>> = linkedMapOf()
    private val playlistItems: MutableMap<String, MutableMap<String, PlaylistItem>> = mutableMapOf()

    // nom de playlist -> chansons déjà jouées
    private val playedSongs: MutableMap<String, MutableSet<String>> = mutableMapOf()
    private val playedSongIds: MutableMap<String, MutableSet<String>> = mutableMapOf()

    // nom de playlist -> chansons marquées "à revoir"
    private val reviewSongs: MutableMap<String, MutableSet<String>> = mutableMapOf()
    private val reviewSongIds: MutableMap<String, MutableSet<String>> = mutableMapOf()

    // nom de playlist -> (uri -> titre personnalisé)
    private val customTitles: MutableMap<String, MutableMap<String, String>> = mutableMapOf()
    private val customTitlesBySongId: MutableMap<String, MutableMap<String, String>> = mutableMapOf()

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
    private var nowPlayingSongId: String? = null

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
        val cleanUri = normalizeUriKey(uri) ?: return
        nowPlayingPlaylist = playlistName
        nowPlayingUri = cleanUri
        nowPlayingSongId = resolveSongIdForState(playlistName, cleanUri)
        playbackAccumMs = 0L
        lastTickElapsedMs = null
        playedTriggeredForCurrent = false
        nowPlayingArmedAtElapsedMs = SystemClock.elapsedRealtime() // ✅ armement
    }

    /** Optionnel : si tu sors du player ou si tu changes de contexte. */
    fun clearNowPlaying() {
        nowPlayingPlaylist = null
        nowPlayingUri = null
        nowPlayingSongId = null
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
        val songId = nowPlayingSongId

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
            var changed = markSongPlayedState(
                playlistName = pl,
                uri = uri,
                songId = songId
            )

            val list = playlists[pl]
            var moved = false
            if (list != null) {
                val idx = list.indexOf(uri).takeIf { it >= 0 }
                    ?: songId?.takeIf { currentSongId ->
                        playlistSongIdOccurrenceCount(pl, currentSongId) == 1
                    }?.let { currentSongId ->
                        list.indexOfFirst { item -> resolveSongIdForState(pl, item) == currentSongId }
                            .takeIf { it >= 0 }
                    }
                if (idx != null) {
                    list.removeAt(idx)
                    list.add(uri)
                    moved = true
                }
            }

            if (changed || moved) {
                bump()
            }
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
            debugPersistLog {
                "repo.addPlaylist name=$clean playlists=${playlists.keys.joinToString(prefix = "[", postfix = "]")}"
            }
            bump()
        }
    }

    fun assignSongToPlaylist(
        playlistName: String,
        songUri: String,
        songId: String? = null
    ) {
        val cleanSongUri = normalizeUriKey(songUri) ?: return
        val list = playlists.getOrPut(playlistName) { mutableListOf() }
        val items = playlistItems.getOrPut(playlistName) { mutableMapOf() }
        val cleanSongId = normalizeSongId(songId)
        val existingItem = items[cleanSongUri]
        val previousSongId = normalizeSongId(existingItem?.songId)
        val nextItem = PlaylistItem(
            uri = cleanSongUri,
            songId = cleanSongId ?: existingItem?.songId
        )
        var changed = false
        if (existingItem != nextItem) {
            items[cleanSongUri] = nextItem
            changed = true
        }
        if (!list.contains(cleanSongUri)) {
            list.add(cleanSongUri)
            changed = true
        }
        val resolvedSongId = normalizeSongId(nextItem.songId) ?: normalizeSongId(getSmpSongId(cleanSongUri))
        if (resolvedSongId != null && (previousSongId != resolvedSongId || existingItem == null)) {
            migrateStateToSongId(
                playlistName = playlistName,
                uri = cleanSongUri,
                previousSongId = previousSongId,
                nextSongId = resolvedSongId
            )
        }
        if (changed) {
            debugPersistLog {
                "repo.assignSong playlist=$playlistName size=${list.size} uri=$cleanSongUri songId=${resolvedSongId ?: "null"}"
            }
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
        val notPlayed = all.filterNot { isSongPlayed(playlistName, it) }
        val alreadyPlayed = all.filter { isSongPlayed(playlistName, it) }
        return notPlayed + alreadyPlayed
    }

    /** Vue brute telle qu’elle est stockée, pour la sauvegarde. */
    fun getAllSongsRaw(playlistName: String): List<String> {
        return playlists[playlistName]?.toList() ?: emptyList()
    }

    fun getItemsFor(playlistName: String): List<PlaylistItem> {
        return getSongsFor(playlistName).map { uri ->
            getPlaylistItem(playlistName, uri) ?: PlaylistItem(uri = uri)
        }
    }

    fun getAllItemsRaw(playlistName: String): List<PlaylistItem> {
        return getAllSongsRaw(playlistName).map { uri ->
            getPlaylistItem(playlistName, uri) ?: PlaylistItem(uri = uri)
        }
    }

    fun getPlaylistItem(playlistName: String, uri: String): PlaylistItem? {
        val cleanUri = normalizeUriKey(uri) ?: return null
        val list = playlists[playlistName] ?: return null
        if (!list.contains(cleanUri)) return null
        return playlistItems[playlistName]?.get(cleanUri) ?: PlaylistItem(uri = cleanUri)
    }

    /** Réordonne une playlist (drag & drop). */
    fun updatePlayListOrder(playlistName: String, newOrder: List<String>) {
        val current = playlists[playlistName] ?: return
        current.clear()
        current.addAll(newOrder)
        bump()
    }

    fun normalizeSmpItemsForPlaylist(playlistName: String): Boolean {
        val list = playlists[playlistName] ?: return false
        val items = playlistItems[playlistName] ?: return false
        var changed = false

        val snapshot = list.toList()
        snapshot.forEach { uri ->
            val cleanUri = normalizeUriKey(uri) ?: return@forEach
            val songId = normalizeSongId(items[cleanUri]?.songId) ?: return@forEach
            val normalizedUri = normalizeSmpPlaylistUri(cleanUri, songId)
            if (normalizedUri == cleanUri) return@forEach

            val index = list.indexOf(cleanUri)
            if (index >= 0) {
                list[index] = normalizedUri
                changed = true
            }

            val previousItem = items.remove(cleanUri)
            if (previousItem != null) {
                val existingNormalizedItem = items[normalizedUri]
                val nextItem = (existingNormalizedItem ?: previousItem).copy(
                    uri = normalizedUri,
                    songId = songId
                )
                if (existingNormalizedItem != nextItem) {
                    items[normalizedUri] = nextItem
                    changed = true
                }
            }

            migratePlaylistUriState(
                playlistName = playlistName,
                oldUri = cleanUri,
                newUri = normalizedUri
            )
        }

        if (changed) {
            bump()
        }
        return changed
    }

    // -------------------------------------------------
    // ETAT "JOUÉ"
    // -------------------------------------------------

    fun isSongPlayed(playlistName: String, uri: String): Boolean {
        val cleanUri = normalizeUriKey(uri) ?: return false
        if (playedSongs[playlistName]?.contains(cleanUri) == true) {
            return true
        }
        val songId = resolveSongIdForState(playlistName, cleanUri)
        if (
            songId != null &&
            playlistSongIdOccurrenceCount(playlistName, songId) == 1 &&
            playedSongIds[playlistName]?.contains(songId) == true
        ) {
            return true
        }
        return false
    }

    fun markSongPlayed(playlistName: String, uri: String) {
        val cleanUri = normalizeUriKey(uri) ?: return
        val songId = resolveSongIdForState(playlistName, cleanUri)
        // ✅ GARDE-FOU : si quelqu’un essaye de marquer "joué" trop tôt sur le titre armé
        val isCurrentTrack = playlistName == nowPlayingPlaylist &&
            (
                cleanUri == nowPlayingUri ||
                    (songId != null && songId == nowPlayingSongId)
                )
        if (isCurrentTrack) {
            val armedAt = nowPlayingArmedAtElapsedMs
            if (armedAt != null) {
                val elapsed = SystemClock.elapsedRealtime() - armedAt
                if (elapsed in 0 until PLAYED_DELAY_MS) {
                    // on ignore : c’est exactement ton bug "ça descend direct"
                    return
                }
            }
        }

        if (markSongPlayedState(playlistName, cleanUri, songId)) {
            bump()
        }
    }

    /** Pour la sauvegarde : liste brute des titres joués. */
    fun getPlayedRaw(playlistName: String): List<String> {
        return getAllSongsRaw(playlistName).filter { uri -> isSongPlayed(playlistName, uri) }
    }

    /** Remet tous les titres de la playlist en "non joués". */
    fun resetPlayedFor(playlistName: String) {
        playedSongs.remove(playlistName)
        playedSongIds.remove(playlistName)
        bump()
    }

    /** Remet tout le monde en "non joué". */
    fun resetAllPlayed() {
        playedSongs.clear()
        playedSongIds.clear()
        bump()
    }

    // -------------------------------------------------
    // TITRES "À REVOIR"
    // -------------------------------------------------

    fun isSongToReview(playlistName: String, uri: String): Boolean {
        val cleanUri = normalizeUriKey(uri) ?: return false
        val songId = resolveSongIdForState(playlistName, cleanUri)
        if (songId != null && reviewSongIds[playlistName]?.contains(songId) == true) {
            return true
        }
        return reviewSongs[playlistName]?.contains(cleanUri) == true
    }

    fun setSongToReview(playlistName: String, uri: String, toReview: Boolean) {
        val cleanUri = normalizeUriKey(uri) ?: return
        val songId = resolveSongIdForState(playlistName, cleanUri)
        val changed = setSongReviewState(
            playlistName = playlistName,
            uri = cleanUri,
            songId = songId,
            toReview = toReview
        )
        if (changed) bump()
    }

    fun clearReviewForPlaylist(playlistName: String) {
        reviewSongs.remove(playlistName)
        reviewSongIds.remove(playlistName)
        bump()
    }

    // -------------------------------------------------
    // TITRES PERSONNALISÉS (RENOMMAGE)
    // -------------------------------------------------

    fun clearCustomTitleEverywhere(uri: String) {
        val cleanUri = normalizeUriKey(uri) ?: return
        val songIds = getPlaylists()
            .mapNotNull { playlistName -> resolveSongIdForState(playlistName, cleanUri) }
            .toSet()
        var changed = false
        customTitles.forEach { (_, map) ->
            if (map.remove(cleanUri) != null) {
                changed = true
            }
        }
        if (songIds.isNotEmpty()) {
            customTitlesBySongId.forEach { (_, map) ->
                songIds.forEach { songId ->
                    if (map.remove(songId) != null) {
                        changed = true
                    }
                }
            }
        }
        if (changed) {
            bump()
        }
    }

    fun getCustomTitle(playlistName: String, uri: String): String? {
        val cleanUri = normalizeUriKey(uri) ?: return null
        val songId = resolveSongIdForState(playlistName, cleanUri)
        if (songId != null) {
            customTitlesBySongId[playlistName]?.get(songId)?.let { return it }
        }
        return customTitles[playlistName]?.get(cleanUri)
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

    fun getAnyCustomTitlesSnapshot(): Map<String, String> {
        return runCatching {
            val out = linkedMapOf<String, String>()
            val pls = getPlaylists()
            for (pl in pls) {
                getAllSongsRaw(pl).forEach { uri ->
                    val title = getCustomTitle(pl, uri) ?: return@forEach
                    val cleanUri = uri.trim()
                    val cleanTitle = title.trim()
                    if (cleanUri.isNotEmpty() && cleanTitle.isNotEmpty() && !out.containsKey(cleanUri)) {
                        out[cleanUri] = cleanTitle
                    }
                }
            }
            out.toMap()
        }.getOrDefault(emptyMap())
    }

    fun renameSongInPlaylist(playlistName: String, uri: String, newTitle: String) {
        val cleanUri = normalizeUriKey(uri) ?: return
        val clean = newTitle.trim()
        val songId = resolveSongIdForState(playlistName, cleanUri)
        var changed = false
        if (songId != null) {
            val map = customTitlesBySongId.getOrPut(playlistName) { mutableMapOf() }
            changed = if (clean.isEmpty()) {
                map.remove(songId) != null
            } else {
                map.put(songId, clean) != clean
            }
            if (customTitles[playlistName]?.remove(cleanUri) != null) {
                changed = true
            }
        } else {
            val map = customTitles.getOrPut(playlistName) { mutableMapOf() }
            changed = if (clean.isEmpty()) {
                map.remove(cleanUri) != null
            } else {
                map.put(cleanUri, clean) != clean
            }
        }
        runCatching {
            Log.i(
                DEMO_TITLES_TAG,
                "repo:rename playlist=$playlistName uri=$cleanUri songId=${songId ?: "null"} newTitle=$clean changed=$changed storedTitle=${getCustomTitle(playlistName, cleanUri) ?: "null"}"
            )
        }
        if (changed) {
            bump()
        }
    }

    fun removeSongFromPlaylist(playlistName: String, uri: String) {
        val cleanUri = normalizeUriKey(uri) ?: return
        val list = playlists[playlistName] ?: return
        val removedFromList = list.remove(cleanUri)
        val removedItem = playlistItems[playlistName]?.remove(cleanUri)
        val removedSongId = normalizeSongId(removedItem?.songId) ?: normalizeSongId(getSmpSongId(cleanUri))
        customTitles[playlistName]?.remove(cleanUri)
        reviewSongs[playlistName]?.remove(cleanUri)
        playedSongs[playlistName]?.remove(cleanUri)
        if (removedSongId != null && !playlistContainsSongId(playlistName, removedSongId)) {
            customTitlesBySongId[playlistName]?.remove(removedSongId)
            reviewSongIds[playlistName]?.remove(removedSongId)
            playedSongIds[playlistName]?.remove(removedSongId)
        }
        if (!removedFromList && removedItem == null) {
            return
        }
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
        playlistItems.clear()
        playedSongs.clear()
        playedSongIds.clear()
        reviewSongs.clear()
        reviewSongIds.clear()
        customTitles.clear()
        customTitlesBySongId.clear()
        playlistColors.clear()
        nowPlayingSongId = null
        bump()
    }

    fun moveSongToEnd(playlistName: String, uri: String) {
        val cleanUri = normalizeUriKey(uri) ?: return
        val songId = resolveSongIdForState(playlistName, cleanUri)
        // ✅ GARDE-FOU : idem, on bloque le "descend direct" si ça arrive trop tôt
        val isCurrentTrack = playlistName == nowPlayingPlaylist &&
            (
                cleanUri == nowPlayingUri ||
                    (songId != null && songId == nowPlayingSongId)
                )
        if (isCurrentTrack) {
            val armedAt = nowPlayingArmedAtElapsedMs
            if (armedAt != null) {
                val elapsed = SystemClock.elapsedRealtime() - armedAt
                if (elapsed in 0 until PLAYED_DELAY_MS) return
            }
        }

        val list = playlists[playlistName] ?: return
        val idx = list.indexOf(cleanUri)
        if (idx == -1) return
        list.removeAt(idx)
        list.add(cleanUri)
        bump()
    }

    fun createIfNotExists(name: String) = addPlaylist(name)

    fun addSong(name: String, uri: String) = assignSongToPlaylist(name, uri)

    fun importMarkPlayed(playlistName: String, uri: String) = markSongPlayed(playlistName, uri)

    fun exportRaw(): Map<String, Pair<List<String>, Set<String>>> {
        return playlists.mapValues { (plName, list) ->
            val played = getPlayedRaw(plName).toSet()
            list.toList() to played
        }
    }

    fun renamePlaylist(oldName: String, newName: String): Boolean {
        val clean = newName.trim()
        if (clean.isEmpty()) return false
        if (!playlists.containsKey(oldName)) return false
        if (playlists.containsKey(clean)) return false

        val songs = playlists.remove(oldName) ?: mutableListOf()
        playlists[clean] = songs
        playlistItems[clean] = playlistItems.remove(oldName) ?: mutableMapOf()
        playedSongs[clean] = playedSongs.remove(oldName) ?: mutableSetOf()
        playedSongIds[clean] = playedSongIds.remove(oldName) ?: mutableSetOf()
        reviewSongs[clean] = reviewSongs.remove(oldName) ?: mutableSetOf()
        reviewSongIds[clean] = reviewSongIds.remove(oldName) ?: mutableSetOf()
        customTitles[clean] = customTitles.remove(oldName) ?: mutableMapOf()
        customTitlesBySongId[clean] = customTitlesBySongId.remove(oldName) ?: mutableMapOf()
        playlistColors[clean] = playlistColors.remove(oldName) ?: 0xFFE86FFF

        bump()
        return true
    }

    fun deletePlaylist(name: String) {
        if (!playlists.containsKey(name)) return
        playlists.remove(name)
        playlistItems.remove(name)
        playedSongs.remove(name)
        playedSongIds.remove(name)
        reviewSongs.remove(name)
        reviewSongIds.remove(name)
        customTitles.remove(name)
        customTitlesBySongId.remove(name)
        playlistColors.remove(name)
        bump()
    }

    fun replaceSongUriEverywhere(oldUri: String, newUri: String) {
        val oldCleanUri = normalizeUriKey(oldUri) ?: return
        val newCleanUri = normalizeUriKey(newUri) ?: return
        if (oldCleanUri == newCleanUri) return

        playlists.forEach { (_, list) ->
            for (i in list.indices) if (list[i] == oldCleanUri) list[i] = newCleanUri
        }
        playlistItems.forEach { (_, items) ->
            val previous = items.remove(oldCleanUri) ?: return@forEach
            items[newCleanUri] = previous.copy(uri = newCleanUri)
        }
        playedSongs.forEach { (_, set) -> if (set.remove(oldCleanUri)) set.add(newCleanUri) }
        reviewSongs.forEach { (_, set) -> if (set.remove(oldCleanUri)) set.add(newCleanUri) }
        customTitles.forEach { (_, map) ->
            val t = map.remove(oldCleanUri)
            if (t != null) map[newCleanUri] = t
        }
        if (nowPlayingUri == oldCleanUri) {
            nowPlayingUri = newCleanUri
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

    private fun debugPersistLog(message: () -> String) {
        runCatching { Log.d(PERSIST_LOG_TAG, message()) }
    }

    private fun normalizeUriKey(uri: String?): String? =
        uri?.trim()?.takeIf { it.isNotEmpty() }

    private fun normalizeSongId(songId: String?): String? =
        songId?.trim()?.takeIf { it.isNotEmpty() }

    private fun normalizeSmpPlaylistUri(uri: String, songId: String): String {
        val normalizedSongId = normalizeSongId(songId) ?: return uri
        val existingSongId = normalizeSongId(getSmpSongId(uri))
        if (existingSongId == normalizedSongId) return uri
        if (!looksLikeSmpArchiveUri(uri)) return uri
        return buildSmpItem(normalizedSongId)
    }

    private fun looksLikeSmpArchiveUri(uri: String): Boolean {
        val decoded = runCatching { Uri.decode(uri).lowercase() }.getOrElse { uri.lowercase() }
        return decoded.endsWith(".smp") || decoded.endsWith(".smp.zip")
    }

    private fun migratePlaylistUriState(
        playlistName: String,
        oldUri: String,
        newUri: String
    ) {
        if (oldUri == newUri) return
        playedSongs[playlistName]?.let { set ->
            if (set.remove(oldUri)) {
                set.add(newUri)
            }
        }
        reviewSongs[playlistName]?.let { set ->
            if (set.remove(oldUri)) {
                set.add(newUri)
            }
        }
        customTitles[playlistName]?.let { map ->
            val title = map.remove(oldUri) ?: return@let
            map.putIfAbsent(newUri, title)
        }
        if (playlistName == nowPlayingPlaylist && nowPlayingUri == oldUri) {
            nowPlayingUri = newUri
        }
    }

    private fun resolveSongIdForState(playlistName: String, uri: String): String? {
        return normalizeSongId(
            playlistItems[playlistName]
                ?.get(uri)
                ?.songId
        ) ?: normalizeSongId(getSmpSongId(uri))
    }

    private fun markSongPlayedState(
        playlistName: String,
        uri: String,
        songId: String?
    ): Boolean {
        val legacySet = playedSongs.getOrPut(playlistName) { mutableSetOf() }
        val added = legacySet.add(uri)
        val removedSongId = songId?.let { playedSongIds[playlistName]?.remove(it) == true } == true
        return added || removedSongId
    }

    private fun setSongReviewState(
        playlistName: String,
        uri: String,
        songId: String?,
        toReview: Boolean
    ): Boolean {
        if (songId != null) {
            val songSet = reviewSongIds.getOrPut(playlistName) { mutableSetOf() }
            val changed = if (toReview) {
                songSet.add(songId)
            } else {
                songSet.remove(songId)
            }
            val removedLegacy = reviewSongs[playlistName]?.remove(uri) == true
            return changed || removedLegacy
        }

        val legacySet = reviewSongs.getOrPut(playlistName) { mutableSetOf() }
        return if (toReview) legacySet.add(uri) else legacySet.remove(uri)
    }

    private fun migrateStateToSongId(
        playlistName: String,
        uri: String,
        previousSongId: String?,
        nextSongId: String
    ) {
        if (previousSongId != null && previousSongId != nextSongId) {
            playedSongIds[playlistName]?.remove(previousSongId)
            if (reviewSongIds[playlistName]?.remove(previousSongId) == true) {
                reviewSongIds.getOrPut(playlistName) { mutableSetOf() }.add(nextSongId)
            }
            customTitlesBySongId[playlistName]
                ?.remove(previousSongId)
                ?.let { title ->
                    customTitlesBySongId.getOrPut(playlistName) { mutableMapOf() }
                        .putIfAbsent(nextSongId, title)
                }
            if (playlistName == nowPlayingPlaylist && nowPlayingSongId == previousSongId) {
                nowPlayingSongId = nextSongId
            }
        }

        if (playedSongs[playlistName]?.contains(uri) == true && previousSongId != nextSongId) {
            playedSongs[playlistName]?.remove(uri)
            playedSongs.getOrPut(playlistName) { mutableSetOf() }.add(uri)
        }
        if (reviewSongs[playlistName]?.remove(uri) == true) {
            reviewSongIds.getOrPut(playlistName) { mutableSetOf() }.add(nextSongId)
        }
        customTitles[playlistName]?.remove(uri)?.let { title ->
            customTitlesBySongId.getOrPut(playlistName) { mutableMapOf() }
                .putIfAbsent(nextSongId, title)
        }
        if (playlistName == nowPlayingPlaylist && nowPlayingUri == uri) {
            nowPlayingSongId = nextSongId
        }
    }

    private fun playlistContainsSongId(playlistName: String, songId: String): Boolean {
        return playlists[playlistName]?.any { item ->
            resolveSongIdForState(playlistName, item) == songId
        } ?: false
    }

    private fun playlistSongIdOccurrenceCount(playlistName: String, songId: String): Int {
        return playlists[playlistName]?.count { item -> resolveSongIdForState(playlistName, item) == songId } ?: 0
    }
}
