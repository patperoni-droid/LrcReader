/**
 * Écran : FillerSoundScreen
 *
 * Rôle principal :
 * - Permet de configurer le "fond sonore" automatiquement joué entre deux chansons.
 *
 * Fonctionnalités :
 *  1) Activer / désactiver le fond sonore.
 *  2) Choisir un dossier audio contenant les fichiers de fond sonore.
 *  3) Afficher le nom du dossier sélectionné.
 *  4) Régler le volume avec un slider (courbe douce pour les faibles volumes).
 *  5) Écouter un aperçu : Play / Stop du fond sonore.
 *  6) Supprimer le dossier et réinitialiser les réglages.
 *
 * Détails techniques :
 * - Les réglages sont stockés dans FillerSoundPrefs.
 * - La lecture réelle du fond sonore est gérée par FillerSoundManager.
 * - La sélection du dossier utilise OpenDocumentTree() et garde la permission.
 *
 * En résumé :
 * L’écran “Fond sonore” est l’endroit où l’utilisateur choisit le dossier
 * d’ambiance et règle son comportement pendant une prestation live.
 */
package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.FillerSoundPrefs
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlin.math.cbrt

@Composable
fun FillerSoundScreen(
    context: Context,
    onBack: () -> Unit
) {
    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)
    val card = Color(0xFF141414)

    var isEnabled by remember { mutableStateOf(FillerSoundPrefs.isEnabled(context)) }
    var fillerUri by remember { mutableStateOf(FillerSoundPrefs.getFillerFolder(context)) }
    var fillerName by remember {
        mutableStateOf(fillerUri?.lastPathSegment ?: "Aucun son sélectionné")
    }
    var isPreviewing by remember { mutableStateOf(false) }

    // mapping courbe : curseur “doux” en bas
    fun uiToRealVolume(u: Float): Float {
        val clamped = u.coerceIn(0f, 1f)
        return clamped * clamped * clamped // u³
    }

    fun realToUiVolume(r: Float): Float {
        val clamped = r.coerceIn(0f, 1f)
        return cbrt(clamped.toDouble()).toFloat() // racine cubique
    }

    val initialReal = FillerSoundPrefs.getFillerVolume(context)
    var uiFillerVolume by remember {
        mutableStateOf(realToUiVolume(initialReal))
    }

    // Sélecteur de dossier audio
    val fillerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            FillerSoundPrefs.saveFillerFolder(context, uri)
            fillerUri = uri
            fillerName = uri.lastPathSegment ?: "Dossier audio"
            Toast.makeText(context, "Dossier enregistré", Toast.LENGTH_SHORT).show()
        }
    }

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
            // HEADER
            TextButton(onClick = onBack) {
                Text("← Retour", color = onBg)
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Fond sonore",
                color = onBg,
                fontSize = 18.sp
            )

            Spacer(Modifier.height(10.dp))

            // CARTE PRINCIPALE
            Card(
                colors = CardDefaults.cardColors(containerColor = card)
            ) {
                Column(Modifier.padding(12.dp)) {

                    // ON / OFF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Activer le fond sonore",
                                color = onBg,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Lecture automatique après la fin d’un morceau.",
                                color = sub,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                isEnabled = checked
                                FillerSoundPrefs.setEnabled(context, checked)
                                if (!checked) {
                                    FillerSoundManager.fadeOutAndStop(0)
                                    isPreviewing = false
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // SÉLECTION DOSSIER
                    Text(
                        text = "Sélection du dossier",
                        color = onBg,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Ce dossier sera joué automatiquement quand un morceau se termine.",
                        color = sub,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))

                    FilledTonalButton(
                        onClick = { fillerLauncher.launch(null) },
                        enabled = isEnabled
                    ) {
                        Text("Choisir un dossier audio…", fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(8.dp))

                    Text("Actuel :", color = sub, fontSize = 11.sp)
                    Text(
                        text = fillerName,
                        color = if (fillerUri != null) Color(0xFFE040FB) else Color.Gray,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(14.dp))

                    // VOLUME
                    Text("Volume", color = sub, fontSize = 11.sp)

                    Slider(
                        value = uiFillerVolume,
                        onValueChange = { v ->
                            uiFillerVolume = v
                            val real = uiToRealVolume(v)
                            FillerSoundPrefs.saveFillerVolume(context, real)
                            FillerSoundManager.setVolume(real)
                        },
                        valueRange = 0f..1f,
                        enabled = isEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val realDisplay = uiToRealVolume(uiFillerVolume)
                    Text(
                        text = "${(realDisplay * 100).toInt()} %",
                        color = onBg,
                        fontSize = 11.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    // BOUTONS ÉCOUTE / SUPPR
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (!isPreviewing) {
                                    FillerSoundManager.startIfConfigured(context)
                                    FillerSoundManager.setVolume(
                                        uiToRealVolume(uiFillerVolume)
                                    )
                                    isPreviewing = true
                                } else {
                                    FillerSoundManager.fadeOutAndStop(200)
                                    isPreviewing = false
                                }
                            },
                            enabled = isEnabled && fillerUri != null
                        ) {
                            Text(
                                text = if (isPreviewing) "Arrêter l’écoute" else "▶︎ Écouter",
                                fontSize = 12.sp
                            )
                        }

                        TextButton(
                            onClick = {
                                FillerSoundManager.fadeOutAndStop(200)
                                isPreviewing = false
                                FillerSoundPrefs.clear(context)
                                fillerUri = null
                                fillerName = "Aucun son sélectionné"
                                uiFillerVolume = 0f
                            },
                            enabled = isEnabled && fillerUri != null
                        ) {
                            Text(
                                text = "🗑 Supprimer",
                                fontSize = 12.sp,
                                color = Color(0xFFFF8A80)
                            )
                        }
                    }
                }
            }
        }
    }
}