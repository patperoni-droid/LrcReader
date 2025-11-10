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
import com.patrick.lrcreader.core.AutoRestore
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.SessionPrefs
import com.patrick.lrcreader.core.crossfadePlay
import com.patrick.lrcreader.core.parseLrc
import com.patrick.lrcreader.ui.AllPlaylistsScreen
import com.patrick.lrcreader.ui.BottomTab
import com.patrick.lrcreader.ui.BottomTabsBar
import com.patrick.lrcreader.ui.DjScreen
import com.patrick.lrcreader.ui.LibraryScreen
import com.patrick.lrcreader.ui.MoreScreen
import com.patrick.lrcreader.ui.PlayerScreen
import com.patrick.lrcreader.ui.PlaylistDetailScreen
import com.patrick.lrcreader.ui.QuickPlaylistsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // on essaye de recharger automatiquement la dernière sauvegarde
        AutoRestore.restoreIfNeeded(this)

        // on relit la session
        val initialTabKey = SessionPrefs.getTab(this)
        val initialQuickPlaylist = SessionPrefs.getQuickPlaylist(this)
        val initialOpenedPlaylist = SessionPrefs.getOpenedPlaylist(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val ctx = this@MainActivity
                val mediaPlayer = remember { MediaPlayer() }

                // onglet courant
                var selectedTab by remember {
                    mutableStateOf(
                        initialTabKey?.let { tabFromKey(it) } ?: BottomTab.Player
                    )
                }

                // navigation
                var selectedQuickPlaylist by rememberSaveable { mutableStateOf<String?>(initialQuickPlaylist) }
                var openedPlaylist by rememberSaveable { mutableStateOf<String?>(initialOpenedPlaylist) }

                // lecture “globale”
                var currentPlayingUri by remember { mutableStateOf<String?>(null) }
                var isPlaying by remember { mutableStateOf(false) }
                var parsedLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
                var currentPlayToken by remember { mutableStateOf(0L) }

                // DJ – ce qu’on affiche dans la timeline
                var djCurrentUri by remember { mutableStateOf<String?>(null) }
                var djProgress by remember { mutableStateOf(0f) }

                // couleur “lyrics”
                var currentLyricsColor by remember { mutableStateOf(Color(0xFFE040FB)) }

                // refresh après import
                var refreshKey by remember { mutableStateOf(0) }

                // tient le repo en vie
                val repoVersion by PlaylistRepository.version

                // lecture classique (vient des playlists, etc.) → on envoie vers Player
                val playWithCrossfade: (String, String?) -> Unit = remember {
                    { uriString, playlistName ->
                        currentPlayingUri = uriString
                        // coupe le fond sonore
                        FillerSoundManager.fadeOutAndStop(400)

                        val myToken = currentPlayToken + 1
                        currentPlayToken = myToken

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
                            onStart = { isPlaying = true },
                            onError = { isPlaying = false },
                            onNaturalEnd = {
                                isPlaying = false
                                FillerSoundManager.startIfConfigured(ctx)
                            }
                        )

                        // ici → on va sur le lecteur normal
                        selectedTab = BottomTab.Player
                        SessionPrefs.saveTab(ctx, tabKeyOf(BottomTab.Player))
                    }
                }

                // lecture spéciale DJ : on reste sur l’onglet DJ
                val playFromDj: (String) -> Unit = remember {
                    { uriString ->
                        currentPlayingUri = uriString
                        djCurrentUri = uriString
                        djProgress = 0f
                        FillerSoundManager.fadeOutAndStop(400)

                        val myToken = currentPlayToken + 1
                        currentPlayToken = myToken

                        crossfadePlay(
                            context = ctx,
                            mediaPlayer = mediaPlayer,
                            uriString = uriString,
                            playlistName = null,
                            playToken = myToken,
                            getCurrentToken = { currentPlayToken },
                            onLyricsLoaded = { /* pas de paroles en DJ */ },
                            onStart = { isPlaying = true },
                            onError = {
                                isPlaying = false
                                djCurrentUri = null
                                djProgress = 0f
                            },
                            onNaturalEnd = {
                                isPlaying = false
                                djCurrentUri = null
                                djProgress = 0f
                                FillerSoundManager.startIfConfigured(ctx)
                            }
                        )

                        // on force à rester sur DJ
                        selectedTab = BottomTab.Dj
                        SessionPrefs.saveTab(ctx, tabKeyOf(BottomTab.Dj))
                    }
                }

                // petite boucle pour faire avancer la timeline DJ
                LaunchedEffect(djCurrentUri) {
                    if (djCurrentUri != null) {
                        djProgress = 0f
                        while (isActive && djCurrentUri != null) {
                            delay(200)
                            djProgress = (djProgress + 0.01f).coerceAtMost(1f)
                        }
                    } else {
                        djProgress = 0f
                    }
                }

                // libère le MP
                DisposableEffect(Unit) {
                    onDispose { mediaPlayer.release() }
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
                            highlightColor = currentLyricsColor
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

                        is BottomTab.Library -> LibraryScreen(
                            modifier = Modifier.padding(innerPadding)
                        )

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
                            onPlayTrack = { uriString ->
                                playFromDj(uriString)
                            },
                            onStop = {
                                // on coupe proprement ce qui joue
                                mediaPlayer.pause()
                                try {
                                    mediaPlayer.seekTo(0)
                                } catch (_: Exception) {}
                                isPlaying = false
                                djCurrentUri = null
                                djProgress = 0f
                                // on peut relancer le fond sonore
                                FillerSoundManager.startIfConfigured(ctx)
                            },
                            currentUri = djCurrentUri,
                            progress = djProgress
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

private fun tabKeyOf(tab: BottomTab): String =
    when (tab) {
        is BottomTab.Player -> TAB_PLAYER
        is BottomTab.QuickPlaylists -> TAB_QUICK
        is BottomTab.Library -> TAB_LIBRARY
        is BottomTab.AllPlaylists -> TAB_ALL
        is BottomTab.More -> TAB_MORE
        is BottomTab.Dj -> TAB_DJ
    }

private fun tabFromKey(key: String): BottomTab =
    when (key) {
        TAB_PLAYER -> BottomTab.Player
        TAB_QUICK -> BottomTab.QuickPlaylists
        TAB_LIBRARY -> BottomTab.Library
        TAB_ALL -> BottomTab.AllPlaylists
        TAB_MORE -> BottomTab.More
        TAB_DJ -> BottomTab.Dj
        else -> BottomTab.Player
    }