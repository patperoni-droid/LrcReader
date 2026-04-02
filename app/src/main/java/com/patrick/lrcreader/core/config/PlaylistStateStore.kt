package com.patrick.lrcreader.core.config

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.patrick.lrcreader.core.PlaylistRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object PlaylistStateStore {

    private const val TAG = "PlaylistStateStore"

    private val mutex = Mutex()
    private var cachedState: PlaylistState? = null
    private var diskReadCount: Long = 0
    private var diskReadTotalMs: Long = 0

    fun ensureInitialized(context: Context): Boolean {
        return PlaylistStateAtomicIo.ensureInitialized(context)
    }

    fun loadManualOrder(context: Context, playlistName: String, currentOrderUris: List<String>): List<String>? {
        return withLockBlocking {
            val conversion = buildConversion(context, playlistName, currentOrderUris) ?: return@withLockBlocking null
            val entry = readStateLocked(context).playlists[playlistName] ?: return@withLockBlocking null
            if (entry.manualOrder.isEmpty()) return@withLockBlocking null
            reorderFromStoredOrder(
                currentOrderUris = currentOrderUris,
                desiredStoredOrder = entry.manualOrder,
                conversion = conversion
            )
        }
    }

    fun loadOriginalOrder(context: Context, playlistName: String, currentOrderUris: List<String>): List<String>? {
        return withLockBlocking {
            val conversion = buildConversion(context, playlistName, currentOrderUris) ?: return@withLockBlocking null
            val entry = readStateLocked(context).playlists[playlistName] ?: return@withLockBlocking null
            if (entry.originalOrder.isEmpty()) return@withLockBlocking null
            reorderFromStoredOrder(
                currentOrderUris = currentOrderUris,
                desiredStoredOrder = entry.originalOrder,
                conversion = conversion
            )
        }
    }

    fun saveManualOrder(context: Context, playlistName: String, manualOrderUris: List<String>): Boolean {
        return withLockBlocking {
            val conversion = buildConversion(context, playlistName, manualOrderUris) ?: return@withLockBlocking false
            val state = readStateLocked(context)
            val previous = state.playlists[playlistName] ?: PlaylistStateEntry()
            val next = previous.copy(
                manualOrder = manualOrderUris.mapNotNull { conversion.storedKeyByUri[it] },
                updatedAt = System.currentTimeMillis()
            )
            writePlaylistEntryLocked(context, playlistName, next)
        }
    }

    fun saveOriginalOrder(context: Context, playlistName: String, originalOrderUris: List<String>): Boolean {
        return withLockBlocking {
            val conversion = buildConversion(context, playlistName, originalOrderUris) ?: return@withLockBlocking false
            val state = readStateLocked(context)
            val previous = state.playlists[playlistName] ?: PlaylistStateEntry()
            val next = previous.copy(
                originalOrder = originalOrderUris.mapNotNull { conversion.storedKeyByUri[it] },
                updatedAt = System.currentTimeMillis()
            )
            writePlaylistEntryLocked(context, playlistName, next)
        }
    }

    fun saveOriginalOrderIfMissing(context: Context, playlistName: String, originalOrderUris: List<String>): Boolean {
        return withLockBlocking {
            val state = readStateLocked(context)
            val previous = state.playlists[playlistName]
            if (previous != null && previous.originalOrder.isNotEmpty()) {
                return@withLockBlocking true
            }

            val conversion = buildConversion(context, playlistName, originalOrderUris) ?: return@withLockBlocking false
            val next = (previous ?: PlaylistStateEntry()).copy(
                originalOrder = originalOrderUris.mapNotNull { conversion.storedKeyByUri[it] },
                updatedAt = System.currentTimeMillis()
            )
            writePlaylistEntryLocked(context, playlistName, next)
        }
    }

    private fun writePlaylistEntryLocked(
        context: Context,
        playlistName: String,
        entry: PlaylistStateEntry
    ): Boolean {
        val current = readStateLocked(context)
        val nextMap = current.playlists.toMutableMap()
        if (entry.isEmpty()) {
            nextMap.remove(playlistName)
        } else {
            nextMap[playlistName] = entry
        }

        val next = current.copy(
            schemaVersion = PlaylistState.SCHEMA_VERSION,
            playlists = nextMap
        )

        val raw = next.toJson().toString(2)
        val saved = PlaylistStateAtomicIo.writeRawAtomic(context, raw)
        if (saved) {
            cachedState = next
            return true
        }

        Log.e(TAG, "writePlaylistEntryLocked: write failed playlist=$playlistName")
        return false
    }

    private fun readStateLocked(context: Context): PlaylistState {
        cachedState?.let { return it }

        val loadStart = SystemClock.elapsedRealtime()
        Log.d("BOOTSTEP", "PlaylistStateStore.loadFromDisk:start")
        val state = try {
            if (!PlaylistStateAtomicIo.ensureInitialized(context)) {
                PlaylistState.empty()
            } else {
                val raw = PlaylistStateAtomicIo.readRaw(context)
                if (raw.isNullOrBlank()) {
                    PlaylistState.empty()
                } else {
                    PlaylistState.fromJson(raw)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "readStateLocked: parse failed", t)
            PlaylistState.empty()
        }
        val elapsed = SystemClock.elapsedRealtime() - loadStart
        diskReadCount += 1
        diskReadTotalMs += elapsed
        Log.d(
            "BOOTSTEP",
            "PlaylistStateStore.loadFromDisk:end ms=$elapsed totalCalls=$diskReadCount totalMs=$diskReadTotalMs playlists=${state.playlists.size}"
        )

        cachedState = state
        return state
    }

    private data class Conversion(
        val storedKeyByUri: Map<String, String>,
        val uriByStoredKey: Map<String, String>
    )

    private fun buildConversion(context: Context, playlistName: String, uris: List<String>): Conversion? {
        if (uris.isEmpty()) return null

        val storedKeyByUri = linkedMapOf<String, String>()
        val uriByStoredKey = linkedMapOf<String, String>()

        for (uri in uris) {
            val storedKey = resolveStoredKey(context, playlistName, uri) ?: return null
            val legacyKey = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uri)

            storedKeyByUri[uri] = storedKey
            if (!uriByStoredKey.containsKey(storedKey)) {
                uriByStoredKey[storedKey] = uri
            }
            if (!legacyKey.isNullOrBlank() && !uriByStoredKey.containsKey(legacyKey)) {
                uriByStoredKey[legacyKey] = uri
            }
        }

        return Conversion(
            storedKeyByUri = storedKeyByUri,
            uriByStoredKey = uriByStoredKey
        )
    }

    private fun resolveStoredKey(context: Context, playlistName: String, uri: String): String? {
        val songId = PlaylistRepository.getPlaylistItem(playlistName, uri)?.songId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: SongIdKeyResolver.resolveSongIdFromUri(context, uri)
        SongIdKeyResolver.songScopedKey(songId)?.let { return it }
        return TrackSettingsPathResolver.resolveRelativeTrackPath(context, uri)
    }

    private fun reorderFromStoredOrder(
        currentOrderUris: List<String>,
        desiredStoredOrder: List<String>,
        conversion: Conversion
    ): List<String> {
        val usedUris = linkedSetOf<String>()
        val reordered = mutableListOf<String>()

        desiredStoredOrder.forEach { storedKey ->
            val uri = conversion.uriByStoredKey[storedKey] ?: return@forEach
            if (usedUris.add(uri)) {
                reordered.add(uri)
            }
        }

        currentOrderUris.forEach { uri ->
            if (usedUris.add(uri)) {
                reordered.add(uri)
            }
        }

        return reordered
    }

    private fun <T> withLockBlocking(block: () -> T): T {
        return runBlocking {
            mutex.withLock {
                block()
            }
        }
    }
}
