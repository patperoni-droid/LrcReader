package com.patrick.lrcreader.ui

import android.content.Context
import android.widget.Toast
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.patrick.lrcreader.core.BackupManager
import com.patrick.lrcreader.core.FillerSoundPrefs
import com.patrick.lrcreader.saveJsonToDownloads
import com.patrick.lrcreader.shareJson
import com.patrick.lrcreader.getDisplayName
import com.patrick.lrcreader.nowString

@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    context: Context,
    onAfterImport: () -> Unit = {}
) {
    // états export / import
    var exportText by remember { mutableStateOf("") }
    var saveName by remember { mutableStateOf("") }
    var lastImportFile by remember { mutableStateOf<String?>(null) }
    var lastImportTime by remember { mutableStateOf<String?>(null) }
    var lastImportSummary by remember { mutableStateOf<String?>(null) }

    // états son de remplissage
    var fillerUri by remember { mutableStateOf(FillerSoundPrefs.getFillerUri(context)) }
    var fillerName by remember { mutableStateOf(fillerUri?.lastPathSegment ?: "Aucun son sélectionné") }

    // --- launchers ---

    // import JSON
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }

                if (!json.isNullOrBlank()) {
                    BackupManager.importState(
                        context = context,
                        json = json
                    ) {
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
            } catch (_: Exception) { }
        }
    }

    // choisir le son de remplissage
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

    // couleurs
    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)
    val card = Color(0xFF141414)
    val accent = Color(0xFFB06CFF)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Plus", color = onBg, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text("Sauvegarde & restauration", color = sub, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        // =======================
        //      EXPORT
        // =======================
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        exportText = BackupManager.exportState(context, null, emptyList())
                    }) { Text("Générer", fontSize = 12.sp) }

                    val finalName = (saveName.trim().ifBlank { "lrc_backup" }) + ".json"

                    FilledTonalButton(
                        onClick = { saveJsonToDownloads(context, finalName, exportText) },
                        enabled = exportText.isNotBlank(),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E1E1E))
                    ) { Text("Enregistrer", fontSize = 12.sp) }

                    TextButton(
                        onClick = { shareJson(context, finalName, exportText) },
                        enabled = exportText.isNotBlank()
                    ) { Text("Partager", fontSize = 12.sp, color = accent) }
                }

                Spacer(Modifier.height(8.dp))
                Text("Aperçu", color = sub, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (exportText.isBlank()) "—"
                    else exportText.take(280) + if (exportText.length > 280) "…" else "",
                    color = onBg,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // =======================
        //      IMPORT
        // =======================
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
                    Spacer(Modifier.height(4.dp))
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

        // =======================
        //  SON DE REMPLISSAGE
        // =======================
        Spacer(Modifier.height(16.dp))
        Text("Son de remplissage", color = sub, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = card)) {
            Column(Modifier.padding(12.dp)) {
                Text("Sélection du son de remplissage", color = onBg, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ce son est joué automatiquement quand un morceau se termine.",
                    color = sub,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
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
                if (fillerUri != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        FillerSoundPrefs.clear(context)
                        fillerUri = null
                        fillerName = "Aucun son sélectionné"
                    }) {
                        Text("🗑 Supprimer le son", fontSize = 12.sp, color = Color(0xFFFF8A80))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
    }
}