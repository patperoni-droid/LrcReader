package com.patrick.lrcreader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.EditSoundPrefs
import com.patrick.lrcreader.core.EditionConfig
import com.patrick.lrcreader.core.TrackVolumePrefs
import com.patrick.lrcreader.core.WaveformSessionPrefs
import com.patrick.lrcreader.core.config.SongIdKeyResolver
import com.patrick.lrcreader.core.waveform.WaveformExtractor
import com.patrick.lrcreader.core.waveform.WaveformPeaksCache
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.core.EditPrefs
import com.patrick.lrcreader.smp.SmpConfig
import com.patrick.lrcreader.smp.SmpLibraryScanner
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val ZOOM_MIN = 1f
private const val ZOOM_MAX = 120f
private const val MATCH_VOLUME_TARGET_LUFS = -14f
private const val MATCH_VOLUME_MIN_DB = -12
private const val MATCH_VOLUME_MAX_DB = 0
private const val WAVEFORM_GESTURE_TAG = "SMP_WAVEFORM_GESTURE"

private enum class DragTarget {
    NONE,
    IN,
    OUT
}

private fun logWaveformGesture(action: String, details: String) {
    Log.d(WAVEFORM_GESTURE_TAG, "$action | $details")
}

@Composable
fun WaveformPreviewScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    initialSongId: String? = null,
    currentPlayingSongId: String? = null,
    onStopCurrentPlayback: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appContext = context.applicationContext
    val exoPlayer = remember(appContext) {
        ExoPlayer.Builder(appContext).build().apply { playWhenReady = false }
    }
    val smpLibraryScanner = remember(appContext) { SmpLibraryScanner(appContext) }

    var selectedSongId by remember { mutableStateOf<String?>(null) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var peaks by remember { mutableStateOf<List<Float>>(emptyList()) }
    var zoom by remember { mutableStateOf(1f) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    var durationMs by remember { mutableIntStateOf(0) }
    var inMs by remember { mutableIntStateOf(0) }
    var outMs by remember { mutableIntStateOf(0) }
    var pendingVolumeDb by remember { mutableIntStateOf(0) }
    var pendingVolumeSource by remember { mutableStateOf<String?>(null) }
    var estimatedTrackLufs by remember { mutableStateOf<Float?>(null) }
    var playheadMs by remember { mutableIntStateOf(0) }
    var stepMs by remember { mutableIntStateOf(50) }
    var isPlayingWave by remember { mutableStateOf(false) }
    var isDetectingSilence by remember { mutableStateOf(false) }
    var isMatchingVolume by remember { mutableStateOf(false) }
    var showWaveformSaveProDialog by remember { mutableStateOf(false) }
    var restoredScrollPx by remember { mutableStateOf<Int?>(null) }
    var lastInitialSongId by remember { mutableStateOf<String?>(null) }
    val sWaveformSaveProTitle = stringResource(R.string.waveform_save_pro_dialog_title)
    val sWaveformSaveProMessage = stringResource(R.string.waveform_save_pro_dialog_message)
    val sUpgradeToPro = stringResource(R.string.library_upgrade_to_pro)

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

    fun loadAudioUri(songId: String, uri: Uri, displayNameHint: String? = null) {
        val restoreSameSongSession = WaveformSessionPrefs.loadSongId(context) == songId
        val restoredZoom = if (restoreSameSongSession) {
            WaveformSessionPrefs.loadZoom(context).coerceIn(ZOOM_MIN, ZOOM_MAX)
        } else {
            ZOOM_MIN
        }
        val restoredPlayhead = if (restoreSameSongSession) {
            WaveformSessionPrefs.loadPlayhead(context).coerceAtLeast(0)
        } else {
            0
        }
        val restoredScroll = if (restoreSameSongSession) {
            WaveformSessionPrefs.loadScroll(context).coerceAtLeast(0)
        } else {
            0
        }

        selectedSongId = songId
        selectedUri = uri
        selectedName = displayNameHint?.takeIf { it.isNotBlank() } ?: songId
        zoom = restoredZoom
        restoredScrollPx = restoredScroll
        WaveformSessionPrefs.saveSongId(context, songId)
        WaveformSessionPrefs.saveTitle(context, selectedName)
        WaveformSessionPrefs.saveZoom(context, zoom)

        peaks = emptyList()
        hasError = false
        isLoading = true
        exoPlayer.pause()
        exoPlayer.playWhenReady = false
        isPlayingWave = false
        durationMs = 0
        inMs = 0
        outMs = 0
        pendingVolumeDb = 0
        pendingVolumeSource = null
        estimatedTrackLufs = null
        playheadMs = 0
        runCatching {
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.seekTo(0L)
        }

        analysisJob?.cancel()
        analysisJob = scope.launch {
            val result = runCatching {
                val localDurationMs = queryDurationMs(context, uri)
                val waveformPeaks = WaveformPeaksCache.getOrCompute(
                    context = context,
                    uri = uri,
                    targetPoints = 20_000,
                    durationMs = localDurationMs
                ) {
                    WaveformExtractor.extractNormalizedPeaks(
                        context = context,
                        uri = uri,
                        targetPoints = 20_000
                    )
                }
                waveformPeaks to localDurationMs
            }

            if (result.isSuccess) {
                val (newPeaks, newDurationMs) = result.getOrNull() ?: (emptyList<Float>() to 0)
                peaks = newPeaks
                durationMs = newDurationMs.coerceAtLeast(0)
                estimatedTrackLufs = estimateLufsFromPeaks(newPeaks.toFloatArray())
                if (durationMs > 0) {
                    playheadMs = restoredPlayhead.coerceIn(0, durationMs)
                    WaveformSessionPrefs.savePlayhead(context, playheadMs)
                    val savedConfigPlayback = selectedSongId
                        ?.let { songId -> smpLibraryScanner.findSongById(songId) }
                        ?.let(SmpConfig::readPlaybackFromSongUnit)
                    val saved = EditSoundPrefs.get(context, uri)
                    val legacySaved = if (saved == null) {
                        EditPrefs.getEdit(context, uri.toString())
                    } else {
                        null
                    }
                    val savedStartMs = savedConfigPlayback?.trimStartMs?.toInt()
                        ?: saved?.startMs
                        ?: legacySaved?.startMs?.toInt()
                        ?: 0
                    val savedOutMs = savedConfigPlayback?.trimEndMs?.toInt()
                        ?: saved?.endMs?.takeIf { it > 0 }
                        ?: legacySaved?.endMs?.toInt()?.takeIf { it > 0 }
                        ?: durationMs
                    pendingVolumeDb = (savedConfigPlayback?.volumeDb
                        ?: TrackVolumePrefs.getDb(context, uri.toString())
                        ?: 0).coerceIn(MATCH_VOLUME_MIN_DB, MATCH_VOLUME_MAX_DB)
                    pendingVolumeSource = savedConfigPlayback?.volumeSource
                    val (safeIn, safeOut) = normalizeInOut(
                        inMs = savedStartMs,
                        outMs = savedOutMs,
                        durationMs = durationMs
                    )
                    inMs = safeIn
                    outMs = safeOut
                }
                hasError = false
            } else {
                peaks = emptyList()
                durationMs = 0
                inMs = 0
                outMs = 0
                pendingVolumeDb = 0
                pendingVolumeSource = null
                estimatedTrackLufs = null
                playheadMs = 0
                hasError = true
            }
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisJob?.cancel()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    isPlayingWave = false
                    playheadMs = durationMs.coerceAtLeast(0)
                    WaveformSessionPrefs.savePlayhead(context, playheadMs)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlayingWave, durationMs) {
        if (!isPlayingWave) return@LaunchedEffect
        while (isPlayingWave) {
            val rawPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val safePos = if (durationMs > 0) rawPos.coerceAtMost(durationMs.toLong()) else rawPos
            playheadMs = safePos.toInt()
            delay(50)
        }
    }

    suspend fun resolveSongTarget(songId: String): Pair<Uri, String>? = withContext(Dispatchers.IO) {
        val cleanSongId = songId.trim().ifBlank { return@withContext null }
        val song = smpLibraryScanner.findSongById(cleanSongId) ?: return@withContext null
        val runtimeTrackUri = SongIdKeyResolver.resolveRuntimeTrackUri(appContext, cleanSongId)
            ?: return@withContext null
        val runtimeUri = runCatching { Uri.parse(runtimeTrackUri) }.getOrNull() ?: return@withContext null
        runtimeUri to song.title
    }

    LaunchedEffect(initialSongId) {
        val songId = initialSongId?.trim()?.takeIf { it.isNotBlank() }
        if (songId == null) {
            selectedSongId = null
            selectedUri = null
            selectedName = ""
            peaks = emptyList()
            hasError = false
            isLoading = false
            durationMs = 0
            inMs = 0
            outMs = 0
            pendingVolumeDb = 0
            pendingVolumeSource = null
            estimatedTrackLufs = null
            playheadMs = 0
            return@LaunchedEffect
        }
        if (songId == lastInitialSongId) return@LaunchedEffect
        lastInitialSongId = songId
        val resolved = resolveSongTarget(songId)
        if (resolved == null) {
            selectedSongId = songId
            selectedUri = null
            selectedName = ""
            peaks = emptyList()
            hasError = true
            isLoading = false
            durationMs = 0
            inMs = 0
            outMs = 0
            pendingVolumeDb = 0
            pendingVolumeSource = null
            estimatedTrackLufs = null
            playheadMs = 0
            return@LaunchedEffect
        }
        val (resolvedUri, resolvedTitle) = resolved
        loadAudioUri(songId = songId, uri = resolvedUri, displayNameHint = resolvedTitle)
    }

    fun nudgePlayhead(deltaMs: Int) {
        if (durationMs <= 0) return
        val newPos = (playheadMs + deltaMs).coerceIn(0, durationMs)
        playheadMs = newPos
        WaveformSessionPrefs.savePlayhead(context, newPos)
        exoPlayer.seekTo(newPos.toLong())
        if (isPlayingWave) exoPlayer.play()
    }

    suspend fun saveWaveformTrimEdit(
        startMs: Int,
        endMs: Int,
        volumeDb: Int,
        volumeSource: String?,
        persistVolume: Boolean,
        successMessage: String
    ) {
        val sourceUri = selectedUri ?: return
        val songId = selectedSongId ?: return
        if (durationMs <= 0) return
        withContext(Dispatchers.IO) {
            val songUnit = smpLibraryScanner.findSongById(songId)
            val savedToSmp = songUnit?.let { song ->
                SmpConfig.writeTrimPlaybackToSongUnit(
                    songUnit = song,
                    startMs = startMs,
                    endMs = endMs
                )
            } == true
            if (BuildConfig.DEBUG) {
                val key = EditSoundPrefs.trimKeyForUri(sourceUri)
                Log.d(
                    "TRIM",
                    "save key=$key uri=$sourceUri entryMs=$startMs exitMs=$endMs store=${if (savedToSmp) "SmpConfig" else "EditSoundPrefs"}"
                )
            }
            if (!savedToSmp) {
                EditSoundPrefs.save(
                    context = appContext,
                    uri = sourceUri,
                    startMs = startMs,
                    endMs = endMs
                )
            }
            if (persistVolume) {
                TrackVolumePrefs.saveDb(
                    context = appContext,
                    uri = sourceUri.toString(),
                    db = volumeDb,
                    source = volumeSource ?: SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL
                )
            }
        }
        Toast.makeText(
            context,
            successMessage,
            Toast.LENGTH_SHORT
        ).show()
    }

    val background = Brush.verticalGradient(
        listOf(Color(0xFF171717), Color(0xFF111111), Color(0xFF171510))
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.waveform_title),
                color = Color(0xFFF5F5F5),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = if (selectedName.isBlank()) stringResource(R.string.waveform_no_song_selected) else selectedName,
                color = Color(0xFFBFC4C8),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(
                        color = Color(0xFF101419),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                when {
                    selectedUri == null -> {
                        CenterText(
                            if (hasError) {
                                stringResource(R.string.waveform_generate_error)
                            } else {
                                stringResource(R.string.waveform_no_song_selected)
                            }
                        )
                    }

                    isLoading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.waveform_analyzing),
                                color = Color(0xFFE9EEF3),
                                fontSize = 12.sp
                            )
                        }
                    }

                    hasError -> {
                        CenterText(stringResource(R.string.waveform_generate_error))
                    }

                    peaks.isEmpty() -> {
                        CenterText(stringResource(R.string.waveform_no_data))
                    }

                    else -> {
                        WaveformCanvas(
                            peaks = peaks,
                            zoom = zoom,
                            durationMs = durationMs,
                            inMs = inMs,
                            outMs = outMs,
                            playheadMs = playheadMs,
                            onTapTimeMs = { tapTimeMs ->
                                val safeTap = tapTimeMs.coerceIn(0, durationMs.coerceAtLeast(0))
                                logWaveformGesture(
                                    "SEEK_TAP",
                                    "tapTimeMs=$tapTimeMs safeTap=$safeTap isPlayingWave=$isPlayingWave"
                                )
                                playheadMs = safeTap
                                WaveformSessionPrefs.savePlayhead(context, safeTap)
                                exoPlayer.seekTo(safeTap.toLong())
                                if (isPlayingWave) {
                                    exoPlayer.play()
                                }
                            },
                            onDragInMs = { draggedInMs ->
                                val (newIn, newOut) = normalizeInOut(
                                    inMs = draggedInMs,
                                    outMs = outMs,
                                    durationMs = durationMs
                                )
                                inMs = newIn
                                outMs = newOut
                            },
                            onDragOutMs = { draggedOutMs ->
                                val (newIn, newOut) = normalizeInOut(
                                    inMs = inMs,
                                    outMs = draggedOutMs,
                                    durationMs = durationMs
                                )
                                inMs = newIn
                                outMs = newOut
                            },
                            onTogglePlayPause = {
                                if (selectedUri == null || durationMs <= 0) return@WaveformCanvas
                                logWaveformGesture(
                                    "TOGGLE_PLAY",
                                    "isPlayingWaveBefore=$isPlayingWave playheadMs=$playheadMs durationMs=$durationMs"
                                )
                                if (isPlayingWave) {
                                    exoPlayer.pause()
                                    val current = exoPlayer.currentPosition.coerceAtLeast(0L).toInt()
                                    playheadMs = current.coerceIn(0, durationMs)
                                    isPlayingWave = false
                                } else {
                                    val safePlayhead = playheadMs.coerceIn(0, durationMs)
                                    playheadMs = safePlayhead
                                    exoPlayer.seekTo(safePlayhead.toLong())
                                    exoPlayer.playWhenReady = true
                                    exoPlayer.play()
                                    isPlayingWave = true
                                }
                            },
                            onSetInFromPlayhead = {
                                val (newIn, newOut) = normalizeInOut(
                                    inMs = playheadMs,
                                    outMs = outMs,
                                    durationMs = durationMs
                                )
                                inMs = newIn
                                outMs = newOut
                            },
                            onSwipeLeftSetIn = {
                                val (newIn, newOut) = normalizeInOut(
                                    inMs = playheadMs,
                                    outMs = outMs,
                                    durationMs = durationMs
                                )
                                inMs = newIn
                                outMs = newOut
                            },
                            onSwipeRightSetOut = {
                                val (newIn, newOut) = normalizeInOut(
                                    inMs = inMs,
                                    outMs = playheadMs,
                                    durationMs = durationMs
                                )
                                inMs = newIn
                                outMs = newOut
                            },
                            restoredScrollPx = restoredScrollPx,
                            scrollRestoreKey = selectedUri?.toString(),
                            onScrollChanged = { px ->
                                WaveformSessionPrefs.saveScroll(context, px)
                            },
                            onZoomChanged = { nextZoom ->
                                zoom = nextZoom
                                WaveformSessionPrefs.saveZoom(context, nextZoom)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val controlsEnabled = selectedUri != null && durationMs > 0
                IconButton(
                    onClick = { nudgePlayhead(-stepMs) },
                    enabled = controlsEnabled
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = null
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { nudgePlayhead(stepMs) },
                    enabled = controlsEnabled
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (selectedUri == null || durationMs <= 0) return@TextButton
                        val (newIn, newOut) = normalizeInOut(
                            inMs = playheadMs,
                            outMs = outMs,
                            durationMs = durationMs
                        )
                        inMs = newIn
                        outMs = newOut
                    },
                    enabled = selectedUri != null && durationMs > 0,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${stringResource(R.string.waveform_in_prefix)} ${formatWaveformMs(inMs)}", fontSize = 12.sp)
                }

                TextButton(
                    onClick = {
                        stepMs = when (stepMs) {
                            10 -> 25
                            25 -> 50
                            50 -> 100
                            100 -> 250
                            else -> 10
                        }
                    },
                    enabled = selectedUri != null && durationMs > 0,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.waveform_step_ms, stepMs),
                        fontSize = 11.sp
                    )
                }

                TextButton(
                    onClick = {
                        if (selectedUri == null || durationMs <= 0) return@TextButton
                        val (newIn, newOut) = normalizeInOut(
                            inMs = inMs,
                            outMs = playheadMs,
                            durationMs = durationMs
                        )
                        inMs = newIn
                        outMs = newOut
                    },
                    enabled = selectedUri != null && durationMs > 0,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${stringResource(R.string.waveform_out_prefix)} ${formatWaveformMs(outMs)}", fontSize = 12.sp)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val controlsEnabled = selectedUri != null && durationMs > 0
                val detectEnabled = controlsEnabled && peaks.isNotEmpty() && !isLoading && !isDetectingSilence
                val matchVolumeEnabled = controlsEnabled && peaks.isNotEmpty() && !isLoading && !isMatchingVolume
                val hasOutTrim = controlsEnabled && outMs in 1 until durationMs
                val effectiveLufs = estimatedTrackLufs?.plus(pendingVolumeDb.toFloat())

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            if (selectedUri == null || durationMs <= 0) return@TextButton
                            val target = if (durationMs > 0 && inMs > 0) inMs else 0
                            val safeTarget = target.coerceIn(0, durationMs.coerceAtLeast(0))
                            playheadMs = safeTarget
                            WaveformSessionPrefs.savePlayhead(context, safeTarget)
                            exoPlayer.seekTo(safeTarget.toLong())
                            if (isPlayingWave) {
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                            } else {
                                exoPlayer.pause()
                                exoPlayer.playWhenReady = false
                            }
                        },
                        enabled = selectedUri != null && durationMs > 0,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("⏮", fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = {
                            if (selectedUri == null || durationMs <= 0) return@TextButton
                            if (isPlayingWave) {
                                exoPlayer.pause()
                                val current = exoPlayer.currentPosition.coerceAtLeast(0L).toInt()
                                playheadMs = current.coerceIn(0, durationMs)
                                WaveformSessionPrefs.savePlayhead(context, playheadMs)
                                isPlayingWave = false
                            } else {
                                val safePlayhead = playheadMs.coerceIn(0, durationMs)
                                playheadMs = safePlayhead
                                WaveformSessionPrefs.savePlayhead(context, safePlayhead)
                                exoPlayer.seekTo(safePlayhead.toLong())
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                                isPlayingWave = true
                            }
                        },
                        enabled = selectedUri != null && durationMs > 0,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.waveform_play), fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = {
                            exoPlayer.pause()
                            exoPlayer.playWhenReady = false
                            val current = exoPlayer.currentPosition.coerceAtLeast(0L).toInt()
                            playheadMs = if (durationMs > 0) current.coerceIn(0, durationMs) else current
                            WaveformSessionPrefs.savePlayhead(context, playheadMs)
                            isPlayingWave = false
                            if (
                                selectedSongId != null &&
                                selectedSongId == currentPlayingSongId
                            ) {
                                onStopCurrentPlayback()
                            }
                        },
                        enabled = selectedUri != null && durationMs > 0,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.waveform_stop), fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            if (!detectEnabled) return@TextButton
                            val snapshotPeaks = peaks.toList()
                            val snapshotDurationMs = durationMs
                            val currentInMs = inMs
                            val currentOutMs = outMs
                            isDetectingSilence = true
                            scope.launch {
                                try {
                                    val detection = withContext(Dispatchers.Default) {
                                        detectSilenceTrimFromPeaks(
                                            peaks = snapshotPeaks,
                                            durationMs = snapshotDurationMs
                                        )
                                    } ?: return@launch

                                    val targetInMs = detection.inMs ?: currentInMs
                                    val targetOutMs = detection.outMs ?: currentOutMs
                                    if (targetInMs == currentInMs && targetOutMs == currentOutMs) {
                                        return@launch
                                    }

                                    val (newInMs, newOutMs) = normalizeInOut(
                                        inMs = targetInMs,
                                        outMs = targetOutMs,
                                        durationMs = snapshotDurationMs
                                    )
                                    if (newInMs != inMs || newOutMs != outMs) {
                                        inMs = newInMs
                                        outMs = newOutMs
                                    }
                                } finally {
                                    isDetectingSilence = false
                                }
                            }
                        },
                        enabled = detectEnabled,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.waveform_detect_silence), fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = {
                            if (selectedUri == null) return@TextButton
                            if (durationMs <= 0) return@TextButton
                            outMs = durationMs
                            scope.launch {
                                saveWaveformTrimEdit(
                                    startMs = inMs,
                                    endMs = 0,
                                    volumeDb = pendingVolumeDb,
                                    volumeSource = pendingVolumeSource,
                                    persistVolume = false,
                                    successMessage = context.getString(R.string.waveform_out_cleared)
                                )
                            }
                        },
                        enabled = hasOutTrim,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.waveform_clear_out), fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = {
                            if (!matchVolumeEnabled) return@TextButton
                            val snapshotPeaks = peaks.toFloatArray()
                            isMatchingVolume = true
                            scope.launch {
                                try {
                                    val matchedVolumeDb = withContext(Dispatchers.Default) {
                                        estimateMatchedVolumeDbFromPeaks(snapshotPeaks)
                                    } ?: return@launch
                                    pendingVolumeDb = matchedVolumeDb
                                    pendingVolumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.waveform_level_adjusted,
                                            matchedVolumeDb.toFloat()
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    isMatchingVolume = false
                                }
                            }
                        },
                        enabled = matchVolumeEnabled,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.waveform_match_volume), fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = {
                            if (durationMs <= 0) return@TextButton
                            if (EditionConfig.isLite) {
                                showWaveformSaveProDialog = true
                                return@TextButton
                            }
                            scope.launch {
                                saveWaveformTrimEdit(
                                    startMs = inMs,
                                    endMs = outMs,
                                    volumeDb = pendingVolumeDb,
                                    volumeSource = pendingVolumeSource,
                                    persistVolume = true,
                                    successMessage = context.getString(R.string.waveform_trim_saved)
                                )
                            }
                        },
                        enabled = selectedUri != null && durationMs > 0,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.waveform_save), fontSize = 12.sp)
                    }
                }

                if (showWaveformSaveProDialog) {
                    AlertDialog(
                        onDismissRequest = { showWaveformSaveProDialog = false },
                        title = { Text(text = sWaveformSaveProTitle) },
                        text = { Text(text = sWaveformSaveProMessage) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showWaveformSaveProDialog = false
                                    openUpgradeToPro()
                                }
                            ) {
                                Text(sUpgradeToPro)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showWaveformSaveProDialog = false }
                            ) {
                                Text(stringResource(R.string.common_close))
                            }
                        }
                    )
                }

                LufsLevelIndicator(
                    currentLufs = effectiveLufs,
                    targetLufs = MATCH_VOLUME_TARGET_LUFS,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
@OptIn(FlowPreview::class)
private fun WaveformCanvas(
    peaks: List<Float>,
    zoom: Float,
    durationMs: Int,
    inMs: Int,
    outMs: Int,
    playheadMs: Int,
    onTapTimeMs: (Int) -> Unit,
    onDragInMs: (Int) -> Unit,
    onDragOutMs: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSetInFromPlayhead: () -> Unit,
    onSwipeLeftSetIn: () -> Unit,
    onSwipeRightSetOut: () -> Unit,
    restoredScrollPx: Int?,
    scrollRestoreKey: String?,
    onScrollChanged: (Int) -> Unit,
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var lastTapUptimeMs by remember { mutableStateOf(0L) }
    var lastTapAbsX by remember { mutableStateOf(Float.NaN) }
    var isDraggingHandle by remember { mutableStateOf(false) }
    var isLongPressTrimDragActive by remember { mutableStateOf(false) }
    var isTransformGestureActive by remember { mutableStateOf(false) }
    var activeTrimHandleForDebug by remember { mutableStateOf(DragTarget.NONE) }
    var waveformCenterFraction by remember(peaks, durationMs) { mutableStateOf(0.5f) }
    var appliedViewportRestoreKey by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val handleHitPx = with(density) { 24.dp.toPx() }
        val swipeThresholdPx = with(density) { 28.dp.toPx() }
        val latestZoom by rememberUpdatedState(zoom)
        val latestDurationMs by rememberUpdatedState(durationMs)
        val latestInMs by rememberUpdatedState(inMs)
        val latestOutMs by rememberUpdatedState(outMs)
        val latestPlayheadMs by rememberUpdatedState(playheadMs)
        val latestViewportWidthPx by rememberUpdatedState(viewportWidthPx)
        val latestHandleHitPx by rememberUpdatedState(handleHitPx)
        val latestSwipeThresholdPx by rememberUpdatedState(swipeThresholdPx)
        val latestLongPressTrimDragActive by rememberUpdatedState(isLongPressTrimDragActive)
        val latestTransformGestureActive by rememberUpdatedState(isTransformGestureActive)
        val latestActiveTrimHandleForDebug by rememberUpdatedState(activeTrimHandleForDebug)
        val latestOnTapTimeMs by rememberUpdatedState(onTapTimeMs)
        val latestOnDragInMs by rememberUpdatedState(onDragInMs)
        val latestOnDragOutMs by rememberUpdatedState(onDragOutMs)
        val latestOnSwipeLeftSetIn by rememberUpdatedState(onSwipeLeftSetIn)
        val latestOnSwipeRightSetOut by rememberUpdatedState(onSwipeRightSetOut)
        val latestOnZoomChanged by rememberUpdatedState(onZoomChanged)

        fun visibleWindow(currentZoom: Float, currentCenter: Float): Triple<Float, Float, Float> {
            val visibleFraction = 1f / currentZoom.coerceAtLeast(1f)
            val startFraction = (currentCenter - visibleFraction / 2f).coerceIn(0f, 1f)
            val endFraction = (startFraction + visibleFraction).coerceIn(0f, 1f)
            val effectiveEndFraction = if (endFraction <= startFraction) 1f else endFraction
            return Triple(visibleFraction, startFraction, effectiveEndFraction)
        }

        fun centerBounds(currentZoom: Float): Pair<Float, Float> {
            val visibleFraction = 1f / currentZoom.coerceAtLeast(1f)
            val minCenter = visibleFraction / 2f
            val maxCenter = 1f - minCenter
            return minCenter to maxCenter
        }

        fun viewportScrollPx(currentZoom: Float, currentCenter: Float): Int {
            val (_, startFraction, _) = visibleWindow(currentZoom, currentCenter)
            val virtualCanvasWidthPx = (viewportWidthPx * currentZoom).coerceAtLeast(1f)
            return (startFraction * virtualCanvasWidthPx).roundToInt().coerceAtLeast(0)
        }

        LaunchedEffect(scrollRestoreKey, restoredScrollPx, zoom, durationMs, viewportWidthPx) {
            val key = scrollRestoreKey ?: return@LaunchedEffect
            val savedScroll = restoredScrollPx ?: return@LaunchedEffect
            if (appliedViewportRestoreKey == key) return@LaunchedEffect
            val visibleFraction = 1f / zoom.coerceAtLeast(1f)
            val virtualCanvasWidthPx = (viewportWidthPx * zoom).coerceAtLeast(1f)
            val savedCenterFraction = if (zoom <= 1f) {
                0.5f
            } else {
                val rawCenter = (savedScroll.toFloat() + viewportWidthPx / 2f) / virtualCanvasWidthPx
                val minCenter = visibleFraction / 2f
                val maxCenter = 1f - minCenter
                rawCenter.coerceIn(minCenter, maxCenter)
            }
            waveformCenterFraction = savedCenterFraction
            logWaveformGesture(
                "VIEWPORT_RESTORE",
                "key=$key savedScroll=$savedScroll restoredCenter=$savedCenterFraction zoom=$zoom"
            )
            appliedViewportRestoreKey = key
        }

        LaunchedEffect(Unit) {
            snapshotFlow { viewportScrollPx(zoom, waveformCenterFraction) }
                .distinctUntilChanged()
                .debounce(120)
                .collect { onScrollChanged(it) }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                    .pointerInput(Unit) {
                        logWaveformGesture(
                            "TRANSFORM_POINTER_READY",
                            "viewportWidthPx=$latestViewportWidthPx durationMs=$latestDurationMs zoom=$latestZoom"
                        )
                        var transformStep = 0
                        awaitEachGesture {
                            var transformHandled = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount >= 2 && !latestLongPressTrimDragActive && !isDraggingHandle) {
                                    if (!transformHandled) {
                                        transformHandled = true
                                        isTransformGestureActive = true
                                        logWaveformGesture(
                                            "TRANSFORM_START",
                                            "pressedCount=$pressedCount zoom=$latestZoom center=$waveformCenterFraction"
                                        )
                                    }
                                    transformStep += 1
                                    val pressedChanges = event.changes.filter { it.pressed && it.previousPressed }
                                    val currentCentroid = if (pressedChanges.isEmpty()) {
                                        Offset.Zero
                                    } else {
                                        val sumX = pressedChanges.sumOf { it.position.x.toDouble() }.toFloat()
                                        val sumY = pressedChanges.sumOf { it.position.y.toDouble() }.toFloat()
                                        Offset(sumX / pressedChanges.size.toFloat(), sumY / pressedChanges.size.toFloat())
                                    }
                                    val previousCentroid = if (pressedChanges.isEmpty()) {
                                        Offset.Zero
                                    } else {
                                        val sumX = pressedChanges.sumOf { it.previousPosition.x.toDouble() }.toFloat()
                                        val sumY = pressedChanges.sumOf { it.previousPosition.y.toDouble() }.toFloat()
                                        Offset(sumX / pressedChanges.size.toFloat(), sumY / pressedChanges.size.toFloat())
                                    }
                                    fun averageDistanceToCentroid(current: Boolean): Float {
                                        if (pressedChanges.isEmpty()) return 0f
                                        val centroid = if (current) currentCentroid else previousCentroid
                                        val total = pressedChanges.sumOf { pointer ->
                                            val point = if (current) pointer.position else pointer.previousPosition
                                            hypot(
                                                (point.x - centroid.x).toDouble(),
                                                (point.y - centroid.y).toDouble()
                                            )
                                        }.toFloat()
                                        return total / pressedChanges.size.toFloat()
                                    }
                                    val previousDistance = averageDistanceToCentroid(current = false)
                                    val currentDistance = averageDistanceToCentroid(current = true)
                                    val zoomChange = if (previousDistance > 0f) {
                                        currentDistance / previousDistance
                                    } else {
                                        1f
                                    }
                                    val pan = currentCentroid - previousCentroid
                                    val centroid = currentCentroid
                                    val currentCanvasWidthPx = size.width.coerceAtLeast(1).toFloat()
                                    val previousZoom = latestZoom
                                    val nextZoom = (previousZoom * zoomChange).coerceIn(ZOOM_MIN, ZOOM_MAX)
                                    if (nextZoom != previousZoom) {
                                        latestOnZoomChanged(nextZoom)
                                    }
                                    val visibleFraction = 1f / nextZoom
                                    val panFraction = if (currentCanvasWidthPx > 0f) {
                                        -pan.x / currentCanvasWidthPx * visibleFraction
                                    } else {
                                        0f
                                    }
                                    val (minCenter, maxCenter) = centerBounds(nextZoom)
                                    waveformCenterFraction = if (nextZoom <= 1f) {
                                        0.5f
                                    } else {
                                        (waveformCenterFraction + panFraction).coerceIn(minCenter, maxCenter)
                                    }
                                    if (transformStep == 1 || transformStep % 8 == 0) {
                                        logWaveformGesture(
                                            "TRANSFORM_EVENT",
                                            "step=$transformStep pointers=$pressedCount centroid=(${centroid.x.roundToInt()},${centroid.y.roundToInt()}) panX=${pan.x.roundToInt()} zoomChange=$zoomChange nextZoom=$nextZoom center=$waveformCenterFraction isLongPressTrimDragActive=$latestLongPressTrimDragActive activeTrimHandle=$latestActiveTrimHandleForDebug"
                                        )
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                                if (pressedCount < 2) {
                                    if (transformHandled) {
                                        logWaveformGesture(
                                            "TRANSFORM_END",
                                            "center=$waveformCenterFraction zoom=$latestZoom"
                                        )
                                        isTransformGestureActive = false
                                    }
                                    if (event.changes.none { it.pressed }) break
                                    if (transformHandled) break
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        logWaveformGesture(
                            "LONG_PRESS_DRAG_READY",
                            "viewportWidthPx=$latestViewportWidthPx durationMs=$latestDurationMs inMs=$latestInMs outMs=$latestOutMs"
                        )
                        var activeLongPressTarget = DragTarget.NONE
                        var longPressDragStep = 0
                        fun xToTimeMs(x: Float, widthPx: Float): Int {
                            val currentDurationMs = latestDurationMs
                            val (_, startFraction, effectiveEndFraction) =
                                visibleWindow(latestZoom, waveformCenterFraction)
                            val localFraction = if (widthPx <= 0f) {
                                0f
                            } else {
                                (x / widthPx).coerceIn(0f, 1f)
                            }
                            val selectedFraction =
                                startFraction + localFraction * (effectiveEndFraction - startFraction)
                            return (selectedFraction * currentDurationMs).roundToInt()
                                .coerceIn(0, currentDurationMs)
                        }
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                if (latestTransformGestureActive) return@detectDragGesturesAfterLongPress
                                if (latestDurationMs <= 0) return@detectDragGesturesAfterLongPress
                                val currentInMs = latestInMs
                                val currentOutMs = latestOutMs
                                val widthPx = size.width.coerceAtLeast(1).toFloat()
                                val safeX = offset.x.coerceIn(0f, widthPx)
                                val touchTimeMs = xToTimeMs(safeX, widthPx)
                                val inDistance = abs(touchTimeMs - currentInMs)
                                val outDistance = abs(touchTimeMs - currentOutMs)
                                activeLongPressTarget =
                                    if (inDistance <= outDistance) DragTarget.IN else DragTarget.OUT
                                activeTrimHandleForDebug = activeLongPressTarget
                                isLongPressTrimDragActive = true
                                isDraggingHandle = true
                                longPressDragStep = 0
                                when (activeLongPressTarget) {
                                    DragTarget.IN -> latestOnDragInMs(touchTimeMs)
                                    DragTarget.OUT -> latestOnDragOutMs(touchTimeMs)
                                    DragTarget.NONE -> Unit
                                }
                                logWaveformGesture(
                                    "LONG_PRESS_START",
                                    "x=${offset.x.roundToInt()} y=${offset.y.roundToInt()} timeMs=$touchTimeMs distanceToIn=$inDistance distanceToOut=$outDistance chosen=$activeLongPressTarget zoom=$latestZoom center=$waveformCenterFraction isLongPressTrimDragActive=$isLongPressTrimDragActive"
                                )
                            },
                            onDragCancel = {
                                logWaveformGesture(
                                    "LONG_PRESS_CANCEL",
                                    "activeTrimHandle=$activeLongPressTarget inMs=$latestInMs outMs=$latestOutMs center=$waveformCenterFraction"
                                )
                                activeLongPressTarget = DragTarget.NONE
                                activeTrimHandleForDebug = DragTarget.NONE
                                isLongPressTrimDragActive = false
                                isDraggingHandle = false
                            },
                            onDragEnd = {
                                logWaveformGesture(
                                    "LONG_PRESS_END",
                                    "activeTrimHandle=$activeLongPressTarget inMs=$latestInMs outMs=$latestOutMs center=$waveformCenterFraction"
                                )
                                activeLongPressTarget = DragTarget.NONE
                                activeTrimHandleForDebug = DragTarget.NONE
                                isLongPressTrimDragActive = false
                                isDraggingHandle = false
                            },
                            onDrag = { change, _ ->
                                val widthPx = size.width.coerceAtLeast(1).toFloat()
                                val safeX = change.position.x.coerceIn(0f, widthPx)
                                val timeMs = xToTimeMs(safeX, widthPx)
                                longPressDragStep += 1
                                when (activeLongPressTarget) {
                                    DragTarget.IN -> latestOnDragInMs(timeMs)
                                    DragTarget.OUT -> latestOnDragOutMs(timeMs)
                                    DragTarget.NONE -> Unit
                                }
                                if (longPressDragStep == 1 || longPressDragStep % 8 == 0) {
                                    logWaveformGesture(
                                        "LONG_PRESS_DRAG",
                                        "step=$longPressDragStep activeTrimHandle=$activeLongPressTarget x=${change.position.x.roundToInt()} timeMs=$timeMs inMs=${latestInMs} outMs=${latestOutMs} zoom=$latestZoom center=$waveformCenterFraction"
                                    )
                                }
                                change.consume()
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        logWaveformGesture(
                            "PRIMARY_POINTER_READY",
                            "viewportWidthPx=$latestViewportWidthPx durationMs=$latestDurationMs inMs=$latestInMs outMs=$latestOutMs zoom=$latestZoom"
                        )
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var pointerId = down.id
                            if (latestDurationMs <= 0) return@awaitEachGesture
                            val widthPx = size.width.coerceAtLeast(1).toFloat()
                            val currentDurationMs = latestDurationMs
                            val currentHandleHitPx = latestHandleHitPx
                            val currentSwipeThresholdPx = latestSwipeThresholdPx
                            val (_, startFraction, effectiveEndFraction) =
                                visibleWindow(latestZoom, waveformCenterFraction)
                            fun timeMsToX(timeMs: Int): Float? {
                                val fraction =
                                    timeMs.coerceIn(0, currentDurationMs).toFloat() / currentDurationMs.toFloat()
                                if (fraction < startFraction || fraction > effectiveEndFraction) return null
                                return ((fraction - startFraction) /
                                    (effectiveEndFraction - startFraction)) * widthPx
                            }
                            fun xToTimeMs(x: Float): Int {
                                val localFraction = if (widthPx <= 0f) 0f else (x / widthPx).coerceIn(0f, 1f)
                                val selectedFraction =
                                    startFraction + localFraction * (effectiveEndFraction - startFraction)
                                return (selectedFraction * currentDurationMs).roundToInt().coerceIn(0, currentDurationMs)
                            }
                            val downAbsX = down.position.x.coerceIn(0f, widthPx)
                            val inAbsX = timeMsToX(latestInMs)
                            val outAbsX = timeMsToX(latestOutMs)
                            val directHandleTarget = when {
                                inAbsX != null && abs(downAbsX - inAbsX) <= currentHandleHitPx -> DragTarget.IN
                                outAbsX != null && abs(downAbsX - outAbsX) <= currentHandleHitPx -> DragTarget.OUT
                                else -> DragTarget.NONE
                            }
                            val nearestDragTarget = run {
                                val inDistance = abs(xToTimeMs(downAbsX) - latestInMs)
                                val outDistance = abs(xToTimeMs(downAbsX) - latestOutMs)
                                if (inDistance <= outDistance) DragTarget.IN else DragTarget.OUT
                            }
                            logWaveformGesture(
                                "PRIMARY_DOWN",
                                "x=${down.position.x.roundToInt()} y=${down.position.y.roundToInt()} directHandle=$directHandleTarget nearest=$nearestDragTarget zoom=$latestZoom center=$waveformCenterFraction isLongPressTrimDragActive=$latestLongPressTrimDragActive activeTrimHandle=$latestActiveTrimHandleForDebug"
                            )
                            if (directHandleTarget != DragTarget.NONE) {
                                activeTrimHandleForDebug = directHandleTarget
                                isDraggingHandle = true
                            }

                            var lastPos = down.position
                            var upUptimeMs = down.uptimeMillis
                            var upAbsX = downAbsX
                            var maxDistanceFromDown = 0f
                            var deltaXFromDown = 0f
                            var deltaYFromDown = 0f
                            var handleMoved = false
                            var singleFingerPanActive = false
                            var waveformActionsLockedByLongPressTrim = false
                            var waveformActionsLockedByTransform = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val pointerStillThere = event.changes.fastAny { it.id == pointerId }
                                val change = if (pointerStillThere) {
                                    event.changes.firstOrNull { it.id == pointerId }
                                } else {
                                    event.changes.firstOrNull()
                                } ?: break
                                pointerId = change.id

                                val currentPos = change.position
                                val fromDownX = currentPos.x - down.position.x
                                val fromDownY = currentPos.y - down.position.y
                                deltaXFromDown = fromDownX
                                deltaYFromDown = fromDownY
                                maxDistanceFromDown = max(
                                    maxDistanceFromDown,
                                    hypot(fromDownX.toDouble(), fromDownY.toDouble()).toFloat()
                                )

                                val activePressedCount = event.changes.count { it.pressed }

                                if (latestTransformGestureActive && directHandleTarget == DragTarget.NONE) {
                                    if (!waveformActionsLockedByTransform) {
                                        logWaveformGesture(
                                            "PRIMARY_LOCKED_BY_TRANSFORM",
                                            "x=${currentPos.x.roundToInt()} y=${currentPos.y.roundToInt()} pressedCount=$activePressedCount center=$waveformCenterFraction"
                                        )
                                    }
                                    waveformActionsLockedByTransform = true
                                    lastPos = currentPos
                                    upUptimeMs = change.uptimeMillis
                                    upAbsX = currentPos.x.coerceIn(0f, widthPx)
                                    if (!change.pressed) break
                                    continue
                                }

                                if (isLongPressTrimDragActive && directHandleTarget == DragTarget.NONE) {
                                    if (!waveformActionsLockedByLongPressTrim) {
                                        logWaveformGesture(
                                            "PRIMARY_LOCKED_BY_LONG_PRESS",
                                            "x=${currentPos.x.roundToInt()} y=${currentPos.y.roundToInt()} center=$waveformCenterFraction activeTrimHandle=$activeTrimHandleForDebug"
                                        )
                                    }
                                    waveformActionsLockedByLongPressTrim = true
                                    lastPos = currentPos
                                    upUptimeMs = change.uptimeMillis
                                    upAbsX = currentPos.x.coerceIn(0f, widthPx)
                                    if (!change.pressed) break
                                    continue
                                }

                                val absX = currentPos.x.coerceIn(0f, widthPx)
                                val timeMs = xToTimeMs(absX)
                                val horizontalDragDominant = abs(deltaXFromDown) > abs(deltaYFromDown)

                                if (
                                    directHandleTarget == DragTarget.NONE &&
                                    latestZoom > 1f &&
                                    (
                                        singleFingerPanActive ||
                                            (
                                                maxDistanceFromDown > viewConfiguration.touchSlop &&
                                                    horizontalDragDominant
                                                )
                                        )
                                ) {
                                    val panDeltaX = currentPos.x - lastPos.x
                                    val visibleFraction = 1f / latestZoom.coerceAtLeast(1f)
                                    val panFraction = if (widthPx > 0f) {
                                        -panDeltaX / widthPx * visibleFraction
                                    } else {
                                        0f
                                    }
                                    val (minCenter, maxCenter) = centerBounds(latestZoom)
                                    singleFingerPanActive = true
                                    waveformCenterFraction = (waveformCenterFraction + panFraction)
                                        .coerceIn(minCenter, maxCenter)
                                    change.consume()
                                    lastPos = currentPos
                                    upUptimeMs = change.uptimeMillis
                                    upAbsX = absX
                                    if (!change.pressed) break
                                    continue
                                }

                                when (directHandleTarget) {
                                    DragTarget.IN -> {
                                        if (maxDistanceFromDown > viewConfiguration.touchSlop) {
                                            isDraggingHandle = true
                                            handleMoved = true
                                            activeTrimHandleForDebug = DragTarget.IN
                                            latestOnDragInMs(timeMs)
                                            logWaveformGesture(
                                                "DIRECT_HANDLE_DRAG",
                                                "target=IN x=${currentPos.x.roundToInt()} timeMs=$timeMs inMs=${latestInMs} outMs=${latestOutMs} zoom=$latestZoom center=$waveformCenterFraction"
                                            )
                                            change.consume()
                                        }
                                    }

                                    DragTarget.OUT -> {
                                        if (maxDistanceFromDown > viewConfiguration.touchSlop) {
                                            isDraggingHandle = true
                                            handleMoved = true
                                            activeTrimHandleForDebug = DragTarget.OUT
                                            latestOnDragOutMs(timeMs)
                                            logWaveformGesture(
                                                "DIRECT_HANDLE_DRAG",
                                                "target=OUT x=${currentPos.x.roundToInt()} timeMs=$timeMs inMs=${latestInMs} outMs=${latestOutMs} zoom=$latestZoom center=$waveformCenterFraction"
                                            )
                                            change.consume()
                                        }
                                    }

                                    DragTarget.NONE -> Unit
                                }

                                lastPos = currentPos
                                upUptimeMs = change.uptimeMillis
                                upAbsX = absX
                                if (!change.pressed) break
                            }

                            if (handleMoved) {
                                logWaveformGesture(
                                    "DIRECT_HANDLE_END",
                                    "activeTrimHandle=$activeTrimHandleForDebug inMs=${latestInMs} outMs=${latestOutMs}"
                                )
                                activeTrimHandleForDebug = DragTarget.NONE
                                isDraggingHandle = false
                                return@awaitEachGesture
                            }

                            if (waveformActionsLockedByLongPressTrim) {
                                logWaveformGesture(
                                    "PRIMARY_SKIP_AFTER_LONG_PRESS",
                                    "activeTrimHandle=$activeTrimHandleForDebug inMs=${latestInMs} outMs=${latestOutMs}"
                                )
                                return@awaitEachGesture
                            }

                            if (waveformActionsLockedByTransform) {
                                logWaveformGesture(
                                    "PRIMARY_SKIP_AFTER_TRANSFORM",
                                    "center=$waveformCenterFraction zoom=$latestZoom"
                                )
                                return@awaitEachGesture
                            }

                            if (singleFingerPanActive) {
                                logWaveformGesture(
                                    "PRIMARY_PAN_END",
                                    "center=$waveformCenterFraction zoom=$latestZoom"
                                )
                                return@awaitEachGesture
                            }

                            val isTapLike = maxDistanceFromDown <= viewConfiguration.touchSlop
                            if (isTapLike) {
                                val doubleTapWindowMs = viewConfiguration.doubleTapTimeoutMillis
                                val isDoubleTap = lastTapUptimeMs > 0L &&
                                    (upUptimeMs - lastTapUptimeMs) <= doubleTapWindowMs &&
                                    !lastTapAbsX.isNaN() &&
                                    abs(upAbsX - lastTapAbsX) <= currentHandleHitPx

                                val safeAbsX = upAbsX.coerceIn(0f, widthPx)
                                val tapTimeMs = xToTimeMs(safeAbsX)
                                if (isDoubleTap) {
                                    logWaveformGesture(
                                        "DOUBLE_TAP_TRIM",
                                        "tapTimeMs=$tapTimeMs inMs=${latestInMs} outMs=${latestOutMs} nearest=${if (abs(tapTimeMs - latestInMs) <= abs(tapTimeMs - latestOutMs)) DragTarget.IN else DragTarget.OUT}"
                                    )
                                    val inDistance = abs(tapTimeMs - latestInMs)
                                    val outDistance = abs(tapTimeMs - latestOutMs)
                                    if (inDistance <= outDistance) {
                                        latestOnDragInMs(tapTimeMs)
                                    } else {
                                        latestOnDragOutMs(tapTimeMs)
                                    }
                                    lastTapUptimeMs = 0L
                                    lastTapAbsX = Float.NaN
                                } else {
                                    val pressDurationMs = upUptimeMs - down.uptimeMillis
                                    if (pressDurationMs < viewConfiguration.longPressTimeoutMillis) {
                                        logWaveformGesture(
                                            "SINGLE_TAP",
                                            "tapTimeMs=$tapTimeMs pressDurationMs=$pressDurationMs zoom=$latestZoom center=$waveformCenterFraction"
                                        )
                                        latestOnTapTimeMs(tapTimeMs)
                                        lastTapUptimeMs = upUptimeMs
                                        lastTapAbsX = upAbsX
                                    }
                                }
                            } else if (
                                !latestLongPressTrimDragActive &&
                                !latestTransformGestureActive &&
                                directHandleTarget == DragTarget.NONE &&
                                abs(deltaXFromDown) > abs(deltaYFromDown) &&
                                abs(deltaXFromDown) >= currentSwipeThresholdPx
                            ) {
                                logWaveformGesture(
                                    "SWIPE_ACTION",
                                    "direction=${if (deltaXFromDown < 0f) "LEFT_SET_IN" else "RIGHT_SET_OUT"} deltaX=$deltaXFromDown deltaY=$deltaYFromDown playheadMs=$latestPlayheadMs activeTrimHandle=$activeTrimHandleForDebug"
                                )
                                if (deltaXFromDown < 0f) latestOnSwipeLeftSetIn() else latestOnSwipeRightSetOut()
                            }
                            activeTrimHandleForDebug = DragTarget.NONE
                            isDraggingHandle = false
                        }
                    }
        ) {
            if (peaks.isEmpty()) return@Canvas

            val centerY = size.height / 2f
            val widthPx = size.width
            val heightPx = size.height
            val safeDuration = durationMs.coerceAtLeast(1)
            val maxIndex = peaks.lastIndex.coerceAtLeast(1)
            val (_, startFraction, effectiveEndFraction) =
                visibleWindow(zoom.coerceAtLeast(1f), waveformCenterFraction)
            val arrangementInColor = Color(0xFFFF1744)
            val arrangementOutColor = Color(0xFFFFC107)

            peaks.forEachIndexed { index, peak ->
                val positionFraction = index.toFloat() / maxIndex.toFloat()
                if (positionFraction < startFraction || positionFraction > effectiveEndFraction) {
                    return@forEachIndexed
                }
                val x = ((positionFraction - startFraction) /
                    (effectiveEndFraction - startFraction)) * widthPx
                val halfHeight = peak.coerceIn(0f, 1f) * (heightPx * 0.48f)
                drawLine(
                    color = Color(0xFF64D2FF),
                    start = Offset(x, centerY - halfHeight),
                    end = Offset(x, centerY + halfHeight),
                    strokeWidth = max(1f, widthPx / peaks.size.coerceAtLeast(1).toFloat() * 0.7f),
                    cap = StrokeCap.Round
                )
            }

            if (durationMs > 0) {
                val inFraction = inMs.coerceIn(0, durationMs).toFloat() / durationMs.toFloat()
                val outFraction = outMs.coerceIn(0, durationMs).toFloat() / durationMs.toFloat()
                val leftFraction = min(inFraction, outFraction).coerceIn(0f, 1f)
                val rightFraction = max(inFraction, outFraction).coerceIn(0f, 1f)
                val overlayStartFraction = max(leftFraction, startFraction)
                val overlayEndFraction = min(rightFraction, effectiveEndFraction)
                if (overlayEndFraction > overlayStartFraction) {
                    val overlayStartX = ((overlayStartFraction - startFraction) /
                        (effectiveEndFraction - startFraction)) * widthPx
                    val overlayEndX = ((overlayEndFraction - startFraction) /
                        (effectiveEndFraction - startFraction)) * widthPx
                    drawRect(
                        color = Color(0x224CD964),
                        topLeft = Offset(overlayStartX, 0f),
                        size = Size(overlayEndX - overlayStartX, heightPx)
                    )
                }

                val safePlayhead = playheadMs.coerceIn(0, durationMs)
                val playheadFraction = safePlayhead.toFloat() / durationMs.toFloat()
                if (playheadFraction in startFraction..effectiveEndFraction) {
                    val playheadX = ((playheadFraction - startFraction) /
                        (effectiveEndFraction - startFraction)) * widthPx
                    drawLine(
                        color = Color(0xFFE3F2FD),
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, heightPx),
                        strokeWidth = 3.5f
                    )
                }

                if (inFraction in startFraction..effectiveEndFraction) {
                    val inX = ((inFraction - startFraction) /
                        (effectiveEndFraction - startFraction)) * widthPx
                    drawLine(
                        color = arrangementInColor.copy(alpha = 0.3f),
                        start = Offset(inX, 0f),
                        end = Offset(inX, heightPx),
                        strokeWidth = 6.5f
                    )
                    drawLine(
                        color = arrangementInColor,
                        start = Offset(inX, 0f),
                        end = Offset(inX, heightPx),
                        strokeWidth = 3.5f
                    )
                    drawCircle(color = arrangementInColor, radius = 7f, center = Offset(inX, 9f))
                    drawCircle(
                        color = arrangementInColor,
                        radius = 7f,
                        center = Offset(inX, heightPx - 9f)
                    )
                }

                if (outFraction in startFraction..effectiveEndFraction) {
                    val outX = ((outFraction - startFraction) /
                        (effectiveEndFraction - startFraction)) * widthPx
                    drawLine(
                        color = arrangementOutColor.copy(alpha = 0.28f),
                        start = Offset(outX, 0f),
                        end = Offset(outX, heightPx),
                        strokeWidth = 6.5f
                    )
                    drawLine(
                        color = arrangementOutColor,
                        start = Offset(outX, 0f),
                        end = Offset(outX, heightPx),
                        strokeWidth = 3.5f
                    )
                    drawCircle(color = arrangementOutColor, radius = 7f, center = Offset(outX, 9f))
                    drawCircle(
                        color = arrangementOutColor,
                        radius = 7f,
                        center = Offset(outX, heightPx - 9f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color(0xFFCDD4DA),
            fontSize = 12.sp
        )
    }
}

private fun normalizeInOut(
    inMs: Int,
    outMs: Int,
    durationMs: Int
): Pair<Int, Int> {
    if (durationMs <= 0) return 0 to 0

    var safeIn = inMs.coerceIn(0, durationMs)
    var safeOut = outMs.coerceIn(0, durationMs)

    if (safeOut <= safeIn) {
        safeOut = (safeIn + 1_000).coerceAtMost(durationMs)
    }
    if (safeOut <= safeIn) {
        safeIn = (durationMs - 1).coerceAtLeast(0)
        safeOut = durationMs
    }
    return safeIn to safeOut
}

private data class SilenceTrimDetection(
    val inMs: Int?,
    val outMs: Int?
)

@Composable
private fun LufsLevelIndicator(
    currentLufs: Float?,
    targetLufs: Float,
    modifier: Modifier = Modifier
) {
    val scaleMin = -30f
    val scaleMax = -6f
    val targetFraction = ((targetLufs - scaleMin) / (scaleMax - scaleMin)).coerceIn(0f, 1f)
    val currentFraction = currentLufs
        ?.let { ((it - scaleMin) / (scaleMax - scaleMin)).coerceIn(0f, 1f) }
        ?: 0f
    val animatedCurrentFraction by animateFloatAsState(
        targetValue = currentFraction,
        animationSpec = tween(durationMillis = 280),
        label = "lufs_current_fraction"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentLufs != null) {
                    stringResource(R.string.waveform_lufs_current_value, currentLufs)
                } else {
                    stringResource(R.string.waveform_lufs_current)
                },
                color = Color(0xFFE9EEF3),
                fontSize = 11.sp
            )
            Text(
                text = stringResource(R.string.waveform_lufs_target_label),
                color = Color(0xFFB9C2C8),
                fontSize = 11.sp
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        ) {
            val barColor = Color(0xFF1E252C)
            val targetColor = Color(0xFFFFC857)
            val currentColor = Color(0xFF64D2FF)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .align(Alignment.Center)
                    .background(barColor, RoundedCornerShape(999.dp))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedCurrentFraction)
                    .height(8.dp)
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF2A3844), currentColor)
                        ),
                        RoundedCornerShape(999.dp)
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .align(Alignment.CenterStart)
                    .padding(start = maxWidth * targetFraction)
                    .background(targetColor, RoundedCornerShape(999.dp))
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(10.dp)
                    .align(Alignment.CenterStart)
                    .padding(start = maxWidth * animatedCurrentFraction)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = currentColor,
                        radius = size.minDimension / 2f
                    )
                }
            }
        }
    }
}

private fun estimateLufsFromPeaks(peaks: FloatArray): Float? {
    if (peaks.isEmpty()) return null
    var sumSquares = 0.0
    var nonZeroSeen = false
    peaks.forEach { peak ->
        val sample = peak.coerceIn(0f, 1f)
        if (sample > 0f) {
            nonZeroSeen = true
        }
        sumSquares += sample * sample
    }
    if (!nonZeroSeen) return null
    val rms = sqrt((sumSquares / peaks.size).toFloat()).coerceAtLeast(1e-4f)
    return (20f * (ln(rms) / ln(10f)))
}

private fun estimateMatchedVolumeDbFromPeaks(peaks: FloatArray): Int? {
    val currentLufs = estimateLufsFromPeaks(peaks) ?: return null
    val gainDb = MATCH_VOLUME_TARGET_LUFS - currentLufs
    return gainDb.roundToInt().coerceIn(MATCH_VOLUME_MIN_DB, MATCH_VOLUME_MAX_DB)
}

private fun detectSilenceTrimFromPeaks(
    peaks: List<Float>,
    durationMs: Int
): SilenceTrimDetection? {
    if (durationMs <= 0 || peaks.isEmpty()) return null
    val peakCount = peaks.size
    val msPerPeak = durationMs.toFloat() / peakCount.toFloat()
    val firstNonZeroIndex = peaks.indexOfFirst { it > 0f }
        .takeIf { it >= 0 }
    val lastNonZeroIndex = peaks.indexOfLast { it > 0f }
        .takeIf { it >= 0 }

    if (firstNonZeroIndex == null && lastNonZeroIndex == null) return null

    val detectedInMs = firstNonZeroIndex
        ?.takeIf { it > -100 }
        ?.let { (it * msPerPeak).roundToInt().coerceIn(0, durationMs) }

    val detectedOutMs = lastNonZeroIndex
        ?.takeIf { it < peakCount - 1 }
        ?.let { ((it + 1) * msPerPeak).roundToInt().coerceIn(0, durationMs) }

    if (detectedInMs == null && detectedOutMs == null) return null
    return SilenceTrimDetection(
        inMs = detectedInMs,
        outMs = detectedOutMs
    )
}

private fun formatWaveformMs(ms: Int): String {
    val totalSec = (ms.coerceAtLeast(0) / 1_000)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

private fun queryDurationMs(context: Context, uri: Uri): Int {
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

    if (fromRetriever > 0) {
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
                if (mime.startsWith("audio/")) {
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = max(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                    }
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
