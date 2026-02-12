package com.patrick.lrcreader.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.patrick.lrcreader.core.audio.AudioEngine

private const val TAG = "AUDIO_TS_UI"

@UnstableApi
@Composable
fun TimeStretchDebugToggle(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(6.dp)
) {
    var mode by remember { mutableStateOf(AudioEngine.getTimeStretchMode()) }

    val label = when (mode) {
        AudioEngine.TimeStretchMode.EXO -> "TS: EXO"
        AudioEngine.TimeStretchMode.HQ  -> "TS: HQ"
    }

    Text(
        text = label,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x66000000))
            .clickable {
                val next = if (mode == AudioEngine.TimeStretchMode.EXO)
                    AudioEngine.TimeStretchMode.HQ
                else
                    AudioEngine.TimeStretchMode.EXO

                Log.d(TAG, "TOGGLE_CLICKED current=$mode next=$next")
                AudioEngine.setTimeStretchMode(next, reason = "uiToggle")
                mode = AudioEngine.getTimeStretchMode()
                Log.d(TAG, "TOGGLE_APPLIED effective=$mode")
            }
            .padding(padding)
    )
}