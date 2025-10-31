package com.patrick.lrcreader

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.parseLrc
import com.patrick.lrcreader.core.readUsltFromUri
import com.patrick.lrcreader.ui.*

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

                // pour l’onglet "Toutes" (détail d’une playlist)
                var openedPlaylist by remember { mutableStateOf<String?>(null) }

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
                                // si tu veux: quand on revient sur "Toutes", on ne reset pas
                            }
                        )
                    }
                ) { innerPadding ->

                    when (selectedTab) {

                        is BottomTab.Player -> {
                            PlayerScreen(
                                modifier = Modifier.padding(innerPadding),
                                mediaPlayer = mediaPlayer,
                                isPlaying = isPlaying,
                                onIsPlayingChange = { isPlaying = it },
                                parsedLines = parsedLines,
                                onParsedLinesChange = { parsedLines = it },
                            )
                        }

                        is BottomTab.QuickPlaylists -> {
                            // 🔴 ICI : notre nouvel écran
                            QuickPlaylistsScreen(
                                modifier = Modifier.padding(innerPadding),
                                onPlaySong = { uriString ->
                                    try {
                                        val uri = Uri.parse(uriString)
                                        mediaPlayer.reset()
                                        mediaPlayer.setDataSource(ctx, uri)
                                        mediaPlayer.prepare()
                                        mediaPlayer.start()
                                        isPlaying = true

                                        // on recharge les paroles si le MP3 en a
                                        val lrcText = readUsltFromUri(ctx, uri)
                                        parsedLines = if (!lrcText.isNullOrBlank()) {
                                            parseLrc(lrcText)
                                        } else {
                                            emptyList()
                                        }

                                        // et on bascule vers le lecteur
                                        selectedTab = BottomTab.Player

                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            )
                        }

                        is BottomTab.Library -> {
                            LibraryScreen(
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

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
                                        try {
                                            val uri = Uri.parse(uriString)
                                            mediaPlayer.reset()
                                            mediaPlayer.setDataSource(ctx, uri)
                                            mediaPlayer.prepare()
                                            mediaPlayer.start()
                                            isPlaying = true

                                            val lrcText = readUsltFromUri(ctx, uri)
                                            parsedLines = if (!lrcText.isNullOrBlank()) {
                                                parseLrc(lrcText)
                                            } else {
                                                emptyList()
                                            }

                                            selectedTab = BottomTab.Player
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}