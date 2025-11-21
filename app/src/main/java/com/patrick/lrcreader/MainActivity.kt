package com.patrick.lrcreader.exo

import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.patrick.lrcreader.core.*
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.dj.DjEngine
import com.patrick.lrcreader.ui.*
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AutoRestore.restoreIfNeeded(this)

        val initialTabKey = SessionPrefs.getTab(this)
        val initialQuickPlaylist = SessionPrefs.getQuickPlaylist(this)
        val initialOpenedPlaylist = SessionPrefs.getOpenedPlaylist(this)

        // 🔴 initialisation DJ
        DjEngine.init(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val ctx = this@MainActivity
                val mediaPlayer = remember { MediaPlayer() }

                val loudnessEnhancer = remember {
                    LoudnessEnhancer(mediaPlayer.audioSessionId).apply { enabled = true }
                }

                var selectedTab by remember {
                    mutableStateOf(
                        initialTabKey?.let { tabFromKey(it) } ?: BottomTab.Home
                    )
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
                val repoVersion by PlaylistRepository.version

                // Tempo par morceau
                var currentTrackTempo by remember { mutableStateOf(1f) }

                fun applyGainToPlayer(db: Int) {
                    try {
                        val clamped = db.coerceIn(-12, 12)
                        if (clamped <= 0) {
                            val linear = 10f.pow(clamped / 20f)
                            mediaPlayer.setVolume(linear, linear)
                            loudnessEnhancer.setTargetGain(0)
                        } else {
                            mediaPlayer.setVolume(1f, 1f)
                            loudnessEnhancer.setTargetGain(clamped * 100)
                        }
                    } catch (_: Exception) {
                    }
                }

                fun applyTempoToPlayer(speed: Float) {
                    try {
                        val params = mediaPlayer.playbackParams
                            .setSpeed(speed)
                            .setPitch(1f)
                        mediaPlayer.playbackParams = params
                    } catch (_: Exception) {
                    }
                }

                val playWithCrossfade: (String, String?) -> Unit = remember {
                    { uriString, playlistName ->
                        currentPlayingUri = uriString
                        runCatching { FillerSoundManager.fadeOutAndStop(400) }

                        val myToken = currentPlayToken + 1
                        currentPlayToken = myToken

                        val savedDb = TrackVolumePrefs.getDb(ctx, uriString) ?: 0
                        currentTrackGainDb = savedDb

                        val savedTempo = TrackTempoPrefs.getTempo(ctx, uriString) ?: 1f
                        currentTrackTempo = savedTempo

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
                                },
                                onNaturalEnd = {
                                    isPlaying = false
                                    runCatching { FillerSoundManager.startIfConfigured(ctx) }
                                }
                            )
                        }

                        if (result.isFailure) {
                            mediaPlayer.reset()
                            isPlaying = false
                        }

                        selectedTab = BottomTab.Player
                        SessionPrefs.saveTab(ctx, TAB_PLAYER)
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        loudnessEnhancer.release()
                        mediaPlayer.release()
                    }
                }

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        BottomTabsBar(
                            selected = selectedTab,
                            onSelected = {
                                selectedTab = it
                                SessionPrefs.saveTab(ctx, tabKeyOf(it))
                            }
                        )
                    }
                ) { innerPadding ->
                    when (selectedTab) {

                        // 🏠 ACCUEIL
                        is BottomTab.Home -> HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onOpenPlayer = {
                                selectedTab = BottomTab.Player
                                SessionPrefs.saveTab(ctx, TAB_PLAYER)
                            },
                            onOpenConcertMode = {
                                DisplayPrefs.setConcertMode(ctx, true)
                                selectedTab = BottomTab.Player
                                SessionPrefs.saveTab(ctx, TAB_PLAYER)
                            },
                            onOpenDjMode = {
                                selectedTab = BottomTab.Dj
                                SessionPrefs.saveTab(ctx, TAB_DJ)
                            },
                            onOpenTuner = {
                                selectedTab = BottomTab.Tuner
                                SessionPrefs.saveTab(ctx, TAB_TUNER)
                            },
                            onOpenProfile = {},
                            onOpenTutorial = {
                                selectedTab = BottomTab.More
                                SessionPrefs.saveTab(ctx, TAB_MORE)
                            },
                            onOpenSettings = {
                                selectedTab = BottomTab.More
                                SessionPrefs.saveTab(ctx, TAB_MORE)
                            }
                        )

                        // 🎵 LECTEUR
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
                            onTrackGainChange = {
                                currentPlayingUri?.let { uri ->
                                    TrackVolumePrefs.saveDb(ctx, uri, it)
                                }
                                currentTrackGainDb = it
                                applyGainToPlayer(it)
                            },
                            tempo = currentTrackTempo,
                            onTempoChange = {
                                currentTrackTempo = it
                                applyTempoToPlayer(it)
                                currentPlayingUri?.let { uri ->
                                    TrackTempoPrefs.saveTempo(ctx, uri, it)
                                }
                            },
                            onRequestShowPlaylist = {
                                selectedTab = BottomTab.QuickPlaylists
                            }
                        )

                        // ⭐ Playlists rapides
                        is BottomTab.QuickPlaylists -> QuickPlaylistsScreen(
                            modifier = Modifier.padding(innerPadding),
                            onPlaySong = { uri, playlistName, color ->
                                playWithCrossfade(uri, playlistName)
                                currentPlayingUri = uri
                                selectedQuickPlaylist = playlistName
                                SessionPrefs.saveQuickPlaylist(ctx, playlistName)
                                currentLyricsColor = color
                            },
                            refreshKey = refreshKey,
                            currentPlayingUri = currentPlayingUri,
                            selectedPlaylist = selectedQuickPlaylist,
                            onSelectedPlaylistChange = {
                                selectedQuickPlaylist = it
                                SessionPrefs.saveQuickPlaylist(ctx, it)
                            }
                        )

                        is BottomTab.Library ->
                            LibraryScreen(modifier = Modifier.padding(innerPadding))

                        is BottomTab.AllPlaylists -> {
                            val m = Modifier.padding(innerPadding)
                            if (openedPlaylist == null) {
                                AllPlaylistsScreen(
                                    modifier = m,
                                    onPlaylistClick = {
                                        openedPlaylist = it
                                        SessionPrefs.saveOpenedPlaylist(ctx, it)
                                    }
                                )
                            } else {
                                PlaylistDetailScreen(
                                    modifier = m,
                                    playlistName = openedPlaylist!!,
                                    onBack = {
                                        openedPlaylist = null
                                        SessionPrefs.saveOpenedPlaylist(ctx, null)
                                    },
                                    onPlaySong = { uri ->
                                        playWithCrossfade(uri, openedPlaylist)
                                    }
                                )
                            }
                        }

                        is BottomTab.More -> MoreScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = ctx,
                            onAfterImport = { refreshKey++ }
                        )

                        is BottomTab.Dj -> DjScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = ctx
                        )

                        // 🎸 ÉCRAN ACCORDEUR
                        is BottomTab.Tuner -> TunerScreen(
                            modifier = Modifier.padding(innerPadding),
                            onClose = {
                                selectedTab = BottomTab.Home
                                SessionPrefs.saveTab(ctx, TAB_HOME)
                            }
                        )
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

    // ⚠️ IMPORTANT : si on retrouve "tuner" au démarrage,
    // on renvoie Home pour éviter de relancer direct l'accordeur
    TAB_TUNER -> BottomTab.Home

    else -> BottomTab.Home
}