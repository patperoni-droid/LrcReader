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
    private const val PERSIST_LOG_TAG = "PLAYLIST_PERSIST"

    private val mutex = Mutex()
    private var cachedState: PlaylistState? = null
    private var diskReadCount: Long = 0
    private var diskReadTotalMs: Long = 0

    data class RestoreResult(
        val success: Boolean,
        val restoredPlaylistCount: Int,
        val internalPlaylistCount: Int,
        val workspacePlaylistCount: Int,
        val workspaceHasPlaylistFiles: Boolean,
        val validated: Boolean
    )

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

    fun restorePlaylistsIntoRepository(context: Context): RestoreResult {
        return withLockBlocking {
            val internalState = readStateLocked(context)
            val workspaceState = WorkspacePlaylistFilesStore.readAll(context)
            val state = mergeWorkspacePlaylists(
                internalState = internalState,
                workspaceState = workspaceState
            )
            Log.d(
                PERSIST_LOG_TAG,
                "restore.begin internal=${internalState.playlists.keys.sorted()} workspace=${workspaceState.playlists.keys.sorted()} merged=${state.playlists.keys.sorted()} counts=${
                    state.playlists.toSortedMap().entries.joinToString { "${it.key}:${it.value.items.size}" }
                }"
            )
            PlaylistRepository.clearAll()
            restoreStateIntoRepository(state)
            cachedState = state
            val workspaceMigrated = if (shouldSyncWorkspaceFiles(state, workspaceState)) {
                WorkspacePlaylistFilesStore.syncFromRepository(context)
            } else {
                true
            }
            Log.d(
                PERSIST_LOG_TAG,
                "restore.end playlists=${PlaylistRepository.getPlaylists()} counts=${
                    PlaylistRepository.getPlaylists().joinToString { "$it:${PlaylistRepository.getAllItemsRaw(it).size}" }
                } workspaceMigrated=$workspaceMigrated"
            )
            val restoredPlaylistCount = PlaylistRepository.getPlaylists().size
            val validated = restoredPlaylistCount > 0 ||
                (
                    !workspaceState.hasPlaylistFiles &&
                        internalState.playlists.isEmpty() &&
                        workspaceState.playlists.isEmpty()
                    )
            Log.d(
                PERSIST_LOG_TAG,
                "restore.validation restored=$restoredPlaylistCount internal=${internalState.playlists.size} workspace=${workspaceState.playlists.size} workspaceHasFiles=${workspaceState.hasPlaylistFiles} validated=$validated"
            )
            RestoreResult(
                success = true,
                restoredPlaylistCount = restoredPlaylistCount,
                internalPlaylistCount = internalState.playlists.size,
                workspacePlaylistCount = workspaceState.playlists.size,
                workspaceHasPlaylistFiles = workspaceState.hasPlaylistFiles,
                validated = validated
            )
        }
    }

    fun savePlaylistsSnapshot(context: Context): Boolean {
        return withLockBlocking {
            val current = readStateLocked(context)
            val workspaceState = WorkspacePlaylistFilesStore.readAll(context)
            val repoPlaylists = PlaylistRepository.getPlaylists()
            val repoIsEmpty = repoPlaylists.isEmpty()
            if (repoIsEmpty && workspaceState.hasPlaylistFiles) {
                Log.e(
                    PERSIST_LOG_TAG,
                    "save.blocked reason=suspect_empty_repo internal=${current.playlists.size} workspace=${workspaceState.playlists.size} workspaceHasFiles=${workspaceState.hasPlaylistFiles}"
                )
                return@withLockBlocking false
            }
            Log.d(
                PERSIST_LOG_TAG,
                "save.begin playlists=$repoPlaylists counts=${
                    repoPlaylists.joinToString { "$it:${PlaylistRepository.getAllItemsRaw(it).size}" }
                }"
            )
            val nextMap = linkedMapOf<String, PlaylistStateEntry>()
            repoPlaylists.sorted().forEach { playlistName ->
                val previous = current.playlists[playlistName] ?: PlaylistStateEntry()
                nextMap[playlistName] = previous.copy(
                    exists = true,
                    items = PlaylistRepository.getAllItemsRaw(playlistName).map { item ->
                        PlaylistStateItem(
                            uri = item.uri,
                            songId = item.songId?.trim()?.ifBlank { null }
                        )
                    },
                    updatedAt = System.currentTimeMillis()
                )
            }
            val internalSaved = writeStateLocked(
                context = context,
                state = current.copy(
                    schemaVersion = PlaylistState.SCHEMA_VERSION,
                    playlists = nextMap
                )
            )
            val workspaceSaved = WorkspacePlaylistFilesStore.syncFromRepository(context)
            Log.d(
                PERSIST_LOG_TAG,
                "save.end internalSaved=$internalSaved workspaceSaved=$workspaceSaved playlists=$repoPlaylists"
            )
            internalSaved || workspaceSaved
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

        return writeStateLocked(
            context = context,
            state = current.copy(
                schemaVersion = PlaylistState.SCHEMA_VERSION,
                playlists = nextMap
            ),
            failureLabel = "writePlaylistEntryLocked: write failed playlist=$playlistName"
        )
    }

    private fun restoreStateIntoRepository(state: PlaylistState) {
        state.playlists
            .toSortedMap()
            .forEach { (playlistName, entry) ->
                if (!entry.exists && entry.items.isEmpty()) return@forEach
                PlaylistRepository.addPlaylist(playlistName)
                entry.items.forEach { item ->
                    PlaylistRepository.assignSongToPlaylist(
                        playlistName = playlistName,
                        songUri = item.uri,
                        songId = item.songId
                    )
                }
            }
    }

    private fun mergeWorkspacePlaylists(
        internalState: PlaylistState,
        workspaceState: WorkspacePlaylistFilesStore.ReadResult
    ): PlaylistState {
        if (workspaceState.playlists.isEmpty()) {
            return internalState
        }

        val merged = linkedMapOf<String, PlaylistStateEntry>()
        val allNames = (internalState.playlists.keys + workspaceState.playlists.keys).toSortedSet()
        allNames.forEach { playlistName ->
            val internalEntry = internalState.playlists[playlistName]
            val workspaceEntry = workspaceState.playlists[playlistName]
            val nextEntry = when {
                workspaceEntry != null -> {
                    (internalEntry ?: PlaylistStateEntry()).copy(
                        exists = true,
                        items = workspaceEntry.items,
                        updatedAt = maxOf(internalEntry?.updatedAt ?: 0L, workspaceEntry.updatedAt)
                    )
                }

                internalEntry != null -> internalEntry
                else -> null
            } ?: return@forEach

            if (!nextEntry.isEmpty()) {
                merged[playlistName] = nextEntry
            }
        }

        return internalState.copy(
            schemaVersion = PlaylistState.SCHEMA_VERSION,
            playlists = merged
        )
    }

    private fun shouldSyncWorkspaceFiles(
        state: PlaylistState,
        workspaceState: WorkspacePlaylistFilesStore.ReadResult
    ): Boolean {
        val desired = state.playlists
            .filterValues { it.exists || it.items.isNotEmpty() }
            .mapValues { (_, entry) -> entry.items }

        if (desired.isEmpty()) {
            return workspaceState.hasPlaylistFiles
        }
        if (desired.size != workspaceState.playlists.size) {
            return true
        }
        return desired.any { (playlistName, items) ->
            workspaceState.playlists[playlistName]?.items != items
        }
    }

    private fun readStateLocked(context: Context): PlaylistState {
        cachedState?.let { return it }

        val loadStart = SystemClock.elapsedRealtime()
        Log.d("BOOTSTEP", "PlaylistStateStore.loadFromDisk:start")
        val state = try {
            val raw = PlaylistStateAtomicIo.readRaw(context)
            if (raw.isNullOrBlank()) {
                PlaylistState.empty()
            } else {
                PlaylistState.fromJson(raw)
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

    private fun writeStateLocked(
        context: Context,
        state: PlaylistState,
        failureLabel: String = "writeStateLocked: write failed"
    ): Boolean {
        val raw = state.toJson().toString(2)
        Log.d(
            PERSIST_LOG_TAG,
            "write.begin rawLength=${raw.length} playlists=${state.playlists.keys.sorted()} counts=${
                state.playlists.toSortedMap().entries.joinToString { "${it.key}:${it.value.items.size}" }
            }"
        )
        val saved = PlaylistStateAtomicIo.writeRawAtomic(context, raw)
        if (saved) {
            cachedState = state
            Log.d(PERSIST_LOG_TAG, "write.success rawLength=${raw.length}")
            return true
        }

        Log.e(TAG, failureLabel)
        Log.e(PERSIST_LOG_TAG, "write.failed rawLength=${raw.length}")
        return false
    }

    private data class Conversion(
        val storedKeyByUri: Map<String, String>,
        val urisByStoredKey: Map<String, List<String>>
    )

    private fun buildConversion(context: Context, playlistName: String, uris: List<String>): Conversion? {
        if (uris.isEmpty()) return null

        val storedKeyByUri = linkedMapOf<String, String>()
        val urisByStoredKey = linkedMapOf<String, MutableList<String>>()

        for (uri in uris) {
            val storedKey = resolveStoredKey(uri)

            storedKeyByUri[uri] = storedKey
            urisByStoredKey.getOrPut(storedKey) { mutableListOf() }.add(uri)

            resolveLegacyStoredKeys(context, playlistName, uri).forEach { legacyKey ->
                urisByStoredKey.getOrPut(legacyKey) { mutableListOf() }.add(uri)
            }
        }

        return Conversion(
            storedKeyByUri = storedKeyByUri,
            urisByStoredKey = urisByStoredKey.mapValues { it.value.toList() }
        )
    }

    private fun resolveStoredKey(uri: String): String {
        return "uri:$uri"
    }

    private fun resolveLegacyStoredKeys(context: Context, playlistName: String, uri: String): List<String> {
        val keys = linkedSetOf<String>()
        val songId = PlaylistRepository.getPlaylistItem(playlistName, uri)?.songId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: SongIdKeyResolver.resolveSongIdFromUri(context, uri)
        SongIdKeyResolver.songScopedKey(songId)?.let { keys.add(it) }
        TrackSettingsPathResolver.resolveRelativeTrackPath(context, uri)?.let { keys.add(it) }
        return keys.toList()
    }

    private fun reorderFromStoredOrder(
        currentOrderUris: List<String>,
        desiredStoredOrder: List<String>,
        conversion: Conversion
    ): List<String> {
        val usedUris = linkedSetOf<String>()
        val reordered = mutableListOf<String>()
        val remainingUrisByStoredKey = conversion.urisByStoredKey
            .mapValues { (_, uris) -> ArrayDeque(uris) }

        desiredStoredOrder.forEach { storedKey ->
            val candidates = remainingUrisByStoredKey[storedKey] ?: return@forEach
            while (candidates.isNotEmpty()) {
                val uri = candidates.removeFirst()
                if (usedUris.add(uri)) {
                    reordered.add(uri)
                    break
                }
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
