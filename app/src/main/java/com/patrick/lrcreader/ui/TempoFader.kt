package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun TempoFaderRight(
    speed: Float,                 // 0.3f .. 3f
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val panelW = 64.dp
    val panelH = 360.dp

    val panelBg = Color(0x22000000)          // transparent
    val border = Color.White.copy(alpha = 0.25f)
    val track = Color.White.copy(alpha = 0.20f)
    val accent = Color(0xFFFFC107)           // ton jaune console

    // map speed (0.3..3) -> ui (0..1)
    val ui = ((speed - 0.3f) / (3f - 0.3f)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .size(panelW, panelH)
            .background(panelBg, RoundedCornerShape(22.dp))
            .border(1.dp, border, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Slider horizontal tourné => devient vertical
        Slider(
            value = ui,
            onValueChange = { u ->
                val newSpeed = 0.3f + (u.coerceIn(0f, 1f) * (3f - 0.3f))
                onSpeedChange(newSpeed)
            },
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 18.dp)
                .graphicsLayer(rotationZ = -90f), // 👈 vertical
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent.copy(alpha = 0.65f),
                inactiveTrackColor = track,
            )
        )

        // petite bulle % (optionnel)
        val pct = (ui * 100f).roundToInt()
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color(0xCCFFFFFF), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "$pct %",
                color = Color.Black,
                fontSize = 12.sp
            )
        }
    }
}