package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Accordeur style "module analogique", même charte couleur que la console.
 *
 * Pour l’instant : maquette visuelle.
 * Tu pourras plus tard remplacer le slider de test par ton moteur d’analyse audio.
 */
@Composable
fun TunerScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    // Même fond que la console "Live in Pocket"
    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF171717),
            Color(0xFF101010),
            Color(0xFF181410)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            // ───── BARRE DU HAUT ─────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color(0xFFEEEEEE)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column {
                    Text(
                        text = "Accordeur",
                        color = Color(0xFFFFE082),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Module analogique",
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ───── MODULE PRINCIPAL ─────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1B1B1B)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {

                // Simu de l’offset en cents : -50 .. +50
                // (tu remplaceras ça par ta vraie valeur plus tard)
                var centsOffset by remember { mutableFloatStateOf(0f) }
                var currentNote by remember { mutableStateOf("A") }
                var currentFreq by remember { mutableStateOf("440 Hz") }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {

                    // Bandeau en haut du module
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF3A2C24),
                                        Color(0xFF4B372A),
                                        Color(0xFF3A2C24)
                                    )
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                Color(0x55FFFFFF),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "TUNE BUS",
                            color = Color(0xFFFFECB3),
                            fontSize = 13.sp,
                            letterSpacing = 2.sp
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    // ───── ZONE AFFICHAGE NOTE + VU CENTS ─────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Colonne gauche : indicateur "FLAT"
                        TunerSideMeter(
                            label = "FLAT",
                            isActive = centsOffset < -5f,
                            activeColor = Color(0xFF64B5F6)  // bleu
                        )

                        // Affichage central : note + fréquence
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                                .height(180.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF101010),
                                            Color(0xFF1E1E1E)
                                        )
                                    ),
                                    RoundedCornerShape(18.dp)
                                )
                                .border(
                                    1.dp,
                                    Color(0x44FFFFFF),
                                    RoundedCornerShape(18.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Lettre de la note
                                Text(
                                    text = currentNote,
                                    color = Color(0xFFFFF8E1),
                                    fontSize = 64.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = currentFreq,
                                    color = Color(0xFFB0BEC5),
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.height(16.dp))

                                // Bande LED centrale (vert / orange / rouge)
                                val absOffset = kotlin.math.abs(centsOffset)
                                val barColor =
                                    when {
                                        absOffset < 5f -> Color(0xFF81C784) // vert bien accordé
                                        absOffset < 15f -> Color(0xFFFFC107) // jaune
                                        else -> Color(0xFFFF5252) // rouge
                                    }

                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(10.dp)
                                        .background(
                                            Color(0xFF050505),
                                            RoundedCornerShape(999.dp)
                                        )
                                ) {
                                    // partie "active"
                                    val factor = (1f - (absOffset / 50f)).coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(factor)
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        barColor.copy(alpha = 0.2f),
                                                        barColor,
                                                    )
                                                ),
                                                RoundedCornerShape(999.dp)
                                            )
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    text = "${centsOffset.toInt()} cents",
                                    color = Color(0xFFCFD8DC),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Colonne droite : indicateur "SHARP"
                        TunerSideMeter(
                            label = "SHARP",
                            isActive = centsOffset > 5f,
                            activeColor = Color(0xFFFF5252)  // rouge
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // ───── BARRE DE CONTROLE / TEST ─────
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Entrée micro – maquette visuelle",
                                color = Color(0xFFB0BEC5),
                                fontSize = 12.sp
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Slider de test : -50 à +50 cents
                        Text(
                            text = "Test offset (maquette) : ${centsOffset.toInt()} cents",
                            color = Color(0xFFCFD8DC),
                            fontSize = 11.sp
                        )
                        Slider(
                            value = centsOffset,
                            onValueChange = { centsOffset = it },
                            valueRange = -50f..50f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = Color(0xFFFFC107),
                                activeTrackColor = Color(0xFFFFC107),
                                inactiveTrackColor = Color(0x33FFFFFF)
                            )
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "Plus tard : relié à ton vrai accordeur (détection de pitch).",
                            color = Color(0xFF757575),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bandeau bas type rack, comme sur la console
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF3E2723),
                                Color(0xFF212121),
                                Color(0xFF3E2723)
                            )
                        ),
                        shape = RoundedCornerShape(100.dp)
                    )
            )
        }
    }
}

/**
 * Petit vumètre vertical pour indiquer si on est trop bas / trop haut.
 */
@Composable
private fun TunerSideMeter(
    label: String,
    isActive: Boolean,
    activeColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color(0xFFB0BEC5),
            fontSize = 11.sp
        )

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .width(22.dp)
                .height(120.dp)
                .background(Color(0xFF050505), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .padding(3.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(8) { index ->
                    val segmentActive = isActive && index >= 3
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(
                                if (segmentActive) activeColor.copy(alpha = 0.8f)
                                else Color(0x33555555),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }
    }
}