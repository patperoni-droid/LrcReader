package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.getDisplayName
import com.patrick.lrcreader.nowString
import com.patrick.lrcreader.saveJsonToUri
import com.patrick.lrcreader.shareJson
import com.patrick.lrcreader.core.BackupManager
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.FillerSoundPrefs

// ─────────────────────────────────────────────
// Écran principal : "Paramètres"
// ─────────────────────────────────────────────
@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    context: Context,
    onAfterImport: () -> Unit = {}
) {
    var current by remember { mutableStateOf(MoreSection.Root) }

    when (current) {
        MoreSection.Root -> MoreRootScreen(
            onOpenBackup = { current = MoreSection.Backup },
            onOpenFiller = { current = MoreSection.Filler }
        )

        MoreSection.Backup -> BackupScreen(
            context = context,
            onAfterImport = onAfterImport,
            onBack = { current = MoreSection.Root }
        )

        MoreSection.Filler -> FillerSoundScreen(
            context = context,
            onBack = { current = MoreSection.Root }
        )
    }
}

private enum class MoreSection { Root, Backup, Filler }

// ─────────────────────────────────────────────
// Menu principal
// ─────────────────────────────────────────────
@Composable
private fun MoreRootScreen(
    onOpenBackup: () -> Unit,
    onOpenFiller: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                start = 10.dp,
                end = 10.dp,
                bottom = 8.dp
            )
            .verticalScroll(rememberScrollState())
    ) {
        Text("Paramètres", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(4.dp))
        Spacer(Modifier.height(10.dp))

        SettingsItem("🎧  Fond sonore", onClick = onOpenFiller)
        SettingsItem("💾  Sauvegarde / Restauration", onClick = onOpenBackup)

        HorizontalDivider(color = Color(0xFF1E1E1E))
        SettingsItem("🎨  Interface", onClick = {})
        SettingsItem("🔊  Audio", onClick = {})
        SettingsItem("⚙️  Avancé", onClick = {})
    }
}

@Composable
private fun SettingsItem(label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp)
    ) {
        Text(label, color = Color.White, fontSize = 15.sp)
    }
    HorizontalDivider(color = Color(0xFF1E1E1E))
}

// ─────────────────────────────────────────────
// Sous-écran : Sauvegarde / Restauration
// ─────────────────────────────────────────────
@Composable
private fun BackupScreen(
    context: Context,
    onAfterImport: () -> Unit = {},
    onBack: () -> Unit
) {
    var saveName by remember { mutableStateOf("") }
    var lastImportFile by remember { mutableStateOf<String?>(null) }
    var lastImportTime by remember { mutableStateOf<String?>(null) }
    var lastImportSummary by remember { mutableStateOf<String?>(null) }

    // buffer du JSON à écrire après avoir choisi le fichier
    val saveLauncherJson = remember { mutableStateOf("") }

    // IMPORT d’un fichier existant
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                if (!json.isNullOrBlank()) {
                    BackupManager.importState(context, json) {
                        lastImportFile = getDisplayName(context, uri)
                        lastImportTime = nowString()
                        lastImportSummary = "Import réussi"
                        onAfterImport()
                    }
                }
            } catch (e: Exception) {
                lastImportSummary = "Échec de l’import (${e.message ?: "erreur inconnue"})"
            }
        }
    }

    // SAVE : on demande à Android où écrire
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val jsonToSave = saveLauncherJson.value
        if (uri != null && jsonToSave.isNotBlank()) {
            val ok = saveJsonToUri(context, uri, jsonToSave)
            Toast.makeText(
                context,
                if (ok) "Sauvegarde enregistrée" else "Impossible d’enregistrer",
                Toast.LENGTH_SHORT
            ).show()
        }
        // on vide
        saveLauncherJson.value = ""
    }

    // ré-autoriser un dossier
    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                Toast.makeText(context, "Accès au dossier autorisé", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        }
    }

    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)
    val card = Color(0xFF141414)
    val accent = Color(0xFFB06CFF)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                start = 14.dp,
                end = 14.dp,
                bottom = 8.dp
            )
            .verticalScroll(rememberScrollState())
    ) {
        TextButton(onClick = onBack) { Text("← Retour", color = onBg) }
        Spacer(Modifier.height(4.dp))
        Text("Sauvegarde / Restauration", color = onBg, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))

        // -------- EXPORT ----------
        Card(colors = CardDefaults.cardColors(containerColor = card)) {
            Column(Modifier.padding(12.dp)) {
                Text("Exporter l’état", color = onBg, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text("Nom du fichier", color = sub, fontSize = 11.sp) },
                    placeholder = { Text("lrc_backup", color = sub, fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = TextStyle(color = onBg, fontSize = 13.sp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = accent,
                        unfocusedIndicatorColor = Color(0xFF3A3A3A),
                        cursorColor = onBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                val finalName = (saveName.trim().ifBlank { "lrc_backup" }) + ".json"

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // bouton unique : génère + ouvre le sélecteur
                    FilledTonalButton(
                        onClick = {
                            val json = BackupManager.exportState(context, null, emptyList())
                            saveLauncherJson.value = json
                            saveLauncher.launch(finalName)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF1E1E1E)
                        )
                    ) {
                        Text("Enregistrer", fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = {
                            val json = BackupManager.exportState(context, null, emptyList())
                            shareJson(context, finalName, json)
                        }
                    ) {
                        Text("Partager", fontSize = 12.sp, color = accent)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // -------- IMPORT ----------
        Card(colors = CardDefaults.cardColors(containerColor = card)) {
            Column(Modifier.padding(12.dp)) {
                Text("Importer une sauvegarde", color = onBg, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { fileLauncher.launch("application/json") }) {
                    Text("Choisir un fichier…", fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { treeLauncher.launch(null) }) {
                    Text("🔓 Ré-autoriser l’accès à un dossier", fontSize = 12.sp, color = accent)
                }

                Spacer(Modifier.height(10.dp))
                if (lastImportFile != null || lastImportTime != null || lastImportSummary != null) {
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    Spacer(Modifier.height(8.dp))
                    Text("Dernier import", color = sub, fontSize = 11.sp)
                    lastImportFile?.let { Text("• Fichier : $it", color = onBg, fontSize = 12.sp) }
                    lastImportTime?.let { Text("• Heure : $it", color = onBg, fontSize = 12.sp) }
                    lastImportSummary?.let {
                        Text(
                            "• État : $it",
                            color = if (it.startsWith("Import réussi")) Color(0xFF6CFF9C) else Color(0xFFFF8A80),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text("Aucun import réalisé pour l’instant.", color = sub, fontSize = 12.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Sous-écran : Fond sonore
// ─────────────────────────────────────────────
@Composable
private fun FillerSoundScreen(
    context: Context,
    onBack: () -> Unit
) {
    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)
    val card = Color(0xFF141414)

    var fillerUri by remember { mutableStateOf(FillerSoundPrefs.getFillerUri(context)) }
    var fillerName by remember {
        mutableStateOf(fillerUri?.lastPathSegment ?: "Aucun son sélectionné")
    }
    var isPreviewing by remember { mutableStateOf(false) }
    var fillerVolume by remember { mutableStateOf(FillerSoundPrefs.getFillerVolume(context)) }

    val fillerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            FillerSoundPrefs.saveFillerUri(context, uri)
            fillerUri = uri
            fillerName = uri.lastPathSegment ?: "Son choisi"
            Toast.makeText(context, "Son enregistré : $fillerName", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                start = 14.dp,
                end = 14.dp,
                bottom = 8.dp
            )
            .verticalScroll(rememberScrollState())
    ) {
        TextButton(onClick = onBack) { Text("← Retour", color = onBg) }
        Spacer(Modifier.height(4.dp))
        Text("Fond sonore", color = onBg, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))

        Card(colors = CardDefaults.cardColors(containerColor = card)) {
            Column(Modifier.padding(12.dp)) {
                Text("Sélection du fond sonore", color = onBg, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ce son est joué automatiquement quand un morceau se termine.",
                    color = sub, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))

                FilledTonalButton(onClick = { fillerLauncher.launch("audio/*") }) {
                    Text("Choisir un fichier audio…", fontSize = 12.sp)
                }

                Spacer(Modifier.height(8.dp))
                Text("Fichier actuel :", color = sub, fontSize = 11.sp)
                Text(
                    fillerName,
                    color = if (fillerUri != null) Color(0xFFE040FB) else Color.Gray,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(14.dp))
                Text("Volume", color = sub, fontSize = 11.sp)
                Slider(
                    value = fillerVolume,
                    onValueChange = { v ->
                        fillerVolume = v
                        FillerSoundPrefs.saveFillerVolume(context, v)
                        if (isPreviewing) FillerSoundManager.setVolume(v)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${(fillerVolume * 100).toInt()} %", color = onBg, fontSize = 11.sp)

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            if (!isPreviewing) {
                                FillerSoundManager.startIfConfigured(context)
                                FillerSoundManager.setVolume(fillerVolume)
                                isPreviewing = true
                            } else {
                                FillerSoundManager.fadeOutAndStop(200)
                                isPreviewing = false
                            }
                        },
                        enabled = fillerUri != null
                    ) {
                        Text(
                            text = if (isPreviewing) "Arrêter l’écoute" else "▶︎ Écouter",
                            fontSize = 12.sp
                        )
                    }

                    if (fillerUri != null) {
                        TextButton(onClick = {
                            FillerSoundManager.fadeOutAndStop(200)
                            isPreviewing = false
                            FillerSoundPrefs.clear(context)
                            fillerUri = null
                            fillerName = "Aucun son sélectionné"
                        }) {
                            Text("🗑 Supprimer le son", fontSize = 12.sp, color = Color(0xFFFF8A80))
                        }
                    }
                }
            }
        }
    }
}