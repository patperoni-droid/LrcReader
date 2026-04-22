package com.patrick.lrcreader.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.EditionConfig
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.TrackTimelineTempoPrefs
import com.patrick.lrcreader.core.light.LightSceneState
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.DEFAULT_TIMELINE_NOTE_DURATION_MS
import com.patrick.lrcreader.smp.TimelineMarker
import com.patrick.lrcreader.smp.TimelineMarkerKind

private enum class TimelineEditorMode {
    TIMELINE,
    GRID_SETUP
}

private enum class TimelineDisplayMode {
    TIME,
    MEASURES
}

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
    onGenerateLights: () -> Unit,
    onEditMidiMarker: (Int) -> Unit,
    onEditDmxMarker: (Int) -> Unit,
    showLightPreview: Boolean,
    lightPreviewSceneState: LightSceneState,
    canPasteDmxCue: Boolean,
    onPasteDmxCueHere: () -> Unit,
    onCopyDmxMarker: (Int) -> Unit,
    measuresTempoBpm: Int?,
    onMeasuresTempoChange: (Int) -> Unit,
    measureAnchorMs: Long?,
    onMeasureAnchorHere: (Long) -> Unit,
    onRenameMarker: (Int, String, Long?) -> Unit,
    onDeleteMarker: (Int) -> Unit
) {
    val context = LocalContext.current
    val safePositionMs = positionMs.coerceAtLeast(0).toLong()
    val isLite = EditionConfig.isLite
    val dmxUiVisible = true
    val proMessage = stringResource(R.string.timeline_pro_only)
    val sTimelineProDialogTitle = stringResource(R.string.timeline_config_pro_dialog_title)
    val sTimelineProDialogMessage = stringResource(R.string.timeline_config_pro_dialog_message)
    val sUpgradeToPro = stringResource(R.string.library_upgrade_to_pro)
    var renameIndex by remember(markers) { mutableStateOf<Int?>(null) }
    var renameText by remember(markers) { mutableStateOf("") }
    var renameDurationSeconds by remember(markers) { mutableStateOf("") }
    var paletteDraft by remember { mutableStateOf("") }
    var showPaletteInput by remember { mutableStateOf(false) }
    var showTimelineConfigProDialog by remember { mutableStateOf(false) }
    var editorMode by remember { mutableStateOf(TimelineEditorMode.TIMELINE) }
    var displayMode by remember { mutableStateOf(TimelineDisplayMode.TIME) }
    var measuresTempoDraft by remember { mutableStateOf("") }
    val paletteNavigationIndexByLabel = remember(markers) { mutableStateMapOf<String, Int>() }
    val paletteNavigationIndexByKind = remember(markers) { mutableStateMapOf<TimelineMarkerKind, Int>() }
    var focusRequestTimeMs by remember { mutableLongStateOf(-1L) }
    var focusRequestToken by remember { mutableIntStateOf(0) }

    val openUpgradeToPro: () -> Unit = remember(context) {
        {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://search?q=Stage Music Player Pro")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=Stage%20Music%20Player%20Pro&c=apps")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(marketIntent)
            } catch (_: ActivityNotFoundException) {
                context.startActivity(webIntent)
            }
        }
    }

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

    LaunchedEffect(measuresTempoBpm) {
        measuresTempoDraft = measuresTempoBpm?.toString().orEmpty()
    }

    val hasMeasuresGrid = remember(measuresTempoBpm, measureAnchorMs) {
        measuresTempoBpm != null && measureAnchorMs != null
    }

    LaunchedEffect(hasMeasuresGrid, displayMode) {
        if (!hasMeasuresGrid && displayMode == TimelineDisplayMode.MEASURES) {
            displayMode = TimelineDisplayMode.TIME
        }
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
                text = formatTimelinePositionLabel(
                    displayMode = displayMode,
                    hasMeasuresGrid = hasMeasuresGrid,
                    positionMs = safePositionMs,
                    tempoBpm = measuresTempoBpm,
                    measureAnchorMs = measureAnchorMs
                ),
                color = Color.LightGray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            when (editorMode) {
                TimelineEditorMode.TIMELINE -> {
                    TimelineDisplayModeSelector(
                        selectedMode = displayMode,
                        hasMeasuresGrid = hasMeasuresGrid,
                        onModeSelected = { nextMode ->
                            if (nextMode == TimelineDisplayMode.TIME || hasMeasuresGrid) {
                                displayMode = nextMode
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { editorMode = TimelineEditorMode.GRID_SETUP }) {
                        Text(
                            text = stringResource(
                                if (hasMeasuresGrid) {
                                    R.string.timeline_grid_edit_action
                                } else {
                                    R.string.timeline_grid_create_action
                                }
                            ),
                            color = Color(0xFF80CBC4),
                            fontSize = 12.sp
                        )
                    }
                }
                TimelineEditorMode.GRID_SETUP -> {
                    TextButton(onClick = { editorMode = TimelineEditorMode.TIMELINE }) {
                        Text(stringResource(R.string.common_close), color = Color(0xFF80CBC4))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when (editorMode) {
            TimelineEditorMode.TIMELINE -> {
                if (displayMode == TimelineDisplayMode.MEASURES && !hasMeasuresGrid) {
                    TimelineGridEmptyState(
                        onOpenGridSetup = { editorMode = TimelineEditorMode.GRID_SETUP }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (!showPaletteInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showPaletteInput = true }) {
                            Text(
                                text = stringResource(R.string.timeline_add_marker_action),
                                color = Color(0xFF80CBC4),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (showPaletteInput) {
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
                                    showPaletteInput = false
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.timeline_palette_add_action),
                                color = Color(0xFF80CBC4)
                            )
                        }
                        TextButton(
                            onClick = {
                                paletteDraft = ""
                                showPaletteInput = false
                            }
                        ) {
                            Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (palette.isNotEmpty()) {
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
                    if (dmxUiVisible) {
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
                }

                if (dmxUiVisible) {
                    TextButton(
                        onClick = {
                            if (isLite) {
                                showTimelineConfigProDialog = true
                            } else {
                                onGenerateLights()
                            }
                        },
                        enabled = durationMs > 0
                    ) {
                        Text(
                            text = stringResource(R.string.light_generate_action),
                            color = if (durationMs > 0) Color(0xFF80CBC4) else Color(0xFFB0BEC5)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dmxUiVisible) {
                        TextButton(
                            onClick = {
                                if (isLite) {
                                    showTimelineConfigProDialog = true
                                } else {
                                    onPasteDmxCueHere()
                                }
                            },
                            enabled = canPasteDmxCue
                        ) {
                            Text(
                                text = stringResource(R.string.timeline_paste_here),
                                color = if (canPasteDmxCue) Color(0xFFFFB74D) else Color(0xFFB0BEC5)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatTimelinePositionLabel(
                            displayMode = displayMode,
                            hasMeasuresGrid = hasMeasuresGrid,
                            positionMs = safePositionMs,
                            tempoBpm = measuresTempoBpm,
                            measureAnchorMs = measureAnchorMs
                        ),
                        color = if (durationMs > 0) Color(0xFF80CBC4) else Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    TimelineScrubColumn(
                        modifier = Modifier.fillMaxSize(),
                        markers = markers,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        slotLabel = { slotTimeMs ->
                            formatTimelinePositionLabel(
                                displayMode = displayMode,
                                hasMeasuresGrid = hasMeasuresGrid,
                                positionMs = slotTimeMs,
                                tempoBpm = measuresTempoBpm,
                                measureAnchorMs = measureAnchorMs
                            )
                        },
                        focusRequestTimeMs = focusRequestTimeMs.takeIf { it >= 0L },
                        focusRequestToken = focusRequestToken,
                        onSeekToMs = seekToMs,
                        onEditMarker = { index ->
                            if (index !in markers.indices) return@TimelineScrubColumn
                            if (markers[index].kind == TimelineMarkerKind.MIDI) {
                                if (!isLite) {
                                    onEditMidiMarker(index)
                                } else {
                                    showTimelineConfigProDialog = true
                                }
                                return@TimelineScrubColumn
                            }
                            if (markers[index].kind == TimelineMarkerKind.DMX) {
                                if (!dmxUiVisible) return@TimelineScrubColumn
                                onEditDmxMarker(index)
                                return@TimelineScrubColumn
                            }
                            if (markers[index].kind == TimelineMarkerKind.NOTE && isLite) {
                                showTimelineConfigProDialog = true
                                return@TimelineScrubColumn
                            }
                            renameIndex = index
                            renameText = markers[index].label
                            renameDurationSeconds = if (markers[index].kind == TimelineMarkerKind.NOTE) {
                                val durationMs = markers[index].durationMs ?: DEFAULT_TIMELINE_NOTE_DURATION_MS
                                (durationMs / 1_000L).toString()
                            } else {
                                ""
                            }
                        },
                        onDeleteMarker = onDeleteMarker,
                        onCopyDmxMarker = onCopyDmxMarker
                    )

                    if (dmxUiVisible && showLightPreview) {
                        LightSimulatorPreview(
                            sceneState = lightPreviewSceneState,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = 12.dp)
                        )
                    }
                }
            }
            TimelineEditorMode.GRID_SETUP -> {
                val sanitizedTempoDraft = measuresTempoDraft.filter { it.isDigit() }.take(3)
                val parsedTempoBpm = sanitizedTempoDraft.toIntOrNull()
                val effectiveTempoBpm = parsedTempoBpm ?: measuresTempoBpm
                val isTempoInvalid = sanitizedTempoDraft.isNotBlank() &&
                    (parsedTempoBpm == null ||
                        parsedTempoBpm !in TrackTimelineTempoPrefs.MIN_TEMPO_BPM..TrackTimelineTempoPrefs.MAX_TEMPO_BPM)
                TimelineMeasuresPlaceholder(
                    title = stringResource(
                        if (hasMeasuresGrid) {
                            R.string.timeline_grid_edit_title
                        } else {
                            R.string.timeline_grid_create_title
                        }
                    ),
                    tempoDraft = measuresTempoDraft,
                    isTempoInvalid = isTempoInvalid,
                    tempoBpm = effectiveTempoBpm,
                    measureAnchorMs = measureAnchorMs,
                    currentPositionMs = safePositionMs,
                    onMeasureAnchorHere = { anchorMs -> onMeasureAnchorHere(anchorMs) },
                    onTempoDraftChange = { input ->
                        val nextDraft = input.filter { ch -> ch.isDigit() }.take(3)
                        measuresTempoDraft = nextDraft
                        val nextTempoBpm = nextDraft.toIntOrNull()
                        if (nextTempoBpm != null &&
                            nextTempoBpm in TrackTimelineTempoPrefs.MIN_TEMPO_BPM..TrackTimelineTempoPrefs.MAX_TEMPO_BPM
                        ) {
                            onMeasuresTempoChange(nextTempoBpm)
                        }
                    }
                )
            }
        }
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

    if (showTimelineConfigProDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimelineConfigProDialog = false },
            title = {
                Text(
                    text = sTimelineProDialogTitle,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = sTimelineProDialogMessage,
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTimelineConfigProDialog = false
                        openUpgradeToPro()
                    }
                ) {
                    Text(sUpgradeToPro, color = Color(0xFF80CBC4))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimelineConfigProDialog = false }) {
                    Text(stringResource(R.string.common_close), color = Color(0xFFB0BEC5))
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

@Composable
private fun TimelineDisplayModeSelector(
    selectedMode: TimelineDisplayMode,
    hasMeasuresGrid: Boolean,
    onModeSelected: (TimelineDisplayMode) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineModeChip(
            text = stringResource(R.string.timeline_mode_time),
            selected = selectedMode == TimelineDisplayMode.TIME,
            enabled = true,
            onClick = { onModeSelected(TimelineDisplayMode.TIME) }
        )
        TimelineModeChip(
            text = stringResource(R.string.timeline_mode_measures),
            selected = selectedMode == TimelineDisplayMode.MEASURES,
            enabled = hasMeasuresGrid,
            onClick = { onModeSelected(TimelineDisplayMode.MEASURES) }
        )
    }
}

@Composable
private fun TimelineModeChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        TextButton(onClick = onClick, enabled = enabled) {
            Text(text = text, color = Color(0xFF80CBC4))
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) {
            Text(
                text = text,
                color = if (enabled) Color(0xFFB0BEC5) else Color(0xFF6B6B6B)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineMeasuresPlaceholder(
    title: String,
    tempoDraft: String,
    isTempoInvalid: Boolean,
    tempoBpm: Int?,
    measureAnchorMs: Long?,
    currentPositionMs: Long,
    onMeasureAnchorHere: (Long) -> Unit,
    onTempoDraftChange: (String) -> Unit
) {
    var localMeasureAnchorMs by remember(measureAnchorMs) { mutableStateOf(measureAnchorMs) }
    val savedAnchorMs = localMeasureAnchorMs
    val measuresStatus = remember(tempoBpm, localMeasureAnchorMs, currentPositionMs) {
        computeTimelineMeasuresStatus(
            tempoBpm = tempoBpm,
            measureAnchorMs = localMeasureAnchorMs,
            currentPositionMs = currentPositionMs
        )
    }

    LaunchedEffect(measureAnchorMs) {
        localMeasureAnchorMs = measureAnchorMs
    }

    fun updateLocalMeasureAnchor(deltaMs: Long) {
        val baseAnchorMs = localMeasureAnchorMs ?: return
        val nextAnchorMs = (baseAnchorMs + deltaMs).coerceAtLeast(0L)
        localMeasureAnchorMs = nextAnchorMs
        onMeasureAnchorHere(nextAnchorMs)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = tempoDraft,
            onValueChange = onTempoDraftChange,
            label = { Text(stringResource(R.string.timeline_measures_tempo_label)) },
            placeholder = { Text(stringResource(R.string.timeline_measures_tempo_placeholder)) },
            singleLine = true,
            isError = isTempoInvalid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text(
                    text = stringResource(
                        R.string.timeline_measures_tempo_range_hint,
                        TrackTimelineTempoPrefs.MIN_TEMPO_BPM,
                        TrackTimelineTempoPrefs.MAX_TEMPO_BPM
                    )
                )
            }
        )
        OutlinedTextField(
            value = stringResource(R.string.timeline_measures_signature_default),
            onValueChange = {},
            label = { Text(stringResource(R.string.timeline_measures_signature_label)) },
            singleLine = true,
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.timeline_measures_placeholder_message),
            color = Color(0xFFB0BEC5),
            fontSize = 13.sp
        )
        TextButton(onClick = {
            localMeasureAnchorMs = currentPositionMs
            onMeasureAnchorHere(currentPositionMs)
        }) {
            Text(
                text = stringResource(R.string.timeline_measures_anchor_action),
                color = Color(0xFF80CBC4)
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { updateLocalMeasureAnchor(-50L) },
                enabled = localMeasureAnchorMs != null
            ) {
                Text(stringResource(R.string.timeline_measures_adjust_minus_50))
            }
            OutlinedButton(
                onClick = { updateLocalMeasureAnchor(-10L) },
                enabled = localMeasureAnchorMs != null
            ) {
                Text(stringResource(R.string.timeline_measures_adjust_minus_10))
            }
            OutlinedButton(
                onClick = { updateLocalMeasureAnchor(10L) },
                enabled = localMeasureAnchorMs != null
            ) {
                Text(stringResource(R.string.timeline_measures_adjust_plus_10))
            }
            OutlinedButton(
                onClick = { updateLocalMeasureAnchor(50L) },
                enabled = localMeasureAnchorMs != null
            ) {
                Text(stringResource(R.string.timeline_measures_adjust_plus_50))
            }
        }
        Text(
            text = if (savedAnchorMs != null) {
                stringResource(
                    R.string.timeline_measures_anchor_saved,
                    formatTimelineMarkerTime(savedAnchorMs)
                )
            } else {
                stringResource(
                    R.string.timeline_measures_anchor_ready,
                    formatTimelineMarkerTime(currentPositionMs)
                )
            },
            color = Color(0xFFB0BEC5),
            fontSize = 12.sp
        )
        if (measuresStatus != null) {
            Text(
                text = stringResource(
                    R.string.timeline_measures_live_position,
                    measuresStatus.currentBar,
                    measuresStatus.currentBeat
                ),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TimelineGridEmptyState(
    onOpenGridSetup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.timeline_grid_missing_message),
            color = Color(0xFFB0BEC5),
            fontSize = 13.sp
        )
        TextButton(onClick = onOpenGridSetup) {
            Text(
                text = stringResource(R.string.timeline_grid_create_action),
                color = Color(0xFF80CBC4)
            )
        }
    }
}

private data class TimelineMeasuresStatus(
    val currentBar: Int,
    val currentBeat: Int,
    val beatIndex: Long
)

private fun formatTimelinePositionLabel(
    displayMode: TimelineDisplayMode,
    hasMeasuresGrid: Boolean,
    positionMs: Long,
    tempoBpm: Int?,
    measureAnchorMs: Long?
): String {
    if (displayMode == TimelineDisplayMode.MEASURES && hasMeasuresGrid) {
        val status = computeTimelineMeasuresStatus(
            tempoBpm = tempoBpm,
            measureAnchorMs = measureAnchorMs,
            currentPositionMs = positionMs
        )
        if (status != null) {
            return if (status.currentBeat == 1) {
                status.currentBar.toString()
            } else {
                ""
            }
        }
    }
    return formatTimelineMarkerTime(positionMs)
}

private fun computeTimelineMeasuresStatus(
    tempoBpm: Int?,
    measureAnchorMs: Long?,
    currentPositionMs: Long
): TimelineMeasuresStatus? {
    val safeTempoBpm = tempoBpm?.takeIf {
        it in TrackTimelineTempoPrefs.MIN_TEMPO_BPM..TrackTimelineTempoPrefs.MAX_TEMPO_BPM
    } ?: return null
    val safeAnchorMs = measureAnchorMs ?: return null
    if (currentPositionMs < safeAnchorMs) return null

    val beatDurationMs = 60_000.0 / safeTempoBpm.toDouble()
    val barDurationMs = beatDurationMs * 4.0
    if (beatDurationMs <= 0.0 || barDurationMs <= 0.0) return null

    val relativeMs = currentPositionMs - safeAnchorMs
    val beatIndex = kotlin.math.floor(relativeMs / beatDurationMs).toLong()
    val currentBar = (beatIndex / 4L).toInt() + 1
    val barOffsetMs = relativeMs % barDurationMs
    val currentBeat = kotlin.math.floor(barOffsetMs / beatDurationMs).toInt() + 1

    return TimelineMeasuresStatus(
        currentBar = currentBar.coerceAtLeast(1),
        currentBeat = currentBeat.coerceIn(1, 4),
        beatIndex = beatIndex.coerceAtLeast(0L)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineEventPaletteButton(
    label: String,
    enabled: Boolean = true,
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
        Text(text = label, color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f))
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
