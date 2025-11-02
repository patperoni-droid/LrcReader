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

    // hauteur de la zone qui affiche les paroles
    var lyricsBoxHeightPx by remember { mutableStateOf(0) }

    // index de la ligne dont le timing est le + proche du temps courant
    var currentLrcIndex by remember { mutableStateOf(0) }

    // hauteur d’une ligne (un peu plus grande pour ne pas couper)
    val lineHeightDp = 72.dp      // ← tu peux monter à 50.dp si tu veux encore plus d’air
    val lineHeightPx = with(density) { lineHeightDp.toPx() }

    // on fait partir du bas : on met un spacer de la hauteur de la box
    val baseTopSpacerPx = remember(lyricsBoxHeightPx) {
        lyricsBoxHeightPx
    }

    // 1) on regarde tout le temps où en est la musique → on choisit la ligne la plus proche
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

    // 2) on force la ligne orange à rester au centre tout le temps
    LaunchedEffect(isPlaying, parsedLines, lyricsBoxHeightPx) {
        if (parsedLines.isEmpty()) return@LaunchedEffect
        if (lyricsBoxHeightPx == 0) return@LaunchedEffect

        while (isPlaying) {
            val centerPx = lyricsBoxHeightPx / 2f

            // position de la ligne dans le contenu scrollé
            val lineAbsY = baseTopSpacerPx + currentLrcIndex * lineHeightPx

            // pour la mettre au centre → scroll = positionLigne - centre
            val wantedScroll = (lineAbsY - centerPx).toInt().coerceAtLeast(0)

            // on y va direct pour que ça ne dérive pas vers le bas
            scrollState.scrollTo(wantedScroll)

            delay(16)   // ~60 fps
        }
    }

    // 3) UI
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // titre
        Text(
            text = "Paroles synchronisées",
            color = Color.LightGray,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            textAlign = TextAlign.Start
        )

        // ZONE PAROLES
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    lyricsBoxHeightPx = coords.size.height
                }
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // gros espace pour faire “partir du bas”
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
                                // on force une vraie hauteur de ligne pour ne pas couper
                                .height(lineHeightDp)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(80.dp))
            }

            // ligne de repère (centre)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x33FFFFFF))
                    .align(Alignment.TopStart)
                    .offset(y = (lyricsBoxHeightPx / 2).dp)
            )
        }

        // CONTROLES
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