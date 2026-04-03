package com.patrick.lrcreader.ui.library

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.StorageModePrefs
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.launch

private const val SETUP_STORAGE_TAG = "SETUP_STORAGE"

@Composable
fun SetupInstallScreen(
    titleColor: Color,
    subtitleColor: Color,
    accent: Color,
    onSetupDone: () -> Unit,
    onImportNow: (() -> Unit)? = null,
    onImportLater: (() -> Unit)? = null,
    onDemoInstalled: ((DemoInstallResult) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var showImportPrompt by remember { mutableStateOf(false) }
    var isInstallingDemo by remember { mutableStateOf(false) }

    // ✅ Anti-boucle vieux téléphones (Téléchargements)
    var showBadFolderDialog by remember { mutableStateOf(false) }
    var pendingBadUri by remember { mutableStateOf<Uri?>(null) }

    // --------------------------------------------
    // Handler : configure SPL_Music sous le dossier choisi (SAF normal)
    // --------------------------------------------
    fun handlePickedUri(uri: Uri) {
        Log.i(SETUP_STORAGE_TAG, "setup:start backend=SAF pickedUri=$uri")
        Log.i(
            SETUP_STORAGE_TAG,
            "setup:picked authority=${uri.authority} treeId=${safeTreeDocumentId(uri)} docId=${safeDocumentId(uri)}"
        )

        val folders = initializeSafWorkspaceFromPickedTree(
            context = context,
            pickedTreeUri = uri,
            stage = "setup_install_screen:saf"
        ) ?: run {
            Log.e(SETUP_STORAGE_TAG, "setup:workspace_prepare_failed pickedUri=$uri")
            return
        }
        Log.i(
            SETUP_STORAGE_TAG,
            "setup:workspace_ready mode=${folders.snapshot.mode} status=${folders.snapshot.status} root=${folders.rootUri} audio=${folders.audioUri} smp=${folders.smpUri} dj=${folders.djUri}"
        )
        showImportPrompt = true
    }

    // --------------------------------------------
    // Picker robuste : Intent ACTION_OPEN_DOCUMENT_TREE + EXTRA_INITIAL_URI
    // --------------------------------------------
    val pickDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode != android.app.Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult

        // ⚠️ Téléchargements = piège sur certains vieux téléphones
        if (uri.authority == "com.android.providers.downloads.documents") {
            Log.w(SETUP_STORAGE_TAG, "setup:downloads_provider_detected pickedUri=$uri")
            pendingBadUri = uri
            showBadFolderDialog = true
            return@rememberLauncherForActivityResult
        }

        // ✅ Mode normal (SAF)
        StorageModePrefs.set(context, StorageModePrefs.Mode.SAF)
        Log.i(SETUP_STORAGE_TAG, "setup:storage_mode=SAF pickedUri=$uri")
        handlePickedUri(uri)
    }

    // -------------------- ÉCRAN 1 --------------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.setup_title), color = titleColor, fontSize = 26.sp)

            Spacer(Modifier.height(10.dp))

            Text(
                stringResource(R.string.setup_workspace_explainer),
                color = subtitleColor,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(26.dp))

            Button(
                onClick = {
                    StorageModePrefs.set(context, StorageModePrefs.Mode.INTERNAL)
                    Log.i(SETUP_STORAGE_TAG, "setup:storage_mode=INTERNAL recommended_workspace")
                    val folders = initializeInternalWorkspace(
                        context = context,
                        stage = "setup_install_screen:recommended"
                    )
                    if (folders == null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.setup_workspace_recommended_error),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }
                    Log.i(
                        SETUP_STORAGE_TAG,
                        "setup:workspace_ready mode=${folders.snapshot.mode} status=${folders.snapshot.status} root=${folders.rootUri} audio=${folders.audioUri} smp=${folders.smpUri} dj=${folders.djUri}"
                    )
                    showImportPrompt = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text(stringResource(R.string.setup_use_recommended_workspace), fontSize = 16.sp)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                        )

                        // ✅ EXTRA_INITIAL_URI (DocumentUri) pointe vers Documents
                        val initial = runCatching {
                            DocumentsContract.buildDocumentUri(
                                "com.android.externalstorage.documents",
                                "primary:Documents"
                            )
                        }.getOrNull()

                        if (initial != null) {
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
                        }
                    }
                    pickDocumentsLauncher.launch(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
            ) {
                Text(stringResource(R.string.setup_choose_workspace), fontSize = 16.sp)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                stringResource(R.string.setup_workspace_hint),
                color = Color(0xFF6F7A80),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    // -------------------- DIALOG "TÉLÉCHARGEMENTS" -> FALLBACK INTERNE --------------------
    if (showBadFolderDialog) {
        AlertDialog(
            onDismissRequest = { /* bloqué volontairement */ },
            title = { Text(stringResource(R.string.setup_workspace_unavailable_title)) },
            text = {
                Text(
                    stringResource(R.string.setup_workspace_unavailable_message)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBadFolderDialog = false

                        StorageModePrefs.set(context, StorageModePrefs.Mode.INTERNAL)
                        val folders = initializeInternalWorkspace(
                            context = context,
                            stage = "setup_install_screen:recommended_fallback"
                        )

                        Log.w(
                            SETUP_STORAGE_TAG,
                            "setup:storage_mode=INTERNAL fallback_root=${folders?.rootUri} audio=${folders?.audioUri} smp=${folders?.smpUri}"
                        )
                        if (folders == null) {
                            pendingBadUri = null
                            return@TextButton
                        }

                        pendingBadUri = null
                        showImportPrompt = true
                    }
                ) { Text(stringResource(R.string.setup_use_recommended_workspace)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBadFolderDialog = false
                        pendingBadUri = null
                    }
                ) { Text(stringResource(R.string.setup_choose_another_workspace)) }
            }
        )
    }

    // -------------------- ÉCRAN 2 (DIALOG PRO) --------------------
    if (showImportPrompt && onImportNow != null && onImportLater != null) {
        Dialog(
            onDismissRequest = { /* bloqué volontairement */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .widthIn(max = 520.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF0F0F0F),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.setup_import_prompt_title),
                            color = titleColor,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            stringResource(R.string.setup_import_prompt_subtitle),
                            color = subtitleColor,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        if (isInstallingDemo) {
                            Spacer(Modifier.height(14.dp))
                            CircularProgressIndicator(
                                color = accent,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.setup_install_demo_progress),
                                color = subtitleColor,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (isInstallingDemo) return@Button
                                    showImportPrompt = false
                                    onImportNow()
                                    onSetupDone()
                                },
                                enabled = !isInstallingDemo,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                )
                            ) { Text(stringResource(R.string.setup_import_now)) }

                            OutlinedButton(
                                onClick = {
                                    if (isInstallingDemo) return@OutlinedButton
                                    scope.launch {
                                        isInstallingDemo = true
                                        try {
                                            val result = installDemoLibrary(context)
                                            showImportPrompt = false
                                            onDemoInstalled?.invoke(result)
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.setup_install_demo_success),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onSetupDone()
                                        } catch (_: Exception) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.setup_install_demo_error),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } finally {
                                            isInstallingDemo = false
                                        }
                                    }
                                },
                                enabled = !isInstallingDemo,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                            ) { Text(stringResource(R.string.setup_install_demo)) }

                            OutlinedButton(
                                onClick = {
                                    if (isInstallingDemo) return@OutlinedButton
                                    showImportPrompt = false
                                    onImportLater()
                                    onSetupDone()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                                enabled = !isInstallingDemo
                            ) { Text(stringResource(R.string.setup_import_later)) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- HELPERS (TOP-LEVEL) ----------------

internal fun splToTreeUri(docUri: Uri): Uri {
    val authority = docUri.authority ?: return docUri
    val docId = runCatching { DocumentsContract.getDocumentId(docUri) }.getOrNull() ?: return docUri
    return DocumentsContract.buildTreeDocumentUri(authority, docId)
}

internal fun shouldUsePickedFolderAsSplRoot(
    folderName: String?,
    childNames: List<String>
): Boolean {
    if (normalizeSetupFolderToken(folderName) == "splmusic") {
        return true
    }

    val normalizedChildren = childNames.map(::normalizeSetupFolderToken)
    return normalizedChildren.any { child ->
        child == "backingtracks" || child == "backingtrack"
    }
}

private fun normalizeSetupFolderToken(rawValue: String?): String {
    return rawValue
        .orEmpty()
        .trim()
        .lowercase()
        .replace("_", "")
        .replace(" ", "")
        .replace(Regex("\\(\\d+\\)$"), "")
}

private fun safeTreeDocumentId(uri: Uri?): String? {
    if (uri == null) return null
    return runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
}

private fun safeDocumentId(uri: Uri?): String? {
    if (uri == null) return null
    return runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
}

internal fun listChildNames(parent: DocumentFile?): List<String> {
    return runCatching {
        parent?.listFiles()
            ?.mapNotNull { child -> child.name }
            ?.sorted()
            .orEmpty()
    }.getOrDefault(emptyList())
}
