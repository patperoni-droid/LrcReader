package com.patrick.lrcreader

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
import com.patrick.lrcreader.core.AutoRestore
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.SessionPrefs
import com.patrick.lrcreader.core.TrackVolumePrefs
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
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // recharge auto
        AutoRestore.restoreIfNeeded(this)

        // session
        val initialTabKey = SessionPrefs.getTab(this)
        val initialQuickPlaylist = SessionPrefs.getQuickPlaylist(this)
        val initialOpenedPlaylist = SessionPrefs.getOpenedPlaylist(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val ctx = this@MainActivity
                val mediaPlayer = remember { MediaPlayer() }

                // effet audio (lié à la session du player)
                var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }

                // onglet courant
                var selectedTab by remember {
                    mutableStateOf(
                        initialTabKey?.let { tabFromKey(it) } ?: BottomTab.Player
                    )
                }

                // navigation
                var selectedQuickPlaylist by rememberSaveable {
                    mutableStateOf<String?>(initialQuickPlaylist)
                }
                var openedPlaylist by rememberSaveable {
                    mutableStateOf<String?>(initialOpenedPlaylist)
                }

                // lecture globale
                var currentPlayingUri by remember { mutableStateOf<String?>(null) }
                var isPlaying by remember { mutableStateOf(false) }
                var parsedLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
                var currentPlayToken by remember { mutableStateOf(0L) }

                // dB mémorisé du titre courant
                var currentTrackGainDb by remember { mutableStateOf(0) }

                // couleur lyrics
                var currentLyricsColor by remember { mutableStateOf(Color(0xFFE040FB)) }

                // refresh après import
                var refreshKey by remember { mutableStateOf(0) }

                // pour garder le repo vivant
                val repoVersion by PlaylistRepository.version

                // ----------- applique le dB au player en mode safe -------------
                fun applyGainToPlayer(gainDb: Int) {
                    try {
                        if (gainDb > 0) {
                            // boost → essayer par l’enhancer
                            val enh = loudnessEnhancer
                            if (enh != null) {
                                try {
                                    enh.setTargetGain(gainDb * 100)
                                    enh.enabled = true
                                    mediaPlayer.setVolume(1f, 1f)
                                    return
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    enh.enabled = false
                                }
                            }
                            // pas d’enhancer dispo → on met à 1
                            mediaPlayer.setVolume(1f, 1f)
                        } else {
                            // 0 ou négatif
                            loudnessEnhancer?.enabled = false
                            if (gainDb < 0) {
                                val factor = 10.0.pow(gainDb / 20.0).toFloat()
                                mediaPlayer.setVolume(factor, factor)
                            } else {
                                mediaPlayer.setVolume(1f, 1f)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        mediaPlayer.setVolume(1f, 1f)
                    }
                }

                // -------------------- lecture “normale” ------------------------
                val playWithCrossfade: (String, String?) -> Unit = remember {
                    { uriString, playlistName ->
                        // on note ce qu'on veut lire
                        currentPlayingUri = uriString
                        // on coupe le filler
                        FillerSoundManager.fadeOutAndStop(400)

                        val myToken = currentPlayToken + 1
                        currentPlayToken = myToken

                        // on essaye de lire le dB AVANT de lancer
                        val savedDb = try {
                            TrackVolumePrefs.getDb(ctx, uriString) ?: 0
                        } catch (e: Exception) {
                            e.printStackTrace()
                            0
                        }
                        currentTrackGainDb = savedDb

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

                                // ⚠️ toute cette partie était fragile → on la blinde
                                try {
                                    // on détruit l'ancien effet (ancienne session)
                                    try {
                                        loudnessEnhancer?.release()
                                    } catch (_: Exception) {}

                                    loudnessEnhancer = null

                                    // on crée un effet sur la session actuelle
                                    val sessionId = mediaPlayer.audioSessionId
                                    if (sessionId != 0) {
                                        loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                                            enabled = false
                                        }
                                    }

                                    // on applique le dB qu'on avait lu
                                    applyGainToPlayer(currentTrackGainDb)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    // si l'effet plante on continue quand même la lecture
                                    loudnessEnhancer = null
                                    mediaPlayer.setVolume(1f, 1f)
                                }
                            },
                            onError = {
                                isPlaying = false
                                // on relance le filler si configuré
                                FillerSoundManager.startIfConfigured(ctx)
                            },
                            onNaturalEnd = {
                                isPlaying = false
                                FillerSoundManager.startIfConfigured(ctx)
                            }
                        )

                        // on va sur le lecteur
                        selectedTab = BottomTab.Player
                        SessionPrefs.saveTab(ctx, tabKeyOf(BottomTab.Player))
                    }
                }

                // libérer le player + l’effet
                DisposableEffect(Unit) {
                    onDispose {
                        try {
                            loudnessEnhancer?.release()
                        } catch (_: Exception) {}
                        mediaPlayer.release()
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
                                val uri = currentPlayingUri
                                if (uri != null) {
                                    try {
                                        TrackVolumePrefs.saveDb(ctx, uri, newDb)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
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