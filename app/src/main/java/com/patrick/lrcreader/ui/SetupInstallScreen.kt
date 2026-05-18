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
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.StorageModePrefs
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.launch

private const val SETUP_STORAGE_TAG = "SETUP_STORAGE"

private enum class SetupOnboardingStep {
    WORKSPACE,
    COMPLETE
}

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

    var currentStep by remember { mutableStateOf(SetupOnboardingStep.WORKSPACE) }
    var isInstallingDemo by remember { mutableStateOf(false) }

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
            Toast.makeText(
                context,
                context.getString(R.string.setup_workspace_prepare_error),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        Log.i(
            SETUP_STORAGE_TAG,
            "setup:workspace_ready mode=${folders.snapshot.mode} status=${folders.snapshot.status} root=${folders.rootUri} audio=${folders.audioUri} smp=${folders.smpUri} dj=${folders.djUri}"
        )
        currentStep = SetupOnboardingStep.COMPLETE
    }

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

    fun launchWorkspacePicker(preferMusicFolder: Boolean) {
        pickDocumentsLauncher.launch(createWorkspacePickerIntent(preferMusicFolder))
    }

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
            when (currentStep) {
                SetupOnboardingStep.WORKSPACE -> {
                    Text(
                        stringResource(R.string.setup_workspace_title),
                        color = titleColor,
                        fontSize = 26.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        stringResource(R.string.setup_workspace_explainer),
                        color = subtitleColor,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        stringResource(R.string.setup_workspace_music_recommended),
                        color = Color(0xFF6F7A80),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        stringResource(R.string.setup_workspace_android_confirmation_hint),
                        color = Color(0xFF8D969B),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(26.dp))

                    Button(
                        onClick = {
                            StorageModePrefs.set(context, StorageModePrefs.Mode.SAF)
                            launchWorkspacePicker(preferMusicFolder = true)
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
                        Text(stringResource(R.string.setup_use_music_workspace), fontSize = 16.sp)
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            StorageModePrefs.set(context, StorageModePrefs.Mode.SAF)
                            launchWorkspacePicker(preferMusicFolder = false)
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
                }

                SetupOnboardingStep.COMPLETE -> {
                    Text(
                        stringResource(R.string.setup_complete_title),
                        color = titleColor,
                        fontSize = 26.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        stringResource(R.string.setup_complete_message),
                        color = subtitleColor,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
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

                    Spacer(Modifier.height(26.dp))

                    Button(
                        onClick = {
                            onImportLater?.invoke()
                            onSetupDone()
                        },
                        enabled = !isInstallingDemo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(stringResource(R.string.setup_complete_start), fontSize = 16.sp)
                    }

                    if (onImportNow != null || onDemoInstalled != null) {
                        Spacer(Modifier.height(12.dp))
                    }

                    if (onImportNow != null) {
                        OutlinedButton(
                            onClick = {
                                if (isInstallingDemo) return@OutlinedButton
                                onImportNow()
                                onSetupDone()
                            },
                            enabled = !isInstallingDemo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                        ) {
                            Text(stringResource(R.string.setup_import_now))
                        }
                    }

                    if (onDemoInstalled != null) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                if (isInstallingDemo) return@OutlinedButton
                                scope.launch {
                                    isInstallingDemo = true
                                    try {
                                        val result = installDemoLibrary(context)
                                        onDemoInstalled.invoke(result)
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
                        ) {
                            Text(stringResource(R.string.setup_install_demo))
                        }
                    }
                }
            }
        }
    }

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
                        pendingBadUri = null
                        StorageModePrefs.set(context, StorageModePrefs.Mode.SAF)
                        launchWorkspacePicker(preferMusicFolder = true)
                    }
                ) { Text(stringResource(R.string.setup_use_music_workspace)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBadFolderDialog = false
                        pendingBadUri = null
                        StorageModePrefs.set(context, StorageModePrefs.Mode.SAF)
                        launchWorkspacePicker(preferMusicFolder = false)
                    }
                ) { Text(stringResource(R.string.setup_choose_another_workspace)) }
            }
        )
    }
}

// ---------------- HELPERS (TOP-LEVEL) ----------------

private fun createWorkspacePickerIntent(preferMusicFolder: Boolean): Intent {
    return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )

        if (preferMusicFolder) {
            val initial = runCatching {
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Music"
                )
            }.getOrNull()

            if (initial != null) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
            }
        }
    }
}

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
