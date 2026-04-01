package com.patrick.lrcreader.core.config

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object NotesConfigStore {

    private const val TAG = "NotesConfigStore"
    const val GLOBAL_SCOPE_KEY = "__global__"

    private val mutex = Mutex()
    private var cachedState: NotesConfigState? = null

    fun ensureInitialized(context: Context): Boolean {
        return NotesConfigAtomicIo.ensureInitialized(context)
    }

    fun getAll(context: Context): List<NotesScopedEntry> = runBlocking {
        mutex.withLock {
            readStateLocked(context).allScopedEntries()
        }
    }

    fun getById(context: Context, id: Long): NotesScopedEntry? = runBlocking {
        mutex.withLock {
            readStateLocked(context).findById(id)
        }
    }

    fun getBySongId(context: Context, songId: String): List<NotesScopedEntry> = runBlocking {
        mutex.withLock {
            val cleanSongKey = SongIdKeyResolver.songScopedKey(songId) ?: return@withLock emptyList()
            val state = readStateLocked(context)

            scopedEntriesForKey(state, cleanSongKey).takeIf { it.isNotEmpty() }
                ?: SongIdKeyResolver.resolveLegacyRelativePathBySongId(context, songId)
                    ?.let { legacyScope -> scopedEntriesForKey(state, legacyScope) }
                ?: emptyList()
        }
    }

    fun upsert(context: Context, scopeKey: String, entry: NotesConfigEntry): Boolean = runBlocking {
        mutex.withLock {
            val writeScopeKeys = resolveWriteScopeKeys(context, scopeKey)
            if (writeScopeKeys.isEmpty()) {
                Log.w(TAG, "upsert: invalid scopeKey=$scopeKey")
                return@withLock false
            }

            val current = readStateLocked(context)
            val next = current.withMirroredUpsert(scopeKeys = writeScopeKeys, entry = entry)
            writeStateLocked(context, next)
        }
    }

    fun delete(context: Context, id: Long): Boolean = runBlocking {
        mutex.withLock {
            val current = readStateLocked(context)
            if (current.findById(id) == null) return@withLock true

            val next = current.withoutId(id)
            writeStateLocked(context, next)
        }
    }

    fun normalizeScopeKey(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw == GLOBAL_SCOPE_KEY) return GLOBAL_SCOPE_KEY
        return sanitizeRelativePath(raw)
    }

    fun isValidScopeKey(raw: String?): Boolean {
        if (raw == null) return false
        return normalizeScopeKey(raw) != null
    }

    private fun writeStateLocked(context: Context, nextState: NotesConfigState): Boolean {
        val raw = nextState.toJson().toString(2)
        val saved = NotesConfigAtomicIo.writeRawAtomic(context, raw)
        if (saved) {
            cachedState = nextState
            return true
        }

        Log.e(TAG, "writeStateLocked: write failed")
        return false
    }

    private fun readStateLocked(context: Context): NotesConfigState {
        cachedState?.let { return it }

        val state = try {
            if (!NotesConfigAtomicIo.ensureInitialized(context)) {
                NotesConfigState.empty()
            } else {
                val raw = NotesConfigAtomicIo.readRaw(context)
                if (raw.isNullOrBlank()) {
                    NotesConfigState.empty()
                } else {
                    NotesConfigState.fromJson(raw)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "readStateLocked: parse failed", t)
            NotesConfigState.empty()
        }

        cachedState = state
        return state
    }

    private fun sanitizeRelativePath(raw: String?): String? {
        val normalized = raw
            ?.replace('\\', '/')
            ?.trim()
            ?.trim('/')
            ?: return null

        if (normalized.isBlank()) return null
        if (normalized.startsWith("../") || normalized == "..") return null
        if (normalized.contains("://")) return null
        if (normalized.startsWith('/')) return null
        if (normalized.matches(Regex("^[A-Za-z]:.*"))) return null

        val segments = normalized.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null

        return normalized
    }

    private fun scopedEntriesForKey(
        state: NotesConfigState,
        scopeKey: String
    ): List<NotesScopedEntry> {
        return state.notesByScope[scopeKey]
            .orEmpty()
            .map { entry -> NotesScopedEntry(scopeKey = scopeKey, note = entry) }
    }

    private fun resolveWriteScopeKeys(context: Context, rawScopeKey: String): List<String> {
        val normalizedScope = normalizeScopeKey(rawScopeKey) ?: return emptyList()
        if (normalizedScope == GLOBAL_SCOPE_KEY) {
            return listOf(GLOBAL_SCOPE_KEY)
        }

        val scopeKeys = linkedSetOf<String>()
        val songId = extractSongIdFromScopeKey(normalizedScope)
            ?: resolveSongIdFromLegacyScope(context, normalizedScope)
        SongIdKeyResolver.songScopedKey(songId)?.let(scopeKeys::add)
        scopeKeys.add(normalizedScope)

        songId?.let { resolvedSongId ->
            SongIdKeyResolver.resolveLegacyRelativePathBySongId(context, resolvedSongId)
                ?.let(scopeKeys::add)
        }

        return scopeKeys.toList()
    }

    private fun extractSongIdFromScopeKey(scopeKey: String): String? {
        val prefix = "songId::"
        return if (scopeKey.startsWith(prefix)) {
            SongIdKeyResolver.normalizeSongId(scopeKey.removePrefix(prefix))
        } else {
            null
        }
    }

    private fun resolveSongIdFromLegacyScope(context: Context, legacyScope: String): String? {
        val tracksDir = File(context.filesDir, "tracks")
        return tracksDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { songDir -> songDir.isDirectory && File(songDir, "config.json").isFile }
            .mapNotNull { songDir ->
                val songId = SongIdKeyResolver.normalizeSongId(songDir.name) ?: return@mapNotNull null
                val legacyPath = SongIdKeyResolver.resolveLegacyRelativePathBySongId(context, songId)
                songId.takeIf { legacyPath == legacyScope }
            }
            .firstOrNull()
    }
}
