@file:OptIn(androidx.media3.common.util.UnstableApi::class, kotlinx.coroutines.FlowPreview::class)

package com.patrick.lrcreader.exo


import android.database.Cursor
import android.content.pm.ActivityInfo
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
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.*
import com.patrick.lrcreader.core.audio.AudioEngine
import com.patrick.lrcreader.core.audio.EmbeddedLyricsListener
import com.patrick.lrcreader.core.dj.DjEngine
import com.patrick.lrcreader.core.exoCrossfadePlay
import com.patrick.lrcreader.core.history.HistoryRepository
import com.patrick.lrcreader.core.history.PlaySource
import com.patrick.lrcreader.core.light.LightCueDispatcher
import com.patrick.lrcreader.core.lyrics.LyricsMemoryCache
import com.patrick.lrcreader.core.lyrics.LyricsResolver
import com.patrick.lrcreader.core.config.PlaylistStateStore
import com.patrick.lrcreader.core.config.TitleAliasesStore
import com.patrick.lrcreader.smp.SmpImporter
import com.patrick.lrcreader.smp.SmpAutoMigration
import com.patrick.lrcreader.smp.SmpAutoMigrationResult
import com.patrick.lrcreader.smp.SmpBatchImportProcessor
import com.patrick.lrcreader.smp.SmpConverter
import com.patrick.lrcreader.smp.SmpConfig
import com.patrick.lrcreader.smp.SmpImportedSongDetail
import com.patrick.lrcreader.smp.SmpImportedUiSignal
import com.patrick.lrcreader.smp.SmpArchiveFinalizeScheduler
import com.patrick.lrcreader.smp.SmpArchiveFinalizeStore
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.SmpRuntimeSongCache
import com.patrick.lrcreader.smp.SmpSecureImportPipeline
import com.patrick.lrcreader.smp.SmpUserArchiveRebuilder
import com.patrick.lrcreader.smp.SmpWorkspaceArchiveStore
import com.patrick.lrcreader.ui.*
import com.patrick.lrcreader.ui.adaptive.rememberSmpAdaptiveTokens
import com.patrick.lrcreader.ui.library.LibraryScreen
import com.patrick.lrcreader.ui.library.SongVariantFamiliesStore
import com.patrick.lrcreader.ui.library.ensureWorkspaceLibraryFolders
import com.patrick.lrcreader.ui.locallink.LocalLinkExperimentalSenderRuntime
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.pow
import kotlin.math.roundToInt

private fun sanitizeDisplayTrackTitle(value: String?): String? {
    return value
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

class MainActivity : AppCompatActivity() {
    private data class ManualCrossfadeRequest(
        val uri: String,
        val playlist: String?,
        val playlistItemKey: String?,
        val title: String,
        val advanceChainIndexTo: Int? = null,
        val clearForcedNextAfterSuccess: Boolean = false
    )

    private enum class HardwareInputRoute {
        NONE,
        QUICK_PLAYLISTS,
        LIBRARY_SONGS,
        PROMPTER,
        PLAYER
    }

    private enum class TabletSplitRightPanel {
        LYRICS,
        LIBRARY,
        SETTINGS,
        BACKGROUND_SOUND,
        DJ,
        MAIN_BUS,
        TUNER
    }

    private data class SessionSnapshot(
        val tabKey: String,
        val quickPlaylist: String?,
        val openedPlaylist: String?,
        val currentPlayingUri: String?,
        val currentPlayingPlaylist: String?
    )

    companion object {
        private const val DEFAULT_TRACK_GAIN_DB = 0
        private const val MIN_TRACK_DB = -24
        private const val MAX_TRACK_DB = 6
        private const val SMP_PLAY_TRACE_TAG = "SMP_PLAY_TRACE"
        private const val ENABLE_SMP_DEBUG_HOME_BUTTONS = false
        private val AUTO_RESTORE_BG_STARTED = AtomicBoolean(false)
        private val BACKUP_RESTORE_BG_STARTED = AtomicBoolean(false)
        private val DEFERRED_BOOTSTRAP_STARTED = AtomicBoolean(false)


    }

    @Volatile
    private var latestSessionSnapshot = SessionSnapshot(
        tabKey = defaultTabKeyForEdition(),
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
    @Volatile
    private var activeHardwareInputRoute: HardwareInputRoute = HardwareInputRoute.NONE
    private var quickHardwareCommand: HardwareListCommand = HardwareListCommand.ACTIVATE
    private var quickHardwareCommandToken by mutableIntStateOf(0)
    private var quickHardwareReturnCommand: HardwareListCommand = HardwareListCommand.MOVE_NEXT
    private var quickHardwareReturnToken by mutableIntStateOf(0)
    private var libraryHardwareCommand: HardwareListCommand = HardwareListCommand.ACTIVATE
    private var libraryHardwareCommandToken by mutableIntStateOf(0)
    private var libraryHardwareReturnCommand: HardwareListCommand = HardwareListCommand.MOVE_NEXT
    private var libraryHardwareReturnToken by mutableIntStateOf(0)
    private var prompterHardwareAction: PrompterAction = PrompterAction.NEXT
    private var prompterHardwareActionToken by mutableIntStateOf(0)
    private var playerMediaToggleToken by mutableIntStateOf(0)
    private var playerReturnNavigateDirection by mutableIntStateOf(0)
    private var playerReturnNavigateToken by mutableIntStateOf(0)

    private fun toHardwareListCommand(keyCode: Int): HardwareListCommand? {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP -> HardwareListCommand.MOVE_PREVIOUS

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN -> HardwareListCommand.MOVE_NEXT

            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> HardwareListCommand.ACTIVATE

            else -> null
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (activeHardwareInputRoute) {
                HardwareInputRoute.QUICK_PLAYLISTS -> {
                    val command = toHardwareListCommand(event.keyCode)
                    if (command != null) {
                        quickHardwareCommand = command
                        quickHardwareCommandToken += 1
                        return true
                    }
                }

                HardwareInputRoute.LIBRARY_SONGS -> {
                    val command = toHardwareListCommand(event.keyCode)
                    if (command != null) {
                        libraryHardwareCommand = command
                        libraryHardwareCommandToken += 1
                        return true
                    }
                }

                HardwareInputRoute.PROMPTER -> {
                    val action = when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> PrompterAction.NEXT

                        KeyEvent.KEYCODE_DPAD_LEFT -> PrompterAction.PREV

                        else -> null
                    }
                    if (action != null) {
                        prompterHardwareAction = action
                        prompterHardwareActionToken += 1
                        return true
                    }
                }

                HardwareInputRoute.PLAYER -> {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            playerMediaToggleToken += 1
                            return true
                        }

                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_PAGE_UP -> {
                            playerReturnNavigateDirection = -1
                            playerReturnNavigateToken += 1
                            return true
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_PAGE_DOWN -> {
                            playerReturnNavigateDirection = 1
                            playerReturnNavigateToken += 1
                            return true
                        }
                    }
                }

                HardwareInputRoute.NONE -> Unit
            }
        }
        return super.dispatchKeyEvent(event)
    }

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
        val safeTab = snapshot.tabKey.ifBlank { defaultTabKeyForEdition() }
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

    private fun warmQuickPlaylistComposeStores() {
        TitleAliasesStore.version.intValue
        SongVariantFamiliesStore.version.intValue
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLanguagePrefs.applySavedLanguage(this)
        EditionConfig.initialize(this)
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
        val initialShowDjTab = UiEntryPrefs.showDjTab(this)
        val initialShowMainBusTab = UiEntryPrefs.showMainBusTab(this)
        mark("SessionPrefs.getTab/getQuick/getOpened:after")
        latestSessionSnapshot = SessionSnapshot(
            tabKey = sanitizeTabKey(
                key = initialTabKey,
                showDjTab = initialShowDjTab,
                showMainBusTab = initialShowMainBusTab
            ),
            quickPlaylist = initialQuickPlaylist,
            openedPlaylist = initialOpenedPlaylist,
            currentPlayingUri = initialLastTrackUri,
            currentPlayingPlaylist = initialLastPlaylist
        )

        mark("DjEngine.init:deferred/lazy (DjScreen)")

        // ✅ Auto backup : planifie le worker à chaque démarrage (WorkManager gère le "unique")
        mark("AutoBackupScheduler.ensureScheduled:before")
        AutoBackupScheduler.ensureScheduled(this)
        mark("AutoBackupScheduler.ensureScheduled:after")
        mark("SmpArchiveFinalizeScheduler.reconcilePending:before")
        SmpArchiveFinalizeScheduler.reconcilePending(this)
        mark("SmpArchiveFinalizeScheduler.reconcilePending:after")


        mark("BackupManager.autoRestoreFromDefaultBackupFile:deferred")
        lifecycleScope.launch {
            mark("WorkspaceResolver.resolve(onCreate):before")
            val startupWorkspaceSnapshot = withContext(Dispatchers.IO) {
                WorkspaceResolver.resolve(this@MainActivity)
            }
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
                val cached = withContext(Dispatchers.IO) { LibraryIndexCache.load(this@MainActivity) }
                mark("LibraryIndexCache.load(onCreate):after size=${cached?.size ?: 0}")
                if (!cached.isNullOrEmpty()) {
                    LibrarySnapshot.rootFolderUri = root
                    LibrarySnapshot.entries = cached.map { it.uriString }
                    LibrarySnapshot.isReady = true
                }
            }
            val startupSmpSongsById = withContext(Dispatchers.IO) {
                SmpRuntimeSongCache.load(this@MainActivity).associateBy { it.id }
            }
            warmQuickPlaylistComposeStores()
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
                var workspaceSnapshot by remember { mutableStateOf(startupWorkspaceSnapshot) }
                LaunchedEffect(setupTick) {
                    if (setupTick == 0) return@LaunchedEffect
                    mark("compose.WorkspaceResolver.resolve:before")
                    workspaceSnapshot = withContext(Dispatchers.IO) {
                        WorkspaceResolver.resolve(ctx)
                    }.also { snapshot ->
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
                var playlistStateReadyForRoot by remember { mutableStateOf<String?>(null) }
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
                    val playlistRestoreResult = withContext(Dispatchers.IO) {
                        mark("compose.ensureInitialized.io:start root=$rootKey")
                        val sessionInitOk = runCatching {
                            SessionPrefs.getLastSessionState(ctx)
                            true
                        }.getOrDefault(false)
                        val trimInitOk = runCatching {
                            EditSoundPrefs.warmCache(ctx)
                            true
                        }.getOrDefault(false)
                        runCatching { FillerSoundPrefs.warmCache(ctx) }
                        val textSongsInitOk = runCatching {
                            TextSongRepository.listAll(ctx)
                            true
                        }.getOrDefault(false)
                        val aliasInitOk = runCatching {
                            TitleAliasesStore.getAll(ctx)
                            true
                        }.getOrDefault(false)
                        val trackInitOk = true
                        val notesInitOk = true
                        val midiInitOk = true
                        val playlistRestoreResult = runCatching {
                            PlaylistStateStore.restorePlaylistsIntoRepository(ctx)
                        }.getOrElse {
                            Log.e("BOOTSTEP", "Playlist restore failed root=$rootKey", it)
                            PlaylistStateStore.RestoreResult(
                                success = false,
                                restoredPlaylistCount = 0,
                                internalPlaylistCount = 0,
                                workspacePlaylistCount = 0,
                                workspaceHasPlaylistFiles = false,
                                validated = false
                            )
                        }
                        migratedTitleAliases = runCatching {
                            TitleAliasesStore.migrateFromLegacyTitlesIfMissing(
                                context = ctx,
                                legacyTitlesByUri = legacyCustomTitlesSnapshot
                            )
                        }.getOrDefault(0)
                        loadedLegacyTrimByUri = runCatching { EditPrefs.getAllEdits(ctx) }.getOrDefault(emptyMap())
                        mark(
                            "compose.ensureInitialized.io:end root=$rootKey session=$sessionInitOk trim=$trimInitOk textSongs=$textSongsInitOk track=$trackInitOk alias=$aliasInitOk aliasMigrated=$migratedTitleAliases notes=$notesInitOk midi=$midiInitOk playlistSuccess=${playlistRestoreResult.success} playlistValidated=${playlistRestoreResult.validated} playlistRestored=${playlistRestoreResult.restoredPlaylistCount} playlistInternal=${playlistRestoreResult.internalPlaylistCount} playlistWorkspace=${playlistRestoreResult.workspacePlaylistCount} playlistWorkspaceHasFiles=${playlistRestoreResult.workspaceHasPlaylistFiles}"
                        )
                        if (!playlistRestoreResult.validated) {
                            Log.e(
                                "PLAYLIST_PERSIST",
                                "restore.not_ready root=$rootKey restored=${playlistRestoreResult.restoredPlaylistCount} internal=${playlistRestoreResult.internalPlaylistCount} workspace=${playlistRestoreResult.workspacePlaylistCount} workspaceHasFiles=${playlistRestoreResult.workspaceHasPlaylistFiles}"
                            )
                        }
                        playlistRestoreResult
                    }

                    legacyTrimByUri = loadedLegacyTrimByUri
                    if (migratedTitleAliases > 0) {
                        PlaylistRepository.touch()
                    }
                    configInitDoneForRoot = rootKey
                    if (playlistRestoreResult.validated) {
                        playlistStateReadyForRoot = rootKey
                    } else {
                        playlistStateReadyForRoot = null
                    }
                    mark("compose.ensureInitialized.effect:end root=$rootKey")
                }

                android.util.Log.d(
                    "SETUP_GATE",
                    "workspaceStatus=${workspaceSnapshot.status} detail=${workspaceSnapshot.detail} savedRoot=$savedRoot setupTree=${workspaceSnapshot.setupTreeUri} internal=$isInternalMode isDone=${BackupFolderPrefs.isDone(ctx)} hasPerm=$hasSetupPerm shouldShow=$shouldShowSetup"
                )
                var isSmpImportedSongsDialogOpen by remember { mutableStateOf(false) }
                var smpImportedSongs by remember { mutableStateOf<List<com.patrick.lrcreader.smp.SongUnit>>(emptyList()) }
                var smpSongsById by remember { mutableStateOf(startupSmpSongsById) }
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

                val autoImportGeneratedSmp: suspend (Uri) -> com.patrick.lrcreader.smp.SongUnit? = { uri ->
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
                                                PlaylistRepository.assignSongToPlaylist(
                                                    playlistName = targetPlaylist,
                                                    songUri = smpMarker,
                                                    songId = importedSong.id
                                                )
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
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    if (uris.isNullOrEmpty()) {
                        Log.d("SMP", "Sélection du fichier .smp annulée")
                        return@rememberLauncherForActivityResult
                    }

                    val distinctUris = uris.distinctBy { it.toString() }
                    distinctUris.forEach { uri ->
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
                    }

                    scope.launch {
                        var successCount = 0
                        var failureCount = 0

                        distinctUris.forEach { uri ->
                            val pickedName = displayNameOf(uri)
                            val importedSong = importSmpIntoApp(uri, libraryRuntimeReadyFirst = true)
                            if (importedSong != null) {
                                successCount += 1
                                Log.i(
                                    "SMP",
                                    "Import SMP réussi: name=$pickedName songId=${importedSong.id} title=${importedSong.title} storageFolder=${importedSong.storageFolder}"
                                )
                            } else {
                                failureCount += 1
                                val failureReason = lastSmpImportFailureReason.get()
                                    ?: smpImporter.lastFailureReason
                                    ?: "inconnue"
                                Log.e(
                                    "SMP",
                                    "Import SMP échoué: name=$pickedName reason=$failureReason"
                                )
                            }
                        }

                        val toastMessage = ctx.getString(
                            R.string.smp_batch_import_summary,
                            successCount,
                            failureCount
                        )
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
                        if (!PlaylistTrackLimitPolicy.canAddTracks(ctx, targetPlaylist, plan.supportedCount)) {
                            pendingPlaylistTrackTarget = null
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.playlist_track_limit_reached),
                                Toast.LENGTH_LONG
                            ).show()
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

                var pendingDemoPlaylistName by rememberSaveable { mutableStateOf<String?>(null) }


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
                        },

                        onDemoInstalled = { result ->
                            pendingDemoPlaylistName = result.playlistName
                            LibraryFolderCache.clear()
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
                val playlistRepoVersion = PlaylistRepository.version.value
                val exoPlayer = remember(audioPlayerEpoch) {
                    mark("compose.AudioEngine.getPlayer:before")
                    val player = AudioEngine.getPlayer(ctx) {}
                    mark("compose.AudioEngine.getPlayer:after")
                    player
                }

                LaunchedEffect(savedRoot, canUseWorkspace, playlistStateReadyForRoot, playlistRepoVersion) {
                    val rootKey = savedRoot?.toString()
                    if (!canUseWorkspace || rootKey == null || playlistStateReadyForRoot != rootKey) {
                        return@LaunchedEffect
                    }
                    withContext(Dispatchers.IO) {
                        PlaylistStateStore.savePlaylistsSnapshot(ctx)
                    }
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
                    mutableStateOf(
                        tabFromKey(
                            key = initialTabKey ?: defaultTabKeyForEdition(),
                            showDjTab = initialShowDjTab,
                            showMainBusTab = initialShowMainBusTab
                        )
                    )
                }
                var showDjTab by rememberSaveable { mutableStateOf(initialShowDjTab) }
                var showMainBusTab by rememberSaveable { mutableStateOf(initialShowMainBusTab) }
                val adaptiveTokens = rememberSmpAdaptiveTokens()
                var tabletExperimentalModeEnabled by rememberSaveable {
                    mutableStateOf(TabletExperimentalModePrefs.isEnabled(ctx))
                }
                LaunchedEffect(adaptiveTokens.tabletMode, tabletExperimentalModeEnabled) {
                    requestedOrientation = if (
                        adaptiveTokens.tabletMode &&
                        tabletExperimentalModeEnabled
                    ) {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
                DisposableEffect(adaptiveTokens.tabletMode, tabletExperimentalModeEnabled) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    val systemBars = WindowInsetsCompat.Type.systemBars()
                    if (adaptiveTokens.tabletMode && tabletExperimentalModeEnabled) {
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(systemBars)
                    } else {
                        controller.show(systemBars)
                    }
                    onDispose {
                        controller.show(systemBars)
                    }
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
                var currentPlayingTitle by rememberSaveable { mutableStateOf<String?>(null) }
                var currentPlayingPlaylist by rememberSaveable { mutableStateOf<String?>(initialLastPlaylist) }
                var currentPlayingPlaylistItemKey by rememberSaveable { mutableStateOf<String?>(null) }
                val currentPlayingSongId = remember(currentPlayingUri) {
                    resolveSessionSongIdFromTrackUri(currentPlayingUri)
                }
                var moreNavigationTarget by remember { mutableStateOf<String?>(null) }
                var moreNavigationToken by remember { mutableStateOf(0) }
                var playerNavigationTarget by remember { mutableStateOf<String?>(null) }
                var playerNavigationToken by remember { mutableStateOf(0) }
                var isPlaying by remember { mutableStateOf(false) }
                var parsedLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
                var lyricsLoading by remember { mutableStateOf(false) }

                var currentPlayToken by remember { mutableStateOf(0L) }
                var playlistTapPlayJob by remember { mutableStateOf<Job?>(null) }
                var playlistSessionWriteJob by remember { mutableStateOf<Job?>(null) }
                var lastPlaylistTapStartedAtMs by remember { mutableStateOf(0L) }
                var currentTrackGainDb by remember { mutableStateOf(DEFAULT_TRACK_GAIN_DB) }
                var currentTrackVolumeSource by remember {
                    mutableStateOf(SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL)
                }
                var currentLyricsColor by remember { mutableStateOf(Color.White) }
                var refreshKey by remember { mutableStateOf(0) }

                var isNotesOpen by remember { mutableStateOf(false) }
                var isFillerSettingsOpen by remember { mutableStateOf(false) }
                var libraryKeyboardNavigationEnabled by remember { mutableStateOf(false) }
                var isTabletSplitMenuOpen by remember { mutableStateOf(false) }
                var isTabletShortcutMoreOpen by remember { mutableStateOf(false) }
                var isTabletCockpitDestinationOpen by rememberSaveable { mutableStateOf(false) }
                var tabletRightPanel by rememberSaveable {
                    mutableStateOf(TabletSplitRightPanel.LYRICS)
                }

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
                var manualCrossfadeTransitionTitle by remember { mutableStateOf<String?>(null) }
                var manualCrossfadePlayer by remember { mutableStateOf<ExoPlayer?>(null) }
                var manualCrossfadeJob by remember { mutableStateOf<Job?>(null) }
                var transitionFadeOutPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
                var transitionFadeOutJob by remember { mutableStateOf<Job?>(null) }
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
                var tabletLibrarySearchToggleSignal by remember { mutableIntStateOf(0) }
                var tabletLibrarySearchCloseSignal by remember { mutableIntStateOf(0) }
                var tabletLibraryOpenStorageSignal by remember { mutableIntStateOf(0) }
                var tabletLyricsEditorFocusMode by remember { mutableStateOf(false) }

                // ✅ MODE de recherche (PLAYER ou DJ)
                var searchMode by remember { mutableStateOf(SearchMode.PLAYER) }

                // ✅ Index pour SearchScreen
                var indexAll by remember { mutableStateOf<List<LibraryIndexCache.CachedEntry>>(emptyList()) }
                val effectiveNextTrackTitle = remember(
                    nextTrack,
                    nextChainedUri,
                    chainPlaylist,
                    smpSongsById,
                    indexAll
                ) {
                    sanitizeDisplayTrackTitle(nextTrack?.title) ?: nextChainedUri?.let { queuedItem ->
                        val cleanPlaylist = chainPlaylist?.trim()?.takeIf { it.isNotEmpty() }
                        sanitizeDisplayTrackTitle(
                            cleanPlaylist?.let { PlaylistRepository.getCustomTitle(it, queuedItem) }
                        )
                            ?: cleanPlaylist
                                ?.let { PlaylistRepository.getPlaylistItem(it, queuedItem)?.songId }
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { songId ->
                                    sanitizeDisplayTrackTitle(smpSongsById[songId]?.title)
                                }
                            ?: getSmpSongId(queuedItem)
                                ?.let { songId ->
                                    sanitizeDisplayTrackTitle(smpSongsById[songId]?.title)
                                }
                            ?: sanitizeDisplayTrackTitle(TitleAliasesStore.getTitleForTrack(ctx, queuedItem))
                            ?: sanitizeDisplayTrackTitle(PlaylistRepository.getAnyCustomTitleForUri(queuedItem))
                            ?: sanitizeDisplayTrackTitle(indexAll.firstOrNull { it.uriString == queuedItem }?.name)
                            ?: sanitizeDisplayTrackTitle(Uri.parse(queuedItem).lastPathSegment)
                            ?: ctx.getString(R.string.player_next_track_fallback)
                    }
                }
                suspend fun syncWorkspaceSmpArchivesToRuntime(
                    trigger: String,
                    useAttemptGate: Boolean
                ): Int {
                    val rootKey = savedRoot?.toString()
                    val canUseStorage = rootKey != null &&
                        !shouldShowSetup &&
                        (isInternalMode || hasSetupPerm) &&
                        configInitDoneForRoot == rootKey

                    Log.i(
                        "SMP_TRACE",
                        "step=rebuild_gate trigger=$trigger rootKey=$rootKey savedRoot=$savedRoot canUseStorage=$canUseStorage shouldShowSetup=$shouldShowSetup hasSetupPerm=$hasSetupPerm isInternalMode=$isInternalMode configInitDoneForRoot=$configInitDoneForRoot"
                    )

                    if (!canUseStorage) {
                        Log.i(
                            "SMP_TRACE",
                            "step=rebuild_gate_skip trigger=$trigger rootKey=$rootKey reason=storage_not_ready"
                        )
                        return 0
                    }

                    if (useAttemptGate && smpUserRebuildAttemptedForRoot == rootKey) {
                        Log.i(
                            "SMP_TRACE",
                            "step=rebuild_gate_skip trigger=$trigger rootKey=$rootKey reason=already_attempted"
                        )
                        return 0
                    }
                    if (useAttemptGate) {
                        smpUserRebuildAttemptedForRoot = rootKey
                    }

                    val userArchives = withContext(Dispatchers.IO) {
                        smpUserArchiveRebuilder.listUserArchiveUris()
                    }
                    if (userArchives.isEmpty()) {
                        Log.i(
                            "SMP_REBUILD",
                            "step=sync_skip_no_user_archives trigger=$trigger root=$rootKey"
                        )
                        Log.i(
                            "SMP_TRACE",
                            "step=sync_skip trigger=$trigger rootKey=$rootKey reason=no_archives runtimeScan=false"
                        )
                        return 0
                    }

                    val runtimeSongsFromCache = smpSongsById.isNotEmpty()
                    val runtimeSongs = smpSongsById.values.takeIf { it.isNotEmpty() }?.toList()
                        ?: withContext(Dispatchers.IO) {
                            smpLibraryScanner.listSongs()
                        }.also { songs ->
                            smpSongsById = songs.associateBy { it.id }
                        }
                    val runtimeSongIds = runtimeSongs.map { it.id }.sorted()
                    Log.i(
                        "SMP_TRACE",
                        "step=runtime_before_sync trigger=$trigger rootKey=$rootKey count=${runtimeSongs.size} fromCache=$runtimeSongsFromCache songIds=${runtimeSongIds.joinToString(prefix = "[", postfix = "]", limit = 20, truncated = "...")}"
                    )
                    if (runtimeSongs.isEmpty()) {
                        Log.i("SMP_TRACE", "step=global_rebuild_mode trigger=$trigger rootKey=$rootKey")
                        Log.i(
                            "SMP_REBUILD",
                            "step=start trigger=$trigger root=$rootKey archiveCount=${userArchives.size}"
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
                            "step=runtime_after_global_rebuild trigger=$trigger rootKey=$rootKey count=${rebuiltSongs.size} songIds=${rebuiltSongs.map { it.id }.sorted().joinToString(prefix = "[", postfix = "]", limit = 20, truncated = "...")}"
                        )

                        if (rebuildResult.importedCount > 0) {
                            smpCacheRefreshTick++
                        }

                        Log.i(
                            "SMP_REBUILD",
                            "step=done trigger=$trigger root=$rootKey archives=${rebuildResult.discoveredCount} imported=${rebuildResult.importedCount} failed=${rebuildResult.failedCount} runtimeCount=${rebuiltSongs.size}"
                        )
                        return rebuildResult.importedCount
                    }

                    Log.i(
                        "SMP_TRACE",
                        "step=partial_sync_mode trigger=$trigger rootKey=$rootKey runtimeCount=${runtimeSongs.size}"
                    )
                    val runtimeSongIdsSet = runtimeSongIds.toSet()
                    val archiveCandidates = withContext(Dispatchers.IO) {
                        smpUserArchiveRebuilder.listUserArchiveCandidates()
                    }
                    val partialPlan = SmpUserArchiveRebuilder.buildPartialSyncPlan(
                        runtimeSongIds = runtimeSongIdsSet,
                        candidates = archiveCandidates
                    )
                    Log.i(
                        "SMP_TRACE",
                        "step=partial_plan_summary trigger=$trigger rootKey=$rootKey archiveCount=${archiveCandidates.size} importCount=${partialPlan.importCount} skippedInvalid=${partialPlan.skippedInvalidArchives.size} skippedDuplicate=${partialPlan.skippedDuplicateSongIds.size}"
                    )

                    partialPlan.skippedInvalidArchives.forEach { archiveUri ->
                        Log.i(
                            "SMP_REBUILD",
                            "step=partial_skip_invalid_id trigger=$trigger root=$rootKey uri=$archiveUri"
                        )
                    }
                    partialPlan.skippedDuplicateSongIds.forEach { songId ->
                        Log.i(
                            "SMP_REBUILD",
                            "step=partial_skip_duplicate_id trigger=$trigger root=$rootKey songId=$songId"
                        )
                    }

                    if (partialPlan.importCount == 0) {
                        Log.i(
                            "SMP_REBUILD",
                            "step=partial_done trigger=$trigger root=$rootKey archives=${archiveCandidates.size} imported=0 failed=0 skippedInvalid=${partialPlan.skippedInvalidArchives.size} skippedDuplicate=${partialPlan.skippedDuplicateSongIds.size} runtimeCount=${runtimeSongs.size}"
                        )
                        return 0
                    }

                    Log.i(
                        "SMP_REBUILD",
                        "step=partial_sync_start trigger=$trigger root=$rootKey archiveCount=${archiveCandidates.size} missingCount=${partialPlan.importCount} runtimeCount=${runtimeSongs.size}"
                    )
                    partialPlan.archivesToImport.forEach { candidate ->
                        Log.i(
                            "SMP_REBUILD",
                            "step=partial_import trigger=$trigger root=$rootKey songId=${candidate.stableSongId} uri=${candidate.archiveUri}"
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
                        "step=runtime_after_partial_sync trigger=$trigger rootKey=$rootKey count=${rebuiltSongs.size} songIds=${rebuiltSongs.map { it.id }.sorted().joinToString(prefix = "[", postfix = "]", limit = 20, truncated = "...")}"
                    )

                    if (rebuildResult.importedCount > 0) {
                        smpCacheRefreshTick++
                    }

                    Log.i(
                        "SMP_REBUILD",
                        "step=partial_done trigger=$trigger root=$rootKey archives=${archiveCandidates.size} imported=${rebuildResult.importedCount} failed=${rebuildResult.failedCount} skippedInvalid=${partialPlan.skippedInvalidArchives.size} skippedDuplicate=${partialPlan.skippedDuplicateSongIds.size} runtimeCount=${runtimeSongs.size}"
                    )
                    return rebuildResult.importedCount
                }
                LaunchedEffect(smpCacheRefreshTick) {
                    if (smpCacheRefreshTick == 0 && smpSongsById.isNotEmpty()) {
                        Log.i(
                            "SMP_TRACE",
                            "step=runtime_cache_refresh_verify reason=cache_already_loaded count=${smpSongsById.size}"
                        )
                        val refreshedSongsById = withContext(Dispatchers.IO) {
                            smpLibraryScanner.listSongs().associateBy { it.id }
                        }
                        if (refreshedSongsById.isEmpty()) {
                            Log.w(
                                "SMP_TRACE",
                                "step=runtime_cache_refresh_keep_cache reason=empty_runtime_scan cachedCount=${smpSongsById.size}"
                            )
                            return@LaunchedEffect
                        }
                        if (refreshedSongsById != smpSongsById) {
                            smpSongsById = refreshedSongsById
                        }
                        withContext(Dispatchers.IO) {
                            SmpRuntimeSongCache.save(ctx, refreshedSongsById.values)
                        }
                        return@LaunchedEffect
                    }
                    val refreshedSongsById = withContext(Dispatchers.IO) {
                        smpLibraryScanner.listSongs().associateBy { it.id }
                    }
                    if (refreshedSongsById.isEmpty() && smpSongsById.isNotEmpty()) {
                        Log.w(
                            "SMP_TRACE",
                            "step=runtime_cache_refresh_keep_cache reason=empty_runtime_scan refreshTick=$smpCacheRefreshTick cachedCount=${smpSongsById.size}"
                        )
                        withContext(Dispatchers.IO) {
                            SmpRuntimeSongCache.save(ctx, smpSongsById.values)
                        }
                        return@LaunchedEffect
                    }
                    smpSongsById = refreshedSongsById
                    withContext(Dispatchers.IO) {
                        SmpRuntimeSongCache.save(ctx, smpSongsById.values)
                    }
                }
                LaunchedEffect(savedRoot, hasSetupPerm, isInternalMode, shouldShowSetup, configInitDoneForRoot) {
                    syncWorkspaceSmpArchivesToRuntime(
                        trigger = "startup",
                        useAttemptGate = true
                    )
                    return@LaunchedEffect
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

	                fun resolveSmpPlaybackTrim(songId: String?): SmpConfig.PlaybackConfig? {
	                    val cleanSongId = songId?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
	                    val song = smpSongsById[cleanSongId] ?: return null
	                    return SmpConfig.readPlaybackFromSongUnit(song)
	                }

	                fun resolveTrimConfig(requestedUri: String, activeUri: String?): TrimConfig {
	                    val candidates = buildList {
	                        add(requestedUri)
	                        if (!activeUri.isNullOrBlank() && activeUri != requestedUri) add(activeUri)
	                    }
	                    val smpPlayback = candidates.asSequence()
	                        .mapNotNull { candidate -> resolveSessionSongIdFromTrackUri(candidate) }
	                        .mapNotNull(::resolveSmpPlaybackTrim)
	                        .firstOrNull { it.trimStartMs != null || it.trimEndMs != null }

	                    val editSound = candidates.asSequence()
	                        .mapNotNull { candidate -> EditSoundPrefs.resolveCached(Uri.parse(candidate)) }
	                        .firstOrNull()
	                    val legacyCandidate = if (smpPlayback == null && editSound == null) {
	                        candidates.asSequence()
	                            .mapNotNull { candidate ->
	                                legacyTrimByUri[candidate]?.let { edit -> candidate to edit }
	                            }
	                            .firstOrNull()
                    } else {
                        null
                    }

	                    val store = when {
	                        smpPlayback != null -> "SmpConfig"
	                        editSound != null -> "EditSoundPrefs"
	                        legacyCandidate != null -> "EditPrefs"
	                        else -> "none"
	                    }
	                    val key = when {
	                        smpPlayback != null -> candidates.asSequence()
	                            .mapNotNull(::resolveSessionSongIdFromTrackUri)
	                            .firstOrNull()
	                            ?: runCatching {
	                                EditSoundPrefs.trimKeyForUri(Uri.parse(activeUri ?: requestedUri))
	                            }.getOrDefault(activeUri ?: requestedUri)
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
	                            smpPlayback?.trimStartMs
	                                ?: editSound?.info?.startMs?.toLong()
	                                ?: legacyCandidate?.second?.startMs
	                                ?: 0L
	                            ).coerceAtLeast(0L)
	                    val rawExit = (
	                            smpPlayback?.trimEndMs
	                                ?: editSound?.info?.endMs?.toLong()
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

                fun resolveEffectiveDurationMs(requestedUri: String?, activeUri: String?): Long {
                    val effectiveRequestedUri = requestedUri?.takeIf { it.isNotBlank() }
                        ?: activeUri?.takeIf { it.isNotBlank() }
                        ?: return exoPlayer.duration
                    val trimConfig = resolveTrimConfig(
                        requestedUri = effectiveRequestedUri,
                        activeUri = activeUri
                    )
                    return trimConfig.exitMs?.takeIf { trimConfig.mode == "seek-stop" && it > 0L }
                        ?: exoPlayer.duration
                }

                val onEnded = rememberUpdatedState {
                    if (manualCrossfadeTransitionTitle != null) {
                        return@rememberUpdatedState
                    }
                    cancelTrimWatcher()
                    isPlaying = false
                    LightCueDispatcher.resetGlobal()
                    PlaybackCoordinator.onPlayerStop()
                    val shouldStartFiller =
                        PlaybackCoordinator.peekNextTrack() == null && !isChaining
                    if (shouldStartFiller) {
                        PlaybackCoordinator.onFillerStart()
                        runCatching { FillerSoundManager.startIfConfigured(ctx) }
                    }
                    backingEndedSignal++
                }

                PlaybackCoordinator.stopPlayer = {
                    manualCrossfadeJob?.cancel()
                    manualCrossfadeJob = null
                    manualCrossfadeTransitionTitle = null
                    manualCrossfadePlayer?.let { player ->
                        runCatching { player.stop() }
                        runCatching { player.release() }
                    }
                    manualCrossfadePlayer = null
                    transitionFadeOutJob?.cancel()
                    transitionFadeOutJob = null
                    transitionFadeOutPlayer?.let { player ->
                        runCatching { player.stop() }
                        runCatching { player.release() }
                    }
                    transitionFadeOutPlayer = null
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

                fun dbToLinearAttenuation(db: Int): Float {
                    return (10f.pow(db / 20f)).coerceIn(0f, 16f)
                }

                fun sanitizeDisplayTrackTitle(value: String?): String? {
                    return value
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                }

                fun resolveQueuedTrackTitle(
                    playlistItemKey: String,
                    playlistName: String?,
                    fallbackUri: String
                ): String {
                    val cleanPlaylist = playlistName?.trim()?.takeIf { it.isNotEmpty() }
                    return sanitizeDisplayTrackTitle(
                        cleanPlaylist
                        ?.let { PlaylistRepository.getCustomTitle(it, playlistItemKey) }
                    )
                        ?: cleanPlaylist
                            ?.let { PlaylistRepository.getPlaylistItem(it, playlistItemKey)?.songId }
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { songId -> sanitizeDisplayTrackTitle(smpSongsById[songId]?.title) }
                        ?: getSmpSongId(playlistItemKey)
                            ?.let { songId -> sanitizeDisplayTrackTitle(smpSongsById[songId]?.title) }
                        ?: sanitizeDisplayTrackTitle(TitleAliasesStore.getTitleForTrack(ctx, fallbackUri))
                        ?: sanitizeDisplayTrackTitle(indexAll.firstOrNull { it.uriString == fallbackUri }?.name)
                        ?: sanitizeDisplayTrackTitle(Uri.parse(fallbackUri).lastPathSegment)
                        ?: ctx.getString(R.string.player_next_track_fallback)
                }

                fun currentManualCrossfadeDurationMs(): Long {
                    return ManualCrossfadePrefs.getDurationMs(ctx)
                }

                fun currentManualCrossfadePrerollMs(): Long {
                    return 400L
                }

                fun cancelTransitionFadeOut() {
                    transitionFadeOutJob?.cancel()
                    transitionFadeOutJob = null
                    transitionFadeOutPlayer?.let { player ->
                        Log.d(
                            "AUDIO_PLAYER_DIAG",
                            "releaseTransitionPlayer playerId=${System.identityHashCode(player)} " +
                                "isPlaying=${runCatching { player.isPlaying }.getOrDefault(false)} " +
                                "mediaUri=${player.currentMediaItem?.localConfiguration?.uri} volume=${runCatching { player.volume }.getOrDefault(-1f)}"
                        )
                        runCatching { player.stop() }
                        runCatching { player.release() }
                    }
                    transitionFadeOutPlayer = null
                }

                fun launchTransitionFadeOut(player: ExoPlayer) {
                    cancelTransitionFadeOut()
                    transitionFadeOutPlayer = player
                    transitionFadeOutJob = scope.launch {
                        try {
                            val startVolume = runCatching { player.volume }.getOrDefault(1f).coerceIn(0f, 1f)
                            val steps = 30
                            val stepDelayMs = (currentManualCrossfadeDurationMs() / steps).coerceAtLeast(1L)
                            repeat(steps) { step ->
                                val progress = (step + 1).toFloat() / steps.toFloat()
                                runCatching {
                                    player.volume = (startVolume * (1f - progress)).coerceIn(0f, 1f)
                                }
                                delay(stepDelayMs)
                            }
                        } finally {
                            val fadingOutPlayer = transitionFadeOutPlayer
                            if (fadingOutPlayer === player) {
                                runCatching { player.stop() }
                                runCatching { player.release() }
                                transitionFadeOutPlayer = null
                                transitionFadeOutJob = null
                            }
                        }
                    }
                }

                suspend fun prepareTransitionPlayer(
                    player: ExoPlayer,
                    playableUri: String,
                    speed: Float,
                    pitchSemi: Int
                ): Boolean = suspendCancellableCoroutine { continuation ->
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY && continuation.isActive) {
                                runCatching { player.removeListener(this) }
                                continuation.resume(true)
                            }
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            if (continuation.isActive) {
                                runCatching { player.removeListener(this) }
                                continuation.resume(false)
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        runCatching { player.removeListener(listener) }
                    }

                    val pitchFactor = 2f.pow(pitchSemi.coerceIn(-6, 6) / 12f)
                    player.addListener(listener)
                    player.setMediaItem(MediaItem.fromUri(playableUri))
                    player.playbackParameters = PlaybackParameters(
                        speed.coerceIn(0.5f, 2.0f),
                        pitchFactor
                    )
                    player.prepare()
                }

                fun cancelManualCrossfadeTransition() {
                    manualCrossfadeJob?.cancel()
                    manualCrossfadeJob = null
                    manualCrossfadeTransitionTitle = null
                    manualCrossfadePlayer?.let { player ->
                        runCatching { player.stop() }
                        runCatching { player.release() }
                    }
                    manualCrossfadePlayer = null
                    transitionFadeOutJob?.cancel()
                    transitionFadeOutJob = null
                    transitionFadeOutPlayer?.let { player ->
                        runCatching { player.stop() }
                        runCatching { player.release() }
                    }
                    transitionFadeOutPlayer = null
                }

                fun persistCurrentUiSession(reason: String, tabOverride: BottomTab? = null) {
                    val tab = sanitizeTab(
                        tab = tabOverride ?: selectedTab,
                        showDjTab = showDjTab,
                        showMainBusTab = showMainBusTab
                    )
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
                    val safeTab = sanitizeTab(
                        tab = tab,
                        showDjTab = showDjTab,
                        showMainBusTab = showMainBusTab
                    )
                    selectedTab = safeTab
                    persistCurrentUiSession(reason = reason, tabOverride = safeTab)
                }

                fun setQuickPlaylistAndPersist(name: String?, reason: String) {
                    selectedQuickPlaylist = name
                    persistCurrentUiSession(reason = reason)
                }

                fun setOpenedPlaylistAndPersist(name: String?, reason: String) {
                    openedPlaylist = name
                    persistCurrentUiSession(reason = reason)
                }

                LaunchedEffect(playerMediaToggleToken) {
                    if (playerMediaToggleToken == 0) return@LaunchedEffect
                    if (selectedTab !is BottomTab.Player) return@LaunchedEffect
                    if (currentPlayingUri.isNullOrBlank()) return@LaunchedEffect
                    val shouldPlay = !isPlaying
                    isPlaying = shouldPlay
                    if (shouldPlay) {
                        exoPlayer.play()
                    } else {
                        exoPlayer.pause()
                    }
                }

                LaunchedEffect(playerReturnNavigateToken) {
                    if (playerReturnNavigateToken == 0) return@LaunchedEffect
                    if (selectedTab !is BottomTab.Player) return@LaunchedEffect

                    val moveCommand = if (playerReturnNavigateDirection < 0) {
                        HardwareListCommand.MOVE_PREVIOUS
                    } else {
                        HardwareListCommand.MOVE_NEXT
                    }

                    val targetPlaylist = currentPlayingPlaylist ?: selectedQuickPlaylist
                    when {
                        !targetPlaylist.isNullOrBlank() -> {
                            selectedQuickPlaylist = targetPlaylist
                            openedPlaylist = targetPlaylist
                            setTabAndPersist(BottomTab.QuickPlaylists, reason = "hardwareReturnFromPlayerPlaylist")
                            quickHardwareReturnCommand = moveCommand
                            quickHardwareReturnToken += 1
                        }

                        !currentPlayingSongId.isNullOrBlank() -> {
                            setTabAndPersist(BottomTab.Library, reason = "hardwareReturnFromPlayerLibrary")
                            libraryHardwareReturnCommand = moveCommand
                            libraryHardwareReturnToken += 1
                        }
                    }
                }

                LaunchedEffect(pendingDemoPlaylistName) {
                    val demoPlaylistName = pendingDemoPlaylistName?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
                    selectedQuickPlaylist = demoPlaylistName
                    openedPlaylist = demoPlaylistName
                    setTabAndPersist(BottomTab.QuickPlaylists, reason = "demoInstallOpenPlaylist")
                    pendingDemoPlaylistName = null
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

                fun hasNonNeutralPitchSpeed(speed: Float, pitchSemi: Int): Boolean {
                    return kotlin.math.abs(speed - 1f) > 0.0005f || pitchSemi != 0
                }

                fun shouldUseSequentialTransition(
                    currentSpeed: Float,
                    currentPitchSemi: Int,
                    nextSpeed: Float,
                    nextPitchSemi: Int
                ): Boolean {
                    return hasNonNeutralPitchSpeed(currentSpeed, currentPitchSemi) ||
                        hasNonNeutralPitchSpeed(nextSpeed, nextPitchSemi)
                }

                suspend fun playWithCrossfadeInternal(
                    uriString: String,
                    playlistName: String?,
                    playlistItemKey: String? = null,
                    openPlayerScreen: Boolean = true
                ) {
                    if (manualCrossfadeTransitionTitle != null) {
                        return
                    }
                    cancelTransitionFadeOut()
                    suspend fun shouldOpenPlayerScreenForLaunch(
                        trackUriString: String,
                        allowPlayerOpen: Boolean
                    ): Boolean {
                        if (!allowPlayerOpen) return false
                        return when (PlayerLaunchPrefs.getMode(ctx)) {
                            PlayerLaunchMode.ALWAYS -> true
                            PlayerLaunchMode.NEVER -> false
                            PlayerLaunchMode.AUTOMATIC -> withContext(Dispatchers.IO) {
                                LyricsMemoryCache.updateScope(LrcStorage.currentWorkspaceScopeKey(ctx))
                                if (LyricsMemoryCache.get(trackUriString)?.parsedLines?.isNotEmpty() == true) {
                                    return@withContext true
                                }

                                val smpSongId = getSmpSongId(trackUriString)
                                if (smpSongId != null) {
                                    return@withContext smpSongsById[smpSongId]?.lyricsPath != null
                                }

                                smpSongsById.values.firstOrNull { it.audioPath == trackUriString }
                                    ?.let { return@withContext it.lyricsPath != null }

                                return@withContext LrcStorage.resolveOriginForTrack(
                                    ctx,
                                    trackUriString
                                ) != null ||
                                    runCatching {
                                        val trackUri = Uri.parse(trackUriString)
                                        !readSyltAsLrcFromUri(ctx, trackUri).isNullOrBlank() ||
                                            !readUsltFromUri(ctx, trackUri).isNullOrBlank()
                                    }.getOrDefault(false)
                            }
                        }
                    }

                    val shouldOpenPlayerScreen = shouldOpenPlayerScreenForLaunch(
                        trackUriString = uriString,
                        allowPlayerOpen = openPlayerScreen
                    )

                    val backingTitle = TitleAliasesStore.getTitleForTrack(ctx, uriString)
                        ?: indexAll.firstOrNull { it.uriString == uriString }?.name
                        ?: Uri.parse(uriString).lastPathSegment
                        ?: HistoryRepository.UNTITLED_FALLBACK
                    currentPlayingTitle = sanitizeDisplayTrackTitle(backingTitle)

                    LyricsPerf.mark(
                        uriString,
                        "play_internal_start",
                        "playlistName=$playlistName"
                    )
                    PlaybackCoordinator.onPlayerStart()
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
                    val previousTrackTempo = currentTrackTempo
                    val previousTrackPitchSemi = currentTrackPitchSemi

                    data class LoadedTrackMixSettings(
                        val gainDb: Int,
                        val volumeSource: String,
                        val tempo: Float,
                        val pitchSemi: Int
                    )

                    SmpLaunchTiming.markConfigLoadStart(uriString)
                    val loadedTrackMixSettings = withContext(Dispatchers.IO) {
                        LoadedTrackMixSettings(
                            gainDb = clampTrackDb(TrackVolumePrefs.getDb(ctx, uriString) ?: DEFAULT_TRACK_GAIN_DB),
                            volumeSource = TrackVolumePrefs.getSource(ctx, uriString),
                            tempo = TrackTempoPrefs.getTempo(ctx, uriString) ?: 1f,
                            pitchSemi = TrackPitchPrefs.getSemi(ctx, uriString) ?: 0
                        )
                    }
                    currentTrackGainDb = loadedTrackMixSettings.gainDb
                    currentTrackVolumeSource = loadedTrackMixSettings.volumeSource
                    currentTrackTempo = loadedTrackMixSettings.tempo
                    currentTrackPitchSemi = loadedTrackMixSettings.pitchSemi
                    val loadedPitchFactor = 2f.pow(loadedTrackMixSettings.pitchSemi.coerceIn(-6, 6) / 12f)
                    Log.d(
                        "PITCH_TRANSITION_DIAG",
                        "SONG_START_REQUEST songId=$uriString tempo=${loadedTrackMixSettings.tempo} pitchSemi=${loadedTrackMixSettings.pitchSemi} pitch=$loadedPitchFactor"
                    )
                    SmpLaunchTiming.markConfigLoadDone(
                        uri = uriString,
                        volumeSource = loadedTrackMixSettings.volumeSource,
                        tempo = loadedTrackMixSettings.tempo,
                        pitchSemi = loadedTrackMixSettings.pitchSemi,
                        gainDb = loadedTrackMixSettings.gainDb
                    )

                    val forceSequentialForPitchSpeed = shouldUseSequentialTransition(
                        currentSpeed = previousTrackTempo,
                        currentPitchSemi = previousTrackPitchSemi,
                        nextSpeed = loadedTrackMixSettings.tempo,
                        nextPitchSemi = loadedTrackMixSettings.pitchSemi
                    )
                    val playerIsCurrentlyPlaying = runCatching { exoPlayer.isPlaying }.getOrDefault(false)
                    Log.d(
                        "PITCH_TRANSITION_GUARD",
                        "TRANSITION_MODE=${if (forceSequentialForPitchSpeed) "SEQUENTIAL_NO_CROSSFADE" else "CROSSFADE_ALLOWED"} " +
                            "currentSpeed=$previousTrackTempo currentPitch=$previousTrackPitchSemi " +
                            "nextSpeed=${loadedTrackMixSettings.tempo} nextPitch=${loadedTrackMixSettings.pitchSemi} " +
                            "isPlaying=$playerIsCurrentlyPlaying currentSongId=$currentPlayingUri nextSongId=$uriString " +
                            "currentUri=$currentPlayingUri nextUri=$uriString"
                    )
                    val shouldUseImmediateTransition =
                        !forceSequentialForPitchSpeed &&
                            playerIsCurrentlyPlaying &&
                            !currentPlayingUri.isNullOrBlank() &&
                            currentPlayingUri != uriString

                    val playbackPlayer: ExoPlayer
                    val playbackLyricsListener: EmbeddedLyricsListener
                    if (shouldUseImmediateTransition) {
                        val promotedPlayer = AudioEngine.createTransitionPlayer(ctx)
                        AudioEngine.promoteTransitionPlayer(ctx, promotedPlayer) {
                            onEnded.value.invoke()
                        }?.let { fadingOutPlayer ->
                            launchTransitionFadeOut(fadingOutPlayer)
                        }
                        parsedLines = emptyList()
                        lyricsLoading = false
                        isPlaying = true
                        currentPlayingUri = uriString
                        currentPlayingPlaylist = playlistName
                        playbackPlayer = promotedPlayer
                        playbackLyricsListener = AudioEngine.getLyricsListener()
                    } else if (forceSequentialForPitchSpeed) {
                        playbackPlayer = AudioEngine.prepareMainPlayerForSequentialStart(
                            context = ctx,
                            speed = loadedTrackMixSettings.tempo,
                            pitch = loadedPitchFactor,
                            onNaturalEnd = { onEnded.value.invoke() }
                        )
                        playbackLyricsListener = AudioEngine.getLyricsListener()
                        Log.d(
                            "AUDIO_PLAYER_DIAG",
                            "activePlayerAfterSequential playerId=${System.identityHashCode(playbackPlayer)} " +
                                "mediaUri=${playbackPlayer.currentMediaItem?.localConfiguration?.uri} " +
                                "volume=${playbackPlayer.volume} playbackParameters=${playbackPlayer.playbackParameters.speed}/${playbackPlayer.playbackParameters.pitch}"
                        )
                    } else {
                        playbackPlayer = exoPlayer
                        playbackLyricsListener = embeddedLyricsListener
                    }

                    var lyricsResolveSeq = 0
                    val result = runCatching {
                        cancelTrimWatcher()
                        AudioEngine.reapplyMixNow()

                        exoCrossfadePlay(
                            context = ctx,
                            exoPlayer = playbackPlayer,
                            embeddedLyricsListener = playbackLyricsListener,
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
                                val activeUri = playbackPlayer.currentMediaItem
                                    ?.localConfiguration
                                    ?.uri
                                    ?.toString()
                                    ?: uriString
                                currentPlayingUri = activeUri
                                currentPlayingPlaylist = playlistName
                                Log.d(
                                    SMP_PLAY_TRACE_TAG,
                                    "PLAYER_ON_START requestedUri=$uriString activeUri=$activeUri playlist=$playlistName token=$myToken playWhenReady=${playbackPlayer.playWhenReady} isPlaying=${playbackPlayer.isPlaying} state=${playbackPlayer.playbackState}"
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
                                        runCatching { playbackPlayer.seekTo(trimConfig.entryMs) }
                                    }
                                    trimAppliedForThisTrack = true
                                }
                                val trimExitMs = trimConfig.exitMs
                                if (trimConfig.mode == "seek-stop" && trimExitMs != null && trimExitMs > 0L) {
                                    trimStopJob = scope.launch {
                                        while (currentPlayToken == myToken) {
                                            val positionMs = runCatching { playbackPlayer.currentPosition }.getOrDefault(0L)
                                            if (positionMs >= trimExitMs) {
                                                runCatching { playbackPlayer.pause() }
                                                onEnded.value.invoke()
                                                return@launch
                                            }
                                            delay(40L)
                                        }
                                    }
                                }

                                AudioEngine.applyTrackGainDb(currentTrackGainDb)
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
                            onNaturalEnd = {
                                onEnded.value.invoke()
                            },
                            sequentialNoCrossfade = forceSequentialForPitchSpeed,
                            beforePrepare = { player, playableUri ->
                                AudioEngine.applyTrackGainDb(currentTrackGainDb)
                                Log.d(
                                    "AUDIO_PLAYER_DIAG",
                                    "appliedGain beforeSetNext gainDb=$currentTrackGainDb volumeSource=$currentTrackVolumeSource " +
                                        "playerId=${System.identityHashCode(player)} mediaUri=${player.currentMediaItem?.localConfiguration?.uri} " +
                                        "volume=${player.volume} nextUri=$playableUri"
                                )
                                Log.d(
                                    "PITCH_TRANSITION_DIAG",
                                    "APPLY_PLAYBACK_PARAMS pitch=$loadedPitchFactor speed=${loadedTrackMixSettings.tempo} songId=$uriString playable=$playableUri"
                                )
                                AudioEngine.applySpeedPitchForPreparedStart(
                                    player = player,
                                    speed = loadedTrackMixSettings.tempo,
                                    pitch = loadedPitchFactor,
                                    reason = "MainActivity.beforePrepare"
                                )
                            },
                            onError = {
                                if (manualCrossfadeTransitionTitle == null) {
                                    cancelTrimWatcher()
                                    val activeUri = playbackPlayer.currentMediaItem
                                        ?.localConfiguration
                                        ?.uri
                                        ?.toString()
                                    val nextArmed = PlaybackCoordinator.peekNextTrack() != null
                                    val durMs = runCatching { playbackPlayer.duration }.getOrDefault(C.TIME_UNSET)
                                    val posMs = runCatching { playbackPlayer.currentPosition }.getOrDefault(0L)
                                    val nearEnd =
                                        durMs > 0L &&
                                        durMs != C.TIME_UNSET &&
                                        posMs >= (durMs - 1500L).coerceAtLeast(0L)
                                    val treatAsEnded = nextArmed && nearEnd
                                    Log.d(
                                        SMP_PLAY_TRACE_TAG,
                                        "PLAYER_ON_ERROR requestedUri=$uriString activeUri=$activeUri playlist=$playlistName token=$myToken playWhenReady=${playbackPlayer.playWhenReady} isPlaying=${playbackPlayer.isPlaying} state=${playbackPlayer.playbackState} pos=$posMs dur=$durMs nextArmed=$nextArmed treatAsEnded=$treatAsEnded"
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
                            playbackPlayer.stop()
                            playbackPlayer.clearMediaItems()
                        }
                        isPlaying = false
                        LightCueDispatcher.resetGlobal()
                        PlaybackCoordinator.onPlayerStop()
                    }

                    if (shouldOpenPlayerScreen) {
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
                }

                val playWithCrossfade: (String, String?, String?, Boolean) -> Unit = { uriString, playlistName, playlistItemKey, openPlayerScreen ->
                    scope.launch {
                        playWithCrossfadeInternal(
                            uriString = uriString,
                            playlistName = playlistName,
                            playlistItemKey = playlistItemKey,
                            openPlayerScreen = openPlayerScreen
                        )
                    }
                }

                fun resolveSmpAudioTarget(
                    songId: String,
                    playlistName: String?,
                    showToastOnFailure: Boolean = false
                ): PlaybackRouter.Target.Audio? {
                    SmpLaunchTiming.markResolveSongStart(songId, playlistName)
                    val cachedSong = smpSongsById[songId]
                    Log.d(
                        SMP_PLAY_TRACE_TAG,
                        "RESOLVE_SMP start songId=$songId playlist=$playlistName cacheHit=${cachedSong != null}"
                    )
                    val song = cachedSong
                    if (song == null) {
                        Log.d(
                            SMP_PLAY_TRACE_TAG,
                            "RESOLVE_SMP miss songId=$songId playlist=$playlistName"
                        )
                        Log.w("SMP", "Lecture SMP impossible sans scan live: songId absent du cache=$songId playlist=$playlistName")
                        smpCacheRefreshTick++
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
                    SmpLaunchTiming.markResolveSongDone(song.id, audioPath)

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
                        val loadedTitle = sanitizeDisplayTrackTitle(song.title)
                        Log.d(
                            "PLAYER_DIAG",
                            "loadedTitle=${loadedTitle ?: "null"} songId=${song.id} uri=$resolvedUri"
                        )
                        if (
                            loadedTitle != null &&
                            sanitizeDisplayTrackTitle(TitleAliasesStore.getTitleForTrack(ctx, resolvedUri)) == null
                        ) {
                            TitleAliasesStore.setTitleForTrack(ctx, resolvedUri, song.title)
                            Log.d(
                                "PLAYER_DIAG",
                                "updatedLibraryTitle=$loadedTitle songId=${song.id} uri=$resolvedUri"
                            )
                        }
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

                fun resolvePlaylistAudioTarget(
                    playlistItemKey: String,
                    playlistName: String?,
                    rawTarget: PlaybackRouter.Target,
                    showToastOnFailure: Boolean = false
                ): PlaybackRouter.Target.Audio? {
                    val cleanPlaylistName = playlistName?.trim().takeUnless { it.isNullOrEmpty() }
                    if (cleanPlaylistName != null) {
                        val playlistSongId = PlaylistRepository.getPlaylistItem(cleanPlaylistName, playlistItemKey)
                            ?.songId
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                        if (playlistSongId != null) {
                            val resolvedBySongId = resolveSmpAudioTarget(
                                songId = playlistSongId,
                                playlistName = cleanPlaylistName,
                                showToastOnFailure = false
                            )
                            if (resolvedBySongId != null) {
                                Log.d(
                                    SMP_PLAY_TRACE_TAG,
                                    "PLAYLIST_META_RESOLVED item=$playlistItemKey songId=$playlistSongId playlist=$cleanPlaylistName uri=${resolvedBySongId.uri}"
                                )
                                return resolvedBySongId
                            }
                            Log.w(
                                SMP_PLAY_TRACE_TAG,
                                "PLAYLIST_META_FALLBACK item=$playlistItemKey songId=$playlistSongId playlist=$cleanPlaylistName"
                            )
                        }
                    }

                    return resolveAudioTarget(
                        target = rawTarget,
                        showToastOnFailure = showToastOnFailure
                    )
                }

                fun resolveManualCrossfadeRequest(): ManualCrossfadeRequest? {
                    val forcedNext = PlaybackCoordinator.peekNextTrack()
                    if (forcedNext != null) {
                        val target = PlaybackRouter.resolve(forcedNext.uri, forcedNext.playlist)
                        val resolvedTarget = resolvePlaylistAudioTarget(
                            playlistItemKey = forcedNext.uri,
                            playlistName = forcedNext.playlist,
                            rawTarget = target
                        ) ?: return null
                        return ManualCrossfadeRequest(
                            uri = resolvedTarget.uri,
                            playlist = resolvedTarget.playlist,
                            playlistItemKey = forcedNext.uri,
                            title = forcedNext.title.ifBlank {
                                resolveQueuedTrackTitle(
                                    playlistItemKey = forcedNext.uri,
                                    playlistName = forcedNext.playlist,
                                    fallbackUri = resolvedTarget.uri
                                )
                            },
                            clearForcedNextAfterSuccess = true
                        )
                    }

                    if (!isChaining) return null
                    val targetIndex = nextPlayableIndexAtOrAfter(chainQueue, chainIndex + 1) ?: return null
                    val itemKey = chainQueue.getOrNull(targetIndex) ?: return null
                    val target = PlaybackRouter.resolve(itemKey, chainPlaylist)
                    val resolvedTarget = resolvePlaylistAudioTarget(
                        playlistItemKey = itemKey,
                        playlistName = chainPlaylist,
                        rawTarget = target
                    ) ?: return null
                    return ManualCrossfadeRequest(
                        uri = resolvedTarget.uri,
                        playlist = resolvedTarget.playlist,
                        playlistItemKey = itemKey,
                        title = resolveQueuedTrackTitle(
                            playlistItemKey = itemKey,
                            playlistName = resolvedTarget.playlist ?: chainPlaylist,
                            fallbackUri = resolvedTarget.uri
                        ),
                        advanceChainIndexTo = targetIndex
                    )
                }

                fun launchManualCrossfadeToNext() {
                    if (manualCrossfadeTransitionTitle != null) {
                        return
                    }

                    val request = resolveManualCrossfadeRequest()
                    if (request == null || currentPlayingUri.isNullOrBlank()) {
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.player_crossfade_no_next_track),
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    manualCrossfadeJob?.cancel()
                    manualCrossfadeJob = scope.launch {
                        var adopted = false
                        try {
                            val resolvedPlayableUri = withContext(Dispatchers.IO) {
                                resolvePlayableUriStringForPlayback(ctx, request.uri)
                            }
                            if (resolvedPlayableUri.isNullOrBlank()) {
                                Toast.makeText(
                                    ctx,
                                    ctx.getString(R.string.player_crossfade_prepare_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }

                            if (resolvedPlayableUri != request.uri) {
                                PlaylistRepository.replaceSongUriEverywhere(
                                    oldUri = request.uri,
                                    newUri = resolvedPlayableUri
                                )
                            }

                            data class TargetMixSettings(
                                val gainDb: Int,
                                val volumeSource: String,
                                val tempo: Float,
                                val pitchSemi: Int
                            )

                            val targetMixSettings = withContext(Dispatchers.IO) {
                                TargetMixSettings(
                                    gainDb = clampTrackDb(TrackVolumePrefs.getDb(ctx, resolvedPlayableUri) ?: DEFAULT_TRACK_GAIN_DB),
                                    volumeSource = TrackVolumePrefs.getSource(ctx, resolvedPlayableUri),
                                    tempo = TrackTempoPrefs.getTempo(ctx, resolvedPlayableUri) ?: 1f,
                                    pitchSemi = TrackPitchPrefs.getSemi(ctx, resolvedPlayableUri) ?: 0
                                )
                            }
                            val forceSequentialForPitchSpeed = shouldUseSequentialTransition(
                                currentSpeed = currentTrackTempo,
                                currentPitchSemi = currentTrackPitchSemi,
                                nextSpeed = targetMixSettings.tempo,
                                nextPitchSemi = targetMixSettings.pitchSemi
                            )
                            Log.d(
                                "PITCH_TRANSITION_GUARD",
                                "TRANSITION_MODE=${if (forceSequentialForPitchSpeed) "SEQUENTIAL_NO_CROSSFADE" else "CROSSFADE_ALLOWED"} " +
                                    "source=manualCrossfade currentSpeed=$currentTrackTempo currentPitch=$currentTrackPitchSemi " +
                                    "nextSpeed=${targetMixSettings.tempo} nextPitch=${targetMixSettings.pitchSemi} uri=$resolvedPlayableUri"
                            )
                            if (forceSequentialForPitchSpeed) {
                                playWithCrossfadeInternal(
                                    uriString = resolvedPlayableUri,
                                    playlistName = request.playlist,
                                    playlistItemKey = request.playlistItemKey,
                                    openPlayerScreen = true
                                )
                                if (currentPlayingUri == resolvedPlayableUri) {
                                    request.advanceChainIndexTo?.let { nextIndex ->
                                        chainIndex = nextIndex
                                    }
                                    if (request.clearForcedNextAfterSuccess) {
                                        PlaybackCoordinator.clearNextTrack(reason = "triggered:manualSequential")
                                    }
                                }
                                adopted = true
                                return@launch
                            }

                            val transitionPlayer = AudioEngine.createTransitionPlayer(ctx)
                            val prepared = runCatching {
                                prepareTransitionPlayer(
                                    player = transitionPlayer,
                                    playableUri = resolvedPlayableUri,
                                    speed = targetMixSettings.tempo,
                                    pitchSemi = targetMixSettings.pitchSemi
                                )
                            }.getOrDefault(false)

                            if (!prepared) {
                                runCatching { transitionPlayer.release() }
                                Toast.makeText(
                                    ctx,
                                    ctx.getString(R.string.player_crossfade_prepare_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }

                            cancelTrimWatcher()
                            manualCrossfadePlayer = transitionPlayer
                            manualCrossfadeTransitionTitle = request.title
                            parsedLines = emptyList()
                            lyricsLoading = false
                            LightCueDispatcher.resetGlobal()
                            MidiCueDispatcher.resetForTrack(currentPlayingUri)

                            val fromVolume = runCatching { exoPlayer.volume }.getOrDefault(1f).coerceIn(0f, 16f)
                            val targetVolume = (dbToLinearAttenuation(targetMixSettings.gainDb) * playerMasterLevel)
                                .coerceIn(0f, 16f)
                            transitionPlayer.volume = 0f
                            transitionPlayer.playWhenReady = true
                            transitionPlayer.play()
                            delay(currentManualCrossfadePrerollMs())

                            val steps = 30
                            val stepDelayMs = (currentManualCrossfadeDurationMs() / steps).coerceAtLeast(1L)
                            repeat(steps) { step ->
                                val progress = (step + 1).toFloat() / steps.toFloat()
                                runCatching { exoPlayer.volume = (fromVolume * (1f - progress)).coerceIn(0f, 16f) }
                                runCatching { transitionPlayer.volume = (targetVolume * progress).coerceIn(0f, 16f) }
                                delay(stepDelayMs)
                            }

                            currentTrackGainDb = targetMixSettings.gainDb
                            currentTrackVolumeSource = targetMixSettings.volumeSource
                            currentTrackTempo = targetMixSettings.tempo
                            currentTrackPitchSemi = targetMixSettings.pitchSemi
                            currentPlayingUri = resolvedPlayableUri
                            currentPlayingTitle = request.title
                            currentPlayingPlaylist = request.playlist
                            armPlaylistPlaybackState(
                                playlistName = request.playlist,
                                playbackUri = resolvedPlayableUri,
                                playlistItemKey = request.playlistItemKey
                            )
                            request.advanceChainIndexTo?.let { nextIndex ->
                                chainIndex = nextIndex
                            }
                            if (request.clearForcedNextAfterSuccess) {
                                PlaybackCoordinator.clearNextTrack(reason = "triggered:manualCrossfade")
                            }
                            if (!request.playlist.isNullOrBlank()) {
                                selectedQuickPlaylist = request.playlist
                            }
                            currentLyricsColor = Color.White

                            AudioEngine.adoptTransitionPlayer(ctx, transitionPlayer) {
                                onEnded.value.invoke()
                            }
                            adopted = true
                            manualCrossfadePlayer = null
                            AudioEngine.applyTrackGainDb(targetMixSettings.gainDb)
                            AudioEngine.applySpeedPitchForPreparedStart(
                                player = transitionPlayer,
                                speed = targetMixSettings.tempo,
                                pitch = 2f.pow(targetMixSettings.pitchSemi.coerceIn(-6, 6) / 12f),
                                reason = "MainActivity.manualCrossfadeAdopt"
                            )
                            PlaybackCoordinator.onPlayerStart()
                            isPlaying = true
                            selectedTab = BottomTab.Player
                            latestSessionSnapshot = SessionSnapshot(
                                tabKey = TAB_PLAYER,
                                quickPlaylist = selectedQuickPlaylist,
                                openedPlaylist = openedPlaylist,
                                currentPlayingUri = currentPlayingUri,
                                currentPlayingPlaylist = currentPlayingPlaylist
                            )
                            persistSession(reason = "manualCrossfade")
                            playlistSessionWriteJob?.cancel()
                            playlistSessionWriteJob = scope.launch(Dispatchers.IO) {
                                try {
                                    SessionPrefs.saveLastSession(
                                        context = ctx,
                                        trackUri = resolvedPlayableUri,
                                        playlistName = request.playlist,
                                        songId = resolveSessionSongIdFromTrackUri(resolvedPlayableUri)
                                    )
                                } catch (_: CancellationException) {
                                }
                            }
                            runCatching {
                                historyRepository.logPlay(
                                    source = PlaySource.BACKING,
                                    title = request.title,
                                    artist = null,
                                    uri = resolvedPlayableUri
                                )
                            }
                        } catch (cancelError: CancellationException) {
                            throw cancelError
                        } catch (error: Throwable) {
                            Log.e(SMP_PLAY_TRACE_TAG, "MANUAL_CROSSFADE_FAILED", error)
                            if (manualCrossfadeTransitionTitle != null) {
                                Toast.makeText(
                                    ctx,
                                    ctx.getString(R.string.player_crossfade_prepare_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } finally {
                            if (!adopted) {
                                manualCrossfadePlayer?.let { player ->
                                    runCatching { player.stop() }
                                    runCatching { player.release() }
                                }
                                manualCrossfadePlayer = null
                            }
                            manualCrossfadeTransitionTitle = null
                            manualCrossfadeJob = null
                        }
                    }
                }

                fun playlistReferencesForSongId(songId: String): List<Pair<String, String>> {
                    val cleanSongId = songId.trim()
                    if (cleanSongId.isEmpty()) return emptyList()
                    return PlaylistRepository.getPlaylists().flatMap { playlistName ->
                        PlaylistRepository.getAllItemsRaw(playlistName)
                            .asSequence()
                            .filter { item ->
                                item.songId?.trim() == cleanSongId ||
                                    resolveSessionSongIdFromTrackUri(item.uri) == cleanSongId
                            }
                            .map { playlistName to it.uri }
                            .distinct()
                            .toList()
                    }
                }

                suspend fun deleteSmpSongById(songId: String): Boolean {
                    val cleanSongId = songId.trim()
                    if (cleanSongId.isEmpty()) return false

                    data class SmpDeleteIoResult(
                        val success: Boolean,
                        val runtimeAudioUri: String?
                    )

                    val playlistReferences = playlistReferencesForSongId(cleanSongId)
                    val currentMatches = resolveSessionSongIdFromTrackUri(currentPlayingUri) == cleanSongId
                    val lastSessionState = SessionPrefs.getLastSessionState(ctx)
                    val lastSessionSongId = lastSessionState.songId?.takeIf { it.isNotBlank() }
                        ?: resolveSessionSongIdFromTrackUri(lastSessionState.trackUri)
                    val lastSessionMatches = lastSessionSongId == cleanSongId

                    val ioResult = withContext(Dispatchers.IO) {
                        val runtimeSong = runCatching {
                            smpLibraryScanner.findSongById(cleanSongId)
                        }.getOrNull()
                        val runtimeDir = runtimeSong
                            ?.storageFolder
                            ?.takeIf { it.isNotBlank() }
                            ?.let(::File)
                            ?: File(ctx.filesDir, "tracks/$cleanSongId")
                        val runtimeAudioUri = runtimeSong
                            ?.audioPath
                            ?.takeIf { it.isNotBlank() }
                            ?.let { path -> Uri.fromFile(File(path)).toString() }

                        SmpArchiveFinalizeScheduler.cancel(ctx, cleanSongId)
                        SmpArchiveFinalizeStore.clear(ctx, cleanSongId)

                        val archiveDeleteResult = SmpWorkspaceArchiveStore.deleteArchivesForSongId(
                            context = ctx,
                            songId = cleanSongId
                        )
                        if (!archiveDeleteResult.isSuccess) {
                            Log.w(
                                "SMP_TRACE",
                                "DELETE_SMP archive_failed songId=$cleanSongId deleted=${archiveDeleteResult.deletedCount} failed=${archiveDeleteResult.failedCount} reason=${archiveDeleteResult.failureReason}"
                            )
                            return@withContext SmpDeleteIoResult(
                                success = false,
                                runtimeAudioUri = runtimeAudioUri
                            )
                        }

                        val runtimeDeleted = !runtimeDir.exists() || runtimeDir.deleteRecursively()
                        if (!runtimeDeleted) {
                            Log.w(
                                "SMP_TRACE",
                                "DELETE_SMP runtime_failed songId=$cleanSongId dir=${runtimeDir.absolutePath}"
                            )
                        }
                        SmpDeleteIoResult(
                            success = runtimeDeleted,
                            runtimeAudioUri = runtimeAudioUri
                        )
                    }

                    if (!ioResult.success) {
                        return false
                    }

                    playlistReferences.forEach { (playlistName, uriString) ->
                        PlaylistRepository.removeSongFromPlaylist(playlistName, uriString)
                    }

                    if (currentMatches) {
                        playlistSessionWriteJob?.cancel()
                        sessionSaveJob?.cancel()
                        cancelTrimWatcher()
                        stopChainPlayback()
                        runCatching {
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                        }
                        isPlaying = false
                        lyricsLoading = false
                        parsedLines = emptyList()
                        currentPlayingUri = null
                        currentPlayingTitle = null
                        currentPlayingPlaylist = null
                        currentPlayingPlaylistItemKey = null
                        currentPlayToken += 1
                        LightCueDispatcher.resetGlobal()
                        PlaybackCoordinator.onPlayerStop()
                        PlaylistRepository.clearNowPlaying()
                    }

                    if (lastSessionMatches) {
                        SessionPrefs.clearLastSession(ctx)
                    }

                    if (currentMatches) {
                        persistCurrentUiSession(reason = "smpDeleteCurrent")
                    } else if (lastSessionMatches) {
                        persistCurrentUiSession(reason = "smpDeleteSessionCleanup")
                    }

                    runCatching {
                        TitleAliasesStore.clearTitleForTrack(ctx, buildSmpItem(cleanSongId))
                    }
                    ioResult.runtimeAudioUri?.let { runtimeAudioUri ->
                        runCatching {
                            TitleAliasesStore.clearTitleForTrack(ctx, runtimeAudioUri)
                        }
                    }

                    lastImportedSmpUiSignal = lastImportedSmpUiSignal?.takeUnless { signal ->
                        signal.songId == cleanSongId
                    }
                    smpSongsById = smpSongsById - cleanSongId
                    smpCacheRefreshTick++
                    Log.i(
                        "SMP_TRACE",
                        "DELETE_SMP success songId=$cleanSongId currentCleared=$currentMatches lastSessionCleared=$lastSessionMatches playlistRefsRemoved=${playlistReferences.size}"
                    )
                    return true
                }

                fun playChainFrom(startIndex: Int): Boolean {
                    if (!isChaining) return false
                    var idx = startIndex
                    while (idx in chainQueue.indices) {
                        val playableIndex = nextPlayableIndexAtOrAfter(chainQueue, idx) ?: return false
                        when (val target = PlaybackRouter.resolve(chainQueue[playableIndex], chainPlaylist)) {
                            is PlaybackRouter.Target.Audio,
                            is PlaybackRouter.Target.Smp -> {
                                val resolvedTarget = resolvePlaylistAudioTarget(
                                    playlistItemKey = chainQueue[playableIndex],
                                    playlistName = chainPlaylist,
                                    rawTarget = target
                                )
                                if (resolvedTarget == null) {
                                    idx = playableIndex + 1
                                    continue
                                }
                                chainIndex = playableIndex
                                playWithCrossfade(
                                    resolvedTarget.uri,
                                    resolvedTarget.playlist,
                                    chainQueue[playableIndex],
                                    true
                                )
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
                                val resolvedTarget = resolvePlaylistAudioTarget(
                                    playlistItemKey = forcedNext.uri,
                                    playlistName = forcedNext.playlist,
                                    rawTarget = target
                                )
                                if (resolvedTarget == null) {
                                    false
                                } else {
                                    stopChainPlayback()
                                playWithCrossfade(
                                    resolvedTarget.uri,
                                    resolvedTarget.playlist,
                                    forcedNext.uri,
                                    true
                                )
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
                        initialTabKey?.let {
                            selectedTab = tabFromKey(
                                key = it,
                                showDjTab = showDjTab,
                                showMainBusTab = showMainBusTab
                            )
                        }
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

                    val restoreStartMs = SystemClock.elapsedRealtime()
                    val restoreThreadName = Thread.currentThread().name
                    Log.e(
                        "ANR_RESTORE",
                        "session_restore:start thread=$restoreThreadName canUseStorage=$canUseStorage playlistsReady=$playlistsReady root=$rootKey"
                    )
                    mark("SessionRestore PhaseB:start canUseStorage=$canUseStorage playlistsReady=$playlistsReady root=$rootKey")
                    if (!canUseStorage || !playlistsReady) {
                        Log.e(
                            "ANR_RESTORE",
                            "session_restore:skip durationMs=${SystemClock.elapsedRealtime() - restoreStartMs} thread=$restoreThreadName canUseStorage=$canUseStorage playlistsReady=$playlistsReady"
                        )
                        return@LaunchedEffect
                    }

                    val prefsStartMs = SystemClock.elapsedRealtime()
                    val restoredTabKey = SessionPrefs.getTab(ctx)
                    val restoredQuickPlaylist = SessionPrefs.getQuickPlaylist(ctx)
                    val restoredOpenedPlaylist = SessionPrefs.getOpenedPlaylist(ctx)
                    val restoredLastSession = SessionPrefs.getLastSessionState(ctx)
                    Log.e(
                        "ANR_RESTORE",
                        "session_restore:prefs_done durationMs=${SystemClock.elapsedRealtime() - prefsStartMs} thread=$restoreThreadName"
                    )
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

                    restoredTabKey?.let {
                        selectedTab = tabFromKey(
                            key = it,
                            showDjTab = showDjTab,
                            showMainBusTab = showMainBusTab
                        )
                    }
                    selectedQuickPlaylist = restoredQuickPlaylist ?: lastPlaylist
                    openedPlaylist = restoredOpenedPlaylist

                    if (!lastUri.isNullOrBlank()) {
                        currentPlayingUri = lastUri
                        currentPlayingTitle = sanitizeDisplayTrackTitle(TitleAliasesStore.getTitleForTrack(ctx, lastUri))
                            ?: sanitizeDisplayTrackTitle(indexAll.firstOrNull { it.uriString == lastUri }?.name)
                            ?: sanitizeDisplayTrackTitle(Uri.parse(lastUri).lastPathSegment)
                        currentPlayingPlaylist = lastPlaylist
                        currentPlayingPlaylistItemKey = null

                        val lyricsLoadStartMs = SystemClock.elapsedRealtime()
                        val overrideText = withContext(Dispatchers.IO) {
                            LrcStorage.loadForTrack(ctx, lastUri)?.takeIf { it.isNotBlank() }
                        }
                        Log.e(
                            "ANR_RESTORE",
                            "session_restore:lyrics_load_done durationMs=${SystemClock.elapsedRealtime() - lyricsLoadStartMs} thread=$restoreThreadName uri=$lastUri hasLyrics=${!overrideText.isNullOrBlank()}"
                        )
                        Log.d(
                            "SMP_TRACE",
                            "RESTORE_LOAD uri=$lastUri playlist=$lastPlaylist loadedLyrics=${!overrideText.isNullOrBlank()} lyricsHash=${overrideText?.hashCode()}"
                        )
                        val parseStartMs = SystemClock.elapsedRealtime()
                        parsedLines = if (overrideText != null) parseLrc(overrideText) else emptyList()
                        Log.e(
                            "ANR_RESTORE",
                            "session_restore:parse_done durationMs=${SystemClock.elapsedRealtime() - parseStartMs} thread=$restoreThreadName parsedLines=${parsedLines.size}"
                        )

                        // IMPORTANT:
                        // -5 dB est la valeur par défaut volontaire (headroom).
                        // NE PAS réinitialiser automatiquement si la valeur est 0.
                        // 0 dB est un choix utilisateur valide.
                        currentTrackGainDb = clampTrackDb(
                            TrackVolumePrefs.getDb(ctx, lastUri) ?: DEFAULT_TRACK_GAIN_DB
                        )
                        currentTrackVolumeSource = TrackVolumePrefs.getSource(ctx, lastUri)

                        currentTrackTempo = TrackTempoPrefs.getTempo(ctx, lastUri) ?: 1f
                        currentTrackPitchSemi = TrackPitchPrefs.getSemi(ctx, lastUri) ?: 0
                    }

                    sessionRestored = true
                    isRestoringSession = false
                    Log.e(
                        "ANR_RESTORE",
                        "session_restore:end durationMs=${SystemClock.elapsedRealtime() - restoreStartMs} thread=$restoreThreadName restored=${sessionRestored} hasLastUri=${!lastUri.isNullOrBlank()}"
                    )
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
                    activeHardwareInputRoute = when {
                        isSearchOpen -> HardwareInputRoute.NONE
                        textPrompterId != null -> HardwareInputRoute.PROMPTER
                        selectedTab is BottomTab.Player -> HardwareInputRoute.PLAYER
                        selectedTab is BottomTab.QuickPlaylists -> HardwareInputRoute.QUICK_PLAYLISTS
                        selectedTab is BottomTab.Library && libraryKeyboardNavigationEnabled ->
                            HardwareInputRoute.LIBRARY_SONGS
                        else -> HardwareInputRoute.NONE
                    }
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

                LaunchedEffect(showDjTab, showMainBusTab, selectedTab, isGlobalMixOpen) {
                    val safeTab = sanitizeTab(
                        tab = selectedTab,
                        showDjTab = showDjTab,
                        showMainBusTab = showMainBusTab
                    )
                    if (safeTab != selectedTab) {
                        selectedTab = safeTab
                        persistCurrentUiSession(reason = "tabVisibilityChanged", tabOverride = safeTab)
                    }
                    if (!showMainBusTab && isGlobalMixOpen) {
                        isGlobalMixOpen = false
                    }
                }
                LaunchedEffect(adaptiveTokens.tabletMode, tabletExperimentalModeEnabled) {
                    if (!adaptiveTokens.tabletMode || !tabletExperimentalModeEnabled) {
                        isTabletCockpitDestinationOpen = false
                        tabletRightPanel = TabletSplitRightPanel.LYRICS
                    }
                }

                val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                val shouldHideBottomBarForPlayerIme =
                    selectedTab is BottomTab.Player && imeBottomPadding > 0.dp
                val shouldHideBottomBarForTabletSplit =
                    adaptiveTokens.tabletMode &&
                        tabletExperimentalModeEnabled &&
                        textPrompterId == null &&
                        !isFillerSettingsOpen &&
                        !isGlobalMixOpen &&
                        !isMixerPreviewOpen &&
                        (selectedTab is BottomTab.Player || selectedTab is BottomTab.QuickPlaylists)
                val shouldShowTabletCockpitDestinationChrome =
                    adaptiveTokens.tabletMode &&
                        tabletExperimentalModeEnabled &&
                        isTabletCockpitDestinationOpen &&
                        textPrompterId == null &&
                        !isNotesOpen &&
                        !isSearchOpen &&
                        !isMixerPreviewOpen &&
                        (
                            isFillerSettingsOpen ||
                                isGlobalMixOpen ||
                                selectedTab is BottomTab.Home ||
                                selectedTab is BottomTab.Library ||
                                selectedTab is BottomTab.Dj ||
                                selectedTab is BottomTab.More
                        )
                fun returnToTabletCockpit() {
                    isTabletCockpitDestinationOpen = false
                    isTabletSplitMenuOpen = false
                    textPrompterId = null
                    isNotesOpen = false
                    isFillerSettingsOpen = false
                    isGlobalMixOpen = false
                    isSearchOpen = false
                    isMixerPreviewOpen = false
                    tabletRightPanel = TabletSplitRightPanel.LYRICS
                    setTabAndPersist(BottomTab.Player, reason = "tabletCockpitReturn")
                }

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        if (
                            !shouldHideBottomBarForPlayerIme &&
                            !shouldHideBottomBarForTabletSplit &&
                            !shouldShowTabletCockpitDestinationChrome
                        ) {
                            BottomTabsBar(
                                selected = selectedTab,
                                showMainBusTab = showMainBusTab,
                                showDjTab = showDjTab,
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
                                    isNotesOpen = false
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
                    val tabletCockpitDestinationContentModifier =
                        if (shouldShowTabletCockpitDestinationChrome) {
                            contentModifier.padding(top = 48.dp)
                        } else {
                            contentModifier
                        }

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
                        Box(modifier = tabletCockpitDestinationContentModifier.fillMaxSize()) {
                            FillerSoundScreen(
                                context = ctx,
                                onBack = {
                                    isTabletCockpitDestinationOpen = false
                                    isFillerSettingsOpen = false
                                }
                            )
                        }
                    } else if (isGlobalMixOpen && EditionConfig.isPro) {
                        GlobalMixScreen(
                            modifier = tabletCockpitDestinationContentModifier,
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
                            onBack = {
                                isTabletCockpitDestinationOpen = false
                                isGlobalMixOpen = false
                            }
                        )
                    } else if (isGlobalMixOpen) {
                        isGlobalMixOpen = false
                    } else {
                        // ✅ overlay PROMPTEUR
                        textPrompterId?.let { tid ->
                            TextPrompterScreen(
                                modifier = contentModifier,
                                songId = tid,
                                onClose = { textPrompterId = null },
                                hardwareActionToken = prompterHardwareActionToken,
                                hardwareAction = prompterHardwareAction
                            )
                        } ?: run {
                            val tabletSplitStateEligible =
                                adaptiveTokens.tabletMode &&
                                    tabletExperimentalModeEnabled &&
                                    (selectedTab is BottomTab.Player || selectedTab is BottomTab.QuickPlaylists)
                            tabStateHolder.SaveableStateProvider(
                                key = if (tabletSplitStateEligible) {
                                    "tablet_split_live"
                                } else {
                                    "tab_${tabKeyOf(selectedTab)}"
                                }
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
                                LocalLinkExperimentalSenderRuntime.updateLiveSource(
                                    currentSongId = { currentPlayingSongId },
                                    currentSongTitle = {
                                        currentPlayingTitle
                                            ?: currentPlayingSongId
                                                ?.let { songId -> sanitizeDisplayTrackTitle(smpSongsById[songId]?.title) }
                                            ?: currentPlayingUri
                                                ?.let { uri -> sanitizeDisplayTrackTitle(TitleAliasesStore.getTitleForTrack(ctx, uri)) }
                                            ?: currentPlayingUri
                                                ?.let { uri -> sanitizeDisplayTrackTitle(indexAll.firstOrNull { it.uriString == uri }?.name) }
                                            ?: currentPlayingUri
                                                ?.let { uri -> sanitizeDisplayTrackTitle(Uri.parse(uri).lastPathSegment) }
                                    },
                                    currentParsedLines = { parsedLines },
                                    currentPositionMs = {
                                        runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
                                    },
                                    currentDurationMs = {
                                        runCatching {
                                            resolveEffectiveDurationMs(
                                                requestedUri = currentPlayingUri,
                                                activeUri = exoPlayer.currentMediaItem
                                                    ?.localConfiguration
                                                    ?.uri
                                                    ?.toString()
                                            )
                                        }.getOrDefault(C.TIME_UNSET)
                                            .takeIf { it > 0L && it != C.TIME_UNSET }
                                    },
                                    isPlaying = {
                                        runCatching { exoPlayer.isPlaying }.getOrDefault(isPlaying)
                                    },
                                    loadParsedLines = {
                                        val uri = currentPlayingUri
                                        when {
                                            uri.isNullOrBlank() -> emptyList()
                                            else -> {
                                                LyricsMemoryCache.updateScope(LrcStorage.currentWorkspaceScopeKey(ctx))
                                                LyricsMemoryCache.get(uri)?.parsedLines?.takeIf { it.isNotEmpty() }
                                                    ?: withContext(Dispatchers.IO) {
                                                        LrcStorage.loadForTrack(ctx, uri)
                                                            ?.takeIf { it.isNotBlank() }
                                                            ?.let { parseLrc(it) }
                                                            .orEmpty()
                                                    }
                                            }
                                        }
                                    }
                                )
                                fun prepareTabletSplitMenuNavigation() {
                                    isTabletSplitMenuOpen = false
                                    isTabletShortcutMoreOpen = false
                                    textPrompterId = null
                                    isNotesOpen = false
                                    isFillerSettingsOpen = false
                                    isGlobalMixOpen = false
                                    isSearchOpen = false
                                    isMixerPreviewOpen = false
                                }

                                fun openTabletSplitLyrics() {
                                    prepareTabletSplitMenuNavigation()
                                    isTabletCockpitDestinationOpen = false
                                    closeMixSignal++
                                    tabletRightPanel = TabletSplitRightPanel.LYRICS
                                }

                                fun openTabletSplitLibrary(openSearch: Boolean = false) {
                                    prepareTabletSplitMenuNavigation()
                                    isTabletCockpitDestinationOpen = false
                                    if (!openSearch) {
                                        tabletLibrarySearchToggleSignal = 0
                                        tabletLibrarySearchCloseSignal++
                                    }
                                    tabletRightPanel = TabletSplitRightPanel.LIBRARY
                                    if (openSearch) {
                                        tabletLibrarySearchToggleSignal++
                                    }
                                }

                                fun openTabletSplitPlaylist() {
                                    prepareTabletSplitMenuNavigation()
                                    isTabletCockpitDestinationOpen = false
                                    tabletRightPanel = TabletSplitRightPanel.LYRICS
                                    setTabAndPersist(
                                        BottomTab.QuickPlaylists,
                                        reason = "tabletSplitShortcutPlaylist"
                                    )
                                }

                                fun openTabletSplitFiller() {
                                    prepareTabletSplitMenuNavigation()
                                    isTabletCockpitDestinationOpen = false
                                    tabletRightPanel = TabletSplitRightPanel.BACKGROUND_SOUND
                                }

                                fun openTabletSplitDj() {
                                    prepareTabletSplitMenuNavigation()
                                    isTabletCockpitDestinationOpen = false
                                    tabletRightPanel = TabletSplitRightPanel.DJ
                                }

                                fun openTabletSplitSettings() {
                                    prepareTabletSplitMenuNavigation()
                                    isTabletCockpitDestinationOpen = false
                                    tabletRightPanel = TabletSplitRightPanel.SETTINGS
                                }

                                fun openTabletSplitTuner() {
                                    prepareTabletSplitMenuNavigation()
                                    isTabletCockpitDestinationOpen = false
                                    tabletRightPanel = TabletSplitRightPanel.TUNER
                                }

                                fun openTabletSplitSearch() {
                                    prepareTabletSplitMenuNavigation()
                                    isTabletCockpitDestinationOpen = false
                                    if (tabletRightPanel == TabletSplitRightPanel.LIBRARY) {
                                        tabletLibrarySearchToggleSignal++
                                    } else if (!selectedQuickPlaylist.isNullOrBlank()) {
                                        playlistSearchToggleSignal++
                                    } else {
                                        tabletRightPanel = TabletSplitRightPanel.LIBRARY
                                        tabletLibrarySearchToggleSignal++
                                    }
                                }

                                fun openTabletShortcutNotes() {
                                    prepareTabletSplitMenuNavigation()
                                    isNotesOpen = true
                                }

                                fun openTabletShortcutAllPlaylists() {
                                    prepareTabletSplitMenuNavigation()
                                    setTabAndPersist(
                                        BottomTab.AllPlaylists,
                                        reason = "tabletShortcutAllPlaylists"
                                    )
                                }

                                fun openTabletShortcutPrompter() {
                                    prepareTabletSplitMenuNavigation()
                                    setTabAndPersist(
                                        BottomTab.QuickPlaylists,
                                        reason = "tabletShortcutPrompter"
                                    )
                                    openPrompterSignal++
                                }

                                fun openTabletShortcutTuner() {
                                    openTabletSplitTuner()
                                }

                                fun currentLufsPlaybackConfig(): SmpConfig.PlaybackConfig? {
                                    currentPlayingUri
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { TrackVolumePrefs.getPlaybackConfig(ctx, it) }
                                        ?.let { return it }
                                    val songId = currentPlayingSongId?.takeIf { it.isNotBlank() } ?: return null
                                    val song = smpSongsById[songId] ?: return null
                                    return SmpConfig.readPlaybackFromSongUnit(song)
                                }

                                fun canAdjustTabletLiveGain(): Boolean {
                                    if (currentPlayingUri.isNullOrBlank()) return false
                                    return !currentPlayingSongId.isNullOrBlank()
                                }

                                fun adjustTabletLiveGain(deltaDb: Int) {
                                    val sourceUri = currentPlayingUri ?: return
                                    val playback = currentLufsPlaybackConfig()
                                    val currentFinalDb = playback?.volumeDb ?: currentTrackGainDb
                                    val safeDb = clampTrackDb(currentFinalDb + deltaDb)
                                    currentTrackGainDb = safeDb
                                    AudioEngine.applyTrackGainDb(safeDb)

                                    val measured = playback?.lufsMeasured
                                    val target = playback?.lufsTarget
                                    val auto = if (measured != null && target != null) {
                                        playback.lufsAutoDb ?: (target - measured)
                                    } else {
                                        null
                                    }

                                    if (measured != null && target != null && auto != null) {
                                        val manual = ((measured + safeDb) - target)
                                            .roundToInt()
                                            .coerceIn(MIN_TRACK_DB, MAX_TRACK_DB)
                                        currentTrackVolumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                TrackVolumePrefs.saveLufsDb(
                                                    context = ctx,
                                                    uri = sourceUri,
                                                    db = safeDb,
                                                    measuredLufs = measured,
                                                    targetLufs = target,
                                                    autoDb = auto,
                                                    manualDb = manual
                                                )
                                            }
                                        }
                                        return
                                    }

                                    currentTrackVolumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            TrackVolumePrefs.saveDb(ctx, sourceUri, safeDb)
                                        }
                                    }
                                }

                                @Composable
                                fun TabletSplitCockpitMenuButton() {
                                    Box {
                                        IconButton(onClick = { isTabletSplitMenuOpen = true }) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                contentDescription = stringResource(R.string.common_cd_options),
                                                tint = Color.White.copy(alpha = 0.72f)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = isTabletSplitMenuOpen,
                                            onDismissRequest = { isTabletSplitMenuOpen = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.player_view_lyrics)) },
                                                onClick = ::openTabletSplitLyrics
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.tablet_split_menu_library)) },
                                                onClick = { openTabletSplitLibrary(openSearch = false) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.tablet_split_menu_main_bus)) },
                                                enabled = EditionConfig.isPro && showMainBusTab,
                                                onClick = {
                                                    prepareTabletSplitMenuNavigation()
                                                    isTabletCockpitDestinationOpen = false
                                                    tabletRightPanel = TabletSplitRightPanel.MAIN_BUS
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.tablet_split_menu_playlist)) },
                                                onClick = ::openTabletSplitPlaylist
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.tablet_split_menu_filler)) },
                                                onClick = ::openTabletSplitFiller
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.tablet_split_menu_dj)) },
                                                enabled = showDjTab,
                                                onClick = ::openTabletSplitDj
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.tablet_split_menu_search)) },
                                                onClick = ::openTabletSplitSearch
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.tablet_split_menu_settings)) },
                                                onClick = ::openTabletSplitSettings
                                            )
                                        }
                                    }
                                }

                                @Composable
                                fun TabletSplitTopNavigationShortcuts() {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(
                                            8.dp,
                                            Alignment.End
                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            modifier = Modifier.size(36.dp),
                                            enabled = EditionConfig.isPro && showMainBusTab,
                                            onClick = {
                                                prepareTabletSplitMenuNavigation()
                                                isTabletCockpitDestinationOpen = false
                                                tabletRightPanel = TabletSplitRightPanel.MAIN_BUS
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Home,
                                                contentDescription = stringResource(R.string.tablet_split_menu_main_bus),
                                                tint = Color.White.copy(
                                                    alpha = if (EditionConfig.isPro && showMainBusTab) 0.78f else 0.32f
                                                )
                                            )
                                        }
                                        IconButton(
                                            modifier = Modifier.size(36.dp),
                                            onClick = ::openTabletSplitLyrics
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MusicNote,
                                                contentDescription = stringResource(R.string.tab_player),
                                                tint = Color.White.copy(alpha = 0.78f)
                                            )
                                        }
                                        IconButton(
                                            modifier = Modifier.size(36.dp),
                                            onClick = ::openTabletSplitFiller
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Waves,
                                                contentDescription = stringResource(R.string.tablet_split_menu_filler),
                                                tint = Color.White.copy(alpha = 0.78f)
                                            )
                                        }
                                        IconButton(
                                            modifier = Modifier.size(36.dp),
                                            enabled = showDjTab,
                                            onClick = ::openTabletSplitDj
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Headset,
                                                contentDescription = stringResource(R.string.tablet_split_menu_dj),
                                                tint = Color.White.copy(alpha = if (showDjTab) 0.78f else 0.32f)
                                            )
                                        }
                                        IconButton(
                                            modifier = Modifier.size(36.dp),
                                            onClick = { openTabletSplitLibrary(openSearch = false) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Folder,
                                                contentDescription = stringResource(R.string.tablet_split_menu_library),
                                                tint = Color.White.copy(alpha = 0.78f)
                                            )
                                        }
                                        IconButton(
                                            modifier = Modifier.size(36.dp),
                                            onClick = ::openTabletSplitTuner
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.GraphicEq,
                                                contentDescription = stringResource(R.string.main_menu_tuner),
                                                tint = Color.White.copy(alpha = 0.78f)
                                            )
                                        }
                                        IconButton(
                                            modifier = Modifier.size(36.dp),
                                            onClick = ::openTabletSplitSearch
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Search,
                                                contentDescription = stringResource(R.string.tablet_split_menu_search),
                                                tint = Color.White.copy(alpha = 0.78f)
                                            )
                                        }
                                        TabletSplitCockpitMenuButton()
                                        Box {
                                            IconButton(
                                                modifier = Modifier.size(36.dp),
                                                onClick = { isTabletShortcutMoreOpen = true }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.MoreVert,
                                                    contentDescription = stringResource(R.string.common_cd_options),
                                                    tint = Color.White.copy(alpha = 0.78f)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = isTabletShortcutMoreOpen,
                                                onDismissRequest = { isTabletShortcutMoreOpen = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.main_menu_notes)) },
                                                    onClick = ::openTabletShortcutNotes
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.main_menu_playlists)) },
                                                    onClick = ::openTabletShortcutAllPlaylists
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.main_menu_prompter)) },
                                                    onClick = ::openTabletShortcutPrompter
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.main_menu_more)) },
                                                    onClick = ::openTabletSplitSettings
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.main_menu_tuner)) },
                                                    onClick = ::openTabletShortcutTuner
                                                )
                                            }
                                        }
                                    }
                                }

                                val playerPane: @Composable (Modifier) -> Unit = { paneModifier ->
                                    PlayerScreen(
                                        modifier = paneModifier,
                                        exoPlayer = exoPlayer,
                                        closeMixSignal = closeMixSignal,
                                        isPlaying = isPlaying,
                                        onIsPlayingChange = { shouldPlay ->
                                            if (manualCrossfadeTransitionTitle != null) {
                                                return@PlayerScreen
                                            }
                                            isPlaying = shouldPlay
                                            if (shouldPlay) exoPlayer.play() else exoPlayer.pause()
                                        },
                                        parsedLines = parsedLines,
                                        lyricsLoading = lyricsLoading,
                                        onParsedLinesChange = { parsedLines = it },
                                        highlightColor = currentLyricsColor,
                                        currentTrackUri = currentPlayingUri,
                                        nextTrackTitle = effectiveNextTrackTitle,
                                        currentTrackGainDb = currentTrackGainDb,
                                        currentTrackVolumeSource = currentTrackVolumeSource,
                                        onTrackGainChange = { db ->
                                            if (currentTrackVolumeSource == SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS) {
                                                return@PlayerScreen
                                            }
                                            val safeDb = clampTrackDb(db)
                                            currentTrackGainDb = safeDb
                                            AudioEngine.applyTrackGainDb(safeDb)
                                        },
                                        onTrackGainCommit = { db ->
                                            if (currentTrackVolumeSource == SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS) {
                                                return@PlayerScreen
                                            }
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
                                        currentSongId = currentPlayingSongId,
                                        onOpenArrangementHub = {
                                            moreNavigationTarget = "arrangement_from_tempo"
                                            moreNavigationToken += 1
                                            setTabAndPersist(BottomTab.More, reason = "playerOpenArrangementHub")
                                        },
                                        manualTransitionTargetTitle = manualCrossfadeTransitionTitle,
                                        onManualCrossfadeToNext = { launchManualCrossfadeToNext() },
                                        onImportGeneratedSmp = autoImportGeneratedSmp,
                                        requestedNavigationTarget = playerNavigationTarget,
                                        requestedNavigationToken = playerNavigationToken,
                                        onOpenWaveform = {
                                            moreNavigationTarget = "waveform_preview"
                                            moreNavigationToken += 1
                                            setTabAndPersist(BottomTab.More, reason = "playerOpenWaveform")
                                        },
                                        getPositionMs = { exoPlayer.currentPosition },
                                        getEffectiveDurationMs = {
                                            resolveEffectiveDurationMs(
                                                requestedUri = currentPlayingUri,
                                                activeUri = exoPlayer.currentMediaItem
                                                    ?.localConfiguration
                                                    ?.uri
                                                    ?.toString()
                                            )
                                        },
                                        seekToMs = { ms -> exoPlayer.seekTo(ms) },
                                        compactTabletLayout = adaptiveTokens.tabletMode &&
                                            tabletExperimentalModeEnabled,
                                        showAutoReturnButton = false,
                                        showLiveGainControls = true,
                                        liveGainControlsEnabled = canAdjustTabletLiveGain(),
                                        onLiveGainDelta = ::adjustTabletLiveGain,
                                        onTabletFocusEditingChange = { tabletLyricsEditorFocusMode = it },
                                        stableTabletLyricsEditorSession = true,
                                        readerHeaderEndContent = {}
                                    )
                                }

                                val libraryPane: @Composable (Modifier) -> Unit = { paneModifier ->
                                    LibraryScreen(
                                        modifier = paneModifier,
                                        workspaceSnapshot = workspaceSnapshot,
                                        workspaceVersion = setupTick,
                                        currentPlayingSongId = currentPlayingSongId,
                                        reselectRootSignal = libraryTabReselectSignal,
                                        openStorageSignal = tabletLibraryOpenStorageSignal,
                                        searchToggleSignal = tabletLibrarySearchToggleSignal,
                                        searchCloseSignal = tabletLibrarySearchCloseSignal,
                                        compactTabletLayout = adaptiveTokens.tabletMode &&
                                            tabletExperimentalModeEnabled,
                                        compactHeaderEndContent = {},
                                        smpRefreshVersion = smpCacheRefreshTick,
                                        smpSongsCache = smpSongsById,
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
                                        onSyncWorkspaceSmpArchives = {
                                            syncWorkspaceSmpArchivesToRuntime(
                                                trigger = "library_manual_rescan",
                                                useAttemptGate = false
                                            )
                                        },
                                        onImportGeneratedSmp = autoImportGeneratedSmp,
                                        onImportGeneratedSmpFailureReason = {
                                            lastSmpImportFailureReason.get() ?: smpImporter.lastFailureReason
                                        },
                                        onDeleteSmpSong = { songId ->
                                            deleteSmpSongById(songId)
                                        },
                                        onKeyboardNavigationAvailabilityChange = { enabled ->
                                            libraryKeyboardNavigationEnabled = enabled
                                        },
                                        onOpenPlaylistFromLibrary = { name ->
                                            selectedQuickPlaylist = name
                                            openedPlaylist = name
                                            setTabAndPersist(BottomTab.QuickPlaylists, reason = "libraryOpenPlaylist")
                                        },
                                        onLufsManualGainChanged = { songId, gainDb ->
                                            if (songId == currentPlayingSongId) {
                                                val safeDb = clampTrackDb(gainDb)
                                                currentTrackGainDb = safeDb
                                                currentTrackVolumeSource =
                                                    SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS
                                                AudioEngine.applyTrackGainDb(safeDb)
                                            }
                                        },
                                        onPlayFromLibrary = { uriString, openRichPlayer ->
                                            isTabletCockpitDestinationOpen = false
                                            tabletRightPanel = TabletSplitRightPanel.LYRICS
                                            Log.d(
                                                SMP_PLAY_TRACE_TAG,
                                                "LIBRARY_TAP item=$uriString"
                                            )
                                            SmpLaunchTiming.start(
                                                source = "library_tap",
                                                requestedItem = uriString,
                                                playlistName = null,
                                                songId = getSmpSongId(uriString)
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
                                                    SmpLaunchTiming.markResolvedAudioTarget(
                                                        uri = resolvedTarget.uri,
                                                        playlistName = resolvedTarget.playlist,
                                                        songId = getSmpSongId(uriString)
                                                    )
                                                    stopChainPlayback()
                                                    LyricsPerf.startOpen(
                                                        trackUriString = resolvedTarget.uri,
                                                        source = "library_tap",
                                                        playlistName = resolvedTarget.playlist
                                                    )
                                                    scope.launch {
                                                        playWithCrossfadeInternal(
                                                            uriString = resolvedTarget.uri,
                                                            playlistName = resolvedTarget.playlist,
                                                            playlistItemKey = null,
                                                            openPlayerScreen = openRichPlayer
                                                        )
                                                    }
                                                    currentLyricsColor = Color.White
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
                                        },
                                        hardwareCommandToken = libraryHardwareCommandToken,
                                        hardwareCommand = libraryHardwareCommand,
                                        hardwareReturnToCurrentToken = libraryHardwareReturnToken,
                                        hardwareReturnCommand = libraryHardwareReturnCommand
                                    )
                                }

                                val settingsPane: @Composable (Modifier) -> Unit = { paneModifier ->
                                    MoreScreen(
                                        modifier = paneModifier,
                                        context = ctx,
                                        currentWaveformSongId = currentPlayingSongId,
                                        currentPlayingSongId = currentPlayingSongId,
                                        currentPlayingTitle = currentPlayingTitle
                                            ?: currentPlayingSongId
                                                ?.let { songId -> sanitizeDisplayTrackTitle(smpSongsById[songId]?.title) }
                                            ?: currentPlayingUri
                                                ?.let { uri -> sanitizeDisplayTrackTitle(TitleAliasesStore.getTitleForTrack(ctx, uri)) }
                                            ?: currentPlayingUri
                                                ?.let { uri -> sanitizeDisplayTrackTitle(indexAll.firstOrNull { it.uriString == uri }?.name) }
                                            ?: currentPlayingUri
                                                ?.let { uri -> sanitizeDisplayTrackTitle(Uri.parse(uri).lastPathSegment) },
                                        currentParsedLines = parsedLines,
                                        getCurrentPositionMs = {
                                            runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
                                        },
                                        getCurrentDurationMs = {
                                            runCatching {
                                                resolveEffectiveDurationMs(
                                                    requestedUri = currentPlayingUri,
                                                    activeUri = exoPlayer.currentMediaItem
                                                        ?.localConfiguration
                                                        ?.uri
                                                        ?.toString()
                                                )
                                            }.getOrDefault(C.TIME_UNSET)
                                                .takeIf { it > 0L && it != C.TIME_UNSET }
                                        },
                                        isCurrentTrackPlaying = {
                                            runCatching { exoPlayer.isPlaying }.getOrDefault(isPlaying)
                                        },
                                        loadCurrentParsedLines = {
                                            val uri = currentPlayingUri
                                            when {
                                                parsedLines.isNotEmpty() -> parsedLines
                                                uri.isNullOrBlank() -> emptyList()
                                                else -> {
                                                    LyricsMemoryCache.updateScope(LrcStorage.currentWorkspaceScopeKey(ctx))
                                                    LyricsMemoryCache.get(uri)?.parsedLines?.takeIf { it.isNotEmpty() }
                                                        ?: withContext(Dispatchers.IO) {
                                                            LrcStorage.loadForTrack(ctx, uri)
                                                                ?.takeIf { it.isNotBlank() }
                                                                ?.let { parseLrc(it) }
                                                                .orEmpty()
                                                        }
                                                }
                                            }
                                        },
                                        requestedRoute = moreNavigationTarget,
                                        requestedRouteToken = moreNavigationToken,
                                        showDjTab = showDjTab,
                                        showMainBusTab = showMainBusTab,
                                        tabletExperimentalModeEnabled = tabletExperimentalModeEnabled,
                                        onShowDjTabChange = { enabled ->
                                            showDjTab = enabled
                                        },
                                        onShowMainBusTabChange = { enabled ->
                                            showMainBusTab = enabled
                                        },
                                        onTabletExperimentalModeChange = { enabled ->
                                            tabletExperimentalModeEnabled = enabled
                                            if (!enabled) {
                                                isTabletCockpitDestinationOpen = false
                                                tabletRightPanel = TabletSplitRightPanel.LYRICS
                                            }
                                            if (
                                                enabled &&
                                                adaptiveTokens.tabletMode &&
                                                selectedTab is BottomTab.More
                                            ) {
                                                setTabAndPersist(
                                                    BottomTab.Player,
                                                    reason = "tabletExperimentalModeEnabled"
                                                )
                                            }
                                        },
                                        onAfterImport = { refreshKey++ },
                                        onAfterSmpRestore = { importedCount, lastImportedSongId ->
                                            if (importedCount > 0) {
                                                smpCacheRefreshTick++
                                                lastImportedSongId?.takeIf { it.isNotBlank() }?.let { songId ->
                                                    lastImportedSmpUiSignal = SmpImportedUiSignal(
                                                        songId = songId,
                                                        title = songId,
                                                        requestVersion = smpCacheRefreshTick
                                                    )
                                                }
                                            }
                                        },
                                        onAfterSyncImport = { importedSongIds ->
                                            scope.launch {
                                                Log.i(
                                                    "SMP_SYNC_IMPORT_DIAG",
                                                    "cache_refresh_start source=main_activity imported=${importedSongIds.size}"
                                                )
                                                val refreshedSongsById = withContext(Dispatchers.IO) {
                                                    smpLibraryScanner.listSongs().associateBy { it.id }
                                                }
                                                if (refreshedSongsById.isNotEmpty()) {
                                                    smpSongsById = refreshedSongsById
                                                    withContext(Dispatchers.IO) {
                                                        SmpRuntimeSongCache.save(ctx, refreshedSongsById.values)
                                                    }
                                                }
                                                smpCacheRefreshTick++
                                                importedSongIds.firstOrNull { it.isNotBlank() }?.let { songId ->
                                                    lastImportedSmpUiSignal = SmpImportedUiSignal(
                                                        songId = songId,
                                                        title = refreshedSongsById[songId]?.title ?: songId,
                                                        requestVersion = smpCacheRefreshTick
                                                    )
                                                }
                                                refreshKey++
                                                Log.i(
                                                    "SMP_SYNC_IMPORT_DIAG",
                                                    "library_visible_after_import count=${refreshedSongsById.size} importedVisible=${importedSongIds.count { songId -> songId in refreshedSongsById }}"
                                                )
                                            }
                                        },
                                        onOpenTempoFromArrangement = {
                                            playerNavigationTarget = "grid_setup"
                                            playerNavigationToken += 1
                                            setTabAndPersist(BottomTab.Player, reason = "arrangementBackToTempo")
                                        },
                                        onStopCurrentPlayback = {
                                            isPlaying = false
                                            exoPlayer.pause()
                                            exoPlayer.playWhenReady = false
                                        },
                                        onOpenTuner = {
                                            openTabletSplitTuner()
                                        }
                                    )
                                }

                                val backgroundSoundPane: @Composable (Modifier) -> Unit = { paneModifier ->
                                    Box(modifier = paneModifier) {
                                        FillerSoundScreen(
                                            context = ctx,
                                            onBack = {
                                                tabletRightPanel = TabletSplitRightPanel.LYRICS
                                            }
                                        )
                                    }
                                }

                                val djPane: @Composable (Modifier) -> Unit = { paneModifier ->
                                    DjScreen(
                                        modifier = paneModifier,
                                        context = ctx
                                    )
                                }

                                val mainBusPane: @Composable (Modifier) -> Unit = { paneModifier ->
                                    MixerHomePreviewScreen(
                                        modifier = paneModifier,
                                        onBack = {
                                            tabletRightPanel = TabletSplitRightPanel.LYRICS
                                        },
                                        onOpenPlayer = {
                                            tabletRightPanel = TabletSplitRightPanel.LYRICS
                                        },
                                        onOpenFondSonore = {
                                            tabletRightPanel = TabletSplitRightPanel.BACKGROUND_SOUND
                                        },
                                        onOpenDj = {
                                            tabletRightPanel = TabletSplitRightPanel.DJ
                                        },
                                        onOpenTuner = {
                                            openTabletSplitTuner()
                                        },
                                        openNotesSignal = openNotesSignal,
                                        showBackButton = false,
                                        compactTabletMode = true
                                    )
                                }

                                val tunerPane: @Composable (Modifier) -> Unit = { paneModifier ->
                                    TunerScreen(
                                        modifier = paneModifier,
                                        onClose = {
                                            tabletRightPanel = TabletSplitRightPanel.LYRICS
                                        }
                                    )
                                }

                                val quickPlaylistsPane: @Composable (Modifier) -> Unit = { paneModifier ->
                                    QuickPlaylistsScreen(
                                        modifier = paneModifier,
                                        onPlaySong = { uri, playlistName, color ->
                                            stopChainPlayback()
                                            PlaybackCoordinator.peekNextTrack()
                                                ?.takeIf { armed -> armed.uri == uri }
                                                ?.let {
                                                    PlaybackCoordinator.clearNextTrack(
                                                        reason = "manualPlayMatch"
                                                    )
                                                }
                                            Log.d(
                                                SMP_PLAY_TRACE_TAG,
                                                "PLAYLIST_TAP item=$uri playlist=$playlistName"
                                            )
                                            SmpLaunchTiming.start(
                                                source = "playlist_tap",
                                                requestedItem = uri,
                                                playlistName = playlistName
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
                                                    val resolvedTarget = resolvePlaylistAudioTarget(
                                                        playlistItemKey = uri,
                                                        playlistName = playlistName,
                                                        rawTarget = target,
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
                                                    SmpLaunchTiming.markResolvedAudioTarget(
                                                        uri = resolvedTarget.uri,
                                                        playlistName = resolvedTarget.playlist,
                                                        songId = getSmpSongId(uri)
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

                                                    selectedQuickPlaylist = resolvedTarget.playlist
                                                    currentLyricsColor = color
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
                                        onArmChainFromCurrent = { visibleQueue, currentIndex, playlistName ->
                                            val resolvedCurrent = nextPlayableIndexAtOrAfter(visibleQueue, currentIndex)
                                            if (resolvedCurrent == null) {
                                                stopChainPlayback()
                                            } else {
                                                chainQueue = visibleQueue
                                                chainIndex = resolvedCurrent
                                                chainPlaylist = playlistName
                                                isChaining = true
                                            }
                                        },
                                        refreshKey = refreshKey,
                                        openPrompterSignal = openPrompterSignal,
                                        libraryLoadedSignal = indexAll.size,
                                        playlistsReady = playlistsReady,
                                        nextChainedUri = nextChainedUri,
                                        nextTrackUri = nextTrack?.uri,
                                        isPlaying = isPlaying,
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
                                        hardwareCommandToken = quickHardwareCommandToken,
                                        hardwareCommand = quickHardwareCommand,
                                        hardwareReturnToCurrentToken = quickHardwareReturnToken,
                                        hardwareReturnCommand = quickHardwareReturnCommand,
                                        onAddTrackToPlaylist = {
                                            openTabletSplitLibrary(openSearch = false)
                                            tabletLibraryOpenStorageSignal++
                                        },
                                        searchToggleSignal = playlistSearchToggleSignal,
                                        smpSongsCache = smpSongsById,
                                        compactTabletLayout = adaptiveTokens.tabletMode &&
                                            tabletExperimentalModeEnabled
                                    )
                                }

                                val tabletSplitBaseEnabled =
                                    adaptiveTokens.tabletMode &&
                                        tabletExperimentalModeEnabled
                                val selectedTabAllowsTabletSplit =
                                    selectedTab is BottomTab.Player || selectedTab is BottomTab.QuickPlaylists
                                val splitEligibleByTabletMode =
                                    tabletSplitBaseEnabled && selectedTabAllowsTabletSplit
                                val useTabletSplitLiveLayout =
                                    splitEligibleByTabletMode

                                LaunchedEffect(useTabletSplitLiveLayout, tabletRightPanel) {
                                    if (!useTabletSplitLiveLayout || tabletRightPanel != TabletSplitRightPanel.LYRICS) {
                                        tabletLyricsEditorFocusMode = false
                                    }
                                }

                                LaunchedEffect(
                                    adaptiveTokens.tabletMode,
                                    adaptiveTokens.isLandscape,
                                    tabletExperimentalModeEnabled,
                                    selectedTab,
                                    splitEligibleByTabletMode,
                                    useTabletSplitLiveLayout
                                ) {
                                    Log.d(
                                        "TABLET_SPLIT_DIAG",
                                        "tabletMode=${adaptiveTokens.tabletMode} " +
                                            "isLandscape=${adaptiveTokens.isLandscape} " +
                                            "tabletExperimentalModeEnabled=$tabletExperimentalModeEnabled " +
                                            "selectedTab=${tabKeyOf(selectedTab)} " +
                                            "splitEligibleByTabletMode=$splitEligibleByTabletMode " +
                                            "splitEnabled=$useTabletSplitLiveLayout"
                                    )
                                    if (!useTabletSplitLiveLayout) {
                                        isTabletSplitMenuOpen = false
                                    }
                                }

                                if (useTabletSplitLiveLayout) {
                                    Box(modifier = contentModifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            // Experimental tablet live layout; each pane reuses the existing screen contract.
                                            Box(
                                                Modifier
                                                    .weight(0.38f)
                                                    .fillMaxHeight()
                                            ) {
                                                quickPlaylistsPane(Modifier.fillMaxSize())
                                            }
                                            Box(
                                                Modifier
                                                    .weight(0.62f)
                                                    .fillMaxHeight()
                                            ) {
                                                when (tabletRightPanel) {
                                                    TabletSplitRightPanel.LYRICS -> {
                                                        Column(Modifier.fillMaxSize()) {
                                                            Box(
                                                                modifier = if (tabletLyricsEditorFocusMode) {
                                                                    Modifier
                                                                        .fillMaxWidth()
                                                                        .height(0.dp)
                                                                } else {
                                                                    Modifier.fillMaxWidth()
                                                                }
                                                            ) {
                                                                if (!tabletLyricsEditorFocusMode) {
                                                                    TabletSplitTopNavigationShortcuts()
                                                                }
                                                            }
                                                            playerPane(
                                                                Modifier
                                                                    .weight(1f)
                                                                    .fillMaxWidth()
                                                            )
                                                        }
                                                    }

                                                    TabletSplitRightPanel.LIBRARY -> {
                                                        Column(Modifier.fillMaxSize()) {
                                                            TabletSplitTopNavigationShortcuts()
                                                            libraryPane(
                                                                Modifier
                                                                    .weight(1f)
                                                                    .fillMaxWidth()
                                                            )
                                                        }
                                                    }

                                                    TabletSplitRightPanel.SETTINGS -> {
                                                        Column(Modifier.fillMaxSize()) {
                                                            TabletSplitTopNavigationShortcuts()
                                                            settingsPane(
                                                                Modifier
                                                                    .weight(1f)
                                                                    .fillMaxWidth()
                                                            )
                                                        }
                                                    }

                                                    TabletSplitRightPanel.BACKGROUND_SOUND -> {
                                                        Column(Modifier.fillMaxSize()) {
                                                            TabletSplitTopNavigationShortcuts()
                                                            backgroundSoundPane(
                                                                Modifier
                                                                    .weight(1f)
                                                                    .fillMaxWidth()
                                                            )
                                                        }
                                                    }

                                                    TabletSplitRightPanel.DJ -> {
                                                        Column(Modifier.fillMaxSize()) {
                                                            TabletSplitTopNavigationShortcuts()
                                                            djPane(
                                                                Modifier
                                                                    .weight(1f)
                                                                    .fillMaxWidth()
                                                            )
                                                        }
                                                    }

                                                    TabletSplitRightPanel.MAIN_BUS -> {
                                                        Column(Modifier.fillMaxSize()) {
                                                            TabletSplitTopNavigationShortcuts()
                                                            mainBusPane(
                                                                Modifier
                                                                    .weight(1f)
                                                                    .fillMaxWidth()
                                                            )
                                                        }
                                                    }

                                                    TabletSplitRightPanel.TUNER -> {
                                                        Column(Modifier.fillMaxSize()) {
                                                            TabletSplitTopNavigationShortcuts()
                                                            tunerPane(
                                                                Modifier
                                                                    .weight(1f)
                                                                    .fillMaxWidth()
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else when (selectedTab) {

                                    is BottomTab.Home -> Box(
                                        modifier = tabletCockpitDestinationContentModifier.fillMaxSize()
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
                                        if (EditionConfig.isPro) {
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
                                        } else {
                                            HomeScreen(
                                                modifier = Modifier.fillMaxSize(),
                                                onOpenPlayer = {
                                                    setTabAndPersist(BottomTab.Player, reason = "homeOpenPlayer")
                                                },
                                                onOpenFondSonore = { isFillerSettingsOpen = true },
                                                onOpenDjMode = {
                                                    setTabAndPersist(BottomTab.Dj, reason = "homeOpenDj")
                                                },
                                                onOpenGlobalMix = {},
                                                onOpenTuner = {
                                                    setTabAndPersist(BottomTab.Tuner, reason = "homeOpenTuner")
                                                },
                                                onOpenProfile = {},
                                                onOpenTutorial = {},
                                                onOpenSettings = {},
                                                onOpenNotes = {
                                                    isNotesOpen = true
                                                    isFillerSettingsOpen = false
                                                    isGlobalMixOpen = false
                                                    isSearchOpen = false
                                                    textPrompterId = null
                                                    isMixerPreviewOpen = false
                                                }
                                            )
                                        }
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
                                            if (manualCrossfadeTransitionTitle != null) {
                                                return@PlayerScreen
                                            }
                                            isPlaying = shouldPlay
                                            if (shouldPlay) exoPlayer.play() else exoPlayer.pause()
                                        },
                                        parsedLines = parsedLines,
                                        lyricsLoading = lyricsLoading,
                                        onParsedLinesChange = { parsedLines = it },
                                        highlightColor = currentLyricsColor,
                                        currentTrackUri = currentPlayingUri,
                                        nextTrackTitle = effectiveNextTrackTitle,
                                        currentTrackGainDb = currentTrackGainDb,
                                        currentTrackVolumeSource = currentTrackVolumeSource,
                                        onTrackGainChange = { db ->
                                            if (currentTrackVolumeSource == SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS) {
                                                return@PlayerScreen
                                            }
                                            val safeDb = clampTrackDb(db)
                                            currentTrackGainDb = safeDb
                                            AudioEngine.applyTrackGainDb(safeDb)
                                        },
                                        onTrackGainCommit = { db ->
                                            if (currentTrackVolumeSource == SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS) {
                                                return@PlayerScreen
                                            }
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
                                        currentSongId = currentPlayingSongId,
                                        onOpenArrangementHub = {
                                            moreNavigationTarget = "arrangement_from_tempo"
                                            moreNavigationToken += 1
                                            setTabAndPersist(BottomTab.More, reason = "playerOpenArrangementHub")
                                        },
                                        manualTransitionTargetTitle = manualCrossfadeTransitionTitle,
                                        onManualCrossfadeToNext = { launchManualCrossfadeToNext() },
                                        onImportGeneratedSmp = autoImportGeneratedSmp,
                                        requestedNavigationTarget = playerNavigationTarget,
                                        requestedNavigationToken = playerNavigationToken,
                                        onOpenWaveform = {
                                            moreNavigationTarget = "waveform_preview"
                                            moreNavigationToken += 1
                                            setTabAndPersist(BottomTab.More, reason = "playerOpenWaveform")
                                        },
                                        getPositionMs = { exoPlayer.currentPosition },
                                        getEffectiveDurationMs = {
                                            resolveEffectiveDurationMs(
                                                requestedUri = currentPlayingUri,
                                                activeUri = exoPlayer.currentMediaItem
                                                    ?.localConfiguration
                                                    ?.uri
                                                    ?.toString()
                                            )
                                        },
                                        seekToMs = { ms -> exoPlayer.seekTo(ms) }
                                    )

                                    is BottomTab.QuickPlaylists -> QuickPlaylistsScreen(
                                        modifier = contentModifier,
                                        onPlaySong = { uri, playlistName, color ->
                                            stopChainPlayback()
                                            PlaybackCoordinator.peekNextTrack()
                                                ?.takeIf { armed -> armed.uri == uri }
                                                ?.let {
                                                    PlaybackCoordinator.clearNextTrack(
                                                        reason = "manualPlayMatch"
                                                    )
                                                }
                                            Log.d(
                                                SMP_PLAY_TRACE_TAG,
                                                "PLAYLIST_TAP item=$uri playlist=$playlistName"
                                            )
                                            SmpLaunchTiming.start(
                                                source = "playlist_tap",
                                                requestedItem = uri,
                                                playlistName = playlistName
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
                                                val resolvedTarget = resolvePlaylistAudioTarget(
                                                    playlistItemKey = uri,
                                                    playlistName = playlistName,
                                                    rawTarget = target,
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
                                                SmpLaunchTiming.markResolvedAudioTarget(
                                                    uri = resolvedTarget.uri,
                                                    playlistName = resolvedTarget.playlist,
                                                    songId = getSmpSongId(uri)
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

                                                selectedQuickPlaylist = resolvedTarget.playlist
                                                currentLyricsColor = color
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
                                    onArmChainFromCurrent = { visibleQueue, currentIndex, playlistName ->
                                        val resolvedCurrent = nextPlayableIndexAtOrAfter(visibleQueue, currentIndex)
                                        if (resolvedCurrent == null) {
                                            stopChainPlayback()
                                        } else {
                                            chainQueue = visibleQueue
                                            chainIndex = resolvedCurrent
                                            chainPlaylist = playlistName
                                            isChaining = true
                                        }
                                    },
                                    refreshKey = refreshKey,
                                    openPrompterSignal = openPrompterSignal,
                                    libraryLoadedSignal = indexAll.size,
                                    playlistsReady = playlistsReady,
                                    nextChainedUri = nextChainedUri,
                                    nextTrackUri = nextTrack?.uri,
                                    isPlaying = isPlaying,
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
                                    hardwareCommandToken = quickHardwareCommandToken,
                                    hardwareCommand = quickHardwareCommand,
                                    hardwareReturnToCurrentToken = quickHardwareReturnToken,
                                    hardwareReturnCommand = quickHardwareReturnCommand,
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
                                    searchToggleSignal = playlistSearchToggleSignal,
                                    smpSongsCache = smpSongsById
                                    )

                                    is BottomTab.Library -> LibraryScreen(
                                        modifier = tabletCockpitDestinationContentModifier,
                                        workspaceSnapshot = workspaceSnapshot,
                                        workspaceVersion = setupTick,
                                        currentPlayingSongId = currentPlayingSongId,
                                        reselectRootSignal = libraryTabReselectSignal,
                                        searchToggleSignal = librarySearchToggleSignal,
                                        smpRefreshVersion = smpCacheRefreshTick,
                                        smpSongsCache = smpSongsById,
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
                                        onSyncWorkspaceSmpArchives = {
                                            syncWorkspaceSmpArchivesToRuntime(
                                                trigger = "library_manual_rescan",
                                                useAttemptGate = false
                                            )
                                        },
                                        onImportGeneratedSmp = autoImportGeneratedSmp,
                                        onImportGeneratedSmpFailureReason = {
                                            lastSmpImportFailureReason.get() ?: smpImporter.lastFailureReason
                                        },
                                        onDeleteSmpSong = { songId ->
                                            deleteSmpSongById(songId)
                                        },
                                        onKeyboardNavigationAvailabilityChange = { enabled ->
                                            libraryKeyboardNavigationEnabled = enabled
                                        },
                                        onOpenPlaylistFromLibrary = { name ->
                                            selectedQuickPlaylist = name
                                            openedPlaylist = name
                                            setTabAndPersist(BottomTab.QuickPlaylists, reason = "libraryOpenPlaylist")
                                        },
                                        onLufsManualGainChanged = { songId, gainDb ->
                                            if (songId == currentPlayingSongId) {
                                                val safeDb = clampTrackDb(gainDb)
                                                currentTrackGainDb = safeDb
                                                currentTrackVolumeSource =
                                                    SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS
                                                AudioEngine.applyTrackGainDb(safeDb)
                                            }
                                        },
                                        onPlayFromLibrary = { uriString, openRichPlayer ->
                                            Log.d(
                                                SMP_PLAY_TRACE_TAG,
                                                "LIBRARY_TAP item=$uriString"
                                            )
                                            SmpLaunchTiming.start(
                                                source = "library_tap",
                                                requestedItem = uriString,
                                                playlistName = null,
                                                songId = getSmpSongId(uriString)
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
                                                    SmpLaunchTiming.markResolvedAudioTarget(
                                                        uri = resolvedTarget.uri,
                                                        playlistName = resolvedTarget.playlist,
                                                        songId = getSmpSongId(uriString)
                                                    )
                                                    stopChainPlayback()
                                                    LyricsPerf.startOpen(
                                                        trackUriString = resolvedTarget.uri,
                                                        source = "library_tap",
                                                        playlistName = resolvedTarget.playlist
                                                    )
                                                    scope.launch {
                                                        playWithCrossfadeInternal(
                                                            uriString = resolvedTarget.uri,
                                                            playlistName = resolvedTarget.playlist,
                                                            playlistItemKey = null,
                                                            openPlayerScreen = openRichPlayer
                                                        )
                                                    }
                                                    currentLyricsColor = Color.White
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
                                        },
                                        hardwareCommandToken = libraryHardwareCommandToken,
                                        hardwareCommand = libraryHardwareCommand,
                                        hardwareReturnToCurrentToken = libraryHardwareReturnToken,
                                        hardwareReturnCommand = libraryHardwareReturnCommand
                                    )

                                    is BottomTab.AllPlaylists -> AllPlaylistsScreen(
                                        modifier = contentModifier,
                                        onPlaylistClick = { name ->
                                            selectedQuickPlaylist = name
                                            openedPlaylist = name
                                            setTabAndPersist(BottomTab.QuickPlaylists, reason = "allPlaylistsOpen")
                                        }
                                    )

                                    is BottomTab.Dj -> DjScreen(
                                        modifier = tabletCockpitDestinationContentModifier,
                                        context = ctx
                                    )

                                    is BottomTab.More -> MoreScreen(
                                        modifier = tabletCockpitDestinationContentModifier,
                                        context = ctx,
                                        currentWaveformSongId = currentPlayingSongId,
                                        currentPlayingSongId = currentPlayingSongId,
                                        currentPlayingTitle = currentPlayingTitle
                                            ?: currentPlayingSongId
                                                ?.let { songId -> sanitizeDisplayTrackTitle(smpSongsById[songId]?.title) }
                                            ?: currentPlayingUri
                                                ?.let { uri -> sanitizeDisplayTrackTitle(TitleAliasesStore.getTitleForTrack(ctx, uri)) }
                                            ?: currentPlayingUri
                                                ?.let { uri -> sanitizeDisplayTrackTitle(indexAll.firstOrNull { it.uriString == uri }?.name) }
                                            ?: currentPlayingUri
                                                ?.let { uri -> sanitizeDisplayTrackTitle(Uri.parse(uri).lastPathSegment) },
                                        currentParsedLines = parsedLines,
                                        getCurrentPositionMs = {
                                            runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
                                        },
                                        getCurrentDurationMs = {
                                            runCatching {
                                                resolveEffectiveDurationMs(
                                                    requestedUri = currentPlayingUri,
                                                    activeUri = exoPlayer.currentMediaItem
                                                        ?.localConfiguration
                                                        ?.uri
                                                        ?.toString()
                                                )
                                            }.getOrDefault(C.TIME_UNSET)
                                                .takeIf { it > 0L && it != C.TIME_UNSET }
                                        },
                                        isCurrentTrackPlaying = {
                                            runCatching { exoPlayer.isPlaying }.getOrDefault(isPlaying)
                                        },
                                        loadCurrentParsedLines = {
                                            val uri = currentPlayingUri
                                            when {
                                                parsedLines.isNotEmpty() -> parsedLines
                                                uri.isNullOrBlank() -> emptyList()
                                                else -> {
                                                    LyricsMemoryCache.updateScope(LrcStorage.currentWorkspaceScopeKey(ctx))
                                                    LyricsMemoryCache.get(uri)?.parsedLines?.takeIf { it.isNotEmpty() }
                                                        ?: withContext(Dispatchers.IO) {
                                                            LrcStorage.loadForTrack(ctx, uri)
                                                                ?.takeIf { it.isNotBlank() }
                                                                ?.let { parseLrc(it) }
                                                                .orEmpty()
                                                        }
                                                }
                                            }
                                        },
                                        requestedRoute = moreNavigationTarget,
                                        requestedRouteToken = moreNavigationToken,
                                        showDjTab = showDjTab,
                                        showMainBusTab = showMainBusTab,
                                        tabletExperimentalModeEnabled = tabletExperimentalModeEnabled,
                                        onShowDjTabChange = { enabled ->
                                            showDjTab = enabled
                                        },
                                        onShowMainBusTabChange = { enabled ->
                                            showMainBusTab = enabled
                                        },
                                        onTabletExperimentalModeChange = { enabled ->
                                            tabletExperimentalModeEnabled = enabled
                                            if (!enabled) {
                                                isTabletCockpitDestinationOpen = false
                                            }
                                            if (
                                                enabled &&
                                                adaptiveTokens.tabletMode &&
                                                selectedTab is BottomTab.More
                                            ) {
                                                setTabAndPersist(
                                                    BottomTab.Player,
                                                    reason = "tabletExperimentalModeEnabled"
                                                )
                                            }
                                        },
                                        onAfterImport = { refreshKey++ },
                                        onAfterSmpRestore = { importedCount, lastImportedSongId ->
                                            if (importedCount > 0) {
                                                smpCacheRefreshTick++
                                                lastImportedSongId?.takeIf { it.isNotBlank() }?.let { songId ->
                                                    lastImportedSmpUiSignal = SmpImportedUiSignal(
                                                        songId = songId,
                                                        title = songId,
                                                        requestVersion = smpCacheRefreshTick
                                                    )
                                                }
                                            }
                                        },
                                        onAfterSyncImport = { importedSongIds ->
                                            scope.launch {
                                                Log.i(
                                                    "SMP_SYNC_IMPORT_DIAG",
                                                    "cache_refresh_start source=main_activity imported=${importedSongIds.size}"
                                                )
                                                val refreshedSongsById = withContext(Dispatchers.IO) {
                                                    smpLibraryScanner.listSongs().associateBy { it.id }
                                                }
                                                if (refreshedSongsById.isNotEmpty()) {
                                                    smpSongsById = refreshedSongsById
                                                    withContext(Dispatchers.IO) {
                                                        SmpRuntimeSongCache.save(ctx, refreshedSongsById.values)
                                                    }
                                                }
                                                smpCacheRefreshTick++
                                                importedSongIds.firstOrNull { it.isNotBlank() }?.let { songId ->
                                                    lastImportedSmpUiSignal = SmpImportedUiSignal(
                                                        songId = songId,
                                                        title = refreshedSongsById[songId]?.title ?: songId,
                                                        requestVersion = smpCacheRefreshTick
                                                    )
                                                }
                                                refreshKey++
                                                Log.i(
                                                    "SMP_SYNC_IMPORT_DIAG",
                                                    "library_visible_after_import count=${refreshedSongsById.size} importedVisible=${importedSongIds.count { songId -> songId in refreshedSongsById }}"
                                                )
                                            }
                                        },
                                        onOpenTempoFromArrangement = {
                                            playerNavigationTarget = "grid_setup"
                                            playerNavigationToken += 1
                                            setTabAndPersist(BottomTab.Player, reason = "arrangementBackToTempo")
                                        },
                                        onStopCurrentPlayback = {
                                            isPlaying = false
                                            exoPlayer.pause()
                                            exoPlayer.playWhenReady = false
                                        },
                                        onOpenTuner = {
                                            setTabAndPersist(BottomTab.Tuner, reason = "moreOpenTuner")
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

                    if (shouldShowTabletCockpitDestinationChrome) {
                        Box(
                            modifier = contentModifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Color.Black),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            TextButton(onClick = { returnToTabletCockpit() }) {
                                Text(
                                    text = stringResource(R.string.tablet_split_return_cockpit),
                                    color = Color.White
                                )
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
                                            scope.launch {
                                                playWithCrossfadeInternal(
                                                    uriString = uriString,
                                                    playlistName = null,
                                                    playlistItemKey = null,
                                                    openPlayerScreen = true
                                                )
                                            }
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
                                            scope.launch {
                                                playWithCrossfadeInternal(
                                                    uriString = uriString,
                                                    playlistName = selectedQuickPlaylist,
                                                    playlistItemKey = uriString,
                                                    openPlayerScreen = true
                                                )
                                            }
                                            currentLyricsColor = Color(0xFFE040FB)
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
                                moreNavigationTarget = "root"
                                moreNavigationToken += 1
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
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.ime)
                                .background(Color(0xAA000000))
                        ) {
                            NotesScreen(
                                modifier = Modifier
                                    .fillMaxSize(),
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
    }
    override fun onStop() {
        val startMs = SystemClock.elapsedRealtime()
        val threadName = Thread.currentThread().name
        Log.e("ANR_TRACE", "onStop:start thread=$threadName")
        super.onStop()
        persistSession(reason = "onStop")
        lifecycleScope.launch(Dispatchers.IO) {
            val playlistsStartMs = SystemClock.elapsedRealtime()
            runCatching {
                PlaylistStateStore.savePlaylistsSnapshot(
                    context = this@MainActivity,
                    transientGroupTitles = setOf(
                        getString(R.string.quickplaylists_group_current),
                        "Groupe en cours",
                        "Current group",
                        "Grupo en curso"
                    )
                )
            }
                .onFailure { Log.e("BOOTSTEP", "PlaylistStateStore.savePlaylistsSnapshot:onStop failed", it) }
            Log.e(
                "ANR_TRACE",
                "onStop:after_savePlaylists durationMs=${SystemClock.elapsedRealtime() - playlistsStartMs} thread=${Thread.currentThread().name}"
            )
            val backupStartMs = SystemClock.elapsedRealtime()
            BackupManager.autoSaveToDefaultBackupFile(this@MainActivity)
            Log.e(
                "ANR_TRACE",
                "onStop:bg_end totalDurationMs=${SystemClock.elapsedRealtime() - startMs} backupDurationMs=${SystemClock.elapsedRealtime() - backupStartMs} thread=${Thread.currentThread().name}"
            )
        }
        Log.e(
            "ANR_TRACE",
            "onStop:end durationMs=${SystemClock.elapsedRealtime() - startMs} thread=$threadName"
        )
    }

    override fun onPause() {
        val startMs = SystemClock.elapsedRealtime()
        val threadName = Thread.currentThread().name
        Log.e("ANR_TRACE", "onPause:start thread=$threadName")
        super.onPause()
        persistSession(reason = "onPause")
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { PlaylistStateStore.savePlaylistsSnapshot(this@MainActivity) }
                .onFailure { Log.e("BOOTSTEP", "PlaylistStateStore.savePlaylistsSnapshot:onPause failed", it) }
        }
        Log.e(
            "ANR_TRACE",
            "onPause:end durationMs=${SystemClock.elapsedRealtime() - startMs} thread=$threadName"
        )
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

private fun defaultTabForEdition(): BottomTab =
    if (EditionConfig.isPro) BottomTab.Home else BottomTab.QuickPlaylists

private fun defaultTabKeyForEdition(): String =
    if (EditionConfig.isPro) TAB_HOME else TAB_QUICK

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

private fun sanitizeTab(tab: BottomTab, showDjTab: Boolean, showMainBusTab: Boolean): BottomTab = when (tab) {
    is BottomTab.Dj -> if (showDjTab) BottomTab.Dj else BottomTab.QuickPlaylists
    is BottomTab.Home -> if (EditionConfig.isPro && showMainBusTab) BottomTab.Home else BottomTab.QuickPlaylists
    else -> tab
}

private fun sanitizeTabKey(key: String?, showDjTab: Boolean, showMainBusTab: Boolean): String =
    tabKeyOf(
        sanitizeTab(
            tab = when (key) {
                TAB_HOME -> if (EditionConfig.isPro) BottomTab.Home else BottomTab.QuickPlaylists
                TAB_PLAYER -> BottomTab.Player
                TAB_QUICK -> BottomTab.QuickPlaylists
                TAB_LIBRARY -> BottomTab.Library
                TAB_ALL -> BottomTab.AllPlaylists
                TAB_MORE -> BottomTab.More
                TAB_DJ -> BottomTab.Dj
                TAB_TUNER -> BottomTab.Tuner
                TAB_FILLER -> BottomTab.Filler
                TAB_SEARCH -> BottomTab.QuickPlaylists
                else -> defaultTabForEdition()
            },
            showDjTab = showDjTab,
            showMainBusTab = showMainBusTab
        )
    )

private fun tabFromKey(key: String, showDjTab: Boolean, showMainBusTab: Boolean): BottomTab = when (key) {
    TAB_HOME -> sanitizeTab(
        tab = defaultTabForEdition(),
        showDjTab = showDjTab,
        showMainBusTab = showMainBusTab
    )
    TAB_PLAYER -> BottomTab.Player
    TAB_QUICK -> BottomTab.QuickPlaylists
    TAB_LIBRARY -> BottomTab.Library
    TAB_ALL -> BottomTab.AllPlaylists
    TAB_MORE -> BottomTab.More
    TAB_DJ -> sanitizeTab(
        tab = BottomTab.Dj,
        showDjTab = showDjTab,
        showMainBusTab = showMainBusTab
    )
    TAB_TUNER -> BottomTab.Tuner
    TAB_FILLER -> BottomTab.Filler
    TAB_SEARCH -> BottomTab.QuickPlaylists // on ne “restore” pas un overlay comme un onglet
    else -> sanitizeTab(
        tab = defaultTabForEdition(),
        showDjTab = showDjTab,
        showMainBusTab = showMainBusTab
    )
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
