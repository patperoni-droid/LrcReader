package com.patrick.lrcreader.exo

import android.util.Log
import com.patrick.lrcreader.core.lyrics.LyricsResolver
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import com.patrick.lrcreader.core.*
import com.patrick.lrcreader.core.audio.AudioEngine
import com.patrick.lrcreader.core.audio.exoCrossfadePlay
import com.patrick.lrcreader.core.dj.DjEngine
import com.patrick.lrcreader.ui.*
import kotlin.math.pow

@androidx.media3.common.util.UnstableApi
class MainActivity : ComponentActivity() {
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
                    android.util.Log.d("LYRICS", "Listener attached to exoPlayer ✅")

                    onDispose {
                        exoPlayer.removeListener(embeddedLyricsListener)
                        android.util.Log.d("LYRICS", "Listener removed from exoPlayer 🧹")
                    }
                }

                // ⚠️ On garde MediaPlayer tant que PlayerScreen utilise encore MediaPlayer.
                val mediaPlayer = remember { MediaPlayer() }

                // EQ attaché à la session du MediaPlayer
                LaunchedEffect(mediaPlayer.audioSessionId) {
                    TrackEqEngine.attachToSession(mediaPlayer.audioSessionId)
                }

                val loudnessEnhancer = remember {
                    LoudnessEnhancer(mediaPlayer.audioSessionId).apply { enabled = true }
                }

                var selectedTab by remember {
                    mutableStateOf(initialTabKey?.let { tabFromKey(it) } ?: BottomTab.Home)
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
                var currentTrackGainDb by remember { mutableStateOf(0) }
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
                    runCatching { exoPlayer.pause() }
                    isPlaying = false
                }

                PlaybackCoordinator.stopDj = {
                    runCatching { DjEngine.stopDj() }
                }

                PlaybackCoordinator.stopFiller = {
                    runCatching { FillerSoundManager.fadeOutAndStop(200) }
                }

                // ---------------- Gain par morceau ----------------------

                fun applyGainToPlayer(db: Int) {
                    try {
                        val clamped = db.coerceIn(-12, 12)
                        val master = playerMasterLevel.coerceIn(0f, 1f)

                        if (clamped <= 0) {
                            val linear = 10f.pow(clamped / 20f)
                            val v = linear * master
                            mediaPlayer.setVolume(v, v)
                            loudnessEnhancer.setTargetGain(0)
                        } else {
                            val v = 1f * master
                            mediaPlayer.setVolume(v, v)
                            loudnessEnhancer.setTargetGain(clamped * 100)
                        }
                    } catch (_: Exception) {}
                }

                // ---------------- Tempo + Pitch par morceau ----------------------

                fun applyTempoAndPitchToPlayer(speed: Float, pitchSemi: Int) {
                    try {
                        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
                        val semiClamped = pitchSemi.coerceIn(-6, 6)

                        val pitchFactor = 2f.pow(semiClamped / 12f)

                        // ⚠️ Sur certains devices, changer playbackParams en pause peut relancer
                        if (!mediaPlayer.isPlaying) return

                        val params = mediaPlayer.playbackParams
                            .setSpeed(safeSpeed)
                            .setPitch(pitchFactor)

                        mediaPlayer.playbackParams = params
                    } catch (_: Exception) {}
                }

                // ---------------- Play + crossfade ----------------------

                val playWithCrossfade: (String, String?) -> Unit = { uriString, playlistName ->

                    PlaybackCoordinator.onPlayerStart()
                    currentPlayingUri = uriString
                  // ok : on vide au démarrage, puis on recharge via onLyricsLoaded
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

                    // Volume / tempo / pitch par morceau
                    currentTrackGainDb = TrackVolumePrefs.getDb(ctx, uriString) ?: 0
                    currentTrackTempo = TrackTempoPrefs.getTempo(ctx, uriString) ?: 1f
                    currentTrackPitchSemi = TrackPitchPrefs.getSemi(ctx, uriString) ?: 0

                    val result = runCatching {
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

                                // ⚠️ Ces fonctions pilotent encore MediaPlayer (pas Exo) pour l’instant
                                applyGainToPlayer(currentTrackGainDb)
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
                            // ✅ PAS de onNaturalEnd ici (géré par listener STATE_ENDED)
                        )
                    }

                    if (result.isFailure) {
                        runCatching { mediaPlayer.reset() }
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

                        // Paroles .lrc si dispo (RESTORE: on ne lit pas les embedded ici)
                        val overrideOk = LrcStorage
                            .loadForTrack(ctx, lastUri)
                            ?.takeIf { it.isNotBlank() }

                        parsedLines = if (overrideOk != null) parseLrc(overrideOk) else emptyList()

                        currentTrackGainDb = TrackVolumePrefs.getDb(ctx, lastUri) ?: 0
                        currentTrackTempo = TrackTempoPrefs.getTempo(ctx, lastUri) ?: 1f
                        currentTrackPitchSemi = TrackPitchPrefs.getSemi(ctx, lastUri) ?: 0
                    }
                }

                // ---------------- Release ----------------------

                DisposableEffect(Unit) {
                    onDispose {
                        try { loudnessEnhancer.release() } catch (_: Exception) {}
                        try { TrackEqEngine.release() } catch (_: Exception) {}
                        try { mediaPlayer.release() } catch (_: Exception) {}
                        // ExoPlayer release géré par AudioEngine (si besoin plus tard)
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
                                applyGainToPlayer(currentTrackGainDb)
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
                            mediaPlayer = mediaPlayer,
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
                                currentPlayingUri?.let { uri ->
                                    TrackVolumePrefs.saveDb(ctx, uri, db)
                                }
                                currentTrackGainDb = db
                                applyGainToPlayer(db)
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
