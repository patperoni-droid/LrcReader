package com.patrick.lrcreader.ui

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.pauseWithFade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    val context = LocalContext.current  // pour lancer/arrêter le fond sonore

    val highlightColor = Color(0xFFE040FB)
    val LYRICS_DELAY_MS = 1000L

    var isConcertMode by remember { mutableStateOf(true) }
    var lyricsBoxHeightPx by remember { mutableStateOf(0) }
    var currentLrcIndex by remember { mutableStateOf(0) }
    var userScrolling by remember { mutableStateOf(false) }

    val lineHeightDp = 80.dp
    val lineHeightPx = with(density) { lineHeightDp.toPx() }
    val baseTopSpacerPx = remember(lyricsBoxHeightPx) { lyricsBoxHeightPx }

    var durationMs by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosMs by remember { mutableStateOf(0) }

    // suivi lecture + ligne active
    LaunchedEffect(isPlaying, parsedLines) {
        while (true) {
            val d = runCatching { mediaPlayer.duration }.getOrNull() ?: -1
            if (d > 0) durationMs = d

            val p = runCatching { mediaPlayer.currentPosition }.getOrNull() ?: 0
            if (!isDragging) positionMs = p

            if (parsedLines.isNotEmpty()) {
                val posMs = (p.toLong() - LYRICS_DELAY_MS).coerceAtLeast(0L)
                val bestIndex = parsedLines.indices.minByOrNull {
                    abs(parsedLines[it].timeMs - posMs)
                } ?: 0
                currentLrcIndex = bestIndex
            }

            delay(200)
            if (!isPlaying && !mediaPlayer.isPlaying) {
                delay(200)
            }
        }
    }

    // détecter le scroll manuel
    LaunchedEffect(scrollState) {
        while (true) {
            userScrolling = scrollState.isScrollInProgress
            delay(80)
        }
    }

    fun centerCurrentLine() {
        if (lyricsBoxHeightPx == 0) return
        val centerPx = lyricsBoxHeightPx / 2f
        val lineAbsY = baseTopSpacerPx + currentLrcIndex * lineHeightPx
        val wantedScroll = (lineAbsY - centerPx).toInt().coerceAtLeast(0)
        scope.launch { scrollState.scrollTo(wantedScroll) }
    }

    fun seekAndCenter(targetMs: Int, targetIndex: Int) {
        runCatching { mediaPlayer.seekTo(targetMs) }
        currentLrcIndex = targetIndex
        positionMs = targetMs
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            onIsPlayingChange(true)
            // on coupe le fond si on repart sur le vrai titre
            runCatching { FillerSoundManager.fadeOutAndStop(400) }
        }
        if (lyricsBoxHeightPx > 0) {
            val centerPx = lyricsBoxHeightPx / 2f
            val lineAbsY = baseTopSpacerPx + targetIndex * lineHeightPx
            val wanted = (lineAbsY - centerPx).toInt().coerceAtLeast(0)
            scope.launch { scrollState.scrollTo(wanted) }
        }
    }

    // auto-centre pendant la lecture
    LaunchedEffect(isPlaying, parsedLines, lyricsBoxHeightPx) {
        if (parsedLines.isEmpty()) return@LaunchedEffect
        if (lyricsBoxHeightPx == 0) return@LaunchedEffect

        while (true) {
            if (isPlaying && !userScrolling && !isDragging) {
                centerCurrentLine()
            }
            delay(120)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isConcertMode) "Paroles synchronisées" else "Paroles (mode clair)",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Start
            )
            IconButton(onClick = { isConcertMode = !isConcertMode }) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Changer de style",
                    tint = if (isConcertMode) highlightColor else Color.White
                )
            }
        }

        // paroles
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
                        val dist = abs(index - currentLrcIndex)

                        val alpha: Float = if (!isConcertMode) {
                            1f
                        } else {
                            when (dist) {
                                0 -> 1f
                                1 -> 0.8f
                                2 -> 0.4f
                                else -> 0.08f
                            }
                        }

                        val color = if (isCurrent) highlightColor else Color.White.copy(alpha = alpha)
                        val weight = if (isCurrent) FontWeight.Bold else FontWeight.Normal

                        Text(
                            text = line.text,
                            color = color,
                            fontWeight = weight,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lineHeightDp)
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .clickable {
                                    seekAndCenter(line.timeMs.toInt(), index)
                                }
                        )
                    }
                }

                Spacer(Modifier.height(80.dp))
            }

            // ligne de repère
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x33FFFFFF))
                    .align(Alignment.TopStart)
                    .offset(y = (lyricsBoxHeightPx / 2).dp)
            )
        }

        // barre de temps
        TimeBar(
            positionMs = if (isDragging) dragPosMs else positionMs,
            durationMs = durationMs,
            onSeekLivePreview = { newPos ->
                isDragging = true
                dragPosMs = newPos
            },
            onSeekCommit = { newPos ->
                isDragging = false
                val safe = when {
                    durationMs <= 0 -> 0
                    else -> min(max(newPos, 0), durationMs)
                }
                runCatching { mediaPlayer.seekTo(safe) }
                positionMs = safe
            }
        )

        // contrôles
        PlayerControls(
            isPlaying = isPlaying,
            onPlayPause = {
                if (mediaPlayer.isPlaying) {
                    // pause en fade + fond sonore qui repart (protégé)
                    pauseWithFade(scope, mediaPlayer, 400L) {
                        onIsPlayingChange(false)
                        runCatching {
                            FillerSoundManager.startIfConfigured(context)
                        }
                    }
                } else {
                    if (durationMs > 0) {
                        mediaPlayer.setVolume(1f, 1f)
                        mediaPlayer.start()
                        onIsPlayingChange(true)
                        centerCurrentLine()
                        // on coupe le filler si on relance la vraie musique
                        runCatching {
                            FillerSoundManager.fadeOutAndStop(400)
                        }
                    }
                }
            },
            onPrev = {
                mediaPlayer.seekTo(0)
                if (!mediaPlayer.isPlaying) {
                    mediaPlayer.start()
                    onIsPlayingChange(true)
                    runCatching { FillerSoundManager.fadeOutAndStop(400) }
                }
                centerCurrentLine()
            },
            onNext = {
                mediaPlayer.seekTo(max(durationMs - 1, 0))
                mediaPlayer.pause()
                onIsPlayingChange(false)
                // on relance le fond mais on protège
                runCatching { FillerSoundManager.startIfConfigured(context) }
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

@Composable
private fun TimeBar(
    positionMs: Int,
    durationMs: Int,
    onSeekLivePreview: (Int) -> Unit,
    onSeekCommit: (Int) -> Unit,
) {
    val posText = remember(positionMs) { formatMs(positionMs) }
    val durText = remember(durationMs) { formatMs(durationMs.coerceAtLeast(0)) }
    val remainingText = remember(positionMs, durationMs) {
        if (durationMs > 0) "-${formatMs((durationMs - positionMs).coerceAtLeast(0))}" else "-00:00"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(posText, color = Color.LightGray, fontSize = 12.sp)
            Text(remainingText, color = Color.LightGray, fontSize = 12.sp)
        }

        val sliderValue = when {
            durationMs <= 0 -> 0f
            else -> positionMs.toFloat() / durationMs.toFloat()
        }

        var lastPreview by remember { mutableStateOf(positionMs) }

        Slider(
            value = sliderValue,
            onValueChange = { frac ->
                val preview = (frac * durationMs).toInt()
                lastPreview = preview
                onSeekLivePreview(preview)
            },
            onValueChangeFinished = {
                onSeekCommit(lastPreview)
            },
            enabled = durationMs > 0,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            durText,
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

/** mm:ss (si > 1h: hh:mm:ss) */
private fun formatMs(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}