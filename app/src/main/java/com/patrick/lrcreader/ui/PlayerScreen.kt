package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.AutoReturnPrefs
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.DisplayPrefs
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.PlayerBusController
import com.patrick.lrcreader.core.pauseWithFade
import com.patrick.lrcreader.core.MidiCueDispatcher   // ⬅️ IMPORT MIDI
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
    currentTrackUri: String?,
    currentTrackGainDb: Int,
    onTrackGainChange: (Int) -> Unit,
    tempo: Float,
    onTempoChange: (Float) -> Unit,
    // Tonalité en demi-tons (mémorisée par titre)
    pitchSemi: Int,
    onPitchSemiChange: (Int) -> Unit,
    onRequestShowPlaylist: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current

    // 🔊 On branche ce MediaPlayer sur le bus LECTEUR
    LaunchedEffect(Unit) {
        PlayerBusController.attachPlayer(context, mediaPlayer)
        PlayerBusController.applyCurrentVolume(context)
    }

    // Décalage global des paroles (latence)
    val lyricsDelayMs = 0L

    var isConcertMode by remember {
        mutableStateOf(DisplayPrefs.isConcertMode(context))
    }

    var lyricsBoxHeightPx by remember { mutableStateOf(0) }
    var currentLrcIndex by remember { mutableStateOf(0) }

    // Dernière ligne pour laquelle on a envoyé un CUE MIDI
    var lastMidiIndex by remember(currentTrackUri) { mutableStateOf(-1) }

    var userScrolling by remember { mutableStateOf(false) }

    val lineHeightDp = 80.dp
    val lineHeightPx = with(density) { lineHeightDp.toPx() }
    val baseTopSpacerPx by remember(lyricsBoxHeightPx) { mutableStateOf(lyricsBoxHeightPx) }

    var durationMs by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosMs by remember { mutableStateOf(0) }

    var hasRequestedPlaylist by remember(currentTrackUri) { mutableStateOf(false) }

    var isEditingLyrics by remember { mutableStateOf(false) }
    var showMixScreen by remember { mutableStateOf(false) }

    var rawLyricsText by remember(currentTrackUri) { mutableStateOf("") }
    var editingLines by remember(currentTrackUri) { mutableStateOf<List<LrcLine>>(emptyList()) }

    var currentEditTab by remember { mutableStateOf(0) }

    // ---------- Suivi lecture + index ligne courante + MIDI ----------
    LaunchedEffect(isPlaying, parsedLines) {
        while (true) {
            val d = runCatching { mediaPlayer.duration }.getOrNull() ?: -1
            if (d > 0) durationMs = d

            val p = runCatching { mediaPlayer.currentPosition }.getOrNull() ?: 0
            if (!isDragging) positionMs = p

            if (parsedLines.isNotEmpty()) {
                val posMs = (p.toLong() - lyricsDelayMs).coerceAtLeast(0L)

                // On prend la ligne dont le timeMs est le plus proche de posMs
                val bestIndex = parsedLines.indices.minByOrNull {
                    abs(parsedLines[it].timeMs - posMs)
                } ?: 0

                if (bestIndex != currentLrcIndex) {
                    currentLrcIndex = bestIndex
                }

                // 🔥 Déclenche le CUE MIDI quand la ligne change
                if (currentTrackUri != null && bestIndex != lastMidiIndex) {
                    lastMidiIndex = bestIndex
                    MidiCueDispatcher.onActiveLineChanged(
                        trackUri = currentTrackUri,
                        lineIndex = bestIndex
                    )
                }
            }

            delay(200)
            if (!isPlaying && !mediaPlayer.isPlaying) delay(200)
        }
    }

    // ---------- Autoswitch playlist (optionnel) ----------
    LaunchedEffect(durationMs, positionMs, hasRequestedPlaylist, currentTrackUri) {
        val enabled = AutoReturnPrefs.isEnabled(context)
        if (
            enabled &&
            !hasRequestedPlaylist &&
            durationMs > 0 &&
            positionMs >= durationMs - 10_000
        ) {
            hasRequestedPlaylist = true
            onRequestShowPlaylist()
        }
    }

    // ---------- Suivi scroll manuel ----------
    LaunchedEffect(scrollState) {
        while (true) {
            userScrolling = scrollState.isScrollInProgress
            delay(80)
        }
    }

    fun centerCurrentLineImmediate() {
        if (lyricsBoxHeightPx == 0) return
        val centerPx = lyricsBoxHeightPx / 2f
        val lineAbsY = baseTopSpacerPx + currentLrcIndex * lineHeightPx
        val wantedScroll = (lineAbsY - centerPx).toInt().coerceAtLeast(0)
        scope.launch { scrollState.scrollTo(wantedScroll) }
    }

    fun seekAndCenter(targetMs: Int, targetIndex: Int) {
        PlaybackCoordinator.onPlayerStart()

        runCatching { mediaPlayer.seekTo(targetMs) }
        currentLrcIndex = targetIndex
        positionMs = targetMs
        if (!mediaPlayer.isPlaying) {
            // 🔊 Volume appliqué via le bus lecteur
            PlayerBusController.applyCurrentVolume(context)

            mediaPlayer.start()
            onIsPlayingChange(true)
        }
        if (lyricsBoxHeightPx > 0) {
            val centerPx = lyricsBoxHeightPx / 2f
            val lineAbsY = baseTopSpacerPx + targetIndex * lineHeightPx
            val wanted = (lineAbsY - centerPx).toInt().coerceAtLeast(0)
            scope.launch { scrollState.scrollTo(wanted) }
        }
    }

    // ---------- Auto-centering pendant lecture ----------
    LaunchedEffect(isPlaying, parsedLines, lyricsBoxHeightPx) {
        if (parsedLines.isEmpty() || lyricsBoxHeightPx == 0) return@LaunchedEffect

        while (true) {
            if (isPlaying && !userScrolling && !isDragging) {
                centerCurrentLineImmediate()
            }
            delay(40)
        }
    }

    // ─────────────────────────────
    //  LAYOUT GLOBAL ANALOGIQUE
    // ─────────────────────────────

    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF171717),
            Color(0xFF101010),
            Color(0xFF181410)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        if (isEditingLyrics) {
            // ========= MODE ÉDITION =========
            LyricsEditorSection(
                highlightColor = highlightColor,
                currentTrackUri = currentTrackUri,
                isEditingLyrics = isEditingLyrics,
                onCloseEditor = { isEditingLyrics = false },
                rawLyricsText = rawLyricsText,
                onRawLyricsTextChange = { rawLyricsText = it },
                editingLines = editingLines,
                onEditingLinesChange = { editingLines = it },
                currentEditTab = currentEditTab,
                onCurrentEditTabChange = { currentEditTab = it },
                mediaPlayer = mediaPlayer,
                positionMs = positionMs,
                durationMs = durationMs,
                onIsPlayingChange = onIsPlayingChange,
                onSaveSortedLines = { sorted ->
                    if (currentTrackUri != null) {
                        runCatching { LrcStorage.saveForTrack(context, currentTrackUri, sorted) }
                    }
                    onParsedLinesChange(sorted)
                    isEditingLyrics = false
                }
            )

        } else {

            // ========= MODE LECTURE =========
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1B1B1B)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {

                        ReaderHeader(
                            isConcertMode = isConcertMode,
                            onToggleConcertMode = {
                                isConcertMode = !isConcertMode
                                DisplayPrefs.setConcertMode(context, isConcertMode)
                            },
                            highlightColor = highlightColor,
                            onOpenMix = { showMixScreen = true },
                            onOpenEditor = {
                                if (parsedLines.isNotEmpty()) {
                                    rawLyricsText = parsedLines.joinToString("\n") { it.text }
                                    editingLines = parsedLines
                                } else {
                                    rawLyricsText = ""
                                    editingLines = emptyList()
                                }
                                currentEditTab = 0
                                isEditingLyrics = true
                            }
                        )

                        Spacer(Modifier.height(8.dp))

                        // Zone centrale : toujours mode MANU avec ligne centrée
                        LyricsArea(
                            modifier = Modifier.weight(1f),
                            scrollState = scrollState,
                            parsedLines = parsedLines,
                            isContinuousScroll = false,
                            isConcertMode = isConcertMode,
                            currentLrcIndex = currentLrcIndex,
                            baseTopSpacerPx = baseTopSpacerPx,
                            lyricsBoxHeightPx = lyricsBoxHeightPx,
                            onLyricsBoxHeightChange = { lyricsBoxHeightPx = it },
                            highlightColor = highlightColor,
                            onLineClick = { index, timeMs ->
                                // Seek + centrage
                                seekAndCenter(timeMs.toInt(), index)

                                // 🔥 Et on déclenche le CUE MIDI immédiatement au clic
                                if (currentTrackUri != null) {
                                    lastMidiIndex = index
                                    MidiCueDispatcher.onActiveLineChanged(
                                        trackUri = currentTrackUri,
                                        lineIndex = index
                                    )
                                }
                            }
                        )

                        TimeBar(
                            positionMs = if (isDragging) dragPosMs else positionMs,
                            durationMs = durationMs,
                            onSeekLivePreview = { newPos ->
                                isDragging = true
                                dragPosMs = newPos
                            },
                            onSeekCommit = { newPos ->
                                isDragging = false
                                val safe = min(max(newPos, 0), durationMs)
                                runCatching { mediaPlayer.seekTo(safe) }
                                positionMs = safe
                            },
                            highlightColor = highlightColor
                        )

                        PlayerControls(
                            isPlaying = isPlaying,
                            onPlayPause = {
                                if (mediaPlayer.isPlaying) {
                                    // Pause + bascule éventuelle vers le fond sonore
                                    pauseWithFade(scope, mediaPlayer, 400L) {
                                        onIsPlayingChange(false)
                                        PlaybackCoordinator.onFillerStart()
                                        runCatching { FillerSoundManager.startFromPlayerPause(context) }
                                    }
                                } else {
                                    if (durationMs > 0) {
                                        PlaybackCoordinator.onPlayerStart()

                                        // 🔊 Le bus applique le volume courant (fader LECTEUR)
                                        PlayerBusController.applyCurrentVolume(context)

                                        mediaPlayer.start()
                                        onIsPlayingChange(true)
                                        centerCurrentLineImmediate()
                                    }
                                }
                            },
                            onPrev = {
                                mediaPlayer.seekTo(0)
                                if (!mediaPlayer.isPlaying) {
                                    PlaybackCoordinator.onPlayerStart()

                                    // 🔊 Même chose quand on relance depuis le début
                                    PlayerBusController.applyCurrentVolume(context)

                                    mediaPlayer.start()
                                    onIsPlayingChange(true)
                                }
                                centerCurrentLineImmediate()
                            },
                            onNext = {
                                mediaPlayer.seekTo(max(durationMs - 1, 0))
                                mediaPlayer.pause()
                                onIsPlayingChange(false)
                                PlaybackCoordinator.onFillerStart()
                                runCatching { FillerSoundManager.startIfConfigured(context) }
                            }
                        )
                    }
                }
            }

            if (showMixScreen) {
                TrackMixScreen(
                    modifier = Modifier.fillMaxSize(),
                    highlightColor = highlightColor,
                    currentTrackGainDb = currentTrackGainDb,
                    onTrackGainChange = onTrackGainChange,
                    tempo = tempo,
                    onTempoChange = onTempoChange,
                    pitchSemi = pitchSemi,
                    onPitchSemiChange = onPitchSemiChange,
                    currentTrackUri = currentTrackUri,
                    onClose = { showMixScreen = false }
                )
            }
        }
    }
}

/* ─────────────────────────────
   HEADER LECTURE – STYLE CONSOLE
   ───────────────────────────── */

@Composable
private fun ReaderHeader(
    isConcertMode: Boolean,
    onToggleConcertMode: () -> Unit,
    highlightColor: Color,
    onOpenMix: () -> Unit,
    onOpenEditor: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF3A2C24),
                        Color(0xFF4B372A),
                        Color(0xFF3A2C24)
                    )
                ),
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                Color(0x55FFFFFF),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            IconButton(onClick = onToggleConcertMode) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Changer de style",
                    tint = if (isConcertMode) highlightColor else Color(0xFFCFD8DC)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onOpenMix) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = "Mixage du titre",
                    tint = Color(0xFFFFC107)
                )
            }

            IconButton(onClick = onOpenEditor) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Éditer les paroles",
                    tint = Color(0xFFFFF3E0)
                )
            }
        }
    }
}