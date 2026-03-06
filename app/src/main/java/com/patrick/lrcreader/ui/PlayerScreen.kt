@file:OptIn(androidx.media3.common.util.UnstableApi::class,
androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.patrick.lrcreader.ui

import android.util.Log
import android.widget.Toast
import android.provider.MediaStore
import java.io.File
import android.provider.DocumentsContract
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.readSyltAsLrcFromUri
import com.patrick.lrcreader.core.readUsltFromUri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.core.AutoReturnPrefs
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.DisplayPrefs
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.LyricsViewMode
import com.patrick.lrcreader.core.MidiCueDispatcher
// ✅ On retire l’import pour éviter tout auto-import douteux
// import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.audio.AudioEngine
import com.patrick.lrcreader.core.audio.SoundTouchBridge
import com.patrick.lrcreader.core.findActiveLrcIndex
import com.patrick.lrcreader.core.parseLrc
import com.patrick.lrcreader.core.resolveChordsLookupFileName
import com.patrick.lrcreader.core.resolveLyricsViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    lyricsLoading: Boolean,
    onParsedLinesChange: (List<LrcLine>) -> Unit,
    highlightColor: Color = Color(0xFFE040FB),
    currentTrackUri: String?,
    nextTrackTitle: String? = null,
    currentTrackGainDb: Int,
    onTrackGainChange: (Int) -> Unit,
    onTrackGainCommit: (Int) -> Unit,
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
    val isLaboBuild = remember(context.packageName) { context.packageName.endsWith(".labo") }
    val hqStatus = remember { SoundTouchBridge.logStatusOnce(reason = "PlayerScreen:init") }
    val isHqAvailable = hqStatus.available
    val showHqOffBanner = isLaboBuild && !isHqAvailable
    var hqToastShownAtMs by remember { mutableStateOf(0L) }
    val sHqUnavailable = stringResource(R.string.player_hq_unavailable)

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
        if (isHqAvailable) {
            AudioEngine.setTimeStretchMode(AudioEngine.TimeStretchMode.HQ, reason = "ui:init:HQ_ONLY")
        } else {
            AudioEngine.setTimeStretchMode(AudioEngine.TimeStretchMode.EXO, reason = "ui:init:HQ_UNAVAILABLE")
        }
    }
    LaunchedEffect(showHqOffBanner) {
        if (showHqOffBanner) {
            SoundTouchBridge.logLaboUnavailableOnce(reason = "PlayerScreen:laboBanner")
        }
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
    var selectedViewMode by remember(currentTrackUri) { mutableStateOf(LyricsViewMode.LYRICS) }
    var parsedChordLines by remember(currentTrackUri) { mutableStateOf<List<LrcLine>>(emptyList()) }
    var chordsLoading by remember(currentTrackUri) { mutableStateOf(false) }
    var hasLyricsSource by remember(currentTrackUri) { mutableStateOf(false) }
    var hasChordsSource by remember(currentTrackUri) { mutableStateOf(false) }
    var resolvedLyricsLrcFileName by remember(currentTrackUri) { mutableStateOf<String?>(null) }

    var lyricsBoxHeightPx by remember { mutableStateOf(0) }
    var currentLrcIndex by remember { mutableStateOf(0) }

    var lastMidiIndex by remember(currentTrackUri) { mutableStateOf(-1) }
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
    var editingTargetMode by remember(currentTrackUri) { mutableStateOf(LyricsViewMode.LYRICS) }
    var editingResolvedLrcFileName by remember(currentTrackUri) { mutableStateOf<String?>(null) }
    var showMixScreen by remember { mutableStateOf(false) }
    LaunchedEffect(closeMixSignal) { showMixScreen = false }

    var rawLyricsText by remember(currentTrackUri) { mutableStateOf("") }
    var editingLines by remember(currentTrackUri) { mutableStateOf<List<LrcLine>>(emptyList()) }
    var editingLinesDirty by remember(currentTrackUri) { mutableStateOf(false) }
    var currentEditTab by remember { mutableStateOf(0) }
    val inlineLrcTimeTagRegex = remember { Regex("""\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?]""") }

    fun updateResolvedLyricsFileName(newValue: String?, reason: String) {
        Log.d(
            "LrcDebug",
            "RESOLVED_LYRICS_FILENAME_UPDATE reason=$reason old=$resolvedLyricsLrcFileName new=$newValue"
        )
        resolvedLyricsLrcFileName = newValue
    }

    fun plainLyricsText(lines: List<LrcLine>): String =
        lines.joinToString("\n") { line -> line.text.replace(inlineLrcTimeTagRegex, "").trim() }

    fun seedEditingLinesIfBetter(lines: List<LrcLine>) {
        if (editingLinesDirty) return
        val currentHasTags = editingLines.any { it.timeMs > 0L }
        val incomingHasTags = lines.any { it.timeMs > 0L }

        val shouldSeed = editingLines.isEmpty() || (!currentHasTags && incomingHasTags)
        if (shouldSeed) {
            editingLines = lines
        }
    }

    // 🔁 reload paroles (priorité : SYLT -> EDIT (LrcStorage) -> SIDECAR -> USLT)
    LaunchedEffect(currentTrackUri) {
        if (isEditingLyrics) return@LaunchedEffect
        if (currentTrackUri == null) {
            onParsedLinesChange(emptyList())
            rawLyricsText = ""
            editingLines = emptyList()
            hasLyricsSource = false
            updateResolvedLyricsFileName(null, "track=null")
            return@LaunchedEffect
        }

        val trackUri = runCatching { Uri.parse(currentTrackUri) }.getOrNull()
        val audioBase = baseNameFromTrackUriString(currentTrackUri)
        Log.d("LrcDebug", "TRACK uriString=$currentTrackUri")
        Log.d("LrcDebug", "TRACK uriParsed=$trackUri scheme=${trackUri?.scheme} authority=${trackUri?.authority}")
        Log.d("LrcDebug", "TRACK audioBaseName=$audioBase")

        // 1) SYLT (synchronisé) -> LRC
        val syltLrcText: String? = if (trackUri != null) {
            withContext(Dispatchers.IO) {
                runCatching { readSyltAsLrcFromUri(context, trackUri) }.getOrNull()
            }
        } else null

        if (!syltLrcText.isNullOrBlank()) {
            val parsed = parseLrc(syltLrcText)
            onParsedLinesChange(parsed)
            rawLyricsText = parsed.joinToString("\n") { it.text }
            seedEditingLinesIfBetter(parsed)
            hasLyricsSource = true
            updateResolvedLyricsFileName(null, "source=SYLT")
            return@LaunchedEffect
        }

        // 2) ✅ PRIORITÉ : paroles éditées (LrcStorage)
        val stored = withContext(Dispatchers.IO) {
            LrcStorage.loadForTrack(context, currentTrackUri)
        }
        Log.d("LrcDebug", "STORED found=${!stored.isNullOrBlank()}")
        if (!stored.isNullOrBlank()) {
            val storedOrigin = withContext(Dispatchers.IO) {
                runCatching { LrcStorage.resolveOriginForTrack(context, currentTrackUri) }.getOrNull()
            }
            Log.d(
                "LrcDebug",
                "LYRICS_SOURCE source=LRC_STORAGE origin=${storedOrigin?.source} fileName=${storedOrigin?.fileName} path=${storedOrigin?.debugPath}"
            )
            val parsed = parseLrc(stored)
            onParsedLinesChange(parsed)
            rawLyricsText = parsed.joinToString("\n") { it.text }
            seedEditingLinesIfBetter(parsed)
            hasLyricsSource = true
            updateResolvedLyricsFileName(
                storedOrigin?.fileName,
                reason = "source=LRC_STORAGE path=${storedOrigin?.debugPath}"
            )
            return@LaunchedEffect
        }

        // 3) Sidecar .lrc (voisin du MP3 / SAF / MediaStore)
        val sidecarLrcResult: LrcTextWithFileName? = if (trackUri != null) {
            withContext(Dispatchers.IO) {
                runCatching { readSidecarLrcSmart(context, trackUri) }.getOrNull()
            }
        } else null

        Log.d("LrcDebug", "SIDECAR found=${sidecarLrcResult != null}")
        if (sidecarLrcResult != null) {
            Log.d(
                "LrcDebug",
                "LYRICS_SOURCE source=SIDECAR fileName=${sidecarLrcResult.fileName} path=${sidecarLrcResult.debugPath}"
            )
            val parsed = if (sidecarLrcResult.text.isNotBlank()) parseLrc(sidecarLrcResult.text) else emptyList()
            onParsedLinesChange(parsed)
            rawLyricsText = parsed.joinToString("\n") { it.text }
            seedEditingLinesIfBetter(parsed)
            hasLyricsSource = true
            updateResolvedLyricsFileName(
                sidecarLrcResult.fileName,
                reason = "source=SIDECAR path=${sidecarLrcResult.debugPath}"
            )
            return@LaunchedEffect
        }

        // 4) USLT (non synchronisé)
        val usltText: String? = if (trackUri != null) {
            withContext(Dispatchers.IO) {
                runCatching { readUsltFromUri(context, trackUri) }.getOrNull()
            }
        } else null

        if (!usltText.isNullOrBlank()) {
            val parsed = parseLrc(usltText)
            onParsedLinesChange(parsed)
            rawLyricsText = parsed.joinToString("\n") { it.text }
            seedEditingLinesIfBetter(parsed)
            hasLyricsSource = true
            updateResolvedLyricsFileName(null, "source=USLT")
            return@LaunchedEffect
        }

        onParsedLinesChange(emptyList())
        rawLyricsText = ""
        seedEditingLinesIfBetter(emptyList())
        hasLyricsSource = false
        Log.d("LrcDebug", "LYRICS_SOURCE source=NONE")
        updateResolvedLyricsFileName(null, "source=NONE")
    }

    // 🔁 reload accords dédiés (BackingTracks/Accords/<base>.lrc)
    LaunchedEffect(currentTrackUri, resolvedLyricsLrcFileName, selectedViewMode) {
        if (currentTrackUri == null) {
            parsedChordLines = emptyList()
            hasChordsSource = false
            chordsLoading = false
            return@LaunchedEffect
        }
        Log.d(
            "LrcDebug",
            "ACCORDS_EFFECT_START uri=$currentTrackUri resolvedLyricsLrcFileName=$resolvedLyricsLrcFileName"
        )
        chordsLoading = true
        var raw = withContext(Dispatchers.IO) {
            readAccordsFromSplByTrackUri(
                context = context,
                trackUriString = currentTrackUri,
                preferredLrcFileName = resolvedLyricsLrcFileName
            )
        }
        if (raw == null && selectedViewMode == LyricsViewMode.CHORDS) {
            val created = withContext(Dispatchers.IO) {
                ensureAccordsFileExistsForTrack(
                    context = context,
                    trackUriString = currentTrackUri,
                    preferredLrcFileName = resolvedLyricsLrcFileName
                )
            }
            if (created) {
                raw = ""
            }
        }
        val parsed = if (!raw.isNullOrBlank()) parseLrc(raw) else emptyList()
        parsedChordLines = parsed
        hasChordsSource = raw != null
        Log.d(
            "LrcDebug",
            "ACCORDS_EFFECT_DONE uri=$currentTrackUri parsedCount=${parsed.size} hasChordsSource=$hasChordsSource"
        )
        chordsLoading = false
    }

    val activeDisplayLines = if (selectedViewMode == LyricsViewMode.CHORDS) parsedChordLines else parsedLines
    val hasLyricsMode = hasLyricsSource || parsedLines.isNotEmpty()
    val hasChordsMode = hasChordsSource || parsedChordLines.isNotEmpty()
    val showViewToggle = hasLyricsMode || hasChordsMode
    val canSelectChordsMode = hasChordsMode || hasLyricsMode

    fun recomputeCurrentIndexForActiveView() {
        if (activeDisplayLines.isEmpty()) {
            currentLrcIndex = 0
            return
        }
        val totalOffsetMs = lyricsDelayMs + userOffsetMs
        val effectivePos = (getPositionMs() - totalOffsetMs).coerceAtLeast(0L)
        val idx = findActiveLrcIndex(activeDisplayLines, effectivePos)
        currentLrcIndex = if (idx >= 0) idx else 0
    }

    LaunchedEffect(hasLyricsMode, hasChordsMode, currentTrackUri) {
        selectedViewMode = resolveLyricsViewMode(
            current = selectedViewMode,
            hasLyrics = hasLyricsMode,
            hasChords = hasChordsMode
        )
    }

    LaunchedEffect(selectedViewMode, parsedLines, parsedChordLines, userOffsetMs) {
        if (selectedViewMode != LyricsViewMode.LYRICS) {
            lastMidiIndex = -1
        }
        recomputeCurrentIndexForActiveView()
    }

    fun centerCurrentLineLazy(state: LazyListState) {
        if (selectedViewMode != LyricsViewMode.LYRICS) return
        if (activeDisplayLines.isEmpty()) return
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
        currentLrcIndex = targetIndex.coerceIn(0, max(activeDisplayLines.size - 1, 0))
        positionMs = seekPos

        if (!isPlaying) {
            PlaybackCoordinator.onPlayerStart()
            onIsPlayingChange(true)
        }
        centerCurrentLineLazy(listState)
    }


    // ---------- Suivi lecture + index ligne courante + MIDI ----------
    LaunchedEffect(isPlaying, activeDisplayLines, selectedViewMode, userOffsetMs, currentTrackUri) {
        while (true) {
            val d = getDurationMs().toInt()
            if (d > 0) durationMs = d

            val p = getPositionMs().toInt()
            if (!isDragging) positionMs = p

            if (activeDisplayLines.isNotEmpty()) {
                val totalOffsetMs = lyricsDelayMs + userOffsetMs
                val posMs = (p.toLong() - totalOffsetMs).coerceAtLeast(0L)
                val newIndex = findActiveLrcIndex(activeDisplayLines, posMs)
                if (newIndex >= 0 && newIndex != currentLrcIndex) {
                    currentLrcIndex = newIndex
                }

                if (
                    selectedViewMode == LyricsViewMode.LYRICS &&
                    currentTrackUri != null &&
                    newIndex >= 0 &&
                    newIndex != lastMidiIndex
                ) {
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
    LaunchedEffect(isPlaying, activeDisplayLines, selectedViewMode, lyricsBoxHeightPx, currentLrcIndex) {
        if (selectedViewMode != LyricsViewMode.LYRICS) return@LaunchedEffect
        if (activeDisplayLines.isEmpty() || lyricsBoxHeightPx == 0) return@LaunchedEffect
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
                onEditingLinesChange = {
                    editingLines = it
                    editingLinesDirty = true
                },
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
                    if (editingTargetMode == LyricsViewMode.LYRICS) {
                        onParsedLinesChange(sorted)
                        hasLyricsSource = sorted.isNotEmpty()
                    } else {
                        parsedChordLines = sorted
                        hasChordsSource = sorted.isNotEmpty()
                        if (selectedViewMode == LyricsViewMode.CHORDS) {
                            recomputeCurrentIndexForActiveView()
                        }
                    }
                    isEditingLyrics = false
                },
                onImportedLinesApplied = { imported ->
                    onParsedLinesChange(imported)
                },
                onPersistLines = { lines ->
                    currentTrackUri?.let { trackUri ->
                        runCatching {
                            if (editingTargetMode == LyricsViewMode.LYRICS) {
                                LrcStorage.saveForTrack(
                                    context = context,
                                    trackUriString = trackUri,
                                    lines = lines
                                )
                                val resolvedFileName = resolveExactLrcFileNameForTrack(
                                    context = context,
                                    trackUriString = trackUri,
                                    preferredLrcFileName = resolvedLyricsLrcFileName
                                )
                                if (resolvedFileName.isNotBlank()) {
                                    updateResolvedLyricsFileName(
                                        resolvedFileName,
                                        "source=LYRICS_SAVE"
                                    )
                                    ensureAccordsFileExistsForTrack(
                                        context = context,
                                        trackUriString = trackUri,
                                        preferredLrcFileName = resolvedFileName
                                    )
                                }
                            } else {
                                parsedChordLines = lines
                                hasChordsSource = lines.isNotEmpty()
                                if (selectedViewMode == LyricsViewMode.CHORDS) {
                                    recomputeCurrentIndexForActiveView()
                                }
                                val writtenName = writeAccordsToSplByTrackUri(
                                    context = context,
                                    trackUriString = trackUri,
                                    preferredLrcFileName = resolvedLyricsLrcFileName,
                                    lines = lines
                                )
                                if (!writtenName.isNullOrBlank()) {
                                    ensureLyricsFileExistsForTrack(
                                        context = context,
                                        trackUriString = trackUri,
                                        preferredLrcFileName = writtenName
                                    )
                                }
                            }
                        }
                    }
                },
                onDeletePersisted = {
                    currentTrackUri?.let { trackUri ->
                        runCatching {
                            if (editingTargetMode == LyricsViewMode.LYRICS) {
                                LrcStorage.deleteForTrack(context, trackUri)
                            } else {
                                deleteAccordsFromSplByTrackUri(
                                    context = context,
                                    trackUriString = trackUri,
                                    preferredLrcFileName = resolvedLyricsLrcFileName
                                )
                            }
                        }
                    }
                },
                showImportButton = editingTargetMode == LyricsViewMode.LYRICS,
                mainTabLabelRes = if (editingTargetMode == LyricsViewMode.LYRICS) {
                    R.string.lyrics_editor_tab_lyrics
                } else {
                    R.string.player_view_chords
                },
                inputLabelRes = if (editingTargetMode == LyricsViewMode.LYRICS) {
                    R.string.lyrics_editor_input_label
                } else {
                    R.string.chords_editor_input_label
                },
                enableCueEditing = editingTargetMode == LyricsViewMode.LYRICS,
                showChordPalette = editingTargetMode == LyricsViewMode.CHORDS,
                chordPaletteStorageKey = if (editingTargetMode == LyricsViewMode.CHORDS) {
                    editingResolvedLrcFileName
                } else {
                    null
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
                        if (showHqOffBanner) {
                            HqOffBanner()
                            Spacer(Modifier.height(8.dp))
                        }

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
                            showEditLyrics = selectedViewMode == LyricsViewMode.LYRICS ||
                                selectedViewMode == LyricsViewMode.CHORDS,
                            onOpenEditor = {
                                editingTargetMode = selectedViewMode
                                editingResolvedLrcFileName = currentTrackUri?.let { trackUri ->
                                    resolveExactLrcFileNameForTrack(
                                        context = context,
                                        trackUriString = trackUri,
                                        preferredLrcFileName = resolvedLyricsLrcFileName
                                    )
                                }
                                val sourceLines = if (editingTargetMode == LyricsViewMode.CHORDS) {
                                    parsedChordLines
                                } else {
                                    parsedLines
                                }
                                if (sourceLines.isNotEmpty()) {
                                    rawLyricsText = plainLyricsText(sourceLines)
                                    editingLines = sourceLines
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

                        if (!nextTrackTitle.isNullOrBlank()) {
                            val blinkTransition = rememberInfiniteTransition(label = "nextTrackBlink")
                            val blinkAlpha by blinkTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 0.35f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(
                                        durationMillis = 300,
                                        easing = FastOutSlowInEasing
                                    ),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "nextTrackBlinkAlpha"
                            )
                            Text(
                                text = stringResource(R.string.player_next_track, nextTrackTitle),
                                color = Color(0xFFEF9A9A),
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .padding(start = 2.dp, top = 4.dp)
                                    .alpha(blinkAlpha)
                            )
                        }

                        if (showViewToggle) {
                            LyricsViewSelector(
                                selectedMode = selectedViewMode,
                                hasLyrics = hasLyricsMode,
                                hasChords = canSelectChordsMode,
                                onSelectMode = { mode ->
                                    selectedViewMode = mode
                                    recomputeCurrentIndexForActiveView()
                                    if (mode == LyricsViewMode.LYRICS) {
                                        centerCurrentLineLazy(listState)
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (selectedViewMode == LyricsViewMode.LYRICS) {
                                val safeLrcIndex = currentLrcIndex
                                    .coerceIn(0, (parsedLines.size - 1).coerceAtLeast(0))
                                LyricsAreaLazy(
                                    modifier = Modifier.fillMaxSize(),
                                    listState = listState,
                                    parsedLines = parsedLines,
                                    currentTrackUri = currentTrackUri,
                                    lyricsLoading = lyricsLoading,
                                    isConcertMode = isConcertMode,
                                    currentLrcIndex = safeLrcIndex,
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
                            } else {
                                val safeChordIndex = currentLrcIndex
                                    .coerceIn(0, (parsedChordLines.size - 1).coerceAtLeast(0))
                                AccordsArea(
                                    modifier = Modifier.fillMaxSize(),
                                    parsedLines = parsedChordLines,
                                    currentTrackUri = currentTrackUri,
                                    loading = chordsLoading,
                                    currentLrcIndex = safeChordIndex
                                )
                            }

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
                                        text = stringResource(R.string.player_live_note_chip, note.text),
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
                    onTrackGainChange = onTrackGainChange,
                    onTrackGainCommit = onTrackGainCommit,
                    tempo = tempo,
                    onTempoChange = { newTempo ->
                        if (!isHqAvailable) {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - hqToastShownAtMs > 1200L) {
                                Toast.makeText(context, sHqUnavailable, Toast.LENGTH_SHORT).show()
                                hqToastShownAtMs = now
                            }
                            return@TrackMixScreen
                        }
                        onTempoChange(newTempo)
                    },
                    pitchSemi = pitchSemi,
                    onPitchSemiChange = { newSemi ->
                        if (!isHqAvailable) {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - hqToastShownAtMs > 1200L) {
                                Toast.makeText(context, sHqUnavailable, Toast.LENGTH_SHORT).show()
                                hqToastShownAtMs = now
                            }
                            return@TrackMixScreen
                        }
                        onPitchSemiChange(newSemi)
                    },
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
            title = { Text(stringResource(R.string.player_add_note_title), color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = noteDraftText,
                        onValueChange = { noteDraftText = it },
                        label = { Text(stringResource(R.string.player_add_note_label_hint)) },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(
                            R.string.player_note_duration_seconds,
                            (noteDraftDurationMs / 1000L).toInt()
                        ),
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { noteDraftDurationMs = 10_000L }) {
                            Text(stringResource(R.string.player_note_preset_10s))
                        }
                        FilledTonalButton(onClick = { noteDraftDurationMs = 30_000L }) {
                            Text(stringResource(R.string.player_note_preset_30s))
                        }
                        FilledTonalButton(onClick = { noteDraftDurationMs = 60_000L }) {
                            Text(stringResource(R.string.player_note_preset_60s))
                        }
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
                    Text(stringResource(R.string.common_ok), color = Color(0xFFFFC107))
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
                    Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

@Composable
private fun HqOffBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFB71C1C), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFFFCDD2), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.player_hq_off_banner),
            color = Color.White,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun LyricsViewSelector(
    selectedMode: LyricsViewMode,
    hasLyrics: Boolean,
    hasChords: Boolean,
    onSelectMode: (LyricsViewMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        if (hasLyrics) {
            FilterChip(
                selected = selectedMode == LyricsViewMode.LYRICS,
                onClick = { onSelectMode(LyricsViewMode.LYRICS) },
                label = { Text(stringResource(R.string.player_view_lyrics)) }
            )
        }
        if (hasLyrics && hasChords) {
            Spacer(Modifier.width(10.dp))
        }
        if (hasChords) {
            FilterChip(
                selected = selectedMode == LyricsViewMode.CHORDS,
                onClick = { onSelectMode(LyricsViewMode.CHORDS) },
                label = { Text(stringResource(R.string.player_view_chords)) }
            )
        }
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
    showEditLyrics: Boolean,
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
                    contentDescription = stringResource(R.string.player_cd_change_style),
                    tint = if (isConcertMode) highlightColor else Color(0xFFCFD8DC)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onToggleAutoReturn) {
                Text(
                    text = stringResource(R.string.player_auto_return_button),
                    color = if (autoReturnEnabled) Color.White else Color(0xFF888888),
                    fontSize = 12.sp
                )
            }

            Text(
                text = stringResource(R.string.player_hq_tag),
                color = Color(0xFFB3E5FC),
                fontSize = 10.sp
            )

            IconButton(onClick = onOpenMix) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = stringResource(R.string.player_cd_track_mix),
                    tint = Color(0xFFFFC107)
                )
            }

            if (showEditLyrics) {
                IconButton(onClick = onOpenEditor) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.player_cd_edit_lyrics),
                        tint = Color(0xFFFFF3E0)
                    )
                }
            }

            IconButton(onClick = onAddLiveNote) {
                Text(stringResource(R.string.player_add_note_icon), color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

private data class LrcTextWithFileName(
    val text: String,
    val fileName: String,
    val debugPath: String? = null
)

private fun readSidecarLrcNearTrack(context: android.content.Context, trackUri: Uri): LrcTextWithFileName? {
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
                val text = cr.openInputStream(lrcUri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: return null
                return LrcTextWithFileName(
                    text = text,
                    fileName = childName,
                    debugPath = lrcUri.toString()
                )
            }
        }
    }

    // 🔥 NOUVEAU fallback : si le .lrc n’est pas "à côté", on tente SPL_Music/BackingTracks/lyrics
    return readLrcFromSplLyricsByBaseNameWithMatch(context, baseName)
}

private fun readSidecarLrcSmart(context: android.content.Context, trackUri: Uri): LrcTextWithFileName? {
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
private fun readSidecarFromFilePath(context: android.content.Context, mp3Path: String): LrcTextWithFileName? {
    val mp3File = File(mp3Path)
    if (!mp3File.exists()) return null

    val base = mp3File.nameWithoutExtension.trim()
    if (base.isBlank()) return null

    val lrcFile = File(mp3File.parentFile, "$base.lrc")
    if (lrcFile.exists() && lrcFile.isFile) {
        val text = runCatching { lrcFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return LrcTextWithFileName(
            text = text,
            fileName = lrcFile.name,
            debugPath = lrcFile.absolutePath
        )
    }

    // 🔥 fallback SPL_Music/BackingTracks/lyrics
    return readLrcFromSplLyricsByBaseNameWithMatch(context, base)
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
    return readLrcFromSplLyricsByBaseNameWithMatch(context, baseName)?.text
}

private fun readLrcFromSplLyricsByBaseNameWithMatch(
    context: android.content.Context,
    baseName: String
): LrcTextWithFileName? {
    return readLrcFromSplFolderByBaseName(
        context = context,
        baseName = baseName,
        folderAliases = listOf("lyrics", "Lyrics"),
        ensureFolderName = "Lyrics"
    )
}

private fun readAccordsFromSplByTrackUri(
    context: android.content.Context,
    trackUriString: String,
    preferredLrcFileName: String?
): String? {
    val fallbackBaseName = baseNameFromTrackUriString(trackUriString)
    Log.d(
        "LrcDebug",
        "ACCORDS_LOOKUP start uri=$trackUriString audioBaseName=$fallbackBaseName preferredLyricsFileName=$preferredLrcFileName"
    )
    val preferredTarget = resolveChordsLookupFileName(
        exactLyricsFileName = preferredLrcFileName,
        fallbackBaseName = fallbackBaseName
    )
    if (preferredTarget.isBlank()) return null
    Log.d("LrcDebug", "ACCORDS_LOOKUP target=$preferredTarget")

    val byPreferred = readLrcFromSplFolderByFileName(
        context = context,
        targetName = preferredTarget,
        folderAliases = listOf("Accords", "accords"),
        ensureFolderName = "Accords",
        debugLookupLabel = "ACCORDS_LOOKUP"
    )
    if (byPreferred != null) return byPreferred.text

    if (!preferredLrcFileName.isNullOrBlank()) {
        Log.d(
            "LrcDebug",
            "ACCORDS_LOOKUP miss preferred=$preferredTarget reason=no_match_for_runtime_resolved_lyrics_filename"
        )
        return null
    }

    if (fallbackBaseName.isBlank()) return null
    Log.d(
        "LrcDebug",
        "ACCORDS_LOOKUP fallback_to_audio_base fallbackBaseName=$fallbackBaseName reason=no_runtime_resolved_lyrics_filename"
    )
    return readLrcFromSplFolderByBaseName(
        context = context,
        baseName = fallbackBaseName,
        folderAliases = listOf("Accords", "accords"),
        ensureFolderName = "Accords",
        debugLookupLabel = "ACCORDS_LOOKUP_FALLBACK"
    )?.text
}

private fun resolveExactLrcFileNameForTrack(
    context: android.content.Context,
    trackUriString: String,
    preferredLrcFileName: String?
): String {
    preferredLrcFileName?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    val fromStorage = runCatching {
        LrcStorage.resolveOriginForTrack(context, trackUriString)?.fileName
    }.getOrNull()
    fromStorage?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    val base = baseNameFromTrackUriString(trackUriString).trim()
    return if (base.isNotBlank()) "$base.lrc" else "track.lrc"
}

private fun ensureAccordsFileExistsForTrack(
    context: android.content.Context,
    trackUriString: String,
    preferredLrcFileName: String?
): Boolean {
    val exactName = resolveExactLrcFileNameForTrack(
        context = context,
        trackUriString = trackUriString,
        preferredLrcFileName = preferredLrcFileName
    )
    return ensureLrcFileExistsInFolder(
        context = context,
        targetName = exactName,
        folderAliases = listOf("Accords", "accords"),
        ensureFolderName = "Accords"
    )
}

private fun ensureLyricsFileExistsForTrack(
    context: android.content.Context,
    trackUriString: String,
    preferredLrcFileName: String?
): Boolean {
    val exactName = resolveExactLrcFileNameForTrack(
        context = context,
        trackUriString = trackUriString,
        preferredLrcFileName = preferredLrcFileName
    )
    return ensureLrcFileExistsInFolder(
        context = context,
        targetName = exactName,
        folderAliases = listOf("Lyrics", "lyrics"),
        ensureFolderName = "Lyrics"
    )
}

private fun ensureLrcFileExistsInFolder(
    context: android.content.Context,
    targetName: String,
    folderAliases: List<String>,
    ensureFolderName: String
): Boolean {
    val exists = readLrcFromSplFolderByFileName(
        context = context,
        targetName = targetName,
        folderAliases = folderAliases,
        ensureFolderName = ensureFolderName
    ) != null
    if (exists) return false
    return writeLrcToSplFolderByFileName(
        context = context,
        targetName = targetName,
        folderAliases = folderAliases,
        ensureFolderName = ensureFolderName,
        text = ""
    )
}

private fun writeAccordsToSplByTrackUri(
    context: android.content.Context,
    trackUriString: String,
    preferredLrcFileName: String?,
    lines: List<LrcLine>
): String? {
    val targetName = resolveExactLrcFileNameForTrack(
        context = context,
        trackUriString = trackUriString,
        preferredLrcFileName = preferredLrcFileName
    )
    if (targetName.isBlank()) return null
    val ok = writeLrcToSplFolderByFileName(
        context = context,
        targetName = targetName,
        folderAliases = listOf("Accords", "accords"),
        ensureFolderName = "Accords",
        text = linesToLrcText(lines)
    )
    return if (ok) targetName else null
}

private fun deleteAccordsFromSplByTrackUri(
    context: android.content.Context,
    trackUriString: String,
    preferredLrcFileName: String?
): Boolean {
    val fallbackBaseName = baseNameFromTrackUriString(trackUriString)
    val candidates = linkedSetOf<String>().apply {
        preferredLrcFileName?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        fallbackBaseName.trim().takeIf { it.isNotBlank() }?.let { add("$it.lrc") }
    }
    if (candidates.isEmpty()) return false
    return deleteLrcFromSplFolderByFileNames(
        context = context,
        fileNames = candidates,
        folderAliases = listOf("Accords", "accords")
    )
}

private fun linesToLrcText(lines: List<LrcLine>): String {
    return lines.joinToString("\n") { line ->
        if (line.timeMs > 0L) {
            val total = line.timeMs
            val mm = (total / 60000).toInt()
            val ss = ((total % 60000) / 1000).toInt()
            val xx = ((total % 1000) / 10).toInt()
            "[%02d:%02d.%02d] %s".format(mm, ss, xx, line.text)
        } else {
            line.text
        }
    }.trim()
}

private fun writeLrcToSplFolderByFileName(
    context: android.content.Context,
    targetName: String,
    folderAliases: List<String>,
    ensureFolderName: String,
    text: String
): Boolean {
    val cleanTargetName = targetName.trim()
    if (cleanTargetName.isBlank()) return false

    val fileRoots = linkedSetOf<File>().apply {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
        if (rootUri?.scheme == "file" && !rootUri.path.isNullOrBlank()) {
            add(File(rootUri.path!!))
        }
        add(File(context.getExternalFilesDir(null), "SPL_Music"))
    }

    for (root in fileRoots) {
        if (!root.exists()) continue
        val backingTracks = File(root, "BackingTracks")
        val backingTrack = File(root, "BackingTrack")
        val backingRoot = when {
            backingTracks.exists() -> backingTracks
            backingTrack.exists() -> backingTrack
            else -> backingTracks
        }
        if (!backingRoot.exists()) backingRoot.mkdirs()
        val targetFolder = folderAliases
            .asSequence()
            .map { File(backingRoot, it) }
            .firstOrNull { it.exists() && it.isDirectory }
            ?: File(backingRoot, ensureFolderName).apply { if (!exists()) mkdirs() }
        val outFile = File(targetFolder, cleanTargetName)
        val ok = runCatching {
            outFile.writeText(text, Charsets.UTF_8)
            true
        }.getOrDefault(false)
        if (ok) return true
    }

    val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
    if (rootUri?.scheme == "content") {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
            ?: DocumentFile.fromSingleUri(context, rootUri)
        if (rootDoc != null && rootDoc.isDirectory) {
            val backingDirs = findDirsByAliases(
                parent = rootDoc,
                aliases = listOf("BackingTracks", "BackingTrack")
            ).toMutableList()
            if (backingDirs.isEmpty()) {
                ensureOrFindDir(
                    parent = rootDoc,
                    preferredName = "BackingTracks",
                    aliases = listOf("BackingTracks", "BackingTrack")
                )?.let { backingDirs.add(it) }
            }

            backingDirs.forEach { backing ->
                val targetFolder = findDirsByAliases(backing, folderAliases).firstOrNull()
                    ?: ensureOrFindDir(
                        parent = backing,
                        preferredName = ensureFolderName,
                        aliases = folderAliases
                    )
                    ?: return@forEach

                val fileDoc = targetFolder.listFiles().firstOrNull { child ->
                    child.isFile && (child.name ?: "").equals(cleanTargetName, ignoreCase = true)
                } ?: targetFolder.createFile("application/octet-stream", cleanTargetName)

                if (fileDoc != null) {
                    val ok = runCatching {
                        context.contentResolver.openOutputStream(fileDoc.uri, "w")?.use { out ->
                            out.write(text.toByteArray(Charsets.UTF_8))
                            out.flush()
                        } != null
                    }.getOrDefault(false)
                    if (ok) return true
                }
            }
        }
    }
    return false
}

private fun deleteLrcFromSplFolderByFileNames(
    context: android.content.Context,
    fileNames: Set<String>,
    folderAliases: List<String>
): Boolean {
    val cleanNames = fileNames.map { it.trim() }.filter { it.isNotBlank() }.toSet()
    if (cleanNames.isEmpty()) return false
    var deleted = false

    val fileRoots = linkedSetOf<File>().apply {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
        if (rootUri?.scheme == "file" && !rootUri.path.isNullOrBlank()) {
            add(File(rootUri.path!!))
        }
        add(File(context.getExternalFilesDir(null), "SPL_Music"))
    }

    for (root in fileRoots) {
        if (!root.exists()) continue
        val backingRoots = linkedSetOf(File(root, "BackingTracks"), File(root, "BackingTrack"))
        for (backingRoot in backingRoots) {
            for (alias in folderAliases) {
                val folder = File(backingRoot, alias)
                for (name in cleanNames) {
                    val candidate = File(folder, name)
                    if (candidate.exists() && candidate.isFile) {
                        deleted = runCatching { candidate.delete() }.getOrDefault(false) || deleted
                    }
                }
            }
        }
    }

    val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
    if (rootUri?.scheme == "content") {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
            ?: DocumentFile.fromSingleUri(context, rootUri)
        if (rootDoc != null && rootDoc.isDirectory) {
            val backingDirs = findDirsByAliases(
                parent = rootDoc,
                aliases = listOf("BackingTracks", "BackingTrack")
            )
            backingDirs.forEach { backing ->
                val targetFolders = findDirsByAliases(backing, folderAliases)
                targetFolders.forEach { targetFolder ->
                    targetFolder.listFiles().forEach { child ->
                        if (child.isFile && cleanNames.any { it.equals(child.name, ignoreCase = true) }) {
                            deleted = runCatching { child.delete() }.getOrDefault(false) || deleted
                        }
                    }
                }
            }
        }
    }

    return deleted
}

private fun readLrcFromSplFolderByBaseName(
    context: android.content.Context,
    baseName: String,
    folderAliases: List<String>,
    ensureFolderName: String? = null,
    debugLookupLabel: String? = null
): LrcTextWithFileName? {
    val b = baseName.trim()
    if (b.isBlank()) return null
    return readLrcFromSplFolderByFileName(
        context = context,
        targetName = "$b.lrc",
        folderAliases = folderAliases,
        ensureFolderName = ensureFolderName,
        debugLookupLabel = debugLookupLabel
    )
}

private fun readLrcFromSplFolderByFileName(
    context: android.content.Context,
    targetName: String,
    folderAliases: List<String>,
    ensureFolderName: String? = null,
    debugLookupLabel: String? = null
): LrcTextWithFileName? {
    val cleanTargetName = targetName.trim()
    if (cleanTargetName.isBlank()) return null

    // INTERNAL (file://) + fallback app-private SPL
    val fileRoots = linkedSetOf<File>().apply {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
        if (rootUri?.scheme == "file" && !rootUri.path.isNullOrBlank()) {
            add(File(rootUri.path!!))
        }
        add(File(context.getExternalFilesDir(null), "SPL_Music"))
    }

    for (root in fileRoots) {
        if (!root.exists()) continue

        val backingTracks = File(root, "BackingTracks")
        val backingTrack = File(root, "BackingTrack")
        val backingRoots = linkedSetOf(backingTracks, backingTrack)

        if (ensureFolderName != null) {
            val ensureParent = when {
                backingTracks.exists() -> backingTracks
                backingTrack.exists() -> backingTrack
                else -> backingTracks
            }
            if (!ensureParent.exists()) ensureParent.mkdirs()
            val ensure = File(ensureParent, ensureFolderName)
            if (!ensure.exists()) ensure.mkdirs()
        }

        for (backingRoot in backingRoots) {
            for (alias in folderAliases) {
                val candidate = File(File(backingRoot, alias), cleanTargetName)
                if (!debugLookupLabel.isNullOrBlank()) {
                    Log.d("LrcDebug", "$debugLookupLabel candidate_file=${candidate.absolutePath}")
                }
                if (candidate.exists() && candidate.isFile) {
                    val text = runCatching { candidate.readText(Charsets.UTF_8) }.getOrNull() ?: return null
                    if (!debugLookupLabel.isNullOrBlank()) {
                        Log.d("LrcDebug", "$debugLookupLabel hit_file=${candidate.absolutePath}")
                    }
                    return LrcTextWithFileName(
                        text = text,
                        fileName = candidate.name,
                        debugPath = candidate.absolutePath
                    )
                }
            }
        }
    }

    // SAF (content://)
    val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
    if (rootUri?.scheme == "content") {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
            ?: DocumentFile.fromSingleUri(context, rootUri)
        if (rootDoc != null && rootDoc.isDirectory) {
            val backingDirs = findDirsByAliases(
                parent = rootDoc,
                aliases = listOf("BackingTracks", "BackingTrack")
            )
                .toMutableList()

            if (backingDirs.isEmpty()) {
                ensureOrFindDir(
                    parent = rootDoc,
                    preferredName = "BackingTracks",
                    aliases = listOf("BackingTracks", "BackingTrack")
                )?.let { backingDirs.add(it) }
            }

            backingDirs.forEach { backing ->
                val targetFolders = findDirsByAliases(backing, folderAliases).toMutableList()

                if (targetFolders.isEmpty()) {
                    ensureOrFindDir(
                        parent = backing,
                        preferredName = ensureFolderName ?: folderAliases.first(),
                        aliases = folderAliases
                    )?.let { targetFolders.add(it) }
                }

                targetFolders.forEach { targetFolder ->
                    if (!debugLookupLabel.isNullOrBlank()) {
                        Log.d(
                            "LrcDebug",
                            "$debugLookupLabel candidate_saf_folder=${targetFolder.uri} target=$cleanTargetName"
                        )
                    }
                    val fileDoc = targetFolder.listFiles().firstOrNull { child ->
                        child.isFile && (child.name ?: "").equals(cleanTargetName, ignoreCase = true)
                    }

                    if (fileDoc != null) {
                        val text = runCatching {
                            context.contentResolver.openInputStream(fileDoc.uri)
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                        }.getOrNull() ?: return null
                        return LrcTextWithFileName(
                            text = text,
                            fileName = fileDoc.name ?: cleanTargetName,
                            debugPath = fileDoc.uri.toString()
                        )
                    }
                }
            }
        }
    }

    return null
}

private fun ensureOrFindDir(
    parent: DocumentFile,
    preferredName: String,
    aliases: List<String>
): DocumentFile? {
    val normalizedAliases = aliases.map { normalizeDirName(it) }.toSet()
    parent.listFiles().firstOrNull { child ->
        child.isDirectory && normalizedAliases.contains(normalizeDirName(child.name.orEmpty()))
    }?.let { return it }
    return runCatching { parent.createDirectory(preferredName) }.getOrNull()
}

private fun findDirsByAliases(
    parent: DocumentFile,
    aliases: List<String>
): List<DocumentFile> {
    val normalizedAliases = aliases.map { normalizeDirName(it) }.toSet()
    return parent.listFiles().filter { child ->
        child.isDirectory && normalizedAliases.contains(normalizeDirName(child.name.orEmpty()))
    }
}

private fun normalizeDirName(name: String): String {
    return name.trim().lowercase()
}

private fun baseNameFromTrackUriString(trackUriString: String): String {
    val uri = runCatching { Uri.parse(trackUriString) }.getOrNull()
    val last = uri?.lastPathSegment ?: trackUriString
    val clean = last.substringAfterLast('/').substringAfterLast(':')
    val base = clean.substringBeforeLast('.', clean).trim()
    return if (base.isBlank()) "track" else base
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
