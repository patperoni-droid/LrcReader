@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.patrick.lrcreader.exo

import com.patrick.lrcreader.ui.library.SetupInstallScreen
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.patrick.lrcreader.core.ImportAudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.MidiOutput
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.patrick.lrcreader.core.*
import com.patrick.lrcreader.core.audio.AudioEngine
import com.patrick.lrcreader.core.dj.DjEngine
import com.patrick.lrcreader.core.exoCrossfadePlay
import com.patrick.lrcreader.core.lyrics.LyricsResolver
import com.patrick.lrcreader.ui.*
import com.patrick.lrcreader.ui.library.LibraryScreen
import kotlin.math.pow

class MainActivity : ComponentActivity() {

    companion object {
        private const val DEFAULT_TRACK_GAIN_DB = -5
        private const val MIN_TRACK_DB = -12
        private const val MAX_TRACK_DB = 0


    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        AutoRestore.restoreIfNeeded(this)
        MidiOutput.init(applicationContext)
        CueMidiStore.init(applicationContext)
        val initialTabKey = SessionPrefs.getTab(this)
        val initialQuickPlaylist = SessionPrefs.getQuickPlaylist(this)
        val initialOpenedPlaylist = SessionPrefs.getOpenedPlaylist(this)

        DjEngine.init(this)

// ✅ MIDI : init tôt (une seule fois)
        MidiOutput.init(applicationContext)
        android.util.Log.d("MainActivity", "MIDI init demandé dès onCreate")
        val savedBackupsUri = BackupFolderPrefs.get(this)
        if (savedBackupsUri != null) {

            // ✅ Si on a encore la permission persistée, OK
            val hasPerm = contentResolver.persistedUriPermissions.any { p ->
                p.uri == savedBackupsUri && p.isReadPermission
            }

            if (hasPerm) {
                // ✅ Pour ta lib : on garde ton mécanisme, mais on doit viser la racine SPL_Music.
                // Backups = .../SPL_Music/Backups
                // Root   = .../SPL_Music
                // Donc on remonte d’un niveau via DocumentFile.
                val backupsDir = DocumentFile.fromTreeUri(this, savedBackupsUri)
                val splRoot = backupsDir?.parentFile

                if (splRoot != null) {
                    LibrarySnapshot.rootFolderUri = splRoot.uri
                    val cached = LibraryIndexCache.load(this)
                    if (!cached.isNullOrEmpty()) {
                        LibrarySnapshot.entries = cached.map { it.uriString }
                        LibrarySnapshot.isReady = true
                    }
                }
            }
        }
        // ✅ Auto backup : planifie le worker à chaque démarrage (WorkManager gère le "unique")
        AutoBackupScheduler.ensureScheduled(this)

// ✅ Auto restore "propre" : si un backup existe, et seulement une fois
        run {
            val already = BackupRestorePrefs.wasRestoredOnce(this)
            if (!already) {
                val restored = BackupManager.autoRestoreFromDefaultBackupFile(this)
                if (restored) {
                    BackupRestorePrefs.setRestoredOnce(this, true)
                }
            }
        }
        setContent {
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
// On ne se base PAS uniquement sur l'URI (Android peut restaurer),
// on se base sur un flag explicite "setup_done".
                val isSetupDone = remember(setupTick) { BackupFolderPrefs.isDone(ctx) }
                val hasSetupPerm = remember(setupTick) { BackupFolderPrefs.hasValidSetupTreePermission(ctx) }

                val shouldShowSetup = forceSetup || !isSetupDone || !hasSetupPerm
                val pickAudioFilesLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult

                    val setupTree = BackupFolderPrefs.getSetupTreeUri(ctx) ?: run {
                        android.util.Log.e("IMPORT", "setupTreeUri manquant")
                        return@rememberLauncherForActivityResult
                    }

                    val baseTree = DocumentFile.fromTreeUri(ctx, setupTree) ?: run {
                        android.util.Log.e("IMPORT", "baseTree null (permission tree ?)")
                        return@rememberLauncherForActivityResult
                    }

                    val splRootDoc = baseTree.findFile("SPL_Music") ?: run {
                        android.util.Log.e("IMPORT", "SPL_Music introuvable")
                        return@rememberLauncherForActivityResult
                    }

                    val backingDoc = splRootDoc.findFile("BackingTracks") ?: run {
                        android.util.Log.e("IMPORT", "BackingTracks introuvable")
                        return@rememberLauncherForActivityResult
                    }

                    val audioDoc = backingDoc.findFile("audio") ?: run {
                        android.util.Log.e("IMPORT", "BackingTracks/audio introuvable")
                        return@rememberLauncherForActivityResult
                    }

                    // ✅ ImportAudioManager veut un TreeUri => on passe le TreeUri du dossier DESTINATION (= audio)
                    val audioTreeUri = toTreeUri(audioDoc.uri)

                    scope.launch {
                        isImporting = true
                        try {
                            val result = withContext(Dispatchers.IO) {
                                ImportAudioManager.importAudioFiles(
                                    context = ctx,
                                    appRootTreeUri = setupTree, // ✅ LE TREE URI autorisé
                                    sourceUris = uris,
                                    destFolderName = "BackingTracks/audio",
                                    overwriteIfExists = false
                                )
                            }

                            android.util.Log.d(
                                "IMPORT",
                                "copied=${result.copiedCount} skipped=${result.skippedCount} errors=${result.errors.size}"
                            )
                            result.errors.take(20).forEach { android.util.Log.e("IMPORT", it) }

                            val newIndex = withContext(Dispatchers.IO) {
                                buildFullIndex(ctx, setupTree) // ✅ si ton buildFullIndex sait descendre depuis le dossier choisi
                            }
                            LibraryIndexCache.save(ctx, newIndex)
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
                    val isDebug = remember {
                        (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    }
                    if (isDebug) {
                        Button(onClick = {
                            BackupFolderPrefs.clearAll(ctx)
                            BackupRestorePrefs.clear(ctx)
                            forceSetup = true
                            setupTick++
                        }) { Text("DEBUG : reset setup") }
                    }

                    return@MaterialTheme
                }
// -------------------- FIN SETUP SPL --------------------



                val exoPlayer = remember { AudioEngine.getPlayer(ctx) {} }

                val embeddedLyricsListener = remember { AudioEngine.getLyricsListener() }
                DisposableEffect(exoPlayer, embeddedLyricsListener) {
                    exoPlayer.addListener(embeddedLyricsListener)
                    onDispose { exoPlayer.removeListener(embeddedLyricsListener) }
                }

                var selectedTab by remember {
                    mutableStateOf<BottomTab>(initialTabKey?.let { tabFromKey(it) } ?: BottomTab.Home)
                }

                var closeMixSignal by remember { mutableIntStateOf(0) }

                var selectedQuickPlaylist by rememberSaveable { mutableStateOf<String?>(initialQuickPlaylist) }
                var openedPlaylist by rememberSaveable { mutableStateOf<String?>(initialOpenedPlaylist) }

                var currentPlayingUri by remember { mutableStateOf<String?>(null) }
                var isPlaying by remember { mutableStateOf(false) }
                var parsedLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }

                var currentPlayToken by remember { mutableStateOf(0L) }
                var currentTrackGainDb by remember { mutableStateOf(DEFAULT_TRACK_GAIN_DB) }
                var currentLyricsColor by remember { mutableStateOf(Color.White) }
                var refreshKey by remember { mutableStateOf(0) }

                var isNotesOpen by remember { mutableStateOf(false) }
                var isFillerSettingsOpen by remember { mutableStateOf(false) }

                var currentTrackTempo by remember { mutableStateOf(1f) }
                var currentTrackPitchSemi by remember { mutableStateOf(0) }
                var isMoreMenuOpen by remember { mutableStateOf(false) }
                var openNotesSignal by remember { mutableStateOf(0) }
                var isGlobalMixOpen by remember { mutableStateOf(false) }
                var playerMasterLevel by remember { mutableStateOf(1f) }
                var djMasterLevel by remember { mutableStateOf(1f) }
                var fillerMasterLevel by remember { mutableStateOf(0.6f) }

                var isMixerPreviewOpen by remember { mutableStateOf(false) }
                var textPrompterId by remember { mutableStateOf<String?>(null) }

                // ✅ overlay states
                var isSearchOpen by remember { mutableStateOf(false) }

                // ✅ MODE de recherche (PLAYER ou DJ)
                var searchMode by remember { mutableStateOf(SearchMode.PLAYER) }

                // ✅ Index pour SearchScreen
                var indexAll by remember { mutableStateOf(LibraryIndexCache.load(ctx) ?: emptyList()) }
                LaunchedEffect(refreshKey) {
                    indexAll = LibraryIndexCache.load(ctx) ?: emptyList()
                }

                val onEnded = rememberUpdatedState {
                    isPlaying = false
                    PlaybackCoordinator.onPlayerStop()
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
                    runCatching { exoPlayer.pause() }
                    isPlaying = false
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

                fun applyTempoAndPitchToPlayer(speed: Float, pitchSemi: Int) {
                    val safeSpeed = speed.coerceIn(0.5f, 2.0f)
                    val semiClamped = pitchSemi.coerceIn(-6, 6)
                    val pitchFactor = 2f.pow(semiClamped / 12f)
                    exoPlayer.playbackParameters = PlaybackParameters(safeSpeed, pitchFactor)
                }

                val playWithCrossfade: (String, String?) -> Unit = { uriString, playlistName ->

                    PlaybackCoordinator.onPlayerStart()
                    currentPlayingUri = uriString
                    embeddedLyricsListener.reset()

                    SessionPrefs.saveLastSession(ctx, uriString, playlistName)
                    runCatching { FillerSoundManager.fadeOutAndStop(400) }

                    val myToken = currentPlayToken + 1
                    currentPlayToken = myToken

                    currentTrackGainDb = clampTrackDb(
                        TrackVolumePrefs.getDb(ctx, uriString) ?: DEFAULT_TRACK_GAIN_DB
                    )
                    currentTrackTempo = TrackTempoPrefs.getTempo(ctx, uriString) ?: 1f
                    currentTrackPitchSemi = TrackPitchPrefs.getSemi(ctx, uriString) ?: 0

                    val result = runCatching {
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
                                parsedLines = LyricsResolver.resolveLyrics(ctx, uriString, embeddedOrNull)
                            },
                            onStart = {
                                isPlaying = true

                                playlistName?.let { pl ->
                                    PlaylistRepository.markSongPlayed(pl, uriString)
                                    refreshKey++
                                }

                                AudioEngine.applyTrackGainDb(currentTrackGainDb)
                                applyTempoAndPitchToPlayer(currentTrackTempo, currentTrackPitchSemi)
                            },
                            onError = {
                                isPlaying = false
                                PlaybackCoordinator.onPlayerStop()
                            }
                        )
                    }

                    if (result.isFailure) {
                        runCatching {
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                        }
                        isPlaying = false
                        PlaybackCoordinator.onPlayerStop()
                    }

                    selectedTab = BottomTab.Player
                    SessionPrefs.saveTab(ctx, TAB_PLAYER)
                }

                // ✅ helper : lancer depuis recherche en mode DJ
                fun playFromSearchInDj(uriString: String) {
                    // On récupère un nom "humain" depuis l'index (sinon fallback)
                    val name = indexAll.firstOrNull { it.uriString == uriString }?.name
                        ?: Uri.parse(uriString).lastPathSegment
                        ?: "Titre"

                    PlaybackCoordinator.onDjStart()
                    DjEngine.selectTrackFromList(uriString, name)

                    selectedTab = BottomTab.Dj
                    SessionPrefs.saveTab(ctx, TAB_DJ)
                }

                LaunchedEffect(Unit) {
                    val (lastUri, _) = SessionPrefs.getLastSession(ctx)
                    if (!lastUri.isNullOrBlank()) {
                        currentPlayingUri = lastUri

                        val overrideOk = LrcStorage.loadForTrack(ctx, lastUri)?.takeIf { it.isNotBlank() }
                        parsedLines = if (overrideOk != null) parseLrc(overrideOk) else emptyList()

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
                }

                DisposableEffect(Unit) {
                    onDispose {
                        try { TrackEqEngine.release() } catch (_: Exception) {}
                    }
                }

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
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
                                    selectedTab = tab
                                    SessionPrefs.saveTab(ctx, tabKeyOf(tab))
                                }
                            },
                            onSearchClick = {
                                // ✅ fermer ce qui doit se fermer quand on ouvre la recherche
                                textPrompterId = null
                                isNotesOpen = false
                                isFillerSettingsOpen = false
                                isMoreMenuOpen = false

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
                                selectedTab = BottomTab.Player
                                SessionPrefs.saveTab(ctx, TAB_PLAYER)
                            },
                            onOpenFondSonore = {
                                isMixerPreviewOpen = false
                                isFillerSettingsOpen = true
                            },
                            onOpenDj = {
                                isMixerPreviewOpen = false
                                selectedTab = BottomTab.Dj
                                SessionPrefs.saveTab(ctx, TAB_DJ)
                            },
                            onOpenTuner = {
                                isMixerPreviewOpen = false
                                selectedTab = BottomTab.Tuner
                                SessionPrefs.saveTab(ctx, TAB_TUNER)
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
                            when (selectedTab) {

                                is BottomTab.Home -> MixerHomePreviewScreen(
                                    modifier = contentModifier,
                                    onBack = {},
                                    onOpenPlayer = {
                                        selectedTab = BottomTab.Player
                                        SessionPrefs.saveTab(ctx, TAB_PLAYER)
                                    },
                                    onOpenFondSonore = { isFillerSettingsOpen = true },
                                    onOpenDj = {
                                        selectedTab = BottomTab.Dj
                                        SessionPrefs.saveTab(ctx, TAB_DJ)
                                    },
                                    onOpenTuner = {
                                        selectedTab = BottomTab.Tuner
                                        SessionPrefs.saveTab(ctx, TAB_TUNER)
                                    }
                                )

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
                                    onParsedLinesChange = { parsedLines = it },
                                    highlightColor = currentLyricsColor,
                                    currentTrackUri = currentPlayingUri,
                                    currentTrackGainDb = currentTrackGainDb,
                                    onTrackGainChange = { db ->
                                        val safeDb = clampTrackDb(db)
                                        currentPlayingUri?.let { uri -> TrackVolumePrefs.saveDb(ctx, uri, safeDb) }
                                        currentTrackGainDb = safeDb
                                        AudioEngine.applyTrackGainDb(safeDb)
                                    },
                                    tempo = currentTrackTempo,
                                    onTempoChange = { newTempo ->
                                        currentTrackTempo = newTempo
                                        applyTempoAndPitchToPlayer(currentTrackTempo, currentTrackPitchSemi)
                                        currentPlayingUri?.let { uri -> TrackTempoPrefs.saveTempo(ctx, uri, newTempo) }
                                    },
                                    pitchSemi = currentTrackPitchSemi,
                                    onPitchSemiChange = { newSemi ->
                                        val clamped = newSemi.coerceIn(-6, 6)
                                        currentTrackPitchSemi = clamped
                                        applyTempoAndPitchToPlayer(currentTrackTempo, currentTrackPitchSemi)
                                        currentPlayingUri?.let { uri -> TrackPitchPrefs.saveSemi(ctx, uri, clamped) }
                                    },
                                    onRequestShowPlaylist = { selectedTab = BottomTab.QuickPlaylists },
                                    getPositionMs = { exoPlayer.currentPosition },
                                    getDurationMs = { exoPlayer.duration },
                                    seekToMs = { ms -> exoPlayer.seekTo(ms) }
                                )

                                is BottomTab.QuickPlaylists -> QuickPlaylistsScreen(
                                    modifier = contentModifier,
                                    onPlaySong = { uri, playlistName, color ->

                                        when (val target = PlaybackRouter.resolve(uri, playlistName)) {

                                            is PlaybackRouter.Target.Prompter -> {
                                                textPrompterId = target.id
                                                return@QuickPlaylistsScreen
                                            }

                                            is PlaybackRouter.Target.Audio -> {
                                                playWithCrossfade(target.uri, target.playlist)
                                                currentPlayingUri = target.uri

                                                selectedQuickPlaylist = target.playlist
                                                target.playlist?.let { SessionPrefs.saveQuickPlaylist(ctx, it) }

                                                currentLyricsColor = color
                                                selectedTab = BottomTab.Player
                                                SessionPrefs.saveTab(ctx, TAB_PLAYER)
                                            }

                                            is PlaybackRouter.Target.Unknown -> {
                                                // rien
                                            }
                                        }
                                    },
                                    refreshKey = refreshKey,
                                    currentPlayingUri = currentPlayingUri,
                                    selectedPlaylist = selectedQuickPlaylist,
                                    onSelectedPlaylistChange = { name ->
                                        selectedQuickPlaylist = name
                                        SessionPrefs.saveQuickPlaylist(ctx, name)
                                    },
                                    onPlaylistColorChange = { _ -> currentLyricsColor = Color.White },
                                    onRequestShowPlayer = {
                                        selectedTab = BottomTab.Player
                                        SessionPrefs.saveTab(ctx, TAB_PLAYER)
                                    }
                                )

                                is BottomTab.Library -> LibraryScreen(
                                    modifier = contentModifier,
                                    onPlayFromLibrary = { uriString ->
                                        playWithCrossfade(uriString, null)
                                        currentPlayingUri = uriString
                                        currentLyricsColor = Color.White
                                        selectedTab = BottomTab.Player
                                        SessionPrefs.saveTab(ctx, TAB_PLAYER)
                                    }
                                )

                                is BottomTab.AllPlaylists -> AllPlaylistsScreen(
                                    modifier = contentModifier,
                                    onPlaylistClick = { name ->
                                        openedPlaylist = name
                                        SessionPrefs.saveOpenedPlaylist(ctx, name)
                                    }
                                )

                                is BottomTab.Dj -> DjScreen(modifier = contentModifier, context = ctx)

                                is BottomTab.More -> MoreScreen(
                                    modifier = contentModifier,
                                    context = ctx,
                                    onAfterImport = { refreshKey++ },
                                    onOpenTuner = {
                                        selectedTab = BottomTab.Tuner
                                        SessionPrefs.saveTab(ctx, TAB_TUNER)
                                    }
                                )

                                is BottomTab.Tuner -> TunerScreen(
                                    modifier = contentModifier,
                                    onClose = {
                                        selectedTab = BottomTab.Home
                                        SessionPrefs.saveTab(ctx, TAB_HOME)
                                    }
                                )

                                else -> Box(
                                    modifier = contentModifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Écran inconnu", color = Color.White)
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
                                            playWithCrossfade(uriString, null)
                                            currentPlayingUri = uriString
                                            selectedTab = BottomTab.Player
                                            SessionPrefs.saveTab(ctx, TAB_PLAYER)
                                        }

                                        SearchMode.DJ -> {
                                            playFromSearchInDj(uriString)
                                        }

                                        SearchMode.PLAYLIST -> {
                                            // ✅ comme PLAYER : on lance et on bascule sur le lecteur
                                            playWithCrossfade(uriString, null)
                                            currentPlayingUri = uriString
                                            currentLyricsColor = Color(0xFFE040FB)
                                            selectedTab = BottomTab.Player
                                            SessionPrefs.saveTab(ctx, TAB_PLAYER)
                                        }
                                    }
                                    isSearchOpen = false
                                },
                                restrictToUriStrings = if (searchMode == SearchMode.PLAYLIST) {
                                    openedPlaylist?.let { plName ->
                                        PlaylistRepository.getSongsFor(plName).toSet()
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }

                    // ✅ menu ⋮
                    DropdownMenu(
                        expanded = isMoreMenuOpen,
                        onDismissRequest = { isMoreMenuOpen = false }

                    ) {
                        DropdownMenuItem(
                            text = { Text("Bloc Notes") },
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
                            text = { Text("Bibliothèque") },
                            onClick = {
                                isMoreMenuOpen = false
                                selectedTab = BottomTab.Library
                                SessionPrefs.saveTab(ctx, TAB_LIBRARY)
                                // ✅ si on est sur “Fond sonore” (overlay), il faut le fermer sinon on reste bloqué dessus
                                isFillerSettingsOpen = false
                                isGlobalMixOpen = false
                                isSearchOpen = false
                                textPrompterId = null
                                isMixerPreviewOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("playlists") },
                            onClick = {
                                isMoreMenuOpen = false
                                selectedTab = BottomTab.AllPlaylists
                                SessionPrefs.saveTab(ctx, TAB_ALL)
                                // ✅ si on est sur “Fond sonore” (overlay), il faut le fermer sinon on reste bloqué dessus
                                isFillerSettingsOpen = false
                                isGlobalMixOpen = false
                                isSearchOpen = false
                                textPrompterId = null
                                isMixerPreviewOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Paramètres / Plus") },
                            onClick = {
                                isMoreMenuOpen = false
                                selectedTab = BottomTab.More
                                SessionPrefs.saveTab(ctx, TAB_MORE)
                                // ✅ si on est sur “Fond sonore” (overlay), il faut le fermer sinon on reste bloqué dessus
                                isFillerSettingsOpen = false
                                isGlobalMixOpen = false
                                isSearchOpen = false
                                textPrompterId = null
                                isMixerPreviewOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Accordeur") },
                            onClick = {
                                isMoreMenuOpen = false
                                selectedTab = BottomTab.Tuner
                                SessionPrefs.saveTab(ctx, TAB_TUNER)
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
    }
    override fun onStop() {
        super.onStop()
        BackupManager.autoSaveToDefaultBackupFile(this)
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