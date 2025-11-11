package com.patrick.lrcreader

import android.media.MediaPlayer
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

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val ctx = this@MainActivity
                val mediaPlayer = remember { MediaPlayer() }

                var selectedTab by remember {
                    mutableStateOf(initialTabKey?.let { tabFromKey(it) } ?: BottomTab.Player)
                }

                var selectedQuickPlaylist by rememberSaveable { mutableStateOf<String?>(initialQuickPlaylist) }
                var openedPlaylist by rememberSaveable { mutableStateOf<String?>(initialOpenedPlaylist) }

                var currentPlayingUri by remember { mutableStateOf<String?>(null) }
                var isPlaying by remember { mutableStateOf(false) }
                var parsedLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
                var currentPlayToken by remember { mutableStateOf(0L) }
                var currentTrackGainDb by remember { mutableStateOf(0) }

                var currentLyricsColor by remember { mutableStateOf(Color(0xFFE040FB)) }
                var refreshKey by remember { mutableStateOf(0) }
                val repoVersion by PlaylistRepository.version

                // Applique le dB au player (sans enhancer)
                fun applyGainToPlayer(gainDb: Int) {
                    try {
                        val clamped = gainDb.coerceIn(-12, 0)
                        if (clamped < 0) {
                            val factor = 10.0.pow(clamped / 20.0).toFloat()
                            mediaPlayer.setVolume(factor, factor)
                        } else {
                            mediaPlayer.setVolume(1f, 1f)
                        }
                    } catch (_: Exception) {
                        runCatching { mediaPlayer.setVolume(1f, 1f) }
                    }
                }

                // Lecture stable avec blindage
                val playWithCrossfade: (String, String?) -> Unit = remember {
                    { uriString, playlistName ->
                        currentPlayingUri = uriString
                        runCatching { FillerSoundManager.fadeOutAndStop(400) }

                        val myToken = currentPlayToken + 1
                        currentPlayToken = myToken

                        val savedDb = runCatching { TrackVolumePrefs.getDb(ctx, uriString) ?: 0 }
                            .getOrElse { 0 }
                        currentTrackGainDb = savedDb

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
                    onDispose { runCatching { mediaPlayer.release() } }
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

                        is BottomTab.Library -> LibraryScreen(modifier = Modifier.padding(innerPadding))

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