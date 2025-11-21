package com.patrick.lrcreader.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Écran d’accueil “waouh”
 *
 * On ne branche que des callbacks.
 * Tu décideras plus tard quoi ouvrir pour chaque bouton.
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
    onOpenSettings: () -> Unit
) {
    val backgroundColor = Color(0xFF080812)
    val context = LocalContext.current

    // ░░ Etat du dialog "Mode Concert" ░░
    var showConcertDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // ---------- Titre + sous-titre ----------
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Live Player",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Ton assistant de scène tout-en-un.",
                    fontSize = 14.sp,
                    color = Color(0xFFB0BEC5)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---------- GROS BOUTONS ----------
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                GradientBigButton(
                    title = "Mode Lecteur",
                    subtitle = "Paroles + playback",
                    colors = listOf(Color(0xFF7C4DFF), Color(0xFFE040FB)),
                    icon = Icons.Filled.MusicNote,
                    onClick = onOpenPlayer
                )

                GradientBigButton(
                    title = "Mode Concert",
                    subtitle = "Ne pas déranger, écran scène",
                    colors = listOf(Color(0xFFFF6F00), Color(0xFFFF4081)),
                    icon = Icons.Filled.PlayCircleFilled,
                    onClick = {
                        // 👉 On n’active plus directement,
                        // on affiche d’abord le choix (DND / Avion)
                        showConcertDialog = true
                    }
                )

                GradientBigButton(
                    title = "Mode DJ / Mix",
                    subtitle = "Mixer, tempo, limiteur…",
                    colors = listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)),
                    icon = Icons.Filled.Headphones,
                    onClick = onOpenDjMode
                )

                GradientBigButton(
                    title = "Accordeur",
                    subtitle = "Guitare & instruments",
                    colors = listOf(Color(0xFF26C6DA), Color(0xFF7E57C2)),
                    icon = Icons.Filled.Tune,
                    onClick = onOpenTuner
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---------- Bandeau bas : profil + aide + réglages ----------
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
                SmallBottomChip(
                    icon = Icons.Filled.LibraryMusic,
                    label = "Bibliothèque",
                    onClick = onOpenPlayer   // ou autre écran plus tard
                )
                SmallBottomChip(
                    icon = Icons.Filled.MoreHoriz,
                    label = "Réglages",
                    onClick = onOpenSettings
                )
            }
        }

        // ╔══════════════════════════════════════╗
        // ║   DIALOG "MODE CONCERT" (DND/AVION)  ║
        // ╚══════════════════════════════════════╝
        if (showConcertDialog) {
            AlertDialog(
                onDismissRequest = { showConcertDialog = false },
                title = {
                    Text(
                        text = "Mode Concert",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Text(
                        text = "Voulez-vous activer un mode pour éviter les interruptions pendant le concert ?",
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        TextButton(
                            onClick = {
                                showConcertDialog = false
                                // Ouvre les réglages "Ne pas déranger"
                                runCatching {
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                    context.startActivity(intent)
                                }
                                // Ensuite on bascule en mode concert (Player + concert mode)
                                onOpenConcertMode()
                            }
                        ) {
                            Text("Mode Ne pas déranger")
                        }

                        TextButton(
                            onClick = {
                                showConcertDialog = false
                                // Ouvre les réglages "Mode Avion"
                                runCatching {
                                    val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                                    context.startActivity(intent)
                                }
                                // Ensuite on bascule en mode concert
                                onOpenConcertMode()
                            }
                        ) {
                            Text("Mode avion")
                        }

                        TextButton(
                            onClick = {
                                showConcertDialog = false
                            }
                        ) {
                            Text("Annuler", color = Color.Gray)
                        }
                    }
                },
                dismissButton = {}
            )
        }
    }
}

/* -------------------------------------------------------------------
   Composants réutilisables
-------------------------------------------------------------------- */

@Composable
private fun GradientBigButton(
    title: String,
    subtitle: String,
    colors: List<Color>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(colors)
                )
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = Color(0xFFF5F5F5),
                        fontSize = 12.sp
                    )
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .height(32.dp)
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
        modifier = Modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFCFD8DC)
        )
        Text(
            text = label,
            color = Color(0xFFCFD8DC),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}