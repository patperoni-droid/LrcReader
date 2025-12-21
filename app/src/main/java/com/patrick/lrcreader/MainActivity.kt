@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.patrick.lrcreader.exo

// ─────────────────────────────
// Android / Activity
// ─────────────────────────────
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat

// ─────────────────────────────
// Compose
// ─────────────────────────────
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ─────────────────────────────
// Media3 / ExoPlayer
// ─────────────────────────────
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters

// ─────────────────────────────
// Kotlin / Coroutines / Math
// ─────────────────────────────
import kotlinx.coroutines.delay
import kotlin.math.pow

// ─────────────────────────────
// App – Core
// ─────────────────────────────
import com.patrick.lrcreader.core.*
import com.patrick.lrcreader.core.audio.AudioEngine
import com.patrick.lrcreader.core.audio.exoCrossfadePlay
import com.patrick.lrcreader.core.dj.DjEngine
import com.patrick.lrcreader.core.lyrics.LyricsResolver

// ─────────────────────────────
// App – UI
// ─────────────────────────────
import com.patrick.lrcreader.ui.*

class MainActivity : ComponentActivity() {

    companion object {
        // 🎚️ TRICHE SAFE : par défaut on démarre à -5 dB (anti saturation)
        private const val DEFAULT_TRACK_GAIN_DB = -5
        private const val MIN_TRACK_DB = -12
        private const val MAX_TRACK_DB = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 Pour que le clavier puisse "pousser" le contenu vers le haut
        WindowCompat.setDecorFitsSystemWindows(window, false)

        AutoRestore.restoreIfNeeded(this)

        val initialTabKey = SessionPrefs.getTab(this)
        val initialQuickPlaylist = SessionPrefs.getQuickPlaylist(this)
        val initialOpenedPlaylist = SessionPrefs.getOpenedPlaylist(this)

        DjEngine.init(this)

        val savedRoot = BackupFolderPrefs.get(this)
        if (savedRoot != null) {
            val hasPerm = contentResolver.persistedUriPermissions.any { p ->
                p.uri == savedRoot && p.isReadPermission
            }
            if (hasPerm) {
                LibrarySnapshot.rootFolderUri = savedRoot

                val cached = LibraryIndexCache.load(this)
                if (!cached.isNullOrEmpty()) {
                    LibrarySnapshot.entries = cached.map { it.uriString }
                    LibrarySnapshot.isReady = true
                }
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {

                val ctx = this@MainActivity

                // ✅ ExoPlayer unique + listener lyrics embedded (USLT/SYLT)
                val exoPlayer = remember {
                    AudioEngine.getPlayer(ctx) {
                        // fallback si jamais quelque chose déclenche une fin hors flux normal
                    }
                }


                val embeddedLyricsListener = remember { AudioEngine.getLyricsListener() }
                DisposableEffect(exoPlayer, embeddedLyricsListener) {
                    exoPlayer.addListener(embeddedLyricsListener)
                    Log.d("LYRICS", "Listener attached to exoPlayer ✅")

                    onDispose {
                        exoPlayer.removeListener(embeddedLyricsListener)
                        Log.d("LYRICS", "Listener removed from exoPlayer 🧹")
                    }
                }


                var selectedTab by remember {
                    mutableStateOf<BottomTab>(initialTabKey?.let { tabFromKey(it) } ?: BottomTab.Home)
                }
                var closeMixSignal by remember { mutableIntStateOf(0) }

                var selectedQuickPlaylist by rememberSaveable {
                    mutableStateOf<String?>(initialQuickPlaylist)
                }
                var openedPlaylist by rememberSaveable {
                    mutableStateOf<String?>(initialOpenedPlaylist)
                }

                var currentPlayingUri by remember { mutableStateOf<String?>(null) }
                var isPlaying by remember { mutableStateOf(false) }

                // 👉 paroles gérées manuellement
                var parsedLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }

                var currentPlayToken by remember { mutableStateOf(0L) }

                // ✅ IMPORTANT : par défaut -5 dB
                var currentTrackGainDb by remember { mutableStateOf(DEFAULT_TRACK_GAIN_DB) }

                var currentLyricsColor by remember { mutableStateOf(Color(0xFFE040FB)) }
                var refreshKey by remember { mutableStateOf(0) }

                var isNotesOpen by remember { mutableStateOf(false) }
                var isFillerSettingsOpen by remember { mutableStateOf(false) }

                var currentTrackTempo by remember { mutableStateOf(1f) }
                var currentTrackPitchSemi by remember { mutableStateOf(0) }

                var isGlobalMixOpen by remember { mutableStateOf(false) }
                var playerMasterLevel by remember { mutableStateOf(1f) }
                var djMasterLevel by remember { mutableStateOf(1f) }
                var fillerMasterLevel by remember { mutableStateOf(0.6f) }

                var isMixerPreviewOpen by remember { mutableStateOf(false) }
                var textPrompterId by remember { mutableStateOf<String?>(null) }

                // ---------------- Fin naturelle ExoPlayer (SANS onNaturalEnd) -------------------
                val onEnded = rememberUpdatedState {
                    isPlaying = false
                    PlaybackCoordinator.onPlayerStop()
                }

                DisposableEffect(exoPlayer) {
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                onEnded.value.invoke()
                            }
                        }
                    }
                    exoPlayer.addListener(listener)
                    onDispose { exoPlayer.removeListener(listener) }
                }

                // ---------------- PlaybackCoordinator -------------------

                PlaybackCoordinator.stopPlayer = {
                    runCatching {
                        exoPlayer.pause()
                        // optionnel mais propre : stop net
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                    }
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
                // ---------------- Gain par morceau ----------------------
                // ✅ On “triche” : PAS de +dB. On autorise seulement -12..0.
                // ✅ Et on démarre à -5 par défaut.
                fun clampTrackDb(db: Int): Int = db.coerceIn(MIN_TRACK_DB, MAX_TRACK_DB)



                // ---------------- Tempo + Pitch par morceau ----------------------

                fun applyTempoAndPitchToPlayer(speed: Float, pitchSemi: Int) {
                    val safeSpeed = speed.coerceIn(0.5f, 2.0f)
                    val semiClamped = pitchSemi.coerceIn(-6, 6)
                    val pitchFactor = 2f.pow(semiClamped / 12f)

                    // ✅ ExoPlayer : tempo + pitch
                    exoPlayer.playbackParameters = PlaybackParameters(safeSpeed, pitchFactor)
                }

                // ---------------- Play + crossfade ----------------------

                val playWithCrossfade: (String, String?) -> Unit = { uriString, playlistName ->

                    PlaybackCoordinator.onPlayerStart()
                    currentPlayingUri = uriString

                    embeddedLyricsListener.reset()
                    Log.d("LYRICS", "reset lyrics for new track ✅")

                    SessionPrefs.saveLastSession(
                        context = ctx,
                        trackUri = uriString,
                        playlistName = playlistName
                    )

                    runCatching { FillerSoundManager.fadeOutAndStop(400) }

                    val myToken = currentPlayToken + 1
                    currentPlayToken = myToken

                    // ✅ Volume / tempo / pitch par morceau
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
                                parsedLines = LyricsResolver.resolveLyrics(
                                    context = ctx,
                                    trackUriString = uriString,
                                    embeddedLyrics = embeddedOrNull
                                )
                            },

                            onStart = {
                                isPlaying = true

                                playlistName?.let { pl ->
                                    PlaylistRepository.moveSongToEnd(pl, uriString)
                                    refreshKey++
                                }

                                // ✅ Applique le niveau PAR TITRE (triche -5 par défaut)
                                AudioEngine.applyTrackGainDb(currentTrackGainDb)

                                applyTempoAndPitchToPlayer(currentTrackTempo, currentTrackPitchSemi)

                                // EQ par morceau (encore sur MediaPlayer)
                                currentPlayingUri?.let { uri ->
                                    val settings = TrackEqPrefs.load(ctx, uri)
                                    if (settings != null) {
                                        TrackEqEngine.setBands(
                                            lowDb = settings.low,
                                            midDb = settings.mid,
                                            highDb = settings.high
                                        )
                                    } else {
                                        TrackEqEngine.setBands(0f, 0f, 0f)
                                    }
                                }
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

                // ---------------- Restore dernière session (état seulement) ----------------------

                LaunchedEffect(Unit) {
                    val (lastUri, _) = SessionPrefs.getLastSession(ctx)

                    if (!lastUri.isNullOrBlank()) {
                        currentPlayingUri = lastUri

                        val overrideOk = LrcStorage
                            .loadForTrack(ctx, lastUri)
                            ?.takeIf { it.isNotBlank() }

                        parsedLines = if (overrideOk != null) parseLrc(overrideOk) else emptyList()

                        // ✅ restore volume : défaut -5
                        currentTrackGainDb = clampTrackDb(
                            TrackVolumePrefs.getDb(ctx, lastUri) ?: DEFAULT_TRACK_GAIN_DB
                        )
                        currentTrackTempo = TrackTempoPrefs.getTempo(ctx, lastUri) ?: 1f
                        currentTrackPitchSemi = TrackPitchPrefs.getSemi(ctx, lastUri) ?: 0
                    }
                }

                // ---------------- Release ----------------------

                DisposableEffect(Unit) {
                    onDispose {

                        try { TrackEqEngine.release() } catch (_: Exception) {}

                    }
                }

                // ---------------- UI principale ----------------------

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        BottomTabsBar(
                            selected = selectedTab,
                            onSelected = { tab ->
                                if (tab is BottomTab.Player) closeMixSignal++

                                selectedTab = tab
                                SessionPrefs.saveTab(ctx, tabKeyOf(tab))

                                isFillerSettingsOpen = false
                                isGlobalMixOpen = false
                                isNotesOpen = false
                                textPrompterId = null
                                isMixerPreviewOpen = false
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
                        return@Scaffold
                    }

                    if (isFillerSettingsOpen) {
                        FillerSoundScreen(
                            context = ctx,
                            onBack = { isFillerSettingsOpen = false }
                        )
                        return@Scaffold
                    }

                    if (isGlobalMixOpen) {
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
                            onFillerLevelChange = { lvl ->
                                fillerMasterLevel = lvl
                            },
                            onBack = { isGlobalMixOpen = false }
                        )
                        return@Scaffold
                    }

                    textPrompterId?.let { tid ->
                        TextPrompterScreen(
                            modifier = contentModifier,
                            songId = tid,
                            onClose = { textPrompterId = null }
                        )
                        return@Scaffold
                    }

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
                                val safeDb = clampTrackDb(db) // ✅ interdit +dB
                                currentPlayingUri?.let { uri ->
                                    TrackVolumePrefs.saveDb(ctx, uri, safeDb)
                                }
                                currentTrackGainDb = safeDb

                                // ✅ APPLIQUER AU VRAI LECTEUR (Exo)
                                AudioEngine.applyTrackGainDb(safeDb)
                            },

                            tempo = currentTrackTempo,
                            onTempoChange = { newTempo ->
                                currentTrackTempo = newTempo
                                applyTempoAndPitchToPlayer(currentTrackTempo, currentTrackPitchSemi)
                                currentPlayingUri?.let { uri ->
                                    TrackTempoPrefs.saveTempo(ctx, uri, newTempo)
                                }
                            },
                            pitchSemi = currentTrackPitchSemi,
                            onPitchSemiChange = { newSemi ->
                                val clamped = newSemi.coerceIn(-6, 6)
                                currentTrackPitchSemi = clamped
                                applyTempoAndPitchToPlayer(currentTrackTempo, currentTrackPitchSemi)
                                currentPlayingUri?.let { uri ->
                                    TrackPitchPrefs.saveSemi(ctx, uri, clamped)
                                }
                            },
                            onRequestShowPlaylist = {
                                selectedTab = BottomTab.QuickPlaylists
                            },

                            getPositionMs = { exoPlayer.currentPosition },
                            getDurationMs = { exoPlayer.duration },
                            seekToMs = { ms -> exoPlayer.seekTo(ms) }
                        )

                        is BottomTab.QuickPlaylists -> QuickPlaylistsScreen(
                            modifier = contentModifier,
                            onPlaySong = { uri, playlistName, color ->
                                if (uri.startsWith("prompter://")) {
                                    val rawId = uri.removePrefix("prompter://")
                                    val numeric = rawId.toLongOrNull()
                                    textPrompterId = if (numeric != null) "note:$numeric" else "text:$rawId"

                                    selectedQuickPlaylist = playlistName
                                    SessionPrefs.saveQuickPlaylist(ctx, playlistName)
                                    currentLyricsColor = color
                                } else {
                                    playWithCrossfade(uri, playlistName)
                                    currentPlayingUri = uri
                                    selectedQuickPlaylist = playlistName
                                    SessionPrefs.saveQuickPlaylist(ctx, playlistName)
                                    currentLyricsColor = color
                                }
                            },
                            refreshKey = refreshKey,
                            currentPlayingUri = currentPlayingUri,
                            selectedPlaylist = selectedQuickPlaylist,
                            onSelectedPlaylistChange = { name ->
                                selectedQuickPlaylist = name
                                SessionPrefs.saveQuickPlaylist(ctx, name)
                            }
                        )

                        is BottomTab.Library -> LibraryScreen(
                            modifier = contentModifier,
                            onPlayFromLibrary = { uriString ->
                                playWithCrossfade(uriString, null)
                                currentPlayingUri = uriString
                                currentLyricsColor = Color(0xFFE040FB)
                                selectedTab = BottomTab.Player
                                SessionPrefs.saveTab(ctx, TAB_PLAYER)
                            }
                        )

                        is BottomTab.AllPlaylists -> {
                            AllPlaylistsScreen(
                                modifier = contentModifier,
                                onPlaylistClick = { name ->
                                    openedPlaylist = name
                                    SessionPrefs.saveOpenedPlaylist(ctx, name)
                                }
                            )
                        }

                        is BottomTab.More -> MoreScreen(
                            modifier = contentModifier,
                            context = ctx,
                            onAfterImport = { refreshKey++ },
                            onOpenTuner = {
                                selectedTab = BottomTab.Tuner
                                SessionPrefs.saveTab(ctx, TAB_TUNER)
                            }
                        )

                        is BottomTab.Dj -> DjScreen(
                            modifier = contentModifier,
                            context = ctx
                        )

                        is BottomTab.Tuner -> TunerScreen(
                            modifier = contentModifier,
                            onClose = {
                                selectedTab = BottomTab.Home
                                SessionPrefs.saveTab(ctx, TAB_HOME)
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

private fun tabKeyOf(tab: BottomTab): String = when (tab) {
    is BottomTab.Home -> TAB_HOME
    is BottomTab.Player -> TAB_PLAYER
    is BottomTab.QuickPlaylists -> TAB_QUICK
    is BottomTab.Library -> TAB_LIBRARY
    is BottomTab.AllPlaylists -> TAB_ALL
    is BottomTab.More -> TAB_MORE
    is BottomTab.Dj -> TAB_DJ
    is BottomTab.Tuner -> TAB_TUNER
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
    else -> BottomTab.Home
}
