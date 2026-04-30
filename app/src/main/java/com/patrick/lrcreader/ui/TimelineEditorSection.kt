package com.patrick.lrcreader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.ToneGenerator
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.EditionConfig
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.TrackTimelineTempoPrefs
import com.patrick.lrcreader.core.waveform.WaveformExtractor
import com.patrick.lrcreader.core.waveform.WaveformPeaksCache
import com.patrick.lrcreader.core.light.LightSceneState
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.ArrangementData
import com.patrick.lrcreader.smp.ArrangementSegmentData
import com.patrick.lrcreader.smp.ArrangementStore
import com.patrick.lrcreader.smp.DEFAULT_TIMELINE_NOTE_DURATION_MS
import com.patrick.lrcreader.smp.GridSetupData
import com.patrick.lrcreader.smp.GridSetupStore
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.TimelineMarker
import com.patrick.lrcreader.smp.TimelineMarkerKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private enum class TimelineEditorMode {
    TIMELINE,
    GRID_SETUP
}

private enum class TimelineDisplayMode {
    TIME,
    MEASURES
}

private enum class TimelineMeasuresViewMode {
    TIMELINE,
    LIST
}

private enum class TimelineEditStep {
    MEASURE,
    BEAT,
    SUBDIVISION
}

private enum class TimelineListPositionDisplayMode {
    MUSIC,
    TIME
}

private enum class TimelineSegmentSelectionMode {
    KEEP,
    REMOVE
}

private enum class TimelineWaveformFocusMarker {
    NONE,
    IN,
    OUT
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineEditorSection(
    currentSongId: String?,
    startInGridSetup: Boolean = false,
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
    onAddMarkerAtPosition: (String, Long) -> Unit,
    onAddTypedMarker: (TimelineMarkerKind) -> Unit,
    onAddTypedMarkerAtPosition: (TimelineMarkerKind, Long) -> Unit,
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
    onOpenArrangement: () -> Unit = {},
    isPreparedClipLoopTestActive: Boolean,
    onStartPreparedClipLoopTest: (Long, Long) -> Unit,
    onStopPreparedClipLoopTest: () -> Unit,
    onMoveMarkerPosition: (Int, Long) -> Unit,
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
    var editorMode by remember(startInGridSetup) {
        mutableStateOf(
            if (startInGridSetup) {
                TimelineEditorMode.GRID_SETUP
            } else {
                TimelineEditorMode.TIMELINE
            }
        )
    }
    var displayMode by remember { mutableStateOf(TimelineDisplayMode.MEASURES) }
    var measuresViewMode by remember { mutableStateOf(TimelineMeasuresViewMode.LIST) }
    var measuresTempoDraft by remember { mutableStateOf("") }
    var positionEditIndex by remember(markers) { mutableStateOf<Int?>(null) }
    var positionEditMeasureDraft by remember { mutableStateOf("") }
    var positionEditBeatDraft by remember { mutableStateOf("") }
    var positionEditSubdivisionDraft by remember { mutableStateOf("") }
    var editingPositionMs by remember { mutableLongStateOf(0L) }
    var editingStep by remember { mutableStateOf(TimelineEditStep.BEAT) }
    var listPositionDisplayMode by remember { mutableStateOf(TimelineListPositionDisplayMode.MUSIC) }
    val paletteNavigationIndexByLabel = remember(markers) { mutableStateMapOf<String, Int>() }
    val paletteNavigationIndexByKind = remember(markers) { mutableStateMapOf<TimelineMarkerKind, Int>() }
    var focusRequestTimeMs by remember { mutableLongStateOf(-1L) }
    var focusRequestToken by remember { mutableIntStateOf(0) }
    var tempoStructurePreviewActive by remember { mutableStateOf(false) }
    var tempoStructurePreviewStopRequest by remember { mutableIntStateOf(0) }

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
    val listEditPositionMs = if (isPlaying) safePositionMs else editingPositionMs

    LaunchedEffect(measuresViewMode, hasMeasuresGrid, measureAnchorMs) {
        if (measuresViewMode == TimelineMeasuresViewMode.LIST && hasMeasuresGrid) {
            editingPositionMs = if (isPlaying) safePositionMs else measureAnchorMs ?: 0L
        }
    }

    fun openMarkerEditor(index: Int) {
        if (index !in markers.indices) return
        if (markers[index].kind == TimelineMarkerKind.MIDI) {
            if (!isLite) {
                onEditMidiMarker(index)
            } else {
                showTimelineConfigProDialog = true
            }
            return
        }
        if (markers[index].kind == TimelineMarkerKind.DMX) {
            if (!dmxUiVisible) return
            onEditDmxMarker(index)
            return
        }
        if (markers[index].kind == TimelineMarkerKind.NOTE && isLite) {
            showTimelineConfigProDialog = true
            return
        }
        renameIndex = index
        renameText = markers[index].label
        renameDurationSeconds = if (markers[index].kind == TimelineMarkerKind.NOTE) {
            val durationMs = markers[index].durationMs ?: DEFAULT_TIMELINE_NOTE_DURATION_MS
            (durationMs / 1_000L).toString()
        } else {
            ""
        }
    }

    fun playFromMarker(timeMs: Long) {
        requestFocusAt(timeMs)
        if (durationMs > 0) {
            onIsPlayingChange(true)
            runCatching { FillerSoundManager.fadeOutAndStop(200) }
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
            if (!startInGridSetup) {
                Text(
                    text = stringResource(R.string.timeline_dialog_title),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!(startInGridSetup || editorMode == TimelineEditorMode.GRID_SETUP)) {
                TextButton(
                    onClick = {
                        if (isPreparedClipLoopTestActive) {
                            onStopPreparedClipLoopTest()
                        }
                        if (startInGridSetup) {
                            onCloseEditor()
                        } else if (editorMode == TimelineEditorMode.GRID_SETUP) {
                            editorMode = TimelineEditorMode.TIMELINE
                        } else {
                            onCloseEditor()
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.common_close),
                        color = Color(0xFFB0BEC5)
                    )
                }
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
                    if (tempoStructurePreviewActive) {
                        tempoStructurePreviewStopRequest += 1
                        return@IconButton
                    }
                    if (editorMode == TimelineEditorMode.GRID_SETUP && isPreparedClipLoopTestActive) {
                        onStopPreparedClipLoopTest()
                    }
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
                    if (startInGridSetup) {
                        TextButton(onClick = onOpenArrangement) {
                            Text(
                                text = stringResource(R.string.arrangement_hub_cutting_action),
                                color = Color(0xFF80CBC4),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when (editorMode) {
            TimelineEditorMode.TIMELINE -> {
                if (displayMode == TimelineDisplayMode.MEASURES && hasMeasuresGrid) {
                    TimelineMeasuresViewModeSelector(
                        selectedMode = measuresViewMode,
                        onModeSelected = { measuresViewMode = it }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (!showPaletteInput && measuresViewMode != TimelineMeasuresViewMode.LIST) {
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

                if (showPaletteInput && measuresViewMode != TimelineMeasuresViewMode.LIST) {
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
                                onClick = {
                                    if (measuresViewMode == TimelineMeasuresViewMode.LIST && hasMeasuresGrid) {
                                        onAddMarkerAtPosition(label, listEditPositionMs)
                                    } else {
                                        onAddMarker(label)
                                    }
                                },
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
                                tint = Color(0xFF80CBC4).copy(alpha = 0.72f),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = {
                            if (measuresViewMode == TimelineMeasuresViewMode.LIST && hasMeasuresGrid) {
                                onAddTypedMarkerAtPosition(TimelineMarkerKind.MIDI, listEditPositionMs)
                            } else {
                                onAddTypedMarker(TimelineMarkerKind.MIDI)
                            }
                        },
                        onLongClick = { seekToNextTypedMarker(TimelineMarkerKind.MIDI) }
                    )
                    TimelineEventPaletteButton(
                        label = stringResource(R.string.timeline_event_note),
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                                contentDescription = null,
                                tint = Color(0xFFFFF176).copy(alpha = 0.72f),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = {
                            if (measuresViewMode == TimelineMeasuresViewMode.LIST && hasMeasuresGrid) {
                                onAddTypedMarkerAtPosition(TimelineMarkerKind.NOTE, listEditPositionMs)
                            } else {
                                onAddTypedMarker(TimelineMarkerKind.NOTE)
                            }
                        },
                        onLongClick = { seekToNextTypedMarker(TimelineMarkerKind.NOTE) }
                    )
                    if (dmxUiVisible) {
                        TimelineEventPaletteButton(
                            label = stringResource(R.string.timeline_event_dmx),
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.FlashOn,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB74D).copy(alpha = 0.72f),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                if (measuresViewMode == TimelineMeasuresViewMode.LIST && hasMeasuresGrid) {
                                    onAddTypedMarkerAtPosition(TimelineMarkerKind.DMX, listEditPositionMs)
                                } else {
                                    onAddTypedMarker(TimelineMarkerKind.DMX)
                                }
                            },
                            onLongClick = { seekToNextTypedMarker(TimelineMarkerKind.DMX) }
                        )
                    }
                }

                if (measuresViewMode != TimelineMeasuresViewMode.LIST) {
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
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (measuresViewMode == TimelineMeasuresViewMode.LIST) {
                        TimelineMeasuresEventList(
                            modifier = Modifier.fillMaxSize(),
                            markers = markers,
                            currentPositionMs = safePositionMs,
                            editingPositionMs = listEditPositionMs,
                            editingStep = editingStep,
                            positionDisplayMode = listPositionDisplayMode,
                            tempoBpm = measuresTempoBpm,
                            measureAnchorMs = measureAnchorMs,
                            onEditingStepSelected = { editingStep = it },
                            onPositionDisplayModeChange = { listPositionDisplayMode = it },
                            onJogSteps = { steps ->
                                editingPositionMs = computeTimelinePositionAfterSteps(
                                    tempoBpm = measuresTempoBpm,
                                    measureAnchorMs = measureAnchorMs,
                                    currentPositionMs = editingPositionMs,
                                    step = editingStep,
                                    steps = steps
                                ) ?: editingPositionMs
                            },
                            onEditPosition = { clickedIndex, status ->
                                positionEditIndex = clickedIndex
                                positionEditMeasureDraft = status?.currentBar?.toString().orEmpty()
                                positionEditBeatDraft = status?.currentBeat?.toString().orEmpty()
                                positionEditSubdivisionDraft = status?.currentSubdivision?.toString().orEmpty()
                            },
                            onPlayMarker = { timeMs -> playFromMarker(timeMs) },
                            onEditMarker = { index -> openMarkerEditor(index) },
                            onDeleteMarker = onDeleteMarker
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "ANCIENNE TIMELINE",
                                color = Color(0xFFFF5252),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                                onEditMarker = { index -> openMarkerEditor(index) },
                                onDeleteMarker = onDeleteMarker,
                                onCopyDmxMarker = onCopyDmxMarker
                            )
                        }
                    }

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
                GridSetupHost(
                    currentSongId = currentSongId,
                    fallbackTempoBpm = measuresTempoBpm,
                    measureAnchorMs = measureAnchorMs,
                    isPlaying = isPlaying,
                    currentPositionMs = safePositionMs,
                    seekToMs = seekToMs,
                    onIsPlayingChange = onIsPlayingChange,
                    onOpenArrangement = onOpenArrangement,
                    isPreparedClipLoopTestActive = isPreparedClipLoopTestActive,
                    onStartPreparedClipLoopTest = onStartPreparedClipLoopTest,
                    onStopPreparedClipLoopTest = onStopPreparedClipLoopTest,
                    structurePreviewStopRequest = tempoStructurePreviewStopRequest,
                    onStructurePreviewActiveChange = { tempoStructurePreviewActive = it }
                )
            }
        }
    }

    val safePositionEditIndex = positionEditIndex
    if (
        safePositionEditIndex != null &&
        safePositionEditIndex in markers.indices &&
        measuresTempoBpm != null &&
        measureAnchorMs != null
    ) {
        val parsedMeasure = positionEditMeasureDraft.toIntOrNull()
        val parsedBeat = positionEditBeatDraft.toIntOrNull()
        val parsedSubdivision = positionEditSubdivisionDraft.toIntOrNull()
        val isPositionInvalid = parsedMeasure == null ||
            parsedMeasure < 1 ||
            parsedBeat == null ||
            parsedBeat !in 1..4 ||
            parsedSubdivision == null ||
            parsedSubdivision !in 1..4

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { positionEditIndex = null },
            title = {
                Text(
                    text = stringResource(R.string.timeline_position_edit_title),
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = positionEditMeasureDraft,
                        onValueChange = { input ->
                            positionEditMeasureDraft = input.filter { it.isDigit() }.take(4)
                        },
                        label = { Text(stringResource(R.string.timeline_position_edit_measure_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = positionEditBeatDraft,
                        onValueChange = { input ->
                            positionEditBeatDraft = input.filter { it.isDigit() }.take(1)
                        },
                        label = { Text(stringResource(R.string.timeline_position_edit_beat_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = positionEditSubdivisionDraft,
                        onValueChange = { input ->
                            positionEditSubdivisionDraft = input.filter { it.isDigit() }.take(1)
                        },
                        label = { Text(stringResource(R.string.timeline_position_edit_subdivision_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetPositionMs = computeTimelinePositionMsFromMusicalPosition(
                            tempoBpm = measuresTempoBpm,
                            measureAnchorMs = measureAnchorMs,
                            measure = parsedMeasure,
                            beat = parsedBeat,
                            subdivision = parsedSubdivision
                        )
                        if (targetPositionMs != null) {
                            onMoveMarkerPosition(safePositionEditIndex, targetPositionMs)
                            positionEditIndex = null
                        }
                    },
                    enabled = !isPositionInvalid
                ) {
                    Text(stringResource(R.string.common_ok), color = Color(0xFF80CBC4))
                }
            },
            dismissButton = {
                TextButton(onClick = { positionEditIndex = null }) {
                    Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
                }
            },
            containerColor = Color(0xFF222222)
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
private fun TimelineMeasuresViewModeSelector(
    selectedMode: TimelineMeasuresViewMode,
    onModeSelected: (TimelineMeasuresViewMode) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineModeChip(
            text = stringResource(R.string.timeline_measures_view_timeline),
            selected = selectedMode == TimelineMeasuresViewMode.TIMELINE,
            enabled = true,
            onClick = { onModeSelected(TimelineMeasuresViewMode.TIMELINE) }
        )
        TimelineModeChip(
            text = stringResource(R.string.timeline_measures_view_list),
            selected = selectedMode == TimelineMeasuresViewMode.LIST,
            enabled = true,
            onClick = { onModeSelected(TimelineMeasuresViewMode.LIST) }
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
            enabled = true,
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
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(
            text = text,
            color = when {
                !enabled -> Color(0xFF6B6B6B)
                selected -> Color(0xFF80CBC4)
                else -> Color(0xFFB0BEC5)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineMeasuresEventList(
    modifier: Modifier = Modifier,
    markers: List<TimelineMarker>,
    currentPositionMs: Long,
    editingPositionMs: Long,
    editingStep: TimelineEditStep,
    positionDisplayMode: TimelineListPositionDisplayMode,
    tempoBpm: Int?,
    measureAnchorMs: Long?,
    onEditingStepSelected: (TimelineEditStep) -> Unit,
    onPositionDisplayModeChange: (TimelineListPositionDisplayMode) -> Unit,
    onJogSteps: (Int) -> Unit,
    onEditPosition: (Int, TimelineMeasuresStatus?) -> Unit,
    onPlayMarker: (Long) -> Unit,
    onEditMarker: (Int) -> Unit,
    onDeleteMarker: (Int) -> Unit
) {
    val activeMarkerIndex = remember(markers, currentPositionMs) {
        markers.indexOfLast { marker -> marker.timeMs <= currentPositionMs }
    }
    val editingMeasuresStatus = remember(tempoBpm, measureAnchorMs, editingPositionMs) {
        computeTimelineMeasuresStatus(
            tempoBpm = tempoBpm,
            measureAnchorMs = measureAnchorMs,
            currentPositionMs = editingPositionMs,
            clampBeforeAnchorToFirstMeasure = true
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TimelineEditPositionJog(
            editingMeasuresStatus = editingMeasuresStatus,
            editingPositionMs = editingPositionMs,
            currentTimeLabel = formatTimelineMarkerTime(currentPositionMs),
            selectedStep = editingStep,
            positionDisplayMode = positionDisplayMode,
            onStepSelected = onEditingStepSelected,
            onPositionDisplayModeChange = onPositionDisplayModeChange,
            onJogSteps = onJogSteps
        )

        if (markers.isEmpty()) {
            Text(
                text = stringResource(R.string.timeline_empty_state),
                color = Color(0xFFB0BEC5),
                fontSize = 13.sp
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(
                    items = markers,
                    key = { index, marker -> "${marker.kind.storageValue}-${marker.timeMs}-$index" }
                ) { index, marker ->
                    val isActive = index == activeMarkerIndex
                    val markerMeasuresStatus = computeTimelineMeasuresStatus(
                        tempoBpm = tempoBpm,
                        measureAnchorMs = measureAnchorMs,
                        currentPositionMs = marker.timeMs,
                        clampBeforeAnchorToFirstMeasure = true
                    )
                    TimelineMeasuresEventListItem(
                        index = index,
                        marker = marker,
                        isActive = isActive,
                        measuresStatus = markerMeasuresStatus,
                        positionDisplayMode = positionDisplayMode,
                        onEditPosition = onEditPosition,
                        onPlay = { onPlayMarker(marker.timeMs) },
                        onEdit = { onEditMarker(index) },
                        onDelete = { onDeleteMarker(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineEditPositionJog(
    editingMeasuresStatus: TimelineMeasuresStatus?,
    editingPositionMs: Long,
    currentTimeLabel: String,
    selectedStep: TimelineEditStep,
    positionDisplayMode: TimelineListPositionDisplayMode,
    onStepSelected: (TimelineEditStep) -> Unit,
    onPositionDisplayModeChange: (TimelineListPositionDisplayMode) -> Unit,
    onJogSteps: (Int) -> Unit
) {
    var dragRemainderPx by remember { mutableStateOf(0f) }
    var helperTextResId by remember { mutableStateOf<Int?>(null) }
    val dragThresholdPx = 36f
    val safeStatus = editingMeasuresStatus ?: TimelineMeasuresStatus(
        currentBar = 1,
        currentBeat = 1,
        currentSubdivision = 1,
        beatIndex = 0L
    )
    val counterSeparator = stringResource(R.string.timeline_counter_separator)

    LaunchedEffect(helperTextResId) {
        if (helperTextResId != null) {
            kotlinx.coroutines.delay(1_500L)
            helperTextResId = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(selectedStep) {
                detectDragGestures(
                    onDragEnd = { dragRemainderPx = 0f },
                    onDragCancel = { dragRemainderPx = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    dragRemainderPx += dragAmount.x
                    val steps = (dragRemainderPx / dragThresholdPx).toInt()
                    if (steps != 0) {
                        onJogSteps(steps)
                        dragRemainderPx -= steps * dragThresholdPx
                    }
                }
            }
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = currentTimeLabel,
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (positionDisplayMode == TimelineListPositionDisplayMode.MUSIC) {
                        TimelineCounterSegment(
                            text = "${safeStatus.currentBar} $counterSeparator",
                            selected = selectedStep == TimelineEditStep.MEASURE,
                            onClick = {
                                onStepSelected(TimelineEditStep.MEASURE)
                                helperTextResId = R.string.timeline_position_edit_measure_label
                            }
                        )
                        TimelineCounterSegment(
                            text = "%02d %s".format(safeStatus.currentBeat, counterSeparator),
                            selected = selectedStep == TimelineEditStep.BEAT,
                            onClick = {
                                onStepSelected(TimelineEditStep.BEAT)
                                helperTextResId = R.string.timeline_position_edit_beat_label
                            }
                        )
                        TimelineCounterSegment(
                            text = "%02d".format(safeStatus.currentSubdivision),
                            selected = selectedStep == TimelineEditStep.SUBDIVISION,
                            onClick = {
                                onStepSelected(TimelineEditStep.SUBDIVISION)
                                helperTextResId = R.string.timeline_position_edit_subdivision_label
                            }
                        )
                    } else {
                        Text(
                            text = formatTimelineMarkerTime(editingPositionMs),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
                TimelinePositionDisplayModeSwitch(
                    selectedMode = positionDisplayMode,
                    onModeSelected = onPositionDisplayModeChange,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
            TimelineJogRail(
                modifier = Modifier.fillMaxWidth(),
                visualOffsetPx = dragRemainderPx
            )
        }
        helperTextResId?.let { textResId ->
            Text(
                text = stringResource(textResId),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = (-30).dp)
                    .background(Color(0xFF263238), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color(0xFFE0F2F1),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TimelinePositionDisplayModeSwitch(
    selectedMode: TimelineListPositionDisplayMode,
    onModeSelected: (TimelineListPositionDisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val nextMode = if (selectedMode == TimelineListPositionDisplayMode.MUSIC) {
        TimelineListPositionDisplayMode.TIME
    } else {
        TimelineListPositionDisplayMode.MUSIC
    }
    TimelinePositionDisplayModeButton(
        text = if (selectedMode == TimelineListPositionDisplayMode.MUSIC) "🎵" else "⏱",
        onClick = { onModeSelected(nextMode) },
        modifier = modifier
    )
}

@Composable
private fun TimelinePositionDisplayModeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    ) {
        Text(
            text = text,
            color = Color(0xFF80CBC4),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun TimelineJogRail(
    modifier: Modifier = Modifier,
    visualOffsetPx: Float
) {
    Canvas(
        modifier = modifier
            .height(32.dp)
            .padding(horizontal = 30.dp, vertical = 5.dp)
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val tickSpacing = size.width / 12f
        val phase = visualOffsetPx % tickSpacing

        drawLine(
            color = Color(0xFF263238),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        for (index in -14..14) {
            val x = centerX + index * tickSpacing + phase
            if (x < 0f || x > size.width) continue
            val distanceRatio = (abs(x - centerX) / centerX).coerceIn(0f, 1f)
            val focus = 1f - distanceRatio
            val tickHeight = size.height * (0.28f + 0.72f * focus)
            val tickAlpha = 0.22f + 0.78f * focus
            drawLine(
                color = Color(0xFFB2DFDB).copy(alpha = tickAlpha),
                start = Offset(x, centerY - tickHeight / 2f),
                end = Offset(x, centerY + tickHeight / 2f),
                strokeWidth = 1.5f + 2.5f * focus,
                cap = StrokeCap.Round
            )
        }

        drawLine(
            color = Color(0xFF80CBC4).copy(alpha = 0.28f),
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height),
            strokeWidth = 10f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFE0F2F1),
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF05080A).copy(alpha = 0.7f),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF05080A).copy(alpha = 0.7f)
                )
            )
        )
    }
}

@Composable
private fun TimelineCounterSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color(0xFFB0BEC5),
            fontSize = 18.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineMeasuresEventListItem(
    index: Int,
    marker: TimelineMarker,
    isActive: Boolean,
    measuresStatus: TimelineMeasuresStatus?,
    positionDisplayMode: TimelineListPositionDisplayMode,
    onEditPosition: (Int, TimelineMeasuresStatus?) -> Unit,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val kindLabel = timelineMarkerKindLabel(marker.kind)
    val markerTitle = when (marker.kind) {
        TimelineMarkerKind.DMX -> kindLabel
        else -> marker.label.ifBlank { kindLabel }
    }
    val positionLabel = if (positionDisplayMode == TimelineListPositionDisplayMode.MUSIC) {
        measuresStatus?.let { status ->
            "%02d %s %02d %s %02d".format(
                status.currentBar,
                stringResource(R.string.timeline_counter_separator),
                status.currentBeat,
                stringResource(R.string.timeline_counter_separator),
                status.currentSubdivision
            )
        } ?: formatTimelineMarkerTime(marker.timeMs)
    } else {
        formatTimelineMarkerTime(marker.timeMs)
    }
    val rowText = stringResource(
        R.string.timeline_event_list_meta,
        markerTitle,
        positionLabel
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rowText,
            color = if (isActive) Color.White else Color(0xFFCFD8DC),
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = { onEditPosition(index, measuresStatus) },
                    onLongClick = null
                )
        )
        IconButton(onClick = onPlay) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.timeline_event_jump_action),
                tint = Color(0xFF80CBC4)
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.timeline_cd_rename),
                tint = Color(0xFF80CBC4)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.timeline_cd_delete),
                tint = Color(0xFFFF8A80)
            )
        }
    }
}

@Composable
private fun timelineMarkerKindLabel(kind: TimelineMarkerKind): String {
    return when (kind) {
        TimelineMarkerKind.TEXT -> stringResource(R.string.timeline_event_text)
        TimelineMarkerKind.MIDI -> stringResource(R.string.timeline_event_midi)
        TimelineMarkerKind.NOTE -> stringResource(R.string.timeline_event_note)
        TimelineMarkerKind.DMX -> stringResource(R.string.timeline_event_dmx)
    }
}

@Composable
private fun GridSetupHost(
    currentSongId: String?,
    fallbackTempoBpm: Int?,
    measureAnchorMs: Long?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    seekToMs: (Long) -> Unit,
    onIsPlayingChange: (Boolean) -> Unit,
    onOpenArrangement: () -> Unit,
    isPreparedClipLoopTestActive: Boolean,
    onStartPreparedClipLoopTest: (Long, Long) -> Unit,
    onStopPreparedClipLoopTest: () -> Unit,
    structurePreviewStopRequest: Int,
    onStructurePreviewActiveChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gridTempoBpm by remember(currentSongId) { mutableStateOf(fallbackTempoBpm?.toDouble()) }
    var gridTempoDraft by remember(currentSongId) { mutableStateOf(fallbackTempoBpm?.toString().orEmpty()) }
    var gridSyncPointMs by remember(currentSongId) { mutableStateOf(measureAnchorMs) }
    var gridInMs by remember(currentSongId) { mutableStateOf<Long?>(null) }
    var gridOutMs by remember(currentSongId) { mutableStateOf<Long?>(null) }

    LaunchedEffect(currentSongId, fallbackTempoBpm, measureAnchorMs) {
        val songId = currentSongId?.trim().orEmpty()
        val fallbackData = GridSetupData(
            tempoBpm = fallbackTempoBpm?.toDouble(),
            syncPointMs = measureAnchorMs
        )
        val gridData = if (songId.isNotEmpty()) {
            GridSetupStore.load(context, songId)
        } else {
            null
        } ?: fallbackData

        gridTempoBpm = gridData.tempoBpm
        gridTempoDraft = gridData.tempoBpm
            ?.roundToInt()
            ?.toString()
            .orEmpty()
        gridSyncPointMs = gridData.syncPointMs
        gridInMs = gridData.inMs
        gridOutMs = gridData.outMs
    }

    fun saveGridSetup(
        nextTempoBpm: Double?,
        nextSyncPointMs: Long?,
        nextInMs: Long? = gridInMs,
        nextOutMs: Long? = gridOutMs
    ) {
        val songId = currentSongId?.trim().orEmpty()
        if (songId.isEmpty()) return
        scope.launch {
            GridSetupStore.save(
                context = context,
                songId = songId,
                data = GridSetupData(
                    tempoBpm = nextTempoBpm,
                    syncPointMs = nextSyncPointMs,
                    inMs = nextInMs,
                    outMs = nextOutMs
                )
            )
        }
    }

    val isTempoInvalid = remember(gridTempoDraft) {
        val sanitizedTempoDraft = gridTempoDraft.filter { it.isDigit() }.take(3)
        val parsedTempoBpm = sanitizedTempoDraft.toIntOrNull()
        sanitizedTempoDraft.isNotBlank() &&
            (parsedTempoBpm == null ||
                parsedTempoBpm !in TrackTimelineTempoPrefs.MIN_TEMPO_BPM..TrackTimelineTempoPrefs.MAX_TEMPO_BPM)
    }

    TimelineMeasuresPlaceholder(
        currentSongId = currentSongId,
        tempoDraft = gridTempoDraft,
        isTempoInvalid = isTempoInvalid,
        tempoBpm = gridTempoBpm?.roundToInt(),
        measureAnchorMs = gridSyncPointMs,
        initialInMs = gridInMs,
        initialOutMs = gridOutMs,
        isPlaying = isPlaying,
        currentPositionMs = currentPositionMs,
        seekToMs = seekToMs,
        onIsPlayingChange = onIsPlayingChange,
        onOpenArrangement = onOpenArrangement,
        onMeasureAnchorHere = { anchorMs ->
            gridSyncPointMs = anchorMs
            saveGridSetup(gridTempoBpm, anchorMs)
        },
        onInitialSyncPointIfMissing = { anchorMs ->
            if (gridSyncPointMs == null) {
                gridSyncPointMs = anchorMs
                saveGridSetup(gridTempoBpm, anchorMs)
            }
        },
        onSegmentInChange = { nextInMs ->
            gridInMs = nextInMs
            saveGridSetup(gridTempoBpm, gridSyncPointMs, nextInMs, gridOutMs)
        },
        onSegmentOutChange = { nextOutMs ->
            gridOutMs = nextOutMs
            saveGridSetup(gridTempoBpm, gridSyncPointMs, gridInMs, nextOutMs)
        },
        isPreparedClipLoopTestActive = isPreparedClipLoopTestActive,
        onStartPreparedClipLoopTest = onStartPreparedClipLoopTest,
        onStopPreparedClipLoopTest = onStopPreparedClipLoopTest,
        structurePreviewStopRequest = structurePreviewStopRequest,
        onStructurePreviewActiveChange = onStructurePreviewActiveChange,
        onTempoDraftChange = { input ->
            val nextDraft = input.filter { ch -> ch.isDigit() }.take(3)
            gridTempoDraft = nextDraft
            val nextTempoBpm = nextDraft.toIntOrNull()
            if (nextTempoBpm != null &&
                nextTempoBpm in TrackTimelineTempoPrefs.MIN_TEMPO_BPM..TrackTimelineTempoPrefs.MAX_TEMPO_BPM
            ) {
                val nextTempoBpmValue = nextTempoBpm.toDouble()
                gridTempoBpm = nextTempoBpmValue
                saveGridSetup(nextTempoBpmValue, gridSyncPointMs)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineMeasuresPlaceholder(
    currentSongId: String?,
    tempoDraft: String,
    isTempoInvalid: Boolean,
    tempoBpm: Int?,
    measureAnchorMs: Long?,
    initialInMs: Long?,
    initialOutMs: Long?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    seekToMs: (Long) -> Unit,
    onIsPlayingChange: (Boolean) -> Unit,
    onOpenArrangement: () -> Unit,
    onMeasureAnchorHere: (Long) -> Unit,
    onInitialSyncPointIfMissing: (Long) -> Unit,
    onSegmentInChange: (Long?) -> Unit,
    onSegmentOutChange: (Long?) -> Unit,
    isPreparedClipLoopTestActive: Boolean,
    onStartPreparedClipLoopTest: (Long, Long) -> Unit,
    onStopPreparedClipLoopTest: () -> Unit,
    structurePreviewStopRequest: Int,
    onStructurePreviewActiveChange: (Boolean) -> Unit,
    onTempoDraftChange: (String) -> Unit
) {
    val structurePreviewFadeDurationMs = 12L
    var localMeasureAnchorMs by remember(measureAnchorMs) { mutableStateOf(measureAnchorMs) }
    var tempoTapTimesMs by remember { mutableStateOf<List<Long>>(emptyList()) }
    var showTapTempoHint by remember { mutableStateOf(false) }
    var metronomeEnabled by remember { mutableStateOf(false) }
    var loopEnabled by remember { mutableStateOf(false) }
    var loopLengthBars by remember { mutableIntStateOf(1) }
    var loopLengthMenuExpanded by remember { mutableStateOf(false) }
    var revealSyncPointRequest by remember { mutableIntStateOf(0) }
    var preparedLoopStartMs by remember(currentSongId) { mutableStateOf<Long?>(null) }
    val savedAnchorMs = localMeasureAnchorMs
    val displayedCurrentPositionMs = if (isPreparedClipLoopTestActive && preparedLoopStartMs != null) {
        preparedLoopStartMs!! + currentPositionMs.coerceAtLeast(0L)
    } else {
        currentPositionMs
    }
    val measuresStatus = remember(tempoBpm, localMeasureAnchorMs, displayedCurrentPositionMs) {
        computeTimelineMeasuresStatus(
            tempoBpm = tempoBpm,
            measureAnchorMs = localMeasureAnchorMs,
            currentPositionMs = displayedCurrentPositionMs
        )
    }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val smpLibraryScanner = remember(context) { SmpLibraryScanner(context.applicationContext) }
    val structurePreviewPlayer = remember(context.applicationContext) {
        ExoPlayer.Builder(context.applicationContext).build().apply { playWhenReady = false }
    }
    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
    }
    var currentSongAudioPath by remember(currentSongId) { mutableStateOf<String?>(null) }
    var waveformPeaks by remember(currentSongId) { mutableStateOf<List<Float>>(emptyList()) }
    var waveformDurationMs by remember(currentSongId) { mutableIntStateOf(0) }
    var waveformLoading by remember(currentSongId) { mutableStateOf(false) }
    var waveformError by remember(currentSongId) { mutableStateOf(false) }
    var gridEnabled by remember(currentSongId) { mutableStateOf(true) }
    var isWaveformExpanded by remember { mutableStateOf(false) }
    var segmentInMs by remember(currentSongId) { mutableStateOf(initialInMs) }
    var segmentOutMs by remember(currentSongId) { mutableStateOf(initialOutMs) }
    var lastWaveformFocusMarker by remember(currentSongId) {
        mutableStateOf(TimelineWaveformFocusMarker.NONE)
    }
    var selectedSegmentLoopId by remember(currentSongId) { mutableStateOf<String?>(null) }
    var selectedSegmentLoopStartMs by remember(currentSongId) { mutableStateOf<Long?>(null) }
    var selectedSegmentLoopEndMs by remember(currentSongId) { mutableStateOf<Long?>(null) }
    var structurePlaybackActive by remember(currentSongId) { mutableStateOf(false) }
    var structurePlaybackIndex by remember(currentSongId) { mutableIntStateOf(-1) }
    var arrangementName by remember(currentSongId) { mutableStateOf("Arrangement 1") }
    var arrangementSegments by remember(currentSongId) { mutableStateOf<List<ArrangementSegmentData>>(emptyList()) }
    var structureSegmentIds by remember(currentSongId) { mutableStateOf<List<String>>(emptyList()) }
    var nextSegmentIndex by remember(currentSongId) { mutableLongStateOf(1L) }
    var renameSegmentId by remember(currentSongId) { mutableStateOf<String?>(null) }
    var renameDraft by remember(currentSongId) { mutableStateOf(TextFieldValue("")) }
    var segmentOptionsTargetId by remember(currentSongId) { mutableStateOf<String?>(null) }
    var segmentSelectionMode by remember(currentSongId) {
        mutableStateOf(TimelineSegmentSelectionMode.KEEP)
    }
    var suppressNextLoopAutoplay by remember(currentSongId) { mutableStateOf(false) }

    fun stopStructurePreviewPlayback() {
        runCatching { structurePreviewPlayer.pause() }
        runCatching { structurePreviewPlayer.stop() }
        runCatching { structurePreviewPlayer.clearMediaItems() }
        runCatching { structurePreviewPlayer.volume = 1f }
        structurePlaybackActive = false
        structurePlaybackIndex = -1
        onStructurePreviewActiveChange(false)
    }

    fun persistArrangementState(
        nextSegments: List<ArrangementSegmentData> = arrangementSegments,
        nextStructureSegmentIds: List<String> = structureSegmentIds
    ) {
        val songId = currentSongId?.trim().orEmpty()
        if (songId.isEmpty()) return
        scope.launch {
            ArrangementStore.save(
                context = context.applicationContext,
                songId = songId,
                data = ArrangementData(
                    name = arrangementName.ifBlank { "Arrangement 1" },
                    sourceSongId = songId,
                    segments = nextSegments,
                    structureSegmentIds = nextStructureSegmentIds
                )
            )
        }
    }

    LaunchedEffect(measureAnchorMs) {
        localMeasureAnchorMs = measureAnchorMs
    }

    LaunchedEffect(initialInMs, initialOutMs) {
        segmentInMs = initialInMs
        segmentOutMs = initialOutMs
    }

    LaunchedEffect(isPreparedClipLoopTestActive) {
        if (!isPreparedClipLoopTestActive) {
            preparedLoopStartMs = null
        }
    }

    LaunchedEffect(showTapTempoHint) {
        if (showTapTempoHint) {
            kotlinx.coroutines.delay(1_800L)
            showTapTempoHint = false
        }
    }

    LaunchedEffect(currentSongId) {
        val songId = currentSongId?.trim().orEmpty()
        stopStructurePreviewPlayback()
        currentSongAudioPath = null
        if (songId.isEmpty()) {
            waveformPeaks = emptyList()
            waveformDurationMs = 0
            waveformLoading = false
            waveformError = false
            arrangementName = "Arrangement 1"
            arrangementSegments = emptyList()
            structureSegmentIds = emptyList()
            nextSegmentIndex = 1L
            renameSegmentId = null
            renameDraft = TextFieldValue("")
            segmentOptionsTargetId = null
            selectedSegmentLoopId = null
            selectedSegmentLoopStartMs = null
            selectedSegmentLoopEndMs = null
            structurePlaybackActive = false
            structurePlaybackIndex = -1
            return@LaunchedEffect
        }

        waveformLoading = true
        waveformError = false
        waveformPeaks = emptyList()
        waveformDurationMs = 0

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val song = smpLibraryScanner.findSongById(songId)
                    ?: error("Song not found")
                val audioPath = song.audioPath?.takeIf { it.isNotBlank() }
                    ?: error("Missing audio path")
                val audioUri = Uri.fromFile(File(audioPath))
                val durationMs = queryTimelineWaveformDurationMs(context, audioUri)
                val peaks = WaveformPeaksCache.getOrCompute(
                    context = context,
                    uri = audioUri,
                    targetPoints = 2_400,
                    durationMs = durationMs
                ) {
                    WaveformExtractor.extractNormalizedPeaks(
                        context = context,
                        uri = audioUri,
                        targetPoints = 2_400
                    )
                }
                Triple(peaks, durationMs, audioPath)
            }
        }

        result
            .onSuccess { (peaks, durationMs, audioPath) ->
                waveformPeaks = peaks
                waveformDurationMs = durationMs
                currentSongAudioPath = audioPath
                waveformLoading = false
            }
            .onFailure {
                waveformPeaks = emptyList()
                waveformDurationMs = 0
                currentSongAudioPath = null
                waveformLoading = false
                waveformError = true
            }

        val arrangementData = ArrangementStore.load(context.applicationContext, songId)
        arrangementName = arrangementData?.name?.ifBlank { "Arrangement 1" } ?: "Arrangement 1"
        arrangementSegments = arrangementData?.segments.orEmpty()
        structureSegmentIds = arrangementData?.structureSegmentIds.orEmpty()
        nextSegmentIndex = resolveNextTimelineArrangementSegmentIndex(arrangementSegments)
        renameSegmentId = null
        renameDraft = TextFieldValue("")
        segmentOptionsTargetId = null
        selectedSegmentLoopId = null
        selectedSegmentLoopStartMs = null
        selectedSegmentLoopEndMs = null
        structurePlaybackActive = false
        structurePlaybackIndex = -1
        suppressNextLoopAutoplay = false
    }

    val detectedTempoBpm = remember(tempoTapTimesMs) {
        estimateTappedTempoBpm(tempoTapTimesMs)
    }
    val defaultSegmentNameBase = stringResource(R.string.arrangement_segment_default_name)
    val metronomeReady = tempoBpm != null && savedAnchorMs != null
    val loopReady = tempoBpm != null && savedAnchorMs != null
    val hasSegmentLoop = segmentInMs != null && segmentOutMs != null && segmentInMs != segmentOutMs
    val hasSelectedSegmentLoop =
        selectedSegmentLoopStartMs != null &&
            selectedSegmentLoopEndMs != null &&
            selectedSegmentLoopStartMs != selectedSegmentLoopEndMs
    val structurePlaybackSegments = remember(arrangementSegments, structureSegmentIds) {
        structureSegmentIds.mapNotNull { segmentId ->
            arrangementSegments.firstOrNull { it.id == segmentId }
        }
    }
    val activeStructureSegmentId = structurePlaybackSegments
        .getOrNull(structurePlaybackIndex)
        ?.id
    val isLoopHighlighted =
        (loopReady || hasSegmentLoop || hasSelectedSegmentLoop) &&
            (loopEnabled || isPreparedClipLoopTestActive)
    val loopLengthLabel = stringResource(
        when (loopLengthBars) {
            4 -> R.string.timeline_measures_loop_length_4
            8 -> R.string.timeline_measures_loop_length_8
            16 -> R.string.timeline_measures_loop_length_16
            32 -> R.string.timeline_measures_loop_length_32
            else -> R.string.timeline_measures_loop_length_1
        }
    )

    val listenAction: () -> Unit = {
        if (structurePlaybackActive) {
            stopStructurePreviewPlayback()
        } else if (loopEnabled && (loopReady || hasSegmentLoop || hasSelectedSegmentLoop)) {
            val customLoopStartMs = selectedSegmentLoopStartMs ?: segmentInMs
            val customLoopEndMs = selectedSegmentLoopEndMs ?: segmentOutMs
            if (customLoopStartMs != null &&
                customLoopEndMs != null &&
                customLoopStartMs != customLoopEndMs
            ) {
                val loopStartMs = minOf(customLoopStartMs, customLoopEndMs).coerceAtLeast(0L)
                val loopEndMs = maxOf(customLoopStartMs, customLoopEndMs).coerceAtLeast(loopStartMs + 1L)
                preparedLoopStartMs = loopStartMs
                onStartPreparedClipLoopTest(loopStartMs, loopEndMs)
            } else {
                val safeAnchorMs = savedAnchorMs
                val safeTempoBpm = tempoBpm
                if (safeAnchorMs != null && safeTempoBpm != null) {
                    val barDurationMs = ((60_000.0 / safeTempoBpm.toDouble()) * 4.0)
                        .roundToLong()
                        .coerceAtLeast(1L)
                    preparedLoopStartMs = safeAnchorMs
                    onStartPreparedClipLoopTest(
                        safeAnchorMs,
                        safeAnchorMs + (barDurationMs * loopLengthBars.toLong())
                    )
                }
            }
        } else {
            onIsPlayingChange(true)
            runCatching { FillerSoundManager.fadeOutAndStop(200) }
        }
    }

    DisposableEffect(toneGenerator) {
        onDispose {
            runCatching { toneGenerator?.release() }
        }
    }
    DisposableEffect(structurePreviewPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (structurePlaybackActive) {
                    structurePlaybackIndex = structurePreviewPlayer.currentMediaItemIndex
                        .coerceAtLeast(0)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    stopStructurePreviewPlayback()
                }
            }
        }
        structurePreviewPlayer.addListener(listener)
        onDispose {
            structurePreviewPlayer.removeListener(listener)
            stopStructurePreviewPlayback()
            runCatching { structurePreviewPlayer.release() }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (isPreparedClipLoopTestActive) {
                onStopPreparedClipLoopTest()
            }
        }
    }
    LaunchedEffect(structurePreviewStopRequest) {
        if (structurePreviewStopRequest > 0) {
            stopStructurePreviewPlayback()
        }
    }
    LaunchedEffect(structurePlaybackActive, structurePreviewPlayer) {
        if (!structurePlaybackActive) {
            runCatching { structurePreviewPlayer.volume = 1f }
            return@LaunchedEffect
        }
        while (structurePlaybackActive) {
            val isSecondaryPlaying = runCatching { structurePreviewPlayer.isPlaying }.getOrDefault(false)
            val itemDurationMs = runCatching { structurePreviewPlayer.duration }.getOrDefault(0L)
            val itemPositionMs = runCatching { structurePreviewPlayer.currentPosition }.getOrDefault(0L)
            val nextVolume = if (!isSecondaryPlaying || itemDurationMs <= 0L) {
                1f
            } else {
                val safePositionMs = itemPositionMs.coerceAtLeast(0L)
                val safeDurationMs = itemDurationMs.coerceAtLeast(1L)
                val remainingMs = (safeDurationMs - safePositionMs).coerceAtLeast(0L)
                val fadeInGain = (safePositionMs.toFloat() / structurePreviewFadeDurationMs.toFloat())
                    .coerceIn(0f, 1f)
                val fadeOutGain = (remainingMs.toFloat() / structurePreviewFadeDurationMs.toFloat())
                    .coerceIn(0f, 1f)
                minOf(fadeInGain, fadeOutGain)
            }
            runCatching { structurePreviewPlayer.volume = nextVolume }
            kotlinx.coroutines.delay(8L)
        }
    }

    LaunchedEffect(detectedTempoBpm) {
        val detected = detectedTempoBpm ?: return@LaunchedEffect
        val detectedText = detected.toString()
        if (tempoDraft != detectedText) {
            onTempoDraftChange(detectedText)
        }
    }

    LaunchedEffect(metronomeReady) {
        if (!metronomeReady) {
            metronomeEnabled = false
        }
    }

    LaunchedEffect(loopReady, hasSegmentLoop, hasSelectedSegmentLoop) {
        if (structurePlaybackActive) return@LaunchedEffect
        if (!loopReady && !hasSegmentLoop && !hasSelectedSegmentLoop) {
            loopEnabled = false
            if (isPreparedClipLoopTestActive) {
                onStopPreparedClipLoopTest()
            }
        }
    }

    LaunchedEffect(
        loopEnabled,
        tempoBpm,
        savedAnchorMs,
        loopLengthBars,
        segmentInMs,
        segmentOutMs,
        selectedSegmentLoopStartMs,
        selectedSegmentLoopEndMs
    ) {
        if (structurePlaybackActive) return@LaunchedEffect
        if (!loopEnabled) {
            if (isPreparedClipLoopTestActive) {
                onStopPreparedClipLoopTest()
            }
            preparedLoopStartMs = null
            selectedSegmentLoopId = null
            selectedSegmentLoopStartMs = null
            selectedSegmentLoopEndMs = null
            suppressNextLoopAutoplay = false
            return@LaunchedEffect
        }

        val customLoopStartMs = selectedSegmentLoopStartMs ?: segmentInMs
        val customLoopEndMs = selectedSegmentLoopEndMs ?: segmentOutMs
        if (suppressNextLoopAutoplay && !isPlaying) {
            suppressNextLoopAutoplay = false
            return@LaunchedEffect
        }
        suppressNextLoopAutoplay = false
        if (customLoopStartMs != null && customLoopEndMs != null && customLoopStartMs != customLoopEndMs) {
            val loopStartMs = minOf(customLoopStartMs, customLoopEndMs).coerceAtLeast(0L)
            val loopEndMs = maxOf(customLoopStartMs, customLoopEndMs).coerceAtLeast(loopStartMs + 1L)
            preparedLoopStartMs = loopStartMs
            onIsPlayingChange(true)
            onStartPreparedClipLoopTest(loopStartMs, loopEndMs)
            return@LaunchedEffect
        }

        val safeTempoBpm = tempoBpm ?: return@LaunchedEffect
        val safeAnchorMs = savedAnchorMs ?: return@LaunchedEffect
        val barDurationMs = ((60_000.0 / safeTempoBpm.toDouble()) * 4.0)
            .roundToLong()
            .coerceAtLeast(1L)
        preparedLoopStartMs = safeAnchorMs
        onIsPlayingChange(true)
        onStartPreparedClipLoopTest(
            safeAnchorMs,
            safeAnchorMs + (barDurationMs * loopLengthBars.toLong())
        )
    }

    LaunchedEffect(metronomeEnabled, isPlaying, tempoBpm, savedAnchorMs) {
        val safeTempoBpm = tempoBpm ?: return@LaunchedEffect
        val safeAnchorMs = savedAnchorMs ?: return@LaunchedEffect
        val generator = toneGenerator ?: return@LaunchedEffect
        if (!metronomeEnabled || !isPlaying) return@LaunchedEffect

        val beatDurationMs = 60_000.0 / safeTempoBpm.toDouble()
        val firstStartedPositionMs = kotlinx.coroutines.withTimeoutOrNull(800L) {
            snapshotFlow { currentPositionMs }
                .first { latestPositionMs -> latestPositionMs >= safeAnchorMs + 20L }
        }
        val stabilizedStartPositionMs = if (firstStartedPositionMs != null) {
            kotlinx.coroutines.withTimeoutOrNull(160L) {
                snapshotFlow { currentPositionMs }
                    .first { latestPositionMs -> latestPositionMs >= firstStartedPositionMs + 10L }
            }?.toDouble() ?: firstStartedPositionMs.toDouble()
        } else {
            currentPositionMs.toDouble().coerceAtLeast(safeAnchorMs.toDouble())
        }
        var syncPositionMs = stabilizedStartPositionMs
        var syncRealtimeMs = SystemClock.elapsedRealtime().toDouble()
        var lastBeepBeatIndex = Long.MIN_VALUE

        launch {
            snapshotFlow { currentPositionMs }
                .collect { latestPositionMs ->
                    val nowRealtimeMs = SystemClock.elapsedRealtime().toDouble()
                    val predictedPositionMs = syncPositionMs + (nowRealtimeMs - syncRealtimeMs)
                    if (abs(latestPositionMs - predictedPositionMs) > 120.0) {
                        syncPositionMs = latestPositionMs.toDouble()
                        syncRealtimeMs = nowRealtimeMs
                    }
                }
        }

        val confirmedPositionMs = kotlinx.coroutines.withTimeoutOrNull(160L) {
            snapshotFlow { currentPositionMs }
                .first { latestPositionMs -> latestPositionMs >= syncPositionMs + 5.0 }
        }?.toDouble()
        if (confirmedPositionMs != null) {
            syncPositionMs = confirmedPositionMs
            syncRealtimeMs = SystemClock.elapsedRealtime().toDouble()
        }
        val initialRelativeMs = syncPositionMs - safeAnchorMs.toDouble()
        if (initialRelativeMs in 0.0..minOf(120.0, beatDurationMs / 2.0)) {
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
            lastBeepBeatIndex = 0L
        }

        while (true) {
            val nowRealtimeMs = SystemClock.elapsedRealtime().toDouble()
            val estimatedPositionMs = syncPositionMs + (nowRealtimeMs - syncRealtimeMs)
            val relativeMs = estimatedPositionMs - safeAnchorMs.toDouble()
            val nextBeatIndex = when {
                relativeMs <= 0.0 -> 0L
                else -> floor(relativeMs / beatDurationMs).toLong() + 1L
            }
            val nextBeatPositionMs = safeAnchorMs + nextBeatIndex * beatDurationMs
            val delayMs = (nextBeatPositionMs - estimatedPositionMs).coerceAtLeast(15.0)
            kotlinx.coroutines.delay(delayMs.roundToLong())
            if (nextBeatIndex != lastBeepBeatIndex) {
                generator.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
                lastBeepBeatIndex = nextBeatIndex
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val nextTapTimeMs = SystemClock.elapsedRealtime()
                            val previousTapTimeMs = tempoTapTimesMs.lastOrNull()
                            tempoTapTimesMs = when {
                                previousTapTimeMs == null -> listOf(nextTapTimeMs)
                                nextTapTimeMs <= previousTapTimeMs -> listOf(nextTapTimeMs)
                                nextTapTimeMs - previousTapTimeMs > 3_000L -> listOf(nextTapTimeMs)
                                else -> (tempoTapTimesMs + nextTapTimeMs).takeLast(8)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.timeline_measures_tap_tempo_action))
                    }
                    BasicTextField(
                        value = tempoDraft,
                        onValueChange = onTempoDraftChange,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        textStyle = TextStyle(
                            color = if (isTempoInvalid) Color(0xFFFF8A80) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(Color(0xFF80CBC4)),
                        modifier = Modifier
                            .width(72.dp)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (tempoDraft.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.timeline_measures_tap_tempo_pending),
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    Text(
                        text = stringResource(R.string.timeline_measures_bpm_suffix),
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                    IconButton(
                        onClick = {
                            val nextLoopEnabled = !loopEnabled
                            loopEnabled = nextLoopEnabled
                            if (nextLoopEnabled && (savedAnchorMs != null || hasSegmentLoop || hasSelectedSegmentLoop)) {
                                revealSyncPointRequest += 1
                            }
                        },
                        enabled = loopReady || hasSegmentLoop || hasSelectedSegmentLoop,
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (isLoopHighlighted) {
                                    Color(0xFF2ECC71)
                                } else {
                                    Color.White.copy(alpha = 0.14f)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(
                                color = if (isLoopHighlighted) {
                                    Color(0xFF2ECC71).copy(alpha = 0.24f)
                                } else {
                                    Color.White.copy(alpha = 0.04f)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Text(
                            text = "\uD83D\uDD01",
                            color = if (loopReady || hasSegmentLoop) {
                                if (isLoopHighlighted) Color(0xFF2ECC71) else Color(0xFFB0BEC5)
                            } else {
                                Color(0xFF607D8B)
                            },
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (!hasSegmentLoop) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            TextButton(
                                onClick = { loopLengthMenuExpanded = true },
                                enabled = loopReady
                            ) {
                                Text(
                                    text = loopLengthLabel,
                                    color = if (loopReady) Color(0xFFB0BEC5) else Color(0xFF607D8B),
                                    fontSize = 12.sp
                                )
                            }
                            DropdownMenu(
                                expanded = loopLengthMenuExpanded,
                                onDismissRequest = { loopLengthMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.timeline_measures_loop_length_1)) },
                                    onClick = {
                                        loopLengthBars = 1
                                        loopLengthMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.timeline_measures_loop_length_4)) },
                                    onClick = {
                                        loopLengthBars = 4
                                        loopLengthMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.timeline_measures_loop_length_8)) },
                                    onClick = {
                                        loopLengthBars = 8
                                        loopLengthMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.timeline_measures_loop_length_16)) },
                                    onClick = {
                                        loopLengthBars = 16
                                        loopLengthMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.timeline_measures_loop_length_32)) },
                                    onClick = {
                                        loopLengthBars = 32
                                        loopLengthMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = if (gridEnabled) Color(0xFF43A047) else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .then(
                        if (gridEnabled) {
                            Modifier
                        } else {
                            Modifier.border(
                                width = 1.dp,
                                color = Color(0xFF455A64),
                                shape = RoundedCornerShape(4.dp)
                            )
                        }
                    )
                    .clickable { gridEnabled = !gridEnabled }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.arrangement_grid_toggle),
                    color = if (gridEnabled) Color.White else Color(0xFFB0BEC5),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            TimelineSegmentControlLabel(
                label = stringResource(R.string.timeline_measures_segment_in_action),
                value = segmentInMs?.let(::formatTimelineMarkerTime)
                    ?: stringResource(R.string.timeline_measures_segment_pending),
                enabled = true,
                onClick = {
                    val nextInMs = if (gridEnabled) {
                        if (localMeasureAnchorMs == null) {
                            displayedCurrentPositionMs.coerceAtLeast(0L)
                        } else {
                            quantizeTimelinePositionToBeat(
                                positionMs = displayedCurrentPositionMs,
                                tempoBpm = tempoBpm?.toDouble(),
                                syncPointMs = localMeasureAnchorMs
                            )
                        }
                    } else {
                        displayedCurrentPositionMs.coerceAtLeast(0L)
                    }
                    suppressNextLoopAutoplay = !isPlaying
                    segmentInMs = nextInMs
                    lastWaveformFocusMarker = TimelineWaveformFocusMarker.IN
                    onSegmentInChange(nextInMs)
                    if (gridEnabled && localMeasureAnchorMs == null) {
                        localMeasureAnchorMs = nextInMs
                        onInitialSyncPointIfMissing(nextInMs)
                    }
                }
            )
            Text(
                text = stringResource(
                    if (segmentSelectionMode == TimelineSegmentSelectionMode.KEEP) {
                        R.string.arrangement_add_mode_keep
                    } else {
                        R.string.arrangement_add_mode_remove
                    }
                ),
                color = if (segmentSelectionMode == TimelineSegmentSelectionMode.REMOVE) {
                    Color(0xFF66BB6A)
                } else {
                    Color(0xFF90A4AE)
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable {
                        segmentSelectionMode = if (segmentSelectionMode == TimelineSegmentSelectionMode.KEEP) {
                            TimelineSegmentSelectionMode.REMOVE
                        } else {
                            TimelineSegmentSelectionMode.KEEP
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            TimelineSegmentControlLabel(
                label = stringResource(R.string.timeline_measures_segment_out_action),
                value = segmentOutMs?.let(::formatTimelineMarkerTime)
                    ?: stringResource(R.string.timeline_measures_segment_pending),
                enabled = true,
                onClick = {
                    val nextOutMs = if (gridEnabled) {
                        if (localMeasureAnchorMs == null) {
                            displayedCurrentPositionMs.coerceAtLeast(0L)
                        } else {
                            quantizeTimelinePositionToBeat(
                                positionMs = displayedCurrentPositionMs,
                                tempoBpm = tempoBpm?.toDouble(),
                                syncPointMs = localMeasureAnchorMs
                            )
                        }
                    } else {
                        displayedCurrentPositionMs.coerceAtLeast(0L)
                    }
                    suppressNextLoopAutoplay = !isPlaying
                    segmentOutMs = nextOutMs
                    lastWaveformFocusMarker = TimelineWaveformFocusMarker.OUT
                    onSegmentOutChange(nextOutMs)
                }
            )
            Text(
                text = stringResource(R.string.timeline_measures_signature_default),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        TimelineGridWaveformSection(
            peaks = waveformPeaks,
            durationMs = waveformDurationMs,
            currentPositionMs = displayedCurrentPositionMs,
            isPlaying = isPlaying,
            isLoopViewLocked = loopEnabled || isPreparedClipLoopTestActive,
            tempoBpm = tempoBpm,
            measureAnchorMs = savedAnchorMs,
            segmentInMs = segmentInMs,
            segmentOutMs = segmentOutMs,
            focusMarker = lastWaveformFocusMarker,
            isRemoveMode = segmentSelectionMode == TimelineSegmentSelectionMode.REMOVE,
            isWaveformExpanded = isWaveformExpanded,
            isLoading = waveformLoading,
            hasError = waveformError,
            revealAnchorRequest = revealSyncPointRequest,
            onToggleExpanded = { isWaveformExpanded = !isWaveformExpanded },
            onSeekRequested = { seekToMs(it) },
            onWaveformPanStarted = {
                lastWaveformFocusMarker = TimelineWaveformFocusMarker.NONE
            },
            onWaveformLongPress = { selectedPositionMs ->
                val quantizedPositionMs = if (gridEnabled) {
                    quantizeTimelinePositionToBeat(
                        positionMs = selectedPositionMs,
                        tempoBpm = tempoBpm?.toDouble(),
                        syncPointMs = localMeasureAnchorMs
                    )
                } else {
                    selectedPositionMs.coerceAtLeast(0L)
                }
                val currentInMs = segmentInMs
                val currentOutMs = segmentOutMs
                when {
                    currentInMs != null && currentOutMs != null -> {
                        val distanceToIn = abs(quantizedPositionMs - currentInMs)
                        val distanceToOut = abs(quantizedPositionMs - currentOutMs)
                        if (distanceToIn <= distanceToOut) {
                            segmentInMs = quantizedPositionMs
                            lastWaveformFocusMarker = TimelineWaveformFocusMarker.IN
                            onSegmentInChange(quantizedPositionMs)
                        } else {
                            segmentOutMs = quantizedPositionMs
                            lastWaveformFocusMarker = TimelineWaveformFocusMarker.OUT
                            onSegmentOutChange(quantizedPositionMs)
                        }
                    }
                    currentInMs != null -> {
                        segmentInMs = quantizedPositionMs
                        lastWaveformFocusMarker = TimelineWaveformFocusMarker.IN
                        onSegmentInChange(quantizedPositionMs)
                    }
                    currentOutMs != null -> {
                        segmentOutMs = quantizedPositionMs
                        lastWaveformFocusMarker = TimelineWaveformFocusMarker.OUT
                        onSegmentOutChange(quantizedPositionMs)
                    }
                    else -> {
                        segmentInMs = quantizedPositionMs
                        lastWaveformFocusMarker = TimelineWaveformFocusMarker.IN
                        onSegmentInChange(quantizedPositionMs)
                    }
                }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.timeline_tempo_action_add),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable {
                        val rawStartMs = segmentInMs ?: return@clickable
                        val rawEndMs = segmentOutMs ?: return@clickable
                        if (rawStartMs == rawEndMs) return@clickable
                        val startMs = minOf(rawStartMs, rawEndMs)
                        val endMs = maxOf(rawStartMs, rawEndMs)
                        val totalDurationMs = waveformDurationMs.coerceAtLeast(0).toLong()
                        val segmentRanges = if (segmentSelectionMode == TimelineSegmentSelectionMode.REMOVE) {
                            buildList {
                                if (startMs > 0L) {
                                    add(0L to startMs)
                                }
                                if (totalDurationMs > 0L && endMs < totalDurationMs) {
                                    add(endMs to totalDurationMs)
                                }
                            }
                        } else {
                            listOf(startMs to endMs)
                        }
                        val validSegmentRanges = segmentRanges.filter { (segmentStartMs, segmentEndMs) ->
                            segmentEndMs > segmentStartMs
                        }
                        if (validSegmentRanges.isEmpty()) return@clickable

                        var nextIndex = nextSegmentIndex
                        val createdSegments = validSegmentRanges.map { (segmentStartMs, segmentEndMs) ->
                            ArrangementSegmentData(
                                id = "segment_$nextIndex",
                                name = "$defaultSegmentNameBase $nextIndex",
                                startMs = segmentStartMs,
                                endMs = segmentEndMs
                            ).also {
                                nextIndex += 1L
                            }
                        }
                        val nextSegments = arrangementSegments + createdSegments
                        arrangementSegments = nextSegments
                        nextSegmentIndex = nextIndex
                        persistArrangementState(nextSegments = nextSegments)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
            Text(
                text = stringResource(R.string.timeline_tempo_action_listen),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(onClick = listenAction)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
            Text(
                text = stringResource(R.string.timeline_tempo_action_export),
                color = Color(0xFF80CBC4),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onOpenArrangement)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArrangementListCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.arrangement_segments_title),
                emptyLabel = stringResource(R.string.arrangement_segments_empty),
                items = arrangementSegments.map { segment ->
                    ArrangementListItem(
                        id = segment.id,
                        title = segment.name,
                        isActive = when {
                            structurePlaybackActive -> activeStructureSegmentId == segment.id
                            else -> selectedSegmentLoopId == segment.id
                        }
                    )
                },
                onItemClick = { segmentId ->
                    val segment = arrangementSegments.firstOrNull { it.id == segmentId }
                        ?: return@ArrangementListCard
                    val loopStartMs = minOf(segment.startMs, segment.endMs).coerceAtLeast(0L)
                    val loopEndMs = maxOf(segment.startMs, segment.endMs).coerceAtLeast(loopStartMs + 1L)
                    stopStructurePreviewPlayback()
                    selectedSegmentLoopId = segment.id
                    selectedSegmentLoopStartMs = loopStartMs
                    selectedSegmentLoopEndMs = loopEndMs
                    preparedLoopStartMs = loopStartMs
                    loopEnabled = true
                    revealSyncPointRequest += 1
                    onStartPreparedClipLoopTest(loopStartMs, loopEndMs)
                },
                onItemAdd = { segmentId ->
                    if (arrangementSegments.any { it.id == segmentId }) {
                        val nextStructureSegmentIds = structureSegmentIds + segmentId
                        structureSegmentIds = nextStructureSegmentIds
                        persistArrangementState(nextStructureSegmentIds = nextStructureSegmentIds)
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
                    val segment = arrangementSegments.firstOrNull { it.id == segmentId } ?: return@mapIndexedNotNull null
                    ArrangementListItem(
                        id = index.toString(),
                        title = "${index + 1}. ${segment.name}",
                        isActive = structurePlaybackActive && index == structurePlaybackIndex
                    )
                },
                onItemClick = { structureIndexId ->
                    val startIndex = structureIndexId.toIntOrNull() ?: return@ArrangementListCard
                    if (startIndex !in structurePlaybackSegments.indices) return@ArrangementListCard
                    val audioPath = currentSongAudioPath ?: return@ArrangementListCard
                    val mediaItems = buildTimelineStructureMediaItems(
                        audioPath = audioPath,
                        segments = structurePlaybackSegments
                    )
                    if (mediaItems.isEmpty()) return@ArrangementListCard
                    stopStructurePreviewPlayback()
                    selectedSegmentLoopId = null
                    selectedSegmentLoopStartMs = null
                    selectedSegmentLoopEndMs = null
                    if (isPreparedClipLoopTestActive) {
                        onStopPreparedClipLoopTest()
                    }
                    loopEnabled = false
                    preparedLoopStartMs = null
                    if (isPlaying) {
                        onIsPlayingChange(false)
                    }
                    structurePlaybackActive = true
                    structurePlaybackIndex = startIndex
                    onStructurePreviewActiveChange(true)
                    structurePreviewPlayer.pause()
                    structurePreviewPlayer.clearMediaItems()
                    structurePreviewPlayer.repeatMode = Player.REPEAT_MODE_OFF
                    structurePreviewPlayer.volume = 1f
                    structurePreviewPlayer.setMediaItems(mediaItems, startIndex, 0L)
                    structurePreviewPlayer.prepare()
                    structurePreviewPlayer.play()
                },
                onItemAdd = null,
                onItemDelete = { structureIndexId ->
                    val removeIndex = structureIndexId.toIntOrNull() ?: return@ArrangementListCard
                    if (removeIndex in structureSegmentIds.indices) {
                        val nextStructureSegmentIds = structureSegmentIds.toMutableList().apply {
                            removeAt(removeIndex)
                        }
                        if (structurePlaybackActive) {
                            when {
                                removeIndex < structurePlaybackIndex -> {
                                    structurePlaybackIndex -= 1
                                }
                                removeIndex == structurePlaybackIndex -> {
                                    stopStructurePreviewPlayback()
                                }
                            }
                        }
                        structureSegmentIds = nextStructureSegmentIds
                        persistArrangementState(nextStructureSegmentIds = nextStructureSegmentIds)
                    }
                },
                onItemLongClick = null
            )
        }
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

    if (renameSegmentId != null) {
        androidx.compose.material3.AlertDialog(
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
                            arrangementSegments.firstOrNull { it.id == targetId }?.name ?: return@Button
                        }
                        val nextSegments = arrangementSegments.map { segment ->
                            if (segment.id == targetId) {
                                segment.copy(name = nextName)
                            } else {
                                segment
                            }
                        }
                        arrangementSegments = nextSegments
                        persistArrangementState(nextSegments = nextSegments)
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { segmentOptionsTargetId = null },
            title = {
                Text(
                    text = arrangementSegments.firstOrNull { it.id == segmentOptionsTargetId }?.name.orEmpty(),
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = stringResource(R.string.common_rename),
                        color = Color.White,
                        modifier = Modifier.clickable {
                            val segment = arrangementSegments.firstOrNull { it.id == segmentOptionsTargetId }
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
                            val nextSegments = arrangementSegments.filterNot { it.id == targetId }
                            val nextStructureSegmentIds = structureSegmentIds.filterNot { it == targetId }
                            arrangementSegments = nextSegments
                            structureSegmentIds = nextStructureSegmentIds
                            if (selectedSegmentLoopId == targetId) {
                                selectedSegmentLoopId = null
                                selectedSegmentLoopStartMs = null
                                selectedSegmentLoopEndMs = null
                            }
                            nextSegmentIndex = resolveNextTimelineArrangementSegmentIndex(nextSegments)
                            persistArrangementState(
                                nextSegments = nextSegments,
                                nextStructureSegmentIds = nextStructureSegmentIds
                            )
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

@Composable
private fun TimelineGridWaveformSection(
    peaks: List<Float>,
    durationMs: Int,
    currentPositionMs: Long,
    isPlaying: Boolean,
    isLoopViewLocked: Boolean,
    tempoBpm: Int?,
    measureAnchorMs: Long?,
    segmentInMs: Long?,
    segmentOutMs: Long?,
    focusMarker: TimelineWaveformFocusMarker,
    isRemoveMode: Boolean,
    isWaveformExpanded: Boolean,
    isLoading: Boolean,
    hasError: Boolean,
    revealAnchorRequest: Int,
    onToggleExpanded: () -> Unit,
    onSeekRequested: (Long) -> Unit,
    onWaveformPanStarted: () -> Unit,
    onWaveformLongPress: (Long) -> Unit
) {
    var waveformZoom by remember(peaks, durationMs) { mutableStateOf(1f) }
    var waveformCenterFraction by remember(peaks, durationMs) { mutableStateOf(0.5f) }
    var lastManualWaveformInteractionMs by remember(peaks, durationMs) { mutableLongStateOf(0L) }
    val waveformHeight by animateDpAsState(
        targetValue = if (isWaveformExpanded) 270.dp else 140.dp,
        label = "timelineWaveformHeight"
    )

    LaunchedEffect(
        isPlaying,
        isLoopViewLocked,
        currentPositionMs,
        durationMs,
        waveformZoom,
        lastManualWaveformInteractionMs
    ) {
        if (!isPlaying || durationMs <= 0) return@LaunchedEffect
        if (isLoopViewLocked) return@LaunchedEffect
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastManualWaveformInteractionMs < 1_200L) return@LaunchedEffect

        val visibleFraction = 1f / waveformZoom.coerceAtLeast(1f)
        val minCenter = visibleFraction / 2f
        val maxCenter = 1f - minCenter
        val currentFraction = (
            currentPositionMs.coerceIn(0L, durationMs.toLong()).toFloat() /
                durationMs.toFloat()
            ).coerceIn(0f, 1f)
        waveformCenterFraction = if (waveformZoom <= 1f) {
            0.5f
        } else {
            currentFraction.coerceIn(minCenter, maxCenter)
        }
    }

    LaunchedEffect(revealAnchorRequest) {
        val safeAnchorMs = measureAnchorMs ?: return@LaunchedEffect
        if (revealAnchorRequest <= 0 || durationMs <= 0) return@LaunchedEffect
        val safeDuration = durationMs.coerceAtLeast(1).toFloat()
        val visibleFraction = 1f / waveformZoom.coerceAtLeast(1f)
        val minCenter = visibleFraction / 2f
        val maxCenter = 1f - minCenter
        val anchorFraction = (safeAnchorMs.coerceIn(0L, durationMs.toLong()).toFloat() / safeDuration)
            .coerceIn(0f, 1f)
        val desiredAnchorScreenFraction = 0.35f
        val desiredCenter = anchorFraction + ((0.5f - desiredAnchorScreenFraction) * visibleFraction)
        waveformCenterFraction = desiredCenter.coerceIn(minCenter, maxCenter)
        lastManualWaveformInteractionMs = SystemClock.elapsedRealtime()
    }

    LaunchedEffect(isLoopViewLocked, segmentInMs, segmentOutMs, durationMs, waveformZoom) {
        if (!isLoopViewLocked || durationMs <= 0) return@LaunchedEffect
        val loopInMs = segmentInMs ?: return@LaunchedEffect
        val loopOutMs = segmentOutMs ?: return@LaunchedEffect
        if (loopInMs == loopOutMs) return@LaunchedEffect

        val safeDuration = durationMs.coerceAtLeast(1).toFloat()
        val loopCenterMs = ((minOf(loopInMs, loopOutMs) + maxOf(loopInMs, loopOutMs)) / 2.0)
            .roundToLong()
            .coerceIn(0L, durationMs.toLong())
        val loopCenterFraction = (loopCenterMs.toFloat() / safeDuration).coerceIn(0f, 1f)
        val visibleFraction = 1f / waveformZoom.coerceAtLeast(1f)
        val minCenter = visibleFraction / 2f
        val maxCenter = 1f - minCenter
        waveformCenterFraction = if (waveformZoom <= 1f) {
            0.5f
        } else {
            loopCenterFraction.coerceIn(minCenter, maxCenter)
        }
        lastManualWaveformInteractionMs = SystemClock.elapsedRealtime()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(waveformHeight)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                hasError -> {
                    Text(
                        text = stringResource(R.string.waveform_generate_error),
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                peaks.isEmpty() || durationMs <= 0 -> {
                    Text(
                        text = stringResource(R.string.waveform_no_data),
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(waveformHeight)
                            .pointerInput(peaks, durationMs, waveformZoom, waveformCenterFraction) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        lastManualWaveformInteractionMs = SystemClock.elapsedRealtime()
                                        val safeDuration = durationMs.coerceAtLeast(1)
                                        val edgeDeadZonePx = 24.dp.toPx()
                                        if (offset.x <= edgeDeadZonePx ||
                                            offset.x >= size.width - edgeDeadZonePx
                                        ) {
                                            return@detectTapGestures
                                        }
                                        val visibleFraction = 1f / waveformZoom.coerceAtLeast(1f)
                                        val startFraction = (waveformCenterFraction - visibleFraction / 2f)
                                            .coerceIn(0f, 1f)
                                        val endFraction = (startFraction + visibleFraction).coerceIn(0f, 1f)
                                        val effectiveEndFraction = if (endFraction <= startFraction) {
                                            1f
                                        } else {
                                            endFraction
                                        }
                                        val localFraction = if (size.width > 0) {
                                            (offset.x / size.width).coerceIn(0f, 1f)
                                        } else {
                                            0f
                                        }
                                        val selectedFraction = startFraction +
                                            localFraction * (effectiveEndFraction - startFraction)
                                        onSeekRequested(
                                            (selectedFraction * safeDuration)
                                                .toLong()
                                                .coerceIn(0L, safeDuration.toLong())
                                        )
                                    },
                                    onLongPress = { offset ->
                                    lastManualWaveformInteractionMs = SystemClock.elapsedRealtime()
                                    val safeDuration = durationMs.coerceAtLeast(1)
                                    val edgeDeadZonePx = 24.dp.toPx()
                                    if (offset.x <= edgeDeadZonePx ||
                                        offset.x >= size.width - edgeDeadZonePx
                                    ) {
                                        return@detectTapGestures
                                    }
                                    val visibleFraction = 1f / waveformZoom.coerceAtLeast(1f)
                                    val startFraction = (waveformCenterFraction - visibleFraction / 2f)
                                        .coerceIn(0f, 1f)
                                    val endFraction = (startFraction + visibleFraction).coerceIn(0f, 1f)
                                    val effectiveEndFraction = if (endFraction <= startFraction) {
                                        1f
                                    } else {
                                        endFraction
                                    }
                                    val localFraction = if (size.width > 0) {
                                        (offset.x / size.width).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    val selectedFraction = startFraction +
                                        localFraction * (effectiveEndFraction - startFraction)
                                    onWaveformLongPress(
                                        (selectedFraction * safeDuration)
                                            .toLong()
                                            .coerceIn(0L, safeDuration.toLong())
                                    )
                                    }
                                )
                            }
                            .pointerInput(
                                peaks,
                                durationMs,
                                segmentInMs,
                                segmentOutMs,
                                focusMarker
                            ) {
                                detectTransformGestures { _, pan, zoomChange, _ ->
                                    lastManualWaveformInteractionMs = SystemClock.elapsedRealtime()
                                    if (abs(pan.x) > 0.5f) {
                                        onWaveformPanStarted()
                                    }
                                    val previousZoom = waveformZoom
                                    val nextZoom = (previousZoom * zoomChange).coerceIn(1f, 120f)
                                    waveformZoom = nextZoom

                                    val visibleFraction = 1f / nextZoom
                                    val panFraction = if (size.width > 0) {
                                        -pan.x / size.width * visibleFraction
                                    } else {
                                        0f
                                    }
                                    val minCenter = visibleFraction / 2f
                                    val maxCenter = 1f - minCenter
                                    val focusedCenterFraction = when (focusMarker) {
                                        TimelineWaveformFocusMarker.IN -> {
                                            segmentInMs
                                                ?.coerceIn(0L, durationMs.toLong())
                                                ?.toFloat()
                                                ?.div(durationMs.coerceAtLeast(1).toFloat())
                                        }
                                        TimelineWaveformFocusMarker.OUT -> {
                                            segmentOutMs
                                                ?.coerceIn(0L, durationMs.toLong())
                                                ?.toFloat()
                                                ?.div(durationMs.coerceAtLeast(1).toFloat())
                                        }
                                        TimelineWaveformFocusMarker.NONE -> null
                                    }
                                    waveformCenterFraction = if (nextZoom <= 1f) {
                                        0.5f
                                    } else if (focusedCenterFraction != null) {
                                        focusedCenterFraction.coerceIn(minCenter, maxCenter)
                                    } else {
                                        (waveformCenterFraction + panFraction)
                                            .coerceIn(minCenter, maxCenter)
                                    }
                                }
                            }
                    ) {
                        val centerY = size.height / 2f
                        val widthPx = size.width
                        val heightPx = size.height
                        val safeDuration = durationMs.coerceAtLeast(1)
                        val maxIndex = peaks.lastIndex.coerceAtLeast(1)
                        val visibleFraction = 1f / waveformZoom.coerceAtLeast(1f)
                        val startFraction = (waveformCenterFraction - visibleFraction / 2f).coerceIn(0f, 1f)
                        val endFraction = (startFraction + visibleFraction).coerceIn(0f, 1f)
                        val effectiveEndFraction = if (endFraction <= startFraction) 1f else endFraction
                        val waveformNormalColor = Color(0xFF80CBC4)
                        val waveformAccentColor = Color(0xFFB2FF59)
                        peaks.forEachIndexed { index, peak ->
                            val positionFraction = index.toFloat() / maxIndex.toFloat()
                            if (positionFraction < startFraction || positionFraction > effectiveEndFraction) {
                                return@forEachIndexed
                            }
                            val x = ((positionFraction - startFraction) /
                                (effectiveEndFraction - startFraction)) * widthPx
                            val normalizedPeak = peak
                                .coerceIn(0f, 1f)
                                .pow(0.7f)
                                .times(1.08f)
                                .coerceIn(0f, 1f)
                            val amplitude = normalizedPeak * (heightPx / 2f)
                            drawLine(
                                color = if (normalizedPeak > 0.6f) {
                                    waveformAccentColor
                                } else {
                                    waveformNormalColor
                                },
                                start = androidx.compose.ui.geometry.Offset(x, centerY - amplitude),
                                end = androidx.compose.ui.geometry.Offset(x, centerY + amplitude),
                                strokeWidth = 1f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            if (index < peaks.lastIndex) {
                                val nextPositionFraction = (index + 1).toFloat() / maxIndex.toFloat()
                                val midpointFraction = (positionFraction + nextPositionFraction) / 2f
                                if (midpointFraction in startFraction..effectiveEndFraction) {
                                    val midpointX = ((midpointFraction - startFraction) /
                                        (effectiveEndFraction - startFraction)) * widthPx
                                    val midpointPeak = ((peak + peaks[index + 1]) / 2f)
                                        .coerceIn(0f, 1f)
                                        .pow(0.7f)
                                        .times(1.08f)
                                        .coerceIn(0f, 1f)
                                    val midpointAmplitude = midpointPeak * (heightPx / 2f)
                                    drawLine(
                                        color = if (midpointPeak > 0.6f) {
                                            waveformAccentColor.copy(alpha = 0.9f)
                                        } else {
                                            waveformNormalColor.copy(alpha = 0.82f)
                                        },
                                        start = androidx.compose.ui.geometry.Offset(
                                            midpointX,
                                            centerY - midpointAmplitude
                                        ),
                                        end = androidx.compose.ui.geometry.Offset(
                                            midpointX,
                                            centerY + midpointAmplitude
                                        ),
                                        strokeWidth = 0.8f,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                }
                            }
                        }

                        if (tempoBpm != null && tempoBpm > 0 && measureAnchorMs != null) {
                            val visibleStartMs = startFraction * safeDuration.toFloat()
                            val visibleEndMs = effectiveEndFraction * safeDuration.toFloat()
                            val beatDurationMs = 60_000.0 / tempoBpm.toDouble()
                            val anchorMs = measureAnchorMs.toDouble()
                            var beatIndex = floor((visibleStartMs - anchorMs) / beatDurationMs).toLong()
                            if (anchorMs + beatIndex * beatDurationMs < visibleStartMs) {
                                beatIndex += 1L
                            }
                            var guard = 0
                            while (guard < 2048) {
                                val beatPositionMs = anchorMs + beatIndex * beatDurationMs
                                if (beatPositionMs > visibleEndMs) break
                                if (beatPositionMs >= 0.0) {
                                    val beatFraction = (beatPositionMs / safeDuration.toDouble()).toFloat()
                                    val beatX = ((beatFraction - startFraction) /
                                        (effectiveEndFraction - startFraction)) * widthPx
                                    val isBarStart = beatIndex.rem(4L) == 0L
                                    drawLine(
                                        color = if (isBarStart) {
                                            Color(0xCCFFF176)
                                        } else {
                                            Color(0x66FFF59D)
                                        },
                                        start = androidx.compose.ui.geometry.Offset(beatX, 0f),
                                        end = androidx.compose.ui.geometry.Offset(beatX, heightPx),
                                        strokeWidth = if (isBarStart) 2.4f else 1.2f
                                    )
                                }
                                beatIndex += 1L
                                guard += 1
                            }
                        }

                        if (segmentInMs != null && segmentOutMs != null && segmentInMs != segmentOutMs) {
                            val loopStartMs = minOf(segmentInMs, segmentOutMs).coerceAtLeast(0L)
                            val loopEndMs = maxOf(segmentInMs, segmentOutMs).coerceAtLeast(loopStartMs + 1L)
                            val loopStartFraction = (
                                loopStartMs.coerceIn(0L, safeDuration.toLong()).toFloat() /
                                    safeDuration.toFloat()
                                ).coerceIn(0f, 1f)
                            val loopEndFraction = (
                                loopEndMs.coerceIn(0L, safeDuration.toLong()).toFloat() /
                                    safeDuration.toFloat()
                                ).coerceIn(0f, 1f)

                            if (isRemoveMode) {
                                val overlayStartX = ((loopStartFraction - startFraction) /
                                    (effectiveEndFraction - startFraction)) * widthPx
                                val overlayEndX = ((loopEndFraction - startFraction) /
                                    (effectiveEndFraction - startFraction)) * widthPx
                                val clampedStartX = overlayStartX.coerceIn(0f, widthPx)
                                val clampedEndX = overlayEndX.coerceIn(0f, widthPx)
                                if (clampedEndX > clampedStartX) {
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.3f),
                                        topLeft = Offset(clampedStartX, 0f),
                                        size = androidx.compose.ui.geometry.Size(
                                            width = clampedEndX - clampedStartX,
                                            height = heightPx
                                        )
                                    )
                                }
                            } else {
                                if (loopStartFraction > startFraction) {
                                    val leftOverlayEndX = ((loopStartFraction - startFraction) /
                                        (effectiveEndFraction - startFraction)) * widthPx
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.3f),
                                        topLeft = Offset(0f, 0f),
                                        size = androidx.compose.ui.geometry.Size(
                                            width = leftOverlayEndX.coerceIn(0f, widthPx),
                                            height = heightPx
                                        )
                                    )
                                }

                                if (loopEndFraction < effectiveEndFraction) {
                                    val rightOverlayStartX = ((loopEndFraction - startFraction) /
                                        (effectiveEndFraction - startFraction)) * widthPx
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.3f),
                                        topLeft = Offset(rightOverlayStartX.coerceIn(0f, widthPx), 0f),
                                        size = androidx.compose.ui.geometry.Size(
                                            width = (widthPx - rightOverlayStartX).coerceAtLeast(0f),
                                            height = heightPx
                                        )
                                    )
                                }
                            }
                        }

                        val currentFraction = currentPositionMs
                            .coerceIn(0L, safeDuration.toLong())
                            .toFloat() / safeDuration.toFloat()
                        if (currentFraction in startFraction..effectiveEndFraction) {
                            val currentX = ((currentFraction - startFraction) /
                                (effectiveEndFraction - startFraction)) * widthPx
                            drawLine(
                                color = Color.White,
                                start = androidx.compose.ui.geometry.Offset(currentX, 0f),
                                end = androidx.compose.ui.geometry.Offset(currentX, heightPx),
                                strokeWidth = 2f
                            )
                        }

                        segmentInMs?.let { inMs ->
                            val inFraction = inMs
                                .coerceIn(0L, safeDuration.toLong())
                                .toFloat() / safeDuration.toFloat()
                            if (inFraction in startFraction..effectiveEndFraction) {
                                val inX = ((inFraction - startFraction) /
                                    (effectiveEndFraction - startFraction)) * widthPx
                                drawLine(
                                    color = Color(0xFFFF1744).copy(alpha = 0.3f),
                                    start = androidx.compose.ui.geometry.Offset(inX, 0f),
                                    end = androidx.compose.ui.geometry.Offset(inX, heightPx),
                                    strokeWidth = 10f
                                )
                                drawLine(
                                    color = Color(0xFFFF1744),
                                    start = androidx.compose.ui.geometry.Offset(inX, 0f),
                                    end = androidx.compose.ui.geometry.Offset(inX, heightPx),
                                    strokeWidth = 8f
                                )
                            }
                        }
                        segmentOutMs?.let { outMs ->
                            val outFraction = outMs
                                .coerceIn(0L, safeDuration.toLong())
                                .toFloat() / safeDuration.toFloat()
                            if (outFraction in startFraction..effectiveEndFraction) {
                                val outX = ((outFraction - startFraction) /
                                    (effectiveEndFraction - startFraction)) * widthPx
                                drawLine(
                                    color = Color(0xFFFFC107).copy(alpha = 0.28f),
                                    start = androidx.compose.ui.geometry.Offset(outX, 0f),
                                    end = androidx.compose.ui.geometry.Offset(outX, heightPx),
                                    strokeWidth = 10f
                                )
                                drawLine(
                                    color = Color(0xFFFFC107),
                                    start = androidx.compose.ui.geometry.Offset(outX, 0f),
                                    end = androidx.compose.ui.geometry.Offset(outX, heightPx),
                                    strokeWidth = 6f
                                )
                            }
                        }
                        if (segmentInMs == null) {
                            measureAnchorMs?.let { anchorMs ->
                                val anchorFraction = anchorMs
                                    .coerceIn(0L, safeDuration.toLong())
                                    .toFloat() / safeDuration.toFloat()
                                if (anchorFraction in startFraction..effectiveEndFraction) {
                                    val anchorX = ((anchorFraction - startFraction) /
                                        (effectiveEndFraction - startFraction)) * widthPx
                                    drawLine(
                                        color = Color(0xFFFF1744).copy(alpha = 0.3f),
                                        start = androidx.compose.ui.geometry.Offset(anchorX, 0f),
                                        end = androidx.compose.ui.geometry.Offset(anchorX, heightPx),
                                        strokeWidth = 10f
                                    )
                                    drawLine(
                                        color = Color(0xFFFF1744),
                                        start = androidx.compose.ui.geometry.Offset(anchorX, 0f),
                                        end = androidx.compose.ui.geometry.Offset(anchorX, heightPx),
                                        strokeWidth = 8f
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .background(Color(0xAA111111), RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggleExpanded() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isWaveformExpanded) "⤡" else "⤢",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TimelineSegmentControlLabel(
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

private fun resolveNextTimelineArrangementSegmentIndex(
    segments: List<ArrangementSegmentData>
): Long {
    val maxExistingIndex = segments.maxOfOrNull { segment ->
        segment.id.removePrefix("segment_").toLongOrNull() ?: 0L
    } ?: 0L
    return (maxExistingIndex + 1L).coerceAtLeast(1L)
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
    val currentSubdivision: Int,
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
        val currentStatus = computeTimelineMeasuresStatus(
            tempoBpm = tempoBpm,
            measureAnchorMs = measureAnchorMs,
            currentPositionMs = positionMs
        ) ?: return ""
        val previousStatus = computeTimelineMeasuresStatus(
            tempoBpm = tempoBpm,
            measureAnchorMs = measureAnchorMs,
            currentPositionMs = (positionMs - 1_000L).coerceAtLeast(0L)
        )
        return if (previousStatus == null || previousStatus.currentBar != currentStatus.currentBar) {
            currentStatus.currentBar.toString()
        } else {
            ""
        }
    }
    return formatTimelineMarkerTime(positionMs)
}

private fun computeTimelineMeasuresStatus(
    tempoBpm: Int?,
    measureAnchorMs: Long?,
    currentPositionMs: Long,
    clampBeforeAnchorToFirstMeasure: Boolean = false
): TimelineMeasuresStatus? {
    val safeTempoBpm = tempoBpm?.takeIf {
        it in TrackTimelineTempoPrefs.MIN_TEMPO_BPM..TrackTimelineTempoPrefs.MAX_TEMPO_BPM
    } ?: return null
    val safeAnchorMs = measureAnchorMs ?: return null
    if (currentPositionMs < safeAnchorMs && !clampBeforeAnchorToFirstMeasure) return null

    val beatDurationMs = 60_000.0 / safeTempoBpm.toDouble()
    val barDurationMs = beatDurationMs * 4.0
    val subdivisionDurationMs = beatDurationMs / 4.0
    if (beatDurationMs <= 0.0 || barDurationMs <= 0.0 || subdivisionDurationMs <= 0.0) return null

    val relativeMs = (currentPositionMs - safeAnchorMs).coerceAtLeast(0L)
    val beatIndex = kotlin.math.floor(relativeMs / beatDurationMs).toLong()
    val currentBar = (beatIndex / 4L).toInt() + 1
    val barOffsetMs = relativeMs % barDurationMs
    val currentBeat = kotlin.math.floor(barOffsetMs / beatDurationMs).toInt() + 1
    val beatOffsetMs = relativeMs % beatDurationMs
    val currentSubdivision = (
        kotlin.math.floor(beatOffsetMs / subdivisionDurationMs).toInt() + 1
        ).coerceIn(1, 4)

    return TimelineMeasuresStatus(
        currentBar = currentBar.coerceAtLeast(1),
        currentBeat = currentBeat.coerceIn(1, 4),
        currentSubdivision = currentSubdivision,
        beatIndex = beatIndex.coerceAtLeast(0L)
    )
}

private fun quantizeTimelinePositionToBeat(
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

private fun computeTimelinePositionMsFromMusicalPosition(
    tempoBpm: Int?,
    measureAnchorMs: Long?,
    measure: Int?,
    beat: Int?,
    subdivision: Int?
): Long? {
    val safeTempoBpm = tempoBpm?.takeIf {
        it in TrackTimelineTempoPrefs.MIN_TEMPO_BPM..TrackTimelineTempoPrefs.MAX_TEMPO_BPM
    } ?: return null
    val safeAnchorMs = measureAnchorMs ?: return null
    val safeMeasure = measure?.takeIf { it >= 1 } ?: return null
    val safeBeat = beat?.takeIf { it in 1..4 } ?: return null
    val safeSubdivision = subdivision?.takeIf { it in 1..4 } ?: return null

    val beatDurationMs = 60_000.0 / safeTempoBpm.toDouble()
    val subdivisionDurationMs = beatDurationMs / 4.0
    if (beatDurationMs <= 0.0 || subdivisionDurationMs <= 0.0) return null

    val relativeMs =
        ((safeMeasure - 1) * 4.0 * beatDurationMs) +
            ((safeBeat - 1) * beatDurationMs) +
            ((safeSubdivision - 1) * subdivisionDurationMs)

    return safeAnchorMs + relativeMs.roundToLong().coerceAtLeast(0L)
}

private fun computeTimelinePositionAfterSteps(
    tempoBpm: Int?,
    measureAnchorMs: Long?,
    currentPositionMs: Long,
    step: TimelineEditStep,
    steps: Int
): Long? {
    if (steps == 0) return currentPositionMs.coerceAtLeast(0L)
    val currentStatus = computeTimelineMeasuresStatus(
        tempoBpm = tempoBpm,
        measureAnchorMs = measureAnchorMs,
        currentPositionMs = currentPositionMs,
        clampBeforeAnchorToFirstMeasure = true
    ) ?: return null

    val currentSubdivisionIndex =
        ((currentStatus.currentBar - 1) * 16) +
            ((currentStatus.currentBeat - 1) * 4) +
            (currentStatus.currentSubdivision - 1)
    val deltaSubdivisions = when (step) {
        TimelineEditStep.MEASURE -> steps * 16
        TimelineEditStep.BEAT -> steps * 4
        TimelineEditStep.SUBDIVISION -> steps
    }
    val nextSubdivisionIndex = (currentSubdivisionIndex + deltaSubdivisions).coerceAtLeast(0)
    val nextMeasure = (nextSubdivisionIndex / 16) + 1
    val nextBeat = ((nextSubdivisionIndex % 16) / 4) + 1
    val nextSubdivision = (nextSubdivisionIndex % 4) + 1

    return computeTimelinePositionMsFromMusicalPosition(
        tempoBpm = tempoBpm,
        measureAnchorMs = measureAnchorMs,
        measure = nextMeasure,
        beat = nextBeat,
        subdivision = nextSubdivision
    )
}


private fun queryTimelineWaveformDurationMs(context: Context, uri: Uri): Int {
    val fromRetriever = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L) ?: 0L
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrDefault(0L)

    if (fromRetriever > 0L) {
        return fromRetriever.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    val fromExtractor = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var durationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/") && format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = max(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                }
            }
            durationUs
        } finally {
            runCatching { extractor.release() }
        }
    }.getOrDefault(0L)

    return (fromExtractor / 1_000L)
        .coerceAtLeast(0L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

private fun buildTimelineStructureMediaItems(
    audioPath: String,
    segments: List<ArrangementSegmentData>
): List<MediaItem> {
    val audioUri = Uri.fromFile(File(audioPath))
    return segments.mapNotNull { segment ->
        val startMs = minOf(segment.startMs, segment.endMs).coerceAtLeast(0L)
        val endMs = maxOf(segment.startMs, segment.endMs).coerceAtLeast(startMs + 1L)
        if (endMs <= startMs) {
            null
        } else {
            MediaItem.Builder()
                .setUri(audioUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()
        }
    }
}

private fun estimateTappedTempoBpm(tapPositionsMs: List<Long>): Int? {
    if (tapPositionsMs.size < 3) return null

    val intervalsMs = tapPositionsMs
        .zipWithNext { previous, next -> next - previous }
        .filter { intervalMs -> intervalMs in 150L..2_000L }
    if (intervalsMs.size < 2) return null

    val sortedIntervalsMs = intervalsMs.sorted()
    val medianIntervalMs = if (sortedIntervalsMs.size % 2 == 0) {
        val upperIndex = sortedIntervalsMs.size / 2
        (sortedIntervalsMs[upperIndex - 1] + sortedIntervalsMs[upperIndex]) / 2.0
    } else {
        sortedIntervalsMs[sortedIntervalsMs.size / 2].toDouble()
    }

    val filteredIntervalsMs = intervalsMs.filter { intervalMs ->
        kotlin.math.abs(intervalMs - medianIntervalMs) <= max(80.0, medianIntervalMs * 0.2)
    }
    if (filteredIntervalsMs.size < 2) return null

    val averageIntervalMs = filteredIntervalsMs.average()
    if (averageIntervalMs <= 0.0) return null

    return (60_000.0 / averageIntervalMs)
        .roundToInt()
        .coerceIn(20, 400)
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
            .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = if (enabled) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.38f),
            fontSize = 12.sp,
            maxLines = 1
        )
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
