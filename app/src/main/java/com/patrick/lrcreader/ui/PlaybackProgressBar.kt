package com.patrick.lrcreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PlaybackProgressBarDefaults {
    val Height = 56.dp
    val ClassicHeight = 28.dp
    val StructureSegmentMinWidth = 48.dp
}

private val LinearPlaybackStructureModel = PlaybackStructureModel(
    segments = listOf(
        PlaybackStructureSegment(
            key = "linear-timeline",
            label = "",
            fraction = 1f,
            color = Color(0xFF37474F)
        )
    )
)

data class PlaybackStructureModel(
    val segments: List<PlaybackStructureSegment>
)

data class PlaybackStructureSegment(
    val key: String,
    val label: String,
    val fraction: Float,
    val color: Color
) {
    init {
        require(fraction.isFinite() && fraction > 0f)
    }
}

sealed interface PlaybackProgressMode {
    data object Classic : PlaybackProgressMode

    data object Linear : PlaybackProgressMode

    data class Structure(
        val model: PlaybackStructureModel,
        val armedSegmentKey: String? = null,
        val loopedSegmentKey: String? = null
    ) : PlaybackProgressMode
}

@Immutable
data class PlaybackProgressState(
    val durationMs: Int,
    val progressFraction: Float,
    val isEnabled: Boolean,
    val positionText: String,
    val durationText: String,
    val highlightColor: Color,
    val compact: Boolean
)

@Composable
fun PlaybackProgressBar(
    mode: PlaybackProgressMode,
    positionMs: Int,
    durationMs: Int,
    onSeekLivePreview: (Int) -> Unit,
    onSeekCommit: (Int) -> Unit,
    highlightColor: Color,
    onStructureSegmentSelected: (String) -> Unit = {},
    onStructureSegmentLongPressed: (String) -> Unit = {},
    compact: Boolean = false
) {
    var previewPositionMs by remember { mutableIntStateOf(positionMs) }
    var isDragging by remember { mutableStateOf(false) }
    val displayPositionMs = if (isDragging) previewPositionMs else positionMs
    val safeDurationMs = durationMs.coerceAtLeast(0)
    val progressFraction = when {
        safeDurationMs <= 0 -> 0f
        else -> displayPositionMs.toFloat() / safeDurationMs.toFloat()
    }
    val state = PlaybackProgressState(
        durationMs = safeDurationMs,
        progressFraction = progressFraction,
        isEnabled = safeDurationMs > 0,
        positionText = remember(displayPositionMs) { formatMsLocal(displayPositionMs) },
        durationText = remember(safeDurationMs) { formatMsLocal(safeDurationMs) },
        highlightColor = highlightColor,
        compact = compact
    )
    val onProgressChange: (Float) -> Unit = { fraction ->
        val preview = (fraction * state.durationMs).toInt()
        previewPositionMs = preview
        isDragging = true
        onSeekLivePreview(preview)
    }
    val onProgressChangeFinished: () -> Unit = {
        isDragging = false
        onSeekCommit(previewPositionMs)
    }

    when (mode) {
        PlaybackProgressMode.Classic -> {
            ClassicTimeBarRenderer(
                state = state,
                onProgressChange = onProgressChange,
                onProgressChangeFinished = onProgressChangeFinished
            )
        }

        PlaybackProgressMode.Linear -> {
            TimeBarRenderer(
                state = state,
                onProgressChange = onProgressChange,
                onProgressChangeFinished = onProgressChangeFinished
            )
        }

        is PlaybackProgressMode.Structure -> {
            StructureRenderer(
                state = state,
                model = mode.model,
                armedSegmentKey = mode.armedSegmentKey,
                loopedSegmentKey = mode.loopedSegmentKey,
                onSegmentSelected = onStructureSegmentSelected,
                onSegmentLongPressed = onStructureSegmentLongPressed
            )
        }
    }
}

@Composable
private fun ClassicTimeBarRenderer(
    state: PlaybackProgressState,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit
) {
    val trackColor = state.highlightColor.copy(alpha = 0.25f)

    PlaybackProgressFrame(
        state = state,
        trackHeight = PlaybackProgressBarDefaults.ClassicHeight
    ) {
        Slider(
            value = state.progressFraction,
            onValueChange = onProgressChange,
            onValueChangeFinished = onProgressChangeFinished,
            enabled = state.isEnabled,
            modifier = Modifier
                .weight(1f)
                .height(PlaybackProgressBarDefaults.ClassicHeight),
            colors = SliderDefaults.colors(
                thumbColor = state.highlightColor,
                activeTrackColor = trackColor,
                inactiveTrackColor = trackColor.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun TimeBarRenderer(
    state: PlaybackProgressState,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit
) {
    PlaybackProgressFrame(
        state = state
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            PlaybackStructureTrack(
                state = state,
                model = LinearPlaybackStructureModel,
                armedSegmentKey = null,
                loopedSegmentKey = null,
                onSegmentSelected = null,
                onSegmentLongPressed = null,
                modifier = Modifier.fillMaxSize()
            )
            Slider(
                value = state.progressFraction,
                onValueChange = onProgressChange,
                onValueChangeFinished = onProgressChangeFinished,
                enabled = state.isEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f)
            )
        }
    }
}

@Composable
private fun StructureRenderer(
    state: PlaybackProgressState,
    model: PlaybackStructureModel,
    armedSegmentKey: String?,
    loopedSegmentKey: String?,
    onSegmentSelected: (String) -> Unit,
    onSegmentLongPressed: (String) -> Unit
) {
    PlaybackProgressFrame(
        state = state
    ) {
        PlaybackStructureTrack(
            state = state,
            model = model,
            armedSegmentKey = armedSegmentKey,
            loopedSegmentKey = loopedSegmentKey,
            onSegmentSelected = onSegmentSelected,
            onSegmentLongPressed = onSegmentLongPressed,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PlaybackStructureTrack(
    state: PlaybackProgressState,
    model: PlaybackStructureModel,
    armedSegmentKey: String?,
    loopedSegmentKey: String?,
    onSegmentSelected: ((String) -> Unit)?,
    onSegmentLongPressed: ((String) -> Unit)?,
    modifier: Modifier
) {
    val progressFraction = state.progressFraction.coerceIn(0f, 1f)
    val activeSegmentIndex = findActivePlaybackStructureSegmentIndex(
        model = model,
        progressFraction = progressFraction
    )
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ArrangementTrackBackgroundColor)
    ) {
        val segmentWidthsDp = remember(model, maxWidth) {
            playbackStructureSegmentWidthsDp(
                model = model,
                viewportWidthDp = maxWidth.value,
                minimumSegmentWidthDp =
                    PlaybackProgressBarDefaults.StructureSegmentMinWidth.value
            )
        }
        val playheadOffsetDp = playbackStructurePlayheadOffsetDp(
            model = model,
            segmentWidthsDp = segmentWidthsDp,
            progressFraction = progressFraction
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
        ) {
            model.segments.forEachIndexed { index, segment ->
                androidx.compose.runtime.key(segment.key) {
                    val isQueued = segment.key == armedSegmentKey
                    val isLooped = segment.key == loopedSegmentKey
                    val containerColor = arrangementTrackOccurrenceContainerColor(
                        color = segment.color,
                        isMuted = false,
                        isActive = index == activeSegmentIndex,
                        isQueued = isQueued
                    )
                    BoxWithConstraints(
                        modifier = Modifier
                            .width(segmentWidthsDp.getOrElse(index) { 0f }.dp)
                            .fillMaxHeight()
                            .then(
                                if (onSegmentSelected != null) {
                                    Modifier.combinedClickable(
                                        onClick = {
                                            onSegmentSelected(segment.key)
                                        },
                                        onLongClick = onSegmentLongPressed?.let { onLongPress ->
                                            { onLongPress(segment.key) }
                                        }
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .background(
                                if (isLooped) {
                                    lerp(containerColor, Color.White, 0.27f)
                                } else {
                                    containerColor
                                }
                            )
                            .then(
                                if (isLooped) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = lerp(segment.color, Color.White, 0.42f)
                                    )
                                } else if (isQueued) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = Color(0xFFFFD54F)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .drawWithContent {
                                drawContent()
                                if (index < model.segments.lastIndex) {
                                    val separatorWidth = 1.dp.toPx()
                                    drawLine(
                                        color = Color.LightGray.copy(alpha = 0.55f),
                                        start = Offset(size.width - separatorWidth / 2f, 0f),
                                        end = Offset(
                                            size.width - separatorWidth / 2f,
                                            size.height
                                        ),
                                        strokeWidth = separatorWidth
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (maxWidth >= 28.dp) {
                            Text(
                                text = segment.label,
                                color = Color.LightGray,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 3.dp)
                            )
                        }
                    }
                }
            }
        }
        Canvas(modifier = Modifier.matchParentSize()) {
            val viewportPlayheadX = playheadOffsetDp.dp.toPx() - scrollState.value
            if (model.segments.isNotEmpty() && viewportPlayheadX in 0f..size.width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.90f),
                    start = Offset(viewportPlayheadX, 0f),
                    end = Offset(viewportPlayheadX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

internal fun findActivePlaybackStructureSegmentIndex(
    model: PlaybackStructureModel,
    progressFraction: Float
): Int {
    if (model.segments.isEmpty()) return -1

    val totalFraction = model.segments.fold(0.0) { total, segment ->
        total + segment.fraction.toDouble()
    }
    if (totalFraction <= 0.0) return -1

    val position = progressFraction.coerceIn(0f, 1f).toDouble() * totalFraction
    var boundary = 0.0
    model.segments.forEachIndexed { index, segment ->
        boundary += segment.fraction.toDouble()
        if (position < boundary || index == model.segments.lastIndex) {
            return index
        }
    }

    return model.segments.lastIndex
}

internal fun playbackStructureSegmentWidthsDp(
    model: PlaybackStructureModel,
    viewportWidthDp: Float,
    minimumSegmentWidthDp: Float
): List<Float> {
    if (model.segments.isEmpty()) return emptyList()
    val safeViewportWidthDp = viewportWidthDp.coerceAtLeast(0f)
    val safeMinimumWidthDp = minimumSegmentWidthDp.coerceAtLeast(0f)
    val totalFraction = model.segments.sumOf { segment -> segment.fraction.toDouble() }
    if (totalFraction <= 0.0) return List(model.segments.size) { safeMinimumWidthDp }
    return model.segments.map { segment ->
        (safeViewportWidthDp * (segment.fraction / totalFraction).toFloat())
            .coerceAtLeast(safeMinimumWidthDp)
    }
}

internal fun playbackStructurePlayheadOffsetDp(
    model: PlaybackStructureModel,
    segmentWidthsDp: List<Float>,
    progressFraction: Float
): Float {
    if (model.segments.isEmpty() || segmentWidthsDp.size != model.segments.size) return 0f
    val totalFraction = model.segments.sumOf { segment -> segment.fraction.toDouble() }
    if (totalFraction <= 0.0) return 0f
    val position = progressFraction.coerceIn(0f, 1f).toDouble() * totalFraction
    var fractionBeforeSegment = 0.0
    var widthBeforeSegmentDp = 0f
    model.segments.forEachIndexed { index, segment ->
        val segmentEnd = fractionBeforeSegment + segment.fraction
        if (position < segmentEnd || index == model.segments.lastIndex) {
            val localProgress = (
                (position - fractionBeforeSegment) / segment.fraction
                ).toFloat().coerceIn(0f, 1f)
            return widthBeforeSegmentDp + segmentWidthsDp[index] * localProgress
        }
        fractionBeforeSegment = segmentEnd
        widthBeforeSegmentDp += segmentWidthsDp[index]
    }
    return segmentWidthsDp.sum()
}

@Composable
private fun PlaybackProgressFrame(
    state: PlaybackProgressState,
    trackHeight: androidx.compose.ui.unit.Dp = PlaybackProgressBarDefaults.Height,
    content: @Composable RowScope.() -> Unit
) {
    val textSize = if (state.compact) 12.sp else 12.sp
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.positionText,
                color = Color.LightGray,
                fontSize = textSize
            )

            Text(
                text = state.durationText,
                color = Color.LightGray,
                fontSize = textSize
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

private fun formatMsLocal(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
