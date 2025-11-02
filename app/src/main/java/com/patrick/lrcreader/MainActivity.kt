package com.patrick.lrcreader

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

                // détail d’une playlist (onglet “Toutes”)
                var openedPlaylist by remember { mutableStateOf<String?>(null) }

                // token de lecture courant
                var currentPlayToken by remember { mutableStateOf(0L) }

                // ✅ lire directement la valeur Int
                val repoVersion by PlaylistRepository.version

                // callback unique
                val playWithCrossfade: (String, String?) -> Unit = remember {
                    { uriString, playlistName ->
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
                                parsedLines = if (!text.isNullOrBlank()) {
                                    parseLrc(text)
                                } else {
                                    emptyList()
                                }
                            },
                            onStart = { isPlaying = true },
                            onError = { isPlaying = false }
                        )

                        // on bascule sur l’onglet “Lecteur”
                        selectedTab = BottomTab.Player
                    }
                }

                DisposableEffect(Unit) {
                    onDispose { mediaPlayer.release() }
                }

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        BottomTabsBar(
                            selected = selectedTab,
                            onSelected = { tab ->
                                // si on clique sur “Toutes” on ferme le détail
                                if (tab is BottomTab.AllPlaylists) {
                                    openedPlaylist = null
                                }
                                selectedTab = tab
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
                        )

                        // 🟠 playlists “live”
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

                        // 🆕 onglet “Plus…”
                        is BottomTab.More -> MoreScreen(
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

// -------- lecture avec fondu + marquage “joué” après 10 s --------
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

            val lrcText = readUsltFromUri(context, uri)
            onLyricsLoaded(lrcText)

            mediaPlayer.start()
            onStart()

            fadeVolume(mediaPlayer, 0f, 1f, fadeDurationMs)

            // ⏱️ après 10 s → marque joué
            if (playlistName != null) {
                val thisToken = playToken
                val thisSong = uriString
                CoroutineScope(Dispatchers.Main).launch {
                    delay(10_000)
                    if (getCurrentToken() == thisToken && mediaPlayer.isPlaying) {
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

// 👇 petit écran “Plus…” (provisoire)
@Composable
fun MoreScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text(text = "Plus / paramètres", color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        Text(text = "Préférences", color = Color(0xFFBBBBBB), fontSize = 16.sp)
        Text(text = "Aide", color = Color(0xFFBBBBBB), fontSize = 16.sp)
        Text(text = "À propos", color = Color(0xFFBBBBBB), fontSize = 16.sp)
        Text(text = "Export / Import", color = Color(0xFFBBBBBB), fontSize = 16.sp)
    }
}