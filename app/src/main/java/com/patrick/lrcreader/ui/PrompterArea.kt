// Affichage du texte en mode prompteur AUTO

package com.patrick.lrcreader.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PrompterArea(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    text: String,
    isRunning: Boolean,
    onToggleRunning: () -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    highlightColor: Color
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // ───── COLONNE TEXTE (PROMPTEUR) ─────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mode prompteur",
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp
                )

                TextButton(onClick = onToggleRunning) {
                    Text(
                        text = if (isRunning) "Pause défilement" else "Démarrer défilement",
                        color = highlightColor,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (text.isBlank()) {
                Text(
                    text = "Aucune parole disponible pour ce morceau.\nAjoute du texte dans l’éditeur.",
                    color = Color.Gray,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            } else {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Spacer(Modifier.height(200.dp))
            }
        }

        // ───── COLONNE FADER VITESSE ─────
        Column(
            modifier = Modifier
                .width(72.dp)
                .padding(start = 8.dp)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Vitesse",
                color = Color(0xFFB0BEC5),
                fontSize = 11.sp
            )

            Spacer(Modifier.height(4.dp))

            // valeur texte (0.2 → 2.0)
            Text(
                text = String.format("%.1fx", speed),
                color = Color.White,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(8.dp))

            // Fader vertical (Slider tourné)
            Box(
                modifier = Modifier
                    .height(180.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Slider(
                    value = speed,
                    onValueChange = { newValue ->
                        onSpeedChange(newValue)
                    },
                    valueRange = 0.3f..2.0f,
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth()
                        .graphicsLayer {
                            // rotation en vertical
                            rotationZ = -90f
                        },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFFE040FB),
                        inactiveTrackColor = Color(0x55E040FB)
                    )
                )
            }
        }
    }
}