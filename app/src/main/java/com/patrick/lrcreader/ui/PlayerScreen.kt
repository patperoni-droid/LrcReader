package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.DisplayPrefs
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
import androidx.compose.material3.SliderDefaults
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
    highlightColor: Color = Color(0xFFE040FB),
    // Niveau par titre
    currentTrackUri: String?,
    currentTrackGainDb: Int,
    onTrackGainChange: (Int) -> Unit,
    // Tempo par morceau (1f = normal), piloté par MainActivity
    tempo: Float,
    onTempoChange: (Float) -> Unit,
    // Demande d’afficher la playlist
    onRequestShowPlaylist: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current

    val lyricsDelayMs = 1000L

    var isConcertMode by remember {
        mutableStateOf(DisplayPrefs.isConcertMode(context))
    }
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

    // flag : bascule playlist déjà demandée pour ce titre ?
    var hasRequestedPlaylist by remember(currentTrackUri) {
        mutableStateOf(false)
    }

    // suivi lecture + index
    LaunchedEffect(isPlaying, parsedLines) {
        while (true) {
            val d = runCatching { mediaPlayer.duration }.getOrNull() ?: -1
            if (d > 0) durationMs = d

            val p = runCatching { mediaPlayer.currentPosition }.getOrNull() ?: 0
            if (!isDragging) positionMs = p

            if (parsedLines.isNotEmpty()) {
                val posMs = (p.toLong() - lyricsDelayMs).coerceAtLeast(0L)
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

    // Auto-switch vers la playlist 15s avant la fin
    LaunchedEffect(durationMs, positionMs, hasRequestedPlaylist) {
        if (!hasRequestedPlaylist && durationMs > 0 && positionMs >= durationMs - 15_000) {
            hasRequestedPlaylist = true
            onRequestShowPlaylist()
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
            runCatching { FillerSoundManager.fadeOutAndStop(400) }
        }
        if (lyricsBoxHeightPx > 0) {
            val centerPx = lyricsBoxHeightPx / 2f
            val lineAbsY = baseTopSpacerPx + targetIndex * lineHeightPx
            val wanted = (lineAbsY - centerPx).toInt().coerceAtLeast(0)
            scope.launch { scrollState.scrollTo(wanted) }
        }
    }

    // auto centre
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
            IconButton(onClick = {
                isConcertMode = !isConcertMode
                DisplayPrefs.setConcertMode(context, isConcertMode)
            }) {
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

                        // mode "pas concert" = aucune opacité
                        val lineAlpha: Float = if (!isConcertMode) {
                            1f
                        } else {
                            when (dist) {
                                0 -> 1f
                                1 -> 0.8f
                                2 -> 0.4f
                                else -> 0.08f
                            }
                        }

                        val color = highlightColor.copy(alpha = lineAlpha)
                        val weight = if (isCurrent) FontWeight.Bold else FontWeight.Normal

                        Text(
                            text = line.text,
                            color = color,
                            fontWeight = weight,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .clickable {
                                    seekAndCenter(line.timeMs.toInt(), index)
                                }
                        )
                    }
                }

                Spacer(Modifier.height(80.dp))
            }

            // ligne repère
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
            },
            highlightColor = highlightColor
        )

        // ───── Bloc niveau titre (gain par morceau) ─────
        val minDb = -12
        val maxDb = 12

        var gainSlider by remember(currentTrackUri, currentTrackGainDb) {
            mutableStateOf(
                (currentTrackGainDb - minDb).toFloat() / (maxDb - minDb).toFloat()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = "Niveau titre : ${if (currentTrackGainDb >= 0) "+${currentTrackGainDb}" else currentTrackGainDb} dB",
                color = Color.White,
                fontSize = 12.sp
            )
            Slider(
                value = gainSlider,
                onValueChange = { v ->
                    gainSlider = v
                    val newDb = (minDb + v * (maxDb - minDb)).toInt()
                    onTrackGainChange(newDb)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = highlightColor,
                    activeTrackColor = highlightColor.copy(alpha = 0.4f),
                    inactiveTrackColor = Color.DarkGray
                )
            )
        }

        // ───── Bloc TEMPO (0.8× à 1.2×, par morceau) ─────
        val minTempo = 0.8f
        val maxTempo = 1.2f

        var tempoSlider by remember(currentTrackUri, tempo) {
            mutableStateOf(
                ((tempo - minTempo) / (maxTempo - minTempo))
                    .coerceIn(0f, 1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 8.dp)
        ) {
            Text(
                text = "Tempo titre : ${String.format("%.2fx", tempo)}",
                color = Color.White,
                fontSize = 12.sp
            )
            Slider(
                value = tempoSlider,
                onValueChange = { v ->
                    tempoSlider = v
                    val newTempo = (minTempo + v * (maxTempo - minTempo))
                        .coerceIn(minTempo, maxTempo)
                    onTempoChange(newTempo)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF80CBC4),
                    activeTrackColor = Color(0xFF80CBC4).copy(alpha = 0.4f),
                    inactiveTrackColor = Color.DarkGray
                )
            )
        }

        // contrôles
        PlayerControls(
            isPlaying = isPlaying,
            onPlayPause = {
                if (mediaPlayer.isPlaying) {
                    pauseWithFade(scope, mediaPlayer, 400L) {
                        onIsPlayingChange(false)
                        runCatching { FillerSoundManager.startIfConfigured(context) }
                    }
                } else {
                    if (durationMs > 0) {
                        mediaPlayer.setVolume(1f, 1f)
                        mediaPlayer.start()
                        onIsPlayingChange(true)
                        centerCurrentLine()
                        runCatching { FillerSoundManager.fadeOutAndStop(400) }
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
    highlightColor: Color,
) {
    val posText = remember(positionMs) { formatMs(positionMs) }
    val durText = remember(durationMs) { formatMs(durationMs.coerceAtLeast(0)) }
    val trackColor = highlightColor.copy(alpha = 0.25f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            posText,
            color = Color.LightGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 6.dp)
        )

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
            modifier = Modifier
                .weight(1f)
                .height(20.dp),
            colors = SliderDefaults.colors(
                thumbColor = highlightColor,
                activeTrackColor = trackColor,
                inactiveTrackColor = trackColor.copy(alpha = 0.4f)
            )
        )

        Text(
            durText,
            color = Color.LightGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

private fun formatMs(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}