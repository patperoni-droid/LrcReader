package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview

/**
 * Écran de mixage pour UN titre.
 *
 * Pour l'instant :
 * - Niveau titre (dB) -> utilise currentTrackGainDb / onTrackGainChange
 * - Tempo (0.8x à 1.2x) -> tempo / onTempoChange
 * - EQ 3 bandes purement visuel (placeholder pour plus tard)
 *
 * Tu peux l'ouvrir en plein écran via un Scaffold ou un simple if(...)
 */
@Composable
fun TrackMixScreen(
    modifier: Modifier = Modifier,
    highlightColor: Color = Color(0xFFE040FB),

    // Niveau par morceau
    currentTrackGainDb: Int,
    onTrackGainChange: (Int) -> Unit,

    // Tempo par morceau
    tempo: Float,
    onTempoChange: (Float) -> Unit,

    // Quand on ferme la page mixage
    onClose: () -> Unit
) {
    // --------- GAIN (dB) ----------
    val minDb = -12
    val maxDb = 12

    var gainSlider by remember(currentTrackGainDb) {
        mutableStateOf(
            (currentTrackGainDb - minDb).toFloat() / (maxDb - minDb).toFloat()
        )
    }

    // --------- TEMPO ----------
    val minTempo = 0.8f
    val maxTempo = 1.2f

    var tempoSlider by remember(tempo) {
        mutableStateOf(
            ((tempo - minTempo) / (maxTempo - minTempo))
                .coerceIn(0f, 1f)
        )
    }

    // --------- EQ VISUEL (placeholders) ----------
    var lowGain by remember { mutableStateOf(0f) }   // -12..+12
    var midGain by remember { mutableStateOf(0f) }
    var highGain by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {

        // HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mixage du titre",
                color = Color.White,
                fontSize = 18.sp
            )
            TextButton(onClick = onClose) {
                Text(
                    text = "Fermer",
                    color = Color(0xFFFF8A80),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ─────────── NIVEAU TITRE ───────────
        Text(
            text = "Niveau du titre",
            color = Color(0xFFB0BEC5),
            fontSize = 13.sp
        )
        Text(
            text = "${if (currentTrackGainDb >= 0) "+${currentTrackGainDb}" else currentTrackGainDb} dB",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Slider(
            value = gainSlider,
            onValueChange = { v ->
                gainSlider = v
                val newDb = (minDb + v * (maxDb - minDb)).toInt()
                onTrackGainChange(newDb)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = highlightColor,
                activeTrackColor = highlightColor.copy(alpha = 0.4f),
                inactiveTrackColor = Color.DarkGray
            )
        )

        Spacer(Modifier.height(18.dp))

        // ─────────── TEMPO ───────────
        Text(
            text = "Tempo",
            color = Color(0xFFB0BEC5),
            fontSize = 13.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format("x%.2f", tempo),
                color = Color.White,
                fontSize = 12.sp
            )
            TextButton(onClick = {
                val resetTempo = 1.0f
                onTempoChange(resetTempo)
                tempoSlider = ((resetTempo - minTempo) / (maxTempo - minTempo))
                    .coerceIn(0f, 1f)
            }) {
                Text(
                    text = "Reset 1.00x",
                    color = Color(0xFF80CBC4),
                    fontSize = 11.sp
                )
            }
        }

        Slider(
            value = tempoSlider,
            onValueChange = { v ->
                tempoSlider = v
                val newTempo = (minTempo + v * (maxTempo - minTempo))
                    .coerceIn(minTempo, maxTempo)
                onTempoChange(newTempo)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF80CBC4),
                activeTrackColor = Color(0xFF80CBC4).copy(alpha = 0.4f),
                inactiveTrackColor = Color.DarkGray
            )
        )

        Spacer(Modifier.height(22.dp))

        // ─────────── EQ VISUEL (PLACEHOLDER) ───────────
        Text(
            text = "Égaliseur (visuel, pour version avancée)",
            color = Color(0xFFB0BEC5),
            fontSize = 13.sp
        )

        Spacer(Modifier.height(8.dp))

        // Graves
        Text(
            text = "Graves",
            color = Color.White,
            fontSize = 12.sp
        )
        Slider(
            value = (lowGain + 12f) / 24f,
            onValueChange = { v ->
                lowGain = v * 24f - 12f
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4CAF50),
                activeTrackColor = Color(0xFF4CAF50).copy(alpha = 0.4f),
                inactiveTrackColor = Color.DarkGray
            )
        )
        Text(
            text = "${if (lowGain >= 0) "+${lowGain.toInt()}" else lowGain.toInt()} dB",
            color = Color(0xFFCFD8DC),
            fontSize = 11.sp
        )

        Spacer(Modifier.height(10.dp))

        // Médiums
        Text(
            text = "Médiums",
            color = Color.White,
            fontSize = 12.sp
        )
        Slider(
            value = (midGain + 12f) / 24f,
            onValueChange = { v ->
                midGain = v * 24f - 12f
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFC107),
                activeTrackColor = Color(0xFFFFC107).copy(alpha = 0.4f),
                inactiveTrackColor = Color.DarkGray
            )
        )
        Text(
            text = "${if (midGain >= 0) "+${midGain.toInt()}" else midGain.toInt()} dB",
            color = Color(0xFFCFD8DC),
            fontSize = 11.sp
        )

        Spacer(Modifier.height(10.dp))

        // Aigus
        Text(
            text = "Aigus",
            color = Color.White,
            fontSize = 12.sp
        )
        Slider(
            value = (highGain + 12f) / 24f,
            onValueChange = { v ->
                highGain = v * 24f - 12f
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF42A5F5),
                activeTrackColor = Color(0xFF42A5F5).copy(alpha = 0.4f),
                inactiveTrackColor = Color.DarkGray
            )
        )
        Text(
            text = "${if (highGain >= 0) "+${highGain.toInt()}" else highGain.toInt()} dB",
            color = Color(0xFFCFD8DC),
            fontSize = 11.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "L’EQ sera câblé plus tard (version avancée).",
            color = Color(0xFF78909C),
            fontSize = 11.sp
        )
    }

}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
fun TrackMixScreenPreview() {
    TrackMixScreen(
        highlightColor = Color(0xFFE040FB),
        currentTrackGainDb = 0,
        onTrackGainChange = { },
        tempo = 1.0f,
        onTempoChange = { },
        onClose = {}
    )
}