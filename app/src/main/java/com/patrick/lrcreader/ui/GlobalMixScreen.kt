package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground

/**
 * Écran de mixage global :
 * - 3 faders : Lecteur, DJ, Fond sonore.
 * - Permet d'équilibrer les niveaux entre eux.
 *
 * Ici, les valeurs vont de 0.0 à 1.0 (0% → muet, 100% → plein pot).
 */
@Composable
fun GlobalMixScreen(
    modifier: Modifier = Modifier,
    playerLevel: Float,
    onPlayerLevelChange: (Float) -> Unit,
    djLevel: Float,
    onDjLevelChange: (Float) -> Unit,
    fillerLevel: Float,
    onFillerLevelChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    val cardColor = Color(0xFF141414)
    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)
    val accent = Color(0xFFE386FF)

    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // HEADER
            TextButton(onClick = onBack) {
                Text("← Retour", color = onBg)
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Mixage général",
                color = onBg,
                fontSize = 20.sp
            )

            Text(
                text = "Réglage des niveaux : Lecteur, DJ, Fond sonore.",
                color = sub,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            // CARTES DES 3 FADERS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardColor, shape = RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // --- FADER LECTEUR ---
                MixFader(
                    title = "Lecteur (playback)",
                    subtitle = "Paroles + playback principal",
                    value = playerLevel,
                    onValueChange = onPlayerLevelChange
                )

                // --- FADER DJ ---
                MixFader(
                    title = "Mode DJ",
                    subtitle = "Crossfade, playlists DJ",
                    value = djLevel,
                    onValueChange = onDjLevelChange
                )

                // --- FADER FOND SONORE ---
                MixFader(
                    title = "Fond sonore",
                    subtitle = "Nappe entre les morceaux",
                    value = fillerLevel,
                    onValueChange = onFillerLevelChange
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Astuce : commence vers 70–80% partout, puis ajuste en live.",
                color = sub,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Petit bloc réutilisable : titre + sous-titre + slider + pourcentage.
 */
@Composable
private fun MixFader(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title, color = onBg, fontSize = 14.sp)
        Text(subtitle, color = sub, fontSize = 11.sp)

        Spacer(Modifier.height(6.dp))

        Slider(
            value = value,
            onValueChange = { v -> onValueChange(v.coerceIn(0f, 1f)) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )

        // Affichage du pourcentage (ça "bouge" visuellement quand tu règles le fader)
        val percent = (value * 100).toInt()
        Text("$percent %", color = onBg, fontSize = 11.sp)
    }
}