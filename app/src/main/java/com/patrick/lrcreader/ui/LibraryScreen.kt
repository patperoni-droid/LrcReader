package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.PlaylistRepository

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // dossier racine enregistré (celui qu’on choisit dans le menu)
    val initialFolder = remember { BackupFolderPrefs.get(context) }

    // dossier courant dans lequel on navigue
    var currentFolderUri by remember { mutableStateOf<Uri?>(initialFolder) }

    // pile de navigation pour revenir en arrière
    var folderStack by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // entrées (dossiers + fichiers audio) dans le dossier courant
    var entries by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    // sélection de fichiers audio
    var selectedSongs by remember { mutableStateOf<Set<Uri>>(emptySet()) }

    var showAssignDialog by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }

    // launcher pour choisir le dossier racine
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                BackupFolderPrefs.save(context, uri)

                currentFolderUri = uri
                folderStack = emptyList()
                entries = listEntriesInFolder(context, uri)
                selectedSongs = emptySet()
            }
        }
    )

    // au démarrage, si on avait déjà un dossier → on le charge
    LaunchedEffect(initialFolder) {
        if (initialFolder != null) {
            entries = listEntriesInFolder(context, initialFolder)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // ─── HEADER ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // bouton retour dossier parent SI on est dans un sous-dossier
            if (folderStack.isNotEmpty()) {
                IconButton(onClick = {
                    val newStack = folderStack.dropLast(1)
                    val parentUri = newStack.lastOrNull() ?: initialFolder
                    currentFolderUri = parentUri
                    entries = parentUri?.let { listEntriesInFolder(context, it) } ?: emptyList()
                    folderStack = newStack
                    selectedSongs = emptySet()
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }
            }

            Text(
                "Bibliothèque",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = { actionsExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Actions",
                    tint = Color.White
                )
            }

            DropdownMenu(
                expanded = actionsExpanded,
                onDismissRequest = { actionsExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Ajouter un dossier") },
                    onClick = {
                        actionsExpanded = false
                        pickFolderLauncher.launch(null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Oublier le dossier") },
                    onClick = {
                        actionsExpanded = false
                        clearPersistedUris(context)
                        BackupFolderPrefs.clear(context)
                        currentFolderUri = null
                        entries = emptyList()
                        selectedSongs = emptySet()
                        folderStack = emptyList()
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (currentFolderUri == null) {
            Text(
                "Aucun dossier pour l’instant.\nAjoute ton dossier Music → puis tes MP3/WAV.",
                color = Color.Gray
            )
        } else {
            // chemin actuel (facultatif)
            Text(
                text = "Dossier actuel : " +
                        (DocumentFile.fromTreeUri(context, currentFolderUri!!)?.name ?: "…"),
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.uri.toString() }) { file ->
                        // dossier
                        if (file.isDirectory) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // on entre dans le dossier
                                        val old = currentFolderUri
                                        if (old != null) {
                                            folderStack = folderStack + old
                                        }
                                        currentFolderUri = file.uri
                                        entries = listEntriesInFolder(context, file.uri)
                                        selectedSongs = emptySet()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color(0xFFE386FF),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = file.name ?: "Dossier",
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            // fichier audio
                            val uri = file.uri
                            val isSelected = selectedSongs.contains(uri)
                            val displayName = (file.name ?: "inconnu")
                                .replace(".mp3", "", true)
                                .replace(".wav", "", true)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            selectedSongs =
                                                if (isSelected) selectedSongs - uri
                                                else selectedSongs + uri
                                        },
                                        onLongClick = {
                                            selectedSongs = setOf(uri)
                                            showAssignDialog = true
                                        }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(
                                            if (isSelected) Color(0xFFE386FF) else Color.Transparent
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = Color(0xFFE386FF)
                                        )
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = displayName,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // barre de sélection en bas
                if (selectedSongs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color(0xFF1E1E1E)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedSongs.size} sélectionné(s)",
                            color = Color.White,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                        Row {
                            Text(
                                text = "Tout effacer",
                                color = Color(0xFFE386FF),
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .clickable { selectedSongs = emptySet() }
                            )
                            Text(
                                text = "Attribuer",
                                color = Color(0xFFE386FF),
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .clickable { showAssignDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // ─── dialogue d’attribution à une playlist ─────────────────────
    if (showAssignDialog) {
        val playlists = PlaylistRepository.getPlaylists()
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            title = { Text("Ajouter à une playlist", color = Color.White) },
            text = {
                if (playlists.isEmpty()) {
                    Text(
                        "Aucune playlist.\nVa dans l’onglet “Toutes” pour en créer.",
                        color = Color.Gray
                    )
                } else {
                    Column {
                        playlists.forEach { plName ->
                            Text(
                                text = plName,
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        selectedSongs.forEach { uri ->
                                            PlaylistRepository.assignSongToPlaylist(
                                                plName,
                                                uri.toString()
                                            )
                                        }
                                        showAssignDialog = false
                                        // on garde la sélection ou pas ? à toi de voir
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAssignDialog = false }) {
                    Text("Fermer", color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

/* ------------------ utils ------------------ */

/**
 * Liste à la fois :
 * - les dossiers
 * - les fichiers audio (.mp3, .wav)
 */
private fun listEntriesInFolder(context: Context, folderUri: Uri): List<DocumentFile> {
    val docFile = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
    val all = docFile.listFiles()

    val folders = all
        .filter { it.isDirectory }
        .sortedBy { it.name?.lowercase() ?: "" }

    val audioFiles = all
        .filter { file ->
            file.isFile && file.name?.let { name ->
                name.endsWith(".mp3", ignoreCase = true) ||
                        name.endsWith(".wav", ignoreCase = true)
            } == true
        }
        .sortedBy { it.name?.lowercase() ?: "" }

    return folders + audioFiles
}

private fun clearPersistedUris(context: Context) {
    val cr = context.contentResolver
    val list = cr.persistedUriPermissions
    list.forEach { perm ->
        try {
            cr.releasePersistableUriPermission(
                perm.uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}
    }
}