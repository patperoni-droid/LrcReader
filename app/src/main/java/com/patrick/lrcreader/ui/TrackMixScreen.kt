package com.patrick.lrcreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.TrackEqEngine
import com.patrick.lrcreader.core.TrackEqPrefs
import com.patrick.lrcreader.core.TrackEqSettings
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlin.math.roundToInt

/**
 * Écran de mixage pour UN titre.
 *
 * - Niveau titre (dB) -> currentTrackGainDb / onTrackGainChange
 * - Tempo (0.8x à 1.2x) -> tempo / onTempoChange
 * - EQ 3 bandes vertical, câblé audio + mémorisé par titre
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

    // Pour mémoriser l’EQ par titre
    currentTrackUri: String?,

    // Quand on ferme la page mixage
    onClose: () -> Unit
) {
    val context = LocalContext.current

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

    // --------- EQ (3 bandes) + MÉMO PAR TITRE ----------

    // Valeurs initiales depuis les préférences du titre
    val initialEq = remember(currentTrackUri) {
        if (currentTrackUri != null) {
            TrackEqPrefs.load(context, currentTrackUri)
                ?: TrackEqSettings(0f, 0f, 0f)
        } else {
            TrackEqSettings(0f, 0f, 0f)
        }
    }

    var lowGain by remember(currentTrackUri) { mutableStateOf(initialEq.low) }   // -12..+12
    var midGain by remember(currentTrackUri) { mutableStateOf(initialEq.mid) }
    var highGain by remember(currentTrackUri) { mutableStateOf(initialEq.high) }

    fun applyAndSaveEq() {
        // 🔊 applique au moteur d’EQ
        TrackEqEngine.setBands(
            lowDb = lowGain,
            midDb = midGain,
            highDb = highGain
        )
        // 💾 mémorise pour ce titre
        currentTrackUri?.let { uri ->
            TrackEqPrefs.save(
                context,
                uri,
                TrackEqSettings(lowGain, midGain, highGain)
            )
        }
    }

    // On applique une première fois les valeurs au moment où l’écran s’ouvre
    // (pour être sûr que le son colle aux faders)
    remember(currentTrackUri) {
        applyAndSaveEq()
        0 // valeur jetable pour satisfy remember
    }

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

            // ─────────── EQ 3 BANDES (VERTICAL, PERSISTANT) ───────────
            Text(
                text = "Égaliseur 3 bandes",
                color = Color(0xFFB0BEC5),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ------- Graves -------
                EqVerticalFader(
                    label = "Graves",
                    gain = lowGain,
                    onGainChange = { g ->
                        lowGain = g
                        applyAndSaveEq()
                    },
                    color = Color(0xFF4CAF50)
                )

                // ------- Médiums -------
                EqVerticalFader(
                    label = "Médiums",
                    gain = midGain,
                    onGainChange = { g ->
                        midGain = g
                        applyAndSaveEq()
                    },
                    color = Color(0xFFFFC107)
                )

                // ------- Aigus -------
                EqVerticalFader(
                    label = "Aigus",
                    gain = highGain,
                    onGainChange = { g ->
                        highGain = g
                        applyAndSaveEq()
                    },
                    color = Color(0xFF42A5F5)
                )
            }
        }
    }
}

/**
 * Une bande d'EQ : label, gros fader vertical en Canvas et valeur en dB.
 */
@Composable
private fun EqVerticalFader(
    label: String,
    gain: Float,                           // -12..+12
    onGainChange: (Float) -> Unit,
    color: Color
) {
    // Convertit -12..+12 -> 0..1
    var sliderValue by remember(gain) {
        mutableStateOf(((gain + 12f) / 24f).coerceIn(0f, 1f))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = String.format("%+d dB", gain.roundToInt()),
            color = Color(0xFFCFD8DC),
            fontSize = 11.sp
        )

        Spacer(Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .height(190.dp)   // hauteur du fader
                .width(40.dp)     // largeur confortable pour le doigt
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        // Vers le haut = augmente
                        val newSliderValue = (sliderValue - dragAmount / size.height)
                            .coerceIn(0f, 1f)
                        sliderValue = newSliderValue
                        val newGain = newSliderValue * 24f - 12f
                        onGainChange(newGain)
                    }
                }
        ) {
            val trackWidth = size.width * 0.35f
            val trackX = (size.width - trackWidth) / 2f
            val trackHeight = size.height

            // Piste grise
            drawRoundRect(
                color = Color.DarkGray,
                topLeft = Offset(trackX, 0f),
                size = Size(trackWidth, trackHeight),
                cornerRadius = CornerRadius(trackWidth / 2f)
            )

            // Piste colorée (du bas vers le haut)
            val activeHeight = trackHeight * sliderValue
            drawRoundRect(
                color = color,
                topLeft = Offset(trackX, trackHeight - activeHeight),
                size = Size(trackWidth, activeHeight),
                cornerRadius = CornerRadius(trackWidth / 2f)
            )

            // "Tête" du fader
            val thumbRadius = trackWidth * 0.9f
            val thumbCenter = Offset(
                x = size.width / 2f,
                y = trackHeight - activeHeight
            )
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = thumbCenter
            )
        }
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
        currentTrackUri = "demo://track",
        onClose = {}
    )
}