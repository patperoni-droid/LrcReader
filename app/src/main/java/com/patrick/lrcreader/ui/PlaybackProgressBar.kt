package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PlaybackProgressBarDefaults {
    val Height = 20.dp
}

sealed interface PlaybackProgressMode {
    data object Linear : PlaybackProgressMode
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
    }
}

@Composable
private fun TimeBarRenderer(
    state: PlaybackProgressState,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit
) {
    val trackColor = state.highlightColor.copy(alpha = 0.25f)
    val textSize = if (state.compact) 12.sp else 12.sp
    val sidePadding = if (state.compact) 10.dp else 6.dp
    val sliderHeight = PlaybackProgressBarDefaults.Height
    val bottomPadding = 0.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = state.positionText,
            color = Color.LightGray,
            fontSize = textSize,
            modifier = Modifier.padding(end = sidePadding)
        )

        Slider(
            value = state.progressFraction,
            onValueChange = onProgressChange,
            onValueChangeFinished = onProgressChangeFinished,
            enabled = state.isEnabled,
            modifier = Modifier
                .weight(1f)
                .height(sliderHeight),
            colors = SliderDefaults.colors(
                thumbColor = state.highlightColor,
                activeTrackColor = trackColor,
                inactiveTrackColor = trackColor.copy(alpha = 0.4f)
            )
        )

        Text(
            text = state.durationText,
            color = Color.LightGray,
            fontSize = textSize,
            modifier = Modifier.padding(start = sidePadding)
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
