package com.patrick.lrcreader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Maquette d’écran "console studio analogique" pour la Home.
 * Pour l’instant, tout est visuel (pas branché au vrai son).
 *
 * On pourra plus tard :
 *  - l’utiliser à la place de HomeScreen
 *  - ou l’inclure dans la Home actuelle.
 */
@Composable
fun MixerHomePreviewScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    onOpenFondSonore: () -> Unit = {},
    onOpenDj: () -> Unit = {},
    onOpenTuner: () -> Unit = {}
) {
    // Fond type "rack analogique" : dégradé sombre légèrement chaud
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
            // ----- BARRE DU HAUT -----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color(0xFFEEEEEE)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Live in Pocket",
                        color = Color(0xFFFFE082),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Console Studio",
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

            // ----- PANNEL PRINCIPAL -----
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Bandeau du haut façon étiquette de console
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
                            text = "BUS PRINCIPAL",
                            color = Color(0xFFFFECB3),
                            fontSize = 13.sp,
                            letterSpacing = 2.sp
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    // ----- LES 3 FADERS PRINCIPAUX -----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        MixerChannelColumn(
                            label = "LECTEUR",
                            subtitle = "Playlists",
                            icon = Icons.Filled.LibraryMusic,
                            faderColor = Color(0xFFFFC107),
                            meterColor = Color(0xFFFFA000),
                            onClick = onOpenPlayer
                        )

                        MixerChannelColumn(
                            label = "FOND",
                            subtitle = "Ambiance",
                            icon = Icons.Filled.MusicNote,
                            faderColor = Color(0xFF81C784),
                            meterColor = Color(0xFF66BB6A),
                            onClick = onOpenFondSonore
                        )

                        MixerChannelColumn(
                            label = "DJ",
                            subtitle = "Crossfade",
                            icon = Icons.Filled.Headphones,
                            faderColor = Color(0xFF64B5F6),
                            meterColor = Color(0xFF42A5F5),
                            onClick = onOpenDj
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // ----- PETITE TRANCHE ACCORDEUR -----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallTunerStrip(onClick = onOpenTuner)
                    }
                }
            }

            // Bandeau bas style "barre de rack"
            Spacer(Modifier.height(8.dp))
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
 * Une tranche de console : VU + fader + bouton.
 */
@Composable
private fun MixerChannelColumn(
    label: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    faderColor: Color,
    meterColor: Color,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Valeur du fader (0 à 1)
    var level by remember { mutableFloatStateOf(0.75f) }

    // Animation simple de VU-mètre (maquette)
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label)
    val vuAnim by infinite.animateFloat(
        initialValue = 0.1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1400 + (0..600).random(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Etiquettes en haut
        Text(
            text = label,
            color = Color(0xFFFFF3E0),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = Color(0xFFB0BEC5),
            fontSize = 11.sp
        )

        Spacer(Modifier.height(10.dp))

        // VU-mètre
        Box(
            modifier = Modifier
                .height(80.dp)
                .width(26.dp)
                .background(Color(0xFF050505), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .padding(3.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Fond gradué
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(10) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                if (index >= 7) Color(0x55FF5252)
                                else Color(0x33555555),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            // Barre "LED"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(vuAnim.coerceIn(0f, 1f))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                meterColor.copy(alpha = 0.1f),
                                meterColor,
                                Color(0xFFFF5252)
                            )
                        ),
                        RoundedCornerShape(6.dp)
                    )
            )
        }

        Spacer(Modifier.height(16.dp))

        // Fader vertical (slider tourné)
        Box(
            modifier = Modifier
                .height(190.dp)
                .width(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                modifier = Modifier
                    .height(190.dp)
                    .width(34.dp)
                    .rotate(-90f),
                value = level,
                onValueChange = {
                    level = it
                },
                valueRange = 0f..1f
            )
        }

        Spacer(Modifier.height(10.dp))

        // Bouton qui ouvre l’écran correspondant
        Card(
            onClick = {
                scope.launch { onClick() }
            },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A2725)
            ),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    faderColor.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            ),
                            RoundedCornerShape(999.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = faderColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = label,
                        color = Color(0xFFFFF8E1),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Ouvrir",
                        color = Color(0xFFB0BEC5),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Petite tranche "Accordeur" stylée.
 */
@Composable
private fun SmallTunerStrip(
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF212121)
        ),
        elevation = CardDefaults.cardElevation(6.dp),
        modifier = Modifier
            .width(220.dp)
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = Color(0xFFFFC107),
                modifier = Modifier.size(24.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Accordeur",
                    color = Color(0xFFFFF8E1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap pour ouvrir",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp
                )
            }

            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}