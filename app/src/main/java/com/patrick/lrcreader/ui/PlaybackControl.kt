package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    compact: Boolean = false,
    liveConsoleMode: Boolean = false,
    liveSelectionInSync: Boolean = true,
    onLivePlay: (() -> Unit)? = null,
    progressMode: PlaybackProgressMode = PlaybackProgressMode.Linear,
    onStructureSegmentSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PlaybackProgressBar(
            mode = progressMode,
            positionMs = positionMs,
            durationMs = durationMs,
            onSeekLivePreview = onSeekLivePreview,
            onSeekCommit = onSeekCommit,
            highlightColor = highlightColor,
            onStructureSegmentSelected = onStructureSegmentSelected,
            compact = compact
        )

        PlayerControls(
            isPlaying = isPlaying,
            onPlayPause = onPlayPause,
            onPrev = onPrev,
            onNext = onNext,
            gainDb = gainDb,
            onGainDelta = onGainDelta,
            compact = compact,
            liveConsoleMode = liveConsoleMode,
            liveSelectionInSync = liveSelectionInSync,
            onLivePlay = onLivePlay
        )
    }
}
