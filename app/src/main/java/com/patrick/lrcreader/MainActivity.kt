package com.patrick.lrcreader

import android.content.Context
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
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.parseLrc
import com.patrick.lrcreader.core.readUsltFromUri
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

                // pour l’onglet "Toutes" (détail d’une playlist)
                var openedPlaylist by remember { mutableStateOf<String?>(null) }

                // 👇 token de lecture courant (augmente à chaque nouveau titre lancé)
                var currentPlayToken by remember { mutableStateOf(0L) }

                // 👇 callback unique que tous les écrans vont utiliser
                val playWithCrossfade: (String, String?) -> Unit = remember {
                    { uriString, playlistName ->
                        // on fabrique un token unique pour CETTE lecture
                        val myToken = currentPlayToken + 1
                        currentPlayToken = myToken

                        crossfadePlay(
                            context = ctx,
                            mediaPlayer = mediaPlayer,
                            uriString = uriString,
                            playlistName = playlistName,          // peut être null
                            playToken = myToken,
                            getCurrentToken = { currentPlayToken },
                            onLyricsLoaded = { text ->
                                parsedLines = if (!text.isNullOrBlank()) {
                                    parseLrc(text)
                                } else {
                                    emptyList()
                                }
                            },
                            onStart = { isPlaying = true },
                            onError = { isPlaying = false }
                        )

                        // on affiche le player après lancement
                        selectedTab = BottomTab.Player
                    }
                }

                // libération du player
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
                            // 👉 depuis ici : pas de playlistName → null
                            QuickPlaylistsScreen(
                                modifier = Modifier.padding(innerPadding),
                                onPlaySong = { uriString ->
                                    playWithCrossfade(uriString, null)
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
                                        // 👉 ici on sait de quelle playlist ça vient
                                        playWithCrossfade(uriString, openedPlaylist)
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

// -------- lecture avec fondu + marquage après 10 s --------
private fun crossfadePlay(
    context: Context,
    mediaPlayer: MediaPlayer,
    uriString: String,
    playlistName: String?,                 // peut être null
    playToken: Long,
    getCurrentToken: () -> Long,
    onLyricsLoaded: (String?) -> Unit,
    onStart: () -> Unit,
    onError: () -> Unit,
    fadeDurationMs: Long = 500
) {
    CoroutineScope(Dispatchers.Main).launch {
        // 1) on baisse l’ancien s’il y en a un
        if (mediaPlayer.isPlaying) {
            fadeVolume(mediaPlayer, 1f, 0f, fadeDurationMs)
        } else {
            mediaPlayer.setVolume(0f, 0f)
        }

        try {
            val uri = Uri.parse(uriString)
            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, uri)
            mediaPlayer.prepare()

            // 2) paroles
            val lrcText = readUsltFromUri(context, uri)
            onLyricsLoaded(lrcText)

            // 3) on démarre
            mediaPlayer.start()
            onStart()

            // 4) fade-in
            fadeVolume(mediaPlayer, 0f, 1f, fadeDurationMs)

            // 5) marquage “joué” après 10s UNIQUEMENT si on vient d’une playlist
            if (playlistName != null) {
                val thisSong = uriString
                CoroutineScope(Dispatchers.Main).launch {
                    delay(10_000)
                    // on vérifie qu’entre-temps on n’a pas lancé un autre morceau
                    if (getCurrentToken() == playToken && mediaPlayer.isPlaying) {
                        PlaylistRepository.markSongPlayed(playlistName, thisSong)
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            onError()
        }
    }
}

private suspend fun fadeVolume(
    player: MediaPlayer,
    from: Float,
    to: Float,
    durationMs: Long
) {
    val steps = 20
    val stepTime = durationMs / steps
    val delta = (to - from) / steps
    for (i in 0..steps) {
        val v = (from + delta * i).coerceIn(0f, 1f)
        player.setVolume(v, v)
        delay(stepTime)
    }
    player.setVolume(to, to)
}