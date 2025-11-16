package com.patrick.lrcreader.exo
import com.patrick.lrcreader.core.dj.DjEngine
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
import com.patrick.lrcreader.ui.*
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AutoRestore.restoreIfNeeded(this)

        val initialTabKey = SessionPrefs.getTab(this)
        val initialQuickPlaylist = SessionPrefs.getQuickPlaylist(this)
        val initialOpenedPlaylist = SessionPrefs.getOpenedPlaylist(this)

        // 🔴 IMPORTANT : initialiser le moteur DJ global
        DjEngine.init(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val ctx = this@MainActivity
                val mediaPlayer = remember { MediaPlayer() }

                // 👉 Effet pour booster le volume quand dB > 0
                val loudnessEnhancer = remember {
                    LoudnessEnhancer(mediaPlayer.audioSessionId).apply {
                        enabled = true
                    }
                }

                var selectedTab by remember {
                    mutableStateOf(initialTabKey?.let { tabFromKey(it) } ?: BottomTab.Player)
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

                // 🔥 Tempo PAR MORCEAU (1f = normal)
                var currentTrackTempo by remember { mutableStateOf(1f) }

                // Applique le dB au player
                fun applyGainToPlayer(db: Int) {
                    try {
                        val clamped = db.coerceIn(-12, 12)

                        if (clamped <= 0) {
                            // 💡 dB négatifs : on atténue proprement avec setVolume
                            val linear = 10f.pow(clamped / 20f)
                            mediaPlayer.setVolume(linear, linear)
                            loudnessEnhancer.setTargetGain(0) // pas de boost
                        } else {
                            // 💡 dB positifs : player à fond, boost via LoudnessEnhancer
                            mediaPlayer.setVolume(1f, 1f)
                            loudnessEnhancer.setTargetGain(clamped * 100) // millibels
                        }
                    } catch (_: Exception) {
                        // on évite de crasher si le player n'est pas prêt
                    }
                }

                // 🔥 Applique le tempo au MediaPlayer (sans changer la tonalité)
                fun applyTempoToPlayer(speed: Float) {
                    try {
                        val params = mediaPlayer.playbackParams
                            .setSpeed(speed)
                            .setPitch(1.0f) // tonalité fixe
                        mediaPlayer.playbackParams = params
                    } catch (_: Exception) {
                        // si le player n'est pas encore prêt, on ignore
                    }
                }

                // Lecture stable avec blindage
                val playWithCrossfade: (String, String?) -> Unit = remember {
                    { uriString, playlistName ->
                        currentPlayingUri = uriString
                        runCatching { FillerSoundManager.fadeOutAndStop(400) }

                        val myToken = currentPlayToken + 1
                        currentPlayToken = myToken

                        // dB par morceau
                        val savedDb = runCatching {
                            TrackVolumePrefs.getDb(ctx, uriString) ?: 0
                        }.getOrElse { 0 }
                        currentTrackGainDb = savedDb

                        // 🔥 Tempo par morceau (par défaut 1.0f)
                        val savedTempo = runCatching {
                            TrackTempoPrefs.getTempo(ctx, uriString) ?: 1f
                        }.getOrElse { 1f }
                        currentTrackTempo = savedTempo

                        val result = runCatching {
                            crossfadePlay(
                                context = ctx,
                                mediaPlayer = mediaPlayer,
                                uriString = uriString,
                                playlistName = playlistName,
                                playToken = myToken,
                                getCurrentToken = { currentPlayToken },
                                onLyricsLoaded = { text ->
                                    parsedLines =
                                        if (!text.isNullOrBlank()) parseLrc(text) else emptyList()
                                },
                                onStart = {
                                    isPlaying = true
                                    applyGainToPlayer(currentTrackGainDb)
                                    applyTempoToPlayer(currentTrackTempo)
                                },
                                onError = {
                                    isPlaying = false
                                    runCatching { mediaPlayer.reset() }
                                },
                                onNaturalEnd = {
                                    isPlaying = false
                                    runCatching { FillerSoundManager.startIfConfigured(ctx) }
                                }
                            )
                        }

                        if (result.isFailure) {
                            runCatching { mediaPlayer.reset() }
                            isPlaying = false
                        }

                        selectedTab = BottomTab.Player
                        SessionPrefs.saveTab(ctx, tabKeyOf(BottomTab.Player))
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        runCatching { loudnessEnhancer.release() }
                        runCatching { mediaPlayer.release() }
                    }
                }

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        BottomTabsBar(
                            selected = selectedTab,
                            onSelected = { tab ->
                                selectedTab = tab
                                SessionPrefs.saveTab(ctx, tabKeyOf(tab))
                            }
                        )
                    }
                ) { innerPadding ->
                    when (selectedTab) {
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
                            onTrackGainChange = { newDb ->
                                currentPlayingUri?.let {
                                    runCatching { TrackVolumePrefs.saveDb(ctx, it, newDb) }
                                }
                                currentTrackGainDb = newDb
                                applyGainToPlayer(newDb)
                            },
                            // 🔥 Tempo par morceau : valeur + callback
                            tempo = currentTrackTempo,
                            onTempoChange = { newTempo ->
                                currentTrackTempo = newTempo
                                applyTempoToPlayer(newTempo)
                                // sauvegarde par morceau
                                currentPlayingUri?.let { uri ->
                                    runCatching { TrackTempoPrefs.saveTempo(ctx, uri, newTempo) }
                                }
                            },
                            onRequestShowPlaylist = {
                                selectedTab = BottomTab.QuickPlaylists
                            }
                        )

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
                            onSelectedPlaylistChange = { newPl ->
                                selectedQuickPlaylist = newPl
                                SessionPrefs.saveQuickPlaylist(ctx, newPl)
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
                            } else {
                                PlaylistDetailScreen(
                                    modifier = m,
                                    playlistName = openedPlaylist!!,
                                    onBack = {
                                        openedPlaylist = null
                                        SessionPrefs.saveOpenedPlaylist(ctx, null)
                                    },
                                    onPlaySong = { uriString ->
                                        playWithCrossfade(uriString, openedPlaylist)
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
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Conversion BottomTab <-> String                                           */
/* -------------------------------------------------------------------------- */

private const val TAB_PLAYER = "player"
private const val TAB_QUICK = "quick"
private const val TAB_LIBRARY = "library"
private const val TAB_ALL = "all"
private const val TAB_MORE = "more"
private const val TAB_DJ = "dj"

private fun tabKeyOf(tab: BottomTab): String = when (tab) {
    is BottomTab.Player -> TAB_PLAYER
    is BottomTab.QuickPlaylists -> TAB_QUICK
    is BottomTab.Library -> TAB_LIBRARY
    is BottomTab.AllPlaylists -> TAB_ALL
    is BottomTab.More -> TAB_MORE
    is BottomTab.Dj -> TAB_DJ
}

private fun tabFromKey(key: String): BottomTab = when (key) {
    TAB_PLAYER -> BottomTab.Player
    TAB_QUICK -> BottomTab.QuickPlaylists
    TAB_LIBRARY -> BottomTab.Library
    TAB_ALL -> BottomTab.AllPlaylists
    TAB_MORE -> BottomTab.More
    TAB_DJ -> BottomTab.Dj
    else -> BottomTab.Player
}