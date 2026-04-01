@file:OptIn(androidx.media3.common.util.UnstableApi::class, kotlinx.coroutines.FlowPreview::class)

package com.patrick.lrcreader.exo


import android.database.Cursor
import android.content.Intent
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import com.patrick.lrcreader.ui.library.SetupInstallScreen
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.patrick.lrcreader.core.ImportAudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.MidiOutput
import android.os.Bundle
import android.os.Looper
import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.patrick.lrcreader.core.*
import com.patrick.lrcreader.core.audio.AudioEngine
import com.patrick.lrcreader.core.dj.DjEngine
import com.patrick.lrcreader.core.exoCrossfadePlay
import com.patrick.lrcreader.core.history.HistoryRepository
import com.patrick.lrcreader.core.history.PlaySource
import com.patrick.lrcreader.core.light.LightCueDispatcher
import com.patrick.lrcreader.core.lyrics.LyricsMemoryCache
import com.patrick.lrcreader.core.lyrics.LyricsResolver
import com.patrick.lrcreader.core.config.MidiCuesConfigStore
import com.patrick.lrcreader.core.config.NotesConfigStore
import com.patrick.lrcreader.core.config.PlaylistStateStore
import com.patrick.lrcreader.core.config.TitleAliasesStore
import com.patrick.lrcreader.core.config.TrackSettingsStore
import com.patrick.lrcreader.smp.SmpImporter
import com.patrick.lrcreader.smp.SmpAutoMigration
import com.patrick.lrcreader.smp.SmpAutoMigrationResult
import com.patrick.lrcreader.smp.SmpBatchImportProcessor
import com.patrick.lrcreader.smp.SmpConverter
import com.patrick.lrcreader.smp.SmpImportedSongDetail
import com.patrick.lrcreader.smp.SmpImportedUiSignal
import com.patrick.lrcreader.smp.SmpArchiveFinalizeScheduler
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.SmpSecureImportPipeline
import com.patrick.lrcreader.smp.SmpUserArchiveRebuilder
import com.patrick.lrcreader.ui.*
import com.patrick.lrcreader.ui.library.LibraryScreen
import com.patrick.lrcreader.ui.library.ensureWorkspaceLibraryFolders
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow

class MainActivity : AppCompatActivity() {
    private data class SessionSnapshot(
        val tabKey: String,
        val quickPlaylist: String?,
        val openedPlaylist: String?,
        val currentPlayingUri: String?,
        val currentPlayingPlaylist: String?
    )

    companion object {
        private const val DEFAULT_TRACK_GAIN_DB = -5
        private const val MIN_TRACK_DB = -12
        private const val MAX_TRACK_DB = 0
        private const val SMP_PLAY_TRACE_TAG = "SMP_PLAY_TRACE"
        private const val ENABLE_SMP_DEBUG_HOME_BUTTONS = false
        private val AUTO_RESTORE_BG_STARTED = AtomicBoolean(false)
        private val BACKUP_RESTORE_BG_STARTED = AtomicBoolean(false)
        private val DEFERRED_BOOTSTRAP_STARTED = AtomicBoolean(false)


    }

    @Volatile
    private var latestSessionSnapshot = SessionSnapshot(
        tabKey = TAB_HOME,
        quickPlaylist = null,
        openedPlaylist = null,
        currentPlayingUri = null,
        currentPlayingPlaylist = null
    )
    @Volatile
    private var lastPersistedSessionSnapshot: SessionSnapshot? = null
    private var sessionSaveJob: Job? = null
    private val sessionPersistGate = MutableSharedFlow<String>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val sessionPersistGateStarted = AtomicBoolean(false)

    private fun ensureSessionPersistGateStarted() {
        if (!sessionPersistGateStarted.compareAndSet(false, true)) return
        lifecycleScope.launch {
            sessionPersistGate.collectLatest { reason ->
                val debounced = reason == "snapshotFlow"
                if (debounced) delay(800L)
                val snapshot = latestSessionSnapshot
                withContext(Dispatchers.IO) {
                    persistSessionNow(reason = reason, snapshot = snapshot, debounced = debounced)
                }
            }
        }
    }

    private fun requestSessionPersist(reason: String, snapshot: SessionSnapshot = latestSessionSnapshot) {
        latestSessionSnapshot = snapshot
        val queued = sessionPersistGate.tryEmit(reason)
        if (!queued && BuildConfig.DEBUG) {
            Log.d("PERF_SESSION", "persistSession reason=$reason droppedByGate")
        }
    }

    private suspend fun persistSessionNow(
        reason: String,
        snapshot: SessionSnapshot = latestSessionSnapshot,
        debounced: Boolean = false
    ) {
        val safeTab = snapshot.tabKey.ifBlank { TAB_HOME }
        val safeUri = snapshot.currentPlayingUri?.takeIf { it.isNotBlank() }
        val safeSongId = resolveSessionSongIdFromTrackUri(safeUri)
        val safePlaylist = snapshot.currentPlayingPlaylist?.takeIf { it.isNotBlank() }
            ?: snapshot.quickPlaylist?.takeIf { it.isNotBlank() }
        val normalizedSnapshot = SessionSnapshot(
            tabKey = safeTab,
            quickPlaylist = snapshot.quickPlaylist?.takeIf { it.isNotBlank() },
            openedPlaylist = snapshot.openedPlaylist?.takeIf { it.isNotBlank() },
            currentPlayingUri = safeUri,
            currentPlayingPlaylist = safePlaylist
        )

        if (lastPersistedSessionSnapshot == normalizedSnapshot && (sessionSaveJob?.isActive != true)) {
            Log.d("BOOTSTEP", "SessionPersist:skip unchanged reason=$reason")
            return
        }

        Log.d(
            "BOOTSTEP",
            "SessionPersist:before reason=$reason tab=$safeTab quick=${snapshot.quickPlaylist} opened=${snapshot.openedPlaylist} uri=$safeUri songId=$safeSongId playlist=$safePlaylist"
        )
        val tStart = SystemClock.elapsedRealtime()
        SessionPrefs.saveSessionSnapshot(
            context = this@MainActivity,
            tab = normalizedSnapshot.tabKey,
            quickPlaylist = normalizedSnapshot.quickPlaylist,
            openedPlaylist = normalizedSnapshot.openedPlaylist,
            lastTrackUri = normalizedSnapshot.currentPlayingUri,
            lastPlaylistName = normalizedSnapshot.currentPlayingPlaylist,
            lastSongId = safeSongId
        )
        lastPersistedSessionSnapshot = normalizedSnapshot
        if (BuildConfig.DEBUG) {
            val suffix = if (debounced) " debounced" else ""
            Log.d(
                "PERF_SESSION",
                "persistSession reason=$reason$suffix ms=${SystemClock.elapsedRealtime() - tStart} tab=${normalizedSnapshot.tabKey}"
            )
        }
        Log.d("BOOTSTEP", "SessionPersist:after reason=$reason")
    }

    private fun resolveSessionSongIdFromTrackUri(trackUriString: String?): String? {
        val rawUri = trackUriString?.trim().orEmpty()
        if (rawUri.isEmpty()) return null

        getSmpSongId(rawUri)?.let { songId ->
            return songId.takeIf { it.isNotBlank() }
        }

        val trackUri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null
        if (trackUri.scheme != "file") return null

        val audioPath = trackUri.path?.takeIf { it.isNotBlank() } ?: return null
        val audioFile = File(audioPath)
        if (!audioFile.isFile || !audioFile.name.startsWith("audio.", ignoreCase = true)) {
            return null
        }

        val songDir = runCatching { audioFile.parentFile?.canonicalFile }.getOrNull() ?: return null
        val tracksRoot = runCatching { File(filesDir, "tracks").canonicalFile }.getOrNull() ?: return null
        if (songDir.parentFile?.canonicalFile != tracksRoot) {
            return null
        }

        if (!File(songDir, "config.json").isFile) {
            return null
        }

        return songDir.name.takeIf { it.isNotBlank() }
    }

    private fun persistSession(reason: String, snapshot: SessionSnapshot = latestSessionSnapshot) {
        sessionSaveJob?.cancel()
        sessionSaveJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                persistSessionNow(reason = reason, snapshot = snapshot, debounced = false)
            } catch (_: CancellationException) {
                Log.d("BOOTSTEP", "SessionPersist:cancelled reason=$reason")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLanguagePrefs.applySavedLanguage(this)
        super.onCreate(savedInstanceState)
        ensureSessionPersistGateStarted()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
        }

        val t0 = SystemClock.elapsedRealtime()
        fun mark(step: String) {
            val elapsed = SystemClock.elapsedRealtime() - t0
            val thread = Thread.currentThread().name
            val isMain = Looper.getMainLooper().thread == Thread.currentThread()
            Log.d("BOOTSTEP", "$step +${elapsed}ms (thread=$thread main=$isMain)")
        }

        mark("onCreate:start")
        mark("WindowCompat.setDecorFitsSystemWindows:before")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        mark("WindowCompat.setDecorFitsSystemWindows:after")

        mark("AutoRestore.restoreIfNeeded:deferred")

        mark("MidiOutput.init:deferred")
        mark("CueMidiStore.init:deferred")

        mark("SessionPrefs.getTab/getQuick/getOpened:before")
        val initialTabKey = SessionPrefs.getTab(this)
        val initialQuickPlaylist = SessionPrefs.getQuickPlaylist(this)
        val initialOpenedPlaylist = SessionPrefs.getOpenedPlaylist(this)
        val initialLastSession = SessionPrefs.getLastSessionState(this)
        val initialLastTrackUri = initialLastSession.trackUri
        val initialLastSongId = initialLastSession.songId
        val initialLastPlaylist = initialLastSession.playlistName
        mark("SessionPrefs.getTab/getQuick/getOpened:after")
        latestSessionSnapshot = SessionSnapshot(
            tabKey = initialTabKey ?: TAB_HOME,
            quickPlaylist = initialQuickPlaylist,
            openedPlaylist = initialOpenedPlaylist,
            currentPlayingUri = initialLastTrackUri,
            currentPlayingPlaylist = initialLastPlaylist
        )

        mark("DjEngine.init:deferred/lazy (DjScreen)")

        mark("WorkspaceResolver.resolve(onCreate):before")
        val startupWorkspaceSnapshot = WorkspaceResolver.resolve(this)
        val root = startupWorkspaceSnapshot
            .takeIf { it.isUsable }
            ?.workspaceRootUri
        Log.i(
            "WORKSPACE_C1",
            "stage=main_activity:on_create status=${startupWorkspaceSnapshot.status} mode=${startupWorkspaceSnapshot.mode} root=$root detail=${startupWorkspaceSnapshot.detail}"
        )
        mark("WorkspaceResolver.resolve(onCreate):after root=$root")
        if (root != null) {
            mark("LibraryIndexCache.load(onCreate):before")
            val cached = LibraryIndexCache.load(this)
            mark("LibraryIndexCache.load(onCreate):after size=${cached?.size ?: 0}")
            if (!cached.isNullOrEmpty()) {
                LibrarySnapshot.rootFolderUri = root
                LibrarySnapshot.entries = cached.map { it.uriString }
                LibrarySnapshot.isReady = true
            }
        }
        // ✅ Auto backup : planifie le worker à chaque démarrage (WorkManager gère le "unique")
        mark("AutoBackupScheduler.ensureScheduled:before")
        AutoBackupScheduler.ensureScheduled(this)
        mark("AutoBackupScheduler.ensureScheduled:after")
        mark("SmpArchiveFinalizeScheduler.reconcilePending:before")
        SmpArchiveFinalizeScheduler.reconcilePending(this)
        mark("SmpArchiveFinalizeScheduler.reconcilePending:after")


        mark("BackupManager.autoRestoreFromDefaultBackupFile:deferred")
        mark("setContent:before")
        setContent {
            LaunchedEffect(Unit) {
                mark("setContent:entered")
            }
            val scheme = darkColorScheme(
                primary = Color(0xFFFFC107),
                onPrimary = Color.Black
            )
            MaterialTheme(colorScheme = scheme) {

                val ctx = this@MainActivity
// -------------------- SETUP SPL (bloc unique, inratable) --------------------
                var setupTick by remember { mutableIntStateOf(0) }
                var forceSetup by rememberSaveable { mutableStateOf(false) }

                var isImporting by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val workspaceSnapshot = remember(setupTick) {
                    mark("compose.WorkspaceResolver.resolve:before")
                    WorkspaceResolver.resolve(ctx).also { snapshot ->
                        mark(
                            "compose.WorkspaceResolver.resolve:after status=${snapshot.status} mode=${snapshot.mode} root=${snapshot.workspaceRootUri} setupTree=${snapshot.setupTreeUri}"
                        )
                    }
                }
                val savedRoot = workspaceSnapshot.workspaceRootUri
                val isInternalMode = workspaceSnapshot.status == WorkspaceResolver.Status.INTERNAL_LEGACY
                val isSetupDone = remember(setupTick, isInternalMode) {
                    BackupFolderPrefs.isDone(ctx) || isInternalMode
                }
                val hasSetupPerm = remember(workspaceSnapshot.status) {
                    when (workspaceSnapshot.status) {
                        WorkspaceResolver.Status.UNCONFIGURED,
                        WorkspaceResolver.Status.PERMISSION_MISSING -> false
                        else -> true
                    }
                }
                val canUseWorkspace = remember(workspaceSnapshot.status) {
                    workspaceSnapshot.isUsable
                }
                val shouldShowSetup = forceSetup || !canUseWorkspace
                var configInitDoneForRoot by remember { mutableStateOf<String?>(null) }
                var legacyTrimByUri by remember { mutableStateOf<Map<String, EditPrefs.EditData>>(emptyMap()) }

                LaunchedEffect(savedRoot, canUseWorkspace, workspaceSnapshot.status) {
                    val canUseStorage = canUseWorkspace
                    val rootKey = savedRoot?.toString()
                    mark(
                        "compose.ensureInitialized.effect:start savedRoot=$savedRoot canUseStorage=$canUseStorage workspaceStatus=${workspaceSnapshot.status} doneFor=$configInitDoneForRoot"
                    )

                    if (savedRoot == null || !canUseStorage) {
                        mark("compose.ensureInitialized.effect:skip noStorage")
                        return@LaunchedEffect
                    }

                    if (configInitDoneForRoot == rootKey) {
                        mark("compose.ensureInitialized.effect:skip alreadyDone root=$rootKey")
                        return@LaunchedEffect
                    }

                    var loadedLegacyTrimByUri: Map<String, EditPrefs.EditData> = emptyMap()
                    val legacyCustomTitlesSnapshot = PlaylistRepository.getAnyCustomTitlesSnapshot()
                    var migratedTitleAliases = 0
                    withContext(Dispatchers.IO) {
                        mark("compose.ensureInitialized.io:start root=$rootKey")
                        val sessionInitOk = runCatching { SessionPrefs.ensureInitialized(ctx) }.getOrDefault(false)
                        val trimInitOk = runCatching { EditSoundPrefs.ensureInitialized(ctx) }.getOrDefault(false)
                        runCatching { EditSoundPrefs.warmCache(ctx) }
                        runCatching { FillerSoundPrefs.warmCache(ctx) }
                        val textSongsInitOk = runCatching { TextSongRepository.ensureInitialized(ctx) }.getOrDefault(false)
                        val trackInitOk = runCatching { TrackSettingsStore.ensureInitialized(ctx) }.getOrDefault(false)
                        val aliasInitOk = runCatching { TitleAliasesStore.ensureInitialized(ctx) }.getOrDefault(false)
                        val notesInitOk = runCatching { NotesConfigStore.ensureInitialized(ctx) }.getOrDefault(false)
                        val midiInitOk = runCatching { MidiCuesConfigStore.ensureInitialized(ctx) }.getOrDefault(false)
                        val playlistInitOk = runCatching { PlaylistStateStore.ensureInitialized(ctx) }.getOrDefault(false)
                        migratedTitleAliases = runCatching {
                            TitleAliasesStore.migrateFromLegacyTitlesIfMissing(
                                context = ctx,
                                legacyTitlesByUri = legacyCustomTitlesSnapshot
                            )
                        }.getOrDefault(0)
                        loadedLegacyTrimByUri = runCatching { EditPrefs.getAllEdits(ctx) }.getOrDefault(emptyMap())
                        mark(
                            "compose.ensureInitialized.io:end root=$rootKey session=$sessionInitOk trim=$trimInitOk textSongs=$textSongsInitOk track=$trackInitOk alias=$aliasInitOk aliasMigrated=$migratedTitleAliases notes=$notesInitOk midi=$midiInitOk playlist=$playlistInitOk"
                        )
                    }

                    legacyTrimByUri = loadedLegacyTrimByUri
                    if (migratedTitleAliases > 0) {
                        PlaylistRepository.touch()
                    }
                    configInitDoneForRoot = rootKey
                    mark("compose.ensureInitialized.effect:end root=$rootKey")
                }

                android.util.Log.d(
                    "SETUP_GATE",
                    "workspaceStatus=${workspaceSnapshot.status} detail=${workspaceSnapshot.detail} savedRoot=$savedRoot setupTree=${workspaceSnapshot.setupTreeUri} internal=$isInternalMode isDone=${BackupFolderPrefs.isDone(ctx)} hasPerm=$hasSetupPerm shouldShow=$shouldShowSetup"
                )
                var isSmpImportedSongsDialogOpen by remember { mutableStateOf(false) }
                var smpImportedSongs by remember { mutableStateOf<List<com.patrick.lrcreader.smp.SongUnit>>(emptyList()) }
                var smpSongsById by remember { mutableStateOf<Map<String, com.patrick.lrcreader.smp.SongUnit>>(emptyMap()) }
                var selectedSmpImportedSongDetail by remember { mutableStateOf<SmpImportedSongDetail?>(null) }
                var pendingPlaylistTrackTarget by remember { mutableStateOf<String?>(null) }
                var pendingPlaylistBatchPlan by remember {
                    mutableStateOf<SmpBatchImportProcessor.BatchPlan?>(null)
                }
                var lastImportedSmpUiSignal by remember { mutableStateOf<SmpImportedUiSignal?>(null) }
                val lastSmpImportFailureReason = remember { AtomicReference<String?>(null) }
                val smpImporter = remember(ctx) { SmpImporter(ctx) }
                val smpSecureImportPipeline = remember(ctx) { SmpSecureImportPipeline(ctx) }
                val smpAutoMigration = remember(ctx) { SmpAutoMigration(ctx) }
                val smpBatchProcessor = remember(ctx) {
                    SmpBatchImportProcessor(ctx, SmpConverter(ctx))
                }
                val smpLibraryScanner = remember(ctx) { SmpLibraryScanner(ctx) }
                val smpUserArchiveRebuilder = remember(ctx) { SmpUserArchiveRebuilder(ctx) }
                var smpCacheRefreshTick by remember { mutableIntStateOf(0) }
                var smpUserRebuildAttemptedForRoot by remember { mutableStateOf<String?>(null) }
                var playlistBatchProgressVisible by remember { mutableStateOf(false) }
                var playlistBatchProgressValue by remember { mutableStateOf<Float?>(null) }
                var playlistBatchProgressLabel by remember { mutableStateOf("") }
                val sBatchUnsupportedOnly = stringResource(R.string.smp_batch_unsupported_only)
                val sBatchPlaylistSummary = stringResource(R.string.smp_batch_playlist_summary)
                val sBatchProgressTitle = stringResource(R.string.smp_batch_progress_title)
                val sBatchStageConverting = stringResource(R.string.smp_batch_stage_converting)
                val sBatchStageImporting = stringResource(R.string.smp_batch_stage_importing)
                val sBatchStagePlaylist = stringResource(R.string.smp_batch_stage_playlist)

                fun playlistBatchProgressFraction(progress: SmpBatchImportProcessor.Progress): Float {
                    val total = progress.totalCount.coerceAtLeast(1)
                    val base = (progress.currentItemIndex - 1).coerceAtLeast(0).toFloat() / total.toFloat()
                    val stageOffset = when (progress.stage) {
                        SmpBatchImportProcessor.ProgressStage.CONVERTING -> 0.2f
                        SmpBatchImportProcessor.ProgressStage.IMPORTING -> 0.7f
                        SmpBatchImportProcessor.ProgressStage.ADDING_TO_PLAYLIST -> 0.95f
                    } / total.toFloat()
                    return (base + stageOffset).coerceIn(0f, 1f)
                }

                fun formatPlaylistBatchProgressLabel(progress: SmpBatchImportProcessor.Progress): String {
                    val stageLabel = when (progress.stage) {
                        SmpBatchImportProcessor.ProgressStage.CONVERTING -> sBatchStageConverting
                        SmpBatchImportProcessor.ProgressStage.IMPORTING -> sBatchStageImporting
                        SmpBatchImportProcessor.ProgressStage.ADDING_TO_PLAYLIST -> sBatchStagePlaylist
                    }
                    return "$stageLabel ${progress.currentItemIndex}/${progress.totalCount}\n${progress.displayName}"
                }

                fun displayNameOf(uri: Uri): String {
                    val cr = ctx.contentResolver
                    var name = uri.lastPathSegment ?: "unknown"
                    val c: Cursor? = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    c?.use {
                        if (it.moveToFirst()) {
                            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) name = it.getString(idx)
                        }
                    }
                    return name
                }

                fun displayNameCandidatesOf(uri: Uri): List<String> {
                    val candidates = linkedSetOf<String>()
                    val displayName = displayNameOf(uri).trim()
                    if (displayName.isNotEmpty()) candidates += displayName
                    val documentName = runCatching {
                        DocumentFile.fromSingleUri(ctx, uri)?.name?.trim()
                    }.getOrNull()
                    if (!documentName.isNullOrEmpty()) candidates += documentName
                    val lastPath = uri.lastPathSegment?.trim()
                    if (!lastPath.isNullOrEmpty()) candidates += lastPath
                    val rawUri = uri.toString().trim()
                    if (rawUri.isNotEmpty()) candidates += rawUri
                    return candidates.toList()
                }

                fun hasAnyExtension(candidates: List<String>, extensions: List<String>): Boolean {
                    return candidates.any { candidate ->
                        extensions.any { ext -> candidate.endsWith(ext, ignoreCase = true) }
                    }
                }

                fun isLikelySmpSelection(
                    mimeType: String?,
                    nameCandidates: List<String>
                ): Boolean {
                    if (hasAnyExtension(nameCandidates, listOf(".smp"))) return true

                    val cleanMime = mimeType?.trim()?.lowercase()
                    return cleanMime == "application/zip" ||
                        cleanMime == "application/x-zip-compressed" ||
                        cleanMime == "application/octet-stream"
                }

                suspend fun importSmpIntoApp(
                    uri: Uri,
                    libraryRuntimeReadyFirst: Boolean = false
                ): com.patrick.lrcreader.smp.SongUnit? {
                    val importStartMs = SystemClock.elapsedRealtime()
                    Log.i(
                        "IMPORT_TRACE",
                        "elapsedMs=$importStartMs step=main_importSmpIntoApp_start uri=$uri libraryRuntimeReadyFirst=$libraryRuntimeReadyFirst"
                    )
                    lastSmpImportFailureReason.set(null)
                    val blockingResult = if (!libraryRuntimeReadyFirst) {
                        withContext(Dispatchers.IO) {
                            smpSecureImportPipeline.import(uri, smpImporter)
                        }
                    } else {
                        null
                    }
                    val runtimeReadyResult = if (libraryRuntimeReadyFirst) {
                        withContext(Dispatchers.IO) {
                            smpSecureImportPipeline.importRuntimeReady(uri, smpImporter)
                        }
                    } else {
                        null
                    }
                    Log.i(
                        "IMPORT_TRACE",
                        "elapsedMs=${SystemClock.elapsedRealtime()} step=main_importSmpIntoApp_pipeline_done durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri blockingSuccess=${blockingResult?.isSuccess} runtimeReadySuccess=${runtimeReadyResult?.isRuntimeReadySuccess} songId=${blockingResult?.importedSong?.id ?: runtimeReadyResult?.importedSong?.id} failureReason=${blockingResult?.failureReason ?: runtimeReadyResult?.failureReason} archiveState=${runtimeReadyResult?.archiveState} archiveFailureReason=${runtimeReadyResult?.archiveFailureReason}"
                    )
                    val blockingFailureReason = blockingResult?.failureReason
                    val runtimeFailureReason = runtimeReadyResult?.failureReason
                    if (!blockingFailureReason.isNullOrBlank()) {
                        lastSmpImportFailureReason.set(blockingFailureReason)
                    } else if (!runtimeFailureReason.isNullOrBlank()) {
                        lastSmpImportFailureReason.set(runtimeFailureReason)
                    }

                    val importedSong = blockingResult?.importedSong ?: runtimeReadyResult?.importedSong ?: run {
                        Log.i(
                            "IMPORT_TRACE",
                            "elapsedMs=${SystemClock.elapsedRealtime()} step=main_importSmpIntoApp_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=no_song"
                        )
                        return null
                    }

                    runtimeReadyResult?.archiveFailureReason?.let { archiveFailureReason ->
                        Log.w(
                            "IMPORT_TRACE",
                            "elapsedMs=${SystemClock.elapsedRealtime()} step=main_importSmpIntoApp_archive_finalize_pending_issue songId=${importedSong.id} requestId=${runtimeReadyResult.archiveRequestId} archiveState=${runtimeReadyResult.archiveState} failureReason=$archiveFailureReason"
                        )
                    }

                    withContext(Dispatchers.Main) {
                        smpSongsById = smpSongsById + (importedSong.id to importedSong)
                        smpCacheRefreshTick++
                        lastImportedSmpUiSignal = SmpImportedUiSignal(
                            songId = importedSong.id,
                            title = importedSong.title,
                            requestVersion = smpCacheRefreshTick
                        )
                        Log.i(
                            "IMPORT_TRACE",
                            "elapsedMs=${SystemClock.elapsedRealtime()} step=main_importSmpIntoApp_publish songId=${importedSong.id} refreshTick=$smpCacheRefreshTick requestVersion=${lastImportedSmpUiSignal?.requestVersion} libraryRuntimeReadyFirst=$libraryRuntimeReadyFirst"
                        )
                    }
                    Log.i(
                        "IMPORT_TRACE",
                        "elapsedMs=${SystemClock.elapsedRealtime()} step=main_importSmpIntoApp_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=success songId=${importedSong.id}"
                    )
                    return importedSong
                }

                fun runPlaylistBatchImport(
                    playlistName: String,
                    plan: SmpBatchImportProcessor.BatchPlan
                ) {
                    scope.launch {
                        playlistBatchProgressVisible = true
                        playlistBatchProgressValue = 0f
                        playlistBatchProgressLabel = sBatchProgressTitle
                        try {
                            val result = withContext(Dispatchers.IO) {
                                smpBatchProcessor.process(
                                    plan = plan,
                                    playlistName = playlistName,
                                    importSmp = { uri -> importSmpIntoApp(uri, libraryRuntimeReadyFirst = true) },
                                    importFailureReasonProvider = {
                                        lastSmpImportFailureReason.get() ?: smpImporter.lastFailureReason
                                    },
                                    addImportedSongToPlaylist = { targetPlaylist, importedSong ->
                                        withContext(Dispatchers.Main) {
                                            runCatching {
                                                val smpMarker = buildSmpItem(importedSong.id)
                                                PlaylistRepository.createIfNotExists(targetPlaylist)
                                                PlaylistRepository.assignSongToPlaylist(targetPlaylist, smpMarker)
                                                PlaylistRepository.renameSongInPlaylist(
                                                    playlistName = targetPlaylist,
                                                    uri = smpMarker,
                                                    newTitle = importedSong.title
                                                )
                                            }
                                        }
                                    },
                                    onProgress = { progress ->
                                        runOnUiThread {
                                            playlistBatchProgressValue = playlistBatchProgressFraction(progress)
                                            playlistBatchProgressLabel = formatPlaylistBatchProgressLabel(progress)
                                        }
                                    }
                                )
                            }

                            playlistBatchProgressValue = 1f
                            playlistBatchProgressLabel = sBatchProgressTitle

                            val message = ctx.getString(
                                R.string.smp_batch_playlist_summary,
                                result.successCount,
                                result.failureCount
                            )
                            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                        } finally {
                            pendingPlaylistTrackTarget = null
                            pendingPlaylistBatchPlan = null
                            playlistBatchProgressVisible = false
                            playlistBatchProgressValue = null
                            playlistBatchProgressLabel = ""
                        }
                    }
                }

                val pickSmpFileLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri == null) {
                        Log.d("SMP", "Sélection du fichier .smp annulée")
                        return@rememberLauncherForActivityResult
                    }

                    runCatching {
                        ctx.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }.onFailure { error ->
                        Log.w("SMP", "Permission persistante non obtenue pour $uri", error)
                    }

                    val pickedName = displayNameOf(uri)
                    if (!pickedName.endsWith(".smp", ignoreCase = true)) {
                        Log.w("SMP", "Fichier sélectionné sans extension .smp: name=$pickedName uri=$uri")
                    }
                    Log.w(
                        "IMPORT_PROOF",
                        "elapsedMs=${SystemClock.elapsedRealtime()} file=MainActivity.kt phase=library_external_smp_picker detail=name=$pickedName uri=$uri"
                    )
                    Toast.makeText(ctx, "IMPORT_PROOF external SMP picker path", Toast.LENGTH_SHORT).show()

                    scope.launch {
                        val importedSong = importSmpIntoApp(uri, libraryRuntimeReadyFirst = true)
                        val toastMessage = if (importedSong != null) {
                            Log.i(
                                "SMP",
                                "Import SMP réussi: name=$pickedName songId=${importedSong.id} title=${importedSong.title} storageFolder=${importedSong.storageFolder}"
                            )
                            "Import SMP réussi"
                        } else {
                            val failureReason = lastSmpImportFailureReason.get()
                                ?: smpImporter.lastFailureReason
                                ?: "inconnue"
                            Log.e(
                                "SMP",
                                "Import SMP échoué: name=$pickedName reason=$failureReason"
                            )
                            "Import SMP échoué: $failureReason"
                        }

                        Toast.makeText(ctx, toastMessage, Toast.LENGTH_SHORT).show()
                    }
                }
                val pickPlaylistTrackLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    val targetPlaylist = pendingPlaylistTrackTarget?.trim()

                    if (uris.isNullOrEmpty()) {
                        Log.d("PLAYLIST_ADD", "Sélection annulée")
                        pendingPlaylistTrackTarget = null
                        return@rememberLauncherForActivityResult
                    }
                    if (targetPlaylist.isNullOrEmpty()) {
                        Log.w("PLAYLIST_ADD", "Aucune playlist cible pour les fichiers sélectionnés")
                        pendingPlaylistTrackTarget = null
                        return@rememberLauncherForActivityResult
                    }

                    scope.launch {
                        val plan = withContext(Dispatchers.IO) {
                            smpBatchProcessor.buildPlan(uris)
                        }
                        if (!plan.hasSupportedItems) {
                            pendingPlaylistTrackTarget = null
                            Toast.makeText(ctx, sBatchUnsupportedOnly, Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        if (plan.hasAudioToPrepare && SmpPreparationNoticePrefs.shouldShow(ctx)) {
                            pendingPlaylistBatchPlan = plan
                        } else {
                            runPlaylistBatchImport(targetPlaylist, plan)
                        }
                    }
                }
                val pickAudioFilesLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
                    val libraryFolders = ensureWorkspaceLibraryFolders(
                        context = ctx,
                        providedSnapshot = workspaceSnapshot,
                        expectedMode = workspaceSnapshot.mode,
                        stage = "main_activity:pick_audio_files"
                    )
                    if (libraryFolders == null) {
                        android.util.Log.e(
                            "WORKSPACE_C1",
                            "stage=main_activity:pick_audio_files error=workspace_unavailable status=${workspaceSnapshot.status} root=${workspaceSnapshot.workspaceRootUri}"
                        )
                        return@rememberLauncherForActivityResult
                    }
                    val internalModeNow = libraryFolders.snapshot.mode == StorageModePrefs.Mode.INTERNAL

                    if (internalModeNow) {
                        scope.launch {
                            isImporting = true
                            try {
                                val audioDir = File(libraryFolders.audioUri.path!!)

                                fun displayNameOf(uri: Uri): String {
                                    val cr = ctx.contentResolver
                                    var name = uri.lastPathSegment ?: "audio_${System.currentTimeMillis()}.mp3"
                                    val c: Cursor? = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                    c?.use {
                                        if (it.moveToFirst()) {
                                            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                            if (idx >= 0) name = it.getString(idx)
                                        }
                                    }
                                    return name
                                }

                                withContext(Dispatchers.IO) {
                                    uris.forEach { u ->
                                        val fileName = displayNameOf(u)
                                        val dest = File(audioDir, fileName)

                                        ctx.contentResolver.openInputStream(u)?.use { input ->
                                            FileOutputStream(dest).use { out ->
                                                input.copyTo(out)
                                            }
                                        }
                                    }
                                }

                                // ✅ Refresh : si tu veux, tu peux juste dire "refreshKey++" plus tard
                                // Ici on force le setup ok
                                BackupFolderPrefs.setDone(ctx, true)
                                forceSetup = false
                                setupTick++

                                android.util.Log.d("IMPORT_INTERNAL", "Copied ${uris.size} files to ${audioDir.absolutePath}")
                            } catch (e: Exception) {
                                android.util.Log.e("IMPORT_INTERNAL", "Crash import internal", e)
                            } finally {
                                isImporting = false
                            }
                        }
                        return@rememberLauncherForActivityResult
                    }

                    scope.launch {
                        isImporting = true
                        try {
                            val result = withContext(Dispatchers.IO) {
                                ImportAudioManager.importAudioFilesToFolder(
                                    context = ctx,
                                    destFolderUri = libraryFolders.audioUri,
                                    sourceUris = uris,
                                    overwriteIfExists = false
                                )
                            }

                            android.util.Log.d(
                                "IMPORT",
                                "copied=${result.copiedCount} skipped=${result.skippedCount} errors=${result.errors.size}"
                            )
                            result.errors.take(20).forEach { android.util.Log.e("IMPORT", it) }

                            val newIndex = withContext(Dispatchers.IO) {
                                buildFullIndex(ctx, libraryFolders.rootUri)
                            }
                            LibraryIndexCache.save(ctx, newIndex)
                            LibrarySnapshot.rootFolderUri = libraryFolders.rootUri
                            LibrarySnapshot.entries = newIndex.map { it.uriString }
                            LibrarySnapshot.isReady = true

                        } finally {
                            isImporting = false
                            BackupFolderPrefs.setDone(ctx, true)
                            forceSetup = false
                            setupTick++
                        }
                    }
                }


                if (shouldShowSetup) {

                    SetupInstallScreen(
                        titleColor = Color.White,
                        subtitleColor = Color(0xFFB0BEC5),
                        accent = Color(0xFFFFC107),

                        onSetupDone = {
                            BackupFolderPrefs.setDone(ctx, true)
                            forceSetup = false
                            setupTick++
                        },

                        onImportNow = {
                            pickAudioFilesLauncher.launch(arrayOf("audio/*"))
                        },

                        onImportLater = {
                            // rien : on continue sans importer
                        }
                    )

                    // ✅ DEBUG : reset setup (optionnel) — tu peux le garder
                    if (BuildConfig.DEBUG) {
                        Button(onClick = {
                            BackupFolderPrefs.clearAll(ctx)
                            BackupRestorePrefs.clear(ctx)
                            forceSetup = true
                            setupTick++
                        }) { Text(stringResource(R.string.debug_reset_setup)) }
                    }

                    return@MaterialTheme
                }
// -------------------- FIN SETUP SPL --------------------


                val audioPlayerEpoch by AudioEngine.playerEpoch.collectAsState()
                val exoPlayer = remember(audioPlayerEpoch) {
                    mark("compose.AudioEngine.getPlayer:before")
                    val player = AudioEngine.getPlayer(ctx) {}
                    mark("compose.AudioEngine.getPlayer:after")
                    player
                }

                val embeddedLyricsListener = remember(audioPlayerEpoch) {
                    mark("compose.AudioEngine.getLyricsListener:before")
                    val listener = AudioEngine.getLyricsListener()
                    mark("compose.AudioEngine.getLyricsListener:after")
                    listener
                }
                DisposableEffect(exoPlayer, embeddedLyricsListener) {
                    exoPlayer.addListener(embeddedLyricsListener)
                    onDispose { exoPlayer.removeListener(embeddedLyricsListener) }
                }

                var selectedTab by remember {
                    mutableStateOf<BottomTab>(initialTabKey?.let { tabFromKey(it) } ?: BottomTab.Home)
                }
                val tabStateHolder = rememberSaveableStateHolder()
                var libraryTabReselectSignal by remember { mutableIntStateOf(0) }

                var closeMixSignal by remember { mutableIntStateOf(0) }
                var sessionRestored by remember { mutableStateOf(false) }
                val hasSessionToRestore = remember(
                    initialTabKey,
                    initialQuickPlaylist,
                    initialOpenedPlaylist,
                    initialLastTrackUri,
                    initialLastSongId
                ) {
                    !initialTabKey.isNullOrBlank() ||
                            !initialQuickPlaylist.isNullOrBlank() ||
                            !initialOpenedPlaylist.isNullOrBlank() ||
                            !initialLastSongId.isNullOrBlank() ||
                            !initialLastTrackUri.isNullOrBlank()
                }
                var isRestoringSession by remember { mutableStateOf(hasSessionToRestore) }

                var selectedQuickPlaylist by rememberSaveable { mutableStateOf<String?>(initialQuickPlaylist) }
                var openedPlaylist by rememberSaveable { mutableStateOf<String?>(initialOpenedPlaylist) }

                var currentPlayingUri by remember { mutableStateOf<String?>(null) }
                var currentPlayingPlaylist by rememberSaveable { mutableStateOf<String?>(initialLastPlaylist) }
                var currentPlayingPlaylistItemKey by rememberSaveable { mutableStateOf<String?>(null) }
                val currentPlayingSongId = remember(currentPlayingUri) {
                    resolveSessionSongIdFromTrackUri(currentPlayingUri)
                }
                var isPlaying by remember { mutableStateOf(false) }
                var parsedLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
                var lyricsLoading by remember { mutableStateOf(false) }

                var currentPlayToken by remember { mutableStateOf(0L) }
                var playlistTapPlayJob by remember { mutableStateOf<Job?>(null) }
                var playlistSessionWriteJob by remember { mutableStateOf<Job?>(null) }
                var lastPlaylistTapStartedAtMs by remember { mutableStateOf(0L) }
                var currentTrackGainDb by remember { mutableStateOf(DEFAULT_TRACK_GAIN_DB) }
                var currentLyricsColor by remember { mutableStateOf(Color.White) }
                var refreshKey by remember { mutableStateOf(0) }

                var isNotesOpen by remember { mutableStateOf(false) }
                var isFillerSettingsOpen by remember { mutableStateOf(false) }

                var currentTrackTempo by remember { mutableStateOf(1f) }
                var currentTrackPitchSemi by remember { mutableStateOf(0) }
                var trackTempoPersistJob by remember { mutableStateOf<Job?>(null) }
                var pendingTempoPersistRequest by remember { mutableStateOf<Pair<String, Float>?>(null) }
                var trackPitchPersistJob by remember { mutableStateOf<Job?>(null) }
                var pendingPitchPersistRequest by remember { mutableStateOf<Pair<String, Int>?>(null) }
                var isMoreMenuOpen by remember { mutableStateOf(false) }
                var openNotesSignal by remember { mutableStateOf(0) }
                var openPrompterSignal by remember { mutableIntStateOf(0) }
                var chainQueue by remember { mutableStateOf<List<String>>(emptyList()) }
                var chainIndex by remember { mutableIntStateOf(-1) }
                var isChaining by remember { mutableStateOf(false) }
                var chainPlaylist by remember { mutableStateOf<String?>(null) }
                var backingEndedSignal by remember { mutableIntStateOf(0) }
                var trimStopJob by remember { mutableStateOf<Job?>(null) }
                var trimAppliedForThisTrack by remember { mutableStateOf(false) }
                val nextTrack by PlaybackCoordinator.nextTrack.collectAsState()
                val nextChainedUri = remember(isChaining, chainIndex, chainQueue) {
                    if (!isChaining) null else nextPlayableUriAfter(chainQueue, chainIndex)
                }
                var isGlobalMixOpen by remember { mutableStateOf(false) }
                var playerMasterLevel by remember { mutableStateOf(1f) }
                var djMasterLevel by remember { mutableStateOf(1f) }
                var fillerMasterLevel by remember { mutableStateOf(0.6f) }

                var isMixerPreviewOpen by remember { mutableStateOf(false) }
                var textPrompterId by remember { mutableStateOf<String?>(null) }

                // ✅ overlay states
                var isSearchOpen by remember { mutableStateOf(false) }
                var playlistSearchToggleSignal by remember { mutableIntStateOf(0) }
                var librarySearchToggleSignal by remember { mutableIntStateOf(0) }

                // ✅ MODE de recherche (PLAYER ou DJ)
                var searchMode by remember { mutableStateOf(SearchMode.PLAYER) }

                // ✅ Index pour SearchScreen
                var indexAll by remember { mutableStateOf<List<LibraryIndexCache.CachedEntry>>(emptyList()) }
                LaunchedEffect(smpCacheRefreshTick) {
                    smpSongsById = withContext(Dispatchers.IO) {
                        smpLibraryScanner.listSongs().associateBy { it.id }
                    }
                }
                LaunchedEffect(savedRoot, hasSetupPerm, isInternalMode, shouldShowSetup, configInitDoneForRoot) {
                    val rootKey = savedRoot?.toString()
                    val canUseStorage = rootKey != null &&
                        !shouldShowSetup &&
                        (isInternalMode || hasSetupPerm) &&
                        configInitDoneForRoot == rootKey

                    Log.i(
                        "SMP_TRACE",
                        "step=rebuild_gate rootKey=$rootKey savedRoot=$savedRoot canUseStorage=$canUseStorage shouldShowSetup=$shouldShowSetup hasSetupPerm=$hasSetupPerm isInternalMode=$isInternalMode configInitDoneForRoot=$configInitDoneForRoot"
                    )

                    if (!canUseStorage) {
                        Log.i("SMP_TRACE", "step=rebuild_gate_skip rootKey=$rootKey reason=storage_not_ready")
                        return@LaunchedEffect
                    }

                    if (smpUserRebuildAttemptedForRoot == rootKey) {
                        Log.i("SMP_TRACE", "step=rebuild_gate_skip rootKey=$rootKey reason=already_attempted")
                        return@LaunchedEffect
                    }
                    smpUserRebuildAttemptedForRoot = rootKey

                    val runtimeSongs = withContext(Dispatchers.IO) {
                        smpLibraryScanner.listSongs()
                    }
                    val runtimeSongIds = runtimeSongs.map { it.id }.sorted()
                    Log.i(
                        "SMP_TRACE",
                        "step=runtime_before_sync rootKey=$rootKey count=${runtimeSongs.size} songIds=${runtimeSongIds.joinToString(prefix = "[", postfix = "]", limit = 20, truncated = "...")}"
                    )

                    if (runtimeSongs.isEmpty()) {
                        Log.i("SMP_TRACE", "step=global_rebuild_mode rootKey=$rootKey")
                        val userArchives = withContext(Dispatchers.IO) {
                            smpUserArchiveRebuilder.listUserArchiveUris()
                        }
                        if (userArchives.isEmpty()) {
                            Log.i("SMP_REBUILD", "step=skip_no_user_archives root=$rootKey")
                            Log.i("SMP_TRACE", "step=global_rebuild_skip rootKey=$rootKey reason=no_archives")
                            return@LaunchedEffect
                        }

                        Log.i(
                            "SMP_REBUILD",
                            "step=start root=$rootKey archiveCount=${userArchives.size}"
                        )

                        val rebuildResult = withContext(Dispatchers.IO) {
                            smpUserArchiveRebuilder.rebuildFromUserArchives(userArchives)
                        }

                        val rebuiltSongs = withContext(Dispatchers.IO) {
                            smpLibraryScanner.listSongs()
                        }
                        smpSongsById = rebuiltSongs.associateBy { it.id }
                        Log.i(
                            "SMP_TRACE",
                            "step=runtime_after_global_rebuild rootKey=$rootKey count=${rebuiltSongs.size} songIds=${rebuiltSongs.map { it.id }.sorted().joinToString(prefix = "[", postfix = "]", limit = 20, truncated = "...")}"
                        )

                        if (rebuildResult.importedCount > 0) {
                            smpCacheRefreshTick++
                        }

                        Log.i(
                            "SMP_REBUILD",
                            "step=done root=$rootKey archives=${rebuildResult.discoveredCount} imported=${rebuildResult.importedCount} failed=${rebuildResult.failedCount} runtimeCount=${rebuiltSongs.size}"
                        )
                        return@LaunchedEffect
                    }

                    Log.i("SMP_TRACE", "step=partial_sync_mode rootKey=$rootKey runtimeCount=${runtimeSongs.size}")
                    val runtimeSongIdsSet = runtimeSongIds.toSet()
                    val archiveCandidates = withContext(Dispatchers.IO) {
                        smpUserArchiveRebuilder.listUserArchiveCandidates()
                    }
                    if (archiveCandidates.isEmpty()) {
                        Log.i(
                            "SMP_REBUILD",
                            "step=partial_skip_no_user_archives root=$rootKey runtimeCount=${runtimeSongs.size}"
                        )
                        Log.i("SMP_TRACE", "step=partial_sync_skip rootKey=$rootKey reason=no_archives")
                        return@LaunchedEffect
                    }

                    val partialPlan = SmpUserArchiveRebuilder.buildPartialSyncPlan(
                        runtimeSongIds = runtimeSongIdsSet,
                        candidates = archiveCandidates
                    )
                    Log.i(
                        "SMP_TRACE",
                        "step=partial_plan_summary rootKey=$rootKey archiveCount=${archiveCandidates.size} importCount=${partialPlan.importCount} skippedInvalid=${partialPlan.skippedInvalidArchives.size} skippedDuplicate=${partialPlan.skippedDuplicateSongIds.size}"
                    )

                    partialPlan.skippedInvalidArchives.forEach { archiveUri ->
                        Log.i(
                            "SMP_REBUILD",
                            "step=partial_skip_invalid_id root=$rootKey uri=$archiveUri"
                        )
                    }
                    partialPlan.skippedDuplicateSongIds.forEach { songId ->
                        Log.i(
                            "SMP_REBUILD",
                            "step=partial_skip_duplicate_id root=$rootKey songId=$songId"
                        )
                    }

                    if (partialPlan.importCount == 0) {
                        Log.i(
                            "SMP_REBUILD",
                            "step=partial_done root=$rootKey archives=${archiveCandidates.size} imported=0 failed=0 skippedInvalid=${partialPlan.skippedInvalidArchives.size} skippedDuplicate=${partialPlan.skippedDuplicateSongIds.size} runtimeCount=${runtimeSongs.size}"
                        )
                        return@LaunchedEffect
                    }

                    Log.i(
                        "SMP_REBUILD",
                        "step=partial_sync_start root=$rootKey archiveCount=${archiveCandidates.size} missingCount=${partialPlan.importCount} runtimeCount=${runtimeSongs.size}"
                    )
                    partialPlan.archivesToImport.forEach { candidate ->
                        Log.i(
                            "SMP_REBUILD",
                            "step=partial_import root=$rootKey songId=${candidate.stableSongId} uri=${candidate.archiveUri}"
                        )
                    }

                    val rebuildResult = withContext(Dispatchers.IO) {
                        smpUserArchiveRebuilder.rebuildFromUserArchives(
                            partialPlan.archivesToImport.map { it.archiveUri }
                        )
                    }

                    val rebuiltSongs = withContext(Dispatchers.IO) {
                        smpLibraryScanner.listSongs()
                    }
                    smpSongsById = rebuiltSongs.associateBy { it.id }
                    Log.i(
                        "SMP_TRACE",
                        "step=runtime_after_partial_sync rootKey=$rootKey count=${rebuiltSongs.size} songIds=${rebuiltSongs.map { it.id }.sorted().joinToString(prefix = "[", postfix = "]", limit = 20, truncated = "...")}"
                    )

                    if (rebuildResult.importedCount > 0) {
                        smpCacheRefreshTick++
                    }

                    Log.i(
                        "SMP_REBUILD",
                        "step=partial_done root=$rootKey archives=${archiveCandidates.size} imported=${rebuildResult.importedCount} failed=${rebuildResult.failedCount} skippedInvalid=${partialPlan.skippedInvalidArchives.size} skippedDuplicate=${partialPlan.skippedDuplicateSongIds.size} runtimeCount=${rebuiltSongs.size}"
                    )
                }
                LaunchedEffect(Unit) {
                    mark("compose.LibraryIndexCache.load(initial):before")
                    val loaded = withContext(Dispatchers.IO) {
                        LibraryIndexCache.load(ctx) ?: emptyList()
                    }
                    indexAll = loaded
                    mark("compose.LibraryIndexCache.load(initial):after size=${loaded.size}")
                }
                LaunchedEffect(refreshKey) {
                    mark("compose.LibraryIndexCache.load(refresh):before refreshKey=$refreshKey")
                    indexAll = withContext(Dispatchers.IO) {
                        LibraryIndexCache.load(ctx) ?: emptyList()
                    }
                    mark("compose.LibraryIndexCache.load(refresh):after size=${indexAll.size}")
                }
                val historyRepository = remember(ctx) { HistoryRepository.getInstance(ctx) }

                data class TrimConfig(
                    val key: String,
                    val store: String,
                    val entryMs: Long,
                    val exitMs: Long?,
                    val mode: String
                )

                fun cancelTrimWatcher() {
                    trimStopJob?.cancel()
                    trimStopJob = null
                }

                fun resolveTrimConfig(requestedUri: String, activeUri: String?): TrimConfig {
                    val candidates = buildList {
                        add(requestedUri)
                        if (!activeUri.isNullOrBlank() && activeUri != requestedUri) add(activeUri)
                    }

                    val editSound = candidates.asSequence()
                        .mapNotNull { candidate -> EditSoundPrefs.resolveCached(Uri.parse(candidate)) }
                        .firstOrNull()
                    val legacyCandidate = if (editSound == null) {
                        candidates.asSequence()
                            .mapNotNull { candidate ->
                                legacyTrimByUri[candidate]?.let { edit -> candidate to edit }
                            }
                            .firstOrNull()
                    } else {
                        null
                    }

                    val store = when {
                        editSound != null -> "EditSoundPrefs"
                        legacyCandidate != null -> "EditPrefs"
                        else -> "none"
                    }
                    val key = when {
                        editSound != null -> editSound.key
                        legacyCandidate != null -> legacyCandidate.first
                        !activeUri.isNullOrBlank() -> runCatching {
                            EditSoundPrefs.trimKeyForUri(Uri.parse(activeUri))
                        }.getOrDefault(activeUri)
                        else -> runCatching {
                            EditSoundPrefs.trimKeyForUri(Uri.parse(requestedUri))
                        }.getOrDefault(requestedUri)
                    }
                    val rawEntry = (
                            editSound?.info?.startMs?.toLong()
                                ?: legacyCandidate?.second?.startMs
                                ?: 0L
                            ).coerceAtLeast(0L)
                    val rawExit = (
                            editSound?.info?.endMs?.toLong()
                                ?: legacyCandidate?.second?.endMs
                                ?: 0L
                            ).coerceAtLeast(0L)

                    if (rawExit > 0L && rawExit <= rawEntry) {
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                "TRIM",
                                "TRIM invalid => disabled key=$key uri=${activeUri ?: requestedUri} entryMs=$rawEntry exitMs=$rawExit store=$store"
                            )
                        }
                        return TrimConfig(
                            key = key,
                            store = store,
                            entryMs = 0L,
                            exitMs = null,
                            mode = "none"
                        )
                    }

                    val entryMs = rawEntry
                    val exitMs = rawExit.takeIf { it > 0L }
                    val mode = if (entryMs > 0L || exitMs != null) "seek-stop" else "none"
                    return TrimConfig(
                        key = key,
                        store = store,
                        entryMs = entryMs,
                        exitMs = exitMs,
                        mode = mode
                    )
                }

                val onEnded = rememberUpdatedState {
                    cancelTrimWatcher()
                    isPlaying = false
                    LightCueDispatcher.resetGlobal()
                    PlaybackCoordinator.onPlayerStop()
                    backingEndedSignal++
                }

                DisposableEffect(exoPlayer) {
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) onEnded.value.invoke()
                        }
                    }
                    exoPlayer.addListener(listener)
                    onDispose { exoPlayer.removeListener(listener) }
                }

                PlaybackCoordinator.stopPlayer = {
                    cancelTrimWatcher()
                    runCatching { exoPlayer.pause() }
                    isPlaying = false
                    LightCueDispatcher.resetGlobal()
                    PlaybackCoordinator.onPlayerStop()
                }
                PlaybackCoordinator.stopDj = {
                    runCatching { DjEngine.stopDj() }
                    PlaybackCoordinator.onDjStop()
                }
                PlaybackCoordinator.stopFiller = {
                    runCatching { FillerSoundManager.fadeOutAndStop(200) }
                    PlaybackCoordinator.onFillerStop()
                }

                fun clampTrackDb(db: Int): Int = db.coerceIn(MIN_TRACK_DB, MAX_TRACK_DB)

                fun stopChainPlayback() {
                    isChaining = false
                    chainQueue = emptyList()
                    chainIndex = -1
                    chainPlaylist = null
                }

                fun applyTempoAndPitchToPlayer(speed: Float, pitchSemi: Int) {
                    val safeSpeed = speed.coerceIn(0.5f, 2.0f)
                    val semiClamped = pitchSemi.coerceIn(-6, 6)
                    val pitchFactor = 2f.pow(semiClamped / 12f)

                    android.util.Log.d(
                        "AUDIO_TS",
                        "apply(REQ,MainActivity.applyTempoAndPitchToPlayer) " +
                                "inSpeed=$speed inSemi=$pitchSemi => safeSpeed=$safeSpeed semi=$semiClamped pitchFactor=$pitchFactor"
                    )

                    AudioEngine.setSpeedPitch(
                        speed = safeSpeed,
                        pitch = pitchFactor,
                        reason = "MainActivity.applyTempoAndPitchToPlayer"
                    )
                }

                fun persistCurrentUiSession(reason: String, tabOverride: BottomTab? = null) {
                    val tab = tabOverride ?: selectedTab
                    latestSessionSnapshot = SessionSnapshot(
                        tabKey = tabKeyOf(tab),
                        quickPlaylist = selectedQuickPlaylist,
                        openedPlaylist = openedPlaylist,
                        currentPlayingUri = currentPlayingUri,
                        currentPlayingPlaylist = currentPlayingPlaylist
                    )
                    persistSession(reason = reason)
                }

                fun setTabAndPersist(tab: BottomTab, reason: String) {
                    selectedTab = tab
                    persistCurrentUiSession(reason = reason, tabOverride = tab)
                }

                fun setQuickPlaylistAndPersist(name: String?, reason: String) {
                    selectedQuickPlaylist = name
                    persistCurrentUiSession(reason = reason)
                }

                fun setOpenedPlaylistAndPersist(name: String?, reason: String) {
                    openedPlaylist = name
                    persistCurrentUiSession(reason = reason)
                }

                fun armPlaylistPlaybackState(
                    playlistName: String?,
                    playbackUri: String,
                    playlistItemKey: String? = null
                ) {
                    val cleanPlaylistName = playlistName?.trim().takeUnless { it.isNullOrEmpty() }
                    val currentItemKey = playlistItemKey
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { cleanKey ->
                            canonicalPlaylistPlaybackKey(
                                playlistItemKey = cleanKey,
                                playbackUri = playbackUri
                            )
                        }
                    if (cleanPlaylistName == null) {
                        currentPlayingPlaylistItemKey = null
                        PlaylistRepository.clearNowPlaying()
                        return
                    }
                    currentPlayingPlaylistItemKey = currentItemKey
                    PlaylistRepository.setNowPlaying(
                        cleanPlaylistName,
                        canonicalPlaylistPlaybackKey(
                            playlistItemKey = playlistItemKey,
                            playbackUri = playbackUri
                        )
                    )
                }

                suspend fun playWithCrossfadeInternal(
                    uriString: String,
                    playlistName: String?,
                    playlistItemKey: String? = null
                ) {
                    val backingTitle = TitleAliasesStore.getTitleForTrack(ctx, uriString)
                        ?: indexAll.firstOrNull { it.uriString == uriString }?.name
                        ?: Uri.parse(uriString).lastPathSegment
                        ?: HistoryRepository.UNTITLED_FALLBACK

                    LyricsPerf.mark(
                        uriString,
                        "play_internal_start",
                        "playlistName=$playlistName"
                    )
                    PlaybackCoordinator.onPlayerStart()
                    currentPlayingUri = uriString
                    currentPlayingPlaylist = playlistName
                    embeddedLyricsListener.reset()
                    armPlaylistPlaybackState(
                        playlistName = playlistName,
                        playbackUri = uriString,
                        playlistItemKey = playlistItemKey
                    )

                    playlistSessionWriteJob?.cancel()
                    playlistSessionWriteJob = scope.launch(Dispatchers.IO) {
                        try {
                            SessionPrefs.saveLastSession(
                                context = ctx,
                                trackUri = uriString,
                                playlistName = playlistName,
                                songId = resolveSessionSongIdFromTrackUri(uriString)
                            )
                        } catch (_: CancellationException) {
                            // coalesced on rapid taps
                        }
                    }
                    runCatching { FillerSoundManager.fadeOutAndStop(400) }

                    val myToken = currentPlayToken + 1
                    currentPlayToken = myToken
                    trimAppliedForThisTrack = false
                    Log.d(
                        SMP_PLAY_TRACE_TAG,
                        "PLAY_INTERNAL requestUri=$uriString playlist=$playlistName token=$myToken currentMedia=${exoPlayer.currentMediaItem?.localConfiguration?.uri}"
                    )

                    val (loadedTrackGainDb, loadedTempo, loadedPitchSemi) = withContext(Dispatchers.IO) {
                        Triple(
                            clampTrackDb(TrackVolumePrefs.getDb(ctx, uriString) ?: DEFAULT_TRACK_GAIN_DB),
                            TrackTempoPrefs.getTempo(ctx, uriString) ?: 1f,
                            TrackPitchPrefs.getSemi(ctx, uriString) ?: 0
                        )
                    }
                    currentTrackGainDb = loadedTrackGainDb
                    currentTrackTempo = loadedTempo
                    currentTrackPitchSemi = loadedPitchSemi

                    var lyricsResolveSeq = 0
                    val result = runCatching {
                        cancelTrimWatcher()
                        AudioEngine.reapplyMixNow()

                        exoCrossfadePlay(
                            context = ctx,
                            exoPlayer = exoPlayer,
                            embeddedLyricsListener = embeddedLyricsListener,
                            uriString = uriString,
                            playlistName = playlistName,
                            playToken = myToken,
                            getCurrentToken = { currentPlayToken },
                            onLyricsLoaded = { embeddedOrNull ->
                                if (embeddedOrNull == null) {
                                    LyricsPerf.mark(
                                        uriString,
                                        "embedded_callback",
                                        "token=$myToken embedded=false"
                                    )
                                    if (BuildConfig.DEBUG) {
                                        Log.d(
                                            "PERF_LYRICS",
                                            "embedded_none uri=$uriString token=$myToken"
                                        )
                                    }
                                } else {
                                    lyricsResolveSeq += 1
                                    val seq = lyricsResolveSeq
                                    LyricsPerf.mark(
                                        uriString,
                                        "embedded_callback",
                                        "token=$myToken embedded=true seq=$seq len=${embeddedOrNull.length}"
                                    )
                                    if (BuildConfig.DEBUG) {
                                        Log.d(
                                            "PERF_LYRICS",
                                            "start uri=$uriString token=$myToken seq=$seq embeddedObj=${System.identityHashCode(embeddedOrNull)}"
                                        )
                                    }
                                    scope.launch {
                                        val tLyrics = SystemClock.elapsedRealtime()
                                        val resolved = withContext(Dispatchers.IO) {
                                            LyricsResolver.resolveLyrics(ctx, uriString, embeddedOrNull)
                                        }
                                        if (currentPlayToken != myToken) {
                                            if (BuildConfig.DEBUG) {
                                                Log.d(
                                                    "PERF_LYRICS",
                                                    "drop(token) uri=$uriString token=$myToken seq=$seq currentToken=$currentPlayToken"
                                                )
                                            }
                                            return@launch
                                        }
                                        if (seq != lyricsResolveSeq) {
                                            if (BuildConfig.DEBUG) {
                                                Log.d(
                                                    "PERF_LYRICS",
                                                    "drop(seq) uri=$uriString token=$myToken seq=$seq currentSeq=$lyricsResolveSeq"
                                                )
                                            }
                                            return@launch
                                        }
                                        if (resolved.isNotEmpty() && parsedLines.isEmpty()) {
                                            parsedLines = resolved
                                            val cacheScopeKey = savedRoot?.toString()
                                            LyricsMemoryCache.updateScope(cacheScopeKey)
                                            LyricsMemoryCache.put(
                                                trackUriString = uriString,
                                                parsedLines = resolved,
                                                resolvedLyricsFileName = null,
                                                source = "EMBEDDED_FALLBACK",
                                                sourceType = "embedded",
                                                debugPath = null
                                            )
                                            LyricsPerf.mark(
                                                uriString,
                                                "cache_store_embedded_fallback",
                                                "lines=${resolved.size}"
                                            )
                                            if (BuildConfig.DEBUG) {
                                                Log.d(
                                                    "PERF_LYRICS",
                                                    "ui_fallback_apply uri=$uriString token=$myToken seq=$seq lines=${resolved.size}"
                                                )
                                            }
                                        } else if (BuildConfig.DEBUG) {
                                            Log.d(
                                                "PERF_LYRICS",
                                                "ui_fallback_skip uri=$uriString token=$myToken seq=$seq resolvedLines=${resolved.size} existingLines=${parsedLines.size}"
                                            )
                                        }
                                        if (BuildConfig.DEBUG) {
                                            Log.d(
                                                "PERF_LYRICS",
                                                "done uri=$uriString token=$myToken seq=$seq ms=${SystemClock.elapsedRealtime() - tLyrics} lines=${resolved.size}"
                                            )
                                        }
                                    }
                                }
                            },
                            onStart = {
                                isPlaying = true
                                val activeUri = exoPlayer.currentMediaItem
                                    ?.localConfiguration
                                    ?.uri
                                    ?.toString()
                                    ?: uriString
                                Log.d(
                                    SMP_PLAY_TRACE_TAG,
                                    "PLAYER_ON_START requestedUri=$uriString activeUri=$activeUri playlist=$playlistName token=$myToken playWhenReady=${exoPlayer.playWhenReady} isPlaying=${exoPlayer.isPlaying} state=${exoPlayer.playbackState}"
                                )
                                LyricsPerf.mark(
                                    uriString,
                                    "player_on_start",
                                    "token=$myToken activeUri=$activeUri"
                                )
                                val trimConfig = resolveTrimConfig(
                                    requestedUri = uriString,
                                    activeUri = activeUri
                                )
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "TRIM",
                                        "load key=${trimConfig.key} uri=$activeUri entryMs=${trimConfig.entryMs} exitMs=${trimConfig.exitMs ?: 0L} mode=${trimConfig.mode} store=${trimConfig.store}"
                                    )
                                }
                                if (!trimAppliedForThisTrack) {
                                    if (trimConfig.entryMs > 0L) {
                                        runCatching { exoPlayer.seekTo(trimConfig.entryMs) }
                                    }
                                    trimAppliedForThisTrack = true
                                }
                                val trimExitMs = trimConfig.exitMs
                                if (trimConfig.mode == "seek-stop" && trimExitMs != null && trimExitMs > 0L) {
                                    trimStopJob = scope.launch {
                                        while (currentPlayToken == myToken) {
                                            val positionMs = runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
                                            if (positionMs >= trimExitMs) {
                                                runCatching { exoPlayer.pause() }
                                                onEnded.value.invoke()
                                                return@launch
                                            }
                                            delay(40L)
                                        }
                                    }
                                }

                                AudioEngine.applyTrackGainDb(currentTrackGainDb)
                                applyTempoAndPitchToPlayer(currentTrackTempo, currentTrackPitchSemi)
                                scope.launch(Dispatchers.IO) {
                                    runCatching {
                                        historyRepository.logPlay(
                                            source = PlaySource.BACKING,
                                            title = backingTitle,
                                            artist = null,
                                            uri = uriString
                                        )
                                    }
                                }
                            },
                            onError = {
                                cancelTrimWatcher()
                                val activeUri = exoPlayer.currentMediaItem
                                    ?.localConfiguration
                                    ?.uri
                                    ?.toString()
                                val nextArmed = PlaybackCoordinator.peekNextTrack() != null
                                val durMs = runCatching { exoPlayer.duration }.getOrDefault(C.TIME_UNSET)
                                val posMs = runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
                                val nearEnd =
                                    durMs > 0L &&
                                    durMs != C.TIME_UNSET &&
                                    posMs >= (durMs - 1500L).coerceAtLeast(0L)
                                val treatAsEnded = nextArmed && nearEnd
                                Log.d(
                                    SMP_PLAY_TRACE_TAG,
                                    "PLAYER_ON_ERROR requestedUri=$uriString activeUri=$activeUri playlist=$playlistName token=$myToken playWhenReady=${exoPlayer.playWhenReady} isPlaying=${exoPlayer.isPlaying} state=${exoPlayer.playbackState} pos=$posMs dur=$durMs nextArmed=$nextArmed treatAsEnded=$treatAsEnded"
                                )

                                if (BuildConfig.DEBUG) {
                                    if (treatAsEnded) {
                                        Log.d(
                                            "NEXT",
                                            "NEXT treat error as ended nearEnd pos=$posMs dur=$durMs uri=$uriString"
                                        )
                                    } else {
                                        Log.d(
                                            "NEXT",
                                            "NEXT error no fallback pos=$posMs dur=$durMs next=$nextArmed"
                                        )
                                    }
                                }

                                if (treatAsEnded) {
                                    onEnded.value.invoke()
                                } else {
                                    isPlaying = false
                                    LightCueDispatcher.resetGlobal()
                                    PlaybackCoordinator.onPlayerStop()
                                }
                            }
                        )
                    }

                    if (result.isFailure) {
                        Log.e(
                            SMP_PLAY_TRACE_TAG,
                            "PLAY_INTERNAL exception uri=$uriString playlist=$playlistName token=$myToken",
                            result.exceptionOrNull()
                        )
                        cancelTrimWatcher()
                        runCatching {
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                        }
                        isPlaying = false
                        LightCueDispatcher.resetGlobal()
                        PlaybackCoordinator.onPlayerStop()
                    }

                    selectedTab = BottomTab.Player
                    latestSessionSnapshot = SessionSnapshot(
                        tabKey = TAB_PLAYER,
                        quickPlaylist = selectedQuickPlaylist,
                        openedPlaylist = openedPlaylist,
                        currentPlayingUri = currentPlayingUri,
                        currentPlayingPlaylist = currentPlayingPlaylist
                    )
                    persistSession(reason = "playStart")
                }

                val playWithCrossfade: (String, String?, String?) -> Unit = { uriString, playlistName, playlistItemKey ->
                    scope.launch {
                        playWithCrossfadeInternal(
                            uriString = uriString,
                            playlistName = playlistName,
                            playlistItemKey = playlistItemKey
                        )
                    }
                }

                fun resolveSmpAudioTarget(
                    songId: String,
                    playlistName: String?,
                    showToastOnFailure: Boolean = false
                ): PlaybackRouter.Target.Audio? {
                    val cachedSong = smpSongsById[songId]
                    Log.d(
                        SMP_PLAY_TRACE_TAG,
                        "RESOLVE_SMP start songId=$songId playlist=$playlistName cacheHit=${cachedSong != null}"
                    )
                    val song = cachedSong ?: run {
                        val scannedSong = runCatching {
                            smpLibraryScanner.findSongById(songId)
                        }.getOrElse { error ->
                            Log.w("SMP", "Lecture SMP impossible: scan échoué pour songId=$songId", error)
                            null
                        }
                        if (scannedSong != null) {
                            smpSongsById = smpSongsById + (scannedSong.id to scannedSong)
                            Log.i(
                                "SMP",
                                "Lecture SMP: cache resynchronisé depuis le scanner songId=${scannedSong.id} title=${scannedSong.title}"
                            )
                        }
                        scannedSong
                    }
                    if (song == null) {
                        Log.d(
                            SMP_PLAY_TRACE_TAG,
                            "RESOLVE_SMP miss songId=$songId playlist=$playlistName"
                        )
                        Log.w("SMP", "Lecture SMP impossible: songId introuvable=$songId playlist=$playlistName")
                        if (showToastOnFailure) {
                            Toast.makeText(ctx, "Morceau SMP introuvable", Toast.LENGTH_SHORT).show()
                        }
                        return null
                    }

                    val audioPath = song.audioPath
                    if (audioPath.isNullOrBlank()) {
                        Log.d(
                            SMP_PLAY_TRACE_TAG,
                            "RESOLVE_SMP no_audio songId=${song.id} title=${song.title}"
                        )
                        Log.w("SMP", "Lecture SMP impossible sans audio: songId=${song.id} title=${song.title}")
                        if (showToastOnFailure) {
                            Toast.makeText(ctx, "Ce SMP ne contient pas d'audio", Toast.LENGTH_SHORT).show()
                        }
                        return null
                    }

                    val audioFile = File(audioPath)
                    Log.d(
                        SMP_PLAY_TRACE_TAG,
                        "RESOLVE_SMP file songId=${song.id} audioPath=$audioPath exists=${audioFile.exists()} isFile=${audioFile.isFile} canRead=${audioFile.canRead()}"
                    )
                    if (!audioFile.isFile) {
                        Log.w(
                            "SMP",
                            "Lecture SMP impossible: fichier audio absent songId=${song.id} path=${audioFile.absolutePath}"
                        )
                        if (showToastOnFailure) {
                            Toast.makeText(ctx, "Audio SMP introuvable", Toast.LENGTH_SHORT).show()
                        }
                        return null
                    }

                    val resolvedUri = Uri.fromFile(audioFile).toString()
                    Log.d(
                        SMP_PLAY_TRACE_TAG,
                        "RESOLVE_SMP ok songId=${song.id} resolvedUri=$resolvedUri playlist=$playlistName"
                    )
                    runCatching {
                        TitleAliasesStore.setTitleForTrack(ctx, resolvedUri, song.title)
                    }.onFailure { error ->
                        Log.w(
                            "SMP",
                            "Alias SMP non enregistré: songId=${song.id} uri=$resolvedUri",
                            error
                        )
                    }

                    Log.i(
                        "SMP",
                        "Lecture SMP résolue: songId=${song.id} title=${song.title} uri=$resolvedUri playlist=$playlistName"
                    )
                    return PlaybackRouter.Target.Audio(resolvedUri, playlistName)
                }

                fun resolveAudioTarget(
                    target: PlaybackRouter.Target,
                    showToastOnFailure: Boolean = false
                ): PlaybackRouter.Target.Audio? {
                    return when (target) {
                        is PlaybackRouter.Target.Audio -> target
                        is PlaybackRouter.Target.Smp -> resolveSmpAudioTarget(
                            songId = target.songId,
                            playlistName = target.playlist,
                            showToastOnFailure = showToastOnFailure
                        )
                        is PlaybackRouter.Target.Prompter,
                        is PlaybackRouter.Target.Unknown -> null
                    }
                }

                fun playChainFrom(startIndex: Int): Boolean {
                    if (!isChaining) return false
                    var idx = startIndex
                    while (idx in chainQueue.indices) {
                        val playableIndex = nextPlayableIndexAtOrAfter(chainQueue, idx) ?: return false
                        when (val target = PlaybackRouter.resolve(chainQueue[playableIndex], chainPlaylist)) {
                            is PlaybackRouter.Target.Audio,
                            is PlaybackRouter.Target.Smp -> {
                                val resolvedTarget = resolveAudioTarget(target)
                                if (resolvedTarget == null) {
                                    idx = playableIndex + 1
                                    continue
                                }
                                chainIndex = playableIndex
                                playWithCrossfade(
                                    resolvedTarget.uri,
                                    resolvedTarget.playlist,
                                    chainQueue[playableIndex]
                                )
                                currentPlayingUri = resolvedTarget.uri
                                setQuickPlaylistAndPersist(resolvedTarget.playlist, reason = "chainPlay")
                                currentLyricsColor = Color.White
                                return true
                            }
                            is PlaybackRouter.Target.Prompter -> idx = playableIndex + 1
                            is PlaybackRouter.Target.Unknown -> idx = playableIndex + 1
                        }
                    }
                    return false
                }

                LaunchedEffect(backingEndedSignal) {
                    if (backingEndedSignal <= 0) return@LaunchedEffect

                    val forcedNext = PlaybackCoordinator.peekNextTrack()
                    if (forcedNext != null) {
                        Log.i(
                            "NEXT",
                            "trigger uri=${forcedNext.uri} title=${forcedNext.title} playlist=${forcedNext.playlist}"
                        )
                        val started = when (val target = PlaybackRouter.resolve(forcedNext.uri, forcedNext.playlist)) {
                            is PlaybackRouter.Target.Audio,
                            is PlaybackRouter.Target.Smp -> {
                                val resolvedTarget = resolveAudioTarget(target)
                                if (resolvedTarget == null) {
                                    false
                                } else {
                                    stopChainPlayback()
                                    playWithCrossfade(
                                        resolvedTarget.uri,
                                        resolvedTarget.playlist,
                                        forcedNext.uri
                                    )
                                    currentPlayingUri = resolvedTarget.uri
                                    setQuickPlaylistAndPersist(resolvedTarget.playlist, reason = "nextTrackTrigger")
                                    currentLyricsColor = Color.White
                                    true
                                }
                            }
                            is PlaybackRouter.Target.Prompter -> {
                                Log.w("NEXT", "trigger skipped (prompter id=${target.id})")
                                false
                            }
                            is PlaybackRouter.Target.Unknown -> {
                                Log.w("NEXT", "trigger skipped (unknown uri=${forcedNext.uri})")
                                false
                            }
                        }
                        PlaybackCoordinator.clearNextTrack(
                            reason = if (started) "triggered:naturalEnd" else "invalid:naturalEnd"
                        )
                        if (started) return@LaunchedEffect
                    }

                    if (!isChaining) return@LaunchedEffect
                    if (!playChainFrom(chainIndex + 1)) {
                        stopChainPlayback()
                    }
                }

                // ✅ helper : lancer depuis recherche en mode DJ
                fun playFromSearchInDj(uriString: String) {
                    // On récupère un nom "humain" depuis l'index (sinon fallback)
                    val name = TitleAliasesStore.getTitleForTrack(ctx, uriString)
                        ?: indexAll.firstOrNull { it.uriString == uriString }?.name
                        ?: Uri.parse(uriString).lastPathSegment
                        ?: "Titre"

                    PlaybackCoordinator.onDjStart()
                    DjEngine.selectTrackFromList(uriString, name)

                    setTabAndPersist(BottomTab.Dj, reason = "searchDjPlay")
                }

                LaunchedEffect(Unit) {
                    mark("SessionRestore PhaseA:start hasSession=$hasSessionToRestore")
                    if (hasSessionToRestore) {
                        initialTabKey?.let { selectedTab = tabFromKey(it) }
                        openedPlaylist = initialOpenedPlaylist
                        selectedQuickPlaylist = initialQuickPlaylist ?: initialLastPlaylist
                        isRestoringSession = true
                    } else {
                        isRestoringSession = false
                    }
                    mark(
                        "SessionRestore PhaseA:end tab=${tabKeyOf(selectedTab)} quick=$selectedQuickPlaylist opened=$openedPlaylist restoring=$isRestoringSession"
                    )
                }

                val canUseStorage = isInternalMode || hasSetupPerm
                val rootKey = savedRoot?.toString()
                val playlistsReady = rootKey != null && configInitDoneForRoot == rootKey

                LaunchedEffect(canUseStorage, playlistsReady, rootKey, sessionRestored) {
                    if (sessionRestored) return@LaunchedEffect

                    mark("SessionRestore PhaseB:start canUseStorage=$canUseStorage playlistsReady=$playlistsReady root=$rootKey")
                    if (!canUseStorage || !playlistsReady) return@LaunchedEffect

                    val restoredTabKey = SessionPrefs.getTab(ctx)
                    val restoredQuickPlaylist = SessionPrefs.getQuickPlaylist(ctx)
                    val restoredOpenedPlaylist = SessionPrefs.getOpenedPlaylist(ctx)
                    val restoredLastSession = SessionPrefs.getLastSessionState(ctx)
                    val lastPlaylist = restoredLastSession.playlistName
                    var restoredSongId = restoredLastSession.songId?.takeIf { it.isNotBlank() }
                    val fallbackLastUri = restoredLastSession.trackUri?.takeIf { it.isNotBlank() }
                    val restoredTarget = restoredSongId?.let { songId ->
                        resolveSmpAudioTarget(
                            songId = songId,
                            playlistName = lastPlaylist,
                            showToastOnFailure = false
                        )
                    }
                    val lastUri = restoredTarget?.uri ?: fallbackLastUri
                    if (restoredTarget == null && !restoredSongId.isNullOrBlank() && !fallbackLastUri.isNullOrBlank()) {
                        Log.w(
                            "SMP_TRACE",
                            "RESTORE fallback_to_uri songId=$restoredSongId playlist=$lastPlaylist uri=$fallbackLastUri"
                        )
                    }
                    if (restoredSongId.isNullOrBlank()) {
                        restoredSongId = resolveSessionSongIdFromTrackUri(lastUri)
                    }
                    Log.d(
                        "SMP_TRACE",
                        "RESTORE playlistQuick=$restoredQuickPlaylist playlistOpened=$restoredOpenedPlaylist lastPlaylist=$lastPlaylist songId=$restoredSongId track=$fallbackLastUri resolvedUri=$lastUri source=SessionPrefs"
                    )

                    restoredTabKey?.let { selectedTab = tabFromKey(it) }
                    selectedQuickPlaylist = restoredQuickPlaylist ?: lastPlaylist
                    openedPlaylist = restoredOpenedPlaylist

                    if (!lastUri.isNullOrBlank()) {
                        currentPlayingUri = lastUri
                        currentPlayingPlaylist = lastPlaylist
                        currentPlayingPlaylistItemKey = null

                        val overrideText = withContext(Dispatchers.IO) {
                            LrcStorage.loadForTrack(ctx, lastUri)?.takeIf { it.isNotBlank() }
                        }
                        Log.d(
                            "SMP_TRACE",
                            "RESTORE_LOAD uri=$lastUri playlist=$lastPlaylist loadedLyrics=${!overrideText.isNullOrBlank()} lyricsHash=${overrideText?.hashCode()}"
                        )
                        parsedLines = if (overrideText != null) parseLrc(overrideText) else emptyList()

                        // IMPORTANT:
                        // -5 dB est la valeur par défaut volontaire (headroom).
                        // NE PAS réinitialiser automatiquement si la valeur est 0.
                        // 0 dB est un choix utilisateur valide.
                        currentTrackGainDb = clampTrackDb(
                            TrackVolumePrefs.getDb(ctx, lastUri) ?: DEFAULT_TRACK_GAIN_DB
                        )

                        currentTrackTempo = TrackTempoPrefs.getTempo(ctx, lastUri) ?: 1f
                        currentTrackPitchSemi = TrackPitchPrefs.getSemi(ctx, lastUri) ?: 0
                    }

                    sessionRestored = true
                    isRestoringSession = false
                    mark(
                        "SessionRestore PhaseB:end tab=$restoredTabKey quick=$restoredQuickPlaylist opened=$restoredOpenedPlaylist hasLastUri=${!lastUri.isNullOrBlank()} restoring=$isRestoringSession"
                    )
                }

                SideEffect {
                    latestSessionSnapshot = SessionSnapshot(
                        tabKey = tabKeyOf(selectedTab),
                        quickPlaylist = selectedQuickPlaylist,
                        openedPlaylist = openedPlaylist,
                        currentPlayingUri = currentPlayingUri,
                        currentPlayingPlaylist = currentPlayingPlaylist
                    )
                }

                LaunchedEffect(Unit) {
                    snapshotFlow {
                        SessionSnapshot(
                            tabKey = tabKeyOf(selectedTab),
                            quickPlaylist = selectedQuickPlaylist,
                            openedPlaylist = openedPlaylist,
                            currentPlayingUri = currentPlayingUri,
                            currentPlayingPlaylist = currentPlayingPlaylist
                        )
                    }
                        .distinctUntilChanged()
                        .collect { snapshot ->
                            requestSessionPersist(reason = "snapshotFlow", snapshot = snapshot)
                        }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        cancelTrimWatcher()
                        try { TrackEqEngine.release() } catch (_: Exception) {}
                    }
                }

                val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                val shouldHideBottomBarForPlayerIme =
                    selectedTab is BottomTab.Player && imeBottomPadding > 0.dp

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        if (!shouldHideBottomBarForPlayerIme) {
                            BottomTabsBar(
                                selected = selectedTab,
                                onSelected = { tab ->

                                    // ✅ fermer les overlays quand on change d'onglet
                                    if (tab !is BottomTab.Filler) isFillerSettingsOpen = false
                                    if (tab !is BottomTab.Search) isSearchOpen = false
                                    isMoreMenuOpen = false

                                    // ✅ sortir du prompteur dès qu'on touche la bottom bar
                                    textPrompterId = null

                                    // ✅ sortir de “Mes notes” dès qu'on touche la bottom bar
                                    isNotesOpen = false

                                    // ✅ "Fond sonore" = overlay
                                    if (tab is BottomTab.Filler) {
                                        isFillerSettingsOpen = true
                                    } else {
                                        setTabAndPersist(tab, reason = "bottomTabSelect")
                                    }
                                },
                                onSearchClick = {
                                    // ✅ fermer ce qui doit se fermer quand on ouvre la recherche
                                    textPrompterId = null
                                    isNotesOpen = false
                                    isFillerSettingsOpen = false
                                    isMoreMenuOpen = false

                                    if (selectedTab is BottomTab.QuickPlaylists) {
                                        isSearchOpen = false
                                        playlistSearchToggleSignal++
                                        return@BottomTabsBar
                                    }

                                    if (selectedTab is BottomTab.Library) {
                                        isSearchOpen = false
                                        librarySearchToggleSignal++
                                        return@BottomTabsBar
                                    }

                                    searchMode = when {
                                        selectedTab is BottomTab.Dj ->
                                            SearchMode.DJ

                                        selectedTab is BottomTab.QuickPlaylists &&
                                                !selectedQuickPlaylist.isNullOrBlank() ->
                                            SearchMode.PLAYLIST

                                        else ->
                                            SearchMode.PLAYER
                                    }

                                    isSearchOpen = true
                                },
                                onMoreClick = {
                                    textPrompterId = null
                                    isMoreMenuOpen = true
                                },
                                onPlayerReselect = {
                                    // ✅ C'EST ICI LE FIX :
                                    // même si selectedTab est déjà Player, on demande explicitement au PlayerScreen
                                    // de fermer Track Console et revenir à l'écran lecteur.
                                    closeMixSignal++
                                }
                            )
                        }
                    }
                ) { innerPadding ->


                    val contentModifier = Modifier
                        .padding(innerPadding)
                        .windowInsetsPadding(WindowInsets.ime)

                    if (isMixerPreviewOpen) {
                        MixerHomePreviewScreen(
                            modifier = contentModifier,
                            openNotesSignal = openNotesSignal,
                            onBack = {},
                            onOpenPlayer = {
                                isMixerPreviewOpen = false
                                setTabAndPersist(BottomTab.Player, reason = "mixerPreviewOpenPlayer")
                            },
                            onOpenFondSonore = {
                                isMixerPreviewOpen = false
                                isFillerSettingsOpen = true
                            },
                            onOpenDj = {
                                isMixerPreviewOpen = false
                                setTabAndPersist(BottomTab.Dj, reason = "mixerPreviewOpenDj")
                            },
                            onOpenTuner = {
                                isMixerPreviewOpen = false
                                setTabAndPersist(BottomTab.Tuner, reason = "mixerPreviewOpenTuner")
                            }
                        )
                    } else if (isFillerSettingsOpen) {
                        FillerSoundScreen(
                            context = ctx,
                            onBack = { isFillerSettingsOpen = false }
                        )
                    } else if (isGlobalMixOpen) {
                        GlobalMixScreen(
                            modifier = contentModifier,
                            playerLevel = playerMasterLevel,
                            onPlayerLevelChange = { lvl ->
                                playerMasterLevel = lvl
                                AudioEngine.setPlayerBusLevel(lvl)
                            },
                            djLevel = djMasterLevel,
                            onDjLevelChange = { lvl ->
                                djMasterLevel = lvl
                                DjEngine.setMasterVolume(lvl)
                            },
                            fillerLevel = fillerMasterLevel,
                            onFillerLevelChange = { lvl -> fillerMasterLevel = lvl },
                            onBack = { isGlobalMixOpen = false }
                        )
                    } else {
                        // ✅ overlay PROMPTEUR
                        textPrompterId?.let { tid ->
                            TextPrompterScreen(
                                modifier = contentModifier,
                                songId = tid,
                                onClose = { textPrompterId = null }
                            )
                        } ?: run {
                            tabStateHolder.SaveableStateProvider(
                                key = "tab_${tabKeyOf(selectedTab)}"
                            ) {
                                SmpPreparationNoticeDialog(
                                    show = pendingPlaylistBatchPlan != null,
                                    onDismiss = {
                                        pendingPlaylistBatchPlan = null
                                        pendingPlaylistTrackTarget = null
                                    },
                                    onContinue = { dontShowAgain ->
                                        if (dontShowAgain) {
                                            SmpPreparationNoticePrefs.setShouldShow(ctx, false)
                                        }
                                        val playlistName = pendingPlaylistTrackTarget?.trim()
                                        val plan = pendingPlaylistBatchPlan
                                        pendingPlaylistBatchPlan = null
                                        if (!playlistName.isNullOrEmpty() && plan != null) {
                                            runPlaylistBatchImport(playlistName, plan)
                                        }
                                    }
                                )
                                SmpBatchProgressDialog(
                                    show = playlistBatchProgressVisible,
                                    title = sBatchProgressTitle,
                                    label = playlistBatchProgressLabel,
                                    progress = playlistBatchProgressValue
                                )
                                when (selectedTab) {

                                    is BottomTab.Home -> Box(
                                        modifier = contentModifier.fillMaxSize()
                                    ) {
                                        if (isSmpImportedSongsDialogOpen) {
                                            SmpImportedSongsDialog(
                                                songs = smpImportedSongs,
                                                onDismiss = { isSmpImportedSongsDialogOpen = false },
                                                onSongSelected = { song ->
                                                    scope.launch(Dispatchers.IO) {
                                                        val detail = smpLibraryScanner.readSongDetail(song)
                                                        withContext(Dispatchers.Main) {
                                                            if (detail != null) {
                                                                Log.i(
                                                                    "SMP",
                                                                    "Ouverture fiche SMP: songId=${detail.song.id} title=${detail.song.title} playback=${detail.playback != null}"
                                                                )
                                                                isSmpImportedSongsDialogOpen = false
                                                                selectedSmpImportedSongDetail = detail
                                                            } else {
                                                                Toast.makeText(
                                                                    ctx,
                                                                    "Lecture détail SMP échouée",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        selectedSmpImportedSongDetail?.let { detail ->
                                            SmpImportedSongDetailDialog(
                                                detail = detail,
                                                onDismiss = { selectedSmpImportedSongDetail = null }
                                            )
                                        }
                                        MixerHomePreviewScreen(
                                            modifier = Modifier.fillMaxSize(),
                                            onBack = {},
                                            onOpenPlayer = {
                                                setTabAndPersist(BottomTab.Player, reason = "homeOpenPlayer")
                                            },
                                            onOpenFondSonore = { isFillerSettingsOpen = true },
                                            onOpenDj = {
                                                setTabAndPersist(BottomTab.Dj, reason = "homeOpenDj")
                                            },
                                            onOpenTuner = {
                                                setTabAndPersist(BottomTab.Tuner, reason = "homeOpenTuner")
                                            }
                                        )
                                        if (ENABLE_SMP_DEBUG_HOME_BUTTONS) {
                                            Button(
                                                onClick = {
                                                    scope.launch(Dispatchers.IO) {
                                                        val songs = smpLibraryScanner.listSongs()
                                                        Log.i("SMP", "SMP importés: count=${songs.size}")
                                                        songs.forEach { song ->
                                                            Log.i(
                                                                "SMP",
                                                                "SMP importé: songId=${song.id} title=${song.title} storageFolder=${song.storageFolder}"
                                                            )
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            smpImportedSongs = songs
                                                            smpSongsById = songs.associateBy { it.id }
                                                            isSmpImportedSongsDialogOpen = true
                                                            Toast.makeText(
                                                                ctx,
                                                                "SMP importés: ${songs.size}",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(end = 16.dp, bottom = 72.dp)
                                            ) {
                                                Text("Afficher SMP importés")
                                            }
                                            Button(
                                                onClick = {
                                                    pickSmpFileLauncher.launch(
                                                        arrayOf("application/zip", "application/octet-stream", "*/*")
                                                    )
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(16.dp)
                                            ) {
                                                Text("Tester un fichier .smp")
                                            }
                                        }
                                    }

                                    is BottomTab.Player -> PlayerScreen(
                                        modifier = contentModifier,
                                        exoPlayer = exoPlayer,
                                        closeMixSignal = closeMixSignal,
                                        isPlaying = isPlaying,
                                        onIsPlayingChange = { shouldPlay ->
                                            isPlaying = shouldPlay
                                            if (shouldPlay) exoPlayer.play() else exoPlayer.pause()
                                        },
                                        parsedLines = parsedLines,
                                        lyricsLoading = lyricsLoading,
                                        onParsedLinesChange = { parsedLines = it },
                                        highlightColor = currentLyricsColor,
                                        currentTrackUri = currentPlayingUri,
                                        nextTrackTitle = nextTrack?.title,
                                        currentTrackGainDb = currentTrackGainDb,
                                        onTrackGainChange = { db ->
                                            val safeDb = clampTrackDb(db)
                                            currentTrackGainDb = safeDb
                                            AudioEngine.applyTrackGainDb(safeDb)
                                        },
                                        onTrackGainCommit = { db ->
                                            val safeDb = clampTrackDb(db)
                                            currentTrackGainDb = safeDb
                                            AudioEngine.applyTrackGainDb(safeDb)
                                            currentPlayingUri?.let { sourceUri ->
                                                scope.launch {
                                                    val migration = withContext(Dispatchers.IO) {
                                                        smpAutoMigration.migrateLegacyTrack(sourceUri)
                                                    }
                                                    val targetUri = migration?.trackUriString ?: sourceUri
                                                    withContext(Dispatchers.IO) {
                                                        TrackVolumePrefs.saveDb(ctx, targetUri, safeDb)
                                                    }
                                                    if (migration != null && currentPlayingUri == sourceUri) {
                                                        currentPlayingUri = migration.trackUriString
                                                        smpSongsById = smpSongsById + (migration.song.id to migration.song)
                                                        smpCacheRefreshTick++
                                                    }
                                                }
                                            }
                                        },
                                        tempo = currentTrackTempo,
                                        onTempoChange = { newTempo ->
                                            currentTrackTempo = newTempo
                                            applyTempoAndPitchToPlayer(currentTrackTempo, currentTrackPitchSemi)
                                            currentPlayingUri?.let { sourceUri ->
                                                pendingTempoPersistRequest = sourceUri to newTempo
                                                if (trackTempoPersistJob?.isActive == true) return@PlayerScreen
                                                trackTempoPersistJob = scope.launch {
                                                    val resolvedTargets = mutableMapOf<String, Pair<String, SmpAutoMigrationResult?>>()
                                                    while (true) {
                                                        val request = pendingTempoPersistRequest ?: break
                                                        pendingTempoPersistRequest = null

                                                        val lockedSourceUri = request.first
                                                        val tempoToSave = request.second
                                                        val resolvedTarget = resolvedTargets[lockedSourceUri] ?: run {
                                                            val migration = withContext(Dispatchers.IO) {
                                                                smpAutoMigration.migrateLegacyTrack(lockedSourceUri)
                                                            }
                                                            (migration?.trackUriString ?: lockedSourceUri) to migration
                                                        }.also {
                                                            resolvedTargets[lockedSourceUri] = it
                                                        }

                                                        withContext(Dispatchers.IO) {
                                                            TrackTempoPrefs.saveTempo(ctx, resolvedTarget.first, tempoToSave)
                                                        }

                                                        val migration = resolvedTarget.second
                                                        if (migration != null && currentPlayingUri == lockedSourceUri) {
                                                            currentPlayingUri = migration.trackUriString
                                                            smpSongsById = smpSongsById + (migration.song.id to migration.song)
                                                            smpCacheRefreshTick++
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        pitchSemi = currentTrackPitchSemi,
                                        onPitchSemiChange = { newSemi ->
                                            val clamped = newSemi.coerceIn(-6, 6)
                                            currentTrackPitchSemi = clamped
                                            applyTempoAndPitchToPlayer(currentTrackTempo, currentTrackPitchSemi)
                                            currentPlayingUri?.let { sourceUri ->
                                                pendingPitchPersistRequest = sourceUri to clamped
                                                if (trackPitchPersistJob?.isActive == true) return@PlayerScreen
                                                trackPitchPersistJob = scope.launch {
                                                    val resolvedTargets = mutableMapOf<String, Pair<String, SmpAutoMigrationResult?>>()
                                                    while (true) {
                                                        val request = pendingPitchPersistRequest ?: break
                                                        pendingPitchPersistRequest = null

                                                        val lockedSourceUri = request.first
                                                        val semiToSave = request.second
                                                        val resolvedTarget = resolvedTargets[lockedSourceUri] ?: run {
                                                            val migration = withContext(Dispatchers.IO) {
                                                                smpAutoMigration.migrateLegacyTrack(lockedSourceUri)
                                                            }
                                                            (migration?.trackUriString ?: lockedSourceUri) to migration
                                                        }.also {
                                                            resolvedTargets[lockedSourceUri] = it
                                                        }

                                                        withContext(Dispatchers.IO) {
                                                            TrackPitchPrefs.saveSemi(ctx, resolvedTarget.first, semiToSave)
                                                        }

                                                        val migration = resolvedTarget.second
                                                        if (migration != null && currentPlayingUri == lockedSourceUri) {
                                                            currentPlayingUri = migration.trackUriString
                                                            smpSongsById = smpSongsById + (migration.song.id to migration.song)
                                                            smpCacheRefreshTick++
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        ensureSmpTrackForLyricsSave = { trackUriString ->
                                            smpAutoMigration.migrateLegacyTrack(trackUriString)
                                        },
                                        onTrackPromotedToSmp = { migration: SmpAutoMigrationResult ->
                                            currentPlayingUri = migration.trackUriString
                                            smpSongsById = smpSongsById + (migration.song.id to migration.song)
                                            smpCacheRefreshTick++
                                        },
                                        onRequestShowPlaylist = { selectedTab = BottomTab.QuickPlaylists },
                                        getPositionMs = { exoPlayer.currentPosition },
                                        getDurationMs = { exoPlayer.duration },
                                        seekToMs = { ms -> exoPlayer.seekTo(ms) }
                                    )

                                    is BottomTab.QuickPlaylists -> QuickPlaylistsScreen(
                                        modifier = contentModifier,
                                        onPlaySong = { uri, playlistName, color ->
                                            stopChainPlayback()
                                            Log.d(
                                                SMP_PLAY_TRACE_TAG,
                                                "PLAYLIST_TAP item=$uri playlist=$playlistName"
                                            )

                                        when (val target = PlaybackRouter.resolve(uri, playlistName)) {

                                            is PlaybackRouter.Target.Prompter -> {
                                                Log.d(
                                                    SMP_PLAY_TRACE_TAG,
                                                    "PLAYLIST_TARGET prompter id=${target.id} playlist=$playlistName"
                                                )
                                                textPrompterId = target.id
                                                return@QuickPlaylistsScreen
                                            }

                                            is PlaybackRouter.Target.Audio,
                                            is PlaybackRouter.Target.Smp -> {
                                                Log.d(
                                                    SMP_PLAY_TRACE_TAG,
                                                    "PLAYLIST_TARGET type=${target.javaClass.simpleName} value=$target"
                                                )
                                                val resolvedTarget = resolveAudioTarget(
                                                    target = target,
                                                    showToastOnFailure = true
                                                ) ?: run {
                                                    Log.d(
                                                        SMP_PLAY_TRACE_TAG,
                                                        "PLAYLIST_RESOLVED null item=$uri playlist=$playlistName target=$target"
                                                    )
                                                    return@QuickPlaylistsScreen
                                                }
                                                Log.d(
                                                    SMP_PLAY_TRACE_TAG,
                                                    "PLAYLIST_RESOLVED uri=${resolvedTarget.uri} playlist=${resolvedTarget.playlist}"
                                                )
                                                LyricsPerf.startOpen(
                                                    trackUriString = resolvedTarget.uri,
                                                    source = "quick_play_tap",
                                                    playlistName = resolvedTarget.playlist
                                                )
                                                playlistTapPlayJob?.cancel()
                                                playlistTapPlayJob = scope.launch {
                                                    val now = SystemClock.uptimeMillis()
                                                    val waitMs = (lastPlaylistTapStartedAtMs + 250L) - now
                                                    if (waitMs > 0L) delay(waitMs)
                                                    lastPlaylistTapStartedAtMs = SystemClock.uptimeMillis()
                                                    playWithCrossfadeInternal(
                                                        uriString = resolvedTarget.uri,
                                                        playlistName = resolvedTarget.playlist,
                                                        playlistItemKey = uri
                                                    )
                                                }
                                                currentPlayingUri = resolvedTarget.uri

                                                selectedQuickPlaylist = resolvedTarget.playlist
                                                currentLyricsColor = color
                                                selectedTab = BottomTab.Player
                                                latestSessionSnapshot = SessionSnapshot(
                                                    tabKey = TAB_PLAYER,
                                                    quickPlaylist = resolvedTarget.playlist ?: selectedQuickPlaylist,
                                                    openedPlaylist = openedPlaylist,
                                                    currentPlayingUri = resolvedTarget.uri,
                                                    currentPlayingPlaylist = resolvedTarget.playlist
                                                )
                                                persistSession(reason = "quickPlayTap")
                                            }

                                            is PlaybackRouter.Target.Unknown -> {
                                                Log.d(
                                                    SMP_PLAY_TRACE_TAG,
                                                    "PLAYLIST_TARGET unknown item=$uri playlist=$playlistName"
                                                )
                                                // rien
                                            }
                                        }
                                    },
                                    onPlayFromHere = { visibleQueue, startIndex, playlistName ->
                                        chainQueue = visibleQueue
                                        chainIndex = -1
                                        chainPlaylist = playlistName
                                        isChaining = true
                                        val resolvedStart = nextPlayableIndexAtOrAfter(visibleQueue, startIndex)
                                        if (resolvedStart == null || !playChainFrom(resolvedStart)) {
                                            stopChainPlayback()
                                        }
                                    },
                                    refreshKey = refreshKey,
                                    openPrompterSignal = openPrompterSignal,
                                    libraryLoadedSignal = indexAll.size,
                                    playlistsReady = playlistsReady,
                                    nextChainedUri = nextChainedUri,
                                    nextTrackUri = nextTrack?.uri,
                                    currentPlayingUri = currentPlayingUri,
                                    currentPlayingPlaylist = currentPlayingPlaylist,
                                    currentPlayingPlaylistItemKey = currentPlayingPlaylistItemKey,
                                    selectedPlaylist = selectedQuickPlaylist,
                                    openedPlaylist = openedPlaylist,
                                    isRestoringSession = isRestoringSession,
                                    onSelectedPlaylistChange = { name ->
                                        setQuickPlaylistAndPersist(name, reason = "quickPlaylistSelect")
                                    },
                                    onPlaylistColorChange = { _ -> currentLyricsColor = Color.White },
                                    onSetNextTrack = { uri, title, playlist ->
                                        PlaybackCoordinator.setNextTrack(uri, title, playlist)
                                    },
                                    onClearNextTrack = {
                                        PlaybackCoordinator.clearNextTrack(reason = "ui")
                                    },
                                    onConsumeOpenPrompterSignal = { openPrompterSignal = 0 },
                                    onRequestShowPlayer = {
                                        setTabAndPersist(BottomTab.Player, reason = "quickPlaylistShowPlayer")
                                    },
                                    onAddTrackToPlaylist = { playlistName ->
                                        pendingPlaylistTrackTarget = playlistName
                                        pickPlaylistTrackLauncher.launch(
                                            arrayOf(
                                                "audio/*",
                                                "application/zip",
                                                "application/x-zip-compressed",
                                                "application/octet-stream",
                                                "*/*"
                                            )
                                        )
                                    },
                                    searchToggleSignal = playlistSearchToggleSignal
                                    )

                                    is BottomTab.Library -> LibraryScreen(
                                        modifier = contentModifier,
                                        workspaceSnapshot = workspaceSnapshot,
                                        workspaceVersion = setupTick,
                                        currentPlayingSongId = currentPlayingSongId,
                                        reselectRootSignal = libraryTabReselectSignal,
                                        searchToggleSignal = librarySearchToggleSignal,
                                        smpRefreshVersion = smpCacheRefreshTick,
                                        lastImportedSmpSignal = lastImportedSmpUiSignal,
                                        onConsumeImportedSmpAutoOpen = {
                                            lastImportedSmpUiSignal = null
                                        },
                                        onWorkspaceChanged = {
                                            forceSetup = false
                                            setupTick++
                                        },
                                        onAfterBackupImport = { refreshKey++ },
                                        onImportExternalSmp = {
                                            pickSmpFileLauncher.launch(
                                                arrayOf(
                                                    "application/zip",
                                                    "application/x-zip-compressed",
                                                    "application/octet-stream",
                                                    "*/*"
                                                )
                                            )
                                        },
                                        onImportGeneratedSmp = { uri ->
                                            Log.i("SMP_CONVERT_FLOW", "step=main_auto_import_start outputUri=$uri")
                                            val importedSong = importSmpIntoApp(uri, libraryRuntimeReadyFirst = true)
                                            if (importedSong != null) {
                                                Log.i(
                                                    "SMP_CONVERT_FLOW",
                                                    "step=main_auto_import_ok outputUri=$uri songId=${importedSong.id} title=${importedSong.title}"
                                                )
                                            } else {
                                                Log.e(
                                                    "SMP_CONVERT_FLOW",
                                                    "step=main_auto_import_failed outputUri=$uri reason=${lastSmpImportFailureReason.get() ?: smpImporter.lastFailureReason ?: "inconnue"}"
                                                )
                                            }
                                            importedSong
                                        },
                                        onImportGeneratedSmpFailureReason = {
                                            lastSmpImportFailureReason.get() ?: smpImporter.lastFailureReason
                                        },
                                        onPlayFromLibrary = { uriString ->
                                            Log.d(
                                                SMP_PLAY_TRACE_TAG,
                                                "LIBRARY_TAP item=$uriString"
                                            )
                                            when (val target = PlaybackRouter.resolve(uriString, null)) {
                                                is PlaybackRouter.Target.Audio,
                                                is PlaybackRouter.Target.Smp -> {
                                                    Log.d(
                                                        SMP_PLAY_TRACE_TAG,
                                                        "LIBRARY_TARGET type=${target.javaClass.simpleName} value=$target"
                                                    )
                                                    val resolvedTarget = resolveAudioTarget(
                                                        target = target,
                                                        showToastOnFailure = true
                                                    ) ?: run {
                                                        Log.d(
                                                            SMP_PLAY_TRACE_TAG,
                                                            "LIBRARY_RESOLVED null item=$uriString target=$target"
                                                        )
                                                        return@LibraryScreen
                                                    }
                                                    Log.d(
                                                        SMP_PLAY_TRACE_TAG,
                                                        "LIBRARY_RESOLVED uri=${resolvedTarget.uri} playlist=${resolvedTarget.playlist}"
                                                    )
                                                    stopChainPlayback()
                                                    LyricsPerf.startOpen(
                                                        trackUriString = resolvedTarget.uri,
                                                        source = "library_tap",
                                                        playlistName = resolvedTarget.playlist
                                                    )
                                                    playWithCrossfade(
                                                        resolvedTarget.uri,
                                                        resolvedTarget.playlist,
                                                        null
                                                    )
                                                    currentPlayingUri = resolvedTarget.uri
                                                    currentLyricsColor = Color.White
                                                    setTabAndPersist(BottomTab.Player, reason = "libraryPlay")
                                                }

                                                is PlaybackRouter.Target.Prompter -> {
                                                    Log.d(
                                                        SMP_PLAY_TRACE_TAG,
                                                        "LIBRARY_TARGET prompter id=${target.id}"
                                                    )
                                                    textPrompterId = target.id
                                                }

                                                is PlaybackRouter.Target.Unknown -> {
                                                    Log.d(
                                                        SMP_PLAY_TRACE_TAG,
                                                        "LIBRARY_TARGET unknown item=$uriString"
                                                    )
                                                    // rien
                                                }
                                            }
                                        }
                                    )

                                    is BottomTab.AllPlaylists -> AllPlaylistsScreen(
                                        modifier = contentModifier,
                                        onPlaylistClick = { name ->
                                            selectedQuickPlaylist = name
                                            openedPlaylist = name
                                            setTabAndPersist(BottomTab.QuickPlaylists, reason = "allPlaylistsOpen")
                                        }
                                    )

                                    is BottomTab.Dj -> DjScreen(modifier = contentModifier, context = ctx)

                                    is BottomTab.More -> MoreScreen(
                                        modifier = contentModifier,
                                        context = ctx,
                                        currentWaveformTrackUri = currentPlayingUri,
                                        onAfterImport = { refreshKey++ },
                                        onOpenTuner = {
                                            setTabAndPersist(BottomTab.Tuner, reason = "moreOpenTuner")
                                        },
                                        onWaveformTrackPromotedToSmp = { migration ->
                                            smpSongsById = smpSongsById + (migration.song.id to migration.song)
                                            smpCacheRefreshTick++
                                        }
                                    )

                                    is BottomTab.Tuner -> TunerScreen(
                                        modifier = contentModifier,
                                        onClose = {
                                            setTabAndPersist(BottomTab.Home, reason = "tunerClose")
                                        }
                                    )

                                    else -> Box(
                                        modifier = contentModifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(stringResource(R.string.main_unknown_screen), color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // ✅ overlay Search
                    if (isSearchOpen) {
                        Box(
                            modifier = contentModifier
                                .fillMaxSize()
                                .background(Color.Black)
                        ) {
                            SearchScreen(
                                modifier = Modifier.fillMaxSize(),
                                indexAll = indexAll,
                                onBack = { isSearchOpen = false },
                                onPlay = { uriString ->
                                    when (searchMode) {
                                        SearchMode.PLAYER -> {
                                            stopChainPlayback()
                                            LyricsPerf.startOpen(
                                                trackUriString = uriString,
                                                source = "search_player_tap",
                                                playlistName = null
                                            )
                                            playWithCrossfade(uriString, null, null)
                                            currentPlayingUri = uriString
                                            setTabAndPersist(BottomTab.Player, reason = "searchPlayPlayer")
                                        }

                                        SearchMode.DJ -> {
                                            playFromSearchInDj(uriString)
                                        }

                                        SearchMode.PLAYLIST -> {
                                            // ✅ comme PLAYER : on lance et on bascule sur le lecteur
                                            stopChainPlayback()
                                            LyricsPerf.startOpen(
                                                trackUriString = uriString,
                                                source = "search_playlist_tap",
                                                playlistName = selectedQuickPlaylist
                                            )
                                            playWithCrossfade(
                                                uriString,
                                                selectedQuickPlaylist,
                                                uriString
                                            )
                                            currentPlayingUri = uriString
                                            currentLyricsColor = Color(0xFFE040FB)
                                            setTabAndPersist(BottomTab.Player, reason = "searchPlayPlaylist")
                                        }
                                    }
                                    isSearchOpen = false
                                },
                                restrictToUriStrings = if (searchMode == SearchMode.PLAYLIST) {
                                    selectedQuickPlaylist?.let { plName ->
                                        PlaylistRepository.getSongsFor(plName)
                                            .asSequence()
                                            .filter { isPlayableAudioItem(it) }
                                            .toSet()
                                    }
                                } else {
                                    null
                                },
                                searchModeLabel = searchMode.name,
                                searchPlaylistName = if (searchMode == SearchMode.PLAYLIST) selectedQuickPlaylist else null
                            )
                        }
                    }

                    // ✅ menu ⋮
                    DropdownMenu(
                        expanded = isMoreMenuOpen,
                        onDismissRequest = { isMoreMenuOpen = false }

                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_menu_notes)) },
                            onClick = {
                                isMoreMenuOpen = false
                                isNotesOpen = true
                                // ✅ si on est sur “Fond sonore” (overlay), il faut le fermer sinon on reste bloqué dessus
                                isFillerSettingsOpen = false
                                isGlobalMixOpen = false
                                isSearchOpen = false
                                textPrompterId = null
                                isMixerPreviewOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_menu_library)) },
                            onClick = {
                                isMoreMenuOpen = false
                                if (selectedTab is BottomTab.Library) {
                                    libraryTabReselectSignal++
                                } else {
                                    setTabAndPersist(BottomTab.Library, reason = "menuLibrary")
                                }
                                // ✅ si on est sur “Fond sonore” (overlay), il faut le fermer sinon on reste bloqué dessus
                                isFillerSettingsOpen = false
                                isGlobalMixOpen = false
                                isSearchOpen = false
                                textPrompterId = null
                                isMixerPreviewOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_menu_playlists)) },
                            onClick = {
                                isMoreMenuOpen = false
                                setTabAndPersist(BottomTab.AllPlaylists, reason = "menuAllPlaylists")
                                // ✅ si on est sur “Fond sonore” (overlay), il faut le fermer sinon on reste bloqué dessus
                                isFillerSettingsOpen = false
                                isGlobalMixOpen = false
                                isSearchOpen = false
                                textPrompterId = null
                                isMixerPreviewOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_menu_prompter)) },
                            onClick = {
                                isMoreMenuOpen = false
                                setTabAndPersist(BottomTab.QuickPlaylists, reason = "menuQuickPlaylists")
                                openPrompterSignal++
                                // ✅ si on est sur “Fond sonore” (overlay), il faut le fermer sinon on reste bloqué dessus
                                isFillerSettingsOpen = false
                                isGlobalMixOpen = false
                                isSearchOpen = false
                                textPrompterId = null
                                isMixerPreviewOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_menu_more)) },
                            onClick = {
                                isMoreMenuOpen = false
                                setTabAndPersist(BottomTab.More, reason = "menuMore")
                                // ✅ si on est sur “Fond sonore” (overlay), il faut le fermer sinon on reste bloqué dessus
                                isFillerSettingsOpen = false
                                isGlobalMixOpen = false
                                isSearchOpen = false
                                textPrompterId = null
                                isMixerPreviewOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_menu_tuner)) },
                            onClick = {
                                isMoreMenuOpen = false
                                setTabAndPersist(BottomTab.Tuner, reason = "menuTuner")
                                // ✅ si on est sur “Fond sonore” (overlay), il faut le fermer sinon on reste bloqué dessus
                                isFillerSettingsOpen = false
                                isGlobalMixOpen = false
                                isSearchOpen = false
                                textPrompterId = null
                                isMixerPreviewOpen = false
                            }
                        )
                    }

                    if (isNotesOpen) {
                        Box(
                            modifier = contentModifier
                                .fillMaxSize()
                                .background(Color(0xAA000000))
                        ) {
                            NotesScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 70.dp),
                                context = ctx,
                                onClose = { isNotesOpen = false }
                            )
                        }
                    }
                }
            }
        }
        mark("setContent:after")

        if (DEFERRED_BOOTSTRAP_STARTED.compareAndSet(false, true)) {
            mark("DeferredBootstrap.launch:scheduled")
            lifecycleScope.launch {
                mark("DeferredBootstrap.launch:start")
                withContext(Dispatchers.Default) {
                    mark("CueMidiStore.init.deferred:before")
                    runCatching { CueMidiStore.init(applicationContext) }
                        .onFailure { Log.e("BOOTSTEP", "CueMidiStore.init.deferred failed", it) }
                    mark("CueMidiStore.init.deferred:after")
                }
                withContext(Dispatchers.Default) {
                    mark("MidiOutput.init.deferred:before")
                    runCatching { MidiOutput.init(applicationContext) }
                        .onFailure { Log.e("BOOTSTEP", "MidiOutput.init.deferred failed", it) }
                    mark("MidiOutput.init.deferred:after")
                }
                mark("DeferredBootstrap.launch:end")
            }
        } else {
            mark("DeferredBootstrap.launch:skip alreadyStarted")
        }

        if (AUTO_RESTORE_BG_STARTED.compareAndSet(false, true)) {
            mark("AutoRestore.bg.launch:scheduled")
            lifecycleScope.launch {
                mark("AutoRestore.bg.launch:start")
                withContext(Dispatchers.IO) {
                    mark("AutoRestore.restoreIfNeeded.bg:before")
                    runCatching { AutoRestore.restoreIfNeeded(this@MainActivity) }
                        .onFailure { Log.e("BOOTSTEP", "AutoRestore.restoreIfNeeded.bg failed", it) }
                    mark("AutoRestore.restoreIfNeeded.bg:after")
                }
                mark("AutoRestore.bg.launch:end")
            }
        } else {
            mark("AutoRestore.bg.launch:skip alreadyStarted")
        }

        if (BACKUP_RESTORE_BG_STARTED.compareAndSet(false, true)) {
            mark("BackupRestore.bg.launch:scheduled")
            lifecycleScope.launch {
                mark("BackupRestore.bg.launch:start")
                withContext(Dispatchers.IO) {
                    mark("BackupRestorePrefs.wasRestoredOnce.bg:before")
                    val already = runCatching {
                        BackupRestorePrefs.wasRestoredOnce(this@MainActivity)
                    }.onFailure {
                        Log.e("BOOTSTEP", "BackupRestorePrefs.wasRestoredOnce.bg failed", it)
                    }.getOrDefault(true)
                    mark("BackupRestorePrefs.wasRestoredOnce.bg:after already=$already")

                    if (!already) {
                        mark("BackupManager.autoRestoreFromDefaultBackupFile.bg:before")
                        val restored = runCatching {
                            BackupManager.autoRestoreFromDefaultBackupFile(this@MainActivity)
                        }.onFailure {
                            Log.e("BOOTSTEP", "BackupManager.autoRestoreFromDefaultBackupFile.bg failed", it)
                        }.getOrDefault(false)
                        mark("BackupManager.autoRestoreFromDefaultBackupFile.bg:after restored=$restored")

                        if (restored) {
                            mark("BackupRestorePrefs.setRestoredOnce.bg:before")
                            runCatching {
                                BackupRestorePrefs.setRestoredOnce(this@MainActivity, true)
                            }.onFailure {
                                Log.e("BOOTSTEP", "BackupRestorePrefs.setRestoredOnce.bg failed", it)
                            }
                            mark("BackupRestorePrefs.setRestoredOnce.bg:after")
                        }
                    }
                }
                mark("BackupRestore.bg.launch:end")
            }
        } else {
            mark("BackupRestore.bg.launch:skip alreadyStarted")
        }
    }
    override fun onStop() {
        super.onStop()
        persistSession(reason = "onStop")
        BackupManager.autoSaveToDefaultBackupFile(this)
    }

    override fun onPause() {
        super.onPause()
        persistSession(reason = "onPause")
    }

}

/* --------------------------------------------------------------- */
/*  Conversion BottomTab <-> String                                */
/* --------------------------------------------------------------- */

private const val TAB_HOME = "home"
private const val TAB_PLAYER = "player"
private const val TAB_QUICK = "quick"
private const val TAB_LIBRARY = "library"
private const val TAB_ALL = "all"
private const val TAB_MORE = "more"
private const val TAB_DJ = "dj"
private const val TAB_TUNER = "tuner"
private const val TAB_FILLER = "filler"
private const val TAB_SEARCH = "search"

private fun tabKeyOf(tab: BottomTab): String = when (tab) {
    is BottomTab.Home -> TAB_HOME
    is BottomTab.Player -> TAB_PLAYER
    is BottomTab.QuickPlaylists -> TAB_QUICK
    is BottomTab.Library -> TAB_LIBRARY
    is BottomTab.AllPlaylists -> TAB_ALL
    is BottomTab.More -> TAB_MORE
    is BottomTab.Dj -> TAB_DJ
    is BottomTab.Tuner -> TAB_TUNER
    is BottomTab.Filler -> TAB_FILLER
    is BottomTab.Search -> TAB_SEARCH
}

private fun tabFromKey(key: String): BottomTab = when (key) {
    TAB_HOME -> BottomTab.Home
    TAB_PLAYER -> BottomTab.Player
    TAB_QUICK -> BottomTab.QuickPlaylists
    TAB_LIBRARY -> BottomTab.Library
    TAB_ALL -> BottomTab.AllPlaylists
    TAB_MORE -> BottomTab.More
    TAB_DJ -> BottomTab.Dj
    TAB_TUNER -> BottomTab.Tuner
    TAB_FILLER -> BottomTab.Filler
    TAB_SEARCH -> BottomTab.Home // on ne “restore” pas un overlay comme un onglet
    else -> BottomTab.Home
}

/* --------------------------------------------------------------- */
/*  Search mode                                                    */
/* --------------------------------------------------------------- */
private fun toTreeUri(docUri: Uri): Uri {
    val authority = docUri.authority ?: return docUri
    val docId = DocumentsContract.getDocumentId(docUri)
    return DocumentsContract.buildTreeDocumentUri(authority, docId)
}
private enum class SearchMode {
    PLAYER,
    DJ,
    PLAYLIST // ✅ nouveau


}
