package com.patrick.lrcreader.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.GridSetupStore
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.SongUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

private const val ARRANGEMENT_FADE_DURATION_MS = 40L
private const val ARRANGEMENT_FADE_STEPS = 5

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
    onStopCurrentPlayback: () -> Unit = {}
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
    var structurePlaybackActive by remember { mutableStateOf(false) }
    var structureFadeOutIndex by remember { mutableIntStateOf(-1) }
    var gridTempoBpm by remember { mutableStateOf<Double?>(null) }
    var gridSyncPointMs by remember { mutableStateOf<Long?>(null) }
    var gridTimeSignatureNumerator by remember { mutableIntStateOf(4) }
    var gridTimeSignatureDenominator by remember { mutableIntStateOf(4) }

    var segmentInMs by remember { mutableStateOf<Long?>(null) }
    var segmentOutMs by remember { mutableStateOf<Long?>(null) }
    var nextSegmentIndex by remember { mutableLongStateOf(1L) }
    val segments = remember { mutableStateListOf<ArrangementSegment>() }
    val structureSegmentIds = remember { mutableStateListOf<String>() }
    var renameSegmentId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf(TextFieldValue("")) }
    val selectedSong = availableSongs.firstOrNull { it.id == selectedSongId }
    val selectedSongLabel = selectedSong?.title ?: selectedSong?.id ?: currentSongId
    val hasSelectedSong = selectedSong != null
    val hasPlayableSong = selectedSong?.audioPath != null
    val canEditPositions = hasPlayableSong
    val canValidateSegment = canEditPositions && segmentInMs != null && segmentOutMs != null && segmentInMs != segmentOutMs
    val defaultSegmentNameBase = stringResource(R.string.arrangement_segment_default_name)

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
                    structureFadeOutIndex = -1
                    arrangementPlayer.volume = 1f
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!structurePlaybackActive) {
                    arrangementPlayer.volume = 1f
                    return
                }
                structureFadeOutIndex = -1
                if (arrangementPlayer.currentMediaItemIndex > 0 &&
                    arrangementPlayer.currentMediaItemIndex < arrangementPlayer.mediaItemCount
                ) {
                    scope.launch {
                        arrangementPlayer.volume = 0f
                        fadeArrangementPlayerVolume(arrangementPlayer, from = 0f, to = 1f)
                    }
                } else {
                    arrangementPlayer.volume = 1f
                }
            }
        }
        arrangementPlayer.addListener(listener)
        onDispose {
            arrangementPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(selectedSong?.id, selectedSong?.audioPath) {
        val audioPath = selectedSong?.audioPath
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
        structurePlaybackActive = false
        structureFadeOutIndex = -1
        arrangementPlayer.pause()
        arrangementPlayer.clearMediaItems()
        if (!audioPath.isNullOrBlank()) {
            prepareArrangementFullTrack(
                player = arrangementPlayer,
                audioPath = audioPath,
                positionMs = 0L,
                shouldPlay = false
            )
        }
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
            if (structurePlaybackActive && isArrangementPlaying) {
                val currentIndex = runCatching { arrangementPlayer.currentMediaItemIndex }.getOrDefault(0)
                val itemCount = runCatching { arrangementPlayer.mediaItemCount }.getOrDefault(0)
                val remainingMs = arrangementDurationMs - currentArrangementPositionMs
                if (currentIndex in 0 until (itemCount - 1) &&
                    currentIndex != structureFadeOutIndex &&
                    remainingMs in 1L..ARRANGEMENT_FADE_DURATION_MS
                ) {
                    structureFadeOutIndex = currentIndex
                    fadeArrangementPlayerVolume(arrangementPlayer, from = arrangementPlayer.volume, to = 0f)
                }
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.arrangement_title),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!selectedSongLabel.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.arrangement_song_id, selectedSongLabel),
                        color = Color(0xFF90A4AE),
                        fontSize = 12.sp
                    )
                }
            }

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = Color(0xFFCFD8DC)
                )
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
                OutlinedButton(
                    onClick = { showSongPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color(0xFF455A64))
                ) {
                    Text(text = stringResource(R.string.arrangement_choose_song))
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
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
                                structurePlaybackActive = false
                                structureFadeOutIndex = -1
                            } else if (isArrangementPlaying) {
                                arrangementPlayer.pause()
                            } else if (hasPlayableSong && audioPath != null) {
                                onStopCurrentPlayback()
                                arrangementPlayer.play()
                            }
                        },
                        enabled = hasPlayableSong,
                        modifier = Modifier.weight(1f)
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
                            )
                        )
                    }
                    OutlinedButton(
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
                                structurePlaybackActive = false
                                structureFadeOutIndex = -1
                            } else {
                                arrangementPlayer.seekTo(0L)
                            }
                            currentArrangementPositionMs = 0L
                            sliderPositionMs = 0L
                        },
                        enabled = hasPlayableSong,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.arrangement_return_action)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            segmentInMs = quantizeArrangementPositionToBeat(
                                positionMs = currentArrangementPositionMs,
                                tempoBpm = gridTempoBpm,
                                syncPointMs = gridSyncPointMs
                            )
                        },
                        enabled = canEditPositions,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = buildString {
                                append(stringResource(R.string.arrangement_in_action))
                                append("  ")
                                append(
                                    segmentInMs?.let(::formatArrangementTimePrecise)
                                        ?: stringResource(R.string.arrangement_position_pending)
                                )
                            },
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            segmentOutMs = quantizeArrangementPositionToBeat(
                                positionMs = currentArrangementPositionMs,
                                tempoBpm = gridTempoBpm,
                                syncPointMs = gridSyncPointMs
                            )
                        },
                        enabled = canEditPositions,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = buildString {
                                append(stringResource(R.string.arrangement_out_action))
                                append("  ")
                                append(
                                    segmentOutMs?.let(::formatArrangementTimePrecise)
                                        ?: stringResource(R.string.arrangement_position_pending)
                                )
                            },
                            fontSize = 12.sp
                        )
                    }
                }

                Slider(
                    value = sliderPositionMs.toFloat(),
                    onValueChange = { nextValue ->
                        isDraggingSlider = true
                        sliderPositionMs = nextValue.toLong()
                        currentArrangementPositionMs = sliderPositionMs
                    },
                    onValueChangeFinished = {
                        if (hasPlayableSong) {
                            arrangementPlayer.seekTo(sliderPositionMs)
                        }
                        currentArrangementPositionMs = sliderPositionMs
                        isDraggingSlider = false
                    },
                    enabled = hasPlayableSong && arrangementDurationMs > 0L,
                    valueRange = 0f..max(arrangementDurationMs, 1L).toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(modifier = Modifier.weight(2f))
                    Button(
                        onClick = {
                            val rawStartMs = segmentInMs ?: return@Button
                            val rawEndMs = segmentOutMs ?: return@Button
                            val startMs = min(rawStartMs, rawEndMs)
                            val endMs = max(rawStartMs, rawEndMs)
                            val segment = ArrangementSegment(
                                id = "segment_$nextSegmentIndex",
                                name = "$defaultSegmentNameBase $nextSegmentIndex",
                                startMs = startMs,
                                endMs = endMs
                            )
                            segments += segment
                            nextSegmentIndex += 1L
                            segmentInMs = null
                            segmentOutMs = null
                        },
                        enabled = canValidateSegment,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.arrangement_validate_segment_short))
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
                        subtitle = stringResource(
                            R.string.arrangement_segment_range,
                            formatArrangementTime(segment.startMs),
                            formatArrangementTime(segment.endMs)
                        ),
                        isCompact = true
                    )
                },
                onItemClick = { segmentId ->
                    val segment = segments.firstOrNull { it.id == segmentId } ?: return@ArrangementListCard
                    val audioPath = selectedSong?.audioPath ?: return@ArrangementListCard
                    onStopCurrentPlayback()
                    loopActive = true
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
                    }
                },
                onItemDelete = { segmentId ->
                    val targetIndex = segments.indexOfFirst { it.id == segmentId }
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
                            structureFadeOutIndex = -1
                        }
                    }
                },
                onItemLongClick = { segmentId ->
                    val segment = segments.firstOrNull { it.id == segmentId } ?: return@ArrangementListCard
                    renameSegmentId = segmentId
                    renameDraft = TextFieldValue(segment.name)
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
                        subtitle = stringResource(
                            R.string.arrangement_segment_range,
                            formatArrangementTime(segment.startMs),
                            formatArrangementTime(segment.endMs)
                        ),
                        isCompact = true
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
                    structurePlaybackActive = true
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
}

private data class ArrangementListItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val isCompact: Boolean = false
)

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
                items.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onItemClick(item.id) },
                                onLongClick = if (onItemLongClick != null) {
                                    { onItemLongClick(item.id) }
                                } else {
                                    null
                                }
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = if (item.isCompact) 13.sp else 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.subtitle,
                                    color = Color(0xFFB0BEC5),
                                    fontSize = if (item.isCompact) 11.sp else 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (onItemAdd != null) {
                                Text(
                                    text = "+",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { onItemAdd(item.id) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
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
