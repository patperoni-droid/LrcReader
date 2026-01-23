package com.patrick.lrcreader.ui.library

import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.DjFolderPrefs

@Composable
fun SetupInstallScreen(
    titleColor: Color,
    subtitleColor: Color,
    accent: Color,
    onSetupDone: () -> Unit,
    onImportNow: (() -> Unit)? = null,
    onImportLater: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showImportPrompt by remember { mutableStateOf(false) }

    val pickDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        // 1) On force Documents (stabilité)
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: ""
        val looksLikeDocuments =
            docId.contains(":Documents", ignoreCase = true) || docId.contains("/Documents", ignoreCase = true)

        if (!looksLikeDocuments) {
            android.util.Log.w("Setup", "Pas Documents: $docId")
            return@rememberLauncherForActivityResult
        }

        // 2) Permission persistée + prefs setup
        persistTreePermIfPossible(context, uri)
        BackupFolderPrefs.saveSetupTreeUri(context, uri)

        // 3) Create/find SPL_Music sous Documents
        val baseTree = DocumentFile.fromTreeUri(context, uri) ?: return@rememberLauncherForActivityResult

        val splRoot =
            baseTree.listFiles().firstOrNull { it.isDirectory && it.name.equals("SPL_Music", ignoreCase = true) }
                ?: baseTree.createDirectory("SPL_Music")

        if (splRoot == null || !splRoot.isDirectory) return@rememberLauncherForActivityResult

        // 4) Create/find sous-dossiers sans doublons
        fun ensureDirSmart(
            parent: DocumentFile,
            expectedName: String,
            aliases: List<String> = emptyList()
        ): DocumentFile? {
            fun norm(s: String) =
                s.trim().lowercase()
                    .replace(" ", "")
                    .replace(Regex("\\(\\d+\\)$"), "")

            val wanted = (listOf(expectedName) + aliases).map { norm(it) }

            parent.listFiles()
                .firstOrNull { it.isDirectory && wanted.contains(norm(it.name ?: "")) }
                ?.let { return it }

            return parent.createDirectory(expectedName)
        }

        ensureDirSmart(splRoot, "BackingTracks", aliases = listOf("BackingTrack"))
        val djDir = ensureDirSmart(splRoot, "DJ")

        // 5) Library root = SPL_Music (tree uri)
        BackupFolderPrefs.saveLibraryRootUri(context, splToTreeUri(splRoot.uri))

        // 6) DJ pref
        if (djDir != null) {
            DjFolderPrefs.save(context, splToTreeUri(djDir.uri))
        }

        // 7) Étape 2
        showImportPrompt = true
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
            Text("Installation", color = titleColor, fontSize = 26.sp)

            Spacer(Modifier.height(10.dp))

            Text(
                "SPL va créer et utiliser ce dossier :\nDocuments / SPL_Music",
                color = subtitleColor,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(26.dp))

            // Bouton SPL style : accent + noir
            Button(
                onClick = { pickDocumentsLauncher.launch(documentsInitialUri()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E1E1E),
                    contentColor = Color.White
                )
            ) {
                Text("Autoriser Documents", fontSize = 16.sp)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "Tu gardes le contrôle : SPL ne touche qu’à son dossier.",
                color = Color(0xFF6F7A80),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
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
                        .widthIn(max = 520.dp), // ✅ rendu pro tablette
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF0F0F0F),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Importer des musiques ?",
                            color = titleColor,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            "Tu peux importer maintenant, ou le faire plus tard depuis la bibliothèque.",
                            color = subtitleColor,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Bouton secondaire SPL : sombre + contour accent
                            OutlinedButton(
                                onClick = {
                                    showImportPrompt = false
                                    onImportLater()
                                    onSetupDone()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    width = 1.dp
                                )
                            ) {
                                Text("Plus tard")
                            }

                            // Bouton principal SPL : accent
                            Button(
                                onClick = {
                                    showImportPrompt = false
                                    onImportNow()
                                    onSetupDone()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Importer")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- HELPERS (TOP-LEVEL) ----------------

private fun splToTreeUri(docUri: Uri): Uri {
    val authority = docUri.authority ?: return docUri
    val docId = runCatching { DocumentsContract.getDocumentId(docUri) }.getOrNull() ?: return docUri
    return DocumentsContract.buildTreeDocumentUri(authority, docId)
}

private fun documentsInitialUri(): Uri =
    Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents")