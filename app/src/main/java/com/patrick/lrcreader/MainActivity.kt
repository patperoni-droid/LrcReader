package com.patrick.lrcreader

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.patrick.lrcreader.core.*
import com.patrick.lrcreader.ui.*
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {

                val ctx = this
                val mediaPlayer = remember { MediaPlayer() }

                var isPlaying by remember { mutableStateOf(false) }
                var parsedLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
                var selectedTab by remember { mutableStateOf<BottomTab>(BottomTab.Player) }
                var openedPlaylist by rememberSaveable { mutableStateOf<String?>(null) }

                var currentPlayToken by remember { mutableStateOf(0L) }
                val repoVersion by PlaylistRepository.version

                // Fonction principale pour lancer un titre
                val playWithCrossfade: (String, String?) -> Unit = remember {
                    { uriString, playlistName ->
                        // 👉 On coupe le son de remplissage avant de lire le vrai titre
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
                                parsedLines = if (!text.isNullOrBlank()) parseLrc(text) else emptyList()
                            },
                            onStart = { isPlaying = true },
                            onError = { isPlaying = false },
                            onNaturalEnd = {
                                // morceau terminé naturellement
                                isPlaying = false
                                // on relance le son de remplissage si l’utilisateur en a choisi un
                                FillerSoundManager.startIfConfigured(ctx)
                            }
                        )

                        selectedTab = BottomTab.Player
                    }
                }

                // Nettoyage du MediaPlayer à la fermeture
                DisposableEffect(Unit) {
                    onDispose {
                        mediaPlayer.release()
                    }
                }

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        BottomTabsBar(
                            selected = selectedTab,
                            onSelected = { tab: BottomTab -> selectedTab = tab }
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
                        )

                        is BottomTab.QuickPlaylists -> QuickPlaylistsScreen(
                            modifier = Modifier.padding(innerPadding),
                            onPlaySong = { uri, playlistName ->
                                playWithCrossfade(uri, playlistName)
                            },
                            refreshKey = repoVersion
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
                                    }
                                )
                            } else {
                                PlaylistDetailScreen(
                                    modifier = m,
                                    playlistName = openedPlaylist!!,
                                    onBack = { openedPlaylist = null },
                                    onPlaySong = { uriString ->
                                        playWithCrossfade(uriString, openedPlaylist)
                                    }
                                )
                            }
                        }

                        is BottomTab.More -> MoreScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = ctx,
                        )
                    }
                } // 👈 celle-ci ferme le Scaffold
            }
        }
    }
}