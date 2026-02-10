package com.patrick.lrcreader.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.BackupManager
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.SplFolders
import com.patrick.lrcreader.getDisplayName
import com.patrick.lrcreader.nowString
import com.patrick.lrcreader.saveJsonToUri
import com.patrick.lrcreader.shareJson
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * BackupScreen (UI simplifié)
 * - Export : CreateDocument()
 * - Import : OpenDocument()
 * + ✅ INTERNAL : export/import direct dans SPL_Music/Backups (sans picker Android)
 */
@Composable
fun BackupScreen(
    context: Context,
    onAfterImport: () -> Unit = {},
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // État dernier import
    var lastImportFile by remember { mutableStateOf<String?>(null) }
    var lastImportTime by remember { mutableStateOf<String?>(null) }
    var lastImportSummary by remember { mutableStateOf<String?>(null) }

    // ✅ Loading import (évite sensation "rien ne se passe")
    var isImporting by remember { mutableStateOf(false) }

    // ✅ Détecte le mode INTERNAL via le root "file://"
    val rootUri = remember { BackupFolderPrefs.getLibraryRootUri(context) }
    val isInternalMode = rootUri?.scheme == "file"

    // ✅ Liste des backups internes (SPL_Music/Backups)
    var internalBackupFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showInternalImportDialog by remember { mutableStateOf(false) }

    fun refreshInternalBackups() {
        if (!isInternalMode) return
        val dir = SplFolders.backupsDirFile(context)
        val list = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        internalBackupFiles = list
    }

    fun importBackupJsonText(json: String, fileLabel: String) {
        // ✅ IMPORTANT: on ne bloque pas l'UI
        scope.launch {
            isImporting = true
            try {
                withContext(Dispatchers.IO) {
                    BackupManager.importState(context, json) {
                        // ✅ force la bibliothèque à se reconstruire après import
                        LibraryIndexCache.clear(context)
                    }
                }

                lastImportFile = fileLabel
                lastImportTime = nowString()
                lastImportSummary = "Import réussi"
                onAfterImport()

            } catch (e: Exception) {
                lastImportSummary = "Échec de l’import (${e.message ?: "erreur inconnue"})"
            } finally {
                isImporting = false
            }
        }
    }

    // ✅ refresh auto en INTERNAL
    LaunchedEffect(isInternalMode) {
        if (isInternalMode) refreshInternalBackups()
    }

    // nom de fichier export
    var backupFileName by remember { mutableStateOf("lrc_backup.json") }

    // on garde le json en mémoire le temps que l’utilisateur choisisse la cible
    val saveLauncherJson = remember { mutableStateOf("") }

    // Palette (garde ta charte)
    val onBg = Color(0xFFFFF8E1)
    val sub = Color(0xFFB0BEC5)
    val card = Color(0xFF181818)
    val cardBorder = Color(0x22FFFFFF)
    val accent = Color(0xFFFFC107)
    val danger = Color(0xFFFF8A80)
    val ok = Color(0xFF6CFF9C)

    // IMPORT via picker système (✅ non-bloquant)
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            isImporting = true
            try {
                // 1) Lire le fichier en IO
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                }

                if (json.isNullOrBlank()) {
                    lastImportSummary = "Fichier vide ou illisible"
                    return@launch
                }

                // 2) Import en IO
                withContext(Dispatchers.IO) {
                    BackupManager.importState(context, json) {
                        LibraryIndexCache.clear(context)
                    }
                }

                // 3) UI update
                lastImportFile = getDisplayName(context, uri)
                lastImportTime = nowString()
                lastImportSummary = "Import réussi"
                onAfterImport()

            } catch (e: Exception) {
                lastImportSummary = "Échec de l’import (${e.message ?: "erreur inconnue"})"
            } finally {
                isImporting = false
            }
        }
    }

    // EXPORT → "Enregistrer dans…"
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val jsonToSave = saveLauncherJson.value
        if (uri != null && jsonToSave.isNotBlank()) {
            val okSave = saveJsonToUri(context, uri, jsonToSave)
            Toast.makeText(
                context,
                if (okSave) "Sauvegarde enregistrée" else "Impossible d’enregistrer",
                LENGTH_SHORT
            ).show()
        }
        saveLauncherJson.value = ""
    }

    DarkBlueGradientBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 12.dp
                )
                .verticalScroll(rememberScrollState())
        ) {
            // Header minimal
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("←", color = onBg, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Retour", color = onBg, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))

            // ─────────────────────────────────────────────────────────────
            //  CARTE : EXPORT
            // ─────────────────────────────────────────────────────────────
            SectionCard(
                title = "Exporter",
                subtitle = "Créer un fichier .json",
                accent = accent,
                card = card,
                border = cardBorder
            ) {
                OutlinedTextField(
                    value = backupFileName,
                    onValueChange = { backupFileName = it },
                    label = { Text("Nom du fichier", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                // ✅ INTERNAL export direct vers /SPL_Music/Backups
                if (isInternalMode) {
                    FilledTonalButton(
                        onClick = {
                            val json = BackupManager.exportState(context, null, emptyList())
                            val trimmed = backupFileName.trim().ifEmpty { "lrc_backup" }
                            val finalName =
                                if (trimmed.endsWith(".json", ignoreCase = true)) trimmed else "$trimmed.json"

                            val dir = SplFolders.backupsDirFile(context)
                            val target = File(dir, finalName)

                            runCatching {
                                target.writeText(json, Charsets.UTF_8)
                                Toast.makeText(context, "Backup écrit dans Backups internes", LENGTH_SHORT).show()
                                refreshInternalBackups()
                            }.onFailure {
                                Toast.makeText(context, "Impossible d’écrire le backup", LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF2A3A2A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text("Exporter vers Backups internes", fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            containerColor = Color(0xFF3E3A2C),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Enregistrer", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            val json = BackupManager.exportState(context, null, emptyList())

                            val trimmed = backupFileName.trim().ifEmpty { "lrc_backup" }
                            val finalName =
                                if (trimmed.endsWith(".json", ignoreCase = true)) trimmed
                                else "$trimmed.json"

                            shareJson(context, finalName, json)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF2A2A2A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Partager", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─────────────────────────────────────────────────────────────
            //  CARTE : IMPORT
            // ─────────────────────────────────────────────────────────────
            SectionCard(
                title = "Importer",
                subtitle = "Restaurer depuis un .json",
                accent = accent,
                card = card,
                border = cardBorder
            ) {
                // ✅ INTERNAL import direct depuis /SPL_Music/Backups
                if (isInternalMode) {
                    FilledTonalButton(
                        onClick = {
                            refreshInternalBackups()
                            showInternalImportDialog = true
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF2A3A2A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Importer depuis Backups internes", fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(8.dp))
                }

                FilledTonalButton(
                    onClick = {
                        fileLauncher.launch(arrayOf("application/json"))
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF3E3A2C),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Choisir un fichier .json", fontSize = 12.sp)
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = cardBorder)
                Spacer(Modifier.height(10.dp))

                if (isImporting) {
                    Text("Import en cours…", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                }

                Text("Dernier import", color = sub, fontSize = 11.sp)

                val hasAny = lastImportFile != null || lastImportTime != null || lastImportSummary != null
                if (!hasAny) {
                    Text("Aucun import pour l’instant.", color = sub, fontSize = 12.sp)
                } else {
                    lastImportFile?.let {
                        KeyValueRow(label = "Fichier", value = it, onBg = onBg, sub = sub)
                    }
                    lastImportTime?.let {
                        KeyValueRow(label = "Heure", value = it, onBg = onBg, sub = sub)
                    }
                    lastImportSummary?.let {
                        val c = if (it.startsWith("Import réussi")) ok else danger
                        KeyValueRow(label = "État", value = it, onBg = c, sub = sub)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ✅ Dialog import INTERNAL
    if (showInternalImportDialog) {
        AlertDialog(
            onDismissRequest = { showInternalImportDialog = false },
            title = { Text("Backups internes") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    if (internalBackupFiles.isEmpty()) {
                        Text("Aucun .json trouvé dans SPL_Music/Backups", color = sub, fontSize = 12.sp)
                    } else {
                        internalBackupFiles.take(12).forEach { f ->
                            TextButton(
                                onClick = {
                                    val json = runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
                                    if (!json.isNullOrBlank()) {
                                        importBackupJsonText(json, f.name)
                                        showInternalImportDialog = false
                                    } else {
                                        lastImportSummary = "Fichier vide ou illisible"
                                        // on laisse le dialog ouvert pour en choisir un autre
                                    }
                                }
                            ) {
                                Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        if (internalBackupFiles.size > 12) {
                            Text(
                                "(${internalBackupFiles.size} fichiers) — j’affiche les 12 plus récents.",
                                color = sub,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInternalImportDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    accent: Color,
    card: Color,
    border: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(card, shape = RoundedCornerShape(18.dp))
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(10.dp)
                    .background(accent, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color(0xFFB0BEC5), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun KeyValueRow(
    label: String,
    value: String,
    onBg: Color,
    sub: Color
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = sub,
            fontSize = 11.sp,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            color = onBg,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}