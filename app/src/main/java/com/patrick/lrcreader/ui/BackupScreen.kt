package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.BackupManager
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.SplFolders
import com.patrick.lrcreader.core.backup.BACKUP_BUNDLE_EXTENSION
import com.patrick.lrcreader.core.backup.BackupBundleExportBuildResult
import com.patrick.lrcreader.core.backup.BackupBundleExporter
import com.patrick.lrcreader.core.backup.BackupBundleImporter
import com.patrick.lrcreader.core.backup.BackupBundleIo
import com.patrick.lrcreader.core.backup.BackupBundlePayload
import com.patrick.lrcreader.core.backup.BackupBundleRestorePreparationResult
import com.patrick.lrcreader.core.backup.BackupBundleRestorePreparer
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.getDisplayName
import com.patrick.lrcreader.nowString
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
private data class BackupImportSelectionInfo(
    val resolvedFileLabel: String,
    val mimeType: String?,
    val rawDisplayName: String?,
    val documentFileName: String?,
    val lastPathSegment: String?
)

private fun resolveBackupsInitialUri(context: Context): Uri? {
    val root = BackupFolderPrefs.getLibraryRootUri(context) ?: return null
    val rootDoc = DocumentFile.fromTreeUri(context, root) ?: return root
    val backups = rootDoc.findFile("Backups") ?: rootDoc.findFile("backups")
    return (backups?.takeIf { it.isDirectory }?.uri) ?: root
}

private fun resolveBackupImportSelectionInfo(
    context: Context,
    uri: Uri
): BackupImportSelectionInfo {
    val rawDisplayName = getDisplayName(context, uri)?.trim().takeUnless { it.isNullOrBlank() }
    val documentFileName = runCatching {
        DocumentFile.fromSingleUri(context, uri)?.name?.trim()
    }.getOrNull().takeUnless { it.isNullOrBlank() }
    val lastPathSegment = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringAfterLast(':')
        ?.trim()
        .takeUnless { it.isNullOrBlank() }
    val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()

    val baseName = rawDisplayName
        ?: documentFileName
        ?: lastPathSegment
        ?: "backup"

    val resolvedFileLabel = when {
        BackupBundleImporter.isBundleFileName(baseName) -> baseName
        mimeType.equals("application/zip", ignoreCase = true) -> ensureBundleFileName(baseName)
        mimeType.equals("application/json", ignoreCase = true) && !baseName.endsWith(".json", ignoreCase = true) -> "$baseName.json"
        else -> baseName
    }

    return BackupImportSelectionInfo(
        resolvedFileLabel = resolvedFileLabel,
        mimeType = mimeType,
        rawDisplayName = rawDisplayName,
        documentFileName = documentFileName,
        lastPathSegment = lastPathSegment
    )
}

@Composable
fun BackupScreen(
    context: Context,
    onAfterImport: (BackupManager.LastPlayed?) -> Unit = {},
    onBack: () -> Unit
) {
    val importTag = "BACKUP_IMPORT"
    val scope = rememberCoroutineScope()
    val sImportSuccess = stringResource(R.string.backup_import_success)
    val sImportEmptyUnreadable = stringResource(R.string.backup_import_empty_unreadable)
    val sSaveToastFailed = stringResource(R.string.backup_save_toast_failed)
    val sSessionSaveSuccess = stringResource(R.string.backup_session_save_success)
    val sInternalExportToastSuccess = stringResource(R.string.backup_internal_export_toast_success)
    val sInternalExportToastFailed = stringResource(R.string.backup_internal_export_toast_failed)
    val sBundleExportFailed = stringResource(R.string.backup_bundle_export_failed)
    val sBundleExportMissing = stringResource(R.string.backup_bundle_export_missing_songs)
    val sBundleImportInvalid = stringResource(R.string.backup_bundle_import_invalid)
    val sBundleImportSmpFailed = stringResource(R.string.backup_bundle_import_smp_failed)
    val sBundleImportRemapFailed = stringResource(R.string.backup_bundle_import_remap_failed)
    val sImportSuccessWithWarnings = stringResource(R.string.backup_import_success_with_warnings)
    val sBackupsTip = "Conseil : sauvegarde dans le dossier Backups pour restaurer sur un autre appareil."

    // État dernier import
    var lastImportFile by remember { mutableStateOf<String?>(null) }
    var lastImportTime by remember { mutableStateOf<String?>(null) }
    var lastImportSummary by remember { mutableStateOf<String?>(null) }

    // ✅ Loading import (évite sensation "rien ne se passe")
    var isImporting by remember { mutableStateOf(false) }

    // ✅ Détecte le mode INTERNAL via le root "file://"
    val rootUri = remember { BackupFolderPrefs.getLibraryRootUri(context) }
    val isInternalMode = rootUri?.scheme == "file"
    var backupsInitialUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(rootUri) {
        backupsInitialUri = withContext(Dispatchers.IO) {
            resolveBackupsInitialUri(context)
        }
    }

    // ✅ Liste des backups internes (SPL_Music/Backups)
    var internalBackupFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showInternalImportDialog by remember { mutableStateOf(false) }
    var pendingSafBundlePayload by remember { mutableStateOf<BackupBundlePayload?>(null) }
    var pendingSafBundleName by remember { mutableStateOf<String?>(null) }
    var pendingSessionBackupJson by remember { mutableStateOf<String?>(null) }

    fun refreshInternalBackups() {
        if (!isInternalMode) return
        val dir = SplFolders.backupsDirFile(context)
        val list = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        internalBackupFiles = list
    }

    suspend fun importBackupJsonText(
        json: String,
        fileLabel: String,
        source: String,
        successSummary: String = sImportSuccess
    ) {
        val importStart = SystemClock.elapsedRealtime()
        var restoredLastPlayed: BackupManager.LastPlayed? = null
        withContext(Dispatchers.IO) {
            val ioStart = SystemClock.elapsedRealtime()
            BackupManager.importState(context, json) {
                restoredLastPlayed = it
                // ✅ force la bibliothèque à se reconstruire après import
                LibraryIndexCache.clear(context)
            }
            Log.i(
                importTag,
                "IMPORT_JSON step=post_restore_steps took=${SystemClock.elapsedRealtime() - ioStart}ms source=$source file=$fileLabel"
            )
        }

        lastImportFile = fileLabel
        lastImportTime = nowString()
        lastImportSummary = successSummary
        onAfterImport(restoredLastPlayed)

        val lp = restoredLastPlayed
        if (lp != null) {
            val title = Uri.parse(lp.uri).lastPathSegment?.substringAfterLast('/') ?: lp.uri
            Log.i(importTag, "RESTORE_LAST_PLAYED applied id=${lp.uri} title=$title")
        } else {
            Log.i(importTag, "RESTORE_LAST_PLAYED missing")
        }
        Log.i(
            importTag,
            "IMPORT_JSON step=ui_post_restore took=${SystemClock.elapsedRealtime() - importStart}ms source=$source file=$fileLabel"
        )
    }

    // ✅ refresh auto en INTERNAL
    LaunchedEffect(isInternalMode) {
        if (isInternalMode) refreshInternalBackups()
    }

    // nom de fichier export
    var backupFileName by remember { mutableStateOf("lrc_backup$BACKUP_BUNDLE_EXTENSION") }

    // Palette (garde ta charte)
    val onBg = Color(0xFFFFF8E1)
    val sub = Color(0xFFB0BEC5)
    val card = Color(0xFF181818)
    val cardBorder = Color(0x22FFFFFF)
    val accent = Color(0xFFFFC107)
    val danger = Color(0xFFFF8A80)
    val ok = Color(0xFF6CFF9C)

    fun formatBundleExportFailure(result: BackupBundleExportBuildResult): String? {
        return when (result) {
            is BackupBundleExportBuildResult.MissingReferencedSongs -> {
                val detail = result.songIds.take(3).joinToString()
                val suffix = if (result.songIds.size > 3) ", …" else ""
                "$sBundleExportMissing $detail$suffix"
            }
            is BackupBundleExportBuildResult.SongExportFailed -> {
                val detail = result.songIds.take(3).joinToString()
                val suffix = if (result.songIds.size > 3) ", …" else ""
                "$sBundleExportFailed $detail$suffix"
            }
            is BackupBundleExportBuildResult.Success -> null
        }
    }

    suspend fun buildManualBundlePayloadOrNull() = when (
        val result = withContext(Dispatchers.IO) {
            BackupBundleExporter.buildManualBundlePayload(context, null, emptyList())
        }
    ) {
        is BackupBundleExportBuildResult.Success -> result.payload
        else -> {
            formatBundleExportFailure(result)?.let { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            null
        }
    }

    suspend fun buildSessionBackupJson(): String = withContext(Dispatchers.IO) {
        BackupManager.exportState(
            context = context,
            lastPlayer = null,
            libraryFolders = emptyList()
        )
    }

    fun formatBundleRestoreFailure(
        result: BackupBundleRestorePreparationResult
    ): String? {
        return when (result) {
            BackupBundleRestorePreparationResult.InvalidBundle -> sBundleImportInvalid
            BackupBundleRestorePreparationResult.NotBundle -> null
            is BackupBundleRestorePreparationResult.SmpImportFailed -> {
                val detail = result.reason ?: context.getString(R.string.backup_unknown_error)
                "$sBundleImportSmpFailed ${result.songId} ($detail)"
            }
            is BackupBundleRestorePreparationResult.RemapFailed -> {
                val firstFailure = result.failures.firstOrNull()
                val detail = if (firstFailure != null) {
                    "${firstFailure.path}: ${firstFailure.reason}"
                } else {
                    context.getString(R.string.backup_unknown_error)
                }
                "$sBundleImportRemapFailed $detail"
            }
            is BackupBundleRestorePreparationResult.Success -> null
        }
    }

    suspend fun importBackupBytes(
        bytes: ByteArray?,
        fileLabel: String,
        source: String,
        forceBundle: Boolean = false
    ) {
        if (bytes == null) {
            lastImportSummary = sImportEmptyUnreadable
            return
        }

        val effectiveFileLabel = if (forceBundle) {
            ensureBundleFileName(fileLabel.ifBlank { "backup" })
        } else {
            fileLabel
        }
        val shouldImportAsBundle = forceBundle || BackupBundleImporter.isBundleFileName(effectiveFileLabel)

        if (!shouldImportAsBundle) {
            val json = withContext(Dispatchers.IO) {
                val decodeStart = SystemClock.elapsedRealtime()
                val text = bytes.toString(Charsets.UTF_8)
                Log.i(
                    importTag,
                    "IMPORT_JSON step=decode_utf8 took=${SystemClock.elapsedRealtime() - decodeStart}ms source=$source file=$effectiveFileLabel chars=${text.length}"
                )
                text
            }

            if (json.isBlank()) {
                lastImportSummary = sImportEmptyUnreadable
                return
            }

            importBackupJsonText(json, effectiveFileLabel, source = source)
            return
        }

        val prepareStart = SystemClock.elapsedRealtime()
        val preparedRestore = withContext(Dispatchers.IO) {
            val importResult = BackupBundleImporter.importBundleIfApplicable(
                context = context,
                fileName = effectiveFileLabel,
                openInputStream = { bytes.inputStream() }
            )
            BackupBundleRestorePreparer.prepareStateJsonForRestore(importResult)
        }
        Log.i(
            importTag,
            "IMPORT_BUNDLE step=prepare_restore took=${SystemClock.elapsedRealtime() - prepareStart}ms source=$source file=$effectiveFileLabel forced=$forceBundle"
        )

        when (preparedRestore) {
            is BackupBundleRestorePreparationResult.Success -> {
                val successSummary = if (preparedRestore.warnings.isEmpty()) {
                    sImportSuccess
                } else {
                    context.getString(
                        R.string.backup_import_success_with_warnings,
                        preparedRestore.warnings.size
                    )
                }
                importBackupJsonText(
                    json = preparedRestore.stateJson,
                    fileLabel = effectiveFileLabel,
                    source = "${source}_bundle",
                    successSummary = successSummary
                )
            }
            else -> {
                lastImportSummary = formatBundleRestoreFailure(preparedRestore)
                    ?: sImportEmptyUnreadable
            }
        }
    }

    // IMPORT via picker système (✅ non-bloquant)
    val fileLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                val intent = super.createIntent(context, input)
                backupsInitialUri?.let { initial ->
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
                }
                return intent
            }
        }
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            isImporting = true
            val importStart = SystemClock.elapsedRealtime()
            try {
                // 1) Lire le fichier en IO (open + bytes)
                val bytes = withContext(Dispatchers.IO) {
                    val openStart = SystemClock.elapsedRealtime()
                    val input = context.contentResolver.openInputStream(uri)
                    Log.i(
                        importTag,
                        "IMPORT_JSON step=open_input_stream took=${SystemClock.elapsedRealtime() - openStart}ms source=saf uri=$uri opened=${input != null}"
                    )
                    val readStart = SystemClock.elapsedRealtime()
                    val data = input?.use { it.readBytes() }
                    Log.i(
                        importTag,
                        "IMPORT_JSON step=read_bytes took=${SystemClock.elapsedRealtime() - readStart}ms source=saf uri=$uri bytes=${data?.size ?: 0}"
                    )
                    data
                }
                val selectionInfo = withContext(Dispatchers.IO) {
                    resolveBackupImportSelectionInfo(context, uri)
                }
                val bundleByName = BackupBundleImporter.isBundleFileName(selectionInfo.resolvedFileLabel)
                val bundleByPayload = if (!bundleByName && bytes != null) {
                    withContext(Dispatchers.IO) {
                        BackupBundleIo.readOrNull(bytes.inputStream()) != null
                    }
                } else {
                    false
                }
                val resolvedRoute = when {
                    bundleByName || bundleByPayload -> "bundle"
                    else -> "json"
                }
                Log.i(
                    importTag,
                    "IMPORT_FILE dispatch source=saf uri=$uri rawDisplayName=${selectionInfo.rawDisplayName} documentFileName=${selectionInfo.documentFileName} lastPathSegment=${selectionInfo.lastPathSegment} mime=${selectionInfo.mimeType} fileLabel=${selectionInfo.resolvedFileLabel} bundleByName=$bundleByName bundleByPayload=$bundleByPayload route=$resolvedRoute"
                )
                importBackupBytes(
                    bytes = bytes,
                    fileLabel = selectionInfo.resolvedFileLabel,
                    source = "saf",
                    forceBundle = bundleByPayload
                )
                Log.i(
                    importTag,
                    "IMPORT_FILE step=total_saf took=${SystemClock.elapsedRealtime() - importStart}ms uri=$uri file=${selectionInfo.resolvedFileLabel} route=$resolvedRoute"
                )
            } catch (e: Exception) {
                val detail = e.message ?: context.getString(R.string.backup_unknown_error)
                lastImportSummary = context.getString(R.string.backup_import_failed, detail)
                Log.e(importTag, "IMPORT_JSON failed source=saf uri=$uri", e)
            } finally {
                isImporting = false
            }
        }
    }

    // EXPORT → "Enregistrer dans…"
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) {
            pendingSafBundlePayload = null
            pendingSafBundleName = null
            return@rememberLauncherForActivityResult
        }

        val payload = pendingSafBundlePayload
        val requestedName = pendingSafBundleName
        pendingSafBundlePayload = null
        pendingSafBundleName = null

        if (payload == null) {
            Log.w(
                importTag,
                "EXPORT_BUNDLE step=missing_pending_payload uri=$uri requestedName=$requestedName"
            )
            Toast.makeText(context, sSaveToastFailed, LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            val stateJsonBytes = payload.stateJson.toByteArray(Charsets.UTF_8).size
            val smpCount = payload.smpFiles.size
            Log.i(
                importTag,
                "EXPORT_BUNDLE step=start_saf_write uri=$uri requestedName=$requestedName stateJsonBytes=$stateJsonBytes smpCount=$smpCount"
            )
            val saveSucceeded = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(uri)
                    Log.i(
                        importTag,
                        "EXPORT_BUNDLE step=open_output_stream uri=$uri opened=${output != null}"
                    )
                    output ?: return@runCatching false
                    output.use { stream ->
                        BackupBundleIo.write(stream, payload)
                    }
                    Log.i(
                        importTag,
                        "EXPORT_BUNDLE step=write_success uri=$uri stateJsonBytes=$stateJsonBytes smpCount=$smpCount"
                    )
                    true
                }.getOrElse { error ->
                    Log.e(
                        importTag,
                        "EXPORT_BUNDLE step=write_failed uri=$uri stateJsonBytes=$stateJsonBytes smpCount=$smpCount",
                        error
                    )
                    false
                }
            }

            if (saveSucceeded) {
                Toast.makeText(
                    context,
                    "Backup enregistré. $sBackupsTip",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, sSaveToastFailed, LENGTH_SHORT).show()
            }
        }
    }

    val sessionSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) {
            pendingSessionBackupJson = null
            return@rememberLauncherForActivityResult
        }

        val sessionJson = pendingSessionBackupJson
        pendingSessionBackupJson = null

        if (sessionJson == null) {
            Log.w(importTag, "EXPORT_SESSION step=missing_pending_json uri=$uri")
            Toast.makeText(context, sSaveToastFailed, LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            val saveSucceeded = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(uri)
                    output ?: return@runCatching false
                    output.use { stream ->
                        stream.write(sessionJson.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    }
                    true
                }.getOrElse { error ->
                    Log.e(importTag, "EXPORT_SESSION step=write_failed uri=$uri", error)
                    false
                }
            }

            if (saveSucceeded) {
                Toast.makeText(context, sSessionSaveSuccess, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, sSaveToastFailed, LENGTH_SHORT).show()
            }
        }
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
                    Text(stringResource(R.string.backup_back), color = onBg, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(card, shape = RoundedCornerShape(18.dp))
                    .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            val sessionJson = buildSessionBackupJson()
                            pendingSessionBackupJson = sessionJson
                            sessionSaveLauncher.launch("session_backup.json")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF24405A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(stringResource(R.string.backup_save_session_button), fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─────────────────────────────────────────────────────────────
            //  CARTE : IMPORT
            // ─────────────────────────────────────────────────────────────
            SectionCard(
                title = stringResource(R.string.backup_section_import_title),
                subtitle = stringResource(R.string.backup_section_import_subtitle),
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
                        Text(stringResource(R.string.backup_import_from_internal), fontSize = 12.sp)
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
                    Text(stringResource(R.string.backup_choose_json_file), fontSize = 12.sp)
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = cardBorder)
                Spacer(Modifier.height(10.dp))

                if (isImporting) {
                    Text(
                        stringResource(R.string.backup_import_in_progress),
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Text(stringResource(R.string.backup_last_import), color = sub, fontSize = 11.sp)

                val hasAny = lastImportFile != null || lastImportTime != null || lastImportSummary != null
                if (!hasAny) {
                    Text(stringResource(R.string.backup_no_import_yet), color = sub, fontSize = 12.sp)
                } else {
                    lastImportFile?.let {
                        KeyValueRow(label = stringResource(R.string.backup_kv_file), value = it, onBg = onBg, sub = sub)
                    }
                    lastImportTime?.let {
                        KeyValueRow(label = stringResource(R.string.backup_kv_time), value = it, onBg = onBg, sub = sub)
                    }
                    lastImportSummary?.let {
                        val c = if (it == sImportSuccess) ok else danger
                        KeyValueRow(label = stringResource(R.string.backup_kv_status), value = it, onBg = c, sub = sub)
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
            title = { Text(stringResource(R.string.backup_internal_dialog_title)) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    if (internalBackupFiles.isEmpty()) {
                        Text(stringResource(R.string.backup_internal_no_json_found), color = sub, fontSize = 12.sp)
                    } else {
                        internalBackupFiles.take(12).forEach { f ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        isImporting = true
                                        try {
                                            val bytes = withContext(Dispatchers.IO) {
                                                val readStart = SystemClock.elapsedRealtime()
                                                val data = runCatching { f.readBytes() }.getOrNull()
                                                Log.i(
                                                    importTag,
                                                    "IMPORT_JSON step=read_bytes took=${SystemClock.elapsedRealtime() - readStart}ms source=internal file=${f.name} bytes=${data?.size ?: 0}"
                                                )
                                                data
                                            }
                                            showInternalImportDialog = false
                                            importBackupBytes(bytes, fileLabel = f.name, source = "internal")
                                        } finally {
                                            isImporting = false
                                        }
                                    }
                                }
                            ) {
                                Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        if (internalBackupFiles.size > 12) {
                            Text(
                                stringResource(R.string.backup_internal_files_truncated, internalBackupFiles.size),
                                color = sub,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInternalImportDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
}

private fun ensureBundleFileName(rawName: String): String {
    val trimmed = rawName.trim().ifEmpty { "lrc_backup" }
    val normalized = when {
        trimmed.endsWith("$BACKUP_BUNDLE_EXTENSION.zip", ignoreCase = true) -> {
            trimmed.dropLast(4)
        }
        trimmed.endsWith(".zip", ignoreCase = true) -> {
            trimmed.dropLast(4)
        }
        else -> trimmed
    }
    return if (normalized.endsWith(BACKUP_BUNDLE_EXTENSION, ignoreCase = true)) {
        normalized
    } else {
        "$normalized$BACKUP_BUNDLE_EXTENSION"
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
