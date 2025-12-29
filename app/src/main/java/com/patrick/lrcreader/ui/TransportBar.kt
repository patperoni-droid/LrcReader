package com.patrick.lrcreader.ui

import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp

@Composable
fun PrompterTransportBar(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    onToggleRun: () -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    accent: Color = Color(0xFFFFC107),
    onPrev: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null
) {
    // même “gueule” que l’audio : une rangée de boutons + une ligne de slider
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // ⏮ optionnel
            IconButton(
                onClick = { onPrev?.invoke() },
                enabled = onPrev != null
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Précédent",
                    tint = if (onPrev != null) accent else Color(0xFF666666)
                )
            }

            // ▶️/⏸ (défilement)
            IconButton(onClick = onToggleRun) {
                Icon(
                    imageVector = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = accent,
                    modifier = Modifier.size(34.dp)
                )
            }

            // ⏭ optionnel
            IconButton(
                onClick = { onNext?.invoke() },
                enabled = onNext != null
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Suivant",
                    tint = if (onNext != null) accent else Color(0xFF666666)
                )
            }

            Spacer(Modifier.width(10.dp))

            Text(
                text = "Vitesse ${String.format("%.1fx", speed)}",
                color = Color.White,
                fontSize = 12.sp
            )
        }

        Slider(
            value = speed,
            onValueChange = onSpeedChange,
            valueRange = 0.3f..3f,
            colors = SliderDefaults.colors(
                activeTrackColor = accent,
                thumbColor = accent,
                inactiveTrackColor = Color(0xFF424242)
            )
        )
    }
}