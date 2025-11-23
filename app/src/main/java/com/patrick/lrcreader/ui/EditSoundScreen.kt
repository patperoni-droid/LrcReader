package com.patrick.lrcreader.ui

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

// ⭐ IMPORTS FOUNDATION
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

// ⭐ IMPORTS WINDOWINSETS — CE QUI MANQUAIT !
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues

// ⭐ IMPORTS MATERIAL3
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

// ⭐ IMPORTS STATE & COMPOSE
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ⭐ TES PREFS & THEME
import com.patrick.lrcreader.core.EditSoundPrefs
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground


@Composable
fun EditSoundScreen(
    context: Context,
    onBack: () -> Unit
) {
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf("Aucun fichier") }
    var durationMs by remember { mutableStateOf(0) }

    var startMs by remember { mutableStateOf(0) }
    var endMs by remember { mutableStateOf(0) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            mediaPlayer?.release()
            mediaPlayer = null

            pickedUri = uri
            pickedName = uri.lastPathSegment ?: "son"

            try {
                val mp = MediaPlayer()
                mp.setDataSource(context, uri)
                mp.prepare()
                val d = mp.duration
                durationMs = d

                val saved = EditSoundPrefs.get(context, uri)
                if (saved != null) {
                    startMs = saved.startMs.coerceIn(0, d)
                    endMs = saved.endMs.coerceIn(startMs, d)
                } else {
                    startMs = 0
                    endMs = d
                }
                mediaPlayer = mp

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Impossible de lire ce son", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)
    val card = Color(0xFF141414)

    DarkBlueGradientBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                    start = 14.dp,
                    end = 14.dp,
                    bottom = 8.dp
                )
                .verticalScroll(rememberScrollState())
        ) {

            TextButton(onClick = onBack) {
                Text("← Retour", color = onBg)
            }
            Spacer(Modifier.height(4.dp))

            Text("Édition de titre", color = onBg, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))


            // ─────────────────────────
            // 1. Choix du fichier audio
            // ─────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = card)) {
                Column(Modifier.padding(12.dp)) {

                    Text("1. Choisir un fichier audio", color = onBg, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))

                    FilledTonalButton(onClick = { audioPicker.launch("audio/*") }) {
                        Text("Choisir un fichier…", fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(6.dp))
                    Text("Fichier : $pickedName", color = sub, fontSize = 12.sp)

                    if (durationMs > 0) {
                        Text(
                            text = "Durée : ${formatMsEditSound(durationMs)}",
                            color = sub,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))


            // ───────────────────────────────
            // 2. Réglage des points IN / OUT
            // ───────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = card)) {
                Column(Modifier.padding(12.dp)) {

                    Text("2. Points d’entrée / sortie", color = onBg, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))

                    if (durationMs <= 0) {

                        Text(
                            "Choisis d’abord un fichier pour afficher les contrôles.",
                            color = sub,
                            fontSize = 12.sp
                        )

                    } else {

                        // ----------- START ----------
                        Text(
                            "Point d’entrée : ${formatMsEditSound(startMs)}",
                            color = onBg,
                            fontSize = 12.sp
                        )

                        Slider(
                            value = startMs.toFloat(),
                            onValueChange = { v ->
                                startMs = v.toInt().coerceIn(0, endMs)
                            },
                            valueRange = 0f..durationMs.toFloat()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                startMs = (startMs - 1000).coerceIn(0, endMs)
                            }) { Text("−1 s", color = onBg, fontSize = 11.sp) }

                            TextButton(onClick = {
                                startMs = (startMs + 1000).coerceIn(0, endMs)
                            }) { Text("+1 s", color = onBg, fontSize = 11.sp) }
                        }

                        Spacer(Modifier.height(8.dp))

                        // ----------- END ----------
                        Text(
                            "Point de sortie : ${formatMsEditSound(endMs)}",
                            color = onBg,
                            fontSize = 12.sp
                        )

                        Slider(
                            value = endMs.toFloat(),
                            onValueChange = { v ->
                                endMs = v.toInt().coerceIn(startMs, durationMs)
                            },
                            valueRange = 0f..durationMs.toFloat()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                endMs = (endMs - 1000).coerceIn(startMs, durationMs)
                            }) { Text("−1 s", color = onBg, fontSize = 11.sp) }

                            TextButton(onClick = {
                                endMs = (endMs + 1000).coerceIn(startMs, durationMs)
                            }) { Text("+1 s", color = onBg, fontSize = 11.sp) }
                        }

                        Spacer(Modifier.height(12.dp))


                        // ────────────────
                        // Lecture preview
                        // ────────────────
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                            FilledTonalButton(
                                onClick = {
                                    val mp = mediaPlayer ?: return@FilledTonalButton
                                    try {
                                        mp.seekTo(startMs)
                                        mp.start()
                                        isPlaying = true
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                enabled = mediaPlayer != null
                            ) {
                                Text("▶️ Lire le segment", fontSize = 12.sp)
                            }

                            FilledTonalButton(
                                onClick = {
                                    val mp = mediaPlayer ?: return@FilledTonalButton
                                    val previewStart =
                                        (endMs - 10_000).coerceAtLeast(startMs)
                                    try {
                                        mp.seekTo(previewStart)
                                        mp.start()
                                        isPlaying = true
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                enabled = mediaPlayer != null
                            ) {
                                Text("▶️ Écouter fin (10s)", fontSize = 12.sp)
                            }

                            TextButton(
                                onClick = {
                                    mediaPlayer?.pause()
                                    isPlaying = false
                                },
                                enabled = mediaPlayer != null && isPlaying
                            ) {
                                Text("⏹ Stop", color = onBg, fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(14.dp))


                        // ────────────────
                        // S A U V E G A R D E
                        // ────────────────
                        FilledTonalButton(
                            onClick = {
                                val uri = pickedUri
                                if (uri != null) {
                                    EditSoundPrefs.save(
                                        context = context,
                                        uri = uri,
                                        startMs = startMs,
                                        endMs = endMs
                                    )
                                    Toast.makeText(
                                        context,
                                        "Réglages enregistrés ✅",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Choisis d’abord un fichier",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) {
                            Text("💾 Enregistrer ces réglages", fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            "Ces valeurs seront ajoutées à la sauvegarde globale.",
                            color = sub,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}


/* utilitaire pour l’affichage mm:ss POUR CET ÉCRAN UNIQUEMENT */
private fun formatMsEditSound(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}