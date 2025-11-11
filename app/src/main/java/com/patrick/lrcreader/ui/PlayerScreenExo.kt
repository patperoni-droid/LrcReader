package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun PlayerScreenExo(
    modifier: Modifier = Modifier,
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    onIsPlayingChange: (Boolean) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("LECTEUR TEST EXOPLAYER", color = Color.White)
        Spacer(Modifier.height(20.dp))
        IconButton(onClick = {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
                onIsPlayingChange(false)
            } else {
                exoPlayer.play()
                onIsPlayingChange(true)
            }
        }) {
            Icon(
                imageVector = if (exoPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(72.dp)
            )
        }
    }
}