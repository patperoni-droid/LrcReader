@file:OptIn(androidx.media3.common.util.UnstableApi::class,
androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.patrick.lrcreader.ui

import android.util.Log
import android.widget.Toast
import java.io.File
import android.net.Uri
import com.patrick.lrcreader.core.LyricsPerf
import com.patrick.lrcreader.core.readSyltAsLrcFromUri
import com.patrick.lrcreader.core.readUsltFromUri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.FilterChip
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.core.AutoReturnPrefs
import com.patrick.lrcreader.core.AccordsEnsureResult
import com.patrick.lrcreader.core.AccordsUiTruth
import com.patrick.lrcreader.core.DisplayPrefs
import com.patrick.lrcreader.core.EditionConfig
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.LatestAccordsWriteQueue
import com.patrick.lrcreader.core.LightIndicatorPrefs
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.LyricsViewMode
import com.patrick.lrcreader.core.MidiCueDispatcher
import com.patrick.lrcreader.core.TrackLyricsViewPrefs
import com.patrick.lrcreader.core.TrackTimelineTempoPrefs
import com.patrick.lrcreader.core.light.LightAction
import com.patrick.lrcreader.core.light.LightCueAutoGenerator
import com.patrick.lrcreader.core.light.LightCue
import com.patrick.lrcreader.core.light.LightCueDispatcher
import com.patrick.lrcreader.core.light.LightPreviewTestController
import com.patrick.lrcreader.core.light.LightSceneState
// ✅ On retire l’import pour éviter tout auto-import douteux
// import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.audio.AudioEngine
import com.patrick.lrcreader.core.audio.SoundTouchBridge
import com.patrick.lrcreader.core.findActiveLrcIndex
import com.patrick.lrcreader.core.parseLrc
import com.patrick.lrcreader.core.lyrics.LyricsMemoryCache
import com.patrick.lrcreader.core.runAccordsDeleteIo
import com.patrick.lrcreader.core.runAccordsSaveIo
import com.patrick.lrcreader.core.resolveAccordsUiTruthAfterDelete
import com.patrick.lrcreader.core.resolveAccordsUiTruthAfterSave
import com.patrick.lrcreader.core.resolveAccordsLrcFileName
import com.patrick.lrcreader.core.buildAccordsIoFailureFeedback
import com.patrick.lrcreader.core.buildAccordsIoFailureLog
import com.patrick.lrcreader.core.resolveChordsLookupFileName
import com.patrick.lrcreader.core.resolveAccordsEditTargetTrack
import com.patrick.lrcreader.core.resolveLyricsViewMode
import com.patrick.lrcreader.core.TimelinePaletteStore
import com.patrick.lrcreader.core.lyrics.LyricsCacheEntry
import com.patrick.lrcreader.smp.DEFAULT_TIMELINE_NOTE_DURATION_MS
import com.patrick.lrcreader.smp.SmpConfig
import com.patrick.lrcreader.smp.SmpAnnotationsStore
import com.patrick.lrcreader.smp.SmpAutoMigrationResult
import com.patrick.lrcreader.smp.SmpLightCueBridge
import com.patrick.lrcreader.smp.SmpTimelineStore
import com.patrick.lrcreader.smp.TimelineMarker
import com.patrick.lrcreader.smp.TimelineMarkerKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val DEFAULT_TIMELINE_LIGHT_CUE_ARGB = 0xFFFF0000L
private const val DMX_PLAYBACK_POLL_INTERVAL_MS = 20L
private const val PLAYER_LRC_TAG = "PLAYER_LRC"

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
    currentTrackVolumeSource: String = com.patrick.lrcreader.smp.SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL,
    onTrackGainChange: (Int) -> Unit,
    onTrackGainCommit: (Int) -> Unit,
    tempo: Float,
    onTempoChange: (Float) -> Unit,
    pitchSemi: Int,
    onPitchSemiChange: (Int) -> Unit,
    ensureSmpTrackForLyricsSave: suspend (String) -> SmpAutoMigrationResult? = { null },
    onTrackPromotedToSmp: (SmpAutoMigrationResult) -> Unit = {},
    onRequestShowPlaylist: () -> Unit,
    currentSongId: String? = null,
    onOpenArrangementHub: () -> Unit = {},
    requestedNavigationTarget: String? = null,
    requestedNavigationToken: Int = 0,
    onOpenWaveform: (String) -> Unit = {},
    getPositionMs: () -> Long,
    getEffectiveDurationMs: () -> Long,
    seekToMs: (Long) -> Unit
) {
    val listState = rememberSaveable(currentTrackUri, saver = LazyListState.Saver) {
        LazyListState()
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val showLightIndicator = remember(context) { LightIndicatorPrefs.isEnabled(context) }
    val dmxUiVisible = EditionConfig.isPro && EditionConfig.isDmxUiEnabled
    val timelineDmxUiVisible = true
    val lastTriggeredMidiProgramChange by MidiCueDispatcher.lastTriggeredProgramChange.collectAsState()
    val simulatedLightScene by LightCueDispatcher.sceneState.collectAsState()
    val isLaboBuild = remember(context.packageName) { context.packageName.endsWith(".labo") }
    val hqStatus = remember { SoundTouchBridge.logStatusOnce(reason = "PlayerScreen:init") }
    val isHqAvailable = hqStatus.available
    val showHqOffBanner = isLaboBuild && !isHqAvailable
    var hqToastShownAtMs by remember { mutableStateOf(0L) }
    val sHqUnavailable = stringResource(R.string.player_hq_unavailable)
    val sAccordsTrackChangedBlocked = stringResource(R.string.player_accords_track_changed_blocked)
    val sAccordsSaveQueueClosed = stringResource(R.string.player_accords_save_queue_closed)
    val sAccordsActionSave = stringResource(R.string.accords_action_save)
    val sAccordsActionDelete = stringResource(R.string.accords_action_delete)
    val sDeleteLiveNote = stringResource(R.string.player_cd_delete_live_note)
    val sTimelineSaveFailed = stringResource(R.string.timeline_save_failed)
    val sLightGenerateFailed = stringResource(R.string.light_generate_failed)
    val sTrackMixProTitle = stringResource(R.string.track_mix_lite_dialog_title)
    val sTrackMixProMessage = stringResource(R.string.track_mix_lite_dialog_message)
    val sUpgradeToPro = stringResource(R.string.library_upgrade_to_pro)
    val midiCueTraceTag = "MIDI_CUE_TRACE"

    // 🔊 Brancher ExoPlayer au bus principal (fader LECTEUR)
    var activeLiveNote by remember { mutableStateOf<LiveNote?>(null) }
    var activeLiveNoteFromTimeline by remember { mutableStateOf(false) }
    var lastLiveNoteTraceKey by remember(currentTrackUri) { mutableStateOf<String?>(null) }
    val openGridSetupOnEntry = requestedNavigationTarget == "grid_setup" && !currentTrackUri.isNullOrBlank()
    var isEditingTimeline by rememberSaveable(currentTrackUri, requestedNavigationToken) {
        mutableStateOf(openGridSetupOnEntry)
    }
    var startTimelineInGridSetup by rememberSaveable(currentTrackUri, requestedNavigationToken) {
        mutableStateOf(openGridSetupOnEntry)
    }
    var editingTimelineMidiMarkerIndex by rememberSaveable(currentTrackUri) { mutableStateOf<Int?>(null) }
    var editingTimelineLightCueTimeMs by rememberSaveable(currentTrackUri) { mutableStateOf<Long?>(null) }
    var showLightGenerationDialog by rememberSaveable(currentTrackUri) { mutableStateOf(false) }
    var timelineLightPreviewPositionMs by remember(currentTrackUri) { mutableStateOf<Long?>(null) }
    var timelinePreparedLoopRestoreItem by remember(currentTrackUri) { mutableStateOf<MediaItem?>(null) }
    var timelinePreparedLoopRestorePositionMs by remember(currentTrackUri) { mutableLongStateOf(0L) }
    var timelinePreparedLoopRestoreRepeatMode by remember(currentTrackUri) { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var timelinePreparedLoopRestorePlayWhenReady by remember(currentTrackUri) { mutableStateOf(false) }
    var isTimelinePreparedLoopActive by remember(currentTrackUri) { mutableStateOf(false) }
    var timelineMarkers by remember(currentTrackUri) { mutableStateOf<List<TimelineMarker>>(emptyList()) }
    var timelineLightCues by remember(currentTrackUri) { mutableStateOf<List<LightCue>>(emptyList()) }
    var timelineDmxClipboard by remember { mutableStateOf<TimelineDmxClipboard?>(null) }
    val projectedTimelineLiveNotes = remember(timelineMarkers) {
        projectTimelineNoteMarkers(timelineMarkers)
    }
    var timelinePalette by remember(context) {
        mutableStateOf(TimelinePaletteStore.load(context))
    }
    val isCurrentTrackSmp = remember(context, currentTrackUri) {
        currentTrackUri?.let { trackUri ->
            LrcStorage.isSmpRuntimeTrack(context, trackUri)
        } ?: false
    }
    val canOpenWaveform = isCurrentTrackSmp && !currentSongId.isNullOrBlank()
    val midiMonitorEvent = remember(lastTriggeredMidiProgramChange, currentTrackUri) {
        val trackUri = currentTrackUri?.takeIf { it.isNotBlank() } ?: return@remember null
        lastTriggeredMidiProgramChange?.takeIf { sent -> sent.trackUri == trackUri }
    }
    val currentTrackLightScene = remember(simulatedLightScene, currentTrackUri) {
        if (simulatedLightScene.trackUri == currentTrackUri) {
            simulatedLightScene
        } else {
            LightSceneState.off(trackUri = currentTrackUri)
        }
    }
    val timelineEditorEntries = remember(context, timelineMarkers, timelineLightCues, timelineDmxUiVisible) {
        buildTimelineEditorEntries(
            context = context,
            timelineMarkers = timelineMarkers,
            lightCues = if (timelineDmxUiVisible) timelineLightCues else emptyList()
        )
    }
    val timelineEditorMarkers = remember(timelineEditorEntries) {
        timelineEditorEntries.map { entry -> entry.marker }
    }
    val stopTimelinePreparedLoopTest = remember(exoPlayer, currentTrackUri) {
        {
            val restoreItem = timelinePreparedLoopRestoreItem
            if (restoreItem == null) {
                isTimelinePreparedLoopActive = false
                Unit
            } else {
                runCatching { exoPlayer.stop() }
                runCatching {
                    exoPlayer.repeatMode = timelinePreparedLoopRestoreRepeatMode
                    exoPlayer.setMediaItem(restoreItem)
                    exoPlayer.prepare()
                    if (timelinePreparedLoopRestorePositionMs > 0L) {
                        exoPlayer.seekTo(timelinePreparedLoopRestorePositionMs)
                    }
                    if (timelinePreparedLoopRestorePlayWhenReady) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    } else {
                        exoPlayer.playWhenReady = false
                        exoPlayer.pause()
                    }
                }
                isTimelinePreparedLoopActive = false
                timelinePreparedLoopRestoreItem = null
            }
        }
    }
    val startTimelinePreparedLoopTest = remember(exoPlayer, currentTrackUri) {
        { inMs: Long, outMs: Long ->
            val safeOutMs = outMs.coerceAtLeast(inMs + 1L)
            val activeItem = exoPlayer.currentMediaItem
            val activeUri = activeItem?.localConfiguration?.uri
            if (activeItem != null && activeUri != null) {
                if (!isTimelinePreparedLoopActive) {
                    timelinePreparedLoopRestoreItem = activeItem
                    timelinePreparedLoopRestorePositionMs = exoPlayer.currentPosition
                    timelinePreparedLoopRestoreRepeatMode = exoPlayer.repeatMode
                    timelinePreparedLoopRestorePlayWhenReady = exoPlayer.playWhenReady || exoPlayer.isPlaying
                }

                val clippedItem = MediaItem.Builder()
                    .setUri(activeUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(inMs)
                            .setEndPositionMs(safeOutMs)
                            .build()
                    )
                    .build()

                runCatching { exoPlayer.stop() }
                runCatching {
                    exoPlayer.setMediaItem(clippedItem)
                    exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }
                isTimelinePreparedLoopActive = true
            }
        }
    }
    val canPasteTimelineDmxCue = remember(currentTrackUri, timelineDmxClipboard) {
        val current = currentTrackUri?.trim().orEmpty()
        val clipboard = timelineDmxClipboard
        clipboard != null && current.isNotBlank() && clipboard.trackUri == current
    }
    var showLightTestDialog by rememberSaveable(currentTrackUri) { mutableStateOf(false) }
    var hasLightCues by remember(currentTrackUri) { mutableStateOf(false) }
    var timelineMeasuresTempoBpm by rememberSaveable(currentTrackUri) { mutableStateOf<Int?>(null) }
    var timelineSessionMeasureAnchorMs by remember(currentTrackUri) { mutableStateOf<Long?>(null) }
    var liteTrackMixTempo by remember(currentTrackUri) { mutableFloatStateOf(tempo) }
    var liteTrackMixPitchSemi by remember(currentTrackUri) { mutableIntStateOf(pitchSemi) }
    var liteTrackMixModified by remember(currentTrackUri) { mutableStateOf(false) }
    var showTrackMixProDialog by remember { mutableStateOf(false) }
    LaunchedEffect(exoPlayer) {
        PlayerBusController.attachPlayer(context, exoPlayer)
    }
    LaunchedEffect(context) {
        LightCueDispatcher.init(context)
    }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            showLightTestDialog = false
            LightPreviewTestController.stop()
            timelineLightPreviewPositionMs = null
        }
    }
    LaunchedEffect(currentTrackUri, isEditingTimeline) {
        if ((!isEditingTimeline || currentTrackUri.isNullOrBlank()) && isTimelinePreparedLoopActive) {
            stopTimelinePreparedLoopTest()
        }
    }
    LaunchedEffect(currentTrackUri) {
        liteTrackMixTempo = tempo
        liteTrackMixPitchSemi = pitchSemi
        liteTrackMixModified = false
    }
    LaunchedEffect(currentTrackUri) {
        timelineMeasuresTempoBpm = currentTrackUri?.let { trackUri ->
            withContext(Dispatchers.IO) {
                TrackTimelineTempoPrefs.getTempoBpm(context, trackUri)
            }
        }
    }

    fun applyLiteTrackMixToPlayer(speed: Float, pitchSemi: Int) {
        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        val semiClamped = pitchSemi.coerceIn(-6, 6)
        val pitchFactor = 2f.pow(semiClamped / 12f)
        AudioEngine.setSpeedPitch(
            speed = safeSpeed,
            pitch = pitchFactor,
            reason = "PlayerScreen.liteTrackMixSession"
        )
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

    suspend fun persistLiveNotesWithAutoMigration(
        trackUriString: String?,
        notes: List<LiveNote>
    ) {
        if (trackUriString.isNullOrBlank()) {
            return
        }

        val isAlreadySmpTrack = resolveSmpAnnotationsTarget(
            context = context,
            trackUriString = trackUriString,
            requireExisting = false
        ) != null
        val migration = if (isAlreadySmpTrack) {
            null
        } else {
            ensureSmpTrackForLyricsSave(trackUriString)
        }
        if (!isAlreadySmpTrack && migration == null) {
            Log.w("LrcDebug", "ANNOTATIONS_SAVE_BLOCKED autoMigrate trackUri=$trackUriString")
            return
        }

        val targetTrackUri = migration?.trackUriString ?: trackUriString
        val saved = persistSmpLiveNotesForTrack(
            context = context,
            trackUriString = targetTrackUri,
            notes = notes
        )
        if (!saved) {
            return
        }

        migration?.let { onTrackPromotedToSmp(it) }
    }

    fun removeLiveNoteAndPersist(note: LiveNote) {
        LiveNoteManager.remove(note)
        activeLiveNote = null
        val liveNotesSnapshot = LiveNoteManager.snapshot()
        val trackUriForPersistence = currentTrackUri
        scope.launch {
            persistLiveNotesWithAutoMigration(
                trackUriString = trackUriForPersistence,
                notes = liveNotesSnapshot
            )
        }
    }

    // ✅ "Niveau du titre" appliqué au moteur
    LaunchedEffect(currentTrackUri, currentTrackGainDb) {
        AudioEngine.applyTrackGainDb(currentTrackGainDb)
    }

    LaunchedEffect(currentTrackUri) {
        MidiCueDispatcher.clearTriggeredProgramChange()
        val loadedNotes = currentTrackUri?.let { trackUriString ->
            withContext(Dispatchers.IO) {
                loadSmpLiveNotesForTrack(context, trackUriString)
            }
        }.orEmpty()
        LiveNoteManager.setNotes(loadedNotes)
        activeLiveNote = null
        activeLiveNoteFromTimeline = false
        lastLiveNoteTraceKey = null
    }
    LaunchedEffect(currentTrackUri) {
        hasLightCues = false
        timelineLightCues = emptyList()
        timelineLightPreviewPositionMs = null
        LightCueDispatcher.resetGlobal()
    }
    LaunchedEffect(currentTrackUri, isCurrentTrackSmp) {
        isEditingTimeline = false
        editingTimelineMidiMarkerIndex = null
        editingTimelineLightCueTimeMs = null
        showLightGenerationDialog = false
        if (!isCurrentTrackSmp || currentTrackUri.isNullOrBlank()) {
            timelineMarkers = emptyList()
            timelineLightCues = emptyList()
            hasLightCues = false
            return@LaunchedEffect
        }

        val trackUriString = currentTrackUri

        timelineMarkers = withContext(Dispatchers.IO) {
            loadSmpTimelineMarkersForTrack(context, trackUriString)
        }
        val loadedLightCues = loadSmpLightCuesForTrack(context, trackUriString)
        timelineLightCues = loadedLightCues
        hasLightCues = loadedLightCues.isNotEmpty()
    }
    LaunchedEffect(timelineMarkers, editingTimelineMidiMarkerIndex) {
        val markerIndex = editingTimelineMidiMarkerIndex ?: return@LaunchedEffect
        if (
            markerIndex !in timelineMarkers.indices ||
            timelineMarkers[markerIndex].kind != TimelineMarkerKind.MIDI
        ) {
            editingTimelineMidiMarkerIndex = null
        }
    }
    LaunchedEffect(isPlaying, currentTrackUri, projectedTimelineLiveNotes) {
        while (true) {
            if (isPlaying) {
                val currentPositionMs = getPositionMs()
                val activeAnnotationNotes = LiveNoteManager.snapshot().filter { note ->
                    isLiveNoteActiveAt(note, currentPositionMs)
                }
                val annotationNote = LiveNoteManager.getActiveNote(currentPositionMs)
                val activeTimelineNotes = projectedTimelineLiveNotes.filter { note ->
                    isLiveNoteActiveAt(note, currentPositionMs)
                }
                val timelineNote = activeTimelineNotes.maxByOrNull { note -> note.timeMs }
                activeLiveNote = annotationNote ?: timelineNote
                activeLiveNoteFromTimeline = annotationNote == null && timelineNote != null
                val traceKey = buildLiveNoteTraceKey(
                    activeAnnotationNotes = activeAnnotationNotes,
                    chosenAnnotationNote = annotationNote,
                    activeTimelineNotes = activeTimelineNotes,
                    chosenTimelineNote = timelineNote,
                    latestTimelineCandidate = activeTimelineNotes.maxByOrNull { note -> note.timeMs },
                    finalChosenNote = activeLiveNote,
                    finalFromTimeline = activeLiveNoteFromTimeline
                )
                val trace = buildLiveNoteTrace(
                    positionMs = currentPositionMs,
                    activeAnnotationNotes = activeAnnotationNotes,
                    chosenAnnotationNote = annotationNote,
                    activeTimelineNotes = activeTimelineNotes,
                    chosenTimelineNote = timelineNote,
                    latestTimelineCandidate = activeTimelineNotes.maxByOrNull { note -> note.timeMs },
                    finalChosenNote = activeLiveNote,
                    finalFromTimeline = activeLiveNoteFromTimeline
                )
                if (traceKey != lastLiveNoteTraceKey) {
                    Log.d("LrcDebug", trace)
                    lastLiveNoteTraceKey = traceKey
                }
            } else {
                activeLiveNote = null
                activeLiveNoteFromTimeline = false
                lastLiveNoteTraceKey = null
            }
            delay(200L)
        }
    }

    val lyricsDelayMs = 0L
    var userOffsetMs by rememberSaveable(currentTrackUri) { mutableStateOf(-100L) }
    var isConcertMode by remember { mutableStateOf(DisplayPrefs.isConcertMode(context)) }
    var selectedViewMode by rememberSaveable(currentTrackUri) {
        mutableStateOf(
            currentTrackUri
                ?.let { TrackLyricsViewPrefs.get(context, it) }
                ?: LyricsViewMode.LYRICS
        )
    }
    var parsedChordLines by remember(currentTrackUri) { mutableStateOf<List<LrcLine>>(emptyList()) }
    var chordsLoading by remember(currentTrackUri) { mutableStateOf(false) }
    var lyricsResolving by remember(currentTrackUri) { mutableStateOf(false) }
    var lyricsResolutionCompleted by remember(currentTrackUri) {
        mutableStateOf(currentTrackUri == null)
    }
    var hasLyricsSource by remember(currentTrackUri) { mutableStateOf(false) }
    var hasChordsSource by remember(currentTrackUri) { mutableStateOf(false) }
    var persistedChordLines by remember(currentTrackUri) { mutableStateOf<List<LrcLine>>(emptyList()) }
    var persistedHasChordsSource by remember(currentTrackUri) { mutableStateOf(false) }
    var resolvedLyricsLrcFileName by remember(currentTrackUri) { mutableStateOf<String?>(null) }
    var lyricsUiVisibleLogged by remember(currentTrackUri) { mutableStateOf(false) }

    var lyricsBoxHeightPx by remember { mutableStateOf(0) }
    var currentLrcIndex by remember(currentTrackUri) { mutableStateOf(0) }

    var lastMidiIndex by remember(currentTrackUri) { mutableStateOf(-1) }
    var userScrolling by remember { mutableStateOf(false) }

    var durationMs by remember(currentTrackUri) { mutableStateOf(0) }
    var positionMs by remember(currentTrackUri) { mutableStateOf(0) }
    var isDragging by remember(currentTrackUri) { mutableStateOf(false) }
    var dragPosMs by remember(currentTrackUri) { mutableStateOf(0) }


    var hasRequestedPlaylist by remember(currentTrackUri) { mutableStateOf(false) }
    var autoReturnArmed by remember(currentTrackUri) { mutableStateOf(false) }
    var autoReturnInitialObservedPositionMs by remember(currentTrackUri) { mutableIntStateOf(-1) }

    LaunchedEffect(currentTrackUri) {
        autoReturnArmed = false
        autoReturnInitialObservedPositionMs = -1
    }

    LaunchedEffect(currentTrackUri, durationMs, positionMs, isPlaying, autoReturnArmed) {
        if (currentTrackUri == null || autoReturnArmed || !isPlaying || durationMs <= 0) return@LaunchedEffect

        if (autoReturnInitialObservedPositionMs < 0) {
            autoReturnInitialObservedPositionMs = positionMs
        }

        val safeStartWindowMs = minOf(5_000, durationMs.coerceAtLeast(1))
        val playbackRestartedNearStart = positionMs in 0..safeStartWindowMs
        val playbackPositionDroppedFromInitial =
            autoReturnInitialObservedPositionMs > 0 &&
                positionMs >= 0 &&
                positionMs + 3_000 < autoReturnInitialObservedPositionMs

        if (playbackRestartedNearStart || playbackPositionDroppedFromInitial) {
            autoReturnArmed = true
        }
    }
    var isAutoReturnEnabled by remember {
        mutableStateOf(AutoReturnPrefs.isEnabled(context))
    }

    var isEditingLyrics by remember { mutableStateOf(false) }
    var editingTrackUri by remember { mutableStateOf<String?>(null) }
    var editingTargetMode by remember { mutableStateOf(LyricsViewMode.LYRICS) }
    var editingResolvedLrcFileName by remember { mutableStateOf<String?>(null) }
    var showMixScreen by remember { mutableStateOf(false) }
    LaunchedEffect(closeMixSignal) {
        if (EditionConfig.isLite && showMixScreen && liteTrackMixModified) {
            showTrackMixProDialog = true
        } else {
            showMixScreen = false
        }
        isEditingLyrics = false
        editingTrackUri = null
        isEditingTimeline = false
        startTimelineInGridSetup = false
        editingTimelineMidiMarkerIndex = null
        editingTimelineLightCueTimeMs = null
        showLightGenerationDialog = false
        timelineLightPreviewPositionMs = null
    }
    LaunchedEffect(tempo, showMixScreen) {
        if (!EditionConfig.isLite || !showMixScreen) {
            liteTrackMixTempo = tempo
        }
    }
    LaunchedEffect(pitchSemi, showMixScreen) {
        if (!EditionConfig.isLite || !showMixScreen) {
            liteTrackMixPitchSemi = pitchSemi
        }
    }

    var rawLyricsText by remember(currentTrackUri) { mutableStateOf("") }
    var editingLines by remember(currentTrackUri) { mutableStateOf<List<LrcLine>>(emptyList()) }
    var editingLinesDirty by remember(currentTrackUri) { mutableStateOf(false) }
    var currentEditTab by rememberSaveable(currentTrackUri) { mutableStateOf(0) }
    var showUnsavedLyricsDialog by remember { mutableStateOf(false) }
    var saveAndCloseRequestToken by remember { mutableIntStateOf(0) }
    val inlineLrcTimeTagRegex = remember { Regex("""\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?]""") }

    LaunchedEffect(currentTrackUri) {
        isEditingLyrics = false
        editingTrackUri = null
        showUnsavedLyricsDialog = false
        isEditingTimeline = openGridSetupOnEntry
        startTimelineInGridSetup = openGridSetupOnEntry
        editingTimelineMidiMarkerIndex = null
        editingTimelineLightCueTimeMs = null
        showLightGenerationDialog = false
    }

    LaunchedEffect(requestedNavigationToken) {
        if (requestedNavigationTarget == "grid_setup" && !currentTrackUri.isNullOrBlank()) {
            startTimelineInGridSetup = true
            isEditingTimeline = true
        }
    }

    fun updateResolvedLyricsFileName(newValue: String?, reason: String) {
        Log.d(
            "LrcDebug",
            "RESOLVED_LYRICS_FILENAME_UPDATE reason=$reason old=$resolvedLyricsLrcFileName new=$newValue"
        )
        resolvedLyricsLrcFileName = newValue
    }

    fun plainLyricsText(lines: List<LrcLine>): String =
        lines.joinToString("\n") { line -> line.text.replace(inlineLrcTimeTagRegex, "").trim() }

    fun editorLyricsText(lines: List<LrcLine>): String =
        if (lines.any { it.timeMs > 0L }) linesToLrcText(lines) else plainLyricsText(lines)

    fun closeLyricsEditorImmediately() {
        showUnsavedLyricsDialog = false
        isEditingLyrics = false
        editingTrackUri = null
    }

    fun requestCloseLyricsEditor() {
        if (editingLinesDirty) {
            showUnsavedLyricsDialog = true
        } else {
            closeLyricsEditorImmediately()
        }
    }

    fun showAccordsTrackChangedBlockedToast() {
        Toast.makeText(
            context,
            sAccordsTrackChangedBlocked,
            Toast.LENGTH_SHORT
        ).show()
    }

    fun showAccordsIoFailureToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun persistTimelineMarkers(
        updatedMarkers: List<TimelineMarker>,
        previousMarkers: List<TimelineMarker>
    ) {
        val trackUriForPersistence = currentTrackUri
        timelineMarkers = updatedMarkers
        scope.launch {
            val saved = persistSmpTimelineMarkersForTrack(
                context = context,
                trackUriString = trackUriForPersistence,
                markers = updatedMarkers
            )
            if (!saved) {
                timelineMarkers = previousMarkers
                Toast.makeText(context, sTimelineSaveFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addTimelineMarkerAtPosition(
        label: String,
        targetPositionMs: Long,
        kind: TimelineMarkerKind = TimelineMarkerKind.TEXT,
        durationMs: Long? = null
    ) {
        if (!isCurrentTrackSmp) return
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        val normalizedDurationMs = if (kind == TimelineMarkerKind.NOTE) {
            durationMs?.coerceAtLeast(1L) ?: DEFAULT_TIMELINE_NOTE_DURATION_MS
        } else {
            null
        }

        val previousMarkers = timelineMarkers
        val updatedMarkers = (timelineMarkers + TimelineMarker(
            timeMs = targetPositionMs.coerceAtLeast(0L),
            label = trimmed,
            kind = kind,
            durationMs = normalizedDurationMs
        )).sortedWith(
            compareBy<TimelineMarker> { it.timeMs }
                .thenBy { it.label }
        )
        persistTimelineMarkers(updatedMarkers, previousMarkers)
    }

    fun addTimelineMarker(
        label: String,
        kind: TimelineMarkerKind = TimelineMarkerKind.TEXT,
        durationMs: Long? = null
    ) {
        addTimelineMarkerAtPosition(
            label = label,
            targetPositionMs = getPositionMs().coerceAtLeast(0L),
            kind = kind,
            durationMs = durationMs
        )
    }

    fun addTypedTimelineMarker(kind: TimelineMarkerKind) {
        if (kind == TimelineMarkerKind.TEXT) return
        addTimelineMarkerAtPosition(
            label = kind.defaultLabel,
            targetPositionMs = getPositionMs().coerceAtLeast(0L),
            kind = kind,
            durationMs = if (kind == TimelineMarkerKind.NOTE) DEFAULT_TIMELINE_NOTE_DURATION_MS else null
        )
    }

    fun addTypedTimelineMarkerAtPosition(kind: TimelineMarkerKind, targetPositionMs: Long) {
        if (kind == TimelineMarkerKind.TEXT) return
        addTimelineMarkerAtPosition(
            label = kind.defaultLabel,
            targetPositionMs = targetPositionMs,
            kind = kind,
            durationMs = if (kind == TimelineMarkerKind.NOTE) DEFAULT_TIMELINE_NOTE_DURATION_MS else null
        )
    }

    fun addTimelinePaletteTag(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        timelinePalette = TimelinePaletteStore.save(
            context = context,
            tags = timelinePalette + trimmed
        )
    }

    fun refreshTimelineLightCues(trackUriString: String?, syncPositionMs: Long? = null) {
        if (trackUriString.isNullOrBlank()) {
            timelineLightCues = emptyList()
            hasLightCues = false
            timelineLightPreviewPositionMs = null
            return
        }
        scope.launch {
            val refreshed = loadSmpLightCuesForTrack(context, trackUriString)
            timelineLightCues = refreshed
            hasLightCues = refreshed.isNotEmpty()
            val editedCue = syncPositionMs?.let { targetTimeMs ->
                refreshed.firstOrNull { cue -> cue.timeMs == targetTimeMs.coerceAtLeast(0L) }
            }
            val targetSyncPositionMs = if (syncPositionMs != null) {
                val previewOffsetMs = editedCue
                    ?.fadeMs
                    ?.coerceAtLeast(0L)
                    ?.takeIf { it > 0L }
                    ?.let { fadeMs -> min(fadeMs / 2L, 1_500L) }
                    ?: 0L
                syncPositionMs.coerceAtLeast(0L) + previewOffsetMs
            } else {
                getPositionMs().coerceAtLeast(0L)
            }
            timelineLightPreviewPositionMs = targetSyncPositionMs.takeIf {
                isEditingTimeline && !isPlaying && syncPositionMs != null
            }
            LightCueDispatcher.syncToPosition(trackUriString, targetSyncPositionMs)
        }
    }

    fun addTimelineDmxCueAtPosition(targetPositionMs: Long) {
        val trackUri = currentTrackUri ?: return
        val cue = LightCue(
            timeMs = targetPositionMs.coerceAtLeast(0L),
            action = LightAction.Color(argb = DEFAULT_TIMELINE_LIGHT_CUE_ARGB),
            intensity = 1f,
            fadeMs = 0L
        )
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                SmpLightCueBridge.upsertCueAtTime(
                    context = context,
                    trackUriString = trackUri,
                    cue = cue
                ) == true
            }
            if (saved) {
                refreshTimelineLightCues(trackUri)
            } else {
                Toast.makeText(context, sTimelineSaveFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun renameTimelineMarker(index: Int, label: String, durationMs: Long?) {
        if (!isCurrentTrackSmp || index !in timelineMarkers.indices) return
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return

        val marker = timelineMarkers[index]
        val normalizedDurationMs = if (marker.kind == TimelineMarkerKind.NOTE) {
            durationMs?.coerceAtLeast(1L) ?: DEFAULT_TIMELINE_NOTE_DURATION_MS
        } else {
            null
        }
        val previousMarkers = timelineMarkers
        val updatedMarkers = timelineMarkers
            .toMutableList()
            .apply {
                this[index] = this[index].copy(
                    label = trimmed,
                    durationMs = normalizedDurationMs
                )
            }
            .sortedWith(
                compareBy<TimelineMarker> { it.timeMs }
                    .thenBy { it.label }
            )
        persistTimelineMarkers(updatedMarkers, previousMarkers)
    }

    fun deleteTimelineMarker(index: Int) {
        if (!isCurrentTrackSmp || index !in timelineMarkers.indices) return

        val previousMarkers = timelineMarkers
        val updatedMarkers = timelineMarkers.toMutableList().apply {
            removeAt(index)
        }
        persistTimelineMarkers(updatedMarkers, previousMarkers)
    }

    val latestCurrentTrackUri by rememberUpdatedState(currentTrackUri)
    val latestParsedLines by rememberUpdatedState(parsedLines)

    val accordsWriteQueue = remember(scope, context) {
        LatestAccordsWriteQueue<AccordsWriteRequest>(
            scope = scope
        ) { request ->
            val ioResult = runCatching {
                runAccordsSaveIo(
                    writeAccords = {
                        writeAccordsToSplByTrackUri(
                            context = context,
                            trackUriString = request.trackUriString,
                            preferredLrcFileName = request.preferredLrcFileName,
                            lines = request.lines
                        )
                    },
                    ensureLyricsTwin = { writtenName ->
                        val ensureResult = ensureLyricsFileExistsForTrack(
                            context = context,
                            trackUriString = request.trackUriString,
                            preferredLrcFileName = writtenName
                        )
                        Log.d(
                            PLAYER_LRC_TAG,
                            "ensure_lyrics_twin trackUri=${request.trackUriString} target=$writtenName result=$ensureResult"
                        )
                        ensureResult
                    }
                )
            }.getOrElse {
                com.patrick.lrcreader.core.AccordsIoResult(
                    success = false,
                    stage = "exception:${it::class.simpleName ?: "Unknown"}"
                )
            }

            scope.launch {
                if (!ioResult.success) {
                    Log.e(
                        "LrcDebug",
                        buildAccordsIoFailureLog(
                            action = "save",
                            trackUri = request.trackUriString,
                            io = ioResult
                        )
                    )
                }

                if (latestCurrentTrackUri != request.trackUriString) {
                    return@launch
                }

                val previous = AccordsUiTruth(
                    lines = persistedChordLines,
                    hasSource = persistedHasChordsSource
                )
                val resolved = resolveAccordsUiTruthAfterSave(
                    previous = previous,
                    requestedLines = request.lines,
                    io = ioResult
                )

                persistedChordLines = resolved.lines
                persistedHasChordsSource = resolved.hasSource
                parsedChordLines = resolved.lines
                hasChordsSource = resolved.hasSource

                if (!ioResult.success) {
                    buildAccordsIoFailureFeedback(
                        context = context,
                        actionLabel = sAccordsActionSave,
                        io = ioResult
                    )
                        ?.let { showAccordsIoFailureToast(it) }
                }
            }
        }
    }
    DisposableEffect(accordsWriteQueue) {
        onDispose { accordsWriteQueue.close() }
    }

    fun seedEditingLinesIfBetter(lines: List<LrcLine>) {
        if (editingLinesDirty) return
        val currentHasTags = editingLines.any { it.timeMs > 0L }
        val incomingHasTags = lines.any { it.timeMs > 0L }

        val shouldSeed = editingLines.isEmpty() || (!currentHasTags && incomingHasTags)
        if (shouldSeed) {
            editingLines = lines
        }
    }

    fun applyCachedLyrics(trackUriString: String, entry: LyricsCacheEntry) {
        Log.d(
            "LrcDebug",
            "LYRICS_CACHE_HIT source=${entry.source} fileName=${entry.resolvedLyricsFileName} path=${entry.debugPath} sourceType=${entry.sourceType}"
        )
        LyricsPerf.mark(
            trackUriString,
            "cache_hit",
            "source=${entry.source} file=${entry.resolvedLyricsFileName} lines=${entry.parsedLines.size} loadedAtMs=${entry.loadedAtMs}"
        )
        onParsedLinesChange(entry.parsedLines)
        LyricsPerf.mark(
            trackUriString,
            "ui_apply",
            "source=CACHE lines=${entry.parsedLines.size}"
        )
        rawLyricsText = entry.parsedLines.joinToString("\n") { it.text }
        seedEditingLinesIfBetter(entry.parsedLines)
        hasLyricsSource = true
        Log.d("LrcDebug", "LYRICS_SOURCE_TYPE ${entry.sourceType ?: "cached"}")
        updateResolvedLyricsFileName(
            entry.resolvedLyricsFileName,
            reason = "source=CACHE origin=${entry.source} path=${entry.debugPath}"
        )
    }

    // 🔁 reload paroles (priorité : SYLT -> EDIT (LrcStorage) -> SIDECAR -> USLT)
    LaunchedEffect(currentTrackUri) {
        if (isEditingLyrics) {
            lyricsResolutionCompleted = true
            return@LaunchedEffect
        }
        lyricsResolutionCompleted = false
        lyricsResolving = true
        val resolveStartMs = android.os.SystemClock.elapsedRealtime()
        var resolvedSource = "SKIPPED"
        try {
        if (currentTrackUri == null) {
            onParsedLinesChange(emptyList())
            rawLyricsText = ""
            editingLines = emptyList()
            hasLyricsSource = false
            updateResolvedLyricsFileName(null, "track=null")
            return@LaunchedEffect
        }

        val cacheScopeKey = LrcStorage.currentWorkspaceScopeKey(context)
        LyricsMemoryCache.updateScope(cacheScopeKey)
        val cachedLyrics = LyricsMemoryCache.get(currentTrackUri)
        if (cachedLyrics != null) {
            applyCachedLyrics(currentTrackUri, cachedLyrics)
            resolvedSource = "CACHE"
            return@LaunchedEffect
        }

        Log.d("LrcDebug", "LYRICS_UI_RESET owner=PlayerScreen uri=$currentTrackUri reason=track_change")
        onParsedLinesChange(emptyList())

        val trackUri = runCatching { Uri.parse(currentTrackUri) }.getOrNull()
        val audioBase = baseNameFromTrackUriString(currentTrackUri)
        val preferExternalLyricsFirst = LrcStorage.isWorkspaceSaf(context)
        resolvedSource = "NONE"
        Log.d("LrcDebug", "TRACK uriString=$currentTrackUri")
        Log.d("LrcDebug", "TRACK uriParsed=$trackUri scheme=${trackUri?.scheme} authority=${trackUri?.authority}")
        Log.d("LrcDebug", "TRACK audioBaseName=$audioBase")
        LyricsPerf.mark(
            currentTrackUri,
            "player_resolution_start",
            "preferExternalFirst=$preferExternalLyricsFirst"
        )
        Log.d(
            "LrcDebug",
            "LYRICS_RESOLUTION_ORDER mode=${if (preferExternalLyricsFirst) "SAF_EXTERNAL_FIRST" else "DEFAULT"} order=${if (preferExternalLyricsFirst) "LRC_STORAGE->SIDECAR->SYLT->USLT" else "SYLT->LRC_STORAGE->SIDECAR->USLT"}"
        )

        fun cacheResolvedLyrics(
            parsed: List<LrcLine>,
            resolvedLyricsFileName: String?,
            source: String,
            sourceType: String?,
            debugPath: String?
        ) {
            if (parsed.isEmpty()) return
            LyricsMemoryCache.put(
                trackUriString = currentTrackUri,
                parsedLines = parsed,
                resolvedLyricsFileName = resolvedLyricsFileName,
                source = source,
                sourceType = sourceType,
                debugPath = debugPath
            )
            LyricsPerf.mark(
                currentTrackUri,
                "cache_store",
                "source=$source file=$resolvedLyricsFileName lines=${parsed.size}"
            )
        }

        fun shouldSkipEmptyUiApply(source: String): Boolean {
            val existingLines = latestParsedLines.size
            if (existingLines <= 0) return false
            LyricsPerf.mark(
                currentTrackUri,
                "ui_apply_skip_empty_over_non_empty",
                "source=$source existingLines=$existingLines"
            )
            return true
        }

        suspend fun tryStoredLyrics(): Boolean {
            val storedLoadStartMs = android.os.SystemClock.elapsedRealtime()
            Log.d(PLAYER_LRC_TAG, "load_lyrics trackUri=$currentTrackUri")
            val stored = withContext(Dispatchers.IO) {
                LrcStorage.loadForTrack(context, currentTrackUri)
            }
            LyricsPerf.mark(
                currentTrackUri,
                "stored_load_done",
                "ms=${android.os.SystemClock.elapsedRealtime() - storedLoadStartMs} found=${!stored.isNullOrBlank()} len=${stored?.length ?: -1}"
            )
            Log.d("LrcDebug", "STORED found=${!stored.isNullOrBlank()}")
            if (stored.isNullOrBlank()) return false

            val originResolveStartMs = android.os.SystemClock.elapsedRealtime()
            val storedOrigin = withContext(Dispatchers.IO) {
                runCatching { LrcStorage.resolveOriginForTrack(context, currentTrackUri) }.getOrNull()
            }
            LyricsPerf.mark(
                currentTrackUri,
                "stored_origin_done",
                "ms=${android.os.SystemClock.elapsedRealtime() - originResolveStartMs} source=${storedOrigin?.source} file=${storedOrigin?.fileName}"
            )
            Log.d(
                "LrcDebug",
                "LYRICS_SOURCE source=LRC_STORAGE origin=${storedOrigin?.source} fileName=${storedOrigin?.fileName} path=${storedOrigin?.debugPath}"
            )
            Log.d("LrcDebug", "LYRICS_SOURCE_TYPE ${storedOrigin?.sourceType ?: "canonical"}")
            val parseStartMs = android.os.SystemClock.elapsedRealtime()
            val parsed = parseLrc(stored)
            LyricsPerf.mark(
                currentTrackUri,
                "parse_done",
                "source=LRC_STORAGE ms=${android.os.SystemClock.elapsedRealtime() - parseStartMs} lines=${parsed.size} chars=${stored.length}"
            )
            onParsedLinesChange(parsed)
            LyricsPerf.mark(
                currentTrackUri,
                "ui_apply",
                "source=LRC_STORAGE lines=${parsed.size}"
            )
            rawLyricsText = parsed.joinToString("\n") { it.text }
            seedEditingLinesIfBetter(parsed)
            hasLyricsSource = true
            resolvedSource = "LRC_STORAGE"
            cacheResolvedLyrics(
                parsed = parsed,
                resolvedLyricsFileName = storedOrigin?.fileName,
                source = "LRC_STORAGE",
                sourceType = storedOrigin?.sourceType ?: "canonical",
                debugPath = storedOrigin?.debugPath
            )
            updateResolvedLyricsFileName(
                storedOrigin?.fileName,
                reason = "source=LRC_STORAGE path=${storedOrigin?.debugPath}"
            )
            return true
        }

        suspend fun trySidecarLyrics(): Boolean {
            val sidecarReadStartMs = android.os.SystemClock.elapsedRealtime()
            val sidecarLrcResult: LrcTextWithFileName? = if (trackUri != null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        readSidecarLrcSmart(
                            context = context,
                            trackUriString = currentTrackUri
                        )
                    }.getOrNull()
                }
            } else null
            LyricsPerf.mark(
                currentTrackUri,
                "sidecar_read_done",
                "ms=${android.os.SystemClock.elapsedRealtime() - sidecarReadStartMs} found=${sidecarLrcResult != null} len=${sidecarLrcResult?.text?.length ?: -1}"
            )

            Log.d("LrcDebug", "SIDECAR found=${sidecarLrcResult != null}")
            if (sidecarLrcResult == null) return false

            Log.d(
                "LrcDebug",
                "LYRICS_SOURCE source=SIDECAR fileName=${sidecarLrcResult.fileName} path=${sidecarLrcResult.debugPath}"
            )
            Log.d("LrcDebug", "LYRICS_SOURCE_TYPE legacy")
            val parseStartMs = android.os.SystemClock.elapsedRealtime()
            val parsed = if (sidecarLrcResult.text.isNotBlank()) parseLrc(sidecarLrcResult.text) else emptyList()
            LyricsPerf.mark(
                currentTrackUri,
                "parse_done",
                "source=SIDECAR ms=${android.os.SystemClock.elapsedRealtime() - parseStartMs} lines=${parsed.size} chars=${sidecarLrcResult.text.length}"
            )
            if (parsed.isEmpty() && shouldSkipEmptyUiApply("SIDECAR")) {
                resolvedSource = "SIDECAR_EMPTY_SKIPPED"
                return false
            }
            onParsedLinesChange(parsed)
            LyricsPerf.mark(
                currentTrackUri,
                "ui_apply",
                "source=SIDECAR lines=${parsed.size}"
            )
            rawLyricsText = parsed.joinToString("\n") { it.text }
            seedEditingLinesIfBetter(parsed)
            hasLyricsSource = true
            resolvedSource = "SIDECAR"
            cacheResolvedLyrics(
                parsed = parsed,
                resolvedLyricsFileName = sidecarLrcResult.fileName,
                source = "SIDECAR",
                sourceType = "legacy",
                debugPath = sidecarLrcResult.debugPath
            )
            updateResolvedLyricsFileName(
                sidecarLrcResult.fileName,
                reason = "source=SIDECAR path=${sidecarLrcResult.debugPath}"
            )
            return true
        }

        suspend fun trySyltLyrics(): Boolean {
            val syltReadStartMs = android.os.SystemClock.elapsedRealtime()
            val syltLrcText: String? = if (trackUri != null) {
                withContext(Dispatchers.IO) {
                    runCatching { readSyltAsLrcFromUri(context, trackUri) }.getOrNull()
                }
            } else null
            LyricsPerf.mark(
                currentTrackUri,
                "embedded_sylt_read_done",
                "ms=${android.os.SystemClock.elapsedRealtime() - syltReadStartMs} found=${!syltLrcText.isNullOrBlank()} len=${syltLrcText?.length ?: -1}"
            )
            if (syltLrcText.isNullOrBlank()) return false

            val parseStartMs = android.os.SystemClock.elapsedRealtime()
            val parsed = parseLrc(syltLrcText)
            LyricsPerf.mark(
                currentTrackUri,
                "parse_done",
                "source=SYLT ms=${android.os.SystemClock.elapsedRealtime() - parseStartMs} lines=${parsed.size} chars=${syltLrcText.length}"
            )
            onParsedLinesChange(parsed)
            LyricsPerf.mark(
                currentTrackUri,
                "ui_apply",
                "source=SYLT lines=${parsed.size}"
            )
            rawLyricsText = parsed.joinToString("\n") { it.text }
            seedEditingLinesIfBetter(parsed)
            hasLyricsSource = true
            Log.d("LrcDebug", "LYRICS_SOURCE_TYPE embedded")
            resolvedSource = "SYLT"
            cacheResolvedLyrics(
                parsed = parsed,
                resolvedLyricsFileName = null,
                source = "SYLT",
                sourceType = "embedded",
                debugPath = null
            )
            updateResolvedLyricsFileName(null, "source=SYLT")
            return true
        }

        suspend fun tryUsltLyrics(): Boolean {
            val usltReadStartMs = android.os.SystemClock.elapsedRealtime()
            val usltText: String? = if (trackUri != null) {
                withContext(Dispatchers.IO) {
                    runCatching { readUsltFromUri(context, trackUri) }.getOrNull()
                }
            } else null
            LyricsPerf.mark(
                currentTrackUri,
                "embedded_uslt_read_done",
                "ms=${android.os.SystemClock.elapsedRealtime() - usltReadStartMs} found=${!usltText.isNullOrBlank()} len=${usltText?.length ?: -1}"
            )
            if (usltText.isNullOrBlank()) return false

            val parseStartMs = android.os.SystemClock.elapsedRealtime()
            val parsed = parseLrc(usltText)
            LyricsPerf.mark(
                currentTrackUri,
                "parse_done",
                "source=USLT ms=${android.os.SystemClock.elapsedRealtime() - parseStartMs} lines=${parsed.size} chars=${usltText.length}"
            )
            onParsedLinesChange(parsed)
            LyricsPerf.mark(
                currentTrackUri,
                "ui_apply",
                "source=USLT lines=${parsed.size}"
            )
            rawLyricsText = parsed.joinToString("\n") { it.text }
            seedEditingLinesIfBetter(parsed)
            hasLyricsSource = true
            Log.d("LrcDebug", "LYRICS_SOURCE_TYPE embedded")
            resolvedSource = "USLT"
            cacheResolvedLyrics(
                parsed = parsed,
                resolvedLyricsFileName = null,
                source = "USLT",
                sourceType = "embedded",
                debugPath = null
            )
            updateResolvedLyricsFileName(null, "source=USLT")
            return true
        }

        val resolved = if (preferExternalLyricsFirst) {
            tryStoredLyrics() || trySidecarLyrics() || trySyltLyrics() || tryUsltLyrics()
        } else {
            trySyltLyrics() || tryStoredLyrics() || trySidecarLyrics() || tryUsltLyrics()
        }
        if (resolved) {
            return@LaunchedEffect
        }

        if (shouldSkipEmptyUiApply("NONE")) {
            resolvedSource = "NONE_EMPTY_SKIPPED"
            return@LaunchedEffect
        }
        onParsedLinesChange(emptyList())
        rawLyricsText = ""
        seedEditingLinesIfBetter(emptyList())
        hasLyricsSource = false
        Log.d("LrcDebug", "LYRICS_SOURCE source=NONE")
        updateResolvedLyricsFileName(null, "source=NONE")
        } finally {
            LyricsPerf.mark(
                currentTrackUri,
                "player_resolution_done",
                "ms=${android.os.SystemClock.elapsedRealtime() - resolveStartMs} source=$resolvedSource"
            )
            lyricsResolving = false
            lyricsResolutionCompleted = true
        }
    }

    LaunchedEffect(currentTrackUri, parsedLines, selectedViewMode) {
        if (lyricsUiVisibleLogged) return@LaunchedEffect
        if (currentTrackUri == null) return@LaunchedEffect
        if (selectedViewMode != LyricsViewMode.LYRICS) return@LaunchedEffect
        if (parsedLines.isEmpty()) return@LaunchedEffect
        lyricsUiVisibleLogged = true
        LyricsPerf.mark(
            currentTrackUri,
            "ui_visible",
            "mode=$selectedViewMode lines=${parsedLines.size}"
        )
    }

    // 🔁 reload accords dédiés (BackingTracks/Accords/<base>.lrc)
    LaunchedEffect(currentTrackUri, resolvedLyricsLrcFileName, selectedViewMode) {
        if (currentTrackUri == null) {
            parsedChordLines = emptyList()
            hasChordsSource = false
            persistedChordLines = emptyList()
            persistedHasChordsSource = false
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
        persistedChordLines = parsed
        persistedHasChordsSource = raw != null
        Log.d(
            "LrcDebug",
            "ACCORDS_EFFECT_DONE uri=$currentTrackUri parsedCount=${parsed.size} hasChordsSource=$hasChordsSource"
        )
        chordsLoading = false
    }

    val activeDisplayLines = if (selectedViewMode == LyricsViewMode.CHORDS) parsedChordLines else parsedLines
    val hasLyricsMode = hasLyricsSource || parsedLines.isNotEmpty()
    val hasChordsMode = hasChordsSource || parsedChordLines.isNotEmpty()
    val showViewToggle = currentTrackUri != null
    val canSelectChordsMode = currentTrackUri != null

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

    LaunchedEffect(selectedViewMode, parsedLines, parsedChordLines, userOffsetMs) {
        if (selectedViewMode != LyricsViewMode.LYRICS) {
            lastMidiIndex = -1
        }
        recomputeCurrentIndexForActiveView()
    }

    LaunchedEffect(currentTrackUri) {
        currentLrcIndex = 0
        if (selectedViewMode == LyricsViewMode.LYRICS) {
            runCatching { listState.scrollToItem(0) }
        }
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
        timelineLightPreviewPositionMs = null
        if (currentTrackUri != null && hasLightCues) {
            LightCueDispatcher.syncToPosition(
                trackUri = currentTrackUri,
                positionMs = seekPos.toLong()
            )
        }

        if (!isPlaying) {
            PlaybackCoordinator.onPlayerStart()
            onIsPlayingChange(true)
        }
        centerCurrentLineLazy(listState)
    }


    // ---------- Suivi lecture + index ligne courante + MIDI ----------
    LaunchedEffect(isPlaying, activeDisplayLines, selectedViewMode, userOffsetMs, currentTrackUri) {
        while (true) {
            val d = getEffectiveDurationMs().toInt()
            if (d > 0) durationMs = d

            val p = getPositionMs().toInt()
            if (!isDragging) positionMs = p

            if (currentTrackUri != null && isCurrentTrackSmp) {
                MidiCueDispatcher.onSmpPlaybackPosition(
                    context = context,
                    trackUri = currentTrackUri,
                    positionMs = p.toLong(),
                    isPlaying = isPlaying
                )
            }

            if (activeDisplayLines.isNotEmpty()) {
                val totalOffsetMs = lyricsDelayMs + userOffsetMs
                val posMs = (p.toLong() - totalOffsetMs).coerceAtLeast(0L)
                val newIndex = findActiveLrcIndex(activeDisplayLines, posMs)
                if (newIndex >= 0 && newIndex != currentLrcIndex) {
                    currentLrcIndex = newIndex
                }

                if (
                    !isCurrentTrackSmp &&
                    selectedViewMode == LyricsViewMode.LYRICS &&
                    currentTrackUri != null &&
                    newIndex >= 0 &&
                    newIndex != lastMidiIndex
                ) {
                    lastMidiIndex = newIndex
                    val cue = MidiCueDispatcher.resolveCueForTrack(
                        context = context,
                        trackUri = currentTrackUri,
                        lines = activeDisplayLines,
                        lineIndex = newIndex
                    )
                    Log.d(
                        midiCueTraceTag,
                        "PLAYER_RESOLVE track=$currentTrackUri newIndex=$newIndex lastMidiIndex=$lastMidiIndex positionMs=${getPositionMs()} cue=${cue?.let { "{lineIndex=${it.lineIndex},channel=${it.channel},program=${it.program}}" } ?: "null"}"
                    )
                    MidiCueDispatcher.onResolvedCueChanged(
                        trackUri = currentTrackUri,
                        lineIndex = newIndex,
                        cue = cue,
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

    LaunchedEffect(isPlaying, currentTrackUri, hasLightCues, timelineLightPreviewPositionMs) {
        val trackUri = currentTrackUri ?: return@LaunchedEffect
        if (!hasLightCues) return@LaunchedEffect

        if (!isPlaying) {
            val pausedPositionMs = timelineLightPreviewPositionMs ?: getPositionMs().coerceAtLeast(0L)
            LightCueDispatcher.advance(
                trackUri = trackUri,
                positionMs = pausedPositionMs,
                isPlaying = false
            )
            return@LaunchedEffect
        }

        while (true) {
            val runtimePositionMs = timelineLightPreviewPositionMs ?: getPositionMs().coerceAtLeast(0L)
            LightCueDispatcher.advance(
                trackUri = trackUri,
                positionMs = runtimePositionMs,
                isPlaying = true
            )
            delay(DMX_PLAYBACK_POLL_INTERVAL_MS)
        }
    }

    LaunchedEffect(midiMonitorEvent, currentTrackUri) {
        Log.d(
            midiCueTraceTag,
            "PLAYER_MONITOR track=${currentTrackUri ?: "null"} monitor=${midiMonitorEvent?.let { "{track=${it.trackUri},channel=${it.channel},program=${it.program},triggeredAtMs=${it.triggeredAtMs}}" } ?: "null"}"
        )
    }

    // ---------- Autoswitch playlist (-10s) ----------
    LaunchedEffect(durationMs, positionMs, hasRequestedPlaylist, currentTrackUri, isEditingLyrics, isEditingTimeline, autoReturnArmed) {
        val enabled = AutoReturnPrefs.isEnabled(context)
        if (enabled &&
            autoReturnArmed &&
            !isEditingLyrics &&
            !isEditingTimeline &&
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
                onCloseEditor = { requestCloseLyricsEditor() },
                rawLyricsText = rawLyricsText,
                onRawLyricsTextChange = {
                    rawLyricsText = it
                    editingLinesDirty = true
                },
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
                    rawLyricsText = editorLyricsText(sorted)
                    editingLines = sorted
                    editingLinesDirty = false
                    if (editingTargetMode == LyricsViewMode.LYRICS) {
                        onParsedLinesChange(sorted)
                        hasLyricsSource = sorted.isNotEmpty()
                    }
                    closeLyricsEditorImmediately()
                },
                onPersistSucceeded = { persisted ->
                    rawLyricsText = editorLyricsText(persisted)
                    editingLines = persisted
                    editingLinesDirty = false
                },
                onPersistLines = persistLines@{ lines ->
                    if (editingTargetMode == LyricsViewMode.CHORDS) {
                        val lockedTrackUri = resolveAccordsEditTargetTrack(
                            lockedTrackUri = editingTrackUri,
                            currentTrackUri = currentTrackUri
                        )
                        if (lockedTrackUri == null) {
                            showAccordsTrackChangedBlockedToast()
                            Log.w(
                                "LrcDebug",
                                "ACCORDS_SAVE_BLOCKED trackChanged editingTrackUri=$editingTrackUri currentTrackUri=$currentTrackUri"
                            )
                            return@persistLines false
                        }

                        val isSmpAccordsTrack = LrcStorage.isSmpRuntimeTrack(context, lockedTrackUri)
                        val saveOutcome = withContext(Dispatchers.IO) {
                            runCatching {
                                val migration = if (isSmpAccordsTrack) {
                                    null
                                } else {
                                    ensureSmpTrackForLyricsSave(lockedTrackUri)
                                }
                                if (!isSmpAccordsTrack && migration == null) {
                                    return@runCatching AccordsPersistOutcome(
                                        trackUriString = lockedTrackUri,
                                        migration = null,
                                        io = com.patrick.lrcreader.core.AccordsIoResult(
                                            success = false,
                                            stage = "autoMigrate"
                                        ),
                                        reloadedText = null
                                    )
                                }

                                val targetTrackUri = migration?.trackUriString ?: lockedTrackUri
                                val ioResult = runAccordsSaveIo(
                                    writeAccords = {
                                        writeAccordsToSplByTrackUri(
                                            context = context,
                                            trackUriString = targetTrackUri,
                                            preferredLrcFileName = editingResolvedLrcFileName,
                                            lines = lines.toList()
                                        )
                                    },
                                    ensureLyricsTwin = { writtenName ->
                                        val ensureResult = ensureLyricsFileExistsForTrack(
                                            context = context,
                                            trackUriString = targetTrackUri,
                                            preferredLrcFileName = writtenName
                                        )
                                        Log.d(
                                            "LrcDebug",
                                            "ACCORDS_ENSURE_LYRICS trackUri=$targetTrackUri target=$writtenName result=$ensureResult"
                                        )
                                        ensureResult
                                    }
                                )
                                if (!ioResult.success) {
                                    return@runCatching AccordsPersistOutcome(
                                        trackUriString = targetTrackUri,
                                        migration = migration,
                                        io = ioResult,
                                        reloadedText = null
                                    )
                                }

                                AccordsPersistOutcome(
                                    trackUriString = targetTrackUri,
                                    migration = migration,
                                    io = ioResult,
                                    reloadedText = readAccordsFromSplByTrackUri(
                                        context = context,
                                        trackUriString = targetTrackUri,
                                        preferredLrcFileName = editingResolvedLrcFileName
                                    )
                                )
                            }.getOrElse {
                                AccordsPersistOutcome(
                                    trackUriString = lockedTrackUri,
                                    migration = null,
                                    io = com.patrick.lrcreader.core.AccordsIoResult(
                                        success = false,
                                        stage = "exception:${it::class.simpleName ?: "Unknown"}"
                                    ),
                                    reloadedText = null
                                )
                            }
                        }
                        if (!saveOutcome.io.success) {
                            Log.e(
                                "LrcDebug",
                                buildAccordsIoFailureLog(
                                    action = "save",
                                    trackUri = saveOutcome.trackUriString,
                                    io = saveOutcome.io
                                )
                            )
                            buildAccordsIoFailureFeedback(
                                context = context,
                                actionLabel = sAccordsActionSave,
                                io = saveOutcome.io
                            )?.let { showAccordsIoFailureToast(it) }
                            return@persistLines false
                        }

                        val reloadedRaw = saveOutcome.reloadedText
                        if (reloadedRaw == null) {
                            Log.e("LrcDebug", "ACCORDS_SAVE_RELOAD_FAILED trackUri=${saveOutcome.trackUriString}")
                            showAccordsIoFailureToast(
                                buildAccordsIoFailureFeedback(
                                    action = "save",
                                    io = com.patrick.lrcreader.core.AccordsIoResult(
                                        success = false,
                                        stage = "reloadAccords"
                                    )
                                ) ?: sAccordsActionSave
                            )
                            return@persistLines false
                        }

                        val reloadedParsed = if (reloadedRaw.isBlank()) emptyList() else parseLrc(reloadedRaw)
                        persistedChordLines = reloadedParsed
                        persistedHasChordsSource = true
                        parsedChordLines = reloadedParsed
                        hasChordsSource = true
                        saveOutcome.migration?.let {
                            editingTrackUri = it.trackUriString
                            onTrackPromotedToSmp(it)
                        }
                        if (selectedViewMode == LyricsViewMode.CHORDS) {
                            recomputeCurrentIndexForActiveView()
                        }
                        return@persistLines true
                    }

                    currentTrackUri?.let { trackUri ->
                        val preferredAtSave = resolvedLyricsLrcFileName
                        val saveStartMs = android.os.SystemClock.elapsedRealtime()
                        val isAlreadySmpTrack = LrcStorage.isSmpRuntimeTrack(context, trackUri)
                        LyricsMemoryCache.invalidate(trackUri)
                        Log.d(
                            "LrcDebug",
                            "LYRICS_SAVE_CONFIRMED start trackUri=$trackUri lines=${lines.size}"
                        )
                        val saveOutcome = withContext(Dispatchers.IO) {
                            runCatching {
                                val migration = if (isAlreadySmpTrack) {
                                    null
                                } else {
                                    ensureSmpTrackForLyricsSave(trackUri)
                                }
                                val targetTrackUri = migration?.trackUriString ?: trackUri
                                if (targetTrackUri != trackUri) {
                                    LyricsMemoryCache.invalidate(targetTrackUri)
                                }
                                Log.d(
                                    PLAYER_LRC_TAG,
                                    "save_lyrics trackUri=$targetTrackUri lines=${lines.size}"
                                )
                                val saved = LrcStorage.saveForTrack(
                                    context = context,
                                    trackUriString = targetTrackUri,
                                    lines = lines
                                )
                                if (!saved) {
                                    return@runCatching null
                                }

                                val resolvedFileName = LrcStorage.resolveOriginForTrack(
                                    context = context,
                                    trackUriString = targetTrackUri
                                )?.fileName ?: resolveExactLrcFileNameForTrack(
                                    context = context,
                                    trackUriString = targetTrackUri,
                                    preferredLrcFileName = preferredAtSave
                                )

                                if (!resolvedFileName.isNullOrBlank()) {
                                    ensureAccordsFileExistsForTrack(
                                        context = context,
                                        trackUriString = targetTrackUri,
                                        preferredLrcFileName = resolvedFileName
                                    )
                                }

                                LyricsPersistOutcome(
                                    trackUriString = targetTrackUri,
                                    migration = migration,
                                    resolvedFileName = resolvedFileName,
                                    reloadedText = LrcStorage.loadForTrack(context, targetTrackUri)
                                )
                            }.onFailure { error ->
                                Log.e(
                                    "LrcDebug",
                                    "LYRICS_SAVE_CONFIRMED failed trackUri=$trackUri",
                                    error
                                )
                            }.getOrNull()
                        }

                        if (saveOutcome == null || saveOutcome.reloadedText.isNullOrBlank()) {
                            Log.e("LrcDebug", "LYRICS_SAVE_CONFIRMED reload_failed trackUri=$trackUri")
                            return@persistLines false
                        }

                        saveOutcome.migration?.let { onTrackPromotedToSmp(it) }

                        if (
                            !saveOutcome.resolvedFileName.isNullOrBlank() &&
                            (latestCurrentTrackUri == trackUri || latestCurrentTrackUri == saveOutcome.trackUriString)
                        ) {
                            updateResolvedLyricsFileName(
                                saveOutcome.resolvedFileName,
                                "source=LYRICS_SAVE"
                            )
                        }

                        val elapsedMs = android.os.SystemClock.elapsedRealtime() - saveStartMs
                        Log.d(
                            "LrcDebug",
                            "LYRICS_SAVE_CONFIRMED end trackUri=${saveOutcome.trackUriString} originalTrackUri=$trackUri durationMs=$elapsedMs resolved=${saveOutcome.resolvedFileName} migrated=${saveOutcome.migration != null}"
                        )
                    }
                    true
                },
                onDeletePersisted = deletePersisted@{
                    if (editingTargetMode == LyricsViewMode.CHORDS) {
                        val lockedTrackUri = resolveAccordsEditTargetTrack(
                            lockedTrackUri = editingTrackUri,
                            currentTrackUri = currentTrackUri
                        )
                        if (lockedTrackUri == null) {
                            showAccordsTrackChangedBlockedToast()
                            Log.w(
                                "LrcDebug",
                                "ACCORDS_DELETE_BLOCKED trackChanged editingTrackUri=$editingTrackUri currentTrackUri=$currentTrackUri"
                            )
                            return@deletePersisted false
                        }

                        val isSmpAccordsTrack = LrcStorage.isSmpRuntimeTrack(context, lockedTrackUri)
                        if (isSmpAccordsTrack) {
                            val initialIo = withContext(Dispatchers.IO) {
                                runCatching {
                                    runAccordsDeleteIo(
                                        deleteAccords = {
                                            deleteAccordsFromSplByTrackUri(
                                                context = context,
                                                trackUriString = lockedTrackUri,
                                                preferredLrcFileName = editingResolvedLrcFileName
                                            )
                                        }
                                    )
                                }.getOrElse {
                                    com.patrick.lrcreader.core.AccordsIoResult(
                                        success = false,
                                        stage = "exception:${it::class.simpleName ?: "Unknown"}"
                                    )
                                }
                            }
                            val ioResult = if (initialIo.success) {
                                val remaining = withContext(Dispatchers.IO) {
                                    readAccordsFromSplByTrackUri(
                                        context = context,
                                        trackUriString = lockedTrackUri,
                                        preferredLrcFileName = editingResolvedLrcFileName
                                    )
                                }
                                if (remaining == null) {
                                    initialIo
                                } else {
                                    com.patrick.lrcreader.core.AccordsIoResult(
                                        success = false,
                                        stage = "reloadAccordsAfterDelete"
                                    )
                                }
                            } else {
                                initialIo
                            }

                            val previous = AccordsUiTruth(
                                lines = persistedChordLines,
                                hasSource = persistedHasChordsSource
                            )
                            val resolved = resolveAccordsUiTruthAfterDelete(
                                previous = previous,
                                io = ioResult
                            )
                            persistedChordLines = resolved.lines
                            persistedHasChordsSource = resolved.hasSource
                            parsedChordLines = resolved.lines
                            hasChordsSource = resolved.hasSource
                            if (selectedViewMode == LyricsViewMode.CHORDS) {
                                recomputeCurrentIndexForActiveView()
                            }

                            if (!ioResult.success) {
                                Log.e(
                                    "LrcDebug",
                                    buildAccordsIoFailureLog(
                                        action = "delete",
                                        trackUri = lockedTrackUri,
                                        io = ioResult
                                    )
                                )
                                buildAccordsIoFailureFeedback(
                                    context = context,
                                    actionLabel = sAccordsActionDelete,
                                    io = ioResult
                                )?.let { showAccordsIoFailureToast(it) }
                                return@deletePersisted false
                            }
                            return@deletePersisted true
                        }

                        val ioResult = runCatching {
                            runAccordsDeleteIo(
                                deleteAccords = {
                                    deleteAccordsFromSplByTrackUri(
                                        context = context,
                                        trackUriString = lockedTrackUri,
                                        preferredLrcFileName = editingResolvedLrcFileName
                                    )
                                }
                            )
                        }.getOrElse {
                            com.patrick.lrcreader.core.AccordsIoResult(
                                success = false,
                                stage = "exception:${it::class.simpleName ?: "Unknown"}"
                            )
                        }

                        val previous = AccordsUiTruth(
                            lines = persistedChordLines,
                            hasSource = persistedHasChordsSource
                        )
                        val resolved = resolveAccordsUiTruthAfterDelete(
                            previous = previous,
                            io = ioResult
                        )
                        persistedChordLines = resolved.lines
                        persistedHasChordsSource = resolved.hasSource
                        parsedChordLines = resolved.lines
                        hasChordsSource = resolved.hasSource
                        if (selectedViewMode == LyricsViewMode.CHORDS) {
                            recomputeCurrentIndexForActiveView()
                        }

                        if (!ioResult.success) {
                            Log.e(
                                "LrcDebug",
                                buildAccordsIoFailureLog(
                                    action = "delete",
                                    trackUri = lockedTrackUri,
                                    io = ioResult
                                )
                            )
                            buildAccordsIoFailureFeedback(
                                context = context,
                                actionLabel = sAccordsActionDelete,
                                io = ioResult
                            )
                                ?.let { showAccordsIoFailureToast(it) }
                            return@deletePersisted false
                        }
                        return@deletePersisted true
                    }

                    currentTrackUri?.let { trackUri ->
                        LyricsMemoryCache.invalidate(trackUri)
                        val deleted = withContext(Dispatchers.IO) {
                            runCatching {
                                Log.d(PLAYER_LRC_TAG, "delete_lyrics trackUri=$trackUri")
                                LrcStorage.deleteForTrack(context, trackUri)
                                LrcStorage.loadForTrack(context, trackUri).isNullOrBlank()
                            }.getOrDefault(false)
                        }
                        if (deleted && latestCurrentTrackUri == trackUri) {
                            updateResolvedLyricsFileName(null, "source=LYRICS_DELETE")
                        }
                        return@deletePersisted deleted
                    }
                    true
                },
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
                saveAndCloseRequestToken = saveAndCloseRequestToken,
                chordPaletteStorageKey = if (editingTargetMode == LyricsViewMode.CHORDS) {
                    editingResolvedLrcFileName
                } else {
                    null
                }
            )
            if (showUnsavedLyricsDialog) {
                AlertDialog(
                    onDismissRequest = { showUnsavedLyricsDialog = false },
                    title = {
                        Text(
                            text = stringResource(R.string.lyrics_editor_unsaved_changes_title),
                            color = Color.White
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.lyrics_editor_unsaved_changes_message),
                            color = Color(0xFFB0BEC5)
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showUnsavedLyricsDialog = false
                                saveAndCloseRequestToken++
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.common_save),
                                color = Color(0xFF80CBC4)
                            )
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = { showUnsavedLyricsDialog = false }
                            ) {
                                Text(
                                    text = stringResource(R.string.common_cancel),
                                    color = Color.LightGray
                                )
                            }
                            TextButton(
                                onClick = { closeLyricsEditorImmediately() }
                            ) {
                                Text(
                                    text = stringResource(R.string.lyrics_editor_discard_changes),
                                    color = Color(0xFFFF8A80)
                                )
                            }
                        }
                    }
                )
            }
        } else if (isEditingTimeline) {
            TimelineEditorSection(
                currentSongId = currentSongId,
                startInGridSetup = startTimelineInGridSetup,
                markers = timelineEditorMarkers,
                palette = timelinePalette,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                onCloseEditor = {
                    editingTimelineMidiMarkerIndex = null
                    editingTimelineLightCueTimeMs = null
                    showLightGenerationDialog = false
                    timelineLightPreviewPositionMs = null
                    startTimelineInGridSetup = false
                    isEditingTimeline = false
                },
                onIsPlayingChange = onIsPlayingChange,
                seekToMs = seekToMs,
                onAddPaletteTag = { label -> addTimelinePaletteTag(label) },
                onAddMarker = { label -> addTimelineMarker(label) },
                onAddMarkerAtPosition = { label, targetPositionMs ->
                    addTimelineMarkerAtPosition(label, targetPositionMs)
                },
                onAddTypedMarker = onAddTypedMarker@ { kind ->
                    if (kind == TimelineMarkerKind.DMX) {
                        addTimelineDmxCueAtPosition(getPositionMs().coerceAtLeast(0L))
                    } else {
                        addTypedTimelineMarker(kind)
                    }
                },
                onAddTypedMarkerAtPosition = onAddTypedMarkerAtPosition@ { kind, targetPositionMs ->
                    if (kind == TimelineMarkerKind.DMX) {
                        addTimelineDmxCueAtPosition(targetPositionMs)
                    } else {
                        addTypedTimelineMarkerAtPosition(kind, targetPositionMs)
                    }
                },
                onGenerateLights = {
                    showLightGenerationDialog = true
                },
                onEditMidiMarker = onEditMidiMarker@ { index ->
                    val entry = timelineEditorEntries.getOrNull(index) ?: return@onEditMidiMarker
                    val source = entry.source as? TimelineEditorMarkerSource.Timeline ?: return@onEditMidiMarker
                    if (isCurrentTrackSmp && source.index in timelineMarkers.indices) {
                        editingTimelineMidiMarkerIndex = source.index
                    }
                },
                onEditDmxMarker = onEditDmxMarker@ { index ->
                    val entry = timelineEditorEntries.getOrNull(index) ?: return@onEditDmxMarker
                    val source = entry.source as? TimelineEditorMarkerSource.Light ?: return@onEditDmxMarker
                    editingTimelineLightCueTimeMs = source.timeMs
                },
                showLightPreview = timelineDmxUiVisible && showLightIndicator && hasLightCues,
                lightPreviewSceneState = currentTrackLightScene,
                canPasteDmxCue = timelineDmxUiVisible && canPasteTimelineDmxCue,
                onPasteDmxCueHere = onPasteDmxCueHere@ {
                    val trackUri = currentTrackUri ?: return@onPasteDmxCueHere
                    val clipboard = timelineDmxClipboard
                        ?.takeIf { it.trackUri == trackUri }
                        ?: return@onPasteDmxCueHere
                    val cueToPaste = clipboard.cue.copy(
                        timeMs = getPositionMs().coerceAtLeast(0L)
                    )
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            SmpLightCueBridge.upsertCueAtTime(
                                context = context,
                                trackUriString = trackUri,
                                cue = cueToPaste
                            ) == true
                        }
                        if (saved) {
                            refreshTimelineLightCues(trackUri)
                            Toast.makeText(context, "1 repère DMX collé", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, sTimelineSaveFailed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onCopyDmxMarker = onCopyDmxMarker@ { index ->
                    val trackUri = currentTrackUri ?: return@onCopyDmxMarker
                    val entry = timelineEditorEntries.getOrNull(index) ?: return@onCopyDmxMarker
                    val source = entry.source as? TimelineEditorMarkerSource.Light ?: return@onCopyDmxMarker
                    scope.launch {
                        val cue = withContext(Dispatchers.IO) {
                            SmpLightCueBridge.getCueAtTime(
                                context = context,
                                trackUriString = trackUri,
                                timeMs = source.timeMs
                            )
                        }
                        if (cue != null) {
                            timelineDmxClipboard = TimelineDmxClipboard(
                                trackUri = trackUri,
                                cue = cue
                            )
                            Toast.makeText(context, "1 repère DMX copié", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Copie du repère DMX impossible.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                measuresTempoBpm = timelineMeasuresTempoBpm,
                onMeasuresTempoChange = onMeasuresTempoChange@ { tempoBpm ->
                    val trackUri = currentTrackUri ?: return@onMeasuresTempoChange
                    timelineMeasuresTempoBpm = tempoBpm
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            TrackTimelineTempoPrefs.saveTempoBpm(context, trackUri, tempoBpm)
                        }
                        if (!saved) {
                            timelineMeasuresTempoBpm = withContext(Dispatchers.IO) {
                                TrackTimelineTempoPrefs.getTempoBpm(context, trackUri)
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.timeline_measures_tempo_save_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                measureAnchorMs = timelineSessionMeasureAnchorMs,
                onMeasureAnchorHere = { anchorMs ->
                    timelineSessionMeasureAnchorMs = anchorMs
                },
                onOpenArrangement = {
                    onOpenArrangementHub()
                },
                isPreparedClipLoopTestActive = isTimelinePreparedLoopActive,
                onStartPreparedClipLoopTest = startTimelinePreparedLoopTest,
                onStopPreparedClipLoopTest = stopTimelinePreparedLoopTest,
                onMoveMarkerPosition = onMoveMarkerPosition@ { index, targetPositionMs ->
                    val entry = timelineEditorEntries.getOrNull(index) ?: return@onMoveMarkerPosition
                    when (val source = entry.source) {
                        is TimelineEditorMarkerSource.Timeline -> {
                            if (!isCurrentTrackSmp || source.index !in timelineMarkers.indices) return@onMoveMarkerPosition
                            val previousMarkers = timelineMarkers
                            val updatedMarkers = timelineMarkers
                                .toMutableList()
                                .apply {
                                    this[source.index] = this[source.index].copy(
                                        timeMs = targetPositionMs.coerceAtLeast(0L)
                                    )
                                }
                                .sortedWith(
                                    compareBy<TimelineMarker> { it.timeMs }
                                        .thenBy { it.label }
                                )
                            persistTimelineMarkers(updatedMarkers, previousMarkers)
                        }
                        is TimelineEditorMarkerSource.Light -> {
                            val trackUri = currentTrackUri ?: return@onMoveMarkerPosition
                            scope.launch {
                                val cue = withContext(Dispatchers.IO) {
                                    SmpLightCueBridge.getCueAtTime(
                                        context = context,
                                        trackUriString = trackUri,
                                        timeMs = source.timeMs
                                    )
                                } ?: return@launch
                                val targetTimeMs = targetPositionMs.coerceAtLeast(0L)
                                val saved = withContext(Dispatchers.IO) {
                                    val deletedOld = if (source.timeMs != targetTimeMs) {
                                        SmpLightCueBridge.deleteCueAtTime(
                                            context = context,
                                            trackUriString = trackUri,
                                            timeMs = source.timeMs
                                        ) == true
                                    } else {
                                        true
                                    }
                                    deletedOld && (
                                        SmpLightCueBridge.upsertCueAtTime(
                                            context = context,
                                            trackUriString = trackUri,
                                            cue = cue.copy(timeMs = targetTimeMs)
                                        ) == true
                                        )
                                }
                                if (saved) {
                                    refreshTimelineLightCues(trackUri, targetTimeMs)
                                } else {
                                    Toast.makeText(context, sTimelineSaveFailed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                onRenameMarker = onRenameMarker@ { index, label, markerDurationMs ->
                    val entry = timelineEditorEntries.getOrNull(index) ?: return@onRenameMarker
                    val source = entry.source as? TimelineEditorMarkerSource.Timeline ?: return@onRenameMarker
                    renameTimelineMarker(source.index, label, markerDurationMs)
                },
                onDeleteMarker = onDeleteMarker@ { index ->
                    val entry = timelineEditorEntries.getOrNull(index) ?: return@onDeleteMarker
                    when (val source = entry.source) {
                        is TimelineEditorMarkerSource.Timeline -> deleteTimelineMarker(source.index)
                        is TimelineEditorMarkerSource.Light -> {
                            val trackUri = currentTrackUri ?: return@onDeleteMarker
                            scope.launch {
                                SmpLightCueBridge.deleteCueAtTime(
                                    context = context,
                                    trackUriString = trackUri,
                                    timeMs = source.timeMs
                                )
                                refreshTimelineLightCues(trackUri, source.timeMs)
                            }
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
                            showMixAction = true,
                            showEditLyrics = selectedViewMode == LyricsViewMode.LYRICS ||
                                selectedViewMode == LyricsViewMode.CHORDS,
                            onOpenEditor = {
                                editingTargetMode = selectedViewMode
                                editingTrackUri = currentTrackUri
                                editingLinesDirty = false
                                showUnsavedLyricsDialog = false
                                // Do not resolve the exact SAF lyrics file synchronously here:
                                // opening the editor must stay on the UI thread and fast.
                                editingResolvedLrcFileName = resolvedLyricsLrcFileName
                                val sourceLines = if (editingTargetMode == LyricsViewMode.CHORDS) {
                                    parsedChordLines
                                } else {
                                    parsedLines
                                }
                                if (sourceLines.isNotEmpty()) {
                                    rawLyricsText = editorLyricsText(sourceLines)
                                    editingLines = sourceLines
                                } else {
                                    rawLyricsText = ""
                                    editingLines = emptyList()
                                }
                                currentEditTab = 0
                                isEditingLyrics = true
                            },
                            showTimeline = isCurrentTrackSmp,
                            onOpenTimeline = {
                                if (isCurrentTrackSmp) {
                                    startTimelineInGridSetup = false
                                    isEditingTimeline = true
                                }
                            },
                            showArrangementAction = isCurrentTrackSmp,
                            onOpenArrangement = {
                                if (isCurrentTrackSmp) {
                                    startTimelineInGridSetup = true
                                    isEditingTimeline = true
                                }
                            },
                            showWaveformAction = canOpenWaveform,
                            onOpenWaveform = {
                                currentSongId?.takeIf { it.isNotBlank() }?.let(onOpenWaveform)
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

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            if (showViewToggle) {
                                LyricsViewSelector(
                                    selectedMode = selectedViewMode,
                                    hasLyrics = currentTrackUri != null,
                                    hasChords = if (EditionConfig.isLite) {
                                        currentTrackUri != null
                                    } else {
                                        currentTrackUri != null
                                    },
                                    chordsBlocked = EditionConfig.isLite,
                                    onSelectMode = { mode ->
                                        if (mode == LyricsViewMode.CHORDS && EditionConfig.isLite) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.timeline_pro_only),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@LyricsViewSelector
                                        }
                                        selectedViewMode = mode
                                        currentTrackUri?.let { trackUri ->
                                            TrackLyricsViewPrefs.save(context, trackUri, mode)
                                        }
                                        recomputeCurrentIndexForActiveView()
                                        if (mode == LyricsViewMode.LYRICS) {
                                            centerCurrentLineLazy(listState)
                                        }
                                    }
                                )
                            }

                            midiMonitorEvent?.let { sent ->
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 6.dp, end = 10.dp)
                                        .background(
                                            Color.White,
                                            RectangleShape
                                        )
                                        .border(
                                            1.dp,
                                            Color.Black.copy(alpha = 0.12f),
                                            RectangleShape
                                        )
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = sent.program.toString(),
                                        color = Color.Black,
                                        fontSize = 12.sp
                                    )
                                }
                            }
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
                                    lyricsLoading = lyricsLoading || lyricsResolving || !lyricsResolutionCompleted,
                                    isConcertMode = isConcertMode,
                                    currentLrcIndex = safeLrcIndex,
                                    onLyricsBoxHeightChange = { lyricsBoxHeightPx = it },
                                    highlightColor = highlightColor,
                                    onLineClick = { index, timeMs ->
                                        seekAndCenter(timeMs.toInt(), index)
                                        if (currentTrackUri != null && !isCurrentTrackSmp) {
                                            lastMidiIndex = index
                                            val cue = MidiCueDispatcher.resolveCueForTrack(
                                                context = context,
                                                trackUri = currentTrackUri,
                                                lines = parsedLines,
                                                lineIndex = index
                                            )
                                            MidiCueDispatcher.onResolvedCueChanged(
                                                trackUri = currentTrackUri,
                                                lineIndex = index,
                                                cue = cue,
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
                                        .then(
                                            if (!activeLiveNoteFromTimeline) {
                                                Modifier.combinedClickable(
                                                    onClick = {}, // rien au click simple
                                                    onLongClick = { removeLiveNoteAndPersist(note) }
                                                )
                                            } else {
                                                Modifier
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = note.text,
                                            color = Color(0xFFFFC107),
                                            fontSize = 16.sp,
                                            lineHeight = 20.sp,
                                            modifier = Modifier.widthIn(max = 320.dp)
                                        )
                                        if (!activeLiveNoteFromTimeline) {
                                            IconButton(
                                                onClick = { removeLiveNoteAndPersist(note) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = sDeleteLiveNote,
                                                    tint = Color(0xFFFFC107)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (dmxUiVisible && showLightIndicator && hasLightCues) {
                                if (showLightTestDialog) {
                                    LightTestDialog(
                                        onRed = { LightPreviewTestController.showRed(currentTrackUri) },
                                        onBlue = { LightPreviewTestController.showBlue(currentTrackUri) },
                                        onGreen = { LightPreviewTestController.showGreen(currentTrackUri) },
                                        onWhite = { LightPreviewTestController.showWhite(currentTrackUri) },
                                        onStrobe = { LightPreviewTestController.showStrobe(currentTrackUri) },
                                        onBlackout = { LightPreviewTestController.showBlackout(currentTrackUri) },
                                        onOff = { LightPreviewTestController.showOff(currentTrackUri) },
                                        onQuickTest = { LightPreviewTestController.runQuickTest(currentTrackUri) },
                                        onClose = {
                                            LightPreviewTestController.stop()
                                            showLightTestDialog = false
                                        },
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                            .padding(start = 14.dp, end = 86.dp, bottom = 14.dp)
                                    )
                                }

                                LightSimulatorPreview(
                                    sceneState = currentTrackLightScene,
                                    enabled = !isPlaying,
                                    onClick = {
                                        LightPreviewTestController.prime(
                                            trackUri = currentTrackUri,
                                            sceneState = currentTrackLightScene
                                        )
                                        showLightTestDialog = true
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 14.dp, bottom = 14.dp)
                                )
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
                                timelineLightPreviewPositionMs = null
                                if (currentTrackUri != null && hasLightCues) {
                                    LightCueDispatcher.syncToPosition(
                                        trackUri = currentTrackUri,
                                        positionMs = safe.toLong()
                                    )
                                }
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
                                timelineLightPreviewPositionMs = null
                                if (currentTrackUri != null && hasLightCues) {
                                    LightCueDispatcher.syncToPosition(
                                        trackUri = currentTrackUri,
                                        positionMs = 0L
                                    )
                                }
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
                                LightCueDispatcher.resetGlobal()
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
                    currentTrackVolumeSource = currentTrackVolumeSource,
                    onTrackGainChange = onTrackGainChange,
                    onTrackGainCommit = onTrackGainCommit,
                    tempo = if (EditionConfig.isLite) liteTrackMixTempo else tempo,
                    onTempoChange = { newTempo ->
                        if (!isHqAvailable) {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - hqToastShownAtMs > 1200L) {
                                Toast.makeText(context, sHqUnavailable, Toast.LENGTH_SHORT).show()
                                hqToastShownAtMs = now
                            }
                            return@TrackMixScreen
                        }
                        if (EditionConfig.isLite) {
                            if (liteTrackMixTempo != newTempo) {
                                liteTrackMixModified = true
                            }
                            liteTrackMixTempo = newTempo
                            applyLiteTrackMixToPlayer(
                                speed = liteTrackMixTempo,
                                pitchSemi = liteTrackMixPitchSemi
                            )
                        } else {
                            onTempoChange(newTempo)
                        }
                    },
                    pitchSemi = if (EditionConfig.isLite) liteTrackMixPitchSemi else pitchSemi,
                    onPitchSemiChange = { newSemi ->
                        if (!isHqAvailable) {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - hqToastShownAtMs > 1200L) {
                                Toast.makeText(context, sHqUnavailable, Toast.LENGTH_SHORT).show()
                                hqToastShownAtMs = now
                            }
                            return@TrackMixScreen
                        }
                        if (EditionConfig.isLite) {
                            val clamped = newSemi.coerceIn(-6, 6)
                            if (liteTrackMixPitchSemi != clamped) {
                                liteTrackMixModified = true
                            }
                            liteTrackMixPitchSemi = clamped
                            applyLiteTrackMixToPlayer(
                                speed = liteTrackMixTempo,
                                pitchSemi = liteTrackMixPitchSemi
                            )
                        } else {
                            onPitchSemiChange(newSemi)
                        }
                    },
                    currentTrackUri = currentTrackUri,
                    onClose = {
                        if (EditionConfig.isLite && liteTrackMixModified) {
                            showTrackMixProDialog = true
                        } else {
                            showMixScreen = false
                        }
                    }
                )
            }
        }
    }

    if (showTrackMixProDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showTrackMixProDialog = false
                showMixScreen = false
            },
            title = {
                Text(
                    text = sTrackMixProTitle,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = sTrackMixProMessage,
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTrackMixProDialog = false
                        showMixScreen = false
                        val marketIntent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("market://search?q=Stage Music Player Pro")
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        val webIntent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://play.google.com/store/search?q=Stage%20Music%20Player%20Pro&c=apps")
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(marketIntent)
                        } catch (_: android.content.ActivityNotFoundException) {
                            context.startActivity(webIntent)
                        }
                    }
                ) {
                    Text(sUpgradeToPro, color = Color(0xFF80CBC4))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTrackMixProDialog = false
                        showMixScreen = false
                    }
                ) {
                    Text(stringResource(R.string.common_close), color = Color(0xFFB0BEC5))
                }
            },
            containerColor = Color(0xFF222222)
        )
    }

    val timelineMidiMarkerIndex = editingTimelineMidiMarkerIndex
    val timelineMidiTrackUri = currentTrackUri?.takeIf { it.isNotBlank() }
    if (
        isEditingTimeline &&
        timelineMidiMarkerIndex != null &&
        timelineMidiMarkerIndex in timelineMarkers.indices &&
        timelineMarkers[timelineMidiMarkerIndex].kind == TimelineMarkerKind.MIDI &&
        timelineMidiTrackUri != null
    ) {
        TimelineMidiCueEditorPopup(
            trackUri = timelineMidiTrackUri,
            markerTimeMs = timelineMarkers[timelineMidiMarkerIndex].timeMs,
            onClose = { editingTimelineMidiMarkerIndex = null }
        )
    }

    val timelineLightCueTimeMs = editingTimelineLightCueTimeMs
    val timelineLightTrackUri = currentTrackUri?.takeIf { it.isNotBlank() }
    if (
        dmxUiVisible &&
        isEditingTimeline &&
        showLightGenerationDialog &&
        timelineLightTrackUri != null
    ) {
        TimelineLightCueGenerationPopup(
            durationMs = durationMs.toLong(),
            onGenerate = { style, replaceExisting ->
                val generated = LightCueAutoGenerator.generate(
                    durationMs = durationMs.toLong(),
                    style = style
                )
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        SmpLightCueBridge.saveCuesBatch(
                            context = context,
                            trackUriString = timelineLightTrackUri,
                            cues = generated,
                            replaceExisting = replaceExisting
                        ) ?: false
                    }
                    if (saved) {
                        refreshTimelineLightCues(timelineLightTrackUri)
                        showLightGenerationDialog = false
                    } else {
                        Toast.makeText(context, sLightGenerateFailed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onClose = { showLightGenerationDialog = false }
        )
    }
    if (
        timelineDmxUiVisible &&
        isEditingTimeline &&
        timelineLightCueTimeMs != null &&
        timelineLightTrackUri != null
    ) {
        TimelineLightCueEditorPopup(
            trackUri = timelineLightTrackUri,
            markerTimeMs = timelineLightCueTimeMs,
            onSaved = {
                refreshTimelineLightCues(timelineLightTrackUri, timelineLightCueTimeMs)
                editingTimelineLightCueTimeMs = null
            },
            onClose = { editingTimelineLightCueTimeMs = null }
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
    chordsBlocked: Boolean = false,
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
                label = {
                    Text(
                        stringResource(R.string.player_view_chords),
                        color = if (chordsBlocked) {
                            Color.White.copy(alpha = 0.55f)
                        } else {
                            Color.Unspecified
                        }
                    )
                }
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
    showMixAction: Boolean,
    showEditLyrics: Boolean,
    onOpenEditor: () -> Unit,
    showTimeline: Boolean,
    onOpenTimeline: () -> Unit,
    showArrangementAction: Boolean,
    onOpenArrangement: () -> Unit,
    showWaveformAction: Boolean,
    onOpenWaveform: () -> Unit,
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

            if (showMixAction) {
                IconButton(onClick = onOpenMix) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = stringResource(R.string.player_cd_track_mix),
                        tint = Color(0xFFFFC107)
                    )
                }
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

            if (showTimeline) {
                IconButton(onClick = onOpenTimeline) {
                    Text(
                        text = stringResource(R.string.player_timeline_short),
                        color = Color(0xFF80CBC4),
                        fontSize = 13.sp
                    )
                }
            }

            if (showArrangementAction) {
                IconButton(onClick = onOpenArrangement) {
                    Text(
                        text = stringResource(R.string.player_arrangement_short),
                        color = Color(0xFFA5D6A7),
                        fontSize = 13.sp
                    )
                }
            }

            if (showWaveformAction) {
                IconButton(onClick = onOpenWaveform) {
                    Icon(
                        imageVector = Icons.Filled.ShowChart,
                        contentDescription = stringResource(R.string.player_cd_open_waveform),
                        tint = Color(0xFF90CAF9)
                    )
                }
            }
        }
    }
}

private data class LrcTextWithFileName(
    val text: String,
    val fileName: String?,
    val debugPath: String? = null
)

private data class SmpSongUnitTextTarget(
    val file: File,
    val fileName: String
)

private data class LyricsPersistOutcome(
    val trackUriString: String,
    val migration: SmpAutoMigrationResult?,
    val resolvedFileName: String?,
    val reloadedText: String?
)

private data class AccordsPersistOutcome(
    val trackUriString: String,
    val migration: SmpAutoMigrationResult?,
    val io: com.patrick.lrcreader.core.AccordsIoResult,
    val reloadedText: String?
)

private fun loadSmpLiveNotesForTrack(
    context: android.content.Context,
    trackUriString: String
): List<LiveNote> {
    val target = resolveSmpAnnotationsTarget(
        context = context,
        trackUriString = trackUriString,
        requireExisting = false
    ) ?: return emptyList()
    return SmpAnnotationsStore.read(target.file)
}

private fun loadSmpTimelineMarkersForTrack(
    context: android.content.Context,
    trackUriString: String
): List<TimelineMarker> {
    val target = resolveSmpTimelineTarget(
        context = context,
        trackUriString = trackUriString,
        requireExisting = false
    ) ?: return emptyList()
    return SmpTimelineStore.read(target.file)
}

private fun loadSmpLightCuesForTrack(
    context: android.content.Context,
    trackUriString: String
): List<LightCue> {
    return SmpLightCueBridge.getRuntimeCues(context, trackUriString).orEmpty()
}

private suspend fun persistSmpLiveNotesForTrack(
    context: android.content.Context,
    trackUriString: String?,
    notes: List<LiveNote>
): Boolean {
    if (trackUriString.isNullOrBlank()) {
        return false
    }

    val saved = withContext(Dispatchers.IO) {
        resolveSmpAnnotationsTarget(
            context = context,
            trackUriString = trackUriString,
            requireExisting = false
        )?.let { target ->
            SmpAnnotationsStore.write(target.file, notes)
        }
    }

    if (saved == false) {
        Log.w("LrcDebug", "ANNOTATIONS_SAVE_FAILED trackUri=$trackUriString")
    }
    return saved == true
}

private suspend fun persistSmpTimelineMarkersForTrack(
    context: android.content.Context,
    trackUriString: String?,
    markers: List<TimelineMarker>
): Boolean {
    if (trackUriString.isNullOrBlank()) {
        return false
    }

    val saved = withContext(Dispatchers.IO) {
        resolveSmpTimelineTarget(
            context = context,
            trackUriString = trackUriString,
            requireExisting = false
        )?.let { target ->
            SmpTimelineStore.write(target.file, markers)
        }
    }

    if (saved == false) {
        Log.w("LrcDebug", "TIMELINE_SAVE_FAILED trackUri=$trackUriString")
    }
    return saved == true
}

private fun resolveSmpAnnotationsTarget(
    context: android.content.Context,
    trackUriString: String,
    requireExisting: Boolean
): SmpSongUnitTextTarget? {
    return resolveSmpSongUnitTextTarget(
        context = context,
        trackUriString = trackUriString,
        transportNameSelector = { it.files?.annotations },
        fallbackName = SmpAnnotationsStore.ANNOTATIONS_FILE_NAME,
        requireExisting = requireExisting
    )
}

private fun resolveSmpTimelineTarget(
    context: android.content.Context,
    trackUriString: String,
    requireExisting: Boolean
): SmpSongUnitTextTarget? {
    val songDir = resolveInternalSmpSongDir(context, trackUriString) ?: return null
    val timelineFile = File(songDir, SmpTimelineStore.TIMELINE_FILE_NAME)
    if (requireExisting && !timelineFile.isFile) {
        return null
    }
    return SmpSongUnitTextTarget(
        file = timelineFile,
        fileName = timelineFile.name
    )
}

private fun projectTimelineNoteMarkers(markers: List<TimelineMarker>): List<LiveNote> {
    return markers.asSequence()
        .filter { it.kind == TimelineMarkerKind.NOTE }
        .mapNotNull { marker ->
            val text = marker.label.trim()
            if (text.isEmpty()) {
                null
            } else {
                LiveNote(
                    timeMs = marker.timeMs.coerceAtLeast(0L),
                    durationMs = marker.durationMs?.coerceAtLeast(1L) ?: DEFAULT_TIMELINE_NOTE_DURATION_MS,
                    text = text
                )
            }
        }
        .sortedBy { it.timeMs }
        .toList()
}

private fun isLiveNoteActiveAt(note: LiveNote, positionMs: Long): Boolean {
    return positionMs >= note.timeMs && positionMs < (note.timeMs + note.durationMs)
}

private fun buildLiveNoteTrace(
    positionMs: Long,
    activeAnnotationNotes: List<LiveNote>,
    chosenAnnotationNote: LiveNote?,
    activeTimelineNotes: List<LiveNote>,
    chosenTimelineNote: LiveNote?,
    latestTimelineCandidate: LiveNote?,
    finalChosenNote: LiveNote?,
    finalFromTimeline: Boolean
): String {
    return "LIVE_NOTE_TRACE${
        buildLiveNoteTraceCore(
            includePositionMs = positionMs,
            activeAnnotationNotes = activeAnnotationNotes,
            chosenAnnotationNote = chosenAnnotationNote,
            activeTimelineNotes = activeTimelineNotes,
            chosenTimelineNote = chosenTimelineNote,
            latestTimelineCandidate = latestTimelineCandidate,
            finalChosenNote = finalChosenNote,
            finalFromTimeline = finalFromTimeline
        )
    }"
}

private fun buildLiveNoteTraceKey(
    activeAnnotationNotes: List<LiveNote>,
    chosenAnnotationNote: LiveNote?,
    activeTimelineNotes: List<LiveNote>,
    chosenTimelineNote: LiveNote?,
    latestTimelineCandidate: LiveNote?,
    finalChosenNote: LiveNote?,
    finalFromTimeline: Boolean
): String {
    return buildLiveNoteTraceCore(
        includePositionMs = null,
        activeAnnotationNotes = activeAnnotationNotes,
        chosenAnnotationNote = chosenAnnotationNote,
        activeTimelineNotes = activeTimelineNotes,
        chosenTimelineNote = chosenTimelineNote,
        latestTimelineCandidate = latestTimelineCandidate,
        finalChosenNote = finalChosenNote,
        finalFromTimeline = finalFromTimeline
    )
}

private fun buildLiveNoteTraceCore(
    includePositionMs: Long?,
    activeAnnotationNotes: List<LiveNote>,
    chosenAnnotationNote: LiveNote?,
    activeTimelineNotes: List<LiveNote>,
    chosenTimelineNote: LiveNote?,
    latestTimelineCandidate: LiveNote?,
    finalChosenNote: LiveNote?,
    finalFromTimeline: Boolean
): String {
    val finalSource = when {
        finalChosenNote == null -> "none"
        finalFromTimeline -> "timeline"
        else -> "annotations"
    }
    return buildString {
        includePositionMs?.let { pos -> append(" posMs=").append(pos) }
        append(" annotationsActive=").append(describeLiveNoteList(activeAnnotationNotes))
        append(" annotationChosen=").append(describeLiveNote(chosenAnnotationNote))
        append(" timelineActive=").append(describeLiveNoteList(activeTimelineNotes))
        append(" timelineChosen=").append(describeLiveNote(chosenTimelineNote))
        append(" timelineLatest=").append(describeLiveNote(latestTimelineCandidate))
        append(" finalSource=").append(finalSource)
        append(" finalChosen=").append(describeLiveNote(finalChosenNote))
    }
}

private fun describeLiveNoteList(notes: List<LiveNote>): String {
    if (notes.isEmpty()) return "[]"
    return notes.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ", "
    ) { note ->
        describeLiveNote(note)
    }
}

private fun describeLiveNote(note: LiveNote?): String {
    if (note == null) return "null"
    return "{start=${note.timeMs},duration=${note.durationMs},end=${note.timeMs + note.durationMs},text=${note.text.quoteForTrace()}}"
}

private fun String.quoteForTrace(): String {
    return "\"" + replace("\"", "\\\"") + "\""
}

private sealed interface TimelineEditorMarkerSource {
    data class Timeline(val index: Int) : TimelineEditorMarkerSource
    data class Light(val timeMs: Long) : TimelineEditorMarkerSource
}

private data class TimelineEditorMarkerEntry(
    val marker: TimelineMarker,
    val source: TimelineEditorMarkerSource
)

private data class TimelineDmxClipboard(
    val trackUri: String,
    val cue: LightCue
)

private fun buildTimelineEditorEntries(
    context: android.content.Context,
    timelineMarkers: List<TimelineMarker>,
    lightCues: List<LightCue>
): List<TimelineEditorMarkerEntry> {
    val timelineEntries = timelineMarkers.mapIndexedNotNull { index, marker ->
        if (marker.kind == TimelineMarkerKind.DMX) {
            null
        } else {
            TimelineEditorMarkerEntry(
                marker = marker,
                source = TimelineEditorMarkerSource.Timeline(index = index)
            )
        }
    }
    val lightEntries = lightCues.map { cue ->
        TimelineEditorMarkerEntry(
            marker = TimelineMarker(
                timeMs = cue.timeMs.coerceAtLeast(0L),
                label = buildTimelineLightCueLabel(context, cue),
                kind = TimelineMarkerKind.DMX
            ),
            source = TimelineEditorMarkerSource.Light(timeMs = cue.timeMs.coerceAtLeast(0L))
        )
    }

    return (timelineEntries + lightEntries).sortedWith(
        compareBy<TimelineEditorMarkerEntry> { entry -> entry.marker.timeMs }
            .thenBy { entry ->
                when (entry.source) {
                    is TimelineEditorMarkerSource.Timeline -> 0
                    is TimelineEditorMarkerSource.Light -> 1
                }
            }
            .thenBy { entry -> entry.marker.label.lowercase() }
    )
}

private fun buildTimelineLightCueLabel(
    context: android.content.Context,
    cue: LightCue
): String {
    return when (val action = cue.action) {
        is LightAction.Color -> {
            val colorLabel = when (action.argb) {
                0xFFFF0000L -> context.getString(R.string.light_color_red)
                0xFF0000FFL -> context.getString(R.string.light_color_blue)
                0xFF00FF00L -> context.getString(R.string.light_color_green)
                0xFFFFFFFFL -> context.getString(R.string.light_color_white)
                else -> "#%06X".format(action.argb and 0x00FFFFFFL)
            }
            "${context.getString(R.string.light_cue_type_color)} $colorLabel"
        }
        LightAction.Blackout -> context.getString(R.string.light_cue_type_blackout)
        is LightAction.Strobe -> context.getString(R.string.light_cue_type_strobe)
    }
}

private data class AccordsWriteRequest(
    val trackUriString: String,
    val preferredLrcFileName: String?,
    val lines: List<LrcLine>
)

private fun readSidecarLrcSmart(
    context: android.content.Context,
    trackUriString: String?
): LrcTextWithFileName? {
    val safeTrackUri = trackUriString?.trim().orEmpty()
    if (safeTrackUri.isBlank()) return null
    Log.d(PLAYER_LRC_TAG, "load_lyrics_fallback trackUri=$safeTrackUri")
    val text = LrcStorage.loadForTrack(context, safeTrackUri) ?: return null
    val origin = runCatching {
        LrcStorage.resolveOriginForTrack(context, safeTrackUri)
    }.getOrNull()
    return LrcTextWithFileName(
        text = text,
        fileName = origin?.fileName,
        debugPath = origin?.debugPath
    )
}

private fun readAccordsFromSplByTrackUri(
    context: android.content.Context,
    trackUriString: String,
    preferredLrcFileName: String?
): String? {
    Log.d(
        PLAYER_LRC_TAG,
        "load_accords trackUri=$trackUriString preferred=$preferredLrcFileName"
    )
    return LrcStorage.loadAccordsForTrack(
        context = context,
        trackUriString = trackUriString,
        preferredLrcFileName = preferredLrcFileName
    )
}

private fun resolveSmpSongUnitTextTarget(
    context: android.content.Context,
    trackUriString: String,
    transportNameSelector: (SmpConfig) -> String?,
    fallbackName: String,
    requireExisting: Boolean
): SmpSongUnitTextTarget? {
    val trackUri = runCatching { Uri.parse(trackUriString) }.getOrNull() ?: return null
    if (trackUri.scheme != "file") return null

    val audioPath = trackUri.path?.takeIf { it.isNotBlank() } ?: return null
    val audioFile = File(audioPath)
    if (!audioFile.isFile || !audioFile.name.startsWith("audio.", ignoreCase = true)) {
        return null
    }

    val songDir = audioFile.parentFile?.canonicalFile ?: return null
    val tracksRoot = File(context.filesDir, "tracks").canonicalFile
    if (songDir.parentFile?.canonicalFile != tracksRoot) {
        return null
    }

    val configFile = File(songDir, "config.json")
    if (!configFile.isFile) {
        return null
    }

    val config = runCatching {
        SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))
    }.getOrNull() ?: return null

    val expectedAudioName = config.files?.audio?.trim()
    if (!expectedAudioName.isNullOrBlank() && !audioFile.name.equals(expectedAudioName, ignoreCase = true)) {
        return null
    }

    val transportName = transportNameSelector(config)?.trim().takeUnless { it.isNullOrBlank() } ?: fallbackName
    val targetFile = resolveSmpSongUnitChildFile(songDir, transportName) ?: return null
    if (requireExisting && !targetFile.isFile) {
        return null
    }

    return SmpSongUnitTextTarget(
        file = targetFile,
        fileName = targetFile.name
    )
}

private fun resolveInternalSmpSongDir(
    context: android.content.Context,
    trackUriString: String
): File? {
    val trackUri = runCatching { Uri.parse(trackUriString) }.getOrNull() ?: return null
    if (trackUri.scheme != "file") return null

    val audioPath = trackUri.path?.takeIf { it.isNotBlank() } ?: return null
    val audioFile = File(audioPath)
    if (!audioFile.isFile || !audioFile.name.startsWith("audio.", ignoreCase = true)) {
        return null
    }

    val songDir = audioFile.parentFile?.canonicalFile ?: return null
    val tracksRoot = File(context.filesDir, "tracks").canonicalFile
    if (songDir.parentFile?.canonicalFile != tracksRoot) {
        return null
    }

    return songDir.takeIf { File(it, "config.json").isFile }
}

private fun resolveSmpSongUnitChildFile(songDir: File, transportName: String): File? {
    val cleanName = transportName.trim()
    if (cleanName.isEmpty()) {
        return null
    }

    return runCatching {
        val canonicalSongDir = songDir.canonicalFile
        val canonicalChild = File(canonicalSongDir, cleanName).canonicalFile
        val songPath = canonicalSongDir.path
        if (!canonicalChild.path.startsWith("$songPath${File.separator}") && canonicalChild.path != songPath) {
            return null
        }
        canonicalChild
    }.getOrNull()
}

private fun resolveExactLrcFileNameForTrack(
    context: android.content.Context,
    trackUriString: String,
    preferredLrcFileName: String?
): String {
    val fromStorage = runCatching {
        LrcStorage.resolveOriginForTrack(context, trackUriString)?.fileName
    }.getOrNull()
    val hashedFallback = LrcStorage.hashedFileNameForTrack(trackUriString)
    val resolution = resolveAccordsLrcFileName(
        preferredLrcFileName = preferredLrcFileName,
        originLrcFileName = fromStorage,
        hashedFallbackFileName = hashedFallback
    )
    Log.d(
        "LrcDebug",
        "ACCORDS_FILE_NAME_RESOLVE trackUri=$trackUriString source=${resolution.source} target=${resolution.fileName} preferred=$preferredLrcFileName origin=$fromStorage"
    )
    return resolution.fileName
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
    val ensureResult = LrcStorage.ensureAccordsFileForTrack(
        context = context,
        trackUriString = trackUriString,
        preferredLrcFileName = preferredLrcFileName
    )
    Log.d(
        PLAYER_LRC_TAG,
        "ACCORDS_ENSURE_FILE trackUri=$trackUriString target=$exactName result=$ensureResult"
    )
    return ensureResult == AccordsEnsureResult.CREATED
}

private fun ensureLyricsFileExistsForTrack(
    context: android.content.Context,
    trackUriString: String,
    preferredLrcFileName: String?
): AccordsEnsureResult {
    val ensureResult = LrcStorage.ensureLyricsFileForTrack(
        context = context,
        trackUriString = trackUriString,
        preferredLrcFileName = preferredLrcFileName
    )
    Log.d(
        PLAYER_LRC_TAG,
        "ensure_lyrics trackUri=$trackUriString preferred=$preferredLrcFileName result=$ensureResult"
    )
    return ensureResult
}

private fun writeAccordsToSplByTrackUri(
    context: android.content.Context,
    trackUriString: String,
    preferredLrcFileName: String?,
    lines: List<LrcLine>
): String? {
    Log.d(
        PLAYER_LRC_TAG,
        "save_accords trackUri=$trackUriString preferred=$preferredLrcFileName lines=${lines.size}"
    )
    return LrcStorage.saveAccordsForTrack(
        context = context,
        trackUriString = trackUriString,
        preferredLrcFileName = preferredLrcFileName,
        lines = lines
    )
}

private fun deleteAccordsFromSplByTrackUri(
    context: android.content.Context,
    trackUriString: String,
    preferredLrcFileName: String?
): Boolean {
    Log.d(
        PLAYER_LRC_TAG,
        "delete_accords trackUri=$trackUriString preferred=$preferredLrcFileName"
    )
    return LrcStorage.deleteAccordsForTrack(
        context = context,
        trackUriString = trackUriString,
        preferredLrcFileName = preferredLrcFileName
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

private fun baseNameFromTrackUriString(trackUriString: String): String {
    val uri = runCatching { Uri.parse(trackUriString) }.getOrNull()
    val last = uri?.lastPathSegment ?: trackUriString
    val clean = last.substringAfterLast('/').substringAfterLast(':')
    val base = clean.substringBeforeLast('.', clean).trim()
    return if (base.isBlank()) "track" else base
}
