package com.patrick.lrcreader.core.config

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.patrick.lrcreader.core.getGroupTitle
import com.patrick.lrcreader.core.isGroupEnd
import com.patrick.lrcreader.core.isGroupHeader
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object PlaylistStateStore {

    private const val TAG = "PlaylistStateStore"
    private const val PERSIST_LOG_TAG = "PLAYLIST_PERSIST"
    private const val DEMO_TITLES_TAG = "DEMO_TITLES"
    private const val DEMO_PLAYLIST_NAME = "SPL Demo"
    private const val ANR_PLAYLIST_TAG = "ANR_PLAYLIST"

    private val mutex = Mutex()
    private val saveCoordinatorLock = Any()
    private var cachedState: PlaylistState? = null
    private var diskReadCount: Long = 0
    private var diskReadTotalMs: Long = 0
    private var saveInFlight: Boolean = false
    private var savePending: Boolean = false
    private var pendingTransientGroupTitles: Set<String> = emptySet()

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

    fun restorePlaylistsIntoRepository(
        context: Context,
        preferInternalState: Boolean = false
    ): RestoreResult {
        return withLockBlocking {
            val internalState = readStateLocked(context)
            val workspaceState = WorkspacePlaylistFilesStore.readAll(context)
            val mergedState = if (preferInternalState) {
                internalState
            } else {
                mergeWorkspacePlaylists(
                    internalState = internalState,
                    workspaceState = workspaceState
                )
            }
            val transientTitles = currentGroupTransientTitles(context)
            val state = stripTransientGroupsFromState(
                state = mergedState,
                transientGroupTitles = transientTitles
            )
            Log.d(
                PERSIST_LOG_TAG,
                "restore.begin preferInternal=$preferInternalState internal=${internalState.playlists.keys.sorted()} workspace=${workspaceState.playlists.keys.sorted()} merged=${mergedState.playlists.keys.sorted()} cleaned=${state.playlists.keys.sorted()} counts=${
                    state.playlists.toSortedMap().entries.joinToString { "${it.key}:${it.value.items.size}" }
                }"
            )
            PlaylistRepository.clearAll()
            restoreStateIntoRepository(state)
            cachedState = state
            if (state != internalState) {
                writeStateLocked(
                    context = context,
                    state = state,
                    failureLabel = "restorePlaylistsIntoRepository: transient group cleanup write failed"
                )
            }
            val workspaceMigrated = if (shouldSyncWorkspaceFiles(state, workspaceState)) {
                WorkspacePlaylistFilesStore.syncFromRepository(
                    context = context,
                    transientGroupTitles = transientTitles
                )
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

    private fun currentGroupTransientTitles(context: Context): Set<String> {
        return setOf(
            context.getString(R.string.quickplaylists_group_current),
            "Groupe en cours",
            "Current group",
            "Grupo en curso"
        ).mapTo(linkedSetOf()) { it.trim() }
            .filterTo(linkedSetOf()) { it.isNotEmpty() }
    }

    fun savePlaylistsSnapshot(
        context: Context,
        transientGroupTitles: Set<String> = emptySet()
    ): Boolean {
        val startMs = SystemClock.elapsedRealtime()
        val threadName = Thread.currentThread().name
        Log.e(ANR_PLAYLIST_TAG, "save:start thread=$threadName")
        synchronized(saveCoordinatorLock) {
            if (saveInFlight) {
                savePending = true
                pendingTransientGroupTitles = pendingTransientGroupTitles + transientGroupTitles
                Log.e(
                    ANR_PLAYLIST_TAG,
                    "save:coalesced thread=$threadName pending=true"
                )
                return true
            }
            saveInFlight = true
        }
        var anySaved = false
        var activeTransientGroupTitles = transientGroupTitles
        try {
            while (true) {
                anySaved = performSavePlaylistsSnapshot(
                    context = context,
                    startMs = startMs,
                    threadName = threadName,
                    transientGroupTitles = activeTransientGroupTitles
                ) || anySaved
                val rerun = synchronized(saveCoordinatorLock) {
                    if (savePending) {
                        savePending = false
                        activeTransientGroupTitles = pendingTransientGroupTitles
                        pendingTransientGroupTitles = emptySet()
                        true
                    } else {
                        saveInFlight = false
                        pendingTransientGroupTitles = emptySet()
                        false
                    }
                }
                if (!rerun) {
                    return anySaved
                }
                Log.e(
                    ANR_PLAYLIST_TAG,
                    "save:rerun thread=$threadName reason=pending_request"
                )
            }
        } catch (t: Throwable) {
            synchronized(saveCoordinatorLock) {
                saveInFlight = false
                pendingTransientGroupTitles = emptySet()
            }
            throw t
        }
    }

    private fun performSavePlaylistsSnapshot(
        context: Context,
        startMs: Long,
        threadName: String,
        transientGroupTitles: Set<String>
    ): Boolean {
        return withLockBlocking {
            val current = readStateLocked(context)
            val workspaceState = WorkspacePlaylistFilesStore.readAll(context)
            val repoPlaylists = PlaylistRepository.getPlaylists()
            Log.e(
                ANR_PLAYLIST_TAG,
                "save:inside_lock thread=$threadName playlistCount=${repoPlaylists.size}"
            )
            val repoIsEmpty = repoPlaylists.isEmpty()
            if (repoIsEmpty && workspaceState.hasPlaylistFiles) {
                Log.e(
                    PERSIST_LOG_TAG,
                    "save.blocked reason=suspect_empty_repo internal=${current.playlists.size} workspace=${workspaceState.playlists.size} workspaceHasFiles=${workspaceState.hasPlaylistFiles}"
                )
                Log.e(
                    ANR_PLAYLIST_TAG,
                    "save:end blocked=true durationMs=${SystemClock.elapsedRealtime() - startMs} thread=$threadName playlistCount=${repoPlaylists.size}"
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
                val transientTitles = transientGroupTitles
                    .mapTo(linkedSetOf()) { it.trim() }
                    .filterTo(linkedSetOf()) { it.isNotEmpty() }
                val items = PlaylistRepository.getAllItemsRaw(playlistName).map { item ->
                    PlaylistStateItem(
                        uri = item.uri,
                        songId = item.songId?.trim()?.ifBlank { null },
                        customTitle = PlaylistRepository.getCustomTitle(playlistName, item.uri)
                            ?.trim()
                            ?.ifBlank { null }
                    )
                }
                val savedItems = stripTransientGroupItems(
                    items = items,
                    transientGroupTitles = transientTitles
                )
                val savedManualOrder = stripTransientGroupStoredKeys(
                    storedKeys = previous.manualOrder,
                    transientGroupTitles = transientTitles
                )
                val savedOriginalOrder = stripTransientGroupStoredKeys(
                    storedKeys = previous.originalOrder,
                    transientGroupTitles = transientTitles
                )
                if (playlistName == DEMO_PLAYLIST_NAME) {
                    savedItems.forEach { item ->
                        Log.i(
                            DEMO_TITLES_TAG,
                            "snapshot:save playlist=$playlistName uri=${item.uri} songId=${item.songId ?: "null"} customTitle=${item.customTitle ?: "null"}"
                        )
                    }
                }
                nextMap[playlistName] = previous.copy(
                    exists = true,
                    items = savedItems,
                    manualOrder = savedManualOrder,
                    originalOrder = savedOriginalOrder,
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
            val workspaceSaved = WorkspacePlaylistFilesStore.syncFromRepository(
                context = context,
                transientGroupTitles = transientGroupTitles
            )
            Log.d(
                PERSIST_LOG_TAG,
                "save.end internalSaved=$internalSaved workspaceSaved=$workspaceSaved playlists=$repoPlaylists"
            )
            Log.e(
                ANR_PLAYLIST_TAG,
                "save:end durationMs=${SystemClock.elapsedRealtime() - startMs} thread=$threadName playlistCount=${repoPlaylists.size} internalSaved=$internalSaved workspaceSaved=$workspaceSaved"
            )
            internalSaved || workspaceSaved
        }
    }

    private fun stripTransientGroupItems(
        items: List<PlaylistStateItem>,
        transientGroupTitles: Set<String>
    ): List<PlaylistStateItem> {
        if (transientGroupTitles.isEmpty()) return items
        return stripTransientGroupMarkers(
            values = items,
            transientGroupTitles = transientGroupTitles,
            markerValue = { it.uri }
        )
    }

    private fun stripTransientGroupsFromState(
        state: PlaylistState,
        transientGroupTitles: Set<String>
    ): PlaylistState {
        if (transientGroupTitles.isEmpty() || state.playlists.isEmpty()) return state
        val cleaned = state.playlists.mapValues { (_, entry) ->
            entry.copy(
                items = stripTransientGroupItems(
                    items = entry.items,
                    transientGroupTitles = transientGroupTitles
                ),
                manualOrder = stripTransientGroupStoredKeys(
                    storedKeys = entry.manualOrder,
                    transientGroupTitles = transientGroupTitles
                ),
                originalOrder = stripTransientGroupStoredKeys(
                    storedKeys = entry.originalOrder,
                    transientGroupTitles = transientGroupTitles
                )
            )
        }
        return state.copy(playlists = cleaned)
    }

    private fun stripTransientGroupStoredKeys(
        storedKeys: List<String>,
        transientGroupTitles: Set<String>
    ): List<String> {
        if (transientGroupTitles.isEmpty() || storedKeys.isEmpty()) return storedKeys
        return stripTransientGroupMarkers(
            values = storedKeys,
            transientGroupTitles = transientGroupTitles,
            markerValue = { key -> key.removePrefix("uri:") }
        )
    }

    private fun <T> stripTransientGroupMarkers(
        values: List<T>,
        transientGroupTitles: Set<String>,
        markerValue: (T) -> String
    ): List<T> {
        if (transientGroupTitles.isEmpty() || values.isEmpty()) return values
        val keep = BooleanArray(values.size) { true }
        values.forEachIndexed { index, value ->
            val marker = markerValue(value)
            if (!isGroupHeader(marker) || getGroupTitle(marker) !in transientGroupTitles) {
                return@forEachIndexed
            }
            keep[index] = false
            findMatchingTransientGroupEndIndex(
                values = values,
                headerIndex = index,
                markerValue = markerValue
            )?.let { endIndex ->
                keep[endIndex] = false
            }
        }
        return values.filterIndexed { index, _ -> keep[index] }
    }

    private fun <T> findMatchingTransientGroupEndIndex(
        values: List<T>,
        headerIndex: Int,
        markerValue: (T) -> String
    ): Int? {
        var depth = 0
        for (index in headerIndex + 1 until values.size) {
            val marker = markerValue(values[index])
            when {
                isGroupHeader(marker) -> depth++
                isGroupEnd(marker) && depth > 0 -> depth--
                isGroupEnd(marker) -> return index
            }
        }
        return null
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
                    if (playlistName == DEMO_PLAYLIST_NAME) {
                        Log.i(
                            DEMO_TITLES_TAG,
                            "snapshot:restore playlist=$playlistName uri=${item.uri} songId=${item.songId ?: "null"} customTitle=${item.customTitle ?: "null"}"
                        )
                    }
                    PlaylistRepository.assignSongToPlaylist(
                        playlistName = playlistName,
                        songUri = item.uri,
                        songId = item.songId
                    )
                    item.customTitle
                        ?.takeIf { it.isNotBlank() }
                        ?.let { customTitle ->
                            if (playlistName == DEMO_PLAYLIST_NAME) {
                                Log.i(
                                    DEMO_TITLES_TAG,
                                    "snapshot:restore_rename playlist=$playlistName uri=${item.uri} songId=${item.songId ?: "null"} customTitle=$customTitle"
                                )
                            }
                            PlaylistRepository.renameSongInPlaylist(
                                playlistName = playlistName,
                                uri = item.uri,
                                newTitle = customTitle
                            )
                        }
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
        val waitStartMs = SystemClock.elapsedRealtime()
        val threadName = Thread.currentThread().name
        return runBlocking {
            mutex.withLock {
                val acquiredMs = SystemClock.elapsedRealtime()
                Log.e(
                    ANR_PLAYLIST_TAG,
                    "lock:acquired waitMs=${acquiredMs - waitStartMs} thread=$threadName"
                )
                val lockStartMs = acquiredMs
                try {
                    block()
                } finally {
                    Log.e(
                        ANR_PLAYLIST_TAG,
                        "lock:released holdMs=${SystemClock.elapsedRealtime() - lockStartMs} thread=$threadName"
                    )
                }
            }
        }
    }
}
