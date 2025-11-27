package com.patrick.lrcreader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Maquette "console analogique"
 * - Faders réalistes
 * - Bouton accordeur SUPPRIMÉ
 * - Textes réduits (pas de retour à la ligne)
 */
@Composable
fun MixerHomePreviewScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    onOpenFondSonore: () -> Unit = {},
    onOpenDj: () -> Unit = {},
    onOpenTuner: () -> Unit = {} // conservé mais non utilisé
) {

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

        Column(modifier = Modifier.fillMaxSize()) {

            // ---- BARRE DU HAUT ----
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

            // ---- PANNEAU PRINCIPAL ----
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {

                    // Bandeau BUS PRINCIPAL
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
                            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(10.dp)),
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

                    // ---- 3 TRANCHES ----
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
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bandeau bas style rack
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
 * Une tranche : vu-mètre + long fader + bouton.
 */
@Composable
private fun MixerChannelColumn(
    label: String,
    subtitle: String,
    icon: ImageVector,
    faderColor: Color,
    meterColor: Color,
    onClick: () -> Unit
) {

    val scope = rememberCoroutineScope()
    var level by remember { mutableFloatStateOf(0.75f) }

    // Animation VU
    val infinite = rememberInfiniteTransition(label)
    val vuAnim by infinite.animateFloat(
        initialValue = 0.1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1400 + (0..600).random(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Titres — police réduite
        Text(
            text = label,
            color = Color(0xFFFFF3E0),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = Color(0xFFB0BEC5),
            fontSize = 9.sp
        )

        Spacer(Modifier.height(10.dp))

        // VU-mètre
        Box(
            modifier = Modifier
                .height(90.dp)
                .width(28.dp)
                .background(Color(0xFF050505), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .padding(3.dp),
            contentAlignment = Alignment.BottomCenter
        ) {

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

        // FADER ANALOGIQUE — VERSION LONGUE
        Box(
            modifier = Modifier
                .height(310.dp)   // <-- PLUS LONG
                .width(40.dp),
            contentAlignment = Alignment.Center
        ) {

            // Gorge
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF050608), Color(0xFF15171B))
                        ),
                        RoundedCornerShape(999.dp)
                    )
            )

            // Curseur
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val clamped = level.coerceIn(0f, 1f)
                Spacer(Modifier.weight(1f - clamped))

                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(30.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFE0E0E0),
                                    Color(0xFFBDBDBD)
                                )
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(faderColor, RoundedCornerShape(999.dp))
                            .align(Alignment.Center)
                    )
                }

                Spacer(Modifier.weight(clamped))
            }

            // Slider transparent
            Slider(
                value = level,
                onValueChange = { level = it },
                valueRange = 0f..1f,
                modifier = Modifier
                    .matchParentSize()
                    .rotate(-90f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        // BOUTON (ICÔNE SEULE)
        Card(
            onClick = { scope.launch { onClick() } },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2725)),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = faderColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}