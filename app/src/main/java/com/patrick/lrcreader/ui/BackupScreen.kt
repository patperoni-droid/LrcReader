package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars   // ⭐ IMPORT AJOUTÉ
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.BackupManager
import com.patrick.lrcreader.getDisplayName
import com.patrick.lrcreader.nowString
import com.patrick.lrcreader.saveJsonToUri
import com.patrick.lrcreader.shareJson
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground

@Composable
fun BackupScreen(
    context: Context,
    onAfterImport: () -> Unit = {},
    onBack: () -> Unit
) {
    var lastImportFile by remember { mutableStateOf<String?>(null) }
    var lastImportTime by remember { mutableStateOf<String?>(null) }
    var lastImportSummary by remember { mutableStateOf<String?>(null) }

    var backupFolderUri by remember { mutableStateOf<Uri?>(BackupFolderPrefs.get(context)) }

    // nom de fichier personnalisable
    var backupFileName by remember { mutableStateOf("lrc_backup.json") }

    // on garde le json en mémoire le temps que l’utilisateur choisisse la cible
    val saveLauncherJson = remember { mutableStateOf("") }

    // IMPORT via picker système
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
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

    // EXPORT → "Enregistrer dans…"
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

    // choix d’un dossier de sauvegarde
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

    // Liste des .json présents dans le dossier choisi
    val jsonFilesInFolder by remember(backupFolderUri) {
        mutableStateOf(
            backupFolderUri?.let { uri ->
                try {
                    val doc = DocumentFile.fromTreeUri(context, uri)
                    doc?.listFiles()
                        ?.filter { it.isFile && (it.name ?: "").endsWith(".json", true) }
                        ?.sortedBy { it.name?.lowercase() }
                        ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            } ?: emptyList()
        )
    }

    val accent = Color(0xFFE386FF)
    val card = Color(0xFF141414)
    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)

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
            TextButton(onClick = onBack) { Text("← Retour", color = onBg) }
            Spacer(Modifier.height(4.dp))
            Text("Sauvegarde / Restauration", color = onBg, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))

            // ─── EXPORT ───────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(card, shape = RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Text("Exporter l’état", color = onBg, fontSize = 16.sp)
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

                // Champ pour le nom de fichier
                OutlinedTextField(
                    value = backupFileName,
                    onValueChange = { backupFileName = it },
                    label = { Text("Nom du fichier de sauvegarde") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            val json = BackupManager.exportState(context, null, emptyList())
                            saveLauncherJson.value = json

                            val trimmed = backupFileName.trim().ifEmpty { "lrc_backup" }
                            val finalName =
                                if (trimmed.endsWith(".json", ignoreCase = true)) trimmed
                                else "$trimmed.json"

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

                        val trimmed = backupFileName.trim().ifEmpty { "lrc_backup" }
                        val finalName =
                            if (trimmed.endsWith(".json", ignoreCase = true)) trimmed
                            else "$trimmed.json"

                        shareJson(context, finalName, json)
                    }) {
                        Text("Partager", fontSize = 12.sp, color = accent)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ─── IMPORT ───────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(card, shape = RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Text("Importer une sauvegarde", color = onBg, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = { fileLauncher.launch(arrayOf("application/json")) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF46405A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Choisir un fichier…", fontSize = 12.sp)
                }

                Spacer(Modifier.height(10.dp))

                if (backupFolderUri != null) {
                    Text("Fichiers dans le dossier :", color = sub, fontSize = 11.sp)
                    if (jsonFilesInFolder.isEmpty()) {
                        Text("• Aucun .json trouvé", color = onBg, fontSize = 12.sp)
                    } else {
                        jsonFilesInFolder.forEach { doc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    doc.name ?: "sauvegarde.json",
                                    color = onBg,
                                    fontSize = 12.sp
                                )
                                TextButton(onClick = {
                                    try {
                                        val json =
                                            context.contentResolver.openInputStream(doc.uri)
                                                ?.bufferedReader()
                                                ?.use { it.readText() }
                                        if (!json.isNullOrBlank()) {
                                            BackupManager.importState(context, json) {
                                                lastImportFile = doc.name
                                                lastImportTime = nowString()
                                                lastImportSummary = "Import réussi"
                                                onAfterImport()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        lastImportSummary =
                                            "Échec de l’import (${e.message ?: "erreur inconnue"})"
                                    }
                                }) {
                                    Text("Importer", fontSize = 11.sp, color = accent)
                                }
                            }
                        }
                    }
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
                            color = if (it.startsWith("Import réussi")) Color(0xFF6CFF9C)
                            else Color(0xFFFF8A80),
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
}