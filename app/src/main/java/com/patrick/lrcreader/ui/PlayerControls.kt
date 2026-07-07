package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.adaptive.rememberSmpAdaptiveTokens

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    gainDb: Int = 4,
    compact: Boolean = false
) {
    val adaptiveTokens = rememberSmpAdaptiveTokens()
    val controlButtonSize = if (compact) 48.dp else adaptiveTokens.playerControlButtonSize
    val primaryButtonWidth = if (compact) 88.dp else 112.dp
    val primaryButtonHeight = if (compact) 48.dp else 58.dp
    val primaryIconSize = if (compact) 25.dp else 30.dp
    val gainButtonSize = if (compact) 34.dp else 40.dp
    val verticalPadding = if (compact) 0.dp else 4.dp
    val buttonShape = RoundedCornerShape(8.dp)
    val consoleGreen = Color(0xFF18B857)
    val gainButtonBackground = Color.White.copy(alpha = 0.10f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(primaryButtonWidth)
                .height(primaryButtonHeight)
                .background(consoleGreen, buttonShape)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.player_cd_play_pause),
                tint = Color.White,
                modifier = Modifier.size(primaryIconSize)
            )
        }

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

        Box(
            modifier = Modifier
                .size(gainButtonSize)
                .background(gainButtonBackground, buttonShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.playback_control_gain_decrease),
                color = Color.White,
                fontSize = if (compact) 18.sp else 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = stringResource(R.string.library_lufs_db_value, gainDb),
            color = Color.White,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Box(
            modifier = Modifier
                .size(gainButtonSize)
                .background(gainButtonBackground, buttonShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.playback_control_gain_increase),
                color = Color.White,
                fontSize = if (compact) 18.sp else 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
