package com.patrick.lrcreader.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.pauseWithFade
import com.patrick.lrcreader.core.parseLrc
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

    // ---------- Auto-centering pendant lecture ----------
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

    // ─────────────────────────────
    //  LAYOUT GLOBAL
    // ─────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // ─────────────── MODE ÉDITION PAROLES ───────────────
            if (isEditingLyrics) {
                Column(
                    modifier = Modifier.fillMaxSize()
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
                        TextButton(onClick = { isEditingLyrics = false }) {
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
                            onClick = { currentEditTab = 0 },
                            text = { Text("Simple") }
                        )
                        Tab(
                            selected = currentEditTab == 1,
                            onClick = {
                                // On reconstruit editingLines à partir du texte brut,
                                // en conservant les timeMs existants par index.
                                val lines = rawLyricsText
                                    .lines()
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }

                                editingLines = lines.mapIndexed { index, lineText ->
                                    val old = editingLines.getOrNull(index)
                                    if (old != null) old.copy(text = lineText)
                                    else LrcLine(timeMs = 0L, text = lineText)
                                }

                                currentEditTab = 1
                            },
                            text = { Text("Synchro") }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    when (currentEditTab) {
                        // ---------- Onglet SIMPLE ----------
                        0 -> {
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
                                                val imported = importLyricsFromAudio(context, uri)
                                                if (imported != null && imported.isNotEmpty()) {
                                                    editingLines = imported
                                                    rawLyricsText =
                                                        imported.joinToString("\n") { it.text }
                                                    currentEditTab = 1
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
                                        // On ne touche qu'au texte, pas aux timecodes.
                                        rawLyricsText = newText
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

                        // ---------- Onglet SYNCHRO ----------
                        1 -> {
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

                                // 🔥 Bouton pour effacer tous les TAGs (remettre tous les timeMs à 0)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            editingLines = editingLines.map {
                                                it.copy(timeMs = 0L)
                                            }
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
                                                // Colonne TAG + temps
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                ) {
                                                    TextButton(
                                                        onClick = {
                                                            val now = runCatching {
                                                                mediaPlayer.currentPosition
                                                            }.getOrElse { 0 }

                                                            editingLines =
                                                                editingLines.mapIndexed { i, old ->
                                                                    if (i == index)
                                                                        old.copy(timeMs = now.toLong())
                                                                    else old
                                                                }
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

                                                // Texte de la phrase
                                                Text(
                                                    text = line.text,
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                // Bouton Play depuis ce temps
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
                                                                FillerSoundManager.fadeOutAndStop(200)
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
                        TextButton(onClick = { isEditingLyrics = false }) {
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

                            if (currentTrackUri != null) {
                                runCatching {
                                    LrcStorage.saveForTrack(context, currentTrackUri, sorted)
                                }
                            }

                            onParsedLinesChange(sorted)
                            isEditingLyrics = false
                        }) {
                            Text("Enregistrer", color = Color(0xFF80CBC4))
                        }
                    }
                }

                // On ne dessine pas le reste quand on est en édition
                return@Column
            }

            // ─────────────── MODE LECTURE NORMAL ───────────────

            // Header lecteur
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

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = { showMixScreen = true }) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Mixage du titre",
                        tint = Color.White
                    )
                }

                IconButton(onClick = {
                    // Ouverture éditeur :
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
                }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Éditer les paroles",
                        tint = Color.White
                    )
                }
            }

            // Paroles
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x33FFFFFF))
                        .align(Alignment.TopStart)
                        .offset(y = (lyricsBoxHeightPx / 2).dp)
                )
            }

            // Timebar
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

            // Gain titre
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

            // Tempo
            val minTempo = 0.8f
            val maxTempo = 1.2f

            var tempoSlider by remember(tempo) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tempo : ${String.format("%.2fx", tempo)}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    TextButton(onClick = { onTempoChange(1.0f) }) {
                        Text(
                            text = "Reset 1.00x",
                            color = Color(0xFF80CBC4),
                            fontSize = 11.sp
                        )
                    }
                }

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

            // Contrôles transport
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

        // Overlay mixage
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

/** Format LRC style : mm:ss.xx */
private fun formatLrcTime(ms: Long): String {
    if (ms <= 0L) return "00:00.00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
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

        val parsed = runCatching { parseLrc(raw) }.getOrElse { emptyList() }
        if (parsed.isNotEmpty()) {
            // On garde le texte mais on remet tous les timecodes à 0 pour re-taguer propre.
            parsed.map { it.copy(timeMs = 0L) }
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