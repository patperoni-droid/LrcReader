package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.adaptive.rememberSmpAdaptiveTokens

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    compact: Boolean = false
) {
    val adaptiveTokens = rememberSmpAdaptiveTokens()
    val controlButtonSize = if (compact) 48.dp else adaptiveTokens.playerControlButtonSize
    val primaryButtonSize = if (compact) 60.dp else adaptiveTokens.playerPrimaryButtonSize
    val verticalPadding = if (compact) 4.dp else 14.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrev,
            modifier = Modifier.size(controlButtonSize)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.player_cd_prev),
                tint = Color.White,
                modifier = Modifier.size(controlButtonSize)
            )
        }

        IconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(primaryButtonSize)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.player_cd_play_pause),
                tint = Color.White,
                modifier = Modifier.size(primaryButtonSize)
            )
        }

        IconButton(
            onClick = onNext,
            modifier = Modifier.size(controlButtonSize)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.player_cd_next),
                tint = Color.White,
                modifier = Modifier.size(controlButtonSize)
            )
        }
    }
}
