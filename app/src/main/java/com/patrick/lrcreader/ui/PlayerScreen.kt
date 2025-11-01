package com.patrick.lrcreader.ui

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.parseLrc
import com.patrick.lrcreader.core.readUsltFromUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    mediaPlayer: MediaPlayer,
    isPlaying: Boolean,
    onIsPlayingChange: (Boolean) -> Unit,
    parsedLines: List<LrcLine>,
    onParsedLinesChange: (List<LrcLine>) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var currentLineIndex by remember { mutableStateOf(0) }

    // suivi auto des paroles
    LaunchedEffect(isPlaying, parsedLines) {
        while (isPlaying && parsedLines.isNotEmpty()) {
            val pos = mediaPlayer.currentPosition
            val index = parsedLines.indexOfLast { it.timeMs <= pos }
            if (index != -1 && index != currentLineIndex) {
                currentLineIndex = index
                coroutineScope.launch { listState.animateScrollToItem(index) }
            }
            delay(120)
        }
    }

    // choisir un MP3
    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                // on passe en coroutine pour pouvoir faire le fade
                coroutineScope.launch {
                    // fade-out de l’ancien si ça joue
                    if (mediaPlayer.isPlaying) {
                        fadeVolume(mediaPlayer, from = 1f, to = 0f, durationMs = 500)
                    } else {
                        // on part de 0 pour le prochain fade-in
                        mediaPlayer.setVolume(0f, 0f)
                    }

                    try {
                        mediaPlayer.reset()
                        mediaPlayer.setDataSource(context, uri)
                        mediaPlayer.prepare()

                        // paroles
                        val lrcText = readUsltFromUri(context, uri)
                        val lines = if (!lrcText.isNullOrBlank()) parseLrc(lrcText) else emptyList()
                        onParsedLinesChange(lines)

                        currentLineIndex = 0
                        listState.scrollToItem(0)

                        mediaPlayer.start()
                        onIsPlayingChange(true)

                        // fade-in du nouveau morceau
                        fadeVolume(mediaPlayer, from = 0f, to = 1f, durationMs = 500)

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Button(
            onClick = {
                pickAudioLauncher.launch(
                    arrayOf(
                        "audio/*",
                        "audio/mpeg",
                        "audio/mp3",
                        "application/octet-stream"
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Choisir un MP3", color = Color.White)
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Paroles synchronisées",
            color = Color.LightGray,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (parsedLines.isEmpty()) {
                item {
                    Text(
                        "Aucune parole LRC trouvée dans ce MP3",
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 40.dp)
                    )
                }
            } else {
                itemsIndexed(parsedLines) { index, line ->
                    Text(
                        text = line.text,
                        color = if (index == currentLineIndex) Color.White else Color(0xFFB0B0B0),
                        fontSize = if (index == currentLineIndex) 26.sp else 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                try {
                                    mediaPlayer.seekTo(line.timeMs.toInt())
                                    currentLineIndex = index
                                    coroutineScope.launch { listState.scrollToItem(index) }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                    )
                }
            }
        }

        PlayerControls(
            isPlaying = isPlaying,
            onPlayPause = {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                    onIsPlayingChange(false)
                } else {
                    mediaPlayer.start()
                    onIsPlayingChange(true)
                }
            },
            onPrev = {
                mediaPlayer.seekTo(0)
                currentLineIndex = 0
                coroutineScope.launch { listState.scrollToItem(0) }
            },
            onNext = {
                mediaPlayer.seekTo(mediaPlayer.duration)
                mediaPlayer.pause()
                onIsPlayingChange(false)
            }
        )
    }
}

/**
 * Petit fondu de volume très simple.
 * On reste en linéaire, 20 steps.
 */
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
    // pour être sûr de finir à la bonne valeur
    player.setVolume(to, to)
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Précédent",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }

        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Suivant",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}