package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
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

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    gainDb: Int = 4,
    onGainDelta: (Int) -> Unit = {},
    compact: Boolean = false
) {
    val controlButtonSize = if (compact) 48.dp else 48.dp
    val controlIconSize = if (compact) 34.dp else 36.dp
    val primaryButtonWidth = if (compact) 144.dp else 126.dp
    val primaryButtonHeight = if (compact) 50.dp else 50.dp
    val primaryIconSize = if (compact) 25.dp else 27.dp
    val gainButtonSize = if (compact) 36.dp else 36.dp
    val verticalPadding = 0.dp
    val itemSpacing = if (compact) 14.dp else 8.dp
    val buttonShape = RoundedCornerShape(7.dp)
    val consoleGreen = Color(0xFF18B857)
    val consoleRed = Color(0xFFD93636)
    val primaryButtonColor = if (isPlaying) consoleRed else consoleGreen
    val pauseButtonColor = if (isPlaying) consoleRed else Color.White.copy(alpha = 0.08f)
    val pauseIconColor = if (isPlaying) Color.White else Color.White.copy(alpha = 0.42f)
    val gainButtonBackground = Color.White.copy(alpha = 0.10f)
    val controlBorder = Color.White.copy(alpha = 0.22f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(primaryButtonWidth)
                .height(primaryButtonHeight)
                .background(primaryButtonColor, buttonShape)
                .border(1.dp, controlBorder, buttonShape)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.player_cd_play_pause),
                tint = Color.White,
                modifier = Modifier.size(primaryIconSize)
            )
        }

        if (compact) {
            Box(
                modifier = Modifier
                    .size(controlButtonSize)
                    .background(pauseButtonColor, buttonShape)
                    .border(1.dp, controlBorder, buttonShape)
                    .clickable(enabled = isPlaying) { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = stringResource(R.string.player_cd_pause),
                    tint = pauseIconColor,
                    modifier = Modifier.size(controlIconSize)
                )
            }
        }

        IconButton(
            onClick = onPrev,
            modifier = Modifier.size(controlButtonSize)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.player_cd_prev),
                tint = Color.White,
                modifier = Modifier.size(controlIconSize)
            )
        }

        Box(
            modifier = Modifier
                .size(gainButtonSize)
                .background(gainButtonBackground, buttonShape)
                .border(1.dp, controlBorder.copy(alpha = 0.45f), buttonShape)
                .clickable { onGainDelta(-1) },
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
                .background(gainButtonBackground, buttonShape)
                .border(1.dp, controlBorder.copy(alpha = 0.45f), buttonShape)
                .clickable { onGainDelta(1) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.playback_control_gain_increase),
                color = Color.White,
                fontSize = if (compact) 18.sp else 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (compact) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
