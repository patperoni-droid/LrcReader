package com.patrick.lrcreader.ui
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * BackupScreen (UI clean / scène)
 * Objectif : moins de texte, hiérarchie claire, boutons cohérents, sans changer la logique.
 */
@Composable
fun BackupScreen(
    context: Context,
    onAfterImport: () -> Unit = {},
    onBack: () -> Unit
) {
    // État dernier import
    var lastImportFile by remember { mutableStateOf<String?>(null) }
    var lastImportTime by remember { mutableStateOf<String?>(null) }
    var lastImportSummary by remember { mutableStateOf<String?>(null) }

    var backupFolderUri by remember { mutableStateOf<Uri?>(BackupFolderPrefs.get(context)) }

    // nom de fichier personnalisable pour l’export
    var backupFileName by remember { mutableStateOf("lrc_backup.json") }

    // on garde le json en mémoire le temps que l’utilisateur choisisse la cible
    val saveLauncherJson = remember { mutableStateOf("") }

    // Palette (garde ta charte)
    val onBg = Color(0xFFFFF8E1)
    val sub = Color(0xFFB0BEC5)
    val card = Color(0xFF181818)
    val card2 = Color(0xFF141414)
    val cardBorder = Color(0x22FFFFFF)
    val accent = Color(0xFFFFC107)
    val danger = Color(0xFFFF8A80)
    val ok = Color(0xFF6CFF9C)

    // IMPORT via picker système (fallback)
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
            val okSave = saveJsonToUri(context, uri, jsonToSave)
            Toast.makeText(
                context,
                if (okSave) "Sauvegarde enregistrée" else "Impossible d’enregistrer",
                LENGTH_SHORT
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
                Toast.makeText(context, "Dossier de sauvegarde choisi", LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        }
    }

    // liste des .json présents dans le dossier choisi
    val jsonFilesInFolder: List<DocumentFile> =
        remember(backupFolderUri) {
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
        }

    // affiche ou non le menu de sélection interne
    var showJsonPicker by remember { mutableStateOf(false) }

    DarkBlueGradientBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 12.dp
                )
                .verticalScroll(rememberScrollState())
        ) {
            // HEADER (plus clean : 1 ligne titre + mini sous-titre)
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

            Text(
                text = "Sauvegarde",
                color = onBg,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Export / import des playlists et réglages.",
                color = sub,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(14.dp))

            // ─────────────────────────────────────────────────────────────
            //  CARTE : DOSSIER
            // ─────────────────────────────────────────────────────────────
            SectionCard(
                title = "Dossier de sauvegarde",
                subtitle = "",
                accent = accent,
                card = card,
                border = cardBorder
            ) {
                val folderName = backupFolderUri?.let { uri ->
                    DocumentFile.fromTreeUri(context, uri)?.name
                        ?: uri.toString().take(40)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(card2, RoundedCornerShape(14.dp))
                        .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                        .clickable { treeLauncher.launch(null) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = accent)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folderName ?: "Choisir un dossier",
                            color = onBg,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (folderName == null) "Tape pour autoriser un dossier"
                            else "Tape pour changer",
                            color = sub,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = sub
                    )
                }

                Spacer(Modifier.height(10.dp))

                val count = jsonFilesInFolder.size
                Text(
                    text = if (backupFolderUri == null) "Aucun dossier sélectionné"
                    else "$count sauvegarde(s) détectée(s) dans ce dossier",
                    color = sub,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(12.dp))

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
                FilledTonalButton(
                    onClick = {
                        if (jsonFilesInFolder.isNotEmpty()) {
                            showJsonPicker = true
                        } else {
                            fileLauncher.launch(arrayOf("application/json"))
                        }
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
                    Text("Choisir une sauvegarde", fontSize = 12.sp)
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = cardBorder)
                Spacer(Modifier.height(10.dp))

                // “Dernier import” plus compact
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

                Spacer(Modifier.height(6.dp))

                TextButton(onClick = { treeLauncher.launch(null) }) {
                    Text("Ré-autoriser / changer le dossier", fontSize = 12.sp, color = accent)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ─────────────────────────────────────────────
    //  POPUP : liste des .json dans le dossier
    // ─────────────────────────────────────────────
    if (showJsonPicker) {
        AlertDialog(
            onDismissRequest = { showJsonPicker = false },
            title = { Text("Choisir une sauvegarde", color = onBg, fontSize = 14.sp) },
            text = {
                Column {
                    if (jsonFilesInFolder.isEmpty()) {
                        Text("Aucun fichier .json trouvé.", fontSize = 12.sp, color = sub)
                    } else {
                        jsonFilesInFolder.forEach { doc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                                    .clickable {
                                        try {
                                            val json = context.contentResolver
                                                .openInputStream(doc.uri)
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
                                        } finally {
                                            showJsonPicker = false
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = accent
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = doc.name ?: "sauvegarde.json",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showJsonPicker = false
                        fileLauncher.launch(arrayOf("application/json"))
                    }
                ) {
                    Text("Autre fichier…", color = accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJsonPicker = false }) {
                    Text("Annuler", color = sub)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

/**
 * Carte section : titre + sous-titre + contenu
 * (purement UI : ne casse rien du métier)
 */
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
            Box(
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
