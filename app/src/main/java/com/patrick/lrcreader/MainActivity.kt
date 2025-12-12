package com.patrick.lrcreader.exo

import androidx.compose.foundation.layout.ime
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import com.patrick.lrcreader.core.*
import com.patrick.lrcreader.core.dj.DjEngine
import com.patrick.lrcreader.core.TrackEqEngine
import com.patrick.lrcreader.core.TrackEqPrefs
import com.patrick.lrcreader.core.TrackPitchPrefs
import com.patrick.lrcreader.ui.*
import com.patrick.lrcreader.ui.TextPrompterScreen
import kotlin.math.pow

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

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {

                val ctx = this@MainActivity

                val mediaPlayer = remember { MediaPlayer() }

                // EQ attaché à la session du MediaPlayer
                LaunchedEffect(mediaPlayer.audioSessionId) {
                    TrackEqEngine.attachToSession(mediaPlayer.audioSessionId)
                }

                val loudnessEnhancer = remember {
                    LoudnessEnhancer(mediaPlayer.audioSessionId).apply {
                        enabled = true
                    }
                }

                var selectedTab by remember {
                    mutableStateOf(initialTabKey?.let { tabFromKey(it) } ?: BottomTab.Home)
                }

                var selectedQuickPlaylist by rememberSaveable {
                    mutableStateOf<String?>(initialQuickPlaylist)
                }

                var openedPlaylist by rememberSaveable {
                    mutableStateOf<String?>(initialOpenedPlaylist)
                }

                var currentPlayingUri by remember { mutableStateOf<String?>(null) }
                var isPlaying by remember { mutableStateOf(false) }

                // 👉 Les paroles sont maintenant reset à chaque changement de morceau
                // 👉 Les paroles sont gérées manuellement (lecture + restore session)
                var parsedLines by remember {
                    mutableStateOf<List<LrcLine>>(emptyList())
                }

                var currentPlayToken by remember { mutableStateOf(0L) }
                var currentTrackGainDb by remember { mutableStateOf(0) }
                var currentLyricsColor by remember { mutableStateOf(Color(0xFFE040FB)) }
                var refreshKey by remember { mutableStateOf(0) }

                var isNotesOpen by remember { mutableStateOf(false) }
                var isFillerSettingsOpen by remember { mutableStateOf(false) }

                var currentTrackTempo by remember { mutableStateOf(1f) }     // 0.5x..2x
                var currentTrackPitchSemi by remember { mutableStateOf(0) }  // -6..+6

                var isGlobalMixOpen by remember { mutableStateOf(false) }
                var playerMasterLevel by remember { mutableStateOf(1f) }
                var djMasterLevel by remember { mutableStateOf(1f) }
                var fillerMasterLevel by remember { mutableStateOf(0.6f) }

                // 👇 écran plein écran "console" Mixer (maquette visuelle)
                var isMixerPreviewOpen by remember { mutableStateOf(false) }

                // ID normalisé pour le prompteur ("note:123" ou "text:abc")
                var textPrompterId by remember { mutableStateOf<String?>(null) }

                // ---------------- PlaybackCoordinator -------------------

                PlaybackCoordinator.stopPlayer = {
                    try {
                        if (mediaPlayer.isPlaying) mediaPlayer.pause()
                        isPlaying = false
                    } catch (_: Exception) {}
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

                // ---------------- Tempo + Pitch par morceau ---------------------

                fun applyTempoAndPitchToPlayer(speed: Float, pitchSemi: Int) {
                    try {
                        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
                        val semiClamped = pitchSemi.coerceIn(-6, 6)

                        // facteur de pitch en fonction des demi-tons
                        val pitchFactor = 2f.pow(semiClamped / 12f)

                        val params = mediaPlayer.playbackParams
                            .setSpeed(safeSpeed)   // vitesse
                            .setPitch(pitchFactor) // hauteur

                        mediaPlayer.playbackParams = params
                    } catch (_: Exception) {}
                }

                // ---------------- Play + crossfade ----------------------

                val playWithCrossfade: (String, String?) -> Unit = { uriString, playlistName ->

                    PlaybackCoordinator.onPlayerStart()
                    currentPlayingUri = uriString

                    // ✅ On mémorise la dernière session (playlist + titre)
                    SessionPrefs.saveLastSession(
                        context = ctx,
                        trackUri = uriString,
                        playlistName = playlistName
                    )

                    runCatching { FillerSoundManager.fadeOutAndStop(400) }

                    val myToken = currentPlayToken + 1
                    currentPlayToken = myToken

                    // Volume par morceau
                    currentTrackGainDb = TrackVolumePrefs.getDb(ctx, uriString) ?: 0
                    // Tempo par morceau
                    currentTrackTempo = TrackTempoPrefs.getTempo(ctx, uriString) ?: 1f
                    // Tonalité par morceau (demi-tons)
                    currentTrackPitchSemi = TrackPitchPrefs.getSemi(ctx, uriString) ?: 0

                    val result = runCatching {
                        crossfadePlay(
                            context = ctx,
                            mediaPlayer = mediaPlayer,
                            uriString = uriString,
                            playlistName = playlistName,
                            playToken = myToken,
                            getCurrentToken = { currentPlayToken },
                            onLyricsLoaded = { _ /* original ignoré */ ->
                                val override = LrcStorage.loadForTrack(ctx, uriString)

                                parsedLines = if (override != null) {
                                    parseLrc(override)
                                } else {
                                    emptyList()   // pas de .lrc sauvegardé → pas de paroles
                                }
                            },
                            onStart = {
                                isPlaying = true
                                playlistName?.let { pl ->
                                    PlaylistRepository
                                    PlaylistRepository.moveSongToEnd(pl, uriString)
                                    refreshKey++
                                }

                                // Gain
                                applyGainToPlayer(currentTrackGainDb)

                                // Tempo + pitch
                                applyTempoAndPitchToPlayer(
                                    currentTrackTempo,
                                    currentTrackPitchSemi
                                )

                                // EQ par morceau
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
                                mediaPlayer.reset()
                                PlaybackCoordinator.onPlayerStop()
                            },
                            onNaturalEnd = {
                                isPlaying = false
                                PlaybackCoordinator.onPlayerStop()
                                runCatching { FillerSoundManager.startIfConfigured(ctx) }
                            }
                        )
                    }

                    if (result.isFailure) {
                        mediaPlayer.reset()
                        isPlaying = false
                        PlaybackCoordinator.onPlayerStop()
                    }

                    selectedTab = BottomTab.Player
                    SessionPrefs.saveTab(ctx, TAB_PLAYER)
                }
                LaunchedEffect(Unit) {
                    val (lastUri, lastPlaylistName) = SessionPrefs.getLastSession(ctx)

                    if (!lastUri.isNullOrBlank()) {
                        // On remet juste l'état du dernier titre,
                        // SANS lancer la lecture.

                        currentPlayingUri = lastUri

                        // Paroles .lrc si dispo
                        val override = LrcStorage.loadForTrack(ctx, lastUri)
                        parsedLines = if (override != null) {
                            parseLrc(override)
                        } else {
                            emptyList()
                        }

                        // Gain / tempo / pitch du morceau
                        currentTrackGainDb = TrackVolumePrefs.getDb(ctx, lastUri) ?: 0
                        currentTrackTempo = TrackTempoPrefs.getTempo(ctx, lastUri) ?: 1f
                        currentTrackPitchSemi = TrackPitchPrefs.getSemi(ctx, lastUri) ?: 0

                        // ⚠️ IMPORTANT : on NE TOUCHE PAS à :
                        // - selectedTab
                        // - isPlaying
                        // Résultat : l'appli s'ouvre sur le même onglet qu'avant,
                        // avec les mêmes playlists, etc. (grâce à initialTabKey, initialQuickPlaylist, etc.)
                        // Et le morceau est prêt, mais en pause.
                    }
                }
                // ✅ RESTORE DERNIÈRE SESSION ICI (après la déclaration de playWithCrossfade)

                // ... puis tout le reste (DisposableEffect, Scaffold, etc.)

                // ---------------- Release -------------------------------

                DisposableEffect(Unit) {
                    onDispose {
                        try { loudnessEnhancer.release() } catch (_: Exception) {}
                        try { TrackEqEngine.release() } catch (_: Exception) {}
                        try { mediaPlayer.release() } catch (_: Exception) {}
                    }
                }

                // ---------------- UI principale -------------------------

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        BottomTabsBar(
                            selected = selectedTab,
                            onSelected = { tab ->
                                selectedTab = tab
                                SessionPrefs.saveTab(ctx, tabKeyOf(tab))

                                // on ferme tous les écrans plein écran / overlays
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

                    // 🔹 CONSOLE MIXER VISUELLE (maquette)
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

                    // Fond sonore
                    if (isFillerSettingsOpen) {
                        FillerSoundScreen(
                            context = ctx,
                            onBack = { isFillerSettingsOpen = false }
                        )
                        return@Scaffold
                    }

                    // Mixage global
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

                    // Prompteur texte plein écran
                    textPrompterId?.let { tid ->
                        TextPrompterScreen(
                            modifier = contentModifier,
                            songId = tid,
                            onClose = { textPrompterId = null }
                        )
                        return@Scaffold
                    }

                    // -------- navigation par onglet --------

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
                            isPlaying = isPlaying,
                            onIsPlayingChange = { isPlaying = it },
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
                                applyTempoAndPitchToPlayer(
                                    currentTrackTempo,
                                    currentTrackPitchSemi
                                )
                                currentPlayingUri?.let { uri ->
                                    TrackTempoPrefs.saveTempo(ctx, uri, newTempo)
                                }
                            },
                            pitchSemi = currentTrackPitchSemi,
                            onPitchSemiChange = { newSemi ->
                                val clamped = newSemi.coerceIn(-6, 6)
                                currentTrackPitchSemi = clamped
                                applyTempoAndPitchToPlayer(
                                    currentTrackTempo,
                                    currentTrackPitchSemi
                                )
                                currentPlayingUri?.let { uri ->
                                    TrackPitchPrefs.saveSemi(ctx, uri, clamped)
                                }
                            },
                            onRequestShowPlaylist = {
                                selectedTab = BottomTab.QuickPlaylists
                            }
                        )

                        is BottomTab.QuickPlaylists -> QuickPlaylistsScreen(
                            modifier = contentModifier,
                            onPlaySong = { uri, playlistName, color ->
                                if (uri.startsWith("prompter://")) {
                                    val rawId = uri.removePrefix("prompter://")
                                    val numeric = rawId.toLongOrNull()
                                    textPrompterId =
                                        if (numeric != null) "note:$numeric"
                                        else "text:$rawId"

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

                        is BottomTab.Library ->
                            LibraryScreen(
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
                                    // Si un jour tu veux ouvrir un écran "détail playlist",
                                    // tu auras déjà la valeur ici.
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

                    // -------- overlay "Mes notes" --------

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
    TAB_TUNER -> BottomTab.Home
    else -> BottomTab.Home
}