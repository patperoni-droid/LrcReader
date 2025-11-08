package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Folder
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.getDisplayName
import com.patrick.lrcreader.nowString
import com.patrick.lrcreader.saveJsonToUri
import com.patrick.lrcreader.shareJson
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.BackupManager
import com.patrick.lrcreader.core.EditSoundPrefs
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
            onOpenFiller = { current = MoreSection.Filler },
            onOpenEdit = { current = MoreSection.Edit }
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

        MoreSection.Edit -> EditSoundScreen(
            context = context,
            onBack = { current = MoreSection.Root }
        )
    }
}

private enum class MoreSection { Root, Backup, Filler, Edit }

// ─────────────────────────────────────────────
// Menu principal
// ─────────────────────────────────────────────
@Composable
private fun MoreRootScreen(
    onOpenBackup: () -> Unit,
    onOpenFiller: () -> Unit,
    onOpenEdit: () -> Unit
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
        SettingsItem("🛠  Édition de titre", onClick = onOpenEdit)

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

    var backupFolderUri by remember { mutableStateOf<Uri?>(BackupFolderPrefs.get(context)) }

    // pour le "CreateDocument"
    val saveLauncherJson = remember { mutableStateOf("") }

    // import fichier
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

    // export vers fichier choisi
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
        saveLauncherJson.value = ""
    }

    // choisir dossier de sauvegarde
    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                BackupFolderPrefs.save(context, treeUri)
                backupFolderUri = treeUri
                Toast.makeText(context, "Dossier de sauvegarde choisi", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        }
    }

    val accent = Color(0xFFE386FF)
    val card = Color(0xFF141414)
    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)

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
        Text("Sauvegarde / Restauration", color = onBg, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))

        // ─── EXPORT ─────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(card, shape = RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Text("Exporter l’état", color = onBg, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = saveName,
                onValueChange = { saveName = it },
                label = { Text("Nom du fichier", color = sub, fontSize = 11.sp) },
                placeholder = { Text("lrc_backup", color = sub, fontSize = 11.sp) },
                singleLine = true,
                textStyle = TextStyle(color = onBg, fontSize = 13.sp),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = accent,
                    unfocusedIndicatorColor = Color(0xFF3A3A3A),
                    cursorColor = onBg,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { treeLauncher.launch(null) }
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = accent
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (backupFolderUri != null)
                        "Changer de dossier de sauvegarde"
                    else
                        "Choisir un dossier de sauvegarde",
                    color = accent,
                    fontSize = 12.sp
                )
            }

            if (backupFolderUri != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Dossier actuel : ${backupFolderUri.toString().take(55)}…",
                    color = sub,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            val finalName = (saveName.trim().ifBlank { "lrc_backup" }) + ".json"

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = {
                        val json = BackupManager.exportState(context, null, emptyList())
                        if (backupFolderUri == null) {
                            // pas de dossier → on demande un fichier
                            saveLauncherJson.value = json
                            saveLauncher.launch(finalName)
                        } else {
                            val ok = saveJsonToFolder(
                                context,
                                backupFolderUri!!,
                                finalName,
                                json
                            )
                            Toast.makeText(
                                context,
                                if (ok) "Sauvegarde enregistrée" else "Impossible d’enregistrer",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1E1E1E),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Enregistrer", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = {
                        val json = BackupManager.exportState(context, null, emptyList())
                        saveLauncherJson.value = json
                        saveLauncher.launch(finalName)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF46405A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Enregistrer dans…", fontSize = 12.sp)
                }

                TextButton(onClick = {
                    val json = BackupManager.exportState(context, null, emptyList())
                    shareJson(context, finalName, json)
                }) {
                    Text("Partager", fontSize = 12.sp, color = accent)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ─── IMPORT ─────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(card, shape = RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Text("Importer une sauvegarde", color = onBg, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))

            FilledTonalButton(
                onClick = { fileLauncher.launch("application/json") },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF46405A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("Choisir un fichier…", fontSize = 12.sp)
            }

            Spacer(Modifier.height(6.dp))

            TextButton(onClick = { treeLauncher.launch(null) }) {
                Text(
                    "🔓 Ré-autoriser l’accès à un dossier",
                    fontSize = 12.sp,
                    color = accent
                )
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

        Spacer(Modifier.height(24.dp))
    }
}

private fun saveJsonToFolder(
    context: Context,
    treeUri: Uri,
    fileName: String,
    json: String
): Boolean {
    return try {
        val docTree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val existing = docTree.findFile(fileName)
        val targetFile = existing ?: docTree.createFile("application/json", fileName)
        if (targetFile == null) return false

        context.contentResolver.openOutputStream(targetFile.uri, "rwt")?.use { out ->
            out.write(json.toByteArray())
            out.flush()
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
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
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            FillerSoundPrefs.saveFillerUri(context, uri)
            fillerUri = uri
            fillerName = uri.lastPathSegment ?: "Dossier choisi"
            Toast.makeText(context, "Dossier enregistré", Toast.LENGTH_SHORT).show()
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
                    "Ce son (ou dossier) est joué automatiquement quand un morceau se termine.",
                    color = sub, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))

                FilledTonalButton(onClick = { fillerLauncher.launch(null) }) {
                    Text("Choisir un dossier audio…", fontSize = 12.sp)
                }

                Spacer(Modifier.height(8.dp))
                Text("Actuel :", color = sub, fontSize = 11.sp)
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
                            Text("🗑 Supprimer", fontSize = 12.sp, color = Color(0xFFFF8A80))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Sous-écran : Édition de titre
// ─────────────────────────────────────────────
@Composable
private fun EditSoundScreen(
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
        Text("Édition de titre", color = onBg, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))

        // 1. choix du fichier
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
                    Text("Durée : ${formatMs(durationMs)}", color = sub, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 2. réglage des points
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
                    Text("Point d’entrée : ${formatMs(startMs)}", color = onBg, fontSize = 12.sp)
                    Slider(
                        value = startMs.toFloat(),
                        onValueChange = { v ->
                            val newStart = v.toInt().coerceIn(0, endMs)
                            startMs = newStart
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

                    Text("Point de sortie : ${formatMs(endMs)}", color = onBg, fontSize = 12.sp)
                    Slider(
                        value = endMs.toFloat(),
                        onValueChange = { v ->
                            val newEnd = v.toInt().coerceIn(startMs, durationMs)
                            endMs = newEnd
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
                                val startPreview =
                                    (endMs - 10_000).coerceAtLeast(startMs).coerceAtLeast(0)
                                try {
                                    mp.seekTo(startPreview)
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
                                Toast.makeText(context, "Réglages enregistrés ✅", Toast.LENGTH_SHORT)
                                    .show()
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
                        "Ces valeurs seront dans les prefs et on pourra les ajouter à la sauvegarde globale.",
                        color = sub,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}