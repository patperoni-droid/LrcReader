package com.patrick.lrcreader.ui

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
    onParsedLinesChange: (List<LrcLine>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var currentLineIndex by remember { mutableStateOf(0) }

    // hauteur réelle de la zone paroles
    var lyricsHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    // hauteur “moyenne” d’une ligne (pour centrer un peu mieux)
    val approxLineHeightDp = 40.dp
    val approxLineHeightPx = with(density) { approxLineHeightDp.toPx() }

    // padding haut dynamique : moitié de la zone - moitié d’une ligne
    val topPaddingDp by remember(lyricsHeightPx) {
        mutableStateOf(
            if (lyricsHeightPx == 0) 0.dp
            else with(density) {
                val px = (lyricsHeightPx / 2f - approxLineHeightPx / 2f).coerceAtLeast(0f)
                px.toDp()
            }
        )
    }

    // suivi auto
    LaunchedEffect(isPlaying, parsedLines) {
        while (isPlaying && parsedLines.isNotEmpty()) {
            val pos = mediaPlayer.currentPosition
            val index = parsedLines.indexOfLast { it.timeMs <= pos }
            if (index != -1 && index != currentLineIndex) {
                currentLineIndex = index
                // on défile MAIS on garde le padding → la ligne reste au milieu
                scope.launch {
                    listState.animateScrollToItem(index)
                }
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

        // zone des paroles
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    lyricsHeightPx = coords.size.height
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    top = topPaddingDp,
                    bottom = 80.dp
                )
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
                    items(parsedLines.size) { index ->
                        val line = parsedLines[index]

                        // 3 niveaux de lumière
                        val isCurrent = index == currentLineIndex
                        val isBefore = index == currentLineIndex - 1
                        val isAfter = index == currentLineIndex + 1

                        val color = when {
                            isCurrent -> Color.White
                            isBefore || isAfter -> Color(0xFFDDDDDD)
                            else -> Color(0xFF666666)
                        }
                        val size = when {
                            isCurrent -> 26.sp
                            isBefore || isAfter -> 22.sp
                            else -> 18.sp
                        }

                        Text(
                            text = line.text,
                            color = color,
                            fontSize = size,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable {
                                    try {
                                        mediaPlayer.seekTo(line.timeMs.toInt())
                                        currentLineIndex = index
                                        scope.launch {
                                            listState.animateScrollToItem(index)
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                        )
                    }
                }
            }

            // si tu veux voir le centre pour débug :
            // Box(
            //     modifier = Modifier
            //         .fillMaxWidth()
            //         .height(1.dp)
            //         .align(Alignment.Center)
            //         .background(Color(0x33FFFFFF))
            // )
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
                scope.launch {
                    listState.scrollToItem(0)   // ← avec le padding, ça restera centré
                }
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