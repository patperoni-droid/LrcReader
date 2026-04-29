package com.patrick.lrcreader.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.audio.ArrangementWavRenderer
import com.patrick.lrcreader.core.waveform.WaveformExtractor
import com.patrick.lrcreader.core.waveform.WaveformPeaksCache
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.ArrangementData
import com.patrick.lrcreader.smp.ArrangementSegmentData
import com.patrick.lrcreader.smp.ArrangementStore
import com.patrick.lrcreader.smp.GridSetupStore
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.SongUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

private const val ARRANGEMENT_FADE_DURATION_MS = 40L
private const val ARRANGEMENT_FADE_STEPS = 5

private enum class ArrangementAddMode {
    KEEP,
    REMOVE
}

private data class ArrangementSegment(
    val id: String,
    val name: String,
    val startMs: Long,
    val endMs: Long
)

@Composable
fun ArrangementEditorSection(
    currentSongId: String?,
    currentPositionMs: Long,
    onClose: () -> Unit,
    onStopCurrentPlayback: () -> Unit = {},
    showSongPicker: Boolean = true,
    onBackToTempo: (() -> Unit)? = null,
    showCloseButton: Boolean = true
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val smpLibraryScanner = remember(appContext) { SmpLibraryScanner(appContext) }
    val arrangementPlayer = remember(appContext) {
        ExoPlayer.Builder(appContext).build().apply { playWhenReady = false }
    }

    var availableSongs by remember { mutableStateOf<List<SongUnit>>(emptyList()) }
    var selectedSongId by remember { mutableStateOf(currentSongId?.takeIf { it.isNotBlank() }) }
    var showSongPicker by remember { mutableStateOf(false) }
    var isLoadingSongs by remember { mutableStateOf(false) }
    var currentArrangementPositionMs by remember { mutableLongStateOf(0L) }
    var arrangementDurationMs by remember { mutableLongStateOf(0L) }
    var isArrangementPlaying by remember { mutableStateOf(false) }
    var sliderPositionMs by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var loopActive by remember { mutableStateOf(false) }
    var loopStartMs by remember { mutableLongStateOf(0L) }
    var loopEndMs by remember { mutableLongStateOf(0L) }
    var isPreviewGenerating by remember { mutableStateOf(false) }
    var previewPlaybackActive by remember { mutableStateOf(false) }
    var previewRenderedFile by remember { mutableStateOf<File?>(null) }
    var structurePlaybackActive by remember { mutableStateOf(false) }
    var structurePlaybackIndex by remember { mutableIntStateOf(-1) }
    var structureFadeOutIndex by remember { mutableIntStateOf(-1) }
    var gridTempoBpm by remember { mutableStateOf<Double?>(null) }
    var gridSyncPointMs by remember { mutableStateOf<Long?>(null) }
    var gridTimeSignatureNumerator by remember { mutableIntStateOf(4) }
    var gridTimeSignatureDenominator by remember { mutableIntStateOf(4) }

    var segmentInMs by remember { mutableStateOf<Long?>(null) }
    var segmentOutMs by remember { mutableStateOf<Long?>(null) }
    var arrangementAddMode by remember { mutableStateOf(ArrangementAddMode.KEEP) }
    var nextSegmentIndex by remember { mutableLongStateOf(1L) }
    var arrangementName by remember { mutableStateOf("Arrangement 1") }
    val segments = remember { mutableStateListOf<ArrangementSegment>() }
    val structureSegmentIds = remember { mutableStateListOf<String>() }
    var renameSegmentId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf(TextFieldValue("")) }
    var segmentOptionsTargetId by remember { mutableStateOf<String?>(null) }
    var arrangementWaveformPeaks by remember { mutableStateOf<List<Float>>(emptyList()) }
    var isArrangementWaveformLoading by remember { mutableStateOf(false) }
    var arrangementWaveformError by remember { mutableStateOf(false) }
    val selectedSong = availableSongs.firstOrNull { it.id == selectedSongId }
    val selectedSongLabel = selectedSong?.title ?: selectedSong?.id ?: currentSongId
    val hasSelectedSong = selectedSong != null
    val hasPlayableSong = selectedSong?.audioPath != null
    val canEditPositions = hasPlayableSong
    val canValidateSegment = canEditPositions && segmentInMs != null && segmentOutMs != null && segmentInMs != segmentOutMs
    val defaultSegmentNameBase = stringResource(R.string.arrangement_segment_default_name)
    fun persistArrangementState() {
        val songId = selectedSongId?.trim().orEmpty()
        if (songId.isEmpty()) return

        val snapshotSegments = segments.map { segment ->
            ArrangementSegmentData(
                id = segment.id,
                name = segment.name,
                startMs = segment.startMs,
                endMs = segment.endMs
            )
        }
        val snapshotStructure = structureSegmentIds.toList()
        val snapshotName = arrangementName.ifBlank { "Arrangement 1" }
        val sourceSongId = selectedSong?.id?.takeIf { it.isNotBlank() } ?: songId

        scope.launch {
            ArrangementStore.save(
                context = appContext,
                songId = songId,
                data = ArrangementData(
                    name = snapshotName,
                    sourceSongId = sourceSongId,
                    segments = snapshotSegments,
                    structureSegmentIds = snapshotStructure
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        isLoadingSongs = true
        availableSongs = withContext(Dispatchers.IO) {
            smpLibraryScanner.listSongs()
        }
        if (selectedSongId.isNullOrBlank()) {
            selectedSongId = currentSongId?.takeIf { candidate ->
                availableSongs.any { it.id == candidate }
            }
        }
        isLoadingSongs = false
    }

    LaunchedEffect(selectedSongId) {
        val songId = selectedSongId?.trim().orEmpty()
        if (songId.isEmpty()) {
            gridTempoBpm = null
            gridSyncPointMs = null
            gridTimeSignatureNumerator = 4
            gridTimeSignatureDenominator = 4
            return@LaunchedEffect
        }
        val gridData = GridSetupStore.load(context, songId)
        gridTempoBpm = gridData?.tempoBpm
        gridSyncPointMs = gridData?.syncPointMs
        gridTimeSignatureNumerator = gridData?.timeSignatureNumerator ?: 4
        gridTimeSignatureDenominator = gridData?.timeSignatureDenominator ?: 4
    }

    DisposableEffect(arrangementPlayer) {
        onDispose {
            arrangementPlayer.release()
            previewRenderedFile?.let { renderedFile ->
                runCatching { renderedFile.delete() }
            }
        }
    }

    DisposableEffect(arrangementPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isArrangementPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val duration = arrangementPlayer.duration
                arrangementDurationMs = if (duration > 0L) duration else 0L
                if (playbackState == Player.STATE_ENDED) {
                    currentArrangementPositionMs = arrangementDurationMs
                    sliderPositionMs = arrangementDurationMs
                    isArrangementPlaying = false
                    structurePlaybackActive = false
                    structurePlaybackIndex = -1
                    structureFadeOutIndex = -1
                    arrangementPlayer.volume = 1f
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                arrangementPlayer.volume = 1f
                if (structurePlaybackActive) {
                    structurePlaybackIndex = arrangementPlayer.currentMediaItemIndex
                    structureFadeOutIndex = -1
                } else {
                    structurePlaybackIndex = -1
                }
            }
        }
        arrangementPlayer.addListener(listener)
        onDispose {
            arrangementPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(selectedSong?.id, selectedSong?.audioPath) {
        val songId = selectedSong?.id?.takeIf { it.isNotBlank() }
        val audioPath = selectedSong?.audioPath
        arrangementName = "Arrangement 1"
        segmentInMs = null
        segmentOutMs = null
        structureSegmentIds.clear()
        segments.clear()
        nextSegmentIndex = 1L
        currentArrangementPositionMs = 0L
        sliderPositionMs = 0L
        arrangementDurationMs = 0L
        isDraggingSlider = false
        loopActive = false
        loopStartMs = 0L
        loopEndMs = 0L
        isPreviewGenerating = false
        previewPlaybackActive = false
        structurePlaybackActive = false
        structurePlaybackIndex = -1
        structureFadeOutIndex = -1
        arrangementWaveformPeaks = emptyList()
        arrangementWaveformError = false
        arrangementPlayer.pause()
        arrangementPlayer.clearMediaItems()
        previewRenderedFile?.let { renderedFile ->
            runCatching { renderedFile.delete() }
        }
        previewRenderedFile = null
        if (songId != null) {
            ArrangementStore.load(appContext, songId)?.let { arrangementData ->
                arrangementName = arrangementData.name.ifBlank { "Arrangement 1" }
                segments += arrangementData.segments.map { segment ->
                    ArrangementSegment(
                        id = segment.id,
                        name = segment.name,
                        startMs = segment.startMs,
                        endMs = segment.endMs
                    )
                }
                val validSegmentIds = segments.map { it.id }.toSet()
                structureSegmentIds += arrangementData.structureSegmentIds.filter { it in validSegmentIds }
                nextSegmentIndex = resolveNextArrangementSegmentIndex(segments)
            }
        }
        if (!audioPath.isNullOrBlank()) {
            prepareArrangementFullTrack(
                player = arrangementPlayer,
                audioPath = audioPath,
                positionMs = 0L,
                shouldPlay = false
            )
        }
    }

    LaunchedEffect(selectedSong?.audioPath) {
        val audioPath = selectedSong?.audioPath
        if (audioPath.isNullOrBlank()) {
            isArrangementWaveformLoading = false
            arrangementWaveformPeaks = emptyList()
            arrangementWaveformError = false
            return@LaunchedEffect
        }

        isArrangementWaveformLoading = true
        arrangementWaveformError = false
        arrangementWaveformPeaks = emptyList()
        val audioUri = Uri.fromFile(File(audioPath))
        val peaksResult = runCatching {
            WaveformPeaksCache.getOrCompute(
                context = appContext,
                uri = audioUri,
                targetPoints = 720
            ) {
                WaveformExtractor.extractNormalizedPeaks(
                    context = appContext,
                    uri = audioUri,
                    targetPoints = 720
                )
            }
        }
        arrangementWaveformPeaks = peaksResult.getOrDefault(emptyList())
        arrangementWaveformError = peaksResult.isFailure
        isArrangementWaveformLoading = false
    }

    LaunchedEffect(arrangementPlayer, selectedSong?.id, isArrangementPlaying) {
        while (selectedSong != null) {
            if (!isDraggingSlider) {
                val nextPosition = runCatching { arrangementPlayer.currentPosition }.getOrDefault(0L)
                currentArrangementPositionMs = nextPosition.coerceAtLeast(0L)
                sliderPositionMs = currentArrangementPositionMs
                val duration = runCatching { arrangementPlayer.duration }.getOrDefault(0L)
                arrangementDurationMs = duration.coerceAtLeast(0L)
            }
            if (structurePlaybackActive && arrangementPlayer.volume != 1f) {
                arrangementPlayer.volume = 1f
            }
            delay(if (isArrangementPlaying) 100L else 250L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                onBackToTempo?.let { backToTempo ->
                    TextButton(onClick = backToTempo) {
                        Text(
                            text = stringResource(R.string.arrangement_back_to_tempo),
                            color = Color(0xFF80CBC4),
                            fontSize = 12.sp
                        )
                    }
                }

                if (!selectedSongLabel.isNullOrBlank()) {
                    Text(
                        text = selectedSongLabel,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (showCloseButton) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = Color(0xFFCFD8DC)
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showSongPicker) {
                    OutlinedButton(
                        onClick = { showSongPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFF455A64))
                    ) {
                        Text(text = stringResource(R.string.arrangement_choose_song))
                    }
                }

                if (!hasSelectedSong) {
                    Text(
                        text = stringResource(R.string.arrangement_no_song_selected),
                        color = Color(0xFFB0BEC5),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else if (!hasPlayableSong) {
                    Text(
                        text = stringResource(R.string.arrangement_song_audio_unavailable),
                        color = Color(0xFFB0BEC5),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.arrangement_current_position,
                                formatArrangementTime(currentArrangementPositionMs)
                            ),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(
                                R.string.arrangement_duration_label,
                                formatArrangementTime(arrangementDurationMs)
                            ),
                            color = Color(0xFF90A4AE),
                            fontSize = 12.sp
                        )
                    }
                }

                ArrangementThinWaveform(
                    peaks = arrangementWaveformPeaks,
                    durationMs = arrangementDurationMs,
                    currentPositionMs = currentArrangementPositionMs,
                    isLoading = isArrangementWaveformLoading,
                    hasError = arrangementWaveformError,
                    onSeekRequested = { targetPositionMs ->
                        if (!hasPlayableSong) return@ArrangementThinWaveform
                        arrangementPlayer.seekTo(targetPositionMs.coerceAtLeast(0L))
                        currentArrangementPositionMs = targetPositionMs.coerceAtLeast(0L)
                        sliderPositionMs = currentArrangementPositionMs
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val audioPath = selectedSong?.audioPath
                                if (loopActive && hasPlayableSong && audioPath != null) {
                                    onStopCurrentPlayback()
                                    prepareArrangementFullTrack(
                                        player = arrangementPlayer,
                                        audioPath = audioPath,
                                        positionMs = currentArrangementPositionMs,
                                        shouldPlay = true
                                    )
                                    loopActive = false
                                    previewPlaybackActive = false
                                    structurePlaybackActive = false
                                    structurePlaybackIndex = -1
                                    structureFadeOutIndex = -1
                                } else if (isArrangementPlaying) {
                                    arrangementPlayer.pause()
                                } else if (hasPlayableSong && audioPath != null) {
                                    onStopCurrentPlayback()
                                    if (previewPlaybackActive) {
                                        prepareArrangementFullTrack(
                                            player = arrangementPlayer,
                                            audioPath = audioPath,
                                            positionMs = 0L,
                                            shouldPlay = true
                                        )
                                        previewPlaybackActive = false
                                        structurePlaybackActive = false
                                        structurePlaybackIndex = -1
                                        structureFadeOutIndex = -1
                                    } else {
                                        arrangementPlayer.play()
                                    }
                                }
                            },
                            enabled = hasPlayableSong
                        ) {
                            Icon(
                                imageVector = if (isArrangementPlaying) {
                                    Icons.Filled.Pause
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = stringResource(
                                    if (isArrangementPlaying) {
                                        R.string.arrangement_pause_action
                                    } else {
                                        R.string.arrangement_play_action
                                    }
                                ),
                                tint = if (hasPlayableSong) Color.White else Color(0xFF546E7A)
                            )
                        }

                        IconButton(
                            onClick = {
                                val audioPath = selectedSong?.audioPath
                                if (loopActive && audioPath != null) {
                                    prepareArrangementFullTrack(
                                        player = arrangementPlayer,
                                        audioPath = audioPath,
                                        positionMs = 0L,
                                        shouldPlay = false
                                    )
                                    loopActive = false
                                    previewPlaybackActive = false
                                    structurePlaybackActive = false
                                    structurePlaybackIndex = -1
                                    structureFadeOutIndex = -1
                                } else if (previewPlaybackActive && audioPath != null) {
                                    prepareArrangementFullTrack(
                                        player = arrangementPlayer,
                                        audioPath = audioPath,
                                        positionMs = 0L,
                                        shouldPlay = false
                                    )
                                    previewPlaybackActive = false
                                    structurePlaybackActive = false
                                    structurePlaybackIndex = -1
                                    structureFadeOutIndex = -1
                                } else {
                                    arrangementPlayer.seekTo(0L)
                                }
                                currentArrangementPositionMs = 0L
                                sliderPositionMs = 0L
                            },
                            enabled = hasPlayableSong
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(R.string.arrangement_return_action),
                                tint = if (hasPlayableSong) Color.White else Color(0xFF546E7A)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArrangementControlLabel(
                            label = stringResource(R.string.arrangement_in_action),
                            value = segmentInMs?.let(::formatArrangementTimePrecise)
                                ?: stringResource(R.string.arrangement_position_pending),
                            enabled = canEditPositions,
                            onClick = {
                                segmentInMs = quantizeArrangementPositionToBeat(
                                    positionMs = currentArrangementPositionMs,
                                    tempoBpm = gridTempoBpm,
                                    syncPointMs = gridSyncPointMs
                                )
                            }
                        )
                        Text(
                            text = stringResource(
                                if (arrangementAddMode == ArrangementAddMode.KEEP) {
                                    R.string.arrangement_add_mode_keep
                                } else {
                                    R.string.arrangement_add_mode_remove
                                }
                            ),
                            color = if (arrangementAddMode == ArrangementAddMode.REMOVE) {
                                Color(0xFF66BB6A)
                            } else {
                                Color(0xFF90A4AE)
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    arrangementAddMode = if (arrangementAddMode == ArrangementAddMode.KEEP) {
                                        ArrangementAddMode.REMOVE
                                    } else {
                                        ArrangementAddMode.KEEP
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        ArrangementControlLabel(
                            label = stringResource(R.string.arrangement_out_action),
                            value = segmentOutMs?.let(::formatArrangementTimePrecise)
                                ?: stringResource(R.string.arrangement_position_pending),
                            enabled = canEditPositions,
                            onClick = {
                                segmentOutMs = quantizeArrangementPositionToBeat(
                                    positionMs = currentArrangementPositionMs,
                                    tempoBpm = gridTempoBpm,
                                    syncPointMs = gridSyncPointMs
                                )
                            }
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val audioPath = selectedSong?.audioPath
                            if (audioPath.isNullOrBlank()) return@OutlinedButton

                            val playlistSegments = structureSegmentIds
                                .mapNotNull { segmentId -> segments.firstOrNull { it.id == segmentId } }
                            if (playlistSegments.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.arrangement_preview_structure_empty),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@OutlinedButton
                            }

                            isPreviewGenerating = true
                            scope.launch {
                                val previousPreviewFile = previewRenderedFile
                                val result = runCatching {
                                    ArrangementWavRenderer.render(
                                        context = appContext,
                                        audioPath = audioPath,
                                        segments = playlistSegments.map { it.startMs to it.endMs }
                                    )
                                }

                                result
                                    .onSuccess { previewFile ->
                                        previousPreviewFile
                                            ?.takeIf { it.absolutePath != previewFile.absolutePath }
                                            ?.let { staleFile -> runCatching { staleFile.delete() } }
                                        previewRenderedFile = previewFile
                                        onStopCurrentPlayback()
                                        loopActive = false
                                        previewPlaybackActive = true
                                        structurePlaybackActive = false
                                        structurePlaybackIndex = -1
                                        structureFadeOutIndex = -1
                                        prepareArrangementFullTrack(
                                            player = arrangementPlayer,
                                            audioPath = previewFile.absolutePath,
                                            positionMs = 0L,
                                            shouldPlay = true
                                        )
                                        currentArrangementPositionMs = 0L
                                        sliderPositionMs = 0L
                                    }
                                    .onFailure {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.arrangement_preview_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                isPreviewGenerating = false
                            }
                        },
                        enabled = hasPlayableSong && !isPreviewGenerating,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(
                                if (isPreviewGenerating) {
                                    R.string.arrangement_preview_generating
                                } else {
                                    R.string.arrangement_preview_action
                                }
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val rawStartMs = segmentInMs ?: return@Button
                                val rawEndMs = segmentOutMs ?: return@Button
                                val startMs = min(rawStartMs, rawEndMs)
                                val endMs = max(rawStartMs, rawEndMs)

                                if (arrangementAddMode == ArrangementAddMode.KEEP) {
                                    val segment = ArrangementSegment(
                                        id = "segment_$nextSegmentIndex",
                                        name = "$defaultSegmentNameBase $nextSegmentIndex",
                                        startMs = startMs,
                                        endMs = endMs
                                    )
                                    segments += segment
                                    nextSegmentIndex += 1L
                                } else {
                                    val totalDurationMs = arrangementDurationMs.coerceAtLeast(0L)
                                    val createdSegments = mutableListOf<ArrangementSegment>()
                                    var candidateIndex = nextSegmentIndex

                                    if (startMs > 0L) {
                                        createdSegments += ArrangementSegment(
                                            id = "segment_$candidateIndex",
                                            name = "$defaultSegmentNameBase $candidateIndex",
                                            startMs = 0L,
                                            endMs = startMs
                                        )
                                        candidateIndex += 1L
                                    }

                                    if (endMs < totalDurationMs) {
                                        createdSegments += ArrangementSegment(
                                            id = "segment_$candidateIndex",
                                            name = "$defaultSegmentNameBase $candidateIndex",
                                            startMs = endMs,
                                            endMs = totalDurationMs
                                        )
                                        candidateIndex += 1L
                                    }

                                    if (createdSegments.isEmpty()) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.arrangement_remove_segment_invalid),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@Button
                                    }

                                    segments += createdSegments
                                    structureSegmentIds += createdSegments.map { it.id }
                                    nextSegmentIndex = candidateIndex
                                }

                                segmentInMs = null
                                segmentOutMs = null
                                persistArrangementState()
                            },
                            enabled = canValidateSegment,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.arrangement_validate_segment_short))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArrangementListCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.arrangement_segments_title),
                emptyLabel = stringResource(R.string.arrangement_segments_empty),
                items = segments.map { segment ->
                    ArrangementListItem(
                        id = segment.id,
                        title = segment.name,
                        isActive = when {
                            structurePlaybackActive -> {
                                structureSegmentIds
                                    .getOrNull(structurePlaybackIndex)
                                    ?.let { it == segment.id } == true
                            }
                            loopActive -> loopStartMs == segment.startMs && loopEndMs == segment.endMs
                            else -> false
                        }
                    )
                },
                onItemClick = { segmentId ->
                    val segment = segments.firstOrNull { it.id == segmentId } ?: return@ArrangementListCard
                    val audioPath = selectedSong?.audioPath ?: return@ArrangementListCard
                    onStopCurrentPlayback()
                    loopActive = true
                    previewPlaybackActive = false
                    structurePlaybackActive = false
                    structureFadeOutIndex = -1
                    loopStartMs = segment.startMs
                    loopEndMs = segment.endMs
                    playArrangementSegmentLoop(
                        player = arrangementPlayer,
                        audioPath = audioPath,
                        startMs = segment.startMs,
                        endMs = segment.endMs
                    )
                },
                onItemAdd = { segmentId ->
                    if (segments.any { it.id == segmentId }) {
                        structureSegmentIds += segmentId
                        persistArrangementState()
                    }
                },
                onItemDelete = null,
                onItemLongClick = { segmentId ->
                    segmentOptionsTargetId = segmentId
                }
            )

            ArrangementListCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.arrangement_structure_title),
                emptyLabel = stringResource(R.string.arrangement_structure_empty),
                items = structureSegmentIds.mapIndexedNotNull { index, segmentId ->
                    val segment = segments.firstOrNull { it.id == segmentId } ?: return@mapIndexedNotNull null
                    ArrangementListItem(
                        id = index.toString(),
                        title = "${index + 1}. ${segment.name}",
                        isActive = structurePlaybackActive && index == structurePlaybackIndex
                    )
                },
                onItemClick = { structureIndexId ->
                    val startIndex = structureIndexId.toIntOrNull() ?: return@ArrangementListCard
                    val audioPath = selectedSong?.audioPath ?: return@ArrangementListCard
                    val playlistSegments = structureSegmentIds
                        .mapNotNull { segmentId -> segments.firstOrNull { it.id == segmentId } }
                    if (startIndex !in playlistSegments.indices) return@ArrangementListCard
                    onStopCurrentPlayback()
                    loopActive = false
                    previewPlaybackActive = false
                    structurePlaybackActive = true
                    structurePlaybackIndex = startIndex
                    structureFadeOutIndex = -1
                    playArrangementStructure(
                        player = arrangementPlayer,
                        audioPath = audioPath,
                        segments = playlistSegments,
                        startIndex = startIndex
                    )
                },
                onItemAdd = null,
                onItemDelete = { structureIndexId ->
                    val removeIndex = structureIndexId.toIntOrNull() ?: return@ArrangementListCard
                    if (removeIndex in structureSegmentIds.indices) {
                        structureSegmentIds.removeAt(removeIndex)
                        persistArrangementState()
                    }
                },
                onItemLongClick = null
            )
        }
    }

    if (showSongPicker) {
        AlertDialog(
            onDismissRequest = { showSongPicker = false },
            confirmButton = {
                Text(
                    text = stringResource(R.string.common_close),
                    color = Color(0xFFCFD8DC),
                    modifier = Modifier.clickable { showSongPicker = false }
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.arrangement_choose_song),
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLoadingSongs) {
                        Text(
                            text = stringResource(R.string.arrangement_songs_loading),
                            color = Color(0xFFB0BEC5),
                            fontSize = 13.sp
                        )
                    } else if (availableSongs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.arrangement_no_smp_song_available),
                            color = Color(0xFFB0BEC5),
                            fontSize = 13.sp
                        )
                    } else {
                        availableSongs.forEach { song ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSongId = song.id
                                        showSongPicker = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (song.id == selectedSongId) {
                                        Color(0xFF263238)
                                    } else {
                                        Color(0xFF1E1E1E)
                                    }
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = song.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = song.id,
                                        color = Color(0xFF90A4AE),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFF121212)
        )
    }

    if (renameSegmentId != null) {
        AlertDialog(
            onDismissRequest = { renameSegmentId = null },
            title = {
                Text(
                    text = stringResource(R.string.common_rename),
                    color = Color.White
                )
            },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    label = {
                        Text(text = stringResource(R.string.timeline_rename_label))
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = renameSegmentId ?: return@Button
                        val nextName = renameDraft.text.trim().ifBlank {
                            segments.firstOrNull { it.id == targetId }?.name ?: return@Button
                        }
                        val targetIndex = segments.indexOfFirst { it.id == targetId }
                        if (targetIndex >= 0) {
                            segments[targetIndex] = segments[targetIndex].copy(name = nextName)
                            persistArrangementState()
                        }
                        renameSegmentId = null
                    }
                ) {
                    Text(text = stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { renameSegmentId = null }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
            containerColor = Color(0xFF121212)
        )
    }

    if (segmentOptionsTargetId != null) {
        AlertDialog(
            onDismissRequest = { segmentOptionsTargetId = null },
            title = {
                Text(
                    text = segments.firstOrNull { it.id == segmentOptionsTargetId }?.name.orEmpty(),
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = stringResource(R.string.common_rename),
                        color = Color.White,
                        modifier = Modifier.clickable {
                            val segment = segments.firstOrNull { it.id == segmentOptionsTargetId }
                                ?: return@clickable
                            renameSegmentId = segment.id
                            renameDraft = TextFieldValue(segment.name)
                            segmentOptionsTargetId = null
                        }
                    )
                    Text(
                        text = stringResource(R.string.library_delete_action),
                        color = Color(0xFFFF8A80),
                        modifier = Modifier.clickable {
                            val targetId = segmentOptionsTargetId ?: return@clickable
                            val targetIndex = segments.indexOfFirst { it.id == targetId }
                            if (targetIndex >= 0) {
                                val removedSegment = segments.removeAt(targetIndex)
                                structureSegmentIds.removeAll { it == removedSegment.id }
                                if (loopActive && loopStartMs == removedSegment.startMs && loopEndMs == removedSegment.endMs) {
                                    selectedSong?.audioPath?.let { audioPath ->
                                        prepareArrangementFullTrack(
                                            player = arrangementPlayer,
                                            audioPath = audioPath,
                                            positionMs = removedSegment.startMs.coerceAtLeast(0L),
                                            shouldPlay = false
                                        )
                                    }
                                    loopActive = false
                                    structurePlaybackActive = false
                                    structurePlaybackIndex = -1
                                    structureFadeOutIndex = -1
                                }
                                persistArrangementState()
                            }
                            segmentOptionsTargetId = null
                        }
                    )
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { segmentOptionsTargetId = null }) {
                    Text(text = stringResource(R.string.common_close))
                }
            },
            containerColor = Color(0xFF121212)
        )
    }
}

private data class ArrangementListItem(
    val id: String,
    val title: String,
    val isActive: Boolean = false
)

private fun resolveNextArrangementSegmentIndex(segments: List<ArrangementSegment>): Long {
    val maxExistingIndex = segments.maxOfOrNull { segment ->
        segment.id.removePrefix("segment_").toLongOrNull() ?: 0L
    } ?: 0L
    return (maxExistingIndex + 1L).coerceAtLeast(1L)
}

private fun buildNextArrangementSongName(
    tracksRoot: File,
    sourceTitle: String?
): String {
    val normalizedBaseName = normalizeArrangementSongBaseName(sourceTitle)
    val namePattern = Regex("^${Regex.escape(normalizedBaseName)}_arrang_(\\d{2})$")
    val nextIndex = tracksRoot
        .takeIf { it.isDirectory }
        ?.listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isDirectory }
        .mapNotNull { songDir ->
            namePattern.matchEntire(songDir.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        .maxOrNull()
        ?.plus(1)
        ?: 1

    return "${normalizedBaseName}_arrang_${nextIndex.coerceAtLeast(1).toString().padStart(2, '0')}"
}

private fun normalizeArrangementSongBaseName(sourceTitle: String?): String {
    val lowercased = sourceTitle
        .orEmpty()
        .trim()
        .lowercase()
    val noAccents = Normalizer.normalize(lowercased, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    return noAccents
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "song" }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArrangementListCard(
    modifier: Modifier,
    title: String,
    emptyLabel: String,
    items: List<ArrangementListItem>,
    onItemClick: (String) -> Unit,
    onItemAdd: ((String) -> Unit)?,
    onItemDelete: ((String) -> Unit)?,
    onItemLongClick: ((String) -> Unit)?
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyLabel,
                        color = Color(0xFF78909C),
                        fontSize = 13.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items.forEach { item ->
                        Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onItemClick(item.id) },
                                onLongClick = if (onItemLongClick != null) {
                                    { onItemLongClick(item.id) }
                                } else {
                                    null
                                }
                            )
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            color = if (item.isActive) Color(0xFF66BB6A) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (onItemAdd != null) {
                            IconButton(
                                onClick = { onItemAdd(item.id) },
                                modifier = Modifier.width(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.timeline_palette_add_action),
                                    tint = Color(0xFFCFD8DC)
                                )
                            }
                        }
                        if (onItemDelete != null) {
                            IconButton(
                                onClick = { onItemDelete(item.id) },
                                modifier = Modifier.width(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.library_delete_action),
                                    tint = Color(0xFFCFD8DC)
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrangementControlLabel(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else Color(0xFF546E7A),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = if (enabled) Color(0xFF90A4AE) else Color(0xFF455A64),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArrangementThinWaveform(
    peaks: List<Float>,
    durationMs: Long,
    currentPositionMs: Long,
    isLoading: Boolean,
    hasError: Boolean,
    onSeekRequested: (Long) -> Unit
) {
    val waveformColor = Color(0xFF607D8B)
    val playheadColor = Color(0xFF66BB6A)
    val centerLineColor = Color(0xFF263238)
    val visiblePeaks = peaks.ifEmpty { List(180) { 0.15f } }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(durationMs, peaks) {
                detectTapGestures { offset ->
                    if (durationMs <= 0L || size.width <= 0f) return@detectTapGestures
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeekRequested((fraction * durationMs.toFloat()).roundToLong())
                }
            }
    ) {
        val centerY = size.height / 2f
        val widthPx = size.width
        val heightPx = size.height

        drawLine(
            color = centerLineColor,
            start = androidx.compose.ui.geometry.Offset(0f, centerY),
            end = androidx.compose.ui.geometry.Offset(widthPx, centerY),
            strokeWidth = 1f
        )

        val peakStep = if (visiblePeaks.size > 1) {
            widthPx / (visiblePeaks.size - 1).toFloat()
        } else {
            widthPx
        }

        visiblePeaks.forEachIndexed { index, rawPeak ->
            val x = index * peakStep
            val peak = rawPeak.coerceIn(0f, 1f).pow(0.75f)
            val amplitude = (peak * (heightPx * 0.42f)).coerceAtLeast(2f)
            drawLine(
                color = if (isLoading || hasError) Color(0xFF455A64) else waveformColor,
                start = androidx.compose.ui.geometry.Offset(x, centerY - amplitude),
                end = androidx.compose.ui.geometry.Offset(x, centerY + amplitude),
                strokeWidth = 1f
            )
        }

        val playheadX = if (durationMs > 0L) {
            (currentPositionMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()) * widthPx
        } else {
            0f
        }
        drawLine(
            color = playheadColor,
            start = androidx.compose.ui.geometry.Offset(playheadX, 0f),
            end = androidx.compose.ui.geometry.Offset(playheadX, heightPx),
            strokeWidth = 2f
        )
    }
}

private fun formatArrangementTime(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1_000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatArrangementTimePrecise(positionMs: Long): String {
    val safeMs = positionMs.coerceAtLeast(0L)
    val totalSeconds = (safeMs / 1_000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (safeMs % 1_000L).toInt()
    return "%02d:%02d.%03d".format(minutes, seconds, millis)
}

private fun playArrangementSegmentLoop(
    player: ExoPlayer,
    audioPath: String,
    startMs: Long,
    endMs: Long
) {
    val safeStartMs = startMs.coerceAtLeast(0L)
    val safeEndMs = endMs.coerceAtLeast(safeStartMs + 1L)
    player.pause()
    player.clearMediaItems()
    player.repeatMode = Player.REPEAT_MODE_ONE
    player.volume = 1f
    player.setMediaItem(
        MediaItem.Builder()
            .setUri(Uri.fromFile(File(audioPath)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(safeStartMs)
                    .setEndPositionMs(safeEndMs)
                    .build()
            )
            .build()
    )
    player.prepare()
    player.play()
}

private fun prepareArrangementFullTrack(
    player: ExoPlayer,
    audioPath: String,
    positionMs: Long,
    shouldPlay: Boolean
) {
    player.pause()
    player.clearMediaItems()
    player.repeatMode = Player.REPEAT_MODE_OFF
    player.volume = 1f
    player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(audioPath))))
    player.prepare()
    player.seekTo(positionMs.coerceAtLeast(0L))
    if (shouldPlay) {
        player.play()
    }
}

private fun playArrangementStructure(
    player: ExoPlayer,
    audioPath: String,
    segments: List<ArrangementSegment>,
    startIndex: Int
) {
    val mediaItems = segments.map { segment ->
        MediaItem.Builder()
            .setUri(Uri.fromFile(File(audioPath)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(segment.startMs.coerceAtLeast(0L))
                    .setEndPositionMs(segment.endMs.coerceAtLeast(segment.startMs + 1L))
                    .build()
            )
            .build()
    }
    player.pause()
    player.clearMediaItems()
    player.repeatMode = Player.REPEAT_MODE_OFF
    player.volume = 1f
    player.setMediaItems(mediaItems, startIndex, 0L)
    player.prepare()
    player.play()
}

private fun quantizeArrangementPositionToBeat(
    positionMs: Long,
    tempoBpm: Double?,
    syncPointMs: Long?
): Long {
    val safeTempoBpm = tempoBpm?.takeIf { it.isFinite() && it > 0.0 } ?: return positionMs
    val safeSyncPointMs = syncPointMs ?: return positionMs
    val beatDurationMs = 60_000.0 / safeTempoBpm
    if (!beatDurationMs.isFinite() || beatDurationMs <= 0.0) return positionMs
    val relativeMs = positionMs - safeSyncPointMs
    val nearestBeatIndex = (relativeMs / beatDurationMs).roundToLong()
    return (safeSyncPointMs + nearestBeatIndex * beatDurationMs).roundToLong().coerceAtLeast(0L)
}

private suspend fun fadeArrangementPlayerVolume(
    player: ExoPlayer,
    from: Float,
    to: Float
) {
    val safeFrom = from.coerceIn(0f, 1f)
    val safeTo = to.coerceIn(0f, 1f)
    if (safeFrom == safeTo) {
        player.volume = safeTo
        return
    }
    val stepDelayMs = (ARRANGEMENT_FADE_DURATION_MS / ARRANGEMENT_FADE_STEPS).coerceAtLeast(1L)
    repeat(ARRANGEMENT_FADE_STEPS) { step ->
        val progress = (step + 1).toFloat() / ARRANGEMENT_FADE_STEPS.toFloat()
        player.volume = safeFrom + (safeTo - safeFrom) * progress
        delay(stepDelayMs)
    }
    player.volume = safeTo
}
