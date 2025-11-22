package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Écran d'accueil “pro”
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenPlayer: () -> Unit,
    onOpenConcertMode: () -> Unit,
    onOpenDjMode: () -> Unit,
    onOpenTuner: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotes: () -> Unit   // <--- AJOUT + CORRECT
) {
    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF050712),
            Color(0xFF060B1C),
            Color(0xFF09132A)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgGradient)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // ---------- TITRE ----------
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Live Player",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Paroles, playback et mixage, prêts pour la scène.",
                    fontSize = 13.sp,
                    color = Color(0xFFB7C0D8)
                )
            }

            Spacer(Modifier.height(18.dp))

            // ---------- BOUTONS ----------
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                NeonCardButton(
                    title = "Mode Lecteur",
                    subtitle = "Paroles synchronisées + playback",
                    icon = Icons.Filled.MusicNote,
                    accentGradient = Brush.horizontalGradient(
                        listOf(Color(0xFF9C27FF), Color(0xFF42A5F5))
                    ),
                    onClick = onOpenPlayer
                )

                NeonCardButton(
                    title = "Mode Concert",
                    subtitle = "Préparation scène, ne pas déranger",
                    icon = Icons.Filled.PlayCircleFilled,
                    accentGradient = Brush.horizontalGradient(
                        listOf(Color(0xFFFFA726), Color(0xFFFF4081))
                    ),
                    onClick = onOpenConcertMode
                )

                NeonCardButton(
                    title = "Mode DJ / Mix",
                    subtitle = "Crossfade, tempo, limiteur…",
                    icon = Icons.Filled.Headphones,
                    accentGradient = Brush.horizontalGradient(
                        listOf(Color(0xFF26C6DA), Color(0xFF7E57C2))
                    ),
                    onClick = onOpenDjMode
                )

                NeonCardButton(
                    title = "Accordeur",
                    subtitle = "Guitare & instruments",
                    icon = Icons.Filled.Tune,
                    accentGradient = Brush.horizontalGradient(
                        listOf(Color(0xFF7C4DFF), Color(0xFF40C4FF))
                    ),
                    onClick = onOpenTuner
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---------- BAS ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                SmallBottomChip(
                    icon = Icons.Filled.Person,
                    label = "Profil",
                    onClick = onOpenProfile
                )

                SmallBottomChip(
                    icon = Icons.Filled.Info,
                    label = "Tutoriel",
                    onClick = onOpenTutorial
                )

                // -------- NOTES (REMPLACE BIBLIOTHÈQUE) --------
                SmallBottomChip(
                    icon = Icons.Filled.LibraryMusic,
                    label = "Notes",
                    onClick = onOpenNotes        // <--- nouveau bouton Notes
                )

                SmallBottomChip(
                    icon = Icons.Filled.MoreHoriz,
                    label = "Réglages",
                    onClick = onOpenSettings
                )
            }
        }
    }
}

/* -------------------------------------------------------------------
   Composants réutilisables
-------------------------------------------------------------------- */

@Composable
private fun NeonCardButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentGradient: Brush,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val cardBg = Color(0xFF101426)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clickable(onClick = onClick),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(cardBg, shape)
                .border(
                    width = 1.3.dp,
                    brush = accentGradient,
                    shape = shape
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .width(4.dp)
                        .background(accentGradient, RoundedCornerShape(999.dp))
                )

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Color(0xFFCFD8E5),
                        fontSize = 12.sp
                    )
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.height(30.dp)
                )
            }
        }
    }
}

@Composable
private fun SmallBottomChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFB0BEC5)
        )
        Text(
            text = label,
            color = Color(0xFFB0BEC5),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}