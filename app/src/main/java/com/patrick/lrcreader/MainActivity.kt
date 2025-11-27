package com.patrick.lrcreader.exo

import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.core.*
import com.patrick.lrcreader.core.dj.DjEngine
import com.patrick.lrcreader.ui.*
import com.patrick.lrcreader.ui.TextPrompterScreen
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AutoRestore.restoreIfNeeded(this)

        val initialTabKey = SessionPrefs.getTab(this)
        val initialQuickPlaylist = SessionPrefs.getQuickPlaylist(this)
        val initialOpenedPlaylist = SessionPrefs.getOpenedPlaylist(this)

        DjEngine.init(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {

                val ctx = this@MainActivity

                val mediaPlayer = remember { MediaPlayer() }

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
                var parsedLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
                var currentPlayToken by remember { mutableStateOf(0L) }
                var currentTrackGainDb by remember { mutableStateOf(0) }
                var currentLyricsColor by remember { mutableStateOf(Color(0xFFE040FB)) }
                var refreshKey by remember { mutableStateOf(0) }

                var isNotesOpen by remember { mutableStateOf(false) }
                var isFillerSettingsOpen by remember { mutableStateOf(false) }
                var currentTrackTempo by remember { mutableStateOf(1f) }

                var isGlobalMixOpen by remember { mutableStateOf(false) }
                var playerMasterLevel by remember { mutableStateOf(1f) }
                var djMasterLevel by remember { mutableStateOf(1f) }
                var fillerMasterLevel by remember { mutableStateOf(0.6f) }

                // 👇 nouveau : écran plein écran "console" Mixer (maquette visuelle)
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

                // ---------------- Tempo par morceau ---------------------

                fun applyTempoToPlayer(speed: Float) {
                    try {
                        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
                        val params = mediaPlayer.playbackParams
                            .setSpeed(safeSpeed)
                            .setPitch(1f)
                        mediaPlayer.playbackParams = params
                    } catch (_: Exception) {}
                }

                // ---------------- Play + crossfade ----------------------

                val playWithCrossfade: (String, String?) -> Unit = { uriString, playlistName ->

                    PlaybackCoordinator.onPlayerStart()
                    currentPlayingUri = uriString
                    runCatching { FillerSoundManager.fadeOutAndStop(400) }

                    val myToken = currentPlayToken + 1
                    currentPlayToken = myToken

                    currentTrackGainDb = TrackVolumePrefs.getDb(ctx, uriString) ?: 0
                    currentTrackTempo = TrackTempoPrefs.getTempo(ctx, uriString) ?: 1f

                    val result = runCatching {
                        crossfadePlay(
                            context = ctx,
                            mediaPlayer = mediaPlayer,
                            uriString = uriString,
                            playlistName = playlistName,
                            playToken = myToken,
                            getCurrentToken = { currentPlayToken },
                            onLyricsLoaded = { original ->
                                val override = LrcStorage.loadForTrack(ctx, uriString)
                                parsedLines = parseLrc(override ?: original ?: "")
                            },
                            onStart = {
                                isPlaying = true
                                applyGainToPlayer(currentTrackGainDb)
                                applyTempoToPlayer(currentTrackTempo)
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

                // ---------------- Release -------------------------------

                DisposableEffect(Unit) {
                    onDispose {
                        loudnessEnhancer.release()
                        mediaPlayer.release()
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
                                isMixerPreviewOpen = false   // 👈 on ferme aussi la console mixer
                            }
                        )
                    }
                ) { innerPadding ->

                    // 🔹 CONSOLE MIXER VISUELLE (maquette)
                    if (isMixerPreviewOpen) {
                        MixerHomePreviewScreen(
                            modifier = Modifier.padding(innerPadding),
                            onOpenPlayer = {
                                isMixerPreviewOpen = false
                                selectedTab = BottomTab.QuickPlaylists
                                SessionPrefs.saveTab(ctx, TAB_QUICK)
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
                            modifier = Modifier.padding(innerPadding),
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
                            modifier = Modifier.padding(innerPadding),
                            songId = tid,
                            onClose = { textPrompterId = null }
                        )
                        return@Scaffold
                    }

                    // -------- navigation par onglet --------

                    when (selectedTab) {

                        is BottomTab.Home -> MixerHomePreviewScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBack = {
                                // Comme c'est l'écran Home, on ne fait rien de spécial.
                                // Si tu veux, on pourrait plus tard revenir vers un autre onglet ici.
                            },
                            onOpenPlayer = {
                                selectedTab = BottomTab.QuickPlaylists
                                SessionPrefs.saveTab(ctx, TAB_QUICK)
                            },
                            onOpenFondSonore = {
                                isFillerSettingsOpen = true
                            },
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
                            modifier = Modifier.padding(innerPadding),
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
                                applyTempoToPlayer(newTempo)
                                currentPlayingUri?.let { uri ->
                                    TrackTempoPrefs.saveTempo(ctx, uri, newTempo)
                                }
                            },
                            onRequestShowPlaylist = {
                                selectedTab = BottomTab.QuickPlaylists
                            }
                        )

                        is BottomTab.QuickPlaylists -> QuickPlaylistsScreen(
                            modifier = Modifier.padding(innerPadding),
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
                            LibraryScreen(modifier = Modifier.padding(innerPadding))

                        is BottomTab.AllPlaylists -> {
                            val m = Modifier.padding(innerPadding)
                            if (openedPlaylist == null) {
                                AllPlaylistsScreen(
                                    modifier = m,
                                    onPlaylistClick = { name ->
                                        openedPlaylist = name
                                        SessionPrefs.saveOpenedPlaylist(ctx, name)
                                    }
                                )
                            }
                        }

                        is BottomTab.More -> MoreScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = ctx,
                            onAfterImport = { refreshKey++ },
                            onOpenTuner = {
                                selectedTab = BottomTab.Tuner
                                SessionPrefs.saveTab(ctx, TAB_TUNER)
                            }
                        )
                        is BottomTab.Dj -> DjScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = ctx
                        )

                        is BottomTab.Tuner -> TunerScreen(
                            modifier = Modifier.padding(innerPadding),
                            onClose = {
                                selectedTab = BottomTab.Home
                                SessionPrefs.saveTab(ctx, TAB_HOME)
                            }
                        )
                    }

                    // -------- overlay "Mes notes" --------

                    if (isNotesOpen) {
                        Box(
                            modifier = Modifier
                                .padding(innerPadding)
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