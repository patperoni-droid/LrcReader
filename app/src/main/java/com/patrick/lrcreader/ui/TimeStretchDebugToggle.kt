package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.core.audio.AudioEngine

/**
 * ✅ Petit toggle DEBUG pour basculer EXO/HQ.
 * HQ n'est pas encore implémenté => AudioEngine fera fallback EXO, mais le bouton est prêt.
 */
@androidx.media3.common.util.UnstableApi
@Composable
fun TimeStretchDebugToggle(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(6.dp)
) {
    var mode by remember { mutableStateOf(AudioEngine.getTimeStretchMode()) }

    val label = when (mode) {
        AudioEngine.TimeStretchMode.EXO -> "TS: EXO"
        AudioEngine.TimeStretchMode.HQ -> "TS: HQ"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(10.dp))
            .background(Color(0x33000000))
            .clickable {
                val next = if (mode == AudioEngine.TimeStretchMode.EXO) {
                    AudioEngine.TimeStretchMode.HQ
                } else {
                    AudioEngine.TimeStretchMode.EXO
                }
                AudioEngine.setTimeStretchMode(next, reason = "debugToggleUI")
                mode = AudioEngine.getTimeStretchMode() // reflète le mode effectif (fallback possible)
            }
            .padding(padding)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}