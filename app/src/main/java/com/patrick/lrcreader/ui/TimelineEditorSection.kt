package com.patrick.lrcreader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.EditionConfig
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.TrackVolumePrefs
import com.patrick.lrcreader.core.TrackTimelineTempoPrefs
import com.patrick.lrcreader.core.audio.ArrangementPreviewPlayer
import com.patrick.lrcreader.core.audio.ArrangementSourceWavCache
import com.patrick.lrcreader.core.audio.ArrangementWavRenderer
import com.patrick.lrcreader.core.audio.SamplerEngine
import com.patrick.lrcreader.core.waveform.WaveformExtractor
import com.patrick.lrcreader.core.waveform.WaveformPeaksCache
import com.patrick.lrcreader.core.light.LightSceneState
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.ArrangementEntryData
import com.patrick.lrcreader.smp.ArrangementSegmentData
import com.patrick.lrcreader.smp.ArrangementStore
import com.patrick.lrcreader.smp.DEFAULT_TIMELINE_NOTE_DURATION_MS
import com.patrick.lrcreader.smp.GridSetupData
import com.patrick.lrcreader.smp.GridSetupStore
import com.patrick.lrcreader.smp.SmpConfig
import com.patrick.lrcreader.smp.SmpConverter
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.SongUnit
import com.patrick.lrcreader.smp.TimelineMarker
import com.patrick.lrcreader.smp.TimelineMarkerKind
import com.patrick.lrcreader.smp.buildArrangementDataForPersistence
import com.patrick.lrcreader.smp.prepareArrangementOccurrences
import com.patrick.lrcreader.smp.reconcileArrangementEntries
import com.patrick.lrcreader.smp.resolvePreparedArrangementPlayheadFromSource
import com.patrick.lrcreader.smp.resolvePreparedArrangementPlayheadFromTimeline
import com.patrick.lrcreader.smp.toOccurrenceProjection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

private const val ARRANGEMENT_PREVIEW_FILE_NAME = "preview_arrangement.wav"
private const val ARRANGEMENT_WAVEFORM_MAX_ZOOM = 240f
private const val ARRANGEMENT_WAVEFORM_VISUAL_PREROLL_MS = 2_000L
private const val ARR_STRUCTURE_QUEUE_TAG = "ARR_STRUCTURE_QUEUE"
private const val ARR_STRUCTURE_WAV_TAG = "ARR_STRUCTURE_WAV"
private const val ARR_STRUCTURE_FLOW_TAG = "ARR_STRUCTURE_FLOW"
private const val ARR_STRUCTURE_SAMPLER_TAG = "ARR_STRUCTURE_SAMPLER"
private const val ARR_SEGMENT_PERSIST_TAG = "ARR_SEGMENT_PERSIST"
private const val ARR_SEGMENT_GESTURE_TAG = "ARR_SEGMENT_GESTURE"
private const val ARR_UNDO_HANDLE_TAG = "ARR_UNDO_HANDLE"
private const val ARR_SEGMENT_STATE_TAG = "ARR_SEGMENT_STATE"
private const val ARR_TIMING_DIAG_TAG = "ARR_TIMING_DIAG"
private const val ARR_PREVIEW_WAV_TAG = "ARR_PREVIEW_WAV"

private fun arrangementTimingSourceLabel(audioPath: String?): String {
    val extension = audioPath
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        .orEmpty()
    return when (extension) {
        "wav" -> "WAV"
        "mp3" -> "MP3"
        else -> if (audioPath.isNullOrBlank()) "UNKNOWN" else extension.uppercase().ifBlank { "UNKNOWN" }
    }
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

private enum class TimelineArrangementEditHandle {
    NONE,
    IN,
    OUT
}

private fun ArrangementSegmentData.persistDebugLabel(): String =
    "id=$id name=$name startMs=$startMs endMs=$endMs"

private fun List<ArrangementSegmentData>.persistDebugSnapshot(): String =
    joinToString(prefix = "[", postfix = "]") { it.persistDebugLabel() }

private data class ArrangementUndoSnapshot(
    val segments: List<ArrangementSegmentData>,
    val structureSegmentIds: List<String>,
    val entries: List<ArrangementEntryData>,
    val selectedSegmentId: String?,
    val selectedStructureIndex: Int?
)

data class TimelinePlaybackControlOverride(
    val isPlaying: Boolean,
    val onPlay: () -> Unit,
    val onPause: () -> Unit,
    val onReturnToMainPlayback: () -> Unit
)

internal fun isTimelineSecondaryPlaybackActive(
    structurePlaybackActive: Boolean,
    wavPreviewActive: Boolean,
    arrangementLoopPreviewActive: Boolean
): Boolean = structurePlaybackActive || wavPreviewActive || arrangementLoopPreviewActive

internal fun shouldUseTimelinePlaybackOverride(
    segmentTargeted: Boolean,
    secondaryPlaybackActive: Boolean
): Boolean = segmentTargeted || secondaryPlaybackActive

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineEditorSection(
    currentSongId: String?,
    startInGridSetup: Boolean = false,
    tabletArrangementLayout: Boolean = false,
    onTabletFocusModeChange: (TabletPlayerFocusMode) -> Unit = {},
    markers: List<TimelineMarker>,
    palette: List<String>,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    playbackControlContent: @Composable (TimelinePlaybackControlOverride?) -> Unit,
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
    onImportGeneratedSmp: suspend (Uri) -> SongUnit? = { null },
    isPreparedClipLoopTestActive: Boolean,
    onStartPreparedClipLoopTest: (Long, Long) -> Unit,
    onStopPreparedClipLoopTest: () -> Unit,
    onSeekPreparedClipLoopToPosition: ((Long) -> Unit)? = null,
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
    val sExportProDialogTitle = stringResource(R.string.arrangement_assemble_pro_dialog_title)
    val sExportProDialogMessage = stringResource(R.string.arrangement_assemble_pro_dialog_message)
    val sUpgradeToPro = stringResource(R.string.library_upgrade_to_pro)
    var renameIndex by remember(markers) { mutableStateOf<Int?>(null) }
    var renameText by remember(markers) { mutableStateOf("") }
    var renameDurationSeconds by remember(markers) { mutableStateOf("") }
    var paletteDraft by remember { mutableStateOf("") }
    var showPaletteInput by remember { mutableStateOf(false) }
    var showTimelineConfigProDialog by remember { mutableStateOf(false) }
    var showArrangementExportProDialog by remember { mutableStateOf(false) }
    var showArrangementHelpPage by remember { mutableStateOf(false) }
    var editorMode by remember(startInGridSetup) {
        mutableStateOf(
            if (startInGridSetup) {
                TimelineEditorMode.GRID_SETUP
            } else {
                TimelineEditorMode.TIMELINE
            }
        )
    }
    val currentOnTabletFocusModeChange by rememberUpdatedState(onTabletFocusModeChange)
    LaunchedEffect(editorMode, tabletArrangementLayout) {
        currentOnTabletFocusModeChange(
            if (tabletArrangementLayout && editorMode == TimelineEditorMode.GRID_SETUP) {
                TabletPlayerFocusMode.ARRANGEMENT
            } else {
                TabletPlayerFocusMode.NONE
            }
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            currentOnTabletFocusModeChange(TabletPlayerFocusMode.NONE)
        }
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
    var tempoStructurePreviewActive by remember(currentSongId) { mutableStateOf(false) }
    var tempoStructurePreviewTargeted by remember(currentSongId) { mutableStateOf(false) }
    var tempoStructurePreviewStopRequest by remember(currentSongId) { mutableIntStateOf(0) }
    var tempoStructurePreviewPlayRequest by remember(currentSongId) { mutableIntStateOf(0) }
    var tempoStructurePreviewUserStopRequest by remember(currentSongId) { mutableIntStateOf(0) }
    var tempoMainPlaybackRequest by remember(currentSongId) { mutableIntStateOf(0) }

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

    if (showArrangementHelpPage) {
        BackHandler { showArrangementHelpPage = false }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showArrangementHelpPage = false }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_close),
                        tint = Color.White
                    )
                }
                Text(
                    text = stringResource(R.string.arrangement_help_title),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                ArrangementHelpPageContent(
                    message = stringResource(R.string.arrangement_help_message)
                )
            }
        }
        return
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
        if (tempoStructurePreviewActive) {
            tempoStructurePreviewStopRequest += 1
            return
        }
        if (durationMs > 0) {
            onIsPlayingChange(true)
            runCatching { FillerSoundManager.fadeOutAndStop(200) }
        }
    }

    fun closeOrReturnFromEditor() {
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

    BackHandler(enabled = tabletArrangementLayout) {
        closeOrReturnFromEditor()
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
            if (
                tabletArrangementLayout ||
                !(startInGridSetup || editorMode == TimelineEditorMode.GRID_SETUP)
            ) {
                TextButton(
                    onClick = ::closeOrReturnFromEditor
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
                        TextButton(onClick = { showArrangementHelpPage = true }) {
                            Text(
                                text = stringResource(R.string.arrangement_hub_help_action),
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
                    modifier = if (tabletArrangementLayout) {
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    } else {
                        Modifier
                    },
                    constrainToAvailableHeight = tabletArrangementLayout,
                    currentSongId = currentSongId,
                    fallbackTempoBpm = measuresTempoBpm,
                    measureAnchorMs = measureAnchorMs,
                    isPlaying = isPlaying,
                    currentPositionMs = safePositionMs,
                    seekToMs = seekToMs,
                    onIsPlayingChange = onIsPlayingChange,
                    onOpenArrangement = onOpenArrangement,
                    onImportGeneratedSmp = onImportGeneratedSmp,
                    isPreparedClipLoopTestActive = isPreparedClipLoopTestActive,
                    onStartPreparedClipLoopTest = onStartPreparedClipLoopTest,
                    onStopPreparedClipLoopTest = onStopPreparedClipLoopTest,
                    structurePreviewStopRequest = tempoStructurePreviewStopRequest,
                    structurePreviewPlayRequest = tempoStructurePreviewPlayRequest,
                    structurePreviewUserStopRequest = tempoStructurePreviewUserStopRequest,
                    mainPlaybackRequest = tempoMainPlaybackRequest,
                    onStructurePreviewActiveChange = { tempoStructurePreviewActive = it },
                    onStructurePreviewTargetChange = { tempoStructurePreviewTargeted = it }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val playbackOverride = if (
            tabletArrangementLayout &&
            editorMode == TimelineEditorMode.GRID_SETUP &&
            shouldUseTimelinePlaybackOverride(
                segmentTargeted = tempoStructurePreviewTargeted,
                secondaryPlaybackActive = tempoStructurePreviewActive
            )
        ) {
            TimelinePlaybackControlOverride(
                isPlaying = tempoStructurePreviewActive,
                onPlay = {
                    if (!tempoStructurePreviewActive) {
                        tempoStructurePreviewPlayRequest += 1
                    }
                },
                onPause = {
                    if (tempoStructurePreviewActive) {
                        tempoStructurePreviewUserStopRequest += 1
                    }
                },
                onReturnToMainPlayback = {
                    tempoStructurePreviewTargeted = false
                    tempoMainPlaybackRequest += 1
                }
            )
        } else {
            null
        }
        playbackControlContent(playbackOverride)
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

    if (showArrangementExportProDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showArrangementExportProDialog = false },
            title = {
                Text(
                    text = sExportProDialogTitle,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = sExportProDialogMessage,
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArrangementExportProDialog = false
                        openUpgradeToPro()
                    }
                ) {
                    Text(sUpgradeToPro, color = Color(0xFF80CBC4))
                }
            },
            dismissButton = {
                TextButton(onClick = { showArrangementExportProDialog = false }) {
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
    modifier: Modifier = Modifier,
    constrainToAvailableHeight: Boolean = false,
    currentSongId: String?,
    fallbackTempoBpm: Int?,
    measureAnchorMs: Long?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    seekToMs: (Long) -> Unit,
    onIsPlayingChange: (Boolean) -> Unit,
    onOpenArrangement: () -> Unit,
    onImportGeneratedSmp: suspend (Uri) -> SongUnit?,
    isPreparedClipLoopTestActive: Boolean,
    onStartPreparedClipLoopTest: (Long, Long) -> Unit,
    onStopPreparedClipLoopTest: () -> Unit,
    onSeekPreparedClipLoopToPosition: ((Long) -> Unit)? = null,
    structurePreviewStopRequest: Int,
    structurePreviewPlayRequest: Int,
    structurePreviewUserStopRequest: Int,
    mainPlaybackRequest: Int,
    onStructurePreviewActiveChange: (Boolean) -> Unit,
    onStructurePreviewTargetChange: (Boolean) -> Unit
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
        modifier = modifier,
        constrainToAvailableHeight = constrainToAvailableHeight,
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
        onImportGeneratedSmp = onImportGeneratedSmp,
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
        onSeekPreparedClipLoopToPosition = onSeekPreparedClipLoopToPosition,
        structurePreviewStopRequest = structurePreviewStopRequest,
        structurePreviewPlayRequest = structurePreviewPlayRequest,
        structurePreviewUserStopRequest = structurePreviewUserStopRequest,
        mainPlaybackRequest = mainPlaybackRequest,
        onStructurePreviewActiveChange = onStructurePreviewActiveChange,
        onStructurePreviewTargetChange = onStructurePreviewTargetChange,
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
    modifier: Modifier = Modifier,
    constrainToAvailableHeight: Boolean = false,
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
    onImportGeneratedSmp: suspend (Uri) -> SongUnit?,
    onMeasureAnchorHere: (Long) -> Unit,
    onInitialSyncPointIfMissing: (Long) -> Unit,
    onSegmentInChange: (Long?) -> Unit,
    onSegmentOutChange: (Long?) -> Unit,
    isPreparedClipLoopTestActive: Boolean,
    onStartPreparedClipLoopTest: (Long, Long) -> Unit,
    onStopPreparedClipLoopTest: () -> Unit,
    onSeekPreparedClipLoopToPosition: ((Long) -> Unit)?,
    structurePreviewStopRequest: Int,
    structurePreviewPlayRequest: Int,
    structurePreviewUserStopRequest: Int,
    mainPlaybackRequest: Int,
    onStructurePreviewActiveChange: (Boolean) -> Unit,
    onStructurePreviewTargetChange: (Boolean) -> Unit,
    onTempoDraftChange: (String) -> Unit
) {
    val structurePreviewFadeDurationMs = 12L
    var localMeasureAnchorMs by remember(measureAnchorMs) { mutableStateOf(measureAnchorMs) }
    var tempoTapTimesMs by remember { mutableStateOf<List<Long>>(emptyList()) }
    var showTapTempoHint by remember { mutableStateOf(false) }
    var loopEnabled by remember { mutableStateOf(false) }
    var revealSyncPointRequest by remember { mutableIntStateOf(0) }
    var preparedLoopStartMs by remember(currentSongId) { mutableStateOf<Long?>(null) }
    var arrangementLoopPreviewActive by remember(currentSongId) { mutableStateOf(false) }
    var arrangementLoopPositionMs by remember(currentSongId) { mutableLongStateOf(0L) }
    var structurePlaybackActive by remember(currentSongId) { mutableStateOf(false) }
    var structurePlaybackIndex by remember(currentSongId) { mutableIntStateOf(-1) }
    var queuedStructureSegmentIndex by remember(currentSongId) { mutableStateOf<Int?>(null) }
    var selectedStructureEditIndex by remember(currentSongId) { mutableStateOf<Int?>(null) }
    var structureEditFocusRequest by remember(currentSongId) { mutableIntStateOf(0) }
    var structurePlaybackAbsolutePositionMs by remember(currentSongId) {
        mutableLongStateOf(currentPositionMs.coerceAtLeast(0L))
    }
    val savedAnchorMs = localMeasureAnchorMs
    val displayedCurrentPositionMs = if (structurePlaybackActive) {
        structurePlaybackAbsolutePositionMs.coerceAtLeast(0L)
    } else if (arrangementLoopPreviewActive && preparedLoopStartMs != null) {
        preparedLoopStartMs!! + arrangementLoopPositionMs.coerceAtLeast(0L)
    } else if (isPreparedClipLoopTestActive && preparedLoopStartMs != null) {
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
    val isLite = EditionConfig.isLite
    val sExportProDialogTitle = stringResource(R.string.arrangement_assemble_pro_dialog_title)
    val sExportProDialogMessage = stringResource(R.string.arrangement_assemble_pro_dialog_message)
    val sUpgradeToPro = stringResource(R.string.library_upgrade_to_pro)
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val smpLibraryScanner = remember(context) { SmpLibraryScanner(context.applicationContext) }
    val smpConverter = remember(context) { SmpConverter(context.applicationContext) }
    val structurePreviewPlayer = remember(context.applicationContext) {
        ExoPlayer.Builder(context.applicationContext).build().apply { playWhenReady = false }
    }
    val structureSamplerEngine = remember { SamplerEngine() }
    val arrangementPreviewPlayer = remember(context.applicationContext) {
        ArrangementPreviewPlayer(context.applicationContext)
    }
    val arrangementPreviewCacheFile = remember(context.applicationContext) {
        File(context.applicationContext.cacheDir, ARRANGEMENT_PREVIEW_FILE_NAME)
    }
    var currentSongAudioPath by remember(currentSongId) { mutableStateOf<String?>(null) }
    var currentSongTitle by remember(currentSongId) { mutableStateOf<String?>(null) }
    var currentSongTrackGainDb by remember(currentSongId) { mutableIntStateOf(0) }
    var currentStructureSourcePath by remember(currentSongId) { mutableStateOf<String?>(null) }
    var structureUsingWavSource by remember(currentSongId) { mutableStateOf(false) }
    var structureUsingSampler by remember(currentSongId) { mutableStateOf(false) }
    var structureSamplerReady by remember(currentSongId) { mutableStateOf(false) }
    var structureSamplerSegmentStartRealtimeMs by remember(currentSongId) { mutableLongStateOf(0L) }
    var structurePreparationGeneration by remember(currentSongId) { mutableIntStateOf(0) }
    var isStructureAudioPreparing by remember(currentSongId) { mutableStateOf(false) }
    var previewRenderedFile by remember(currentSongId) { mutableStateOf<File?>(null) }
    var previewRenderedSignature by remember(currentSongId) { mutableStateOf<String?>(null) }
    var wavPreviewActive by remember(currentSongId) { mutableStateOf(false) }
    var wavPreviewPositionMs by remember(currentSongId) { mutableLongStateOf(0L) }
    var wavPreviewDurationMs by remember(currentSongId) { mutableLongStateOf(0L) }
    var isPreviewGenerating by remember(currentSongId) { mutableStateOf(false) }
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
    var arrangementName by remember(currentSongId) { mutableStateOf("Arrangement 1") }
    var arrangementSegments by remember(currentSongId) { mutableStateOf<List<ArrangementSegmentData>>(emptyList()) }
    var structureSegmentIds by remember(currentSongId) { mutableStateOf<List<String>>(emptyList()) }
    var arrangementEntries by remember(currentSongId) { mutableStateOf<List<ArrangementEntryData>>(emptyList()) }
    var preservedLegacyArrangementSegments by remember(currentSongId) {
        mutableStateOf<List<ArrangementSegmentData>>(emptyList())
    }
    val arrangementSaveMutex = remember(currentSongId) { Mutex() }
    val segmentSelectionCounts = remember(currentSongId) { mutableStateMapOf<String, Int>() }
    var arrangementUndoStack by remember(currentSongId) { mutableStateOf<List<ArrangementUndoSnapshot>>(emptyList()) }
    var pendingSegmentEditUndoSnapshot by remember(currentSongId) {
        mutableStateOf<ArrangementUndoSnapshot?>(null)
    }
    var previousObservedSegmentState by remember(currentSongId) {
        mutableStateOf<Triple<String?, Long?, Long?>?>(null)
    }
    var nextSegmentIndex by remember(currentSongId) { mutableLongStateOf(1L) }
    var renameSegmentId by remember(currentSongId) { mutableStateOf<String?>(null) }
    var segmentOptionsTargetId by remember(currentSongId) { mutableStateOf<String?>(null) }
    var copiedArrangementEntry by remember(currentSongId) { mutableStateOf<ArrangementEntryData?>(null) }
    var colorArrangementEntryId by remember(currentSongId) { mutableStateOf<String?>(null) }
    var showArrangementExportProDialog by remember(currentSongId) { mutableStateOf(false) }
    var showExportNameDialog by remember(currentSongId) { mutableStateOf(false) }
    var showSamplerTestScreen by remember(currentSongId) { mutableStateOf(false) }
    var exportNameDraft by remember(currentSongId) { mutableStateOf(TextFieldValue("")) }
    var isExportNameLoading by remember(currentSongId) { mutableStateOf(false) }
    var isFinalExporting by remember(currentSongId) { mutableStateOf(false) }
    var segmentSelectionMode by remember(currentSongId) {
        mutableStateOf(TimelineSegmentSelectionMode.KEEP)
    }
    var suppressNextLoopAutoplay by remember(currentSongId) { mutableStateOf(false) }
    val arrangementTrackGainLinear = remember(currentSongTrackGainDb) {
        val safeDb = currentSongTrackGainDb.coerceIn(-12, 0)
        if (safeDb >= 0) {
            1f
        } else {
            10f.pow(safeDb / 20f).coerceIn(0f, 1f)
        }
    }

    fun publishSecondaryPlaybackState() {
        onStructurePreviewActiveChange(
            isTimelineSecondaryPlaybackActive(
                structurePlaybackActive = structurePlaybackActive,
                wavPreviewActive = wavPreviewActive,
                arrangementLoopPreviewActive = arrangementLoopPreviewActive
            )
        )
    }

    fun stopStructurePreviewPlayback(reason: String = "unspecified") {
        structurePreparationGeneration += 1
        val mediaItemCountBefore = runCatching { structurePreviewPlayer.mediaItemCount }.getOrDefault(0)
        Log.d(
            ARR_STRUCTURE_FLOW_TAG,
            "STOP_STRUCTURE reason=$reason currentStructureIndex=$structurePlaybackIndex queuedStructureSegmentIndex=$queuedStructureSegmentIndex mediaItemCount=$mediaItemCountBefore"
        )
        if (queuedStructureSegmentIndex != null) {
            Log.d(
                ARR_STRUCTURE_QUEUE_TAG,
                "QUEUE_RESET old=$queuedStructureSegmentIndex reason=stopStructurePreviewPlayback"
            )
        }
        runCatching { structurePreviewPlayer.pause() }
        runCatching { structurePreviewPlayer.stop() }
        runCatching { structureSamplerEngine.stop() }
        Log.d(ARR_STRUCTURE_SAMPLER_TAG, "STOP reason=$reason")
        Log.d(
            ARR_STRUCTURE_FLOW_TAG,
            "CLEAR_MEDIA_ITEMS_CALL reason=$reason mediaItemCountBefore=$mediaItemCountBefore"
        )
        runCatching { structurePreviewPlayer.clearMediaItems() }
        runCatching { structurePreviewPlayer.volume = arrangementTrackGainLinear }
        structurePlaybackActive = false
        structurePlaybackIndex = -1
        queuedStructureSegmentIndex = null
        currentStructureSourcePath = null
        structureUsingWavSource = false
        structureUsingSampler = false
        structureSamplerReady = false
        structureSamplerSegmentStartRealtimeMs = 0L
        isStructureAudioPreparing = false
        structurePlaybackAbsolutePositionMs = currentPositionMs.coerceAtLeast(0L)
        wavPreviewActive = false
        wavPreviewPositionMs = 0L
        wavPreviewDurationMs = 0L
        isPreviewGenerating = false
        publishSecondaryPlaybackState()
    }

    fun stopArrangementLoopPreviewPlayback() {
        runCatching { arrangementPreviewPlayer.stop() }
        arrangementLoopPreviewActive = false
        arrangementLoopPositionMs = 0L
        publishSecondaryPlaybackState()
    }

    fun replacePreviewRenderedFile(nextFile: File?) {
        val previousFile = previewRenderedFile
        previewRenderedFile = nextFile
        previousFile
            ?.takeIf { staleFile -> nextFile == null || staleFile.absolutePath != nextFile.absolutePath }
            ?.let { staleFile -> runCatching { staleFile.delete() } }
    }

    fun clearArrangementPreviewCache() {
        previewRenderedSignature = null
        replacePreviewRenderedFile(null)
        runCatching { arrangementPreviewCacheFile.delete() }
    }

    fun playArrangementPreviewFile(previewFile: File) {
        Log.d(
            ARR_PREVIEW_WAV_TAG,
            "PLAYER_START_REQUEST path=${previewFile.absolutePath} exists=${previewFile.isFile} " +
                "size=${previewFile.length()} currentState=${structurePreviewPlayer.playbackState} " +
                "isPlaying=${structurePreviewPlayer.isPlaying}"
        )
        stopStructurePreviewPlayback(reason = "playArrangementPreviewFile")
        stopArrangementLoopPreviewPlayback()
        if (isPreparedClipLoopTestActive) {
            onStopPreparedClipLoopTest()
        }
        loopEnabled = false
        preparedLoopStartMs = null
        onIsPlayingChange(false)
        wavPreviewActive = true
        wavPreviewPositionMs = 0L
        wavPreviewDurationMs = 0L
        publishSecondaryPlaybackState()
        structurePreviewPlayer.pause()
        Log.d(
            ARR_STRUCTURE_FLOW_TAG,
            "CLEAR_MEDIA_ITEMS_CALL reason=playArrangementPreviewFile mediaItemCountBefore=${structurePreviewPlayer.mediaItemCount}"
        )
        structurePreviewPlayer.clearMediaItems()
        structurePreviewPlayer.repeatMode = Player.REPEAT_MODE_OFF
        structurePreviewPlayer.volume = arrangementTrackGainLinear
        structurePreviewPlayer.setMediaItem(
            MediaItem.fromUri(Uri.fromFile(previewFile))
        )
        Log.d(
            ARR_STRUCTURE_FLOW_TAG,
            "PREPARE_CALL reason=playArrangementPreviewFile mediaItemCount=${structurePreviewPlayer.mediaItemCount} sourceUri=${Uri.fromFile(previewFile)}"
        )
        structurePreviewPlayer.prepare()
        structurePreviewPlayer.play()
        Log.d(
            ARR_PREVIEW_WAV_TAG,
            "PLAYER_START path=${previewFile.absolutePath} exists=${previewFile.isFile} " +
                "size=${previewFile.length()} mediaItemCount=${structurePreviewPlayer.mediaItemCount} " +
                "playbackState=${structurePreviewPlayer.playbackState} playWhenReady=${structurePreviewPlayer.playWhenReady}"
        )
        Log.d(
            ARR_TIMING_DIAG_TAG,
            "PLAY_START playMode=NORMAL_PREVIEW requestedStartMs=0 requestedEndMs=NA " +
                "actualPlayerPositionMs=${runCatching { structurePreviewPlayer.currentPosition }.getOrDefault(-1L)} " +
                "source=WAV_PREVIEW sourceUri=${Uri.fromFile(previewFile)}"
        )
    }

    fun playStructureSegmentPreview(
        startIndex: Int,
        audioPath: String,
        segments: List<ArrangementSegmentData>
    ) {
        if (startIndex !in segments.indices) return
        val requestedSegment = segments[startIndex]
        val sourceUri = Uri.fromFile(File(audioPath))
        val mediaItemCountBefore = structurePreviewPlayer.mediaItemCount
        Log.d(
            ARR_STRUCTURE_QUEUE_TAG,
            "START_SEGMENT requestedIndex=$startIndex name=${requestedSegment.name} startMs=${requestedSegment.startMs} endMs=${requestedSegment.endMs}"
        )
        val mediaItem = buildTimelineStructureMediaItem(
            audioPath = audioPath,
            segment = requestedSegment
        ) ?: return
        structurePlaybackActive = true
        structureUsingSampler = false
        structurePlaybackIndex = startIndex
        currentStructureSourcePath = audioPath
        if (queuedStructureSegmentIndex != null) {
            Log.d(
                ARR_STRUCTURE_QUEUE_TAG,
                "QUEUE_RESET old=$queuedStructureSegmentIndex reason=startStructureSegment"
            )
        }
        queuedStructureSegmentIndex = null
        structurePlaybackAbsolutePositionMs = minOf(
            requestedSegment.startMs,
            requestedSegment.endMs
        ).coerceAtLeast(0L)
        Log.d(
            ARR_STRUCTURE_QUEUE_TAG,
            "START_SEGMENT_APPLIED currentPlayingIndex=$structurePlaybackIndex isStructureSegmentPlaying=$structurePlaybackActive"
        )
        Log.d(
            ARR_STRUCTURE_WAV_TAG,
            "STRUCTURE_SOURCE uri=$sourceUri"
        )
        Log.d(
            ARR_STRUCTURE_WAV_TAG,
            "STRUCTURE_USING_WAV $structureUsingWavSource"
        )
        Log.d(
            ARR_STRUCTURE_FLOW_TAG,
            "START_SEGMENT index=$startIndex name=${requestedSegment.name} startMs=${requestedSegment.startMs} endMs=${requestedSegment.endMs} sourceUri=$sourceUri mediaItemCountBefore=$mediaItemCountBefore mediaItemCountAfter=-1 currentMediaItemIndex=${structurePreviewPlayer.currentMediaItemIndex}"
        )
        publishSecondaryPlaybackState()
        structurePreviewPlayer.pause()
        Log.d(
            ARR_STRUCTURE_FLOW_TAG,
            "CLEAR_MEDIA_ITEMS_CALL reason=playStructureSegmentPreview mediaItemCountBefore=$mediaItemCountBefore"
        )
        structurePreviewPlayer.clearMediaItems()
        structurePreviewPlayer.repeatMode = Player.REPEAT_MODE_OFF
        structurePreviewPlayer.volume = arrangementTrackGainLinear
        structurePreviewPlayer.setMediaItem(mediaItem)
        val autoNextIndex = (startIndex + 1).takeIf { it in segments.indices }
        if (autoNextIndex != null) {
            val nextSegment = segments[autoNextIndex]
            val nextStartMs = minOf(nextSegment.startMs, nextSegment.endMs).coerceAtLeast(0L)
            val nextEndMs = maxOf(nextSegment.startMs, nextSegment.endMs).coerceAtLeast(nextStartMs + 1L)
            val mediaItemCountBeforeAuto = structurePreviewPlayer.mediaItemCount
            buildTimelineStructureMediaItem(
                audioPath = audioPath,
                segment = nextSegment
            )?.let { nextMediaItem ->
                structurePreviewPlayer.addMediaItem(nextMediaItem)
                Log.d(
                    ARR_STRUCTURE_QUEUE_TAG,
                    "NEXT_ITEM_AUTO queuedIndex=$autoNextIndex insertionIndex=1 name=${nextSegment.name}"
                )
                Log.d(
                    ARR_STRUCTURE_FLOW_TAG,
                    "NEXT_ITEM_AUTO currentIndex=$startIndex nextIndex=$autoNextIndex name=${nextSegment.name} startMs=$nextStartMs endMs=$nextEndMs sourceUri=$sourceUri mediaItemCountBefore=$mediaItemCountBeforeAuto mediaItemCountAfter=${structurePreviewPlayer.mediaItemCount}"
                )
            }
        }
        Log.d(
            ARR_STRUCTURE_FLOW_TAG,
            "PREPARE_CALL reason=playStructureSegmentPreview mediaItemCount=${structurePreviewPlayer.mediaItemCount} sourceUri=$sourceUri"
        )
        structurePreviewPlayer.prepare()
        Log.d(
            ARR_STRUCTURE_FLOW_TAG,
            "START_SEGMENT index=$startIndex name=${requestedSegment.name} startMs=${requestedSegment.startMs} endMs=${requestedSegment.endMs} sourceUri=$sourceUri mediaItemCountBefore=$mediaItemCountBefore mediaItemCountAfter=${structurePreviewPlayer.mediaItemCount} currentMediaItemIndex=${structurePreviewPlayer.currentMediaItemIndex}"
        )
        structurePreviewPlayer.play()
        Log.d(
            ARR_TIMING_DIAG_TAG,
            "PLAY_START playMode=STRUCTURE requestedStartMs=${requestedSegment.startMs} " +
                "requestedEndMs=${requestedSegment.endMs} " +
                "actualPlayerPositionMs=${runCatching { structurePreviewPlayer.currentPosition }.getOrDefault(-1L)} " +
                "source=${if (structureUsingWavSource) "WAV_CACHE" else arrangementTimingSourceLabel(audioPath)} " +
                "segmentId=${requestedSegment.id} mediaItemSourceUri=$sourceUri"
        )
    }

    fun playStructureSegmentWithSampler(
        startIndex: Int,
        sourcePath: String,
        segments: List<ArrangementSegmentData>
    ): Boolean {
        if (startIndex !in segments.indices || !structureSamplerReady) {
            return false
        }
        val requestedSegment = segments[startIndex]
        Log.d(
            ARR_STRUCTURE_SAMPLER_TAG,
            "USING_SAMPLER true"
        )
        Log.d(
            ARR_STRUCTURE_SAMPLER_TAG,
            "PLAY_SEGMENT index=$startIndex"
        )
        return runCatching {
            structurePreviewPlayer.pause()
            structurePreviewPlayer.stop()
            structurePreviewPlayer.clearMediaItems()
            structurePlaybackActive = true
            structureUsingSampler = true
            structureUsingWavSource = true
            structurePlaybackIndex = startIndex
            queuedStructureSegmentIndex = null
            currentStructureSourcePath = sourcePath
            structureSamplerSegmentStartRealtimeMs = SystemClock.elapsedRealtime()
            structurePlaybackAbsolutePositionMs = minOf(
                requestedSegment.startMs,
                requestedSegment.endMs
            ).coerceAtLeast(0L)
            publishSecondaryPlaybackState()
            structureSamplerEngine.play(startIndex)
        }.onFailure { error ->
            Log.w(
                ARR_STRUCTURE_SAMPLER_TAG,
                "FALLBACK_EXOPLAYER reason=sampler_play_failed error=${error.message}",
                error
            )
            stopStructurePreviewPlayback(reason = "sampler_play_failed")
        }.isSuccess
    }

    fun queueStructureSegmentPreview(
        nextIndex: Int,
        audioPath: String,
        segments: List<ArrangementSegmentData>
    ) {
        if (structureUsingSampler && structurePlaybackActive) {
            if (nextIndex !in segments.indices) return
            runCatching {
                structureSamplerEngine.queueNext(nextIndex)
                queuedStructureSegmentIndex = nextIndex
                Log.d(ARR_STRUCTURE_SAMPLER_TAG, "QUEUE_NEXT index=$nextIndex")
            }.onFailure { error ->
                Log.w(
                    ARR_STRUCTURE_SAMPLER_TAG,
                    "FALLBACK_EXOPLAYER reason=sampler_queue_failed error=${error.message}",
                    error
                )
            }
            return
        }
        if (nextIndex !in segments.indices) return
        val queuedSegment = segments[nextIndex]
        val queuedStartMs = minOf(queuedSegment.startMs, queuedSegment.endMs).coerceAtLeast(0L)
        val queuedEndMs = maxOf(queuedSegment.startMs, queuedSegment.endMs).coerceAtLeast(queuedStartMs + 1L)
        val sourceUri = Uri.fromFile(File(audioPath))
        val nextMediaItem = buildTimelineStructureMediaItem(
            audioPath = audioPath,
            segment = queuedSegment
        ) ?: return
        val insertionIndex = structurePreviewPlayer.currentMediaItemIndex.coerceAtLeast(0) + 1
        val mediaItemCount = structurePreviewPlayer.mediaItemCount
        if (mediaItemCount <= insertionIndex) {
            structurePreviewPlayer.addMediaItem(nextMediaItem)
            Log.d(
                ARR_STRUCTURE_QUEUE_TAG,
                "NEXT_ITEM_SET queuedIndex=$nextIndex insertionIndex=$insertionIndex name=${queuedSegment.name}"
            )
            Log.d(
                ARR_STRUCTURE_FLOW_TAG,
                "NEXT_ITEM_SET queuedIndex=$nextIndex name=${queuedSegment.name} startMs=$queuedStartMs endMs=$queuedEndMs sourceUri=$sourceUri previousQueuedIndex=$queuedStructureSegmentIndex mediaItemCountBefore=$mediaItemCount mediaItemCountAfter=${structurePreviewPlayer.mediaItemCount}"
            )
        } else {
            val oldQueuedIndex = queuedStructureSegmentIndex
            structurePreviewPlayer.replaceMediaItem(insertionIndex, nextMediaItem)
            Log.d(
                ARR_STRUCTURE_QUEUE_TAG,
                "NEXT_ITEM_REPLACED old=$oldQueuedIndex new=$nextIndex insertionIndex=$insertionIndex name=${queuedSegment.name}"
            )
            Log.d(
                ARR_STRUCTURE_FLOW_TAG,
                "NEXT_ITEM_REPLACED queuedIndex=$nextIndex name=${queuedSegment.name} startMs=$queuedStartMs endMs=$queuedEndMs sourceUri=$sourceUri previousQueuedIndex=$oldQueuedIndex mediaItemCountBefore=$mediaItemCount mediaItemCountAfter=${structurePreviewPlayer.mediaItemCount}"
            )
        }
        queuedStructureSegmentIndex = nextIndex
        Log.d(
            ARR_TIMING_DIAG_TAG,
            "STRUCTURE_QUEUE segmentId=${queuedSegment.id} segmentStartMs=$queuedStartMs " +
                "segmentEndMs=$queuedEndMs mediaItemSourceUri=$sourceUri " +
                "transitionTimestampMs=${SystemClock.elapsedRealtime()} expectedStartMs=$queuedStartMs " +
                "currentPosition=${runCatching { structurePreviewPlayer.currentPosition }.getOrDefault(-1L)}"
        )
    }

    fun preloadAutomaticStructureSegmentPreview(
        currentIndex: Int,
        audioPath: String,
        segments: List<ArrangementSegmentData>
    ) {
        val nextIndex = (currentIndex + 1).takeIf { it in segments.indices } ?: return
        val nextSegment = segments[nextIndex]
        val nextStartMs = minOf(nextSegment.startMs, nextSegment.endMs).coerceAtLeast(0L)
        val nextEndMs = maxOf(nextSegment.startMs, nextSegment.endMs).coerceAtLeast(nextStartMs + 1L)
        val sourceUri = Uri.fromFile(File(audioPath))
        val nextMediaItem = buildTimelineStructureMediaItem(
            audioPath = audioPath,
            segment = nextSegment
        ) ?: return
        val insertionIndex = structurePreviewPlayer.currentMediaItemIndex.coerceAtLeast(0) + 1
        val mediaItemCount = structurePreviewPlayer.mediaItemCount
        if (mediaItemCount <= insertionIndex) {
            structurePreviewPlayer.addMediaItem(nextMediaItem)
            Log.d(
                ARR_STRUCTURE_QUEUE_TAG,
                "NEXT_ITEM_AUTO queuedIndex=$nextIndex insertionIndex=$insertionIndex name=${nextSegment.name}"
            )
            Log.d(
                ARR_STRUCTURE_FLOW_TAG,
                "NEXT_ITEM_AUTO currentIndex=$currentIndex nextIndex=$nextIndex name=${nextSegment.name} startMs=$nextStartMs endMs=$nextEndMs sourceUri=$sourceUri mediaItemCountBefore=$mediaItemCount mediaItemCountAfter=${structurePreviewPlayer.mediaItemCount}"
            )
        } else {
            structurePreviewPlayer.replaceMediaItem(insertionIndex, nextMediaItem)
            Log.d(
                ARR_STRUCTURE_QUEUE_TAG,
                "NEXT_ITEM_AUTO_REPLACED queuedIndex=$nextIndex insertionIndex=$insertionIndex name=${nextSegment.name}"
            )
            Log.d(
                ARR_STRUCTURE_FLOW_TAG,
                "NEXT_ITEM_AUTO currentIndex=$currentIndex nextIndex=$nextIndex name=${nextSegment.name} startMs=$nextStartMs endMs=$nextEndMs sourceUri=$sourceUri mediaItemCountBefore=$mediaItemCount mediaItemCountAfter=${structurePreviewPlayer.mediaItemCount}"
            )
        }
        Log.d(
            ARR_TIMING_DIAG_TAG,
            "STRUCTURE_PRELOAD segmentId=${nextSegment.id} segmentStartMs=$nextStartMs " +
                "segmentEndMs=$nextEndMs mediaItemSourceUri=$sourceUri " +
                "transitionTimestampMs=${SystemClock.elapsedRealtime()} expectedStartMs=$nextStartMs " +
                "currentPosition=${runCatching { structurePreviewPlayer.currentPosition }.getOrDefault(-1L)}"
        )
    }

    fun persistArrangementState(
        nextSegments: List<ArrangementSegmentData> = arrangementSegments,
        nextStructureSegmentIds: List<String> = structureSegmentIds,
        nextEntries: List<ArrangementEntryData> = arrangementEntries,
        debugSegmentId: String? = null
    ) {
        val songId = currentSongId?.trim().orEmpty()
        if (songId.isEmpty()) return
        val snapshotName = arrangementName.ifBlank { "Arrangement 1" }
        val snapshotSegments = nextSegments.toList()
        val snapshotStructureSegmentIds = nextStructureSegmentIds.toList()
        val snapshotEntries = nextEntries.toList()
        val snapshotLegacySegments = preservedLegacyArrangementSegments.toList()
        val dataToPersist = buildArrangementDataForPersistence(
            useOccurrenceModel = constrainToAvailableHeight,
            name = snapshotName,
            sourceSongId = songId,
            segments = snapshotSegments,
            structureSegmentIds = snapshotStructureSegmentIds,
            existingEntries = snapshotEntries,
            preservedLegacySegments = snapshotLegacySegments
        )
        debugSegmentId?.let { segmentId ->
            Log.d(
                ARR_SEGMENT_PERSIST_TAG,
                "SAVE_BEFORE segmentId=$segmentId " +
                    "arrangementSegment=${snapshotSegments.firstOrNull { it.id == segmentId }?.persistDebugLabel()} " +
                    "structureCount=${snapshotStructureSegmentIds.count { it == segmentId }} " +
                    "structureValues=${snapshotStructureSegmentIds.mapIndexedNotNull { index, structureSegmentId ->
                        snapshotSegments.firstOrNull { it.id == structureSegmentId }
                            ?.takeIf { it.id == segmentId }
                            ?.let { "index=$index ${it.persistDebugLabel()}" }
                    }} " +
                    "allSegments=${snapshotSegments.persistDebugSnapshot()}"
            )
        }
        scope.launch {
            arrangementSaveMutex.withLock {
                val saved = ArrangementStore.save(
                    context = context.applicationContext,
                    songId = songId,
                    data = dataToPersist
                )
                debugSegmentId?.let { segmentId ->
                    val storedData = ArrangementStore.load(context.applicationContext, songId)
                    Log.d(
                        ARR_SEGMENT_PERSIST_TAG,
                        "SAVE_AFTER segmentId=$segmentId saved=$saved " +
                            "localMemory=${arrangementSegments.firstOrNull { it.id == segmentId }?.persistDebugLabel()} " +
                            "snapshot=${snapshotSegments.firstOrNull { it.id == segmentId }?.persistDebugLabel()} " +
                            "store=${storedData?.segments?.firstOrNull { it.id == segmentId }?.persistDebugLabel()} " +
                            "storeAll=${storedData?.segments.orEmpty().persistDebugSnapshot()}"
                )
            }
            }
        }
    }

    fun currentArrangementUndoSnapshot(): ArrangementUndoSnapshot =
        ArrangementUndoSnapshot(
            segments = arrangementSegments.toList(),
            structureSegmentIds = structureSegmentIds.toList(),
            entries = arrangementEntries.toList(),
            selectedSegmentId = selectedSegmentLoopId,
            selectedStructureIndex = selectedStructureEditIndex
        )

    fun pushArrangementUndoSnapshot(snapshot: ArrangementUndoSnapshot = currentArrangementUndoSnapshot()) {
        arrangementUndoStack = (arrangementUndoStack + snapshot).takeLast(20)
    }

    fun restoreArrangementUndoSnapshot(snapshot: ArrangementUndoSnapshot) {
        arrangementSegments = snapshot.segments
        structureSegmentIds = snapshot.structureSegmentIds
        arrangementEntries = snapshot.entries
        nextSegmentIndex = resolveNextTimelineArrangementSegmentIndex(
            snapshot.segments + preservedLegacyArrangementSegments
        )
        val selectedSegment = snapshot.selectedSegmentId
            ?.let { segmentId -> snapshot.segments.firstOrNull { it.id == segmentId } }
        selectedSegmentLoopId = selectedSegment?.id
        selectedStructureEditIndex = snapshot.selectedStructureIndex
            ?.takeIf { index -> index in snapshot.structureSegmentIds.indices }
        if (selectedSegment != null) {
            val restoredStartMs = minOf(selectedSegment.startMs, selectedSegment.endMs).coerceAtLeast(0L)
            val restoredEndMs = maxOf(selectedSegment.startMs, selectedSegment.endMs)
                .coerceAtLeast(restoredStartMs + 1L)
            segmentInMs = restoredStartMs
            segmentOutMs = restoredEndMs
            selectedSegmentLoopStartMs = restoredStartMs
            selectedSegmentLoopEndMs = restoredEndMs
            preparedLoopStartMs = restoredStartMs
            structureEditFocusRequest += 1
	        } else {
            Log.d(
                ARR_SEGMENT_STATE_TAG,
                "STATE_CHANGE reason=undo_restore_without_selected_segment " +
                    "oldId=$selectedSegmentLoopId newId=null " +
                    "oldStart=$selectedSegmentLoopStartMs newStart=null " +
                    "oldEnd=$selectedSegmentLoopEndMs newEnd=null"
            )
	            selectedSegmentLoopStartMs = null
	            selectedSegmentLoopEndMs = null
	            preparedLoopStartMs = null
	        }
        pendingSegmentEditUndoSnapshot = null
        persistArrangementState(
            nextSegments = snapshot.segments,
            nextStructureSegmentIds = snapshot.structureSegmentIds,
            nextEntries = snapshot.entries
        )
    }

    fun undoArrangementChange() {
        val snapshot = arrangementUndoStack.lastOrNull() ?: return
        arrangementUndoStack = arrangementUndoStack.dropLast(1)
        stopStructurePreviewPlayback(reason = "arrangement_undo")
        stopArrangementLoopPreviewPlayback()
        restoreArrangementUndoSnapshot(snapshot)
    }

    fun removeArrangementSegment(targetId: String) {
        val nextSegments = arrangementSegments.filterNot { it.id == targetId }
        if (nextSegments.size == arrangementSegments.size) return
        pushArrangementUndoSnapshot()
        val nextStructureSegmentIds = structureSegmentIds.filterNot { it == targetId }
        val structureChanged = nextStructureSegmentIds.size != structureSegmentIds.size
        val nextEntries = if (constrainToAvailableHeight) {
            reconcileArrangementEntries(
                segments = nextSegments,
                structureSegmentIds = nextStructureSegmentIds,
                existingEntries = arrangementEntries
            )
        } else {
            arrangementEntries
        }
        arrangementSegments = nextSegments
        structureSegmentIds = nextStructureSegmentIds
        arrangementEntries = nextEntries
	        if (selectedSegmentLoopId == targetId) {
            Log.d(
                ARR_SEGMENT_STATE_TAG,
                "STATE_CHANGE reason=remove_selected_segment callSite=removeArrangementSegment " +
                    "oldId=$selectedSegmentLoopId newId=null " +
                    "oldStart=$selectedSegmentLoopStartMs newStart=null " +
                    "oldEnd=$selectedSegmentLoopEndMs newEnd=null"
            )
	            selectedSegmentLoopId = null
	            selectedSegmentLoopStartMs = null
	            selectedSegmentLoopEndMs = null
            preparedLoopStartMs = null
            loopEnabled = false
            if (isPreparedClipLoopTestActive) {
                onStopPreparedClipLoopTest()
            }
        }
        if (structureChanged && structurePlaybackActive) {
            stopStructurePreviewPlayback(reason = "structure_changed")
        }
        selectedStructureEditIndex = selectedStructureEditIndex
            ?.takeIf { index -> index in nextStructureSegmentIds.indices }
        nextSegmentIndex = resolveNextTimelineArrangementSegmentIndex(
            nextSegments + preservedLegacyArrangementSegments
        )
        persistArrangementState(
            nextSegments = nextSegments,
            nextStructureSegmentIds = nextStructureSegmentIds,
            nextEntries = nextEntries
        )
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
        stopStructurePreviewPlayback(reason = "song_changed")
        stopArrangementLoopPreviewPlayback()
        currentSongAudioPath = null
        currentSongTitle = null
        currentSongTrackGainDb = 0
        replacePreviewRenderedFile(null)
        if (songId.isEmpty()) {
            waveformPeaks = emptyList()
            waveformDurationMs = 0
            waveformLoading = false
                waveformError = false
                arrangementName = "Arrangement 1"
                arrangementSegments = emptyList()
                structureSegmentIds = emptyList()
                arrangementEntries = emptyList()
                preservedLegacyArrangementSegments = emptyList()
                currentSongTrackGainDb = 0
                nextSegmentIndex = 1L
	            renameSegmentId = null
	            segmentOptionsTargetId = null
            Log.d(
                ARR_SEGMENT_STATE_TAG,
                "STATE_CHANGE reason=current_song_empty callSite=LaunchedEffect(currentSongId) " +
                    "oldId=$selectedSegmentLoopId newId=null " +
                    "oldStart=$selectedSegmentLoopStartMs newStart=null " +
                    "oldEnd=$selectedSegmentLoopEndMs newEnd=null"
            )
	            selectedSegmentLoopId = null
	            selectedSegmentLoopStartMs = null
	            selectedSegmentLoopEndMs = null
            arrangementLoopPreviewActive = false
            arrangementLoopPositionMs = 0L
            structurePlaybackActive = false
            structurePlaybackIndex = -1
            queuedStructureSegmentIndex = null
            selectedStructureEditIndex = null
            structureEditFocusRequest = 0
            structurePlaybackAbsolutePositionMs = 0L
            wavPreviewPositionMs = 0L
            wavPreviewDurationMs = 0L
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
                val trackGainDb = TrackVolumePrefs.getDb(context, audioUri.toString())
                    ?: SmpConfig.readPlaybackFromSongUnit(song)?.volumeDb
                    ?: 0
                val peaks = WaveformPeaksCache.getOrCompute(
                    context = context,
                    uri = audioUri,
                    targetPoints = 20_000,
                    durationMs = durationMs
                ) {
                    WaveformExtractor.extractNormalizedPeaks(
                        context = context,
                        uri = audioUri,
                        targetPoints = 20_000
                    )
                }
                Quadruple(peaks, durationMs, song, trackGainDb)
            }
        }

        result
            .onSuccess { (peaks, durationMs, song, trackGainDb) ->
                waveformPeaks = peaks
                waveformDurationMs = durationMs
                currentSongAudioPath = song.audioPath
                currentSongTitle = song.title
                currentSongTrackGainDb = trackGainDb
                Log.d(
                    ARR_TIMING_DIAG_TAG,
                    "LOAD songId=$songId audioPath=${song.audioPath} " +
                        "sourceUri=${song.audioPath?.let { Uri.fromFile(File(it)) }} " +
                        "waveformDurationMs=$durationMs playerDurationMs=$durationMs " +
                        "durationDiffMs=0 source=${arrangementTimingSourceLabel(song.audioPath)}"
                )
                waveformLoading = false
            }
            .onFailure {
                waveformPeaks = emptyList()
                waveformDurationMs = 0
                currentSongAudioPath = null
                currentSongTitle = null
                currentSongTrackGainDb = 0
                waveformLoading = false
                waveformError = true
            }

        val arrangementData = ArrangementStore.load(context.applicationContext, songId)
        arrangementName = arrangementData?.name?.ifBlank { "Arrangement 1" } ?: "Arrangement 1"
        if (constrainToAvailableHeight && arrangementData != null) {
            val occurrenceProjection = arrangementData.toOccurrenceProjection()
            arrangementSegments = occurrenceProjection.segments
            structureSegmentIds = occurrenceProjection.structureSegmentIds
            arrangementEntries = occurrenceProjection.entries
            preservedLegacyArrangementSegments = occurrenceProjection.preservedLegacySegments
        } else {
            arrangementSegments = arrangementData?.segments.orEmpty()
            structureSegmentIds = arrangementData?.structureSegmentIds.orEmpty()
            arrangementEntries = arrangementData?.entries.orEmpty()
            preservedLegacyArrangementSegments = emptyList()
        }
        Log.d(
            ARR_SEGMENT_PERSIST_TAG,
            "LOAD_FROM_STORE songId=$songId segments=${arrangementSegments.persistDebugSnapshot()} " +
                "structureIds=$structureSegmentIds"
        )
        selectedStructureEditIndex = null
        structureEditFocusRequest = 0
        nextSegmentIndex = resolveNextTimelineArrangementSegmentIndex(
            arrangementSegments + preservedLegacyArrangementSegments
        )
	        renameSegmentId = null
	        segmentOptionsTargetId = null
        Log.d(
            ARR_SEGMENT_STATE_TAG,
            "STATE_CHANGE reason=arrangement_loaded_reset_selection callSite=LaunchedEffect(currentSongId) " +
                "oldId=$selectedSegmentLoopId newId=null " +
                "oldStart=$selectedSegmentLoopStartMs newStart=null " +
                "oldEnd=$selectedSegmentLoopEndMs newEnd=null"
        )
	        selectedSegmentLoopId = null
	        selectedSegmentLoopStartMs = null
	        selectedSegmentLoopEndMs = null
        arrangementLoopPreviewActive = false
        arrangementLoopPositionMs = 0L
        structurePlaybackActive = false
        structurePlaybackIndex = -1
        queuedStructureSegmentIndex = null
        structurePlaybackAbsolutePositionMs = 0L
        wavPreviewPositionMs = 0L
        wavPreviewDurationMs = 0L
        suppressNextLoopAutoplay = false
    }

    val detectedTempoBpm = remember(tempoTapTimesMs) {
        estimateTappedTempoBpm(tempoTapTimesMs)
    }
    val defaultSegmentNameBase = stringResource(R.string.arrangement_segment_default_name)
    val loopReady = tempoBpm != null && savedAnchorMs != null
    val hasSegmentLoop = segmentInMs != null && segmentOutMs != null && segmentInMs != segmentOutMs
    val hasSelectedSegmentLoop =
        selectedSegmentLoopStartMs != null &&
            selectedSegmentLoopEndMs != null &&
            selectedSegmentLoopStartMs != selectedSegmentLoopEndMs
    val preparedStructureOccurrences = remember(
        arrangementSegments,
        structureSegmentIds,
        arrangementEntries,
        constrainToAvailableHeight
    ) {
        prepareArrangementOccurrences(
            segments = arrangementSegments,
            structureSegmentIds = structureSegmentIds,
            entries = arrangementEntries,
            useOccurrenceModel = constrainToAvailableHeight
        )
    }
    val structurePlaybackSegments = remember(preparedStructureOccurrences) {
        preparedStructureOccurrences.map { occurrence -> occurrence.segment }
    }
    val previewRenderSignature = remember(currentSongAudioPath, structurePlaybackSegments) {
        buildString {
            append(currentSongAudioPath.orEmpty())
            append('|')
            structurePlaybackSegments.forEach { segment ->
                append(segment.id)
                append(':')
                append(segment.startMs)
                append('-')
                append(segment.endMs)
                append(';')
            }
        }
    }
    val activeStructureSegmentId = structurePlaybackSegments
        .getOrNull(structurePlaybackIndex)
        ?.id
	val activeStructureEntryIndex = preparedStructureOccurrences
	    .getOrNull(structurePlaybackIndex)
	    ?.entryIndex
	val queuedStructureEntryIndex = queuedStructureSegmentIndex
	    ?.let { index -> preparedStructureOccurrences.getOrNull(index)?.entryIndex }
	val preparedArrangementPlayhead = if (!constrainToAvailableHeight) {
	    null
	} else if (structurePlaybackActive) {
	    resolvePreparedArrangementPlayheadFromSource(
	        occurrences = preparedStructureOccurrences,
	        playbackIndex = structurePlaybackIndex,
	        sourcePositionMs = structurePlaybackAbsolutePositionMs
	    )
	} else if (wavPreviewActive) {
	    resolvePreparedArrangementPlayheadFromTimeline(
	        occurrences = preparedStructureOccurrences,
	        arrangementPositionMs = wavPreviewPositionMs
	    )
	} else {
	    null
	}
	val arrangementTrackPlayhead = preparedArrangementPlayhead?.let { playhead ->
	    ArrangementTrackPlayhead(
	        itemId = playhead.entryIndex.toString(),
	        repeatIndex = playhead.repeatIndex,
	        repeatCount = playhead.repeatCount,
	        segmentProgressFraction = playhead.segmentProgressFraction
	    )
	}
	    val selectedStructureEditSegment = selectedStructureEditIndex
	        ?.let { index -> structureSegmentIds.getOrNull(index) }
	        ?.let { segmentId -> arrangementSegments.firstOrNull { it.id == segmentId } }
	    val selectedArrangementEditSegment = selectedSegmentLoopId
	        ?.let { segmentId -> arrangementSegments.firstOrNull { it.id == segmentId } }
    val isSegmentEditMode = selectedArrangementEditSegment != null
    LaunchedEffect(
        selectedSegmentLoopId,
        selectedSegmentLoopStartMs,
        selectedSegmentLoopEndMs,
        isSegmentEditMode
    ) {
        val oldState = previousObservedSegmentState
        val newState = Triple(selectedSegmentLoopId, selectedSegmentLoopStartMs, selectedSegmentLoopEndMs)
        Log.d(
            ARR_SEGMENT_STATE_TAG,
            "STATE_OBSERVED reason=state_changed oldId=${oldState?.first} newId=${newState.first} " +
                "oldStart=${oldState?.second} newStart=${newState.second} " +
                "oldEnd=${oldState?.third} newEnd=${newState.third} " +
                "isSegmentEditMode=$isSegmentEditMode"
        )
        previousObservedSegmentState = newState
    }
    val latestArrangementSegments by rememberUpdatedState(arrangementSegments)
    val latestStructureSegmentIds by rememberUpdatedState(structureSegmentIds)
    val latestArrangementEntries by rememberUpdatedState(arrangementEntries)
    val latestStructurePlaybackSegments by rememberUpdatedState(structurePlaybackSegments)
    val latestCurrentSongAudioPath by rememberUpdatedState(currentSongAudioPath)
    val latestCurrentStructureSourcePath by rememberUpdatedState(currentStructureSourcePath)
    DisposableEffect(structureSamplerEngine) {
        structureSamplerEngine.autoAdvanceSequentially = true
        structureSamplerEngine.setCrossfadeDurationMs(0)
        structureSamplerEngine.setAntiClickFadeDurationMs(2)
        structureSamplerEngine.onSegmentStart = { index ->
            scope.launch(Dispatchers.Main) {
                val segment = latestStructurePlaybackSegments.getOrNull(index)
                Log.d(
                    ARR_STRUCTURE_SAMPLER_TAG,
                    "PLAY_SEGMENT index=$index name=${segment?.name.orEmpty()}"
                )
                if (segment != null && structurePlaybackActive && structureUsingSampler) {
                    val previousIndex = structurePlaybackIndex
                    structurePlaybackIndex = index
                    queuedStructureSegmentIndex = null
                    structureSamplerSegmentStartRealtimeMs = SystemClock.elapsedRealtime()
                    structurePlaybackAbsolutePositionMs = minOf(segment.startMs, segment.endMs).coerceAtLeast(0L)
                    if (previousIndex >= 0 && previousIndex != index) {
                        Log.d(
                            ARR_STRUCTURE_SAMPLER_TAG,
                            "TRANSITION indexFrom=$previousIndex indexTo=$index"
                        )
                    }
                }
            }
        }
        structureSamplerEngine.onSegmentTransition = { fromIndex, toIndex ->
            Log.d(
                ARR_STRUCTURE_SAMPLER_TAG,
                "TRANSITION indexFrom=$fromIndex indexTo=$toIndex"
            )
        }
        structureSamplerEngine.onPlaybackEnded = {
            scope.launch(Dispatchers.Main) {
                Log.d(ARR_STRUCTURE_SAMPLER_TAG, "STOP reason=sampler_ended")
                stopStructurePreviewPlayback(reason = "sampler_ended")
            }
        }
        onDispose {
            structureSamplerEngine.onSegmentStart = null
            structureSamplerEngine.onSegmentTransition = null
            structureSamplerEngine.onPlaybackEnded = null
            structureSamplerEngine.autoAdvanceSequentially = false
            structureSamplerEngine.release()
        }
    }
    val isLoopHighlighted =
        (loopReady || hasSegmentLoop || hasSelectedSegmentLoop) &&
            (loopEnabled || arrangementLoopPreviewActive || isPreparedClipLoopTestActive)
    val activeLoopRange = remember(
        selectedSegmentLoopStartMs,
        selectedSegmentLoopEndMs,
        segmentInMs,
        segmentOutMs
    ) {
        val rawLoopStartMs = selectedSegmentLoopStartMs ?: segmentInMs
        val rawLoopEndMs = selectedSegmentLoopEndMs ?: segmentOutMs
        if (rawLoopStartMs == null || rawLoopEndMs == null || rawLoopStartMs == rawLoopEndMs) {
            null
        } else {
            val loopStartMs = minOf(rawLoopStartMs, rawLoopEndMs).coerceAtLeast(0L)
            val loopEndMs = maxOf(rawLoopStartMs, rawLoopEndMs).coerceAtLeast(loopStartMs + 1L)
            loopStartMs to loopEndMs
        }
    }
    fun seekStructurePreviewToAbsolutePosition(targetPositionMs: Long) {
        if (!structurePlaybackActive) {
            seekToMs(targetPositionMs)
            return
        }
        val targetSegmentIndex = structurePlaybackSegments.indexOfFirst { segment ->
            val startMs = minOf(segment.startMs, segment.endMs)
            val endMs = maxOf(segment.startMs, segment.endMs)
            targetPositionMs in startMs..endMs
        }
        if (targetSegmentIndex < 0) {
            return
        }
        val targetSegment = structurePlaybackSegments[targetSegmentIndex]
        val segmentStartMs = minOf(targetSegment.startMs, targetSegment.endMs).coerceAtLeast(0L)
        val segmentEndMs = maxOf(targetSegment.startMs, targetSegment.endMs).coerceAtLeast(segmentStartMs + 1L)
        val relativePositionMs = (targetPositionMs - segmentStartMs)
            .coerceIn(0L, (segmentEndMs - segmentStartMs - 1L).coerceAtLeast(0L))
        if (structureUsingSampler) {
            val fallbackSourcePath = currentStructureSourcePath
                ?.takeIf { it.isNotBlank() }
                ?: currentSongAudioPath
                ?: return
            Log.d(
                ARR_STRUCTURE_SAMPLER_TAG,
                "FALLBACK_EXOPLAYER reason=seek_not_supported targetIndex=$targetSegmentIndex"
            )
            structureUsingSampler = false
            runCatching { structureSamplerEngine.stop() }
            playStructureSegmentPreview(
                startIndex = targetSegmentIndex,
                audioPath = fallbackSourcePath,
                segments = structurePlaybackSegments
            )
            runCatching { structurePreviewPlayer.seekTo(0, relativePositionMs) }
            structurePlaybackAbsolutePositionMs = segmentStartMs + relativePositionMs
            return
        }
        structurePlaybackIndex = targetSegmentIndex
        structurePlaybackAbsolutePositionMs = segmentStartMs + relativePositionMs
        runCatching { structurePreviewPlayer.seekTo(targetSegmentIndex, relativePositionMs) }
        runCatching {
            if (!structurePreviewPlayer.isPlaying && structurePlaybackActive) {
                structurePreviewPlayer.play()
            }
        }
    }

    fun selectArrangementSegmentForEdit(
        segment: ArrangementSegmentData,
        structureIndex: Int?,
        source: String
    ) {
        val selectedStartMs = minOf(segment.startMs, segment.endMs).coerceAtLeast(0L)
        val selectedEndMs = maxOf(segment.startMs, segment.endMs)
            .coerceAtLeast(selectedStartMs + 1L)
        val previousSelectionCount = segmentSelectionCounts[segment.id] ?: 0
        segmentSelectionCounts[segment.id] = previousSelectionCount + 1
        Log.d(
            ARR_SEGMENT_PERSIST_TAG,
            "SELECT source=$source selectedSegmentId=${segment.id} " +
                "selectedSegmentName=${segment.name} selectedStartMs=$selectedStartMs " +
                "selectedEndMs=$selectedEndMs structureIndex=$structureIndex " +
                "fromArrangement=${arrangementSegments.firstOrNull { it.id == segment.id }?.persistDebugLabel()} " +
                "fromStructure=${structureIndex?.let { index -> structureSegmentIds.getOrNull(index) }?.let { id -> arrangementSegments.firstOrNull { it.id == id } }?.persistDebugLabel()} " +
                "allSegments=${arrangementSegments.persistDebugSnapshot()} structureIds=$structureSegmentIds"
        )
        if (previousSelectionCount > 0) {
            Log.d(
                ARR_SEGMENT_PERSIST_TAG,
                "SELECT_AFTER_RETURN source=$source segmentId=${segment.id} " +
                    "startMs=$selectedStartMs endMs=$selectedEndMs " +
                    "handlesSource=${arrangementSegments.firstOrNull { it.id == segment.id }?.persistDebugLabel()} " +
                    "selectionCountBefore=$previousSelectionCount"
            )
        }
	        selectedStructureEditIndex = structureIndex
	        segmentInMs = selectedStartMs
	        segmentOutMs = selectedEndMs
        Log.d(
            ARR_SEGMENT_STATE_TAG,
            "STATE_CHANGE reason=select_segment callSite=selectArrangementSegmentForEdit " +
                "oldId=$selectedSegmentLoopId newId=${segment.id} " +
                "oldStart=$selectedSegmentLoopStartMs newStart=$selectedStartMs " +
                "oldEnd=$selectedSegmentLoopEndMs newEnd=$selectedEndMs " +
                "source=$source"
        )
	        selectedSegmentLoopId = segment.id
	        selectedSegmentLoopStartMs = selectedStartMs
	        selectedSegmentLoopEndMs = selectedEndMs
        preparedLoopStartMs = selectedStartMs
        suppressNextLoopAutoplay = false
        lastWaveformFocusMarker = TimelineWaveformFocusMarker.IN
        structureEditFocusRequest += 1
    }

    fun updateSelectedArrangementSegmentBounds(
        nextStartMs: Long,
        nextEndMs: Long,
        commit: Boolean,
        handle: TimelineArrangementEditHandle
    ) {
        val targetSegment = selectedArrangementEditSegment ?: return
        val boundedStartMs = nextStartMs.coerceAtLeast(0L)
        val boundedEndMs = nextEndMs.coerceAtLeast(boundedStartMs + 1L)
        val oldSegment = latestArrangementSegments.firstOrNull { it.id == targetSegment.id }
            ?: targetSegment
        if (pendingSegmentEditUndoSnapshot == null) {
            val snapshot = currentArrangementUndoSnapshot()
            pendingSegmentEditUndoSnapshot = snapshot
            pushArrangementUndoSnapshot(snapshot)
        }
        Log.d(
            ARR_SEGMENT_PERSIST_TAG,
            "DRAG segmentId=${targetSegment.id} handle=$handle " +
                "oldStartMs=${oldSegment.startMs} oldEndMs=${oldSegment.endMs} " +
                "newStartMs=$boundedStartMs newEndMs=$boundedEndMs commit=$commit"
        )
        val nextSegments = latestArrangementSegments.map { segment ->
            if (segment.id == targetSegment.id) {
                segment.copy(startMs = boundedStartMs, endMs = boundedEndMs)
            } else {
                segment
            }
        }
        val nextEntries = if (constrainToAvailableHeight) {
            reconcileArrangementEntries(
                segments = nextSegments,
                structureSegmentIds = latestStructureSegmentIds,
                existingEntries = latestArrangementEntries
            )
        } else {
            latestArrangementEntries
        }
        arrangementSegments = nextSegments
        arrangementEntries = nextEntries
        segmentInMs = boundedStartMs
        segmentOutMs = boundedEndMs
        Log.d(
            ARR_SEGMENT_STATE_TAG,
            "STATE_CHANGE reason=edit_segment_bounds callSite=updateSelectedArrangementSegmentBounds " +
                "oldId=$selectedSegmentLoopId newId=${targetSegment.id} " +
                "oldStart=$selectedSegmentLoopStartMs newStart=$boundedStartMs " +
                "oldEnd=$selectedSegmentLoopEndMs newEnd=$boundedEndMs " +
                "commit=$commit"
        )
	        selectedSegmentLoopId = targetSegment.id
        selectedSegmentLoopStartMs = boundedStartMs
        selectedSegmentLoopEndMs = boundedEndMs
        preparedLoopStartMs = boundedStartMs
        suppressNextLoopAutoplay = false
        if (structurePlaybackActive) {
            stopStructurePreviewPlayback(reason = "structure_segment_edited")
        }
        if (commit) {
            val committedHandleTimeMs = when (handle) {
                TimelineArrangementEditHandle.IN -> boundedStartMs
                TimelineArrangementEditHandle.OUT -> boundedEndMs
                TimelineArrangementEditHandle.NONE -> boundedStartMs
            }
            Log.d(
                ARR_TIMING_DIAG_TAG,
                "ACTION action=${if (handle == TimelineArrangementEditHandle.OUT) "SET_OUT" else "SET_IN"} " +
                    "x=HANDLE calculatedTimeMs=$committedHandleTimeMs clampedTimeMs=$committedHandleTimeMs " +
                    "waveformZoom=NA waveformCenterFraction=NA startFraction=NA endFraction=NA " +
                    "safeDurationMs=$waveformDurationMs segmentId=${targetSegment.id}"
            )
            Log.d(
                ARR_SEGMENT_PERSIST_TAG,
                "COMMIT_START segmentId=${targetSegment.id} " +
                    "committedStartMs=$boundedStartMs committedEndMs=$boundedEndMs"
            )
            persistArrangementState(
                nextSegments = nextSegments,
                nextStructureSegmentIds = latestStructureSegmentIds,
                nextEntries = nextEntries,
                debugSegmentId = targetSegment.id
            )
            if (loopEnabled && !structurePlaybackActive && arrangementLoopPreviewActive) {
                currentSongAudioPath?.takeIf { it.isNotBlank() }?.let { audioPath ->
                    arrangementLoopPositionMs = 0L
                    arrangementPreviewPlayer.playLoop(
                        sourceUri = Uri.fromFile(File(audioPath)),
                        startMs = boundedStartMs,
                        endMs = boundedEndMs
                    )
                    Log.d(
                        ARR_TIMING_DIAG_TAG,
                        "PLAY_START playMode=LOOP requestedStartMs=$boundedStartMs requestedEndMs=$boundedEndMs " +
                            "actualPlayerPositionMs=${arrangementPreviewPlayer.currentPositionMs()} " +
                            "source=${arrangementTimingSourceLabel(audioPath)} sourceUri=${Uri.fromFile(File(audioPath))}"
                    )
                    publishSecondaryPlaybackState()
                }
            }
            pendingSegmentEditUndoSnapshot = null
        }
    }

    fun captureSelectedArrangementSegmentUndoSnapshot() {
        val targetSegment = selectedArrangementEditSegment
        if (targetSegment == null || pendingSegmentEditUndoSnapshot != null) return
        Log.d(
            ARR_UNDO_HANDLE_TAG,
            "UNDO_PUSH_BEFORE_DRAG segmentId=${targetSegment.id} " +
                "startMs=${targetSegment.startMs} endMs=${targetSegment.endMs} " +
                "undoStackSizeBefore=${arrangementUndoStack.size}"
        )
        val snapshot = currentArrangementUndoSnapshot()
        pendingSegmentEditUndoSnapshot = snapshot
        pushArrangementUndoSnapshot(snapshot)
        Log.d(
            ARR_UNDO_HANDLE_TAG,
            "UNDO_PUSH_DONE undoStackSizeAfter=${arrangementUndoStack.size}"
        )
    }

    val arrangementStructuralActionsEnabled =
        !structurePlaybackActive &&
            !isStructureAudioPreparing &&
            !wavPreviewActive &&
            !isPreviewGenerating &&
            !isFinalExporting

    fun startStructurePreviewAtLogicalIndex(logicalIndex: Int) {
        if (isStructureAudioPreparing || structurePlaybackActive) return
        val startIndex = preparedStructureOccurrences.indexOfFirst { occurrence ->
            occurrence.entryIndex == logicalIndex
        }
        if (startIndex < 0) return
        val sourceAudioPath = currentSongAudioPath?.takeIf { it.isNotBlank() } ?: return
        val playbackSegments = structurePlaybackSegments

        stopStructurePreviewPlayback(reason = "structure_manual_start")
        val preparationGeneration = structurePreparationGeneration
        stopArrangementLoopPreviewPlayback()
        if (isPreparedClipLoopTestActive) {
            onStopPreparedClipLoopTest()
        }
        loopEnabled = false
        preparedLoopStartMs = null
        onIsPlayingChange(false)
        isStructureAudioPreparing = true
        scope.launch {
            try {
                val sourceUri = Uri.fromFile(File(sourceAudioPath))
                val preparedSourceResult = withContext(Dispatchers.IO) {
                    runCatching {
                        Log.d(
                            ARR_STRUCTURE_SAMPLER_TAG,
                            "PRELOAD_START songId=${currentSongId?.trim().orEmpty()} segmentCount=${playbackSegments.size}"
                        )
                        ArrangementSourceWavCache.ensureSourceWav(
                            context = context.applicationContext,
                            songId = currentSongId?.trim().orEmpty(),
                            sourceUri = sourceUri
                        )
                    }
                }
                if (preparationGeneration != structurePreparationGeneration) {
                    return@launch
                }

                preparedSourceResult
                    .onSuccess { wavFile ->
                        structureUsingWavSource = true
                        val sampleResult = withContext(Dispatchers.IO) {
                            runCatching {
                                buildSampleSegmentsFromWav(
                                    wavFile = wavFile,
                                    structureSegments = playbackSegments
                                )
                            }
                        }
                        sampleResult
                            .onSuccess { sampleSegments ->
                                runCatching {
                                    structureSamplerEngine.loadSegments(sampleSegments)
                                    structureSamplerReady = true
                                    Log.d(
                                        ARR_STRUCTURE_SAMPLER_TAG,
                                        "PRELOAD_DONE segmentCount=${sampleSegments.size} source=${wavFile.absolutePath}"
                                    )
                                }.onFailure { error ->
                                    structureSamplerReady = false
                                    Log.w(
                                        ARR_STRUCTURE_SAMPLER_TAG,
                                        "PRELOAD_FAIL reason=sampler_load_failed error=${error.message}",
                                        error
                                    )
                                }
                            }
                            .onFailure { error ->
                                structureSamplerReady = false
                                Log.w(
                                    ARR_STRUCTURE_SAMPLER_TAG,
                                    "PRELOAD_FAIL reason=sample_build_failed error=${error.message}",
                                    error
                                )
                            }
                        if (preparationGeneration != structurePreparationGeneration) {
                            return@launch
                        }
                        val samplerStarted = playStructureSegmentWithSampler(
                            startIndex = startIndex,
                            sourcePath = wavFile.absolutePath,
                            segments = playbackSegments
                        )
                        if (!samplerStarted) {
                            Log.d(
                                ARR_STRUCTURE_SAMPLER_TAG,
                                "FALLBACK_EXOPLAYER reason=sampler_not_ready"
                            )
                            Log.d(ARR_STRUCTURE_SAMPLER_TAG, "USING_SAMPLER false")
                            structureUsingWavSource = true
                            playStructureSegmentPreview(
                                startIndex = startIndex,
                                audioPath = wavFile.absolutePath,
                                segments = playbackSegments
                            )
                        }
                    }
                    .onFailure { error ->
                        if (preparationGeneration != structurePreparationGeneration) {
                            return@onFailure
                        }
                        Log.w(
                            ARR_STRUCTURE_WAV_TAG,
                            "CACHE_BUILD_FAIL error=${error.message}",
                            error
                        )
                        Log.w(
                            ARR_STRUCTURE_SAMPLER_TAG,
                            "PRELOAD_FAIL reason=wav_cache_failed error=${error.message}",
                            error
                        )
                        Log.d(
                            ARR_STRUCTURE_WAV_TAG,
                            "FALLBACK_MP3 reason=cache_build_fail"
                        )
                        Log.d(
                            ARR_STRUCTURE_SAMPLER_TAG,
                            "FALLBACK_EXOPLAYER reason=wav_cache_failed"
                        )
                        Log.d(ARR_STRUCTURE_SAMPLER_TAG, "USING_SAMPLER false")
                        structureSamplerReady = false
                        structureUsingWavSource = false
                        playStructureSegmentPreview(
                            startIndex = startIndex,
                            audioPath = sourceAudioPath,
                            segments = playbackSegments
                        )
                    }
            } finally {
                isStructureAudioPreparing = false
            }
        }
    }

    fun updateArrangementOccurrence(
        targetId: String,
        transform: (ArrangementEntryData) -> ArrangementEntryData
    ) {
        val segment = arrangementSegments.firstOrNull { it.id == targetId } ?: return
        val currentEntry = arrangementEntries.firstOrNull { it.entryId == targetId }
            ?: ArrangementEntryData(
                entryId = segment.id,
                name = segment.name,
                startMs = segment.startMs,
                endMs = segment.endMs
            )
        val nextEntry = transform(currentEntry).copy(entryId = targetId)
        if (nextEntry == currentEntry) return
        pushArrangementUndoSnapshot()
        val nextSegments = arrangementSegments.map { currentSegment ->
            if (currentSegment.id == targetId) {
                currentSegment.copy(
                    name = nextEntry.name,
                    startMs = nextEntry.startMs,
                    endMs = nextEntry.endMs
                )
            } else {
                currentSegment
            }
        }
        val metadataEntries = arrangementEntries
            .filterNot { entry -> entry.entryId == targetId } + nextEntry
        val nextEntries = reconcileArrangementEntries(
            segments = nextSegments,
            structureSegmentIds = structureSegmentIds,
            existingEntries = metadataEntries
        )
        arrangementSegments = nextSegments
        arrangementEntries = nextEntries
        persistArrangementState(nextSegments = nextSegments, nextEntries = nextEntries)
    }

    fun insertArrangementOccurrenceAfter(
        targetIndex: Int,
        template: ArrangementEntryData
    ) {
        if (!constrainToAvailableHeight || !arrangementStructuralActionsEnabled) return
        val insertIndex = (targetIndex + 1).coerceIn(0, structureSegmentIds.size)
        var candidateIndex = nextSegmentIndex
        val usedIds = (arrangementSegments.map { segment -> segment.id } +
            preservedLegacyArrangementSegments.map { segment -> segment.id }).toSet()
        var newEntryId = "segment_$candidateIndex"
        while (newEntryId in usedIds) {
            candidateIndex += 1L
            newEntryId = "segment_$candidateIndex"
        }
        val nextEntry = template.copy(
            entryId = newEntryId,
            repeatCount = template.repeatCount.coerceAtLeast(1)
        )
        val nextSegment = ArrangementSegmentData(
            id = nextEntry.entryId,
            name = nextEntry.name,
            startMs = nextEntry.startMs,
            endMs = nextEntry.endMs
        )
        pushArrangementUndoSnapshot()
        val nextStructureSegmentIds = structureSegmentIds.toMutableList().apply {
            add(insertIndex, newEntryId)
        }
        val nextSegmentsById = (arrangementSegments + nextSegment).associateBy { segment -> segment.id }
        val nextSegments = nextStructureSegmentIds.mapNotNull(nextSegmentsById::get)
        val nextEntriesById = (arrangementEntries + nextEntry).associateBy { entry -> entry.entryId }
        val nextEntries = nextStructureSegmentIds.mapNotNull(nextEntriesById::get)
        arrangementSegments = nextSegments
        structureSegmentIds = nextStructureSegmentIds
        arrangementEntries = nextEntries
        nextSegmentIndex = candidateIndex + 1L
        selectedStructureEditIndex = insertIndex
        selectArrangementSegmentForEdit(
            segment = nextSegment,
            structureIndex = insertIndex,
            source = "OCCURRENCE_INSERT"
        )
        persistArrangementState(
            nextSegments = nextSegments,
            nextStructureSegmentIds = nextStructureSegmentIds,
            nextEntries = nextEntries
        )
    }

    fun moveArrangementOccurrence(sourceIndex: Int, direction: Int) {
        if (!constrainToAvailableHeight || !arrangementStructuralActionsEnabled) return
        if (sourceIndex !in structureSegmentIds.indices) return
        val targetIndex = (sourceIndex + direction).coerceIn(0, structureSegmentIds.lastIndex)
        if (targetIndex == sourceIndex) return
        val selectedEntryId = selectedStructureEditIndex
            ?.let { index -> structureSegmentIds.getOrNull(index) }
        pushArrangementUndoSnapshot()
        val nextStructureSegmentIds = structureSegmentIds.toMutableList().apply {
            val movedId = removeAt(sourceIndex)
            add(targetIndex, movedId)
        }
        val segmentsById = arrangementSegments.associateBy { segment -> segment.id }
        val nextSegments = nextStructureSegmentIds.mapNotNull(segmentsById::get)
        val nextEntries = reconcileArrangementEntries(
            segments = nextSegments,
            structureSegmentIds = nextStructureSegmentIds,
            existingEntries = arrangementEntries
        )
        arrangementSegments = nextSegments
        structureSegmentIds = nextStructureSegmentIds
        arrangementEntries = nextEntries
        selectedStructureEditIndex = selectedEntryId
            ?.let(nextStructureSegmentIds::indexOf)
            ?.takeIf { index -> index >= 0 }
        persistArrangementState(
            nextSegments = nextSegments,
            nextStructureSegmentIds = nextStructureSegmentIds,
            nextEntries = nextEntries
        )
    }

    val listenAction: () -> Unit = {
        Log.d(
            ARR_PREVIEW_WAV_TAG,
            "PREVIEW_REQUESTED songId=${currentSongId?.trim().orEmpty()} " +
                "audioPath=$currentSongAudioPath structureCount=${structurePlaybackSegments.size} " +
                "structurePlaybackActive=$structurePlaybackActive wavPreviewActive=$wavPreviewActive " +
                "arrangementLoopPreviewActive=$arrangementLoopPreviewActive isPreviewGenerating=$isPreviewGenerating"
        )
        if (structurePlaybackActive || wavPreviewActive || arrangementLoopPreviewActive) {
            Log.d(
                ARR_PREVIEW_WAV_TAG,
                "PREVIEW_STOP_REQUEST reason=listen_action_toggle " +
                    "structurePlaybackActive=$structurePlaybackActive wavPreviewActive=$wavPreviewActive " +
                    "arrangementLoopPreviewActive=$arrangementLoopPreviewActive"
            )
            stopStructurePreviewPlayback(reason = "listen_action_stop")
            stopArrangementLoopPreviewPlayback()
        } else {
            val audioPath = currentSongAudioPath
            val playlistSegments = structurePlaybackSegments
            if (audioPath.isNullOrBlank()) {
                Unit
            } else if (playlistSegments.isEmpty()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.arrangement_preview_structure_empty),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val reusablePreviewFile = previewRenderedFile?.takeIf { file ->
                    file.absolutePath == arrangementPreviewCacheFile.absolutePath &&
                        file.isFile &&
                        previewRenderedSignature == previewRenderSignature
                }
                Log.d(
                    ARR_PREVIEW_WAV_TAG,
                    "SOURCE_ARRANGEMENT audioPath=$audioPath outputPath=${arrangementPreviewCacheFile.absolutePath} " +
                        "signature=$previewRenderSignature reusable=${reusablePreviewFile != null} " +
                        "segments=${playlistSegments.joinToString { segment ->
                            "${segment.id}:${segment.startMs}-${segment.endMs}"
                        }}"
                )
                if (reusablePreviewFile != null) {
                    Log.d(
                        ARR_PREVIEW_WAV_TAG,
                        "REUSE_PREVIEW path=${reusablePreviewFile.absolutePath} exists=${reusablePreviewFile.isFile} " +
                            "size=${reusablePreviewFile.length()}"
                    )
                    playArrangementPreviewFile(reusablePreviewFile)
                } else {
                    isPreviewGenerating = true
                    scope.launch {
                        Log.d(
                            ARR_PREVIEW_WAV_TAG,
                            "RENDER_START audioPath=$audioPath outputPath=${arrangementPreviewCacheFile.absolutePath} " +
                                "segmentCount=${playlistSegments.size}"
                        )
                        val result = runCatching {
                            ArrangementWavRenderer.render(
                                context = context.applicationContext,
                                audioPath = audioPath,
                                segments = playlistSegments.map { segment ->
                                    minOf(segment.startMs, segment.endMs) to
                                        maxOf(segment.startMs, segment.endMs).coerceAtLeast(
                                            minOf(segment.startMs, segment.endMs) + 1L
                                        )
                                },
                                outputFile = arrangementPreviewCacheFile
                            )
                        }

                        result
                            .onSuccess { previewFile ->
                                Log.d(
                                    ARR_PREVIEW_WAV_TAG,
                                    "RENDER_END path=${previewFile.absolutePath} exists=${previewFile.isFile} " +
                                        "size=${previewFile.length()}"
                                )
                                replacePreviewRenderedFile(previewFile)
                                previewRenderedSignature = previewRenderSignature
                                playArrangementPreviewFile(previewFile)
                            }
                            .onFailure { error ->
                                Log.w(
                                    ARR_PREVIEW_WAV_TAG,
                                    "RENDER_FAIL outputPath=${arrangementPreviewCacheFile.absolutePath} " +
                                        "exists=${arrangementPreviewCacheFile.isFile} size=${arrangementPreviewCacheFile.length()} " +
                                        "error=${error.message}",
                                    error
                                )
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.arrangement_preview_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        isPreviewGenerating = false
                    }
                }
            }
        }
    }

    DisposableEffect(structurePreviewPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!structurePlaybackActive) return
                val queuedIndex = queuedStructureSegmentIndex
                val currentMediaItemIndex = structurePreviewPlayer.currentMediaItemIndex
                val mediaItemCount = structurePreviewPlayer.mediaItemCount
                val autoNextStructureIndex = (structurePlaybackIndex + 1)
                    .takeIf { it in latestStructurePlaybackSegments.indices }
                val sourceUri = mediaItem?.localConfiguration?.uri
                Log.d(
                    ARR_STRUCTURE_QUEUE_TAG,
                    "MEDIA_ITEM_TRANSITION mediaIndex=$currentMediaItemIndex currentPlayingIndex=$structurePlaybackIndex queuedIndex=$queuedIndex reason=$reason"
                )
                Log.d(
                    ARR_STRUCTURE_FLOW_TAG,
                    "MEDIA_ITEM_TRANSITION reason=$reason mediaItemCount=$mediaItemCount currentMediaItemIndex=$currentMediaItemIndex currentStructureIndex=$structurePlaybackIndex queuedStructureSegmentIndex=$queuedIndex autoNextStructureIndex=$autoNextStructureIndex sourceUri=$sourceUri"
                )
                val expectedSegmentIndex = queuedIndex ?: autoNextStructureIndex ?: structurePlaybackIndex
                val expectedSegment = latestStructurePlaybackSegments.getOrNull(expectedSegmentIndex)
                if (expectedSegment != null) {
                    val expectedStartMs = minOf(expectedSegment.startMs, expectedSegment.endMs).coerceAtLeast(0L)
                    val expectedEndMs = maxOf(expectedSegment.startMs, expectedSegment.endMs)
                        .coerceAtLeast(expectedStartMs + 1L)
                    Log.d(
                        ARR_TIMING_DIAG_TAG,
                        "STRUCTURE_TRANSITION segmentId=${expectedSegment.id} " +
                            "segmentStartMs=$expectedStartMs segmentEndMs=$expectedEndMs " +
                            "mediaItemSourceUri=$sourceUri transitionTimestampMs=${SystemClock.elapsedRealtime()} " +
                            "expectedStartMs=$expectedStartMs " +
                            "currentPosition=${runCatching { structurePreviewPlayer.currentPosition }.getOrDefault(-1L)}"
                    )
                }
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val previousIndex = structurePlaybackIndex
                    if (queuedIndex != null) {
                        structurePlaybackIndex = queuedIndex
                        Log.d(
                            ARR_STRUCTURE_QUEUE_TAG,
                            "QUEUE_RESET old=$queuedStructureSegmentIndex reason=mediaItemTransition"
                        )
                        queuedStructureSegmentIndex = null
                    } else {
                        val autoNextIndex = previousIndex + 1
                        if (autoNextIndex in latestStructurePlaybackSegments.indices) {
                            structurePlaybackIndex = autoNextIndex
                        }
                    }
                    latestCurrentStructureSourcePath?.takeIf { it.isNotBlank() }?.let { audioPath ->
                        preloadAutomaticStructureSegmentPreview(
                            currentIndex = structurePlaybackIndex,
                            audioPath = audioPath,
                            segments = latestStructurePlaybackSegments
                        )
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (wavPreviewActive) {
                    Log.d(
                        ARR_PREVIEW_WAV_TAG,
                        "PLAYER_STATE state=$playbackState isPlaying=${structurePreviewPlayer.isPlaying} " +
                            "position=${structurePreviewPlayer.currentPosition} duration=${structurePreviewPlayer.duration} " +
                            "mediaItemCount=${structurePreviewPlayer.mediaItemCount}"
                    )
                }
                if (playbackState == Player.STATE_ENDED) {
                    if (wavPreviewActive && !structurePlaybackActive) {
                        Log.d(
                            ARR_PREVIEW_WAV_TAG,
                            "PLAYER_ENDED position=${structurePreviewPlayer.currentPosition} " +
                                "duration=${structurePreviewPlayer.duration}"
                        )
                        stopStructurePreviewPlayback(reason = "wav_preview_ended")
                        return
                    }
                    val validationSegments = latestStructurePlaybackSegments
                    val validationListSize = validationSegments.size
                    val mediaItemCount = structurePreviewPlayer.mediaItemCount
                    val currentMediaItemIndex = structurePreviewPlayer.currentMediaItemIndex
                    Log.d(
                        ARR_STRUCTURE_QUEUE_TAG,
                        "END_CALLBACK currentPlayingIndex=$structurePlaybackIndex queuedIndex=$queuedStructureSegmentIndex listSize=$validationListSize playerState=$playbackState playWhenReady=${structurePreviewPlayer.playWhenReady} isPlaying=${structurePreviewPlayer.isPlaying}"
                    )
                    Log.d(
                        ARR_STRUCTURE_FLOW_TAG,
                        "END_CALLBACK mediaItemCount=$mediaItemCount currentMediaItemIndex=$currentMediaItemIndex currentStructureIndex=$structurePlaybackIndex queuedStructureSegmentIndex=$queuedStructureSegmentIndex playerState=$playbackState playWhenReady=${structurePreviewPlayer.playWhenReady} isPlaying=${structurePreviewPlayer.isPlaying}"
                    )
                    Log.d(
                        ARR_STRUCTURE_QUEUE_TAG,
                        "END_DECISION action=STOP_NO_NEXT currentPlayingIndex=$structurePlaybackIndex queuedIndex=$queuedStructureSegmentIndex listSize=$validationListSize"
                    )
                    Log.d(
                        ARR_STRUCTURE_FLOW_TAG,
                        "STOP_STRUCTURE reason=state_ended_no_next currentStructureIndex=$structurePlaybackIndex queuedStructureSegmentIndex=$queuedStructureSegmentIndex mediaItemCount=$mediaItemCount"
                    )
                    Log.d(
                        ARR_STRUCTURE_QUEUE_TAG,
                        "STOP_AFTER_END reason=no queued segment currentPlayingIndex=$structurePlaybackIndex queuedIndex=$queuedStructureSegmentIndex"
                    )
                    stopStructurePreviewPlayback(reason = "state_ended_no_next")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (wavPreviewActive) {
                    Log.w(
                        ARR_PREVIEW_WAV_TAG,
                        "PLAYER_ERROR message=${error.message} code=${error.errorCode} " +
                            "position=${structurePreviewPlayer.currentPosition} duration=${structurePreviewPlayer.duration}",
                        error
                    )
                }
            }
        }
        structurePreviewPlayer.addListener(listener)
        onDispose {
            structurePreviewPlayer.removeListener(listener)
            stopStructurePreviewPlayback(reason = "dispose_structurePreviewPlayer")
            clearArrangementPreviewCache()
            runCatching { structurePreviewPlayer.release() }
        }
    }
    DisposableEffect(arrangementPreviewPlayer) {
        onDispose {
            stopArrangementLoopPreviewPlayback()
            runCatching { arrangementPreviewPlayer.release() }
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
            val activeStructurePlayback = structurePlaybackActive &&
                (structureUsingSampler || runCatching { structurePreviewPlayer.isPlaying }.getOrDefault(false))
            val activeWavPreviewPlayback = wavPreviewActive &&
                runCatching { structurePreviewPlayer.isPlaying }.getOrDefault(false)
            if (activeStructurePlayback) {
                Log.d(
                    ARR_STRUCTURE_FLOW_TAG,
                    "STOP_IGNORED reason=structure_preview_stop_request active_structure_playback=true currentStructureIndex=$structurePlaybackIndex queuedStructureSegmentIndex=$queuedStructureSegmentIndex mediaItemCount=${structurePreviewPlayer.mediaItemCount}"
                )
                return@LaunchedEffect
            }
            if (activeWavPreviewPlayback) {
                Log.d(
                    ARR_PREVIEW_WAV_TAG,
                    "STOP_IGNORED reason=structure_preview_stop_request active_wav_preview=true " +
                        "position=${structurePreviewPlayer.currentPosition} duration=${structurePreviewPlayer.duration}"
                )
                return@LaunchedEffect
            }
            stopStructurePreviewPlayback(reason = "structure_preview_stop_request")
            if (arrangementLoopPreviewActive) {
                loopEnabled = false
                preparedLoopStartMs = null
                stopArrangementLoopPreviewPlayback()
            }
        }
    }
    LaunchedEffect(
        constrainToAvailableHeight,
        selectedStructureEditIndex,
        preparedStructureOccurrences
    ) {
        val selectedLogicalIndex = selectedStructureEditIndex
        onStructurePreviewTargetChange(
            constrainToAvailableHeight &&
                selectedLogicalIndex != null &&
                preparedStructureOccurrences.any { occurrence ->
                    occurrence.entryIndex == selectedLogicalIndex
                }
        )
    }
    LaunchedEffect(structurePreviewPlayRequest) {
        if (structurePreviewPlayRequest > 0 && constrainToAvailableHeight) {
            selectedStructureEditIndex?.let { logicalIndex ->
                startStructurePreviewAtLogicalIndex(logicalIndex)
            }
        }
    }
    LaunchedEffect(structurePreviewUserStopRequest) {
        if (structurePreviewUserStopRequest > 0) {
            stopStructurePreviewPlayback(reason = "user_transport_stop")
            if (arrangementLoopPreviewActive) {
                loopEnabled = false
                preparedLoopStartMs = null
                stopArrangementLoopPreviewPlayback()
            }
        }
    }
    LaunchedEffect(mainPlaybackRequest) {
        if (mainPlaybackRequest > 0) {
            stopStructurePreviewPlayback(reason = "return_to_main_transport")
            if (arrangementLoopPreviewActive) {
                loopEnabled = false
                preparedLoopStartMs = null
                stopArrangementLoopPreviewPlayback()
            }
            selectedStructureEditIndex = null
            onStructurePreviewTargetChange(false)
        }
    }
    LaunchedEffect(structurePlaybackActive, wavPreviewActive, structurePreviewPlayer) {
        if (!structurePlaybackActive && !wavPreviewActive) {
            runCatching { structurePreviewPlayer.volume = arrangementTrackGainLinear }
            return@LaunchedEffect
        }
        var lastTimingDiagLogMs = 0L
        while (structurePlaybackActive || wavPreviewActive) {
            val currentMediaItemIndex = runCatching {
                structurePreviewPlayer.currentMediaItemIndex
            }.getOrDefault(0).coerceAtLeast(0)
            val isSecondaryPlaying = runCatching { structurePreviewPlayer.isPlaying }.getOrDefault(false)
            val itemDurationMs = runCatching { structurePreviewPlayer.duration }.getOrDefault(0L)
            val samplerPositionMs = if (structurePlaybackActive && structureUsingSampler) {
                (SystemClock.elapsedRealtime() - structureSamplerSegmentStartRealtimeMs)
                    .coerceAtLeast(0L)
            } else {
                null
            }
            val itemPositionMs = samplerPositionMs
                ?: runCatching { structurePreviewPlayer.currentPosition }.getOrDefault(0L)
            if (structurePlaybackActive) {
                structurePlaybackSegments.getOrNull(structurePlaybackIndex)?.let { segment ->
                    val segmentStartMs = minOf(segment.startMs, segment.endMs).coerceAtLeast(0L)
                    val segmentEndMs = maxOf(segment.startMs, segment.endMs).coerceAtLeast(segmentStartMs + 1L)
                    val safeItemPositionMs = itemPositionMs.coerceIn(
                        0L,
                        (segmentEndMs - segmentStartMs - 1L).coerceAtLeast(0L)
                    )
                    structurePlaybackAbsolutePositionMs = segmentStartMs + safeItemPositionMs
                }
            }
            if (wavPreviewActive) {
                wavPreviewPositionMs = itemPositionMs.coerceAtLeast(0L)
                wavPreviewDurationMs = itemDurationMs.coerceAtLeast(0L)
            }
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastTimingDiagLogMs >= 500L) {
                lastTimingDiagLogMs = nowMs
                val source = when {
                    wavPreviewActive -> "WAV_PREVIEW"
                    structureUsingSampler -> "SAMPLER"
                    structureUsingWavSource -> "WAV_CACHE"
                    else -> arrangementTimingSourceLabel(currentStructureSourcePath ?: currentSongAudioPath)
                }
                val playerAbsolutePositionMs = if (structurePlaybackActive) {
                    structurePlaybackSegments.getOrNull(structurePlaybackIndex)?.let { segment ->
                        minOf(segment.startMs, segment.endMs).coerceAtLeast(0L) +
                            itemPositionMs.coerceAtLeast(0L)
                    } ?: itemPositionMs.coerceAtLeast(0L)
                } else {
                    itemPositionMs.coerceAtLeast(0L)
                }
                val visualPlayheadMs = if (structurePlaybackActive) {
                    resolvePreparedArrangementPlayheadFromSource(
                        occurrences = preparedStructureOccurrences,
                        playbackIndex = structurePlaybackIndex,
                        sourcePositionMs = structurePlaybackAbsolutePositionMs
                    )?.arrangementPositionMs ?: structurePlaybackAbsolutePositionMs
                } else {
                    wavPreviewPositionMs
                }
                val playerArrangementPositionMs = if (structurePlaybackActive) {
                    resolvePreparedArrangementPlayheadFromSource(
                        occurrences = preparedStructureOccurrences,
                        playbackIndex = structurePlaybackIndex,
                        sourcePositionMs = playerAbsolutePositionMs
                    )?.arrangementPositionMs ?: playerAbsolutePositionMs
                } else {
                    playerAbsolutePositionMs
                }
                Log.d(
                    ARR_TIMING_DIAG_TAG,
                    "PLAYHEAD visualPlayheadMs=$visualPlayheadMs " +
                        "playerCurrentPositionMs=$playerArrangementPositionMs " +
                        "rawPlayerPositionMs=$itemPositionMs diffMs=${visualPlayheadMs - playerArrangementPositionMs} " +
                        "source=$source isLoopActive=$arrangementLoopPreviewActive " +
                        "isStructureActive=$structurePlaybackActive"
                )
            }
            val nextVolume = if (!isSecondaryPlaying || itemDurationMs <= 0L) {
                arrangementTrackGainLinear
            } else {
                val safePositionMs = itemPositionMs.coerceAtLeast(0L)
                val safeDurationMs = itemDurationMs.coerceAtLeast(1L)
                val remainingMs = (safeDurationMs - safePositionMs).coerceAtLeast(0L)
                val fadeInGain = (safePositionMs.toFloat() / structurePreviewFadeDurationMs.toFloat())
                    .coerceIn(0f, 1f)
                val fadeOutGain = (remainingMs.toFloat() / structurePreviewFadeDurationMs.toFloat())
                    .coerceIn(0f, 1f)
                arrangementTrackGainLinear * minOf(fadeInGain, fadeOutGain)
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

    LaunchedEffect(arrangementLoopPreviewActive, arrangementPreviewPlayer) {
        if (!arrangementLoopPreviewActive) return@LaunchedEffect
        var lastTimingDiagLogMs = 0L
        var lastPlayerPositionMs = 0L
        while (arrangementLoopPreviewActive) {
            val playerPositionMs = arrangementPreviewPlayer.currentPositionMs()
            val loopStartMs = activeLoopRange?.first ?: preparedLoopStartMs ?: 0L
            val loopEndMs = activeLoopRange?.second ?: loopStartMs
            if (playerPositionMs + 30L < lastPlayerPositionMs) {
                val visualAfterMs = loopStartMs + playerPositionMs
                val playerAfterMs = loopStartMs + playerPositionMs
                Log.d(
                    ARR_TIMING_DIAG_TAG,
                    "LOOP_RETURN loopStartMs=$loopStartMs loopEndMs=$loopEndMs " +
                        "playerPositionBefore=$lastPlayerPositionMs playerPositionAfter=$playerPositionMs " +
                        "diffMs=${visualAfterMs - playerAfterMs}"
                )
            }
            lastPlayerPositionMs = playerPositionMs
            arrangementLoopPositionMs = playerPositionMs
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastTimingDiagLogMs >= 500L) {
                lastTimingDiagLogMs = nowMs
                val visualPlayheadMs = loopStartMs + arrangementLoopPositionMs.coerceAtLeast(0L)
                val playerAbsoluteMs = loopStartMs + playerPositionMs.coerceAtLeast(0L)
                Log.d(
                    ARR_TIMING_DIAG_TAG,
                    "PLAYHEAD visualPlayheadMs=$visualPlayheadMs " +
                        "playerCurrentPositionMs=$playerAbsoluteMs " +
                        "rawPlayerPositionMs=$playerPositionMs diffMs=${visualPlayheadMs - playerAbsoluteMs} " +
                        "source=${arrangementTimingSourceLabel(currentSongAudioPath)} " +
                        "isLoopActive=$arrangementLoopPreviewActive isStructureActive=$structurePlaybackActive"
                )
            }
            kotlinx.coroutines.delay(12L)
        }
    }

    LaunchedEffect(loopReady, hasSegmentLoop, hasSelectedSegmentLoop) {
        if (structurePlaybackActive) return@LaunchedEffect
        if (!loopReady && !hasSegmentLoop && !hasSelectedSegmentLoop) {
            loopEnabled = false
            stopArrangementLoopPreviewPlayback()
            if (isPreparedClipLoopTestActive) {
                onStopPreparedClipLoopTest()
            }
        }
    }

    LaunchedEffect(
        loopEnabled,
        segmentInMs,
        segmentOutMs,
        selectedSegmentLoopStartMs,
        selectedSegmentLoopEndMs
    ) {
        if (structurePlaybackActive) return@LaunchedEffect
        if (!loopEnabled) {
            stopArrangementLoopPreviewPlayback()
            if (isPreparedClipLoopTestActive) {
                onStopPreparedClipLoopTest()
            }
            preparedLoopStartMs = null
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
            val audioPath = currentSongAudioPath
            if (audioPath.isNullOrBlank()) {
                loopEnabled = false
                preparedLoopStartMs = null
                stopArrangementLoopPreviewPlayback()
                return@LaunchedEffect
            }
            stopStructurePreviewPlayback(reason = "arrangement_loop_started")
            if (isPreparedClipLoopTestActive) {
                onStopPreparedClipLoopTest()
            }
            onIsPlayingChange(false)
            preparedLoopStartMs = loopStartMs
            arrangementLoopPositionMs = 0L
            arrangementPreviewPlayer.setTrackGainDb(currentSongTrackGainDb.toFloat())
            arrangementPreviewPlayer.playLoop(
                sourceUri = Uri.fromFile(File(audioPath)),
                startMs = loopStartMs,
                endMs = loopEndMs
            )
            Log.d(
                ARR_TIMING_DIAG_TAG,
                "PLAY_START playMode=LOOP requestedStartMs=$loopStartMs requestedEndMs=$loopEndMs " +
                    "actualPlayerPositionMs=${arrangementPreviewPlayer.currentPositionMs()} " +
                    "source=${arrangementTimingSourceLabel(audioPath)} sourceUri=${Uri.fromFile(File(audioPath))}"
            )
            arrangementLoopPreviewActive = true
            publishSecondaryPlaybackState()
            return@LaunchedEffect
        }
        loopEnabled = false
        preparedLoopStartMs = null
        stopArrangementLoopPreviewPlayback()
    }

    if (showSamplerTestScreen) {
        BackHandler {
            showSamplerTestScreen = false
        }
        ArrangementSamplerTestScreen(
            songId = currentSongId,
            onClose = { showSamplerTestScreen = false },
            modifier = if (constrainToAvailableHeight) {
                modifier.fillMaxSize()
            } else {
                Modifier.fillMaxSize()
            }
        )
    } else {
    Column(
        modifier = if (constrainToAvailableHeight) {
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp)
        } else {
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
	                Row(
	                    modifier = if (constrainToAvailableHeight) Modifier else Modifier.fillMaxWidth(),
	                    horizontalArrangement = Arrangement.spacedBy(8.dp),
	                    verticalAlignment = Alignment.CenterVertically
	                ) {
                        IconButton(
                            onClick = { undoArrangementChange() },
                            enabled = arrangementUndoStack.isNotEmpty(),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.arrangement_undo_action),
                                tint = if (arrangementUndoStack.isNotEmpty()) {
                                    Color.White
                                } else {
                                    Color(0xFF607D8B)
                                }
                            )
                        }
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
                            if (nextLoopEnabled && (hasSegmentLoop || hasSelectedSegmentLoop)) {
                                revealSyncPointRequest += 1
                            }
                        },
                        enabled = hasSegmentLoop || hasSelectedSegmentLoop,
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
                            color = if (hasSegmentLoop || hasSelectedSegmentLoop) {
                                if (isLoopHighlighted) Color(0xFF2ECC71) else Color(0xFFB0BEC5)
                            } else {
                                Color(0xFF607D8B)
                            },
                            fontSize = 18.sp
                        )
                    }
	                    if (!constrainToAvailableHeight) {
	                        Spacer(modifier = Modifier.weight(1f))
	                    }
	                }
        Row(
            modifier = if (constrainToAvailableHeight) Modifier else Modifier.fillMaxWidth(),
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
            if (gridEnabled) {
                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .background(
                            color = Color.White.copy(alpha = 0.04f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable {
                            val anchorMs = displayedCurrentPositionMs.coerceAtLeast(0L)
                            localMeasureAnchorMs = anchorMs
                            onMeasureAnchorHere(anchorMs)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.arrangement_grid_sync_action),
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
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
                    Log.d(
                        ARR_TIMING_DIAG_TAG,
                        "ACTION action=SET_IN x=BUTTON calculatedTimeMs=$nextInMs clampedTimeMs=$nextInMs " +
                            "waveformZoom=NA waveformCenterFraction=NA startFraction=NA endFraction=NA " +
                            "safeDurationMs=$waveformDurationMs"
                    )
                    suppressNextLoopAutoplay = !isPlaying
                    lastWaveformFocusMarker = TimelineWaveformFocusMarker.IN
                    if (selectedArrangementEditSegment != null) {
                        updateSelectedArrangementSegmentBounds(
                            nextStartMs = nextInMs,
                            nextEndMs = selectedSegmentLoopEndMs ?: segmentOutMs ?: nextInMs + 1L,
                            commit = true,
                            handle = TimelineArrangementEditHandle.IN
                        )
                    } else {
                        segmentInMs = nextInMs
                        onSegmentInChange(nextInMs)
                    }
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
                    Log.d(
                        ARR_TIMING_DIAG_TAG,
                        "ACTION action=SET_OUT x=BUTTON calculatedTimeMs=$nextOutMs clampedTimeMs=$nextOutMs " +
                            "waveformZoom=NA waveformCenterFraction=NA startFraction=NA endFraction=NA " +
                            "safeDurationMs=$waveformDurationMs"
                    )
                    suppressNextLoopAutoplay = !isPlaying
                    lastWaveformFocusMarker = TimelineWaveformFocusMarker.OUT
                    if (selectedArrangementEditSegment != null) {
                        updateSelectedArrangementSegmentBounds(
                            nextStartMs = selectedSegmentLoopStartMs ?: segmentInMs ?: 0L,
                            nextEndMs = nextOutMs,
                            commit = true,
                            handle = TimelineArrangementEditHandle.OUT
                        )
                    } else {
                        segmentOutMs = nextOutMs
                        onSegmentOutChange(nextOutMs)
                    }
                }
            )
            Text(
                text = stringResource(R.string.timeline_measures_signature_default),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        }
        TimelineGridWaveformSection(
            peaks = waveformPeaks,
            durationMs = waveformDurationMs,
            currentPositionMs = displayedCurrentPositionMs,
            isPlaying = isPlaying || arrangementLoopPreviewActive,
            isLoopViewLocked = loopEnabled || arrangementLoopPreviewActive || isPreparedClipLoopTestActive,
            tempoBpm = tempoBpm,
            measureAnchorMs = savedAnchorMs,
            segmentInMs = segmentInMs,
            segmentOutMs = segmentOutMs,
            undoStackSize = arrangementUndoStack.size,
            editableSegmentId = selectedArrangementEditSegment?.id,
            editableSegmentStartMs = selectedArrangementEditSegment?.startMs,
            editableSegmentEndMs = selectedArrangementEditSegment?.endMs,
            editableSegmentFocusRequest = structureEditFocusRequest,
            focusMarker = lastWaveformFocusMarker,
            isRemoveMode = segmentSelectionMode == TimelineSegmentSelectionMode.REMOVE,
            isWaveformExpanded = isWaveformExpanded,
            isLoading = waveformLoading,
            hasError = waveformError,
            revealAnchorRequest = revealSyncPointRequest,
            onToggleExpanded = { isWaveformExpanded = !isWaveformExpanded },
            onSeekRequested = { requestedPositionMs ->
                val shouldReturnToMainPlayback = constrainToAvailableHeight &&
                    (selectedStructureEditIndex != null ||
                        selectedSegmentLoopId != null ||
                        isTimelineSecondaryPlaybackActive(
                            structurePlaybackActive = structurePlaybackActive,
                            wavPreviewActive = wavPreviewActive,
                            arrangementLoopPreviewActive = arrangementLoopPreviewActive
                        ))
                Log.d(
                    ARR_TIMING_DIAG_TAG,
                    "ACTION action=SEEK x=NA calculatedTimeMs=$requestedPositionMs " +
                        "clampedTimeMs=${requestedPositionMs.coerceAtLeast(0L)} " +
                        "waveformZoom=NA waveformCenterFraction=NA startFraction=NA endFraction=NA " +
                        "safeDurationMs=$waveformDurationMs isLoopActive=$arrangementLoopPreviewActive " +
                        "isStructureActive=$structurePlaybackActive"
                )
                if (shouldReturnToMainPlayback) {
                    onIsPlayingChange(false)
                    stopStructurePreviewPlayback(reason = "waveform_seek_to_main")
                    loopEnabled = false
                    preparedLoopStartMs = null
                    stopArrangementLoopPreviewPlayback()
                    selectedStructureEditIndex = null
                    selectedSegmentLoopId = null
                    selectedSegmentLoopStartMs = null
                    selectedSegmentLoopEndMs = null
                    onStructurePreviewTargetChange(false)
                    seekToMs(requestedPositionMs.coerceAtLeast(0L))
                } else if (structurePlaybackActive) {
                    seekStructurePreviewToAbsolutePosition(requestedPositionMs)
                } else if (arrangementLoopPreviewActive) {
                    val activeLoop = activeLoopRange
                    if (activeLoop != null) {
                        val (loopStartMs, loopEndMs) = activeLoop
                        val relativeSeekMs = (requestedPositionMs - loopStartMs)
                            .coerceIn(0L, (loopEndMs - loopStartMs - 1L).coerceAtLeast(0L))
                        arrangementLoopPositionMs = relativeSeekMs
                        arrangementPreviewPlayer.seekTo(relativeSeekMs)
                    }
                } else {
                    val activeLoop = activeLoopRange
                    if (isPreparedClipLoopTestActive && activeLoop != null && onSeekPreparedClipLoopToPosition != null) {
                        onSeekPreparedClipLoopToPosition(requestedPositionMs)
                    } else if (isPreparedClipLoopTestActive && activeLoop != null) {
                        val (loopStartMs, loopEndMs) = activeLoop
                        val relativeSeekMs = (requestedPositionMs - loopStartMs)
                            .coerceIn(0L, (loopEndMs - loopStartMs - 1L).coerceAtLeast(0L))
                        seekToMs(relativeSeekMs)
                    } else {
                        seekToMs(requestedPositionMs)
                    }
                }
            },
            onWaveformPanStarted = {
                lastWaveformFocusMarker = TimelineWaveformFocusMarker.NONE
            },
            onWaveformLongPress = { selectedPositionMs ->
                val waveformPositionMs = selectedPositionMs.coerceAtLeast(0L)
                val currentInMs = segmentInMs
                val currentOutMs = segmentOutMs
                when {
                    currentInMs != null && currentOutMs != null -> {
                        val distanceToIn = abs(waveformPositionMs - currentInMs)
                        val distanceToOut = abs(waveformPositionMs - currentOutMs)
                        if (distanceToIn <= distanceToOut) {
                            lastWaveformFocusMarker = TimelineWaveformFocusMarker.IN
                            if (selectedArrangementEditSegment != null) {
                                updateSelectedArrangementSegmentBounds(
                                    nextStartMs = waveformPositionMs,
                                    nextEndMs = currentOutMs,
                                    commit = true,
                                    handle = TimelineArrangementEditHandle.IN
                                )
                            } else {
                                segmentInMs = waveformPositionMs
                                onSegmentInChange(waveformPositionMs)
                            }
                        } else {
                            lastWaveformFocusMarker = TimelineWaveformFocusMarker.OUT
                            if (selectedArrangementEditSegment != null) {
                                updateSelectedArrangementSegmentBounds(
                                    nextStartMs = currentInMs,
                                    nextEndMs = waveformPositionMs,
                                    commit = true,
                                    handle = TimelineArrangementEditHandle.OUT
                                )
                            } else {
                                segmentOutMs = waveformPositionMs
                                onSegmentOutChange(waveformPositionMs)
                            }
                        }
                    }
                    currentInMs != null -> {
                        lastWaveformFocusMarker = TimelineWaveformFocusMarker.IN
                        if (selectedArrangementEditSegment != null) {
                            updateSelectedArrangementSegmentBounds(
                                nextStartMs = waveformPositionMs,
                                nextEndMs = selectedSegmentLoopEndMs ?: waveformPositionMs + 1L,
                                commit = true,
                                handle = TimelineArrangementEditHandle.IN
                            )
                        } else {
                            segmentInMs = waveformPositionMs
                            onSegmentInChange(waveformPositionMs)
                        }
                    }
                    currentOutMs != null -> {
                        lastWaveformFocusMarker = TimelineWaveformFocusMarker.OUT
                        if (selectedArrangementEditSegment != null) {
                            updateSelectedArrangementSegmentBounds(
                                nextStartMs = selectedSegmentLoopStartMs ?: 0L,
                                nextEndMs = waveformPositionMs,
                                commit = true,
                                handle = TimelineArrangementEditHandle.OUT
                            )
                        } else {
                            segmentOutMs = waveformPositionMs
                            onSegmentOutChange(waveformPositionMs)
                        }
                    }
                    else -> {
                        segmentInMs = waveformPositionMs
                        lastWaveformFocusMarker = TimelineWaveformFocusMarker.IN
                        onSegmentInChange(waveformPositionMs)
                    }
                }
            },
            onEditableSegmentBoundsChange = { nextStartMs, nextEndMs, commit, handle ->
                updateSelectedArrangementSegmentBounds(nextStartMs, nextEndMs, commit, handle)
            },
            onEditableSegmentHandleCaptured = {
                captureSelectedArrangementSegmentUndoSnapshot()
            },
            onLoopBoundsChange = { nextStartMs, nextEndMs, commit, handle ->
                val boundedStartMs = nextStartMs.coerceAtLeast(0L)
                val boundedEndMs = nextEndMs.coerceAtLeast(boundedStartMs + 1L)
                segmentInMs = boundedStartMs
                segmentOutMs = boundedEndMs
                if (commit) {
                    when (handle) {
                        TimelineArrangementEditHandle.IN -> onSegmentInChange(boundedStartMs)
                        TimelineArrangementEditHandle.OUT -> onSegmentOutChange(boundedEndMs)
                        TimelineArrangementEditHandle.NONE -> Unit
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
                color = if (!constrainToAvailableHeight || arrangementStructuralActionsEnabled) {
                    Color.White
                } else {
                    Color(0xFF546E7A)
                },
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(
                        enabled = !constrainToAvailableHeight || arrangementStructuralActionsEnabled
                    ) {
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
                            pushArrangementUndoSnapshot()

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
                        val nextSegments = if (constrainToAvailableHeight) {
                            createdSegments + arrangementSegments
                        } else {
                            arrangementSegments + createdSegments
                        }
                        val nextStructureSegmentIds = if (constrainToAvailableHeight) {
                            createdSegments.map { segment -> segment.id } + structureSegmentIds
                        } else {
                            structureSegmentIds
                        }
                        val nextEntries = if (constrainToAvailableHeight) {
                            reconcileArrangementEntries(
                                segments = nextSegments,
                                structureSegmentIds = nextStructureSegmentIds,
                                existingEntries = arrangementEntries
                            )
                        } else {
                            arrangementEntries
                        }
                        arrangementSegments = nextSegments
                        structureSegmentIds = nextStructureSegmentIds
                        arrangementEntries = nextEntries
                        nextSegmentIndex = nextIndex
                        persistArrangementState(
                            nextSegments = nextSegments,
                            nextStructureSegmentIds = nextStructureSegmentIds,
                            nextEntries = nextEntries
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
            Text(
                text = stringResource(R.string.timeline_tempo_action_listen),
                color = if (isPreviewGenerating) Color(0xFF607D8B) else Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(enabled = !isPreviewGenerating, onClick = listenAction)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
            Text(
                text = stringResource(R.string.timeline_tempo_action_export),
                color = if (isFinalExporting) Color(0xFF607D8B) else Color(0xFF80CBC4),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(enabled = !isFinalExporting) {
                        if (isLite) {
                            showArrangementExportProDialog = true
                            return@clickable
                        }
                        showExportNameDialog = true
                        isExportNameLoading = true
                        exportNameDraft = TextFieldValue("")
                        scope.launch {
                            val suggestedName = withContext(Dispatchers.IO) {
                                val tracksRoot = resolveTempoPublicBackingTracksDir().apply { mkdirs() }
                                buildNextTempoExportName(
                                    tracksRoot = tracksRoot,
                                    sourceTitle = currentSongTitle
                                )
                            }
                            exportNameDraft = TextFieldValue(suggestedName)
                            isExportNameLoading = false
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
	        if (constrainToAvailableHeight) {
	            Text(
	                text = stringResource(R.string.arrangement_sampler_test_open_action),
	                color = Color(0xFFB0BEC5),
	                fontSize = 14.sp,
	                modifier = Modifier
	                    .clickable {
	                        stopStructurePreviewPlayback(reason = "open_sampler_test")
	                        stopArrangementLoopPreviewPlayback()
	                        if (isPreparedClipLoopTestActive) {
	                            onStopPreparedClipLoopTest()
	                        }
	                        if (isPlaying) {
	                            onIsPlayingChange(false)
	                        }
	                        showSamplerTestScreen = true
	                    }
	                    .padding(horizontal = 10.dp, vertical = 8.dp)
	            )
	        }
        }
	    if (!constrainToAvailableHeight) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = stringResource(R.string.arrangement_sampler_test_open_action),
                color = Color(0xFFB0BEC5),
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable {
                        stopStructurePreviewPlayback(reason = "open_sampler_test")
                        stopArrangementLoopPreviewPlayback()
                        if (isPreparedClipLoopTestActive) {
                            onStopPreparedClipLoopTest()
                        }
                        if (isPlaying) {
                            onIsPlayingChange(false)
                        }
                        showSamplerTestScreen = true
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
	    }
        if (isPreviewGenerating) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFB0BEC5)
                )
                Text(
                    text = stringResource(R.string.timeline_tempo_preview_generating),
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp
                )
            }
        }
        if (isStructureAudioPreparing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFB0BEC5)
                )
                Text(
                    text = stringResource(R.string.arrangement_structure_audio_preparing),
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp
                )
            }
        }
        if (wavPreviewActive) {
            var wavPreviewSliderPositionMs by remember(wavPreviewPositionMs, wavPreviewDurationMs) {
                mutableLongStateOf(wavPreviewPositionMs.coerceIn(0L, wavPreviewDurationMs.coerceAtLeast(0L)))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.timeline_tempo_preview_wave_title),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        R.string.timeline_tempo_preview_wave_position,
                        formatTimelineMarkerTime(wavPreviewPositionMs),
                        formatTimelineMarkerTime(wavPreviewDurationMs)
                    ),
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp
                )
                Slider(
                    value = wavPreviewSliderPositionMs.toFloat(),
                    onValueChange = { nextValue ->
                        wavPreviewSliderPositionMs = nextValue.toLong()
                    },
                    onValueChangeFinished = {
                        val targetPositionMs = wavPreviewSliderPositionMs
                            .coerceIn(0L, wavPreviewDurationMs.coerceAtLeast(0L))
                        wavPreviewPositionMs = targetPositionMs
                        runCatching { structurePreviewPlayer.seekTo(targetPositionMs) }
                    },
                    valueRange = 0f..wavPreviewDurationMs.coerceAtLeast(1L).toFloat()
                )
            }
        }
        if (isFinalExporting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFB0BEC5)
                )
                Text(
                    text = stringResource(R.string.timeline_tempo_export_generating),
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!constrainToAvailableHeight) {
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
                        val structureIndex = structurePlaybackSegments.indexOfFirst { it.id == segment.id }
                            .takeIf { it >= 0 }
                        selectArrangementSegmentForEdit(
                            segment = segment,
                            structureIndex = structureIndex,
                            source = "SEGMENTS_COLUMN"
                        )
                        stopStructurePreviewPlayback(reason = "segment_loop_selected")
                        stopArrangementLoopPreviewPlayback()
                        preparedLoopStartMs = loopStartMs
                        loopEnabled = true
                        revealSyncPointRequest += 1
                    },
	                    onItemAdd = { segmentId ->
	                        if (arrangementSegments.any { it.id == segmentId }) {
                                pushArrangementUndoSnapshot()
	                            val nextStructureSegmentIds = structureSegmentIds + segmentId
	                            structureSegmentIds = nextStructureSegmentIds
                            persistArrangementState(nextStructureSegmentIds = nextStructureSegmentIds)
                        }
                    },
                    onItemDelete = { segmentId ->
                        removeArrangementSegment(segmentId)
                    },
                    onItemLongClick = { segmentId ->
                        segmentOptionsTargetId = segmentId
                    }
                )
            }

            ArrangementListCard(
                modifier = if (constrainToAvailableHeight) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.weight(1f)
                },
                title = stringResource(R.string.arrangement_structure_title),
                emptyLabel = stringResource(R.string.arrangement_structure_empty),
                items = structureSegmentIds.mapIndexedNotNull { index, segmentId ->
                    val segment = arrangementSegments.firstOrNull { it.id == segmentId } ?: return@mapIndexedNotNull null
                    val entry = arrangementEntries.firstOrNull { it.entryId == segmentId }
                    ArrangementListItem(
                        id = index.toString(),
                        title = "${index + 1}. ${segment.name}",
                        durationMs = (segment.endMs - segment.startMs).coerceAtLeast(0L),
                        repeatCount = entry?.repeatCount ?: 1,
                        isMuted = entry?.muted ?: false,
                        color = entry?.color,
                        isActive = index == selectedStructureEditIndex ||
                            (structurePlaybackActive && index == activeStructureEntryIndex),
                        isQueued = structurePlaybackActive && index == queuedStructureEntryIndex
                    )
                },
                onItemClick = { structureIndexId ->
                    val logicalIndex = structureIndexId.toIntOrNull() ?: return@ArrangementListCard
                    val selectedSegmentId = structureSegmentIds.getOrNull(logicalIndex)
                        ?: return@ArrangementListCard
                    val selectedSegment = arrangementSegments.firstOrNull { segment ->
                        segment.id == selectedSegmentId
                    } ?: return@ArrangementListCard
                    selectArrangementSegmentForEdit(
                        segment = selectedSegment,
                        structureIndex = logicalIndex,
                        source = "STRUCTURE_COLUMN"
                    )
                    val startIndex = preparedStructureOccurrences.indexOfFirst { occurrence ->
                        occurrence.entryIndex == logicalIndex
                    }
                    if (startIndex < 0) return@ArrangementListCard
                    val sourceAudioPath = currentSongAudioPath ?: return@ArrangementListCard
                    val isStructureSegmentPlaying = structurePlaybackActive &&
                        (structureUsingSampler || runCatching { structurePreviewPlayer.isPlaying }.getOrDefault(false))
                    Log.d(
                        ARR_STRUCTURE_QUEUE_TAG,
                        "CLICK clickedIndex=$startIndex currentPlayingIndex=$structurePlaybackIndex isStructureSegmentPlaying=$isStructureSegmentPlaying queuedBefore=$queuedStructureSegmentIndex action=${if (isStructureSegmentPlaying) "QUEUE_NEXT" else "START_NOW"}"
                    )
                    if (isStructureSegmentPlaying) {
                        val activeSourcePath = currentStructureSourcePath?.takeIf { it.isNotBlank() }
                            ?: sourceAudioPath
                        queueStructureSegmentPreview(startIndex, activeSourcePath, structurePlaybackSegments)
                    } else {
                        startStructurePreviewAtLogicalIndex(logicalIndex)
                    }
                },
                onItemAdd = null,
                onItemDelete = { structureIndexId ->
                    val removeIndex = structureIndexId.toIntOrNull() ?: return@ArrangementListCard
	                    if (constrainToAvailableHeight && !arrangementStructuralActionsEnabled) {
	                        return@ArrangementListCard
	                    }
	                    if (removeIndex in structureSegmentIds.indices) {
                            pushArrangementUndoSnapshot()
	                        val removedSegmentId = structureSegmentIds[removeIndex]
	                        val nextStructureSegmentIds = structureSegmentIds.toMutableList().apply {
                            removeAt(removeIndex)
                        }
                        val nextSegments = if (
                            constrainToAvailableHeight &&
                            removedSegmentId !in nextStructureSegmentIds
                        ) {
                            arrangementSegments.filterNot { segment -> segment.id == removedSegmentId }
                        } else {
                            arrangementSegments
                        }
                        val nextEntries = if (constrainToAvailableHeight) {
                            reconcileArrangementEntries(
                                segments = nextSegments,
                                structureSegmentIds = nextStructureSegmentIds,
                                existingEntries = arrangementEntries
                            )
                        } else {
                            arrangementEntries
                        }
                        if (
                            constrainToAvailableHeight &&
                            removedSegmentId !in nextStructureSegmentIds &&
                            selectedSegmentLoopId == removedSegmentId
                        ) {
                            selectedSegmentLoopId = null
                            selectedSegmentLoopStartMs = null
                            selectedSegmentLoopEndMs = null
                            preparedLoopStartMs = null
                            loopEnabled = false
                            stopArrangementLoopPreviewPlayback()
                        }
                        if (structurePlaybackActive) {
                            when {
                                removeIndex < structurePlaybackIndex -> {
                                    structurePlaybackIndex -= 1
                                }
                                removeIndex == structurePlaybackIndex -> {
                                    stopStructurePreviewPlayback(reason = "structure_item_deleted_current")
                                }
                            }
                        }
                        selectedStructureEditIndex = selectedStructureEditIndex?.let { selectedIndex ->
                            when {
                                selectedIndex == removeIndex -> null
                                selectedIndex > removeIndex -> selectedIndex - 1
                                else -> selectedIndex
                            }
                        }
                        arrangementSegments = nextSegments
                        structureSegmentIds = nextStructureSegmentIds
                        arrangementEntries = nextEntries
                        nextSegmentIndex = resolveNextTimelineArrangementSegmentIndex(
                            nextSegments + preservedLegacyArrangementSegments
                        )
                        persistArrangementState(
                            nextSegments = nextSegments,
                            nextStructureSegmentIds = nextStructureSegmentIds,
                            nextEntries = nextEntries
                        )
                    }
                },
                onItemLongClick = if (constrainToAvailableHeight) {
                    { structureIndexId ->
                        val targetIndex = structureIndexId.toIntOrNull()
                            ?: return@ArrangementListCard
                        segmentOptionsTargetId = structureSegmentIds.getOrNull(targetIndex)
                    }
                } else {
                    null
                },
                horizontalTrack = constrainToAvailableHeight,
                onItemMove = if (constrainToAvailableHeight) {
                    { structureIndexId, direction ->
                        structureIndexId.toIntOrNull()?.let { sourceIndex ->
                            moveArrangementOccurrence(sourceIndex, direction)
                        }
                    }
                } else {
                    null
                },
                itemActionsEnabled = !constrainToAvailableHeight || arrangementStructuralActionsEnabled,
                playhead = arrangementTrackPlayhead
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
    }

    if (renameSegmentId != null) {
        val targetSegment = arrangementSegments.firstOrNull { it.id == renameSegmentId }
        var localRenameDraft by remember(renameSegmentId, targetSegment?.name) {
            mutableStateOf(TextFieldValue(targetSegment?.name.orEmpty()))
        }
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
                    value = localRenameDraft,
                    onValueChange = { localRenameDraft = it },
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
	                        val nextName = targetSegment?.let { segment ->
	                            localRenameDraft.text.trim().ifBlank { segment.name }
	                        } ?: return@Button
                        if (constrainToAvailableHeight) {
                            updateArrangementOccurrence(targetId) { entry ->
                                entry.copy(name = nextName)
                            }
                        } else {
                            pushArrangementUndoSnapshot()
	                            val nextSegments = arrangementSegments.map { segment ->
                                if (segment.id == targetId) {
                                    segment.copy(name = nextName)
                                } else {
                                    segment
                                }
                            }
                            arrangementSegments = nextSegments
                            persistArrangementState(nextSegments = nextSegments)
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

    if (showExportNameDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                if (!isExportNameLoading && !isFinalExporting) {
                    showExportNameDialog = false
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.timeline_tempo_export_name_title),
                    color = Color.White
                )
            },
            text = {
                OutlinedTextField(
                    value = exportNameDraft,
                    onValueChange = { exportNameDraft = it },
                    singleLine = true,
                    enabled = !isExportNameLoading && !isFinalExporting,
                    label = {
                        Text(text = stringResource(R.string.timeline_tempo_export_name_label))
                    }
                )
            },
            confirmButton = {
                Button(
                    enabled = !isExportNameLoading && !isFinalExporting && exportNameDraft.text.trim().isNotEmpty(),
                    onClick = {
                        val chosenName = exportNameDraft.text.trim()
                        val audioPath = currentSongAudioPath
                        val exportSegments = structurePlaybackSegments.map { segment ->
                            minOf(segment.startMs, segment.endMs) to
                                maxOf(segment.startMs, segment.endMs).coerceAtLeast(
                                    minOf(segment.startMs, segment.endMs) + 1L
                                )
                        }
                        if (audioPath.isNullOrBlank()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.timeline_tempo_export_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        if (exportSegments.isEmpty()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.arrangement_preview_structure_empty),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        arrangementName = chosenName
                        val songId = currentSongId?.trim().orEmpty()
                        showExportNameDialog = false
                        isFinalExporting = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val backingTracksDir = resolveTempoPublicBackingTracksDir()
                                        .apply { mkdirs() }
                                    require(backingTracksDir.isDirectory) {
                                        "Tempo export backing tracks dir unavailable"
                                    }
                                    val targetFile = File(
                                        backingTracksDir,
                                        buildTempoExportWavFileName(chosenName)
                                    )
                                    require(!targetFile.exists()) {
                                        "Tempo export target already exists"
                                    }
                                    val renderedFile = ArrangementWavRenderer.render(
                                        context = context.applicationContext,
                                        audioPath = audioPath,
                                        segments = exportSegments
                                    )
                                    try {
                                        renderedFile.copyTo(targetFile, overwrite = false)
                                    } finally {
                                        runCatching { renderedFile.delete() }
                                    }
                                    targetFile
                                }
                            }

                            result
                                .onSuccess { targetFile ->
                                    if (songId.isNotEmpty()) {
                                        ArrangementStore.save(
                                            context = context.applicationContext,
                                            songId = songId,
                                            data = buildArrangementDataForPersistence(
                                                useOccurrenceModel = constrainToAvailableHeight,
                                                name = chosenName,
                                                sourceSongId = songId,
                                                segments = arrangementSegments,
                                                structureSegmentIds = structureSegmentIds,
                                                existingEntries = arrangementEntries,
                                                preservedLegacySegments = preservedLegacyArrangementSegments
                                            )
                                        )
                                    }
                                    MediaScannerConnection.scanFile(
                                        context.applicationContext,
                                        arrayOf(targetFile.absolutePath),
                                        arrayOf("audio/wav"),
                                        null
                                    )
                                    val generatedSmpUri = withContext(Dispatchers.IO) {
                                        smpConverter.convertSingleToLibrarySmp(
                                            Uri.fromFile(targetFile)
                                        ).getOrNull()
                                    }
                                    val importedSong = generatedSmpUri?.let { smpUri ->
                                        onImportGeneratedSmp(smpUri)
                                    }
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            if (importedSong != null) {
                                                R.string.timeline_tempo_export_import_success
                                            } else {
                                                R.string.timeline_tempo_export_import_partial
                                            },
                                            targetFile.name
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.timeline_tempo_export_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            isFinalExporting = false
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !isExportNameLoading && !isFinalExporting,
                    onClick = { showExportNameDialog = false }
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
            containerColor = Color(0xFF121212)
        )
    }

    if (showArrangementExportProDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showArrangementExportProDialog = false },
            title = {
                Text(
                    text = sExportProDialogTitle,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = sExportProDialogMessage,
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArrangementExportProDialog = false
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
                ) {
                    Text(sUpgradeToPro, color = Color(0xFF80CBC4))
                }
            },
            dismissButton = {
                TextButton(onClick = { showArrangementExportProDialog = false }) {
                    Text(stringResource(R.string.common_close), color = Color(0xFFB0BEC5))
                }
            },
            containerColor = Color(0xFF121212)
        )
    }

    if (segmentOptionsTargetId != null) {
        val targetId = segmentOptionsTargetId
        val targetSegment = arrangementSegments.firstOrNull { it.id == targetId }
        val targetEntry = arrangementEntries.firstOrNull { it.entryId == targetId }
            ?: targetSegment?.let { segment ->
                ArrangementEntryData(
                    entryId = segment.id,
                    name = segment.name,
                    startMs = segment.startMs,
                    endMs = segment.endMs
                )
            }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { segmentOptionsTargetId = null },
            title = {
                Text(
                    text = targetSegment?.name.orEmpty(),
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_rename),
                        color = Color.White,
                        modifier = Modifier.clickable {
                            val segment = arrangementSegments.firstOrNull { it.id == segmentOptionsTargetId }
                                ?: return@clickable
                            renameSegmentId = segment.id
                            segmentOptionsTargetId = null
                        }
                    )
                    if (constrainToAvailableHeight && targetEntry != null) {
                        Text(
                            text = stringResource(R.string.arrangement_occurrence_duplicate),
                            color = Color.White,
                            modifier = Modifier.clickable {
                                val targetIndex = structureSegmentIds.indexOf(targetEntry.entryId)
                                if (targetIndex >= 0) {
                                    insertArrangementOccurrenceAfter(targetIndex, targetEntry)
                                }
                                segmentOptionsTargetId = null
                            }
                        )
                        Text(
                            text = stringResource(R.string.library_bottom_copy),
                            color = Color.White,
                            modifier = Modifier.clickable {
                                copiedArrangementEntry = targetEntry
                                segmentOptionsTargetId = null
                            }
                        )
                        Text(
                            text = stringResource(R.string.arrangement_occurrence_paste_after),
                            color = if (copiedArrangementEntry != null) Color.White else Color(0xFF546E7A),
                            modifier = Modifier.clickable(enabled = copiedArrangementEntry != null) {
                                val targetIndex = structureSegmentIds.indexOf(targetEntry.entryId)
                                val copiedEntry = copiedArrangementEntry
                                if (targetIndex >= 0 && copiedEntry != null) {
                                    insertArrangementOccurrenceAfter(targetIndex, copiedEntry)
                                }
                                segmentOptionsTargetId = null
                            }
                        )
                        Text(
                            text = stringResource(
                                if (targetEntry.muted) {
                                    R.string.arrangement_occurrence_unmute
                                } else {
                                    R.string.arrangement_occurrence_mute
                                }
                            ),
                            color = Color.White,
                            modifier = Modifier.clickable {
                                updateArrangementOccurrence(targetEntry.entryId) { entry ->
                                    entry.copy(muted = !entry.muted)
                                }
                                segmentOptionsTargetId = null
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.arrangement_occurrence_repeat),
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                enabled = targetEntry.repeatCount > 1,
                                onClick = {
                                    updateArrangementOccurrence(targetEntry.entryId) { entry ->
                                        entry.copy(repeatCount = (entry.repeatCount - 1).coerceAtLeast(1))
                                    }
                                }
                            ) {
                                Text(
                                    text = "−",
                                    color = if (targetEntry.repeatCount > 1) Color.White else Color(0xFF546E7A)
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.arrangement_occurrence_repeat_value,
                                    targetEntry.repeatCount
                                ),
                                color = Color(0xFF80CBC4),
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
                                enabled = targetEntry.repeatCount < Int.MAX_VALUE,
                                onClick = {
                                    updateArrangementOccurrence(targetEntry.entryId) { entry ->
                                        entry.copy(repeatCount = entry.repeatCount + 1)
                                    }
                                }
                            ) {
                                Text(text = "+", color = Color.White)
                            }
                        }
                        Text(
                            text = stringResource(R.string.quickplaylists_menu_change_group_color),
                            color = Color.White,
                            modifier = Modifier.clickable {
                                colorArrangementEntryId = targetEntry.entryId
                                segmentOptionsTargetId = null
                            }
                        )
                    }
                    Text(
                        text = stringResource(R.string.library_delete_action),
                        color = Color(0xFFFF8A80),
                        modifier = Modifier.clickable {
                            val targetId = segmentOptionsTargetId ?: return@clickable
                            removeArrangementSegment(targetId)
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

    if (colorArrangementEntryId != null) {
        val targetId = colorArrangementEntryId
        val colorOptions = listOf(
            null to R.string.quickplaylists_group_color_default,
            "red" to R.string.quickplaylists_group_color_red,
            "blue" to R.string.quickplaylists_group_color_blue,
            "green" to R.string.quickplaylists_group_color_green,
            "violet" to R.string.quickplaylists_group_color_purple,
            "orange" to R.string.quickplaylists_group_color_orange,
            "yellow" to R.string.quickplaylists_group_color_yellow,
            "gray" to R.string.quickplaylists_group_color_gray
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { colorArrangementEntryId = null },
            title = {
                Text(
                    text = stringResource(R.string.quickplaylists_group_color_title),
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    colorOptions.forEach { (colorKey, labelRes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val entryId = targetId ?: return@clickable
                                    updateArrangementOccurrence(entryId) { entry ->
                                        entry.copy(color = colorKey)
                                    }
                                    colorArrangementEntryId = null
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(
                                        arrangementOccurrenceColorPreview(colorKey),
                                        RoundedCornerShape(5.dp)
                                    )
                            )
                            Text(text = stringResource(labelRes), color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { colorArrangementEntryId = null }) {
                    Text(text = stringResource(R.string.common_close))
                }
            },
            containerColor = Color(0xFF121212)
        )
    }
}

private fun arrangementOccurrenceColorPreview(color: String?): Color = when (color) {
    "red" -> Color(0xFFB94A48)
    "blue" -> Color(0xFF3D78B4)
    "green" -> Color(0xFF3F8A58)
    "violet" -> Color(0xFF8651A8)
    "orange", "amber" -> Color(0xFFC67732)
    "yellow" -> Color(0xFFB6A333)
    "gray" -> Color(0xFF607D8B)
    else -> Color(0xFF1C2933)
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

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
    undoStackSize: Int,
    editableSegmentId: String?,
    editableSegmentStartMs: Long?,
    editableSegmentEndMs: Long?,
    editableSegmentFocusRequest: Int,
    focusMarker: TimelineWaveformFocusMarker,
    isRemoveMode: Boolean,
    isWaveformExpanded: Boolean,
    isLoading: Boolean,
    hasError: Boolean,
    revealAnchorRequest: Int,
    onToggleExpanded: () -> Unit,
    onSeekRequested: (Long) -> Unit,
    onWaveformPanStarted: () -> Unit,
    onWaveformLongPress: (Long) -> Unit,
    onEditableSegmentBoundsChange: (Long, Long, Boolean, TimelineArrangementEditHandle) -> Unit,
    onEditableSegmentHandleCaptured: () -> Unit,
    onLoopBoundsChange: (Long, Long, Boolean, TimelineArrangementEditHandle) -> Unit
) {
    var waveformZoom by remember(peaks, durationMs) { mutableStateOf(1f) }
    var waveformCenterFraction by remember(peaks, durationMs) { mutableStateOf(0.5f) }
    var activeEditHandle by remember { mutableStateOf(TimelineArrangementEditHandle.NONE) }
    val latestEditableSegmentStartMs by rememberUpdatedState(editableSegmentStartMs)
    val latestEditableSegmentEndMs by rememberUpdatedState(editableSegmentEndMs)
    val latestEditableSegmentId by rememberUpdatedState(editableSegmentId)
    val latestUndoStackSize by rememberUpdatedState(undoStackSize)
    val latestOnEditableSegmentBoundsChange by rememberUpdatedState(onEditableSegmentBoundsChange)
    val latestOnEditableSegmentHandleCaptured by rememberUpdatedState(onEditableSegmentHandleCaptured)
    val latestOnLoopBoundsChange by rememberUpdatedState(onLoopBoundsChange)
    val waveformHeight by animateDpAsState(
        targetValue = if (isWaveformExpanded) 270.dp else 140.dp,
        label = "timelineWaveformHeight"
    )

    LaunchedEffect(editableSegmentFocusRequest) {
        val startMs = editableSegmentStartMs ?: return@LaunchedEffect
        val endMs = editableSegmentEndMs ?: return@LaunchedEffect
        val safeDuration = durationMs.coerceAtLeast(1)
        val visualPreRollMs = ARRANGEMENT_WAVEFORM_VISUAL_PREROLL_MS
        val visualDurationMs = safeDuration.toLong() + visualPreRollMs
        val segmentStartMs = minOf(startMs, endMs).coerceAtLeast(0L)
        val segmentEndMs = maxOf(startMs, endMs).coerceAtLeast(segmentStartMs + 1L)
        val segmentDurationFraction = (
            (segmentEndMs - segmentStartMs).toFloat() / visualDurationMs.toFloat().coerceAtLeast(1f)
            ).coerceIn(0.02f, 1f)
        val targetVisibleFraction = (segmentDurationFraction * 3.2f).coerceIn(0.08f, 1f)
        waveformZoom = (1f / targetVisibleFraction).coerceIn(1f, ARRANGEMENT_WAVEFORM_MAX_ZOOM)
        waveformCenterFraction = (
            (((segmentStartMs + segmentEndMs) / 2L).toFloat() + visualPreRollMs.toFloat()) /
                visualDurationMs.toFloat().coerceAtLeast(1f)
            ).coerceIn(0f, 1f)
    }

    LaunchedEffect(editableSegmentId, editableSegmentStartMs, editableSegmentEndMs) {
        Log.d(
            ARR_SEGMENT_STATE_TAG,
            "WAVEFORM_PARAMS selectedSegmentId=$editableSegmentId " +
                "selectedSegmentStartMs=$editableSegmentStartMs " +
                "selectedSegmentEndMs=$editableSegmentEndMs " +
                "isSegmentEditMode=${editableSegmentId != null && editableSegmentStartMs != null && editableSegmentEndMs != null}"
        )
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
                                        val safeDuration = durationMs.coerceAtLeast(1)
                                        val visualPreRollMs = ARRANGEMENT_WAVEFORM_VISUAL_PREROLL_MS
                                        val visualDurationMs = safeDuration.toLong() + visualPreRollMs
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
                                        val selectedVisualFraction = startFraction +
                                            localFraction * (effectiveEndFraction - startFraction)
                                        val calculatedTimeMs = (
                                            selectedVisualFraction * visualDurationMs.toFloat() -
                                                visualPreRollMs.toFloat()
                                            ).roundToLong()
                                        val clampedTimeMs = calculatedTimeMs
                                            .coerceIn(0L, safeDuration.toLong())
                                        Log.d(
                                            ARR_TIMING_DIAG_TAG,
                                            "ACTION action=TAP x=${offset.x} calculatedTimeMs=$calculatedTimeMs " +
                                                "clampedTimeMs=$clampedTimeMs waveformZoom=$waveformZoom " +
                                                "waveformCenterFraction=$waveformCenterFraction " +
                                                "startFraction=$startFraction endFraction=$effectiveEndFraction " +
                                                "safeDurationMs=$safeDuration"
                                        )
                                        onSeekRequested(clampedTimeMs)
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
                                        Log.d(
                                            ARR_SEGMENT_GESTURE_TAG,
                                            "TRANSFORM_GESTURE selectedSegmentId=$latestEditableSegmentId " +
                                                "isSegmentEditMode=${latestEditableSegmentStartMs != null && latestEditableSegmentEndMs != null} " +
                                                "loopStartMs=$segmentInMs loopEndMs=$segmentOutMs " +
                                                "selectedSegmentStartMs=$latestEditableSegmentStartMs " +
                                                "selectedSegmentEndMs=$latestEditableSegmentEndMs " +
                                                "panX=${pan.x} zoomChange=$zoomChange"
                                        )
	                                    val previousZoom = waveformZoom
	                                    val nextZoom = (previousZoom * zoomChange)
	                                        .coerceIn(1f, ARRANGEMENT_WAVEFORM_MAX_ZOOM)
                                    waveformZoom = nextZoom

                                    val visibleFraction = 1f / nextZoom
                                    val panFraction = if (size.width > 0) {
                                        -pan.x / size.width * visibleFraction
                                    } else {
                                        0f
                                    }
                                    val minCenter = visibleFraction / 2f
                                    val maxCenter = 1f - minCenter
                                    waveformCenterFraction = if (nextZoom <= 1f) {
                                        0.5f
                                    } else {
                                        (waveformCenterFraction + panFraction)
                                            .coerceIn(minCenter, maxCenter)
                                    }
                                }
                            }
                            .pointerInput(
                                peaks,
                                durationMs,
                                waveformZoom,
                                waveformCenterFraction,
                                editableSegmentFocusRequest
                            ) {
                                fun timeMsForX(x: Float): Long {
                                    val safeDuration = durationMs.coerceAtLeast(1)
                                    val visualPreRollMs = ARRANGEMENT_WAVEFORM_VISUAL_PREROLL_MS
                                    val visualDurationMs = safeDuration.toLong() + visualPreRollMs
                                    val visibleFraction = 1f / waveformZoom.coerceAtLeast(1f)
                                    val startFraction = (waveformCenterFraction - visibleFraction / 2f)
                                        .coerceIn(0f, 1f)
                                    val endFraction = (startFraction + visibleFraction).coerceIn(0f, 1f)
                                    val effectiveEndFraction = if (endFraction <= startFraction) 1f else endFraction
                                    val localFraction = if (size.width > 0) {
                                        (x / size.width).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    val selectedVisualFraction = startFraction +
                                        localFraction * (effectiveEndFraction - startFraction)
                                    return (
                                        selectedVisualFraction * visualDurationMs.toFloat() -
                                            visualPreRollMs.toFloat()
                                        ).roundToLong()
                                        .coerceIn(0L, safeDuration.toLong())
                                }

                                fun xForTimeMs(timeMs: Long): Float? {
                                    val safeDuration = durationMs.coerceAtLeast(1)
                                    val visualPreRollMs = ARRANGEMENT_WAVEFORM_VISUAL_PREROLL_MS
                                    val visualDurationMs = safeDuration.toLong() + visualPreRollMs
                                    val visualDurationFloat = visualDurationMs.toFloat().coerceAtLeast(1f)
                                    val visibleFraction = 1f / waveformZoom.coerceAtLeast(1f)
                                    val startFraction = (waveformCenterFraction - visibleFraction / 2f)
                                        .coerceIn(0f, 1f)
                                    val endFraction = (startFraction + visibleFraction).coerceIn(0f, 1f)
                                    val effectiveEndFraction = if (endFraction <= startFraction) 1f else endFraction
                                    val positionFraction = (
                                        timeMs.coerceIn(0L, safeDuration.toLong()).toFloat() +
                                            visualPreRollMs.toFloat()
                                        ) / visualDurationFloat
                                    if (positionFraction !in startFraction..effectiveEndFraction) return null
                                    return ((positionFraction - startFraction) /
                                        (effectiveEndFraction - startFraction)) * size.width
                                }

	                                awaitEachGesture {
	                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    Log.d(
                                        ARR_SEGMENT_STATE_TAG,
                                        "BEFORE_TOUCH_START selectedSegmentId=$latestEditableSegmentId " +
                                            "selectedSegmentStartMs=$latestEditableSegmentStartMs " +
                                            "selectedSegmentEndMs=$latestEditableSegmentEndMs " +
                                            "isSegmentEditMode=${latestEditableSegmentId != null && latestEditableSegmentStartMs != null && latestEditableSegmentEndMs != null}"
                                    )
	                                    val pointerCountAtDown = 1
                                    val startMs = latestEditableSegmentStartMs
                                    val endMs = latestEditableSegmentEndMs
                                    val loopStartMs = segmentInMs?.let { inMs ->
                                        segmentOutMs?.let { outMs -> minOf(inMs, outMs).coerceAtLeast(0L) }
                                    }
                                    val loopEndMs = segmentInMs?.let { inMs ->
                                        segmentOutMs?.let { outMs ->
                                            maxOf(inMs, outMs).coerceAtLeast(minOf(inMs, outMs) + 1L)
                                        }
                                    }
                                    val loopInX = loopStartMs?.let(::xForTimeMs)
                                    val loopOutX = loopEndMs?.let(::xForTimeMs)
                                    val handleHitPx = 34.dp.toPx()
                                    val distanceToLoopIn = loopInX?.let { abs(down.position.x - it) } ?: Float.MAX_VALUE
                                    val distanceToLoopOut = loopOutX?.let { abs(down.position.x - it) } ?: Float.MAX_VALUE
                                    val nearestLoopHandle = when {
                                        distanceToLoopIn <= handleHitPx && distanceToLoopIn <= distanceToLoopOut ->
                                            TimelineArrangementEditHandle.IN
                                        distanceToLoopOut <= handleHitPx ->
                                            TimelineArrangementEditHandle.OUT
                                        else -> TimelineArrangementEditHandle.NONE
                                    }
	                                    val isSegmentEditMode = startMs != null && endMs != null
                                    val target = if (isSegmentEditMode) "SEGMENT" else "LOOP"
                                    val rawTargetStartMs = if (isSegmentEditMode) startMs else loopStartMs
                                    val rawTargetEndMs = if (isSegmentEditMode) endMs else loopEndMs
	                                    if (pointerCountAtDown > 1 || rawTargetStartMs == null || rawTargetEndMs == null) {
	                                        val touchTarget = if (nearestLoopHandle != TimelineArrangementEditHandle.NONE) {
	                                            "LOOP"
	                                        } else {
	                                            "NONE"
	                                        }
	                                        Log.d(
	                                            ARR_UNDO_HANDLE_TAG,
	                                            "TOUCH_START selectedSegmentId=$latestEditableSegmentId " +
	                                                "nearestHandle=$nearestLoopHandle target=$touchTarget " +
	                                                "undoStackSize=$latestUndoStackSize"
	                                        )
	                                        Log.d(
	                                            ARR_SEGMENT_GESTURE_TAG,
	                                            "DRAG_START x=${down.position.x} pointerCount=$pointerCountAtDown " +
	                                                "nearestHandle=$nearestLoopHandle selectedSegmentId=$latestEditableSegmentId " +
	                                                "isSegmentEditMode=$isSegmentEditMode loopStartMs=$loopStartMs " +
	                                                "loopEndMs=$loopEndMs selectedSegmentStartMs=$startMs " +
	                                                "selectedSegmentEndMs=$endMs"
	                                        )
	                                        Log.d(
	                                            ARR_SEGMENT_GESTURE_TAG,
	                                            "HANDLE_CAPTURED handle=${TimelineArrangementEditHandle.NONE} target=NONE"
	                                        )
	                                        return@awaitEachGesture
	                                    }

	                                    val safeStartMs = minOf(rawTargetStartMs, rawTargetEndMs)
                                    val safeEndMs = maxOf(rawTargetStartMs, rawTargetEndMs)
	                                    var pendingStartMs = safeStartMs
	                                    var pendingEndMs = safeEndMs
                                    var pendingX = down.position.x
                                    val inX = xForTimeMs(safeStartMs)
                                    val outX = xForTimeMs(safeEndMs)
                                    val distanceToIn = inX?.let { abs(down.position.x - it) } ?: Float.MAX_VALUE
                                    val distanceToOut = outX?.let { abs(down.position.x - it) } ?: Float.MAX_VALUE
                                    activeEditHandle = when {
                                        distanceToIn <= handleHitPx && distanceToIn <= distanceToOut ->
                                            TimelineArrangementEditHandle.IN
                                        distanceToOut <= handleHitPx ->
                                            TimelineArrangementEditHandle.OUT
                                        else -> TimelineArrangementEditHandle.NONE
                                    }
	                                    val pendingTarget = if (activeEditHandle != TimelineArrangementEditHandle.NONE) {
	                                        target
	                                    } else {
	                                        "NONE"
	                                    }
                                    val nearestHandle = if (activeEditHandle != TimelineArrangementEditHandle.NONE) {
                                        activeEditHandle
                                    } else {
                                        nearestLoopHandle
                                    }
                                    Log.d(
                                        ARR_UNDO_HANDLE_TAG,
                                        "TOUCH_START selectedSegmentId=$latestEditableSegmentId " +
                                            "nearestHandle=$nearestHandle target=$pendingTarget " +
                                            "undoStackSize=$latestUndoStackSize"
                                    )
	                                    Log.d(
	                                        ARR_SEGMENT_GESTURE_TAG,
	                                        "DRAG_START x=${down.position.x} pointerCount=$pointerCountAtDown " +
	                                            "nearestHandle=$nearestHandle selectedSegmentId=$latestEditableSegmentId " +
	                                            "isSegmentEditMode=$isSegmentEditMode loopStartMs=$loopStartMs loopEndMs=$loopEndMs " +
	                                            "selectedSegmentStartMs=$safeStartMs selectedSegmentEndMs=$safeEndMs " +
	                                            "segmentInX=$inX segmentOutX=$outX loopInX=$loopInX loopOutX=$loopOutX"
	                                    )
                                    Log.d(
                                        ARR_SEGMENT_GESTURE_TAG,
                                        "HANDLE_CAPTURED handle=$activeEditHandle target=$pendingTarget"
                                    )
                                    val willPushUndo = activeEditHandle != TimelineArrangementEditHandle.NONE &&
                                        pendingTarget == "SEGMENT"
	                                    Log.d(
	                                        ARR_UNDO_HANDLE_TAG,
	                                        "HANDLE_CAPTURED handle=$activeEditHandle target=$pendingTarget " +
	                                            "willPushUndo=$willPushUndo"
	                                    )
	                                    if (activeEditHandle == TimelineArrangementEditHandle.NONE) {
	                                        return@awaitEachGesture
	                                    }
                                    var longPressPosition = down.position
                                    var latestLongPressChange = down
                                    val longPressCancelled = withTimeoutOrNull(
                                        viewConfiguration.longPressTimeoutMillis
                                    ) {
                                        var cancelled = false
                                        while (!cancelled) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.count { it.pressed } > 1) {
                                                cancelled = true
                                                continue
                                            }
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            if (change == null) {
                                                cancelled = true
                                                continue
                                            }
                                            if (!change.pressed || change.isConsumed) {
                                                cancelled = true
                                                continue
                                            }
                                            latestLongPressChange = change
                                            longPressPosition = change.position
                                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                                cancelled = true
                                            }
                                        }
                                        cancelled
                                    } ?: false
	                                    if (longPressCancelled) {
	                                        Log.d(
	                                            ARR_UNDO_HANDLE_TAG,
	                                            "HANDLE_RELEASED target=$pendingTarget committed=false reason=long_press_cancelled"
                                        )
	                                        activeEditHandle = TimelineArrangementEditHandle.NONE
	                                        return@awaitEachGesture
	                                    }
		                                    val longPressX = longPressPosition.x
	                                    val capturedX = when (activeEditHandle) {
	                                        TimelineArrangementEditHandle.IN -> inX
	                                        TimelineArrangementEditHandle.OUT -> outX
                                        TimelineArrangementEditHandle.NONE -> null
                                    }
                                    if (capturedX == null || abs(longPressX - capturedX) > handleHitPx) {
                                        Log.d(
                                            ARR_UNDO_HANDLE_TAG,
                                            "HANDLE_RELEASED target=$pendingTarget committed=false reason=long_press_moved"
                                        )
	                                        activeEditHandle = TimelineArrangementEditHandle.NONE
	                                        return@awaitEachGesture
	                                    }
	                                    latestLongPressChange.consume()
                                    if (pendingTarget == "SEGMENT") {
		                                    latestOnEditableSegmentHandleCaptured()
                                    }
		                                    var cancelled = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.count { it.pressed } > 1) {
                                            cancelled = true
                                            break
                                        }
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        change.consume()
                                        pendingX = change.position.x
                                        val draggedTimeMs = timeMsForX(change.position.x)
                                        when (activeEditHandle) {
                                            TimelineArrangementEditHandle.IN -> {
                                                val safeCurrentEndMs = pendingEndMs.coerceAtLeast(pendingStartMs + 1L)
                                                val nextStartMs = draggedTimeMs.coerceIn(0L, safeCurrentEndMs - 1L)
                                                pendingStartMs = nextStartMs
	                                                Log.d(
	                                                    ARR_SEGMENT_GESTURE_TAG,
	                                                    "HANDLE_DRAGGING handle=${TimelineArrangementEditHandle.IN} " +
	                                                        "target=$pendingTarget newTimeMs=$nextStartMs " +
	                                                        "selectedSegmentId=$latestEditableSegmentId"
	                                                )
                                                Log.d(
                                                    ARR_UNDO_HANDLE_TAG,
                                                    "HANDLE_DRAGGING handle=${TimelineArrangementEditHandle.IN} " +
                                                        "target=$pendingTarget newStartMs=$nextStartMs " +
                                                        "newEndMs=$safeCurrentEndMs"
                                                )
                                                if (pendingTarget == "SEGMENT") {
		                                                latestOnEditableSegmentBoundsChange(
                                                        nextStartMs,
                                                        safeCurrentEndMs,
                                                        false,
                                                        TimelineArrangementEditHandle.IN
                                                    )
                                                } else {
                                                    latestOnLoopBoundsChange(
                                                        nextStartMs,
                                                        safeCurrentEndMs,
                                                        false,
                                                        TimelineArrangementEditHandle.IN
                                                    )
                                                }
                                            }
                                            TimelineArrangementEditHandle.OUT -> {
                                                val safeCurrentStartMs = pendingStartMs.coerceAtLeast(0L)
                                                val nextEndMs = draggedTimeMs.coerceIn(
                                                    safeCurrentStartMs + 1L,
                                                    durationMs.toLong()
                                                )
                                                pendingEndMs = nextEndMs
	                                                Log.d(
	                                                    ARR_SEGMENT_GESTURE_TAG,
	                                                    "HANDLE_DRAGGING handle=${TimelineArrangementEditHandle.OUT} " +
	                                                        "target=$pendingTarget newTimeMs=$nextEndMs " +
	                                                        "selectedSegmentId=$latestEditableSegmentId"
	                                                )
                                                Log.d(
                                                    ARR_UNDO_HANDLE_TAG,
                                                    "HANDLE_DRAGGING handle=${TimelineArrangementEditHandle.OUT} " +
                                                        "target=$pendingTarget newStartMs=$safeCurrentStartMs " +
                                                        "newEndMs=$nextEndMs"
                                                )
                                                if (pendingTarget == "SEGMENT") {
		                                                latestOnEditableSegmentBoundsChange(
                                                        safeCurrentStartMs,
                                                        nextEndMs,
                                                        false,
                                                        TimelineArrangementEditHandle.OUT
                                                    )
                                                } else {
                                                    latestOnLoopBoundsChange(
                                                        safeCurrentStartMs,
                                                        nextEndMs,
                                                        false,
                                                        TimelineArrangementEditHandle.OUT
                                                    )
                                                }
                                            }
                                            TimelineArrangementEditHandle.NONE -> Unit
                                        }
                                    }

                                    val shouldCommitSegment = !cancelled &&
                                        activeEditHandle != TimelineArrangementEditHandle.NONE
	                                    Log.d(
	                                        ARR_SEGMENT_GESTURE_TAG,
	                                        "HANDLE_RELEASED handle=$activeEditHandle target=$pendingTarget " +
                                            "shouldCommitSegment=$shouldCommitSegment cancelled=$cancelled " +
                                            "selectedSegmentId=$latestEditableSegmentId " +
	                                            "pendingStartMs=$pendingStartMs pendingEndMs=$pendingEndMs"
	                                    )
                                    Log.d(
                                        ARR_UNDO_HANDLE_TAG,
                                        "HANDLE_RELEASED target=$pendingTarget committed=$shouldCommitSegment"
                                    )
		                                    if (shouldCommitSegment) {
                                        val safeDuration = durationMs.coerceAtLeast(1)
                                        val visualPreRollMs = ARRANGEMENT_WAVEFORM_VISUAL_PREROLL_MS
                                        val visualDurationMs = safeDuration.toLong() + visualPreRollMs
                                        val visibleFraction = 1f / waveformZoom.coerceAtLeast(1f)
                                        val startFraction = (waveformCenterFraction - visibleFraction / 2f)
                                            .coerceIn(0f, 1f)
                                        val endFraction = (startFraction + visibleFraction).coerceIn(0f, 1f)
                                        val effectiveEndFraction = if (endFraction <= startFraction) 1f else endFraction
                                        val committedTimeMs = when (activeEditHandle) {
                                            TimelineArrangementEditHandle.IN -> pendingStartMs
                                            TimelineArrangementEditHandle.OUT -> pendingEndMs
                                            TimelineArrangementEditHandle.NONE -> pendingStartMs
                                        }
                                        Log.d(
                                            ARR_TIMING_DIAG_TAG,
                                            "ACTION action=${if (activeEditHandle == TimelineArrangementEditHandle.OUT) "SET_OUT" else "SET_IN"} " +
                                                "x=$pendingX calculatedTimeMs=${timeMsForX(pendingX)} " +
                                                "clampedTimeMs=$committedTimeMs waveformZoom=$waveformZoom " +
                                                "waveformCenterFraction=$waveformCenterFraction " +
                                                "startFraction=$startFraction endFraction=$effectiveEndFraction " +
                                                "safeDurationMs=$safeDuration visualDurationMs=$visualDurationMs " +
                                                "target=$pendingTarget selectedSegmentId=$latestEditableSegmentId"
                                        )
                                        if (pendingTarget == "SEGMENT") {
		                                            latestOnEditableSegmentBoundsChange(
	                                                minOf(pendingStartMs, pendingEndMs),
	                                                maxOf(pendingStartMs, pendingEndMs),
	                                                true,
	                                                activeEditHandle
	                                            )
                                        } else {
                                            latestOnLoopBoundsChange(
                                                minOf(pendingStartMs, pendingEndMs),
                                                maxOf(pendingStartMs, pendingEndMs),
                                                true,
                                                activeEditHandle
                                            )
                                        }
	                                    }
                                    activeEditHandle = TimelineArrangementEditHandle.NONE
                                }
                            }
                    ) {
                        val centerY = size.height / 2f
                        val widthPx = size.width
                        val heightPx = size.height
                        val safeDuration = durationMs.coerceAtLeast(1)
                        val visualPreRollMs = ARRANGEMENT_WAVEFORM_VISUAL_PREROLL_MS
                        val visualDurationMs = safeDuration.toLong() + visualPreRollMs
                        val visualDurationFloat = visualDurationMs.toFloat().coerceAtLeast(1f)
                        val peakCount = peaks.size.coerceAtLeast(1)
                        val visibleFraction = 1f / waveformZoom.coerceAtLeast(1f)
                        val startFraction = (waveformCenterFraction - visibleFraction / 2f).coerceIn(0f, 1f)
                        val endFraction = (startFraction + visibleFraction).coerceIn(0f, 1f)
                        val effectiveEndFraction = if (endFraction <= startFraction) 1f else endFraction
                        val waveformNormalColor = Color(0xFF80CBC4)
                        val waveformAccentColor = Color(0xFFB2FF59)
                        val visibleAudioStartMs = (
                            startFraction * visualDurationFloat - visualPreRollMs.toFloat()
                            ).coerceIn(0f, safeDuration.toFloat())
                        val visibleAudioEndMs = (
                            effectiveEndFraction * visualDurationFloat - visualPreRollMs.toFloat()
                            ).coerceIn(0f, safeDuration.toFloat())
                        val firstVisiblePeakIndex = (
                            visibleAudioStartMs / safeDuration.toFloat() * peakCount.toFloat()
                            ).toInt().coerceIn(0, peaks.lastIndex)
                        val lastVisiblePeakIndexExclusive = (
                            visibleAudioEndMs / safeDuration.toFloat() * peakCount.toFloat()
                            ).toInt().plus(1).coerceIn(firstVisiblePeakIndex + 1, peakCount)
                        val visiblePeakCount = lastVisiblePeakIndexExclusive - firstVisiblePeakIndex
                        val maxWaveformColumns = widthPx.roundToInt().coerceAtLeast(1)
                        val peaksPerColumn = (
                            (visiblePeakCount + maxWaveformColumns - 1) / maxWaveformColumns
                            ).coerceAtLeast(1)
                        var bucketStartIndex = firstVisiblePeakIndex
                        while (bucketStartIndex < lastVisiblePeakIndexExclusive) {
                            val bucketEndIndex = minOf(
                                bucketStartIndex + peaksPerColumn,
                                lastVisiblePeakIndexExclusive
                            )
                            var peak = 0f
                            for (index in bucketStartIndex until bucketEndIndex) {
                                peak = maxOf(peak, peaks[index])
                            }
                            val bucketCenterIndex = (bucketStartIndex + bucketEndIndex - 1) / 2f
                            val realTimeMs = (
                                (bucketCenterIndex + 0.5f) / peakCount.toFloat()
                                ) * safeDuration.toFloat()
                            val positionFraction = ((realTimeMs + visualPreRollMs.toFloat()) / visualDurationFloat)
                                .coerceIn(0f, 1f)
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
                            bucketStartIndex = bucketEndIndex
                        }

                        if (tempoBpm != null && tempoBpm > 0 && measureAnchorMs != null) {
                            val visibleStartMs =
                                startFraction * visualDurationFloat - visualPreRollMs.toFloat()
                            val visibleEndMs =
                                effectiveEndFraction * visualDurationFloat - visualPreRollMs.toFloat()
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
                                    val beatFraction = (
                                        (beatPositionMs + visualPreRollMs.toDouble()) /
                                            visualDurationMs.toDouble()
                                        ).toFloat()
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
                                loopStartMs.coerceIn(0L, safeDuration.toLong()).toFloat() +
                                    visualPreRollMs.toFloat()
                                ) / visualDurationFloat
                            val loopEndFraction = (
                                loopEndMs.coerceIn(0L, safeDuration.toLong()).toFloat() +
                                    visualPreRollMs.toFloat()
                                ) / visualDurationFloat

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

                        val currentFraction = (
                            currentPositionMs.coerceIn(0L, safeDuration.toLong()).toFloat() +
                                visualPreRollMs.toFloat()
                            ) / visualDurationFloat
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
                            val inFraction = (
                                inMs.coerceIn(0L, safeDuration.toLong()).toFloat() +
                                    visualPreRollMs.toFloat()
                                ) / visualDurationFloat
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
                            val outFraction = (
                                outMs.coerceIn(0L, safeDuration.toLong()).toFloat() +
                                    visualPreRollMs.toFloat()
                                ) / visualDurationFloat
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
                        if (editableSegmentStartMs != null &&
                            editableSegmentEndMs != null &&
                            editableSegmentStartMs != editableSegmentEndMs
                        ) {
                            val editStartMs = minOf(editableSegmentStartMs, editableSegmentEndMs)
                                .coerceAtLeast(0L)
                            val editEndMs = maxOf(editableSegmentStartMs, editableSegmentEndMs)
                                .coerceAtLeast(editStartMs + 1L)
                            val editStartFraction = (
                                editStartMs.coerceIn(0L, safeDuration.toLong()).toFloat() +
                                    visualPreRollMs.toFloat()
                                ) / visualDurationFloat
                            val editEndFraction = (
                                editEndMs.coerceIn(0L, safeDuration.toLong()).toFloat() +
                                    visualPreRollMs.toFloat()
                                ) / visualDurationFloat
                            val editStartX = ((editStartFraction - startFraction) /
                                (effectiveEndFraction - startFraction)) * widthPx
                            val editEndX = ((editEndFraction - startFraction) /
                                (effectiveEndFraction - startFraction)) * widthPx
                            val clampedEditStartX = editStartX.coerceIn(0f, widthPx)
                            val clampedEditEndX = editEndX.coerceIn(0f, widthPx)
                            if (clampedEditEndX > clampedEditStartX) {
                                drawRect(
                                    color = Color(0xFF64B5F6).copy(alpha = 0.12f),
                                    topLeft = Offset(clampedEditStartX, 0f),
                                    size = androidx.compose.ui.geometry.Size(
                                        width = clampedEditEndX - clampedEditStartX,
                                        height = heightPx
                                    )
                                )
                            }
                        }
                        if (segmentInMs == null) {
                            measureAnchorMs?.let { anchorMs ->
                                val anchorFraction = (
                                    anchorMs.coerceIn(0L, safeDuration.toLong()).toFloat() +
                                        visualPreRollMs.toFloat()
                                    ) / visualDurationFloat
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

private fun buildTimelineStructureMediaItem(
    audioPath: String,
    segment: ArrangementSegmentData
): MediaItem? {
    return buildTimelineStructureMediaItems(audioPath, listOf(segment)).firstOrNull()
}

private fun buildNextTempoExportName(
    tracksRoot: File,
    sourceTitle: String?
): String {
    val baseName = normalizeTempoExportBaseName(sourceTitle)
    val namePattern = Regex("^${Regex.escape(baseName)}_AR(\\d{2})$", RegexOption.IGNORE_CASE)
    val nextIndex = tracksRoot
        .takeIf { it.isDirectory }
        ?.listFiles()
        .orEmpty()
        .asSequence()
        .map { file ->
            if (file.isDirectory) {
                file.name
            } else {
                file.nameWithoutExtension
            }
        }
        .mapNotNull { existingName ->
            namePattern.matchEntire(existingName)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        .maxOrNull()
        ?.plus(1)
        ?: 1

    return "${baseName}_AR${nextIndex.coerceAtLeast(1).toString().padStart(2, '0')}"
}

@Composable
private fun ArrangementHelpPageContent(
    message: String
) {
    val sectionTitleRegex = Regex("^\\d+\\)")
    val lines = remember(message) { message.split('\n') }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isBlank() -> Spacer(modifier = Modifier.height(8.dp))

                line.startsWith("🎵") -> {
                    Text(
                        text = line,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                sectionTitleRegex.containsMatchIn(line) -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = line,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    )
                }

                line == line.uppercase() || line.endsWith(":") -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = line,
                        color = Color(0xFFF3F6F8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )
                }

                else -> {
                    Text(
                        text = line,
                        color = Color(0xFFD7DEE3),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 21.sp
                    )
                }
            }
        }
    }
}

private fun normalizeTempoExportBaseName(sourceTitle: String?): String {
    return sourceTitle
        .orEmpty()
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "Song" }
}

private fun buildTempoExportWavFileName(sourceName: String): String {
    val sanitizedName = sourceName
        .trim()
        .removeSuffix(".wav")
        .removeSuffix(".WAV")
        .replace(Regex("[\\\\/:*?\"<>|]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "Arrangement" }
    return "$sanitizedName.wav"
}

private fun resolveTempoPublicBackingTracksDir(): File {
    val musicRoot = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_MUSIC
    )
    return File(File(musicRoot, "SPL_Music"), "BackingTracks")
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
