package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.EditSoundPrefs
import com.patrick.lrcreader.core.WaveformSessionPrefs
import com.patrick.lrcreader.core.waveform.WaveformExtractor
import com.patrick.lrcreader.core.waveform.WaveformPeaksCache
import com.patrick.lrcreader.exo.BuildConfig
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val ZOOM_MIN = 1f
private const val ZOOM_MAX = 120f

private enum class DragTarget {
    NONE,
    IN,
    OUT
}

@Composable
fun WaveformPreviewScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    initialUri: Uri? = null,
    initialName: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appContext = context.applicationContext
    val exoPlayer = remember(appContext) {
        ExoPlayer.Builder(appContext).build().apply { playWhenReady = false }
    }

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
    var playheadMs by remember { mutableIntStateOf(0) }
    var stepMs by remember { mutableIntStateOf(50) }
    var isPlayingWave by remember { mutableStateOf(false) }
    var restoredScrollPx by remember { mutableStateOf<Int?>(null) }
    var lastInitialUri by remember { mutableStateOf<String?>(null) }

    fun loadAudioUri(uri: Uri, displayNameHint: String? = null, requestPersistable: Boolean) {
        val uriString = uri.toString()
        val restoreSameUriSession = WaveformSessionPrefs.loadUri(context) == uriString
        val restoredZoom = if (restoreSameUriSession) {
            WaveformSessionPrefs.loadZoom(context).coerceIn(ZOOM_MIN, ZOOM_MAX)
        } else {
            ZOOM_MIN
        }
        val restoredPlayhead = if (restoreSameUriSession) {
            WaveformSessionPrefs.loadPlayhead(context).coerceAtLeast(0)
        } else {
            0
        }
        val restoredScroll = if (restoreSameUriSession) {
            WaveformSessionPrefs.loadScroll(context).coerceAtLeast(0)
        } else {
            0
        }

        if (requestPersistable) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }

        selectedUri = uri
        selectedName = displayNameHint?.takeIf { it.isNotBlank() } ?: queryDisplayName(context, uri)
        zoom = restoredZoom
        restoredScrollPx = restoredScroll
        WaveformSessionPrefs.saveUri(context, uriString)
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
                if (durationMs > 0) {
                    playheadMs = restoredPlayhead.coerceIn(0, durationMs)
                    WaveformSessionPrefs.savePlayhead(context, playheadMs)
                    val saved = EditSoundPrefs.get(context, uri)
                    val (safeIn, safeOut) = normalizeInOut(
                        inMs = saved?.startMs ?: 0,
                        outMs = saved?.endMs ?: durationMs,
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
                playheadMs = 0
                hasError = true
            }
            isLoading = false
        }
    }

    val openAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loadAudioUri(uri = uri, displayNameHint = null, requestPersistable = true)
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

    LaunchedEffect(initialUri, initialName) {
        val uri = initialUri ?: return@LaunchedEffect
        val key = uri.toString()
        if (key == lastInitialUri) return@LaunchedEffect
        lastInitialUri = key
        loadAudioUri(uri = uri, displayNameHint = initialName, requestPersistable = false)
    }

    LaunchedEffect(initialUri, selectedUri) {
        if (initialUri != null || selectedUri != null) return@LaunchedEffect
        val savedUriString = WaveformSessionPrefs.loadUri(context) ?: return@LaunchedEffect
        val savedUri = runCatching { Uri.parse(savedUriString) }.getOrNull() ?: return@LaunchedEffect
        loadAudioUri(
            uri = savedUri,
            displayNameHint = WaveformSessionPrefs.loadTitle(context),
            requestPersistable = false
        )
    }

    fun nudgePlayhead(deltaMs: Int) {
        if (durationMs <= 0) return
        val newPos = (playheadMs + deltaMs).coerceIn(0, durationMs)
        playheadMs = newPos
        WaveformSessionPrefs.savePlayhead(context, newPos)
        exoPlayer.seekTo(newPos.toLong())
        if (isPlayingWave) exoPlayer.play()
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
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("← Retour", color = Color(0xFFF1F1F1), fontSize = 12.sp)
            }

            Text(
                text = "Waveform",
                color = Color(0xFFF5F5F5),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedButton(
                onClick = { openAudioLauncher.launch(arrayOf("audio/*")) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Choisir un fichier…", fontSize = 12.sp)
            }

            Text(
                text = if (selectedName.isBlank()) "Aucun fichier" else selectedName,
                color = Color(0xFFBFC4C8),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(252.dp)
                    .background(
                        color = Color(0xFF101419),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                when {
                    selectedUri == null -> {
                        CenterText("Choisis un titre")
                    }

                    isLoading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Analyse…",
                                color = Color(0xFFE9EEF3),
                                fontSize = 12.sp
                            )
                        }
                    }

                    hasError -> {
                        CenterText("Impossible de générer la waveform")
                    }

                    peaks.isEmpty() -> {
                        CenterText("Aucune donnée de waveform")
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Text(
                text = "Zoom x${"%.1f".format(zoom)}",
                color = Color(0xFFB9C2C8),
                fontSize = 11.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    modifier = Modifier.weight(1f),
                    value = zoom,
                    onValueChange = {
                        zoom = it
                        WaveformSessionPrefs.saveZoom(context, it)
                    },
                    valueRange = ZOOM_MIN..ZOOM_MAX
                )
                TextButton(
                    onClick = {
                        zoom = ZOOM_MIN
                        WaveformSessionPrefs.saveZoom(context, ZOOM_MIN)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Reset", fontSize = 11.sp)
                }
            }

            Text(
                text = "IN ${formatWaveformMs(inMs)}  •  OUT ${formatWaveformMs(outMs)}  •  CURSOR ${formatWaveformMs(playheadMs)}",
                color = Color(0xFFD5D8DC),
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val controlsEnabled = selectedUri != null && durationMs > 0
                TextButton(
                    onClick = { nudgePlayhead(-stepMs) },
                    enabled = controlsEnabled,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("◀︎", fontSize = 12.sp)
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
                    enabled = controlsEnabled,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Step ${stepMs}ms", fontSize = 11.sp)
                }

                TextButton(
                    onClick = { nudgePlayhead(stepMs) },
                    enabled = controlsEnabled,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("▶︎", fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
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
                    Text("IN = CURSOR", fontSize = 12.sp)
                }

                TextButton(
                    modifier = Modifier.weight(1f),
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
                    Text("OUT = CURSOR", fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    Text("▶ Play", fontSize = 12.sp)
                }

                TextButton(
                    onClick = {
                        exoPlayer.pause()
                        exoPlayer.playWhenReady = false
                        val current = exoPlayer.currentPosition.coerceAtLeast(0L).toInt()
                        playheadMs = if (durationMs > 0) current.coerceIn(0, durationMs) else current
                        WaveformSessionPrefs.savePlayhead(context, playheadMs)
                        isPlayingWave = false
                    },
                    enabled = selectedUri != null && durationMs > 0,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("⏹ Stop", fontSize = 12.sp)
                }

                TextButton(
                    onClick = {
                        val uri = selectedUri ?: return@TextButton
                        if (durationMs <= 0) return@TextButton
                        if (BuildConfig.DEBUG) {
                            val key = EditSoundPrefs.trimKeyForUri(uri)
                            Log.d(
                                "TRIM",
                                "save key=$key uri=$uri entryMs=$inMs exitMs=$outMs store=EditSoundPrefs"
                            )
                        }
                        EditSoundPrefs.save(
                            context = context,
                            uri = uri,
                            startMs = inMs,
                            endMs = outMs
                        )
                        Toast.makeText(context, "Réglages enregistrés ✅", Toast.LENGTH_SHORT).show()
                    },
                    enabled = selectedUri != null && durationMs > 0,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Save", fontSize = 12.sp)
                }
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
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var lastTapUptimeMs by remember { mutableStateOf(0L) }
    var lastTapAbsX by remember { mutableStateOf(Float.NaN) }
    var isDraggingHandle by remember { mutableStateOf(false) }
    var appliedScrollRestoreKey by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val canvasWidthDp = maxWidth * zoom
        val canvasWidthPx = with(density) { canvasWidthDp.toPx() }.coerceAtLeast(1f)
        val handleHitPx = with(density) { 24.dp.toPx() }
        val swipeThresholdPx = with(density) { 28.dp.toPx() }

        val maxScrollPx = (canvasWidthPx - viewportWidthPx).coerceAtLeast(0f)

        LaunchedEffect(scrollRestoreKey, restoredScrollPx, maxScrollPx, zoom, playheadMs, durationMs) {
            val key = scrollRestoreKey ?: return@LaunchedEffect
            val savedScroll = restoredScrollPx ?: return@LaunchedEffect
            if (appliedScrollRestoreKey == key) return@LaunchedEffect
            val clamped = savedScroll.coerceIn(0, maxScrollPx.roundToInt())
            scrollState.scrollTo(clamped)
            appliedScrollRestoreKey = key
        }

        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.value }
                .distinctUntilChanged()
                .debounce(120)
                .collect { onScrollChanged(it) }
        }

        LaunchedEffect(zoom, durationMs) {
            if (durationMs <= 0 || viewportWidthPx <= 0f) return@LaunchedEffect
            val clampedScrollPx = scrollState.value.toFloat().coerceIn(0f, maxScrollPx)
            val safePlayheadMs = playheadMs.coerceIn(0, durationMs)
            val playFrac = safePlayheadMs.toFloat() / durationMs.toFloat()
            val targetScrollPx = (playFrac * canvasWidthPx - viewportWidthPx / 2f).coerceIn(0f, maxScrollPx)
            val targetScrollInt = targetScrollPx.roundToInt()
            if (scrollState.value != targetScrollInt) {
                scrollState.scrollTo(targetScrollInt)
            } else {
                val clampedScrollInt = clampedScrollPx.roundToInt()
                if (scrollState.value != clampedScrollInt) {
                    scrollState.scrollTo(clampedScrollInt)
                }
            }
        }

        LaunchedEffect(playheadMs, durationMs, canvasWidthPx, viewportWidthPx, isDraggingHandle) {
            if (durationMs <= 0 || viewportWidthPx <= 0f) return@LaunchedEffect
            if (isDraggingHandle) return@LaunchedEffect
            val clampedScrollPx = scrollState.value.toFloat().coerceIn(0f, maxScrollPx)
            val safePlayheadMs = playheadMs.coerceIn(0, durationMs)
            val playFrac = safePlayheadMs.toFloat() / durationMs.toFloat()
            val playX = playFrac * canvasWidthPx
            val isVisible = playX in clampedScrollPx..(clampedScrollPx + viewportWidthPx)
            if (!isVisible) {
                val targetScrollPx = (playX - viewportWidthPx / 2f).coerceIn(0f, maxScrollPx)
                val targetScrollInt = targetScrollPx.roundToInt()
                if (scrollState.value != targetScrollInt) {
                    scrollState.scrollTo(targetScrollInt)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .width(canvasWidthDp)
                    .fillMaxHeight()
                    .pointerInput(
                        canvasWidthPx,
                        durationMs,
                        inMs,
                        outMs,
                        handleHitPx,
                        swipeThresholdPx,
                        zoom
                    ) {
                        if (durationMs <= 0) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var pointerId = down.id
                            val downAbsX = down.position.x.coerceIn(0f, canvasWidthPx)
                            val inAbsX = (inMs.toFloat() / durationMs.toFloat()) * canvasWidthPx
                            val outAbsX = (outMs.toFloat() / durationMs.toFloat()) * canvasWidthPx
                            fun absXToTimeMs(absX: Float): Int {
                                val fraction = if (canvasWidthPx <= 0f) 0f else (absX / canvasWidthPx).coerceIn(0f, 1f)
                                return (fraction * durationMs).roundToInt().coerceIn(0, durationMs)
                            }
                            val dragTarget = when {
                                abs(downAbsX - inAbsX) <= handleHitPx -> DragTarget.IN
                                abs(downAbsX - outAbsX) <= handleHitPx -> DragTarget.OUT
                                else -> DragTarget.NONE
                            }
                            if (dragTarget != DragTarget.NONE) {
                                isDraggingHandle = true
                            }

                            var lastPos = down.position
                            var upUptimeMs = down.uptimeMillis
                            var upAbsX = downAbsX
                            var maxDistanceFromDown = 0f
                            var deltaXFromDown = 0f
                            var deltaYFromDown = 0f
                            var handleMoved = false

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

                                val absX = currentPos.x.coerceIn(0f, canvasWidthPx)
                                val timeMs = absXToTimeMs(absX)

                                when (dragTarget) {
                                    DragTarget.IN -> {
                                        if (maxDistanceFromDown > viewConfiguration.touchSlop) {
                                            handleMoved = true
                                            onDragInMs(timeMs)
                                            change.consume()
                                        }
                                    }

                                    DragTarget.OUT -> {
                                        if (maxDistanceFromDown > viewConfiguration.touchSlop) {
                                            handleMoved = true
                                            onDragOutMs(timeMs)
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

                            if (dragTarget != DragTarget.NONE && handleMoved) {
                                isDraggingHandle = false
                                return@awaitEachGesture
                            }

                            val isTapLike = maxDistanceFromDown <= viewConfiguration.touchSlop
                            if (isTapLike) {
                                val doubleTapWindowMs = viewConfiguration.doubleTapTimeoutMillis
                                val isDoubleTap = lastTapUptimeMs > 0L &&
                                    (upUptimeMs - lastTapUptimeMs) <= doubleTapWindowMs &&
                                    !lastTapAbsX.isNaN() &&
                                    abs(upAbsX - lastTapAbsX) <= handleHitPx

                                val safeAbsX = upAbsX.coerceIn(0f, canvasWidthPx)
                                val tapTimeMs = absXToTimeMs(safeAbsX)
                                if (isDoubleTap) {
                                    val inDistance = abs(tapTimeMs - inMs)
                                    val outDistance = abs(tapTimeMs - outMs)
                                    if (inDistance <= outDistance) {
                                        onDragInMs(tapTimeMs)
                                    } else {
                                        onDragOutMs(tapTimeMs)
                                    }
                                    lastTapUptimeMs = 0L
                                    lastTapAbsX = Float.NaN
                                } else {
                                    val pressDurationMs = upUptimeMs - down.uptimeMillis
                                    if (pressDurationMs >= viewConfiguration.longPressTimeoutMillis) {
                                        onTogglePlayPause()
                                    } else {
                                        onTapTimeMs(tapTimeMs)
                                        lastTapUptimeMs = upUptimeMs
                                        lastTapAbsX = upAbsX
                                    }
                                }
                            } else if (abs(deltaXFromDown) > abs(deltaYFromDown) && abs(deltaXFromDown) >= swipeThresholdPx) {
                                if (deltaXFromDown < 0f) onSwipeLeftSetIn() else onSwipeRightSetOut()
                            }
                            isDraggingHandle = false
                        }
                    }
            ) {
                if (peaks.isEmpty()) return@Canvas

                val centerY = size.height / 2f
                val peakCount = peaks.size
                val dx = size.width / peakCount.toFloat()
                val strokeWidth = max(1f, dx * 0.7f)

                peaks.forEachIndexed { i, peak ->
                    val x = i * dx
                    val halfHeight = peak.coerceIn(0f, 1f) * (size.height * 0.48f)
                    drawLine(
                        color = Color(0xFF64D2FF),
                        start = androidx.compose.ui.geometry.Offset(x, centerY - halfHeight),
                        end = androidx.compose.ui.geometry.Offset(x, centerY + halfHeight),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                if (durationMs > 0) {
                    val inX = (inMs.toFloat() / durationMs.toFloat()) * size.width
                    val outX = (outMs.toFloat() / durationMs.toFloat()) * size.width
                    val safePlayhead = playheadMs.coerceIn(0, durationMs)
                    val playheadX = (safePlayhead.toFloat() / durationMs.toFloat()) * size.width
                    val leftX = min(inX, outX)
                    val rightX = max(inX, outX)

                    if (rightX > leftX) {
                        drawRect(
                            color = Color(0x224CD964),
                            topLeft = Offset(leftX, 0f),
                            size = Size(rightX - leftX, size.height)
                        )
                    }

                    drawLine(
                        color = Color(0xFF77FF77),
                        start = Offset(inX, 0f),
                        end = Offset(inX, size.height),
                        strokeWidth = 3.5f
                    )
                    drawLine(
                        color = Color(0xFFFF6B6B),
                        start = Offset(outX, 0f),
                        end = Offset(outX, size.height),
                        strokeWidth = 3.5f
                    )
                    drawLine(
                        color = Color(0xFFE3F2FD),
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, size.height),
                        strokeWidth = 3.5f
                    )
                    drawCircle(
                        color = Color(0xFF77FF77),
                        radius = 7f,
                        center = Offset(inX, 9f)
                    )
                    drawCircle(
                        color = Color(0xFF77FF77),
                        radius = 7f,
                        center = Offset(inX, size.height - 9f)
                    )
                    drawCircle(
                        color = Color(0xFFFF6B6B),
                        radius = 7f,
                        center = Offset(outX, 9f)
                    )
                    drawCircle(
                        color = Color(0xFFFF6B6B),
                        radius = 7f,
                        center = Offset(outX, size.height - 9f)
                    )
                }
            }
            Spacer(Modifier.width(2.dp))
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

private fun queryDisplayName(context: Context, uri: Uri): String {
    val fallback = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.ifBlank { "audio" }
        ?: "audio"

    return runCatching {
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    cursor.getString(idx)?.ifBlank { fallback } ?: fallback
                } else {
                    fallback
                }
            } else {
                fallback
            }
        } finally {
            cursor?.close()
        }
    }.getOrElse { fallback }
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
