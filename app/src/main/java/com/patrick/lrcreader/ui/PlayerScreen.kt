package com.patrick.lrcreader.ui

import android.media.MediaPlayer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.LrcLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    mediaPlayer: MediaPlayer,
    isPlaying: Boolean,
    onIsPlayingChange: (Boolean) -> Unit,
    parsedLines: List<LrcLine>,
    onParsedLinesChange: (List<LrcLine>) -> Unit, // on le garde
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var currentLineIndex by remember { mutableStateOf(0) }

    // défilement auto
    LaunchedEffect(isPlaying, parsedLines) {
        while (isPlaying && parsedLines.isNotEmpty()) {
            val pos = mediaPlayer.currentPosition
            val index = parsedLines.indexOfLast { it.timeMs <= pos }
            if (index != -1 && index != currentLineIndex) {
                currentLineIndex = index
                // 👉 on essaye d’afficher aussi la ligne d’avant
                val target = if (index > 0) index - 1 else 0
                scope.launch { listState.animateScrollToItem(target) }
            }
            delay(120)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

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
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 40.dp, bottom = 16.dp) // 👈 espace en haut
        ) {
            if (parsedLines.isEmpty()) {
                item {
                    Text(
                        "Aucune parole LRC trouvée dans ce titre",
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 40.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                itemsIndexed(parsedLines) { index, line ->
                    val isActive = index == currentLineIndex
                    Text(
                        text = line.text,
                        color = if (isActive) Color.White else Color(0xFFB0B0B0),
                        fontSize = if (isActive) 26.sp else 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                try {
                                    mediaPlayer.seekTo(line.timeMs.toInt())
                                    currentLineIndex = index
                                    // même logique : on remonte un peu
                                    val target = if (index > 0) index - 1 else 0
                                    scope.launch { listState.scrollToItem(target) }
                                } catch (_: Exception) {
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
                scope.launch { listState.scrollToItem(0) }
            },
            onNext = {
                mediaPlayer.seekTo(mediaPlayer.duration)
                mediaPlayer.pause()
                onIsPlayingChange(false)
            }
        )
    }
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