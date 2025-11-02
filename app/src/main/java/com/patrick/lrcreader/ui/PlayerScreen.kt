package com.patrick.lrcreader.ui

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.LrcLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    mediaPlayer: MediaPlayer,
    isPlaying: Boolean,
    onIsPlayingChange: (Boolean) -> Unit,
    parsedLines: List<LrcLine>,
    onParsedLinesChange: (List<LrcLine>) -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var lyricsBoxHeightPx by remember { mutableStateOf(0) }
    var currentLrcIndex by remember { mutableStateOf(0) }
    var userScrolling by remember { mutableStateOf(false) }

    val lineHeightDp = 80.dp
    val lineHeightPx = with(density) { lineHeightDp.toPx() }
    val baseTopSpacerPx = remember(lyricsBoxHeightPx) { lyricsBoxHeightPx }

    // 1️⃣ Suivi de la lecture pour trouver la ligne en cours
    LaunchedEffect(isPlaying, parsedLines) {
        if (parsedLines.isEmpty()) return@LaunchedEffect
        while (isPlaying) {
            val posMs = try {
                mediaPlayer.currentPosition.toLong()
            } catch (_: Exception) {
                0L
            }
            val bestIndex = parsedLines.indices.minByOrNull {
                abs(parsedLines[it].timeMs - posMs)
            } ?: 0
            currentLrcIndex = bestIndex
            delay(120)
        }
    }

    // 2️⃣ Détection du scroll utilisateur
    LaunchedEffect(scrollState) {
        while (true) {
            userScrolling = scrollState.isScrollInProgress
            delay(80)
        }
    }

    // Fonction qui centre la ligne orange
    fun centerCurrentLine() {
        if (lyricsBoxHeightPx == 0) return
        val centerPx = lyricsBoxHeightPx / 2f
        val lineAbsY = baseTopSpacerPx + currentLrcIndex * lineHeightPx
        val wantedScroll = (lineAbsY - centerPx).toInt().coerceAtLeast(0)
        scope.launch { scrollState.scrollTo(wantedScroll) }
    }

    // 3️⃣ Centrage automatique si pas de scroll manuel
    LaunchedEffect(isPlaying, parsedLines, lyricsBoxHeightPx) {
        if (parsedLines.isEmpty() || lyricsBoxHeightPx == 0) return@LaunchedEffect
        while (isPlaying) {
            if (!userScrolling) centerCurrentLine()
            delay(16)
        }
    }

    // 4️⃣ UI
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        Text(
            text = "Paroles synchronisées",
            color = Color.LightGray,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            textAlign = TextAlign.Start
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coords -> lyricsBoxHeightPx = coords.size.height }
        ) {

            // colonne scrollable des paroles
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (baseTopSpacerPx > 0) {
                    Spacer(Modifier.height(with(density) { baseTopSpacerPx.toDp() }))
                }

                if (parsedLines.isEmpty()) {
                    Text(
                        "Aucune parole",
                        color = Color.Gray,
                        modifier = Modifier.padding(30.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    parsedLines.forEachIndexed { index, line ->
                        val isCurrent = index == currentLrcIndex
                        Text(
                            text = line.text,
                            color = if (isCurrent) Color(0xFFFF9800) else Color.White,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lineHeightDp)
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(80.dp))
            }

            // ligne de repère au centre
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x33FFFFFF))
                    .align(Alignment.TopStart)
                    .offset(y = (lyricsBoxHeightPx / 2).dp)
            )

            // 🟣 FADE HAUT (resserré à ~2 lignes)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black,
                                Color.Black.copy(alpha = 0f)
                            )
                        )
                    )
            )

            // 🟣 FADE BAS (resserré aussi)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0f),
                                Color.Black
                            )
                        )
                    )
            )
        }

        // Contrôles du lecteur
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
                onIsPlayingChange(true)
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
            .padding(vertical = 14.dp),
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