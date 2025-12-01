package com.patrick.lrcreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground

/**
 * Écran de mixage pour UN titre.
 *
 * - Niveau titre (dB)
 * - Tempo (0.8x à 1.2x)
 * - EQ 3 bandes VISUEL (Grave / Med / Aigu)
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
        mutableFloatStateOf(
            (currentTrackGainDb - minDb).toFloat() / (maxDb - minDb).toFloat()
        )
    }

    // --------- TEMPO ----------
    val minTempo = 0.8f
    val maxTempo = 1.2f

    var tempoSlider by remember(tempo) {
        mutableFloatStateOf(
            ((tempo - minTempo) / (maxTempo - minTempo))
                .coerceIn(0f, 1f)
        )
    }

    // --------- EQ VISUEL 3 BANDES (-12..+12 dB) ----------
    var lowGainDb by remember { mutableFloatStateOf(0f) }
    var midGainDb by remember { mutableFloatStateOf(0f) }
    var highGainDb by remember { mutableFloatStateOf(0f) }

    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
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
                text = String.format("%+d dB", currentTrackGainDb),
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

            // ─────────── EQ VISUEL 3 BANDES ───────────
            Text(
                text = "Égaliseur 3 bandes (visuel)",
                color = Color(0xFFB0BEC5),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),          // << hauteur maxi du bloc EQ
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                EqBandSlider(
                    label = "Grave",
                    color = Color(0xFF4CAF50),
                    valueDb = lowGainDb,
                    onValueDbChange = { lowGainDb = it }
                )
                EqBandSlider(
                    label = "Med",
                    color = Color(0xFFFFC107),
                    valueDb = midGainDb,
                    onValueDbChange = { midGainDb = it }
                )
                EqBandSlider(
                    label = "Aigu",
                    color = Color(0xFF42A5F5),
                    valueDb = highGainDb,
                    onValueDbChange = { highGainDb = it }
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "L’EQ est pour l’instant seulement visuel (pas encore branché audio).",
                color = Color(0xFF78909C),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Un fader vertical "gros" pour une bande d'EQ.
 * valueDb est entre -12 et +12.
 */
/**
 * Un fader vertical "gros" pour une bande d'EQ.
 * Gère son état en interne et renvoie la valeur au parent.
 */
@Composable
private fun EqBandSlider(
    label: String,
    color: Color,
    valueDb: Float,
    onValueDbChange: (Float) -> Unit
) {
    // On garde un état local qui bouge immédiatement à l'écran
    var localDb by remember { mutableFloatStateOf(valueDb.coerceIn(-12f, 12f)) }
    val sliderPos = (localDb + 12f) / 24f    // 0..1

    Column(
        modifier = Modifier.width(70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Valeur en dB
        Text(
            text = String.format("%+d dB", localDb.toInt()),
            color = Color.White,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(6.dp))

        // Corps du fader
        Canvas(
            modifier = Modifier
                .width(26.dp)          // plus large
                .height(200.dp)        // bien haut
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        // dragAmount > 0 = vers le bas → on baisse le gain
                        val deltaDb = -dragAmount / 8f   // règle la "vitesse" du fader
                        val newDb = (localDb + deltaDb).coerceIn(-12f, 12f)
                        localDb = newDb          // 👉 bouge visuellement tout de suite
                        onValueDbChange(newDb)   // 👉 remonte la valeur au parent
                    }
                }
        ) {
            val trackWidth = size.width
            val trackHeight = size.height

            // Piste de fond
            drawRoundRect(
                color = Color(0xFF303030),
                topLeft = Offset(
                    x = (size.width - trackWidth) / 2f,
                    y = 0f
                ),
                size = Size(trackWidth, trackHeight),
                cornerRadius = CornerRadius(trackWidth / 2f)
            )

            // Partie active colorée
            val activeHeight = trackHeight * sliderPos
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    x = (size.width - trackWidth) / 2f,
                    y = trackHeight - activeHeight
                ),
                size = Size(trackWidth, activeHeight),
                cornerRadius = CornerRadius(trackWidth / 2f)
            )

            // Le "bouton" du fader
            val knobY = trackHeight * (1f - sliderPos)
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(
                    x = (size.width - trackWidth * 1.6f) / 2f,
                    y = knobY - 6.dp.toPx()
                ),
                size = Size(trackWidth * 1.6f, 12.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Color(0xFFCFD8DC),
            fontSize = 14.sp
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