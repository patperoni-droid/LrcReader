@file:OptIn(androidx.media3.common.util.UnstableApi::class,
androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.patrick.lrcreader.ui

import android.util.Log
import com.patrick.lrcreader.core.MidiOutput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import com.patrick.lrcreader.core.notes.LiveNote
import com.patrick.lrcreader.core.notes.LiveNoteManager
import com.patrick.lrcreader.core.PlayerBusController
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.AutoReturnPrefs
import com.patrick.lrcreader.core.DisplayPrefs
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.MidiCueDispatcher
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.audio.AudioEngine
import com.patrick.lrcreader.core.parseLrc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    exoPlayer: ExoPlayer, // ok même si pas utilisé directement ici

    closeMixSignal: Int = 0,
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
    pitchSemi: Int,
    onPitchSemiChange: (Int) -> Unit,
    onRequestShowPlaylist: () -> Unit,
    getPositionMs: () -> Long,
    getDurationMs: () -> Long,
    seekToMs: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        MidiOutput.init(context)
    }
    android.util.Log.d("PlayerScreen", "COMPOSED ✅ PlayerScreen actif")

    LaunchedEffect(Unit) {
        android.util.Log.d("PlayerScreen", "MIDI INIT LaunchedEffect ✅")
    }
    val density = LocalDensity.current

    // 📝 Notes LIVE (création depuis le lecteur)
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteDraftText by remember { mutableStateOf("") }
    // durée par défaut (en ms)
    var noteDraftDurationMs by remember { mutableStateOf(30_000L) }
    var noteAnchorMs by remember { mutableStateOf<Long?>(null) }      // timecode gelé
    var wasPlayingBeforeNote by remember { mutableStateOf(false) }    // pour relancer après

    // 🔊 Brancher ExoPlayer au bus principal (fader LECTEUR)
    var activeLiveNote by remember { mutableStateOf<LiveNote?>(null) }
    LaunchedEffect(exoPlayer) {
        PlayerBusController.attachPlayer(context, exoPlayer)
    }

    // 🔊 bus LECTEUR (réapplique le mix sur Exo)
    LaunchedEffect(Unit) {
        AudioEngine.reapplyMixNow()
    }

    // ✅ "Niveau du titre" appliqué au moteur
    LaunchedEffect(currentTrackUri, currentTrackGainDb) {
        AudioEngine.applyTrackGainDb(currentTrackGainDb)
    }

    LaunchedEffect(currentTrackUri) {
        LiveNoteManager.clear()
        activeLiveNote = null
    }
    LaunchedEffect(isPlaying, currentTrackUri) {
        while (true) {
            activeLiveNote = if (isPlaying) {
                LiveNoteManager.getActiveNote(getPositionMs())
            } else {
                null
            }
            delay(200)
        }
    }

    val lyricsDelayMs = 0L
    var userOffsetMs by remember(currentTrackUri) { mutableStateOf(-100L) }
    var isConcertMode by remember { mutableStateOf(DisplayPrefs.isConcertMode(context)) }

    var lyricsBoxHeightPx by remember { mutableStateOf(0) }
    var currentLrcIndex by remember { mutableStateOf(0) }

    var lastMidiIndex by remember(currentTrackUri) { mutableStateOf(-1) }
    var showLyrics by remember { mutableStateOf(true) }
    var userScrolling by remember { mutableStateOf(false) }

    var durationMs by remember(currentTrackUri) { mutableStateOf(0) }
    var positionMs by remember(currentTrackUri) { mutableStateOf(0) }
    var isDragging by remember(currentTrackUri) { mutableStateOf(false) }
    var dragPosMs by remember(currentTrackUri) { mutableStateOf(0) }


    var hasRequestedPlaylist by remember(currentTrackUri) { mutableStateOf(false) }
    var autoReturnArmed by remember(currentTrackUri) { mutableStateOf(false) }

    LaunchedEffect(currentTrackUri) {
        autoReturnArmed = false
        delay(1500) // laisse Exo stabiliser duration/position après changement de titre
        autoReturnArmed = true
    }
    var isAutoReturnEnabled by remember {
        mutableStateOf(AutoReturnPrefs.isEnabled(context))
    }

    var isEditingLyrics by remember { mutableStateOf(false) }
    var showMixScreen by remember { mutableStateOf(false) }
    LaunchedEffect(closeMixSignal) { showMixScreen = false }

    var rawLyricsText by remember(currentTrackUri) { mutableStateOf("") }
    var editingLines by remember(currentTrackUri) { mutableStateOf<List<LrcLine>>(emptyList()) }
    var currentEditTab by remember { mutableStateOf(0) }

    // 🔁 reload LRC depuis storage
    LaunchedEffect(currentTrackUri) {
        if (currentTrackUri == null) {
            onParsedLinesChange(emptyList())
            rawLyricsText = ""
            editingLines = emptyList()
            return@LaunchedEffect
        }

        val stored = LrcStorage.loadForTrack(context, currentTrackUri)
        if (!stored.isNullOrBlank()) {
            val parsed = parseLrc(stored)
            onParsedLinesChange(parsed)
            rawLyricsText = parsed.joinToString("\n") { it.text }
            editingLines = parsed
        } else {
            rawLyricsText = ""
            editingLines = emptyList()
        }
    }

    fun centerCurrentLineLazy(state: LazyListState) {
        if (parsedLines.isEmpty()) return
        scope.launch {
            val visible = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentLrcIndex }
            if (visible == null) state.scrollToItem(currentLrcIndex)

            val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentLrcIndex }
            if (info != null) {
                val start = state.layoutInfo.viewportStartOffset
                val end = state.layoutInfo.viewportEndOffset
                val bias = ((end - start) * 0.08f).toInt()
                val viewportCenter = (start + end) / 2 - bias

                val itemCenter = info.offset + info.size / 2
                val delta = itemCenter - viewportCenter
                if (abs(delta) > 1) state.scrollBy(delta.toFloat())
            }
        }
    }

    fun seekAndCenter(targetMs: Int, targetIndex: Int) {
        PlaybackCoordinator.onPlayerStart()

        val totalOffsetMs = lyricsDelayMs + userOffsetMs
        val seekPos = (targetMs.toLong() + totalOffsetMs)
            .coerceAtLeast(0L)
            .coerceAtMost(durationMs.toLong())
            .toInt()

        runCatching { seekToMs(seekPos.toLong()) }
        currentLrcIndex = targetIndex.coerceIn(0, max(parsedLines.size - 1, 0))
        positionMs = seekPos

        if (!isPlaying) {
            PlaybackCoordinator.onPlayerStart()
            onIsPlayingChange(true)
        }
        centerCurrentLineLazy(listState)
    }

    // ---------- Suivi lecture + index ligne courante + MIDI + masque ----------
    LaunchedEffect(isPlaying, parsedLines, userOffsetMs, currentTrackUri) {
        while (true) {
            val d = getDurationMs().toInt()
            if (d > 0) durationMs = d

            val p = getPositionMs().toInt()
            if (!isDragging) positionMs = p

            if (parsedLines.isNotEmpty()) {
                val totalOffsetMs = lyricsDelayMs + userOffsetMs
                val posMs = (p.toLong() - totalOffsetMs).coerceAtLeast(0L)

                val taggedIndices = parsedLines.withIndex()
                    .filter { it.value.timeMs > 0L }
                    .map { it.index }

                showLyrics = if (taggedIndices.isEmpty()) {
                    true
                } else {
                    val firstTaggedIndex = taggedIndices.first()
                    val firstTaggedTime = parsedLines[firstTaggedIndex].timeMs
                    posMs >= firstTaggedTime
                }

                val newIndex = taggedIndices.lastOrNull { idx ->
                    parsedLines[idx].timeMs <= posMs
                } ?: -1

                if (newIndex != -1 && newIndex != currentLrcIndex) currentLrcIndex = newIndex

                if (currentTrackUri != null && newIndex != -1 && newIndex != lastMidiIndex) {
                    lastMidiIndex = newIndex
                    MidiCueDispatcher.onActiveLineChanged(trackUri = currentTrackUri, lineIndex = newIndex)
                }
            }

            delay(200)
            if (!isPlaying) delay(200)
        }
    }

    // ---------- Autoswitch playlist (-10s) ----------
    LaunchedEffect(durationMs, positionMs, hasRequestedPlaylist, currentTrackUri, isEditingLyrics, autoReturnArmed) {
        val enabled = AutoReturnPrefs.isEnabled(context)
        if (enabled &&
            autoReturnArmed &&
            !isEditingLyrics &&
            !hasRequestedPlaylist &&
            durationMs > 0 &&
            positionMs > 3_000 &&
            positionMs >= durationMs - 10_000
        ) {
            hasRequestedPlaylist = true
            onRequestShowPlaylist()
        }
    }

    // ---------- Suivi scroll user ----------
    LaunchedEffect(listState) {
        while (true) {
            userScrolling = listState.isScrollInProgress
            delay(80)
        }
    }

    // ---------- Auto-centering ----------
    LaunchedEffect(isPlaying, parsedLines, lyricsBoxHeightPx, currentLrcIndex) {
        if (parsedLines.isEmpty() || lyricsBoxHeightPx == 0) return@LaunchedEffect
        while (true) {
            if (isPlaying && !userScrolling && !isDragging) centerCurrentLineLazy(listState)
            delay(120)
        }
    }

    val backgroundBrush = Brush.verticalGradient(
        listOf(Color(0xFF171717), Color(0xFF101010), Color(0xFF181410))
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        if (isEditingLyrics) {
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

                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                onIsPlayingChange = onIsPlayingChange,
                seekToMs = seekToMs,

                onSaveSortedLines = { sorted ->
                    rawLyricsText = sorted.joinToString("\n") { it.text }
                    editingLines = sorted
                    onParsedLinesChange(sorted)
                    isEditingLyrics = false

                    if (currentTrackUri != null) {
                        runCatching {
                            LrcStorage.saveForTrack(
                                context = context,
                                trackUriString = currentTrackUri,
                                lines = sorted
                            )
                        }
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
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
                            autoReturnEnabled = isAutoReturnEnabled,
                            onToggleAutoReturn = {
                                val newValue = !isAutoReturnEnabled
                                isAutoReturnEnabled = newValue
                                AutoReturnPrefs.setEnabled(context, newValue)
                                hasRequestedPlaylist = false
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
                            },
                            // ✅ ICI EXACTEMENT
                            onAddLiveNote = {
                                // 1) on gèle le timecode ICI (moment précis)
                                noteAnchorMs = getPositionMs()

                                // 2) on pause pendant qu'il écrit
                                wasPlayingBeforeNote = isPlaying
                                if (isPlaying) onIsPlayingChange(false)

                                // 3) on ouvre la popup
                                showAddNoteDialog = true
                            }
                        )

                        Spacer(Modifier.height(8.dp))

                        Box(modifier = Modifier.weight(1f)) {

                            // --- PAROLES (INCHANGÉES) ---
                            LyricsAreaLazy(
                                modifier = Modifier.fillMaxSize(),
                                listState = listState,
                                parsedLines = parsedLines,
                                isConcertMode = isConcertMode,
                                currentLrcIndex = currentLrcIndex,
                                onLyricsBoxHeightChange = { lyricsBoxHeightPx = it },
                                highlightColor = highlightColor,
                                onLineClick = { index, timeMs ->
                                    seekAndCenter(timeMs.toInt(), index)
                                    if (currentTrackUri != null) {
                                        lastMidiIndex = index
                                        MidiCueDispatcher.onActiveLineChanged(
                                            trackUri = currentTrackUri,
                                            lineIndex = index
                                        )
                                    }
                                }
                            )

                            // --- NOTE LIVE (AU-DESSUS DES PAROLES) ---
                            activeLiveNote?.let { note ->
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 10.dp)
                                        .combinedClickable(
                                            onClick = {}, // rien au click simple
                                            onLongClick = {
                                                LiveNoteManager.remove(note)
                                                activeLiveNote = null
                                            }
                                        )
                                        .background(
                                            Color(0xCC000000),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .border(
                                            1.dp,
                                            Color(0x33FFFFFF),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "📝 ${note.text}",
                                        color = Color(0xFFFFC107),
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

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
                                runCatching { seekToMs(safe.toLong()) }
                                positionMs = safe
                            },
                            highlightColor = highlightColor
                        )

                        PlayerControls(
                            isPlaying = isPlaying,
                            onPlayPause = {
                                if (isPlaying) {
                                    AudioEngine.pause(durationMs = 1000L)
                                    scope.launch {
                                        delay(420)
                                        onIsPlayingChange(false)
                                        PlaybackCoordinator.onFillerStart()
                                        runCatching { FillerSoundManager.startFromPlayerPause(context) }
                                    }
                                } else {
                                    if (durationMs > 0) {
                                        PlaybackCoordinator.onPlayerStart()
                                        onIsPlayingChange(true)
                                        centerCurrentLineLazy(listState)
                                    }
                                }
                            },
                            onPrev = {
                                seekToMs(0L)
                                if (!isPlaying) {
                                    PlaybackCoordinator.onPlayerStart()
                                    onIsPlayingChange(true)
                                }
                                centerCurrentLineLazy(listState)
                            },
                            onNext = {
                                val end = max(durationMs - 1, 0)
                                seekToMs(end.toLong())
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
                    onTrackGainChange = { newDb ->
                        onTrackGainChange(newDb)
                        AudioEngine.applyTrackGainDb(newDb)
                    },
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

    // ─────────────────────────────────────────────────────────────
    //  POPUP : ajouter une note LIVE au timecode courant
    // ─────────────────────────────────────────────────────────────
    if (showAddNoteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (wasPlayingBeforeNote) onIsPlayingChange(true)
                noteAnchorMs = null
                wasPlayingBeforeNote = false
                noteDraftText = ""
                showAddNoteDialog = false
            },
            title = { Text("Ajouter une note", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = noteDraftText,
                        onValueChange = { noteDraftText = it },
                        label = { Text("Ex: Solo guitare — Mi mineur") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Durée : ${noteDraftDurationMs / 1000}s",
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { noteDraftDurationMs = 10_000L }) { Text("10s") }
                        FilledTonalButton(onClick = { noteDraftDurationMs = 30_000L }) { Text("30s") }
                        FilledTonalButton(onClick = { noteDraftDurationMs = 60_000L }) { Text("60s") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = noteDraftText.trim()
                    if (text.isNotEmpty()) {
                        val startMs = noteAnchorMs ?: getPositionMs()
                        val note = LiveNote(
                            timeMs = startMs,
                            durationMs = noteDraftDurationMs,
                            text = text
                        )
                        LiveNoteManager.addNote(note)
                        activeLiveNote = note
                    }

                    if (wasPlayingBeforeNote) onIsPlayingChange(true)
                    noteAnchorMs = null
                    wasPlayingBeforeNote = false
                    noteDraftText = ""
                    showAddNoteDialog = false
                }) {
                    Text("OK", color = Color(0xFFFFC107))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (wasPlayingBeforeNote) onIsPlayingChange(true)
                    noteAnchorMs = null
                    wasPlayingBeforeNote = false
                    noteDraftText = ""
                    showAddNoteDialog = false
                }) {
                    Text("Annuler", color = Color(0xFFB0BEC5))
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

@Composable
private fun ReaderHeader(
    isConcertMode: Boolean,
    onToggleConcertMode: () -> Unit,
    autoReturnEnabled: Boolean,
    onToggleAutoReturn: () -> Unit,
    highlightColor: Color,
    onOpenMix: () -> Unit,
    onOpenEditor: () -> Unit,
    onAddLiveNote: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF3A2C24), Color(0xFF4B372A), Color(0xFF3A2C24))
                ),
                shape = RoundedCornerShape(10.dp)
            )
            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
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

            IconButton(onClick = onToggleAutoReturn) {
                Text(
                    text = "-10",
                    color = if (autoReturnEnabled) Color.White else Color(0xFF888888),
                    fontSize = 12.sp
                )
            }

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

            // ✅ bouton note LIVE
            IconButton(onClick = onAddLiveNote) {
                Text("📝", color = Color.White, fontSize = 16.sp)
            }
            // ✅ bouton TEST BLE (fait clignoter la LED bleue du WIDI)
            IconButton(
                onClick = {
                    MidiOutput.sendBleBlinkTest(channel = 1)
                }
            ) {
                Text(
                    "BLE",
                    color = Color.Cyan,
                    fontSize = 12.sp
                )
            }
        }
    }
}