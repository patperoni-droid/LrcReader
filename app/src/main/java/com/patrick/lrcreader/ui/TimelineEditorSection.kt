package com.patrick.lrcreader.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.DEFAULT_TIMELINE_NOTE_DURATION_MS
import com.patrick.lrcreader.smp.TimelineMarker
import com.patrick.lrcreader.smp.TimelineMarkerKind

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineEditorSection(
    markers: List<TimelineMarker>,
    palette: List<String>,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    onCloseEditor: () -> Unit,
    onIsPlayingChange: (Boolean) -> Unit,
    seekToMs: (Long) -> Unit,
    onAddPaletteTag: (String) -> Unit,
    onAddMarker: (String) -> Unit,
    onAddTypedMarker: (TimelineMarkerKind) -> Unit,
    onRenameMarker: (Int, String, Long?) -> Unit,
    onDeleteMarker: (Int) -> Unit
) {
    val safePositionMs = positionMs.coerceAtLeast(0).toLong()
    var renameIndex by remember(markers) { mutableStateOf<Int?>(null) }
    var renameText by remember(markers) { mutableStateOf("") }
    var renameDurationSeconds by remember(markers) { mutableStateOf("") }
    var paletteDraft by remember { mutableStateOf("") }
    val paletteNavigationIndexByLabel = remember(markers) { mutableStateMapOf<String, Int>() }
    val paletteNavigationIndexByKind = remember(markers) { mutableStateMapOf<TimelineMarkerKind, Int>() }
    var focusRequestTimeMs by remember { mutableLongStateOf(-1L) }
    var focusRequestToken by remember { mutableIntStateOf(0) }

    fun requestFocusAt(timeMs: Long) {
        val targetTimeMs = timeMs.coerceAtLeast(0L)
        focusRequestTimeMs = targetTimeMs
        focusRequestToken += 1
        runCatching { seekToMs(targetTimeMs) }
    }

    fun seekToNextMatchingMarker(
        currentIndexProvider: () -> Int?,
        nextIndexConsumer: (Int) -> Unit,
        matcher: (TimelineMarker) -> Boolean
    ) {
        val matchingMarkers = markers.filter(matcher)
        if (matchingMarkers.isEmpty()) {
            return
        }

        val nextIndex = currentIndexProvider() ?: 0
        val targetMarker = matchingMarkers[nextIndex % matchingMarkers.size]
        nextIndexConsumer((nextIndex + 1) % matchingMarkers.size)
        requestFocusAt(targetMarker.timeMs)
    }

    fun seekToNextPaletteMarker(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return

        val key = trimmed.lowercase()
        seekToNextMatchingMarker(
            currentIndexProvider = { paletteNavigationIndexByLabel[key] },
            nextIndexConsumer = { nextIndex -> paletteNavigationIndexByLabel[key] = nextIndex },
            matcher = { marker ->
                marker.kind == TimelineMarkerKind.TEXT &&
                    marker.label.equals(trimmed, ignoreCase = true)
            }
        )
    }

    fun seekToNextTypedMarker(kind: TimelineMarkerKind) {
        if (kind == TimelineMarkerKind.TEXT) return
        seekToNextMatchingMarker(
            currentIndexProvider = { paletteNavigationIndexByKind[kind] },
            nextIndexConsumer = { nextIndex -> paletteNavigationIndexByKind[kind] = nextIndex },
            matcher = { marker -> marker.kind == kind }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.timeline_dialog_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onCloseEditor) {
                Text(stringResource(R.string.common_close), color = Color(0xFFB0BEC5))
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        onIsPlayingChange(false)
                    } else if (durationMs > 0) {
                        onIsPlayingChange(true)
                        runCatching { FillerSoundManager.fadeOutAndStop(200) }
                    }
                }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.lyrics_editor_cd_sync_play_pause),
                    tint = Color.White
                )
            }

            IconButton(onClick = { runCatching { seekToMs(0L) } }) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.lyrics_editor_cd_back_to_start),
                    tint = Color.White
                )
            }

            Text(
                text = formatTimelineEditorPosition(positionMs),
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }

        Text(
            text = stringResource(R.string.timeline_palette_title),
            color = Color(0xFFB0BEC5),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = paletteDraft,
                onValueChange = { paletteDraft = it },
                label = { Text(stringResource(R.string.timeline_palette_input_label)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    val trimmed = paletteDraft.trim()
                    if (trimmed.isNotEmpty()) {
                        onAddPaletteTag(trimmed)
                        paletteDraft = ""
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.timeline_palette_add_action),
                    color = Color(0xFF80CBC4)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (palette.isEmpty()) {
            Text(
                text = stringResource(R.string.timeline_palette_empty_state),
                color = Color.Gray
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                palette.forEach { label ->
                    TimelinePaletteTagButton(
                        label = label,
                        onClick = { onAddMarker(label) },
                        onLongClick = { seekToNextPaletteMarker(label) }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.timeline_event_palette_title),
            color = Color(0xFFB0BEC5),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TimelineEventPaletteButton(
                label = stringResource(R.string.timeline_event_midi),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFF80CBC4)
                    )
                },
                onClick = { onAddTypedMarker(TimelineMarkerKind.MIDI) },
                onLongClick = { seekToNextTypedMarker(TimelineMarkerKind.MIDI) }
            )
            TimelineEventPaletteButton(
                label = stringResource(R.string.timeline_event_note),
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                        contentDescription = null,
                        tint = Color(0xFFFFF176)
                    )
                },
                onClick = { onAddTypedMarker(TimelineMarkerKind.NOTE) },
                onLongClick = { seekToNextTypedMarker(TimelineMarkerKind.NOTE) }
            )
            TimelineEventPaletteButton(
                label = stringResource(R.string.timeline_event_dmx),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.FlashOn,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D)
                    )
                },
                onClick = { onAddTypedMarker(TimelineMarkerKind.DMX) },
                onLongClick = { seekToNextTypedMarker(TimelineMarkerKind.DMX) }
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.timeline_markers_title),
                color = Color(0xFFB0BEC5),
                fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTimelineMarkerTime(safePositionMs),
                color = if (durationMs > 0) Color(0xFF80CBC4) else Color(0xFFB0BEC5),
                fontSize = 12.sp
            )
        }

        TimelineScrubColumn(
            modifier = Modifier.weight(1f),
            markers = markers,
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            focusRequestTimeMs = focusRequestTimeMs.takeIf { it >= 0L },
            focusRequestToken = focusRequestToken,
            onSeekToMs = seekToMs,
            onEditMarker = { index ->
                if (index !in markers.indices) return@TimelineScrubColumn
                renameIndex = index
                renameText = markers[index].label
                renameDurationSeconds = if (markers[index].kind == TimelineMarkerKind.NOTE) {
                    val durationMs = markers[index].durationMs ?: DEFAULT_TIMELINE_NOTE_DURATION_MS
                    (durationMs / 1_000L).toString()
                } else {
                    ""
                }
            },
            onDeleteMarker = onDeleteMarker
        )
    }

    val safeRenameIndex = renameIndex
    if (safeRenameIndex != null && safeRenameIndex in markers.indices) {
        val markerToEdit = markers[safeRenameIndex]
        val isEditingNote = markerToEdit.kind == TimelineMarkerKind.NOTE
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameIndex = null },
            title = {
                Text(
                    text = stringResource(R.string.timeline_rename_title),
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text(stringResource(R.string.timeline_rename_label)) },
                        singleLine = true
                    )
                    if (isEditingNote) {
                        OutlinedTextField(
                            value = renameDurationSeconds,
                            onValueChange = { input ->
                                renameDurationSeconds = input.filter { it.isDigit() }
                            },
                            label = { Text(stringResource(R.string.timeline_note_duration_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotEmpty()) {
                            val updatedDurationMs = if (isEditingNote) {
                                val seconds = renameDurationSeconds.toLongOrNull()
                                    ?.coerceAtLeast(1L)
                                    ?: (DEFAULT_TIMELINE_NOTE_DURATION_MS / 1_000L)
                                seconds * 1_000L
                            } else {
                                null
                            }
                            onRenameMarker(safeRenameIndex, trimmed, updatedDurationMs)
                        }
                        renameIndex = null
                    }
                ) {
                    Text(stringResource(R.string.common_ok), color = Color(0xFF80CBC4))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameIndex = null }) {
                    Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineEventPaletteButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = Color.White)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelinePaletteTagButton(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF80CBC4)
        )
    }
}

private fun formatTimelineEditorPosition(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun formatTimelineMarkerTime(timeMs: Long): String {
    val safe = timeMs.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val hundredths = (safe % 1000L) / 10L
    return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
}
