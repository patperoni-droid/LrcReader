package com.patrick.lrcreader.ui

import androidx.compose.ui.graphics.graphicsLayer
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.patrick.lrcreader.core.DisplayPrefs
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.LrcCleaner
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.PrompterPrefs
import com.patrick.lrcreader.core.pauseWithFade
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
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

    // MANU / AUTO = false / true -> AUTO = mode prompteur
    var isContinuousScroll by remember { mutableStateOf(false) } // utilisé comme "mode prompteur"

    // état pour le prompteur
    val prompterScrollState = rememberScrollState()
    var isPrompterRunning by remember { mutableStateOf(false) }
    var prompterSpeed by remember {
        mutableStateOf(PrompterPrefs.getSpeed(context))
    }

    var lyricsBoxHeightPx by remember { mutableStateOf(0) }
    var currentLrcIndex by remember { mutableStateOf(0) }
    var userScrolling by remember { mutableStateOf(false) }

    val lineHeightDp = 80.dp
    val lineHeightPx = with(density) { lineHeightDp.toPx() }
    val baseTopSpacerPx by remember(lyricsBoxHeightPx) { mutableStateOf(lyricsBoxHeightPx) }

    var durationMs by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosMs by remember { mutableStateOf(0) }

    var hasRequestedPlaylist by remember(currentTrackUri) {
        mutableStateOf(false)
    }

    var isEditingLyrics by remember { mutableStateOf(false) }
    var showMixScreen by remember { mutableStateOf(false) }

    // ► Etat texte brut et lignes d’édition : mémorisés seulement par morceau
    var rawLyricsText by remember(currentTrackUri) {
        mutableStateOf("")
    }
    var editingLines by remember(currentTrackUri) {
        mutableStateOf<List<LrcLine>>(emptyList())
    }

    var currentEditTab by remember { mutableStateOf(0) }

    // texte utilisé pour le prompteur
    val prompterText = remember(currentTrackUri, parsedLines, rawLyricsText) {
        buildPrompterText(parsedLines, rawLyricsText)
    }

    // ---------- Suivi lecture + index ligne courante ----------
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

    // ---------- Autoswitch playlist ----------
    LaunchedEffect(durationMs, positionMs, hasRequestedPlaylist) {
        if (!hasRequestedPlaylist && durationMs > 0 && positionMs >= durationMs - 15_000) {
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

    // ---------- Auto-scroll du prompteur ----------
    LaunchedEffect(isPrompterRunning, prompterSpeed, prompterText, currentTrackUri) {
        if (!isPrompterRunning) return@LaunchedEffect
        // on repart du haut à chaque lancement
        prompterScrollState.scrollTo(0)
        while (isPrompterRunning && prompterScrollState.canScrollForward) {
            prompterScrollState.scrollBy(6f * prompterSpeed.coerceIn(0.2f, 2.0f))
            delay(30L)
        }
    }

    fun centerCurrentLineImmediate() {
        if (lyricsBoxHeightPx == 0) return
        val centerPx = lyricsBoxHeightPx / 2f
        val lineAbsY = baseTopSpacerPx + currentLrcIndex * lineHeightPx
        val wantedScroll = (lineAbsY - centerPx).toInt().coerceAtLeast(0)
        scope.launch { scrollState.scrollTo(wantedScroll) }
    }

    fun centerCurrentLineAnimated() {
        if (lyricsBoxHeightPx == 0) return
        val centerPx = lyricsBoxHeightPx / 2f
        val lineAbsY = baseTopSpacerPx + currentLrcIndex * lineHeightPx
        val wantedScroll = (lineAbsY - centerPx).toInt().coerceAtLeast(0)
        scope.launch { scrollState.animateScrollTo(wantedScroll) }
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
            scope.launch {
                // en mode prompteur, on ne touche pas au scroll des paroles
                if (!isContinuousScroll) {
                    scrollState.scrollTo(wanted)
                }
            }
        }
    }

    // ---------- Auto-centering pendant lecture (mode MANU uniquement) ----------
    LaunchedEffect(isPlaying, parsedLines, lyricsBoxHeightPx, isContinuousScroll) {
        if (parsedLines.isEmpty()) return@LaunchedEffect
        if (lyricsBoxHeightPx == 0) return@LaunchedEffect

        while (true) {
            if (isPlaying && !userScrolling && !isDragging && !isContinuousScroll) {
                // seulement en MANU
                centerCurrentLineImmediate()
            }
            delay(40)
        }
    }

    // ─────────────────────────────
    //  LAYOUT GLOBAL
    // ─────────────────────────────
    DarkBlueGradientBackground {
        Box(
            modifier = modifier
                .fillMaxSize()
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
                            runCatching {
                                LrcStorage.saveForTrack(context, currentTrackUri, sorted)
                            }
                        }
                        onParsedLinesChange(sorted)
                        isEditingLyrics = false
                    }
                )
            } else {
                // ========= MODE LECTURE / PROMPTEUR =========
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {

                    ReaderHeader(
                        isConcertMode = isConcertMode,
                        onToggleConcertMode = {
                            isConcertMode = !isConcertMode
                            DisplayPrefs.setConcertMode(context, isConcertMode)
                        },
                        isContinuousScroll = isContinuousScroll,
                        onToggleContinuousScroll = {
                            // bascule MANU / AUTO (AUTO = prompteur)
                            isContinuousScroll = !isContinuousScroll
                            isPrompterRunning = false
                            // on remet le prompteur en haut quand on l’active
                            if (isContinuousScroll) {
                                scope.launch { prompterScrollState.scrollTo(0) }
                            }
                        },
                        highlightColor = highlightColor,
                        onOpenMix = { showMixScreen = true },
                        onOpenEditor = {
                            if (parsedLines.isNotEmpty()) {
                                rawLyricsText = parsedLines.joinToString("\n") { it.text }
                                editingLines = parsedLines
                            } else {
                                if (rawLyricsText.isBlank() && editingLines.isEmpty()) {
                                    rawLyricsText = ""
                                    editingLines = emptyList()
                                }
                            }
                            currentEditTab = 0
                            isEditingLyrics = true
                        }
                    )

                    // Zone centrale : soit paroles LRC, soit prompteur AUTO
                    if (isContinuousScroll) {
                        PrompterArea(
                            modifier = Modifier.weight(1f),
                            scrollState = prompterScrollState,
                            text = prompterText,
                            isRunning = isPrompterRunning,
                            onToggleRunning = { isPrompterRunning = !isPrompterRunning },
                            speed = prompterSpeed,
                            onSpeedChange = { new ->
                                val clamped = new.coerceIn(0.2f, 2.0f)
                                prompterSpeed = clamped
                                PrompterPrefs.saveSpeed(context, clamped)
                            },
                            highlightColor = highlightColor
                        )
                    } else {
                        LyricsArea(
                            modifier = Modifier.weight(1f),
                            scrollState = scrollState,
                            parsedLines = parsedLines,
                            isContinuousScroll = false, // le mode AUTO est maintenant le prompteur
                            isConcertMode = isConcertMode,
                            currentLrcIndex = currentLrcIndex,
                            baseTopSpacerPx = baseTopSpacerPx,
                            lyricsBoxHeightPx = lyricsBoxHeightPx,
                            onLyricsBoxHeightChange = { lyricsBoxHeightPx = it },
                            highlightColor = highlightColor,
                            onLineClick = { index, timeMs ->
                                seekAndCenter(timeMs.toInt(), index)
                            }
                        )
                    }

                    // Timeline
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

                    // ► Play / Pause + Next / Prev juste sous la timeline
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
                                    if (!isContinuousScroll) {
                                        centerCurrentLineImmediate()
                                    }
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
                            if (!isContinuousScroll) {
                                centerCurrentLineImmediate()
                            }
                        },
                        onNext = {
                            mediaPlayer.seekTo(max(durationMs - 1, 0))
                            mediaPlayer.pause()
                            onIsPlayingChange(false)
                            runCatching { FillerSoundManager.startIfConfigured(context) }
                        }
                    )
                }

                if (showMixScreen) {
                    TrackMixScreen(
                        modifier = Modifier.fillMaxSize(),
                        highlightColor = highlightColor,
                        currentTrackGainDb = currentTrackGainDb,
                        onTrackGainChange = onTrackGainChange,
                        tempo = tempo,
                        onTempoChange = onTempoChange,
                        onClose = { showMixScreen = false }
                    )
                }
            }
        }
    }
}

/* ─────────────────────────────
   SECTION : ÉDITEUR DE PAROLES
   ───────────────────────────── */

@Composable
private fun LyricsEditorSection(
    highlightColor: Color,
    currentTrackUri: String?,
    isEditingLyrics: Boolean,
    onCloseEditor: () -> Unit,
    rawLyricsText: String,
    onRawLyricsTextChange: (String) -> Unit,
    editingLines: List<LrcLine>,
    onEditingLinesChange: (List<LrcLine>) -> Unit,
    currentEditTab: Int,
    onCurrentEditTabChange: (Int) -> Unit,
    mediaPlayer: MediaPlayer,
    positionMs: Int,
    durationMs: Int,
    onIsPlayingChange: (Boolean) -> Unit,
    onSaveSortedLines: (List<LrcLine>) -> Unit
) {
    if (!isEditingLyrics) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Éditeur de paroles",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCloseEditor) {
                Text("Fermer", color = Color(0xFFFF8A80))
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = currentEditTab,
            containerColor = Color.Transparent,
            contentColor = highlightColor
        ) {
            Tab(
                selected = currentEditTab == 0,
                onClick = { onCurrentEditTabChange(0) },
                text = { Text("Simple") }
            )
            Tab(
                selected = currentEditTab == 1,
                onClick = {
                    val lines = rawLyricsText
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    onEditingLinesChange(
                        lines.mapIndexed { index, lineText ->
                            val old = editingLines.getOrNull(index)
                            if (old != null) old.copy(text = lineText)
                            else LrcLine(timeMs = 0L, text = lineText)
                        }
                    )

                    onCurrentEditTabChange(1)
                },
                text = { Text("Synchro") }
            )
        }

        Spacer(Modifier.height(8.dp))

        when (currentEditTab) {
            0 -> {
                // ---------- Onglet SIMPLE ----------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val uri = currentTrackUri
                                if (uri != null) {
                                    val imported =
                                        importLyricsFromAudio(context, uri)
                                    if (imported != null && imported.isNotEmpty()) {
                                        onEditingLinesChange(imported)
                                        onRawLyricsTextChange(
                                            imported.joinToString("\n") { it.text }
                                        )
                                        onCurrentEditTabChange(1)
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = "Importer depuis MP3",
                                color = Color(0xFF80CBC4),
                                fontSize = 12.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = rawLyricsText,
                        onValueChange = { newText ->
                            onRawLyricsTextChange(newText)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        label = {
                            Text(
                                "Paroles (une ligne par phrase)",
                                color = Color.LightGray
                            )
                        }
                    )
                }
            }

            1 -> {
                // ---------- Onglet SYNCHRO ----------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Mini player synchro
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(onClick = {
                            if (mediaPlayer.isPlaying) {
                                pauseWithFade(scope, mediaPlayer, 200L) {
                                    onIsPlayingChange(false)
                                    runCatching {
                                        FillerSoundManager.startIfConfigured(context)
                                    }
                                }
                            } else {
                                if (durationMs > 0) {
                                    mediaPlayer.start()
                                    onIsPlayingChange(true)
                                    runCatching {
                                        FillerSoundManager.fadeOutAndStop(200)
                                    }
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (mediaPlayer.isPlaying)
                                    Icons.Filled.Pause
                                else
                                    Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause synchro",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            runCatching { mediaPlayer.seekTo(0) }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Revenir début",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = formatMs(positionMs),
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }

                    // Reset TAGs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                onEditingLinesChange(
                                    editingLines.map {
                                        it.copy(timeMs = 0L)
                                    }
                                )
                            }
                        ) {
                            Text(
                                text = "Reset TAGs",
                                color = Color(0xFFFF8A80),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Liste des lignes taguables
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (editingLines.isEmpty()) {
                            Text(
                                "Ajoute d’abord des paroles dans l’onglet Simple.",
                                color = Color.Gray,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            editingLines.forEachIndexed { index, line ->
                                val timeLabel =
                                    if (line.timeMs > 0)
                                        formatLrcTime(line.timeMs)
                                    else
                                        "--:--.--"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        TextButton(
                                            onClick = {
                                                val now = runCatching {
                                                    mediaPlayer.currentPosition
                                                }.getOrElse { 0 }

                                                onEditingLinesChange(
                                                    editingLines.mapIndexed { i, old ->
                                                        if (i == index)
                                                            old.copy(
                                                                timeMs = now.toLong()
                                                            )
                                                        else old
                                                    }
                                                )
                                            }
                                        ) {
                                            Text(
                                                text = "TAG",
                                                color = Color(0xFF80CBC4),
                                                fontSize = 12.sp
                                            )
                                        }
                                        Text(
                                            text = timeLabel,
                                            color = Color(0xFFB0BEC5),
                                            fontSize = 10.sp
                                        )
                                    }

                                    Text(
                                        text = line.text,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            val t = line.timeMs
                                                .coerceAtLeast(0L)
                                                .toInt()
                                            runCatching { mediaPlayer.seekTo(t) }
                                            if (!mediaPlayer.isPlaying) {
                                                mediaPlayer.start()
                                                onIsPlayingChange(true)
                                                runCatching {
                                                    FillerSoundManager.fadeOutAndStop(
                                                        200
                                                    )
                                                }
                                            }
                                        },
                                        enabled = line.timeMs > 0L
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Lire depuis cette phrase",
                                            tint = if (line.timeMs > 0L)
                                                Color.White
                                            else
                                                Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Barre d’actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCloseEditor) {
                Text("Annuler", color = Color.LightGray)
            }
            TextButton(onClick = {
                var lines = editingLines
                if (lines.isEmpty()) {
                    lines = rawLyricsText
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { LrcLine(timeMs = 0L, text = it) }
                }

                val sorted = lines.sortedWith(
                    compareBy<LrcLine> {
                        if (it.timeMs <= 0L) Long.MAX_VALUE else it.timeMs
                    }
                )

                onSaveSortedLines(sorted)
            }) {
                Text("Enregistrer", color = Color(0xFF80CBC4))
            }
        }
    }
}

/* ─────────────────────────────
   HEADER LECTURE
   ───────────────────────────── */

@Composable
private fun ReaderHeader(
    isConcertMode: Boolean,
    onToggleConcertMode: () -> Unit,
    isContinuousScroll: Boolean,
    onToggleContinuousScroll: () -> Unit,
    highlightColor: Color,
    onOpenMix: () -> Unit,
    onOpenEditor: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onToggleConcertMode) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Changer de style",
                tint = if (isConcertMode) highlightColor else Color.White
            )
        }

        // Bouton MANU / AUTO (AUTO = prompteur)
        TextButton(onClick = onToggleContinuousScroll) {
            Text(
                text = if (isContinuousScroll) "AUTO" else "MANU",
                color = if (isContinuousScroll) highlightColor else Color.White,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onOpenMix) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = "Mixage du titre",
                tint = Color.White
            )
        }

        IconButton(onClick = onOpenEditor) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Éditer les paroles",
                tint = Color.White
            )
        }
    }
}

/* ─────────────────────────────
   LYRICS AREA (MODE MANU)
   ───────────────────────────── */

@Composable
private fun LyricsArea(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    parsedLines: List<LrcLine>,
    isContinuousScroll: Boolean,
    isConcertMode: Boolean,
    currentLrcIndex: Int,
    baseTopSpacerPx: Int,
    lyricsBoxHeightPx: Int,
    onLyricsBoxHeightChange: (Int) -> Unit,
    highlightColor: Color,
    onLineClick: (index: Int, timeMs: Long) -> Unit
) {
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                onLyricsBoxHeightChange(coords.size.height)
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Espace haut pour centrer la ligne courante
            if (baseTopSpacerPx > 0) {
                Spacer(
                    Modifier.height(
                        with(density) { baseTopSpacerPx.toDp() }
                    )
                )
            }

            // --- Texte ---
            if (parsedLines.isEmpty()) {
                Text(
                    "Aucune parole",
                    color = Color.Gray,
                    modifier = Modifier.padding(30.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                parsedLines.forEachIndexed { index, line ->

                    val (color, weight) =
                        if (isContinuousScroll) {
                            Color.White to FontWeight.Normal
                        } else {
                            val isCurrent = index == currentLrcIndex
                            val dist = abs(index - currentLrcIndex)

                            val alpha = if (!isConcertMode) 1f else when (dist) {
                                0 -> 1f
                                1 -> 0.8f
                                2 -> 0.4f
                                else -> 0.08f
                            }

                            highlightColor.copy(alpha = alpha) to
                                    (if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                        }

                    Text(
                        text = line.text,
                        color = color,
                        fontWeight = weight,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .clickable {
                                onLineClick(index, line.timeMs)
                            }
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        // Trait horizontal au centre (pour repère visuel)
        if (lyricsBoxHeightPx > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x33FFFFFF))
                    .align(Alignment.TopStart)
                    .offset(
                        y = with(density) {
                            (lyricsBoxHeightPx / 2).toDp()
                        }
                    )
            )
        }
    }
}

/* ─────────────────────────────
   PROMPTEUR AUTO
   ───────────────────────────── */

@Composable
private fun PrompterArea(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    text: String,
    isRunning: Boolean,
    onToggleRunning: () -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    highlightColor: Color
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // ───── COLONNE TEXTE (PROMPTEUR) ─────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mode prompteur",
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp
                )

                TextButton(onClick = onToggleRunning) {
                    Text(
                        text = if (isRunning) "Pause défilement" else "Démarrer défilement",
                        color = highlightColor,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (text.isBlank()) {
                Text(
                    text = "Aucune parole disponible pour ce morceau.\nAjoute du texte dans l’éditeur.",
                    color = Color.Gray,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            } else {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Spacer(Modifier.height(200.dp))
            }
        }

        // ───── COLONNE FADER VITESSE ─────
        Column(
            modifier = Modifier
                .width(72.dp)
                .padding(start = 8.dp)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Vitesse",
                color = Color(0xFFB0BEC5),
                fontSize = 11.sp
            )

            Spacer(Modifier.height(4.dp))

            // valeur texte (0.2 → 2.0)
            Text(
                text = String.format("%.1fx", speed),
                color = Color.White,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(8.dp))

            // Fader vertical (Slider tourné)
            Box(
                modifier = Modifier
                    .height(180.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Slider(
                    value = speed,
                    onValueChange = { newValue ->
                        onSpeedChange(newValue)
                    },
                    valueRange = 0.3f..2.0f,
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth()
                        .graphicsLayer {
                            // rotation en vertical
                            rotationZ = -90f
                        },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFFE040FB),
                        inactiveTrackColor = Color(0x55E040FB)
                    )
                )
            }
        }
    }
}

/* ─────────────────────────────
   CONTROLES LECTURE
   ───────────────────────────── */

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

/* ─────────────────────────────
   FONCTIONS UTILITAIRES
   ───────────────────────────── */

private fun formatMs(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Format LRC style : mm:ss.xx */
private fun formatLrcTime(ms: Long): String {
    if (ms <= 0L) return "00:00.00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
}

/** Construit le texte brut pour le prompteur. */
private fun buildPrompterText(
    parsedLines: List<LrcLine>,
    rawLyrics: String
): String {
    return when {
        parsedLines.isNotEmpty() ->
            parsedLines.joinToString("\n") { it.text }
        rawLyrics.isNotBlank() ->
            rawLyrics
        else -> ""
    }
}

/**
 * Essaie d'importer les paroles intégrées dans le MP3 (Musicolet ou autre).
 * Ne modifie jamais le MP3.
 */
private fun importLyricsFromAudio(
    context: Context,
    uriString: String
): List<LrcLine>? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.parse(uriString))

        val raw: String? = try {
            val field = MediaMetadataRetriever::class.java.getField("METADATA_KEY_LYRICS")
            val key = field.getInt(null)
            retriever.extractMetadata(key)
        } catch (e: Exception) {
            null
        }

        retriever.release()

        if (raw.isNullOrBlank()) return null

        val cleaned = LrcCleaner.clean(raw)

        if (cleaned.isNotEmpty()) {
            cleaned.map { it.copy(timeMs = 0L, text = it.text) }
        } else {
            raw.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line ->
                    LrcLine(timeMs = 0L, text = line)
                }
        }
    } catch (_: Exception) {
        null
    }
}