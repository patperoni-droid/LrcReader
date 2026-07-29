package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PlaybackProgressBarDefaults {
    val TimeRowHeight = 20.dp
    val Height = 56.dp
    val TotalHeight = TimeRowHeight + Height
    val LinearTrackHeight = 28.dp
}

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
    data object Linear : PlaybackProgressMode

    data class Structure(
        val model: PlaybackStructureModel,
        val armedSegmentKey: String? = null
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

    when (mode) {
        PlaybackProgressMode.Linear -> {
            TimeBarRenderer(
                state = state,
                onProgressChange = { fraction ->
                    val preview = (fraction * state.durationMs).toInt()
                    previewPositionMs = preview
                    isDragging = true
                    onSeekLivePreview(preview)
                },
                onProgressChangeFinished = {
                    isDragging = false
                    onSeekCommit(previewPositionMs)
                }
            )
        }

        is PlaybackProgressMode.Structure -> {
            StructureRenderer(
                state = state,
                model = mode.model,
                armedSegmentKey = mode.armedSegmentKey,
                onSegmentSelected = onStructureSegmentSelected
            )
        }
    }
}

@Composable
private fun TimeBarRenderer(
    state: PlaybackProgressState,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit
) {
    val trackColor = state.highlightColor.copy(alpha = 0.25f)

    PlaybackProgressFrame(
        state = state
    ) {
        Slider(
            value = state.progressFraction,
            onValueChange = onProgressChange,
            onValueChangeFinished = onProgressChangeFinished,
            enabled = state.isEnabled,
            modifier = Modifier
                .weight(1f)
                .height(PlaybackProgressBarDefaults.LinearTrackHeight),
            colors = SliderDefaults.colors(
                thumbColor = state.highlightColor,
                activeTrackColor = trackColor,
                inactiveTrackColor = trackColor.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun StructureRenderer(
    state: PlaybackProgressState,
    model: PlaybackStructureModel,
    armedSegmentKey: String?,
    onSegmentSelected: (String) -> Unit
) {
    val progressFraction = state.progressFraction.coerceIn(0f, 1f)
    val activeSegmentIndex = findActivePlaybackStructureSegmentIndex(
        model = model,
        progressFraction = progressFraction
    )

    PlaybackProgressFrame(
        state = state
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(PlaybackProgressBarDefaults.Height)
                .clip(RoundedCornerShape(4.dp))
                .background(ArrangementTrackBackgroundColor)
                .drawWithContent {
                    drawContent()
                    if (model.segments.isNotEmpty()) {
                        val playheadWidth = 2.dp.toPx()
                        val playheadX = size.width * progressFraction
                        drawLine(
                            color = Color.White.copy(alpha = 0.90f),
                            start = Offset(playheadX, 0f),
                            end = Offset(playheadX, size.height),
                            strokeWidth = playheadWidth
                        )
                    }
                }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                model.segments.forEachIndexed { index, segment ->
                    androidx.compose.runtime.key(segment.key) {
                        val isQueued = segment.key == armedSegmentKey
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(segment.fraction)
                                .fillMaxHeight()
                                .clickable { onSegmentSelected(segment.key) }
                                .background(
                                    arrangementTrackOccurrenceContainerColor(
                                        color = segment.color,
                                        isMuted = false,
                                        isActive = index == activeSegmentIndex,
                                        isQueued = isQueued
                                    )
                                )
                                .then(
                                    if (isQueued) {
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
                                            end = Offset(size.width - separatorWidth / 2f, size.height),
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

@Composable
private fun PlaybackProgressFrame(
    state: PlaybackProgressState,
    content: @Composable RowScope.() -> Unit
) {
    val textSize = if (state.compact) 12.sp else 12.sp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(PlaybackProgressBarDefaults.TotalHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlaybackProgressBarDefaults.TimeRowHeight),
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
                .height(PlaybackProgressBarDefaults.Height),
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
