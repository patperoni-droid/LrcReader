package com.patrick.lrcreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun PlaybackControl(
    positionMs: Int,
    durationMs: Int,
    onSeekLivePreview: (Int) -> Unit,
    onSeekCommit: (Int) -> Unit,
    highlightColor: Color,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    gainDb: Int,
    onGainDelta: (Int) -> Unit,
    compact: Boolean = false
) {
    TimeBar(
        positionMs = positionMs,
        durationMs = durationMs,
        onSeekLivePreview = onSeekLivePreview,
        onSeekCommit = onSeekCommit,
        highlightColor = highlightColor,
        compact = compact
    )

    PlayerControls(
        isPlaying = isPlaying,
        onPlayPause = onPlayPause,
        onPrev = onPrev,
        onNext = onNext,
        gainDb = gainDb,
        onGainDelta = onGainDelta,
        compact = compact
    )
}
