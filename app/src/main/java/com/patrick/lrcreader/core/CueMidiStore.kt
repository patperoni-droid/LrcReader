package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.patrick.lrcreader.core.config.MidiCuesConfigStore
import com.patrick.lrcreader.core.config.SongIdKeyResolver
import com.patrick.lrcreader.core.config.TrackSettingsPathResolver

/**
 * Store en mémoire des Cues MIDI avec migration douce:
 * - portable: SPL_Music/Config/midi_cues.json (clé relative SPL)
 * - legacy: storage existant CueMidiStorePersistence
 */
object CueMidiStore {

    private const val TAG = "CueMidiStore"

    // relPath SPL -> cues
    private val portableCuesByTrack: MutableMap<String, MutableList<CueMidi>> = mutableMapOf()
    // legacy key (URI normalisée) -> cues
    private val legacyCuesByTrack: MutableMap<String, MutableList<CueMidi>> = mutableMapOf()
    // cache résolution URI -> relPath (ou null si non résolu)
    private val relativePathByRawUriCache: MutableMap<String, String?> = mutableMapOf()

    private var appContext: Context? = null

    private data class TrackKeys(
        val legacyKey: String,
        val relativePath: String?,
        val songScopedKey: String?
    )

    private data class SongReadKeys(
        val songScopedKey: String,
        val legacyKey: String?,
        val relativePath: String?
    )

    fun init(context: Context) {
        appContext = context.applicationContext
        portableCuesByTrack.clear()
        legacyCuesByTrack.clear()
        relativePathByRawUriCache.clear()

        val ctx = appContext ?: return

        runCatching { MidiCuesConfigStore.ensureInitialized(ctx) }

        val portableLoaded = runCatching {
            MidiCuesConfigStore.loadAll(ctx)
        }.getOrElse {
            Log.w(TAG, "init: portable load failed, fallback legacy only", it)
            mutableMapOf()
        }
        portableCuesByTrack.putAll(portableLoaded)

        val legacyLoaded = CueMidiStorePersistence.load(ctx)
        legacyLoaded.forEach { (rawKey, cues) ->
            val normalized = normalizeLegacyKey(rawKey) ?: return@forEach
            legacyCuesByTrack[normalized] = cues.toMutableList()
        }
    }

    private fun persistLegacyOnly() {
        val ctx = appContext ?: return
        CueMidiStorePersistence.save(ctx, legacyCuesByTrack)
    }

    private fun persistPortableAndLegacy() {
        val ctx = appContext ?: return

        val portableOk = runCatching {
            MidiCuesConfigStore.saveAll(ctx, portableCuesByTrack)
        }.getOrDefault(false)
        if (!portableOk) {
            Log.w(TAG, "persistPortableAndLegacy: portable write skipped/failed")
        }

        CueMidiStorePersistence.save(ctx, legacyCuesByTrack)
    }

    private fun normalizeLegacyKey(trackUri: String?): String? {
        val raw = trackUri?.trim().orEmpty()
        if (raw.isBlank()) return null
        return runCatching { Uri.parse(raw).normalizeScheme().toString() }
            .getOrElse { raw }
    }

    private fun resolveRelativePathCached(legacyKey: String): String? {
        if (relativePathByRawUriCache.containsKey(legacyKey)) {
            return relativePathByRawUriCache[legacyKey]
        }

        val ctx = appContext
        val resolved = if (ctx != null) {
            runCatching {
                TrackSettingsPathResolver.resolveRelativeTrackPath(ctx, legacyKey)
            }.getOrNull()
        } else {
            null
        }

        relativePathByRawUriCache[legacyKey] = resolved
        return resolved
    }

    private fun resolveTrackKeys(trackUri: String?): TrackKeys? {
        val legacyKey = normalizeLegacyKey(trackUri) ?: return null
        val songScopedKey = appContext?.let { context ->
            SongIdKeyResolver.songScopedKey(
                SongIdKeyResolver.resolveSongIdFromUri(context, trackUri)
            )
        }
        return TrackKeys(
            legacyKey = legacyKey,
            relativePath = resolveRelativePathCached(legacyKey),
            songScopedKey = songScopedKey
        )
    }

    private fun upsertInMap(
        map: MutableMap<String, MutableList<CueMidi>>,
        key: String,
        cue: CueMidi
    ) {
        val list = map.getOrPut(key) { mutableListOf() }
        val idx = list.indexOfFirst { it.lineIndex == cue.lineIndex }
        if (idx >= 0) {
            list[idx] = cue
        } else {
            list += cue
        }
    }

    private fun deleteFromMap(
        map: MutableMap<String, MutableList<CueMidi>>,
        key: String,
        lineIndex: Int
    ): Boolean {
        val list = map[key] ?: return false
        val changed = list.removeAll { it.lineIndex == lineIndex }
        if (list.isEmpty()) map.remove(key)
        return changed
    }

    private fun shiftInMap(
        map: MutableMap<String, MutableList<CueMidi>>,
        key: String,
        deletedLineIndex: Int
    ): Boolean {
        val list = map[key] ?: return false
        val shifted = list.mapNotNull { cue ->
            when {
                cue.lineIndex == deletedLineIndex -> null
                cue.lineIndex > deletedLineIndex -> cue.copy(lineIndex = cue.lineIndex - 1)
                else -> cue
            }
        }.toMutableList()

        val changed = shifted != list
        if (!changed) return false

        if (shifted.isEmpty()) {
            map.remove(key)
        } else {
            map[key] = shifted
        }
        return true
    }

    fun getCuesForTrack(trackUri: String?): List<CueMidi> {
        val keys = resolveTrackKeys(trackUri) ?: return emptyList()
        return readCues(
            songScopedKey = keys.songScopedKey,
            relativePath = keys.relativePath,
            legacyKey = keys.legacyKey
        )
    }

    fun getBySongId(songId: String?): List<CueMidi> {
        val keys = resolveSongReadKeys(songId) ?: return emptyList()
        return readCues(
            songScopedKey = keys.songScopedKey,
            relativePath = keys.relativePath,
            legacyKey = keys.legacyKey
        )
    }

    fun shiftAfterDelete(trackUri: String?, deletedLineIndex: Int) {
        val keys = resolveTrackKeys(trackUri) ?: return
        if (keys.songScopedKey != null) {
            val changed = migrateAndMutateSongScoped(
                keys = keys,
                mutate = { cues -> shiftedCues(cues, deletedLineIndex) }
            )
            if (changed) {
                persistPortableAndLegacy()
            }
            return
        }

        if (keys.relativePath != null) {
            val portableChanged = shiftInMap(portableCuesByTrack, keys.relativePath, deletedLineIndex)
            val legacyChanged = shiftInMap(legacyCuesByTrack, keys.legacyKey, deletedLineIndex)
            if (portableChanged || legacyChanged) {
                persistPortableAndLegacy()
            }
            return
        }

        if (shiftInMap(legacyCuesByTrack, keys.legacyKey, deletedLineIndex)) {
            persistLegacyOnly()
        }
    }

    /**
     * Ajoute ou remplace un Cue pour une ligne donnée d’un morceau.
     * (clé = lineIndex)
     */
    fun upsertCue(trackUri: String?, cue: CueMidi) {
        val keys = resolveTrackKeys(trackUri) ?: return
        if (keys.songScopedKey != null) {
            val changed = migrateAndMutateSongScoped(
                keys = keys,
                mutate = { cues -> upsertedCues(cues, cue) }
            )
            if (changed) {
                persistPortableAndLegacy()
            }
        } else if (keys.relativePath != null) {
            upsertInMap(portableCuesByTrack, keys.relativePath, cue)
            upsertInMap(legacyCuesByTrack, keys.legacyKey, cue)
            persistPortableAndLegacy()
        } else {
            upsertInMap(legacyCuesByTrack, keys.legacyKey, cue)
            persistLegacyOnly()
        }
    }

    /**
     * Supprime le Cue associé à une ligne de paroles.
     */
    fun deleteCue(trackUri: String?, lineIndex: Int) {
        val keys = resolveTrackKeys(trackUri) ?: return
        if (keys.songScopedKey != null) {
            val changed = migrateAndMutateSongScoped(
                keys = keys,
                mutate = { cues -> cues.filterNot { it.lineIndex == lineIndex } }
            )
            if (changed) {
                persistPortableAndLegacy()
            }
            return
        }

        if (keys.relativePath != null) {
            val portableChanged = deleteFromMap(portableCuesByTrack, keys.relativePath, lineIndex)
            val legacyChanged = deleteFromMap(legacyCuesByTrack, keys.legacyKey, lineIndex)
            if (portableChanged || legacyChanged) {
                persistPortableAndLegacy()
            }
            return
        }

        if (deleteFromMap(legacyCuesByTrack, keys.legacyKey, lineIndex)) {
            persistLegacyOnly()
        }
    }

    private fun resolveSongReadKeys(songId: String?): SongReadKeys? {
        val cleanSongScopedKey = SongIdKeyResolver.songScopedKey(songId) ?: return null
        val ctx = appContext
        val runtimeUri = ctx?.let { SongIdKeyResolver.resolveRuntimeTrackUri(it, songId) }
        return SongReadKeys(
            songScopedKey = cleanSongScopedKey,
            legacyKey = normalizeLegacyKey(runtimeUri),
            relativePath = ctx?.let { SongIdKeyResolver.resolveLegacyRelativePathBySongId(it, songId) }
        )
    }

    private fun readCues(
        songScopedKey: String?,
        relativePath: String?,
        legacyKey: String?
    ): List<CueMidi> {
        if (songScopedKey != null) {
            portableCuesByTrack[songScopedKey]?.let { return it.toList() }
            legacyCuesByTrack[songScopedKey]?.let { return it.toList() }
        }

        if (relativePath != null) {
            portableCuesByTrack[relativePath]?.let { return it.toList() }
        }

        if (legacyKey != null) {
            legacyCuesByTrack[legacyKey]?.let { return it.toList() }
        }

        return emptyList()
    }

    private fun migrateAndMutateSongScoped(
        keys: TrackKeys,
        mutate: (List<CueMidi>) -> List<CueMidi>
    ): Boolean {
        val songScopedKey = keys.songScopedKey ?: return false
        val hadSongScopedEntry = portableCuesByTrack.containsKey(songScopedKey)
        val hadRelativePathEntry = keys.relativePath
            ?.takeUnless { it == songScopedKey }
            ?.let { portableCuesByTrack.containsKey(it) }
            ?: false
        val hadLegacyEntry = legacyCuesByTrack.containsKey(keys.legacyKey)
        val currentCues = readCues(
            songScopedKey = songScopedKey,
            relativePath = keys.relativePath,
            legacyKey = keys.legacyKey
        )
        val nextCues = mutate(currentCues)
        if (nextCues == currentCues &&
            hadSongScopedEntry &&
            portableCuesByTrack[songScopedKey] == nextCues &&
            !hadRelativePathEntry &&
            !hadLegacyEntry
        ) {
            return false
        }

        if (nextCues.isEmpty()) {
            portableCuesByTrack.remove(songScopedKey)
        } else {
            portableCuesByTrack[songScopedKey] = nextCues.toMutableList()
        }

        keys.relativePath
            ?.takeUnless { it == songScopedKey }
            ?.let { portableCuesByTrack.remove(it) }
        legacyCuesByTrack.remove(keys.legacyKey)
        return currentCues != nextCues ||
            !hadSongScopedEntry ||
            hadRelativePathEntry ||
            hadLegacyEntry
    }

    private fun upsertedCues(existing: List<CueMidi>, cue: CueMidi): List<CueMidi> {
        val updated = existing.toMutableList()
        val idx = updated.indexOfFirst { it.lineIndex == cue.lineIndex }
        if (idx >= 0) {
            updated[idx] = cue
        } else {
            updated += cue
        }
        return updated
    }

    private fun shiftedCues(existing: List<CueMidi>, deletedLineIndex: Int): List<CueMidi> {
        return existing.mapNotNull { cue ->
            when {
                cue.lineIndex == deletedLineIndex -> null
                cue.lineIndex > deletedLineIndex -> cue.copy(lineIndex = cue.lineIndex - 1)
                else -> cue
            }
        }
    }
}
