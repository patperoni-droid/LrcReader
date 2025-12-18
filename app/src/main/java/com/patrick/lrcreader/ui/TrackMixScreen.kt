package com.patrick.lrcreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
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

@Composable
fun TrackMixScreen(
    modifier: Modifier = Modifier,
    highlightColor: Color = Color(0xFFE040FB),

    currentTrackGainDb: Int,
    onTrackGainChange: (Int) -> Unit,

    tempo: Float,
    onTempoChange: (Float) -> Unit,

    pitchSemi: Int,
    onPitchSemiChange: (Int) -> Unit,

    currentTrackUri: String?,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    val minDb = -12
    val maxDb = 0

    // ✅ défaut “anti-saturation”
    val defaultDb = -5

    val minTempo = 0.8f
    val maxTempo = 1.2f
    val minSemi = -6
    val maxSemi = 6

    // ✅ Valeur affichée/éditée dans l’écran (source de vérité UI)
    var displayGainDb by remember(currentTrackUri) {
        mutableStateOf(
            // Si le titre arrive avec 0 (cas fréquent quand rien n'a été réglé),
            // on préfère ouvrir sur -5dB.
            if (currentTrackGainDb == 0) defaultDb else currentTrackGainDb
        )
    }

    // ✅ Au changement de titre : si on reçoit 0 (pas de prefs), on force -5 UNE fois
    LaunchedEffect(currentTrackUri) {
        if (currentTrackUri != null && currentTrackGainDb == 0) {
            displayGainDb = defaultDb
            onTrackGainChange(defaultDb) // => persist + appli immédiate (selon ton wiring)
        } else {
            displayGainDb = currentTrackGainDb
        }
    }

    // Slider 0..1 basé sur displayGainDb
    var gainSlider by remember(displayGainDb) {
        mutableStateOf(((displayGainDb - minDb).toFloat() / (maxDb - minDb)).coerceIn(0f, 1f))
    }

    var tempoSlider by remember(tempo) {
        mutableStateOf(((tempo - minTempo) / (maxTempo - minTempo)).coerceIn(0f, 1f))
    }

    var pitchSlider by remember(pitchSemi) {
        mutableStateOf(((pitchSemi - minSemi).toFloat() / (maxSemi - minSemi)).coerceIn(0f, 1f))
    }

    val initialEq = remember(currentTrackUri) {
        currentTrackUri?.let { TrackEqPrefs.load(context, it) }
            ?: TrackEqSettings(0f, 0f, 0f)
    }

    var lowGain by remember { mutableStateOf(initialEq.low) }
    var midGain by remember { mutableStateOf(initialEq.mid) }
    var highGain by remember { mutableStateOf(initialEq.high) }

    fun applyAndSaveEq() {
        TrackEqEngine.setBands(lowGain, midGain, highGain)
        currentTrackUri?.let {
            TrackEqPrefs.save(context, it, TrackEqSettings(lowGain, midGain, highGain))
        }
    }

    LaunchedEffect(Unit) { applyAndSaveEq() }

    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ -> change.consume() }
                }
                .pointerInput(Unit) {
                    detectTapGestures { /* on consomme */ }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {

            Text(
                text = "Mixage du titre",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // ───── NIVEAU TITRE ─────
            Text("Niveau du titre", color = Color(0xFFB0BEC5), fontSize = 13.sp)
            Text(
                text = "${if (displayGainDb >= 0) "+$displayGainDb" else displayGainDb} dB",
                color = Color.White,
                fontSize = 12.sp
            )

            Slider(
                value = gainSlider,
                onValueChange = { v01 ->
                    gainSlider = v01
                    val newDb = (minDb + v01 * (maxDb - minDb)).toInt().coerceIn(minDb, maxDb)
                    displayGainDb = newDb
                    onTrackGainChange(newDb)
                },
                colors = SliderDefaults.colors(
                    thumbColor = highlightColor,
                    activeTrackColor = highlightColor.copy(alpha = 0.4f),
                    inactiveTrackColor = Color.DarkGray
                )
            )

            Spacer(Modifier.height(16.dp))

            // ───── TEMPO ─────
            Text("Tempo", color = Color(0xFFB0BEC5), fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(String.format("x%.2f", tempo), color = Color.White, fontSize = 12.sp)
                TextButton(onClick = {
                    onTempoChange(1f)
                    tempoSlider = ((1f - minTempo) / (maxTempo - minTempo))
                }) {
                    Text("Reset 1.00x", color = Color(0xFF80CBC4), fontSize = 11.sp)
                }
            }

            Slider(
                value = tempoSlider,
                onValueChange = {
                    tempoSlider = it
                    onTempoChange(minTempo + it * (maxTempo - minTempo))
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF80CBC4),
                    activeTrackColor = Color(0xFF80CBC4).copy(alpha = 0.4f),
                    inactiveTrackColor = Color.DarkGray
                )
            )

            Spacer(Modifier.height(16.dp))

            // ───── TONALITÉ ─────
            Text("Tonalité (demi-tons)", color = Color(0xFFB0BEC5), fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${if (pitchSemi >= 0) "+$pitchSemi" else pitchSemi} demi-tons",
                    color = Color.White,
                    fontSize = 12.sp
                )
                TextButton(onClick = {
                    onPitchSemiChange(0)
                    pitchSlider = ((0 - minSemi).toFloat() / (maxSemi - minSemi))
                }) {
                    Text("Reset 0", color = Color(0xFFCE93D8), fontSize = 11.sp)
                }
            }

            Slider(
                value = pitchSlider,
                onValueChange = {
                    pitchSlider = it
                    onPitchSemiChange((minSemi + it * (maxSemi - minSemi)).toInt())
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFCE93D8),
                    activeTrackColor = Color(0xFFCE93D8).copy(alpha = 0.4f),
                    inactiveTrackColor = Color.DarkGray
                )
            )

            Spacer(Modifier.height(20.dp))

            // ───── EQ ─────
            Text("Égaliseur 3 bandes", color = Color(0xFFB0BEC5), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EqVerticalFader("Graves", lowGain, { lowGain = it; applyAndSaveEq() }, Color(0xFF4CAF50))
                EqVerticalFader("Médiums", midGain, { midGain = it; applyAndSaveEq() }, Color(0xFFFFC107))
                EqVerticalFader("Aigus", highGain, { highGain = it; applyAndSaveEq() }, Color(0xFF42A5F5))
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TOUCHER ICI POUR REVENIR",
                    color = Color(0x55FFFFFF),
                    fontSize = 11.sp
                )
            }
        }
    }
}

/* ---------- EQ FADER ---------- */

@Composable
private fun EqVerticalFader(
    label: String,
    gain: Float,
    onGainChange: (Float) -> Unit,
    color: Color
) {
    var sliderValue by remember(gain) {
        mutableStateOf(((gain + 12f) / 24f).coerceIn(0f, 1f))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        Text(label, color = Color.White, fontSize = 12.sp)
        Text(String.format("%+d dB", gain.roundToInt()), color = Color(0xFFCFD8DC), fontSize = 11.sp)

        Canvas(
            modifier = Modifier
                .height(190.dp)
                .width(40.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, drag ->
                        change.consume()
                        sliderValue = (sliderValue - drag / size.height).coerceIn(0f, 1f)
                        onGainChange(sliderValue * 24f - 12f)
                    }
                }
        ) {
            val w = size.width * 0.35f
            val x = (size.width - w) / 2f
            drawRoundRect(Color.DarkGray, Offset(x, 0f), Size(w, size.height), CornerRadius(w))
            drawRoundRect(color, Offset(x, size.height * (1 - sliderValue)), Size(w, size.height * sliderValue), CornerRadius(w))
            drawCircle(Color.White, w * 0.9f, Offset(size.width / 2f, size.height * (1 - sliderValue)))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun TrackMixScreenPreview() {
    TrackMixScreen(
        currentTrackGainDb = 0,
        onTrackGainChange = {},
        tempo = 1f,
        onTempoChange = {},
        pitchSemi = 0,
        onPitchSemiChange = {},
        currentTrackUri = "demo",
        onClose = {}
    )
}
