@file:OptIn(androidx.media3.common.util.UnstableApi::class,
androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.patrick.lrcreader.ui

import android.util.Log
import android.provider.MediaStore
import java.io.File
import android.provider.DocumentsContract
import android.net.Uri
import com.patrick.lrcreader.core.readSyltAsLrcFromUri
import com.patrick.lrcreader.core.readUsltFromUri
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.AutoReturnPrefs
import com.patrick.lrcreader.core.DisplayPrefs
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.MidiCueDispatcher
// ✅ On retire l’import pour éviter tout auto-import douteux
// import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.audio.AudioEngine
import com.patrick.lrcreader.core.audio.SoundTouchBridge
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

    val hqAvailable = remember { SoundTouchBridge.isAvailable() }
    var timeStretchMode by remember { mutableStateOf(AudioEngine.getTimeStretchMode()) }
    val timeStretchLabel = remember(timeStretchMode, hqAvailable) {
        when {
            !hqAvailable -> "TS=EXO (HQ OFF)"
            timeStretchMode == AudioEngine.TimeStretchMode.HQ -> "TS=HQ"
            else -> "TS=EXO"
        }
    }


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
    LaunchedEffect(Unit) {
        AudioEngine.setTimeStretchMode(timeStretchMode, reason = "ui:init")
        timeStretchMode = AudioEngine.getTimeStretchMode()
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
            delay(200L)
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
        delay(1500L) // laisse Exo stabiliser duration/position après changement de titre
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

    // 🔁 reload paroles (priorité : SYLT -> EDIT (LrcStorage) -> SIDECAR -> USLT)
    LaunchedEffect(currentTrackUri) {
        if (currentTrackUri == null) {
            onParsedLinesChange(emptyList())
            rawLyricsText = ""
            editingLines = emptyList()
            return@LaunchedEffect
        }

        val trackUri = runCatching { Uri.parse(currentTrackUri) }.getOrNull()
        Log.d("LrcDebug", "TRACK uriString=$currentTrackUri")
        Log.d("LrcDebug", "TRACK uriParsed=$trackUri scheme=${trackUri?.scheme} authority=${trackUri?.authority}")

        // 1) SYLT (synchronisé) -> LRC
        val syltLrcText: String? = if (trackUri != null) {
            runCatching { readSyltAsLrcFromUri(context, trackUri) }.getOrNull()
        } else null

        if (!syltLrcText.isNullOrBlank()) {
            val parsed = parseLrc(syltLrcText)
            onParsedLinesChange(parsed)
            rawLyricsText = parsed.joinToString("\n") { it.text }
            editingLines = parsed
            return@LaunchedEffect
        }

        // 2) ✅ PRIORITÉ : paroles éditées (LrcStorage)
        val stored = LrcStorage.loadForTrack(context, currentTrackUri)
        Log.d("LrcDebug", "STORED found=${!stored.isNullOrBlank()}")
        if (!stored.isNullOrBlank()) {
            val parsed = parseLrc(stored)
            onParsedLinesChange(parsed)
            rawLyricsText = parsed.joinToString("\n") { it.text }
            editingLines = parsed
            return@LaunchedEffect
        }

        // 3) Sidecar .lrc (voisin du MP3 / SAF / MediaStore)
        val sidecarLrcText: String? = if (trackUri != null) {
            runCatching { readSidecarLrcSmart(context, trackUri) }.getOrNull()
        } else null

        Log.d("LrcDebug", "SIDECAR found=${!sidecarLrcText.isNullOrBlank()}")
        if (!sidecarLrcText.isNullOrBlank()) {
            val parsed = parseLrc(sidecarLrcText)
            onParsedLinesChange(parsed)
            rawLyricsText = parsed.joinToString("\n") { it.text }
            editingLines = parsed
            return@LaunchedEffect
        }

        // 4) USLT (non synchronisé)
        val usltText: String? = if (trackUri != null) {
            runCatching { readUsltFromUri(context, trackUri) }.getOrNull()
        } else null

        if (!usltText.isNullOrBlank()) {
            val lines = usltText
                .replace("\r\n", "\n")
                .split("\n")
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
                .map { LrcLine(timeMs = 0L, text = it) }

            onParsedLinesChange(lines)
            rawLyricsText = lines.joinToString("\n") { it.text }
            editingLines = lines
            return@LaunchedEffect
        }

        onParsedLinesChange(emptyList())
        rawLyricsText = ""
        editingLines = emptyList()
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
                    MidiCueDispatcher.onActiveLineChanged(
                        trackUri = currentTrackUri,
                        lineIndex = newIndex,
                        positionMs = getPositionMs()
                    )
                }
            }

            // ✅ Suivi lecture réelle : déclenche "joué + fin de liste" après 10s
// ⚠️ IMPORTANT : si tu as plusieurs overloads de onPlaybackTick(), on force l'appel avec des named args
            // ✅ Suivi lecture réelle : déclenche "joué + fin de liste" après 10s
            com.patrick.lrcreader.core.PlaylistRepository.onPlaybackTick(isPlaying)


            delay(200L)
            if (!isPlaying) delay(200L)
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
        listOf(
            Color(0xFF0B0B0B), // noir très foncé
            Color(0xFF070707), // encore plus sombre
            Color(0xFF0B0B0B)  // légère variation (pas de “gris sale”)
        )
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
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
                            timeStretchLabel = timeStretchLabel,
                            onToggleTimeStretch = {
                                val next = when (timeStretchMode) {
                                    AudioEngine.TimeStretchMode.EXO -> AudioEngine.TimeStretchMode.HQ
                                    AudioEngine.TimeStretchMode.HQ -> AudioEngine.TimeStretchMode.EXO
                                }
                                AudioEngine.setTimeStretchMode(next, reason = "ui")
                                timeStretchMode = AudioEngine.getTimeStretchMode()
                            },
                            timeStretchEnabled = hqAvailable,
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
                            onAddLiveNote = {
                                noteAnchorMs = getPositionMs()
                                wasPlayingBeforeNote = isPlaying
                                if (isPlaying) onIsPlayingChange(false)
                                showAddNoteDialog = true
                            },
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
                                            lineIndex = index,
                                            positionMs = getPositionMs()
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
    timeStretchLabel: String,
    onToggleTimeStretch: () -> Unit,
    timeStretchEnabled: Boolean,
    highlightColor: Color,
    onOpenMix: () -> Unit,
    onOpenEditor: () -> Unit,
    onAddLiveNote: () -> Unit,
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

            TextButton(
                onClick = onToggleTimeStretch,
                enabled = timeStretchEnabled
            ) {
                Text(
                    text = timeStretchLabel,
                    color = if (timeStretchEnabled) Color(0xFFB3E5FC) else Color(0xFF90A4AE),
                    fontSize = 10.sp
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

            IconButton(onClick = onAddLiveNote) {
                Text("📝", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

private fun readSidecarLrcNearTrack(context: android.content.Context, trackUri: Uri): String? {
    // trackUri = content://com.android.externalstorage.documents/tree/.../document/...
    val docId = runCatching { DocumentsContract.getDocumentId(trackUri) }.getOrNull() ?: return null

    // docId ressemble à: primary:Documents/SPL_Music/BackingTracks/audio/RED RED WINE-31.wav
    val slash = docId.lastIndexOf('/')
    if (slash <= 0) return null

    val parentDocId = docId.substring(0, slash)
    val fileName = docId.substring(slash + 1)
    val baseName = fileName.substringBeforeLast('.', fileName).trim()

    if (baseName.isBlank()) return null

    // On cherche un .lrc avec le même nom de base
    val targetLrcName = "$baseName.lrc"

    val cr = context.contentResolver

    // ✅ IMPORTANT : childrenUri doit être construit avec le TREE uri (trackUri), pas avec parentUri
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        trackUri,
        parentDocId
    )

    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME
    )

    cr.query(childrenUri, projection, null, null, null)?.use { c ->
        val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

        while (c.moveToNext()) {
            val childId = c.getString(idCol)
            val childName = (c.getString(nameCol) ?: "")

            if (childName.equals(targetLrcName, ignoreCase = true)) {
                // ✅ Pour ouvrir le fichier, on reconstruit une URI document via le TREE
                val lrcUri = DocumentsContract.buildDocumentUriUsingTree(trackUri, childId)
                return cr.openInputStream(lrcUri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
            }
        }
    }

    // 🔥 NOUVEAU fallback : si le .lrc n’est pas "à côté", on tente SPL_Music/BackingTracks/lyrics
    return readLrcFromSplLyricsByBaseName(context, baseName)
}

private fun readSidecarLrcSmart(context: android.content.Context, trackUri: Uri): String? {
    // 1) Si c’est un SAF tree (DocumentsContract) : on utilise la méthode SAF
    val isDoc = runCatching { DocumentsContract.isDocumentUri(context, trackUri) }.getOrDefault(false)
    if (isDoc) {
        // readSidecarLrcNearTrack inclut maintenant le fallback SPL_Music/lyrics
        return readSidecarLrcNearTrack(context, trackUri)
    }

    // 2) Si c’est un MediaStore content://media/... : essayer d’obtenir un chemin fichier
    if (trackUri.scheme == "content" && trackUri.authority == MediaStore.AUTHORITY) {
        val path = queryMediaStoreDataPath(context, trackUri)
        if (!path.isNullOrBlank()) {
            // readSidecarFromFilePath inclut maintenant le fallback SPL_Music/lyrics
            return readSidecarFromFilePath(context, path)
        }
        return null
    }

    // 3) Si c’est file://... : chemin direct
    if (trackUri.scheme == "file") {
        val path = trackUri.path
        if (!path.isNullOrBlank()) return readSidecarFromFilePath(context, path)
    }

    return null
}

/**
 * Ancien comportement : chercher "<base>.lrc" dans le même dossier que l’audio.
 * 🔥 NOUVEAU : si pas trouvé, on tente SPL_Music/BackingTracks/lyrics et /Lyrics.
 */
private fun readSidecarFromFilePath(context: android.content.Context, mp3Path: String): String? {
    val mp3File = File(mp3Path)
    if (!mp3File.exists()) return null

    val base = mp3File.nameWithoutExtension.trim()
    if (base.isBlank()) return null

    val lrcFile = File(mp3File.parentFile, "$base.lrc")
    if (lrcFile.exists() && lrcFile.isFile) {
        return runCatching { lrcFile.readText(Charsets.UTF_8) }.getOrNull()
    }

    // 🔥 fallback SPL_Music/BackingTracks/lyrics
    return readLrcFromSplLyricsByBaseName(context, base)
}

/**
 * 🔥 NOUVEAU : lecture .lrc dans
 * /Android/data/<package>/files/SPL_Music/BackingTracks/lyrics/<base>.lrc
 * + fallback /Lyrics (différence de casse)
 *
 * But : vieux téléphone => tu stockes les .lrc séparés dans BackingTracks/lyrics.
 * Aucun impact sur le téléphone concert si ce dossier n’existe pas.
 */
private fun readLrcFromSplLyricsByBaseName(
    context: android.content.Context,
    baseName: String
): String? {
    val b = baseName.trim()
    if (b.isBlank()) return null

    val root = File(context.getExternalFilesDir(null), "SPL_Music")
    val backingTracks = File(root, "BackingTracks")

    val candidates = listOf(
        File(File(backingTracks, "lyrics"), "$b.lrc"),
        File(File(backingTracks, "Lyrics"), "$b.lrc")
    )

    for (f in candidates) {
        if (f.exists() && f.isFile) {
            return runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
        }
    }
    return null
}

private fun queryMediaStoreDataPath(context: android.content.Context, uri: Uri): String? {
    // ⚠️ Android 10+ : DATA peut être null selon le stockage / permissions.
    val projection = arrayOf(MediaStore.MediaColumns.DATA)
    return runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            val col = c.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (col == -1) return@use null
            if (!c.moveToFirst()) return@use null
            c.getString(col)
        }
    }.getOrNull()
}
