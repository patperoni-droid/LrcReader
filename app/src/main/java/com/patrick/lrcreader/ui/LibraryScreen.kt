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

/* -----------------------------------------------------------
   Cache global pour la bibliothèque
   (reste vivant tant que l'appli tourne)
   ----------------------------------------------------------- */
private object LibraryFolderCache {
    // clé = uri.toString()
    private val cache = mutableMapOf<String, List<LibraryEntry>>()

    fun get(uri: Uri): List<LibraryEntry>? = cache[uri.toString()]

    fun put(uri: Uri, list: List<LibraryEntry>) {
        cache[uri.toString()] = list
    }

    fun clear() = cache.clear()
}

/* ce qu’on affiche dans la liste */
private data class LibraryEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // dossier racine enregistré
    val initialFolder = remember { BackupFolderPrefs.get(context) }

    var currentFolderUri by remember { mutableStateOf<Uri?>(initialFolder) }
    var folderStack by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var entries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var selectedSongs by remember { mutableStateOf<Set<Uri>>(emptySet()) }

    var showAssignDialog by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }

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

                // on lit une fois et on met en cache global
                val fresh = listEntriesInFolder(context, uri)
                entries = fresh
                LibraryFolderCache.put(uri, fresh)

                selectedSongs = emptySet()
            }
        }
    )

    // premier chargement
    LaunchedEffect(initialFolder) {
        if (initialFolder != null) {
            val cached = LibraryFolderCache.get(initialFolder)
            if (cached != null) {
                entries = cached
            } else {
                val fresh = listEntriesInFolder(context, initialFolder)
                entries = fresh
                LibraryFolderCache.put(initialFolder, fresh)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (folderStack.isNotEmpty()) {
                IconButton(onClick = {
                    val newStack = folderStack.dropLast(1)
                    val parentUri = newStack.lastOrNull() ?: initialFolder
                    currentFolderUri = parentUri

                    entries = parentUri?.let { uri ->
                        LibraryFolderCache.get(uri)
                            ?: listEntriesInFolder(context, uri).also { LibraryFolderCache.put(uri, it) }
                    } ?: emptyList()

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
                        LibraryFolderCache.clear()
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
                    items(entries, key = { it.uri.toString() }) { entry ->
                        if (entry.isDirectory) {
                            // dossier
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val old = currentFolderUri
                                        if (old != null) folderStack = folderStack + old
                                        currentFolderUri = entry.uri

                                        val cached = LibraryFolderCache.get(entry.uri)
                                        if (cached != null) {
                                            entries = cached
                                        } else {
                                            val fresh = listEntriesInFolder(context, entry.uri)
                                            entries = fresh
                                            LibraryFolderCache.put(entry.uri, fresh)
                                        }
                                        selectedSongs = emptySet()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = entry.name,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            // fichier audio
                            val uri = entry.uri
                            val isSelected = selectedSongs.contains(uri)

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
                                // carré + croix centrée
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(
                                            if (isSelected) Color.White.copy(alpha = 0.22f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.8f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Text(
                                            text = "✕",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier.offset(y = (-6).dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = entry.name,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // barre de sélection
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
                                color = Color.White,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .clickable { selectedSongs = emptySet() }
                            )
                            Text(
                                text = "Attribuer",
                                color = Color.White,
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

    // DIALOG ATTRIBUTION
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

private fun listEntriesInFolder(context: Context, folderUri: Uri): List<LibraryEntry> {
    val docFile = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
    val all = docFile.listFiles()

    val folders = all
        .filter { it.isDirectory }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map { LibraryEntry(it.uri, it.name ?: "Dossier", true) }

    val audioFiles = all
        .filter { file ->
            file.isFile && file.name?.let { name ->
                name.endsWith(".mp3", ignoreCase = true) ||
                        name.endsWith(".wav", ignoreCase = true)
            } == true
        }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map {
            val cleanName = (it.name ?: "inconnu")
                .replace(".mp3", "", true)
                .replace(".wav", "", true)
            LibraryEntry(it.uri, cleanName, false)
        }

    return folders + audioFiles
}

private fun clearPersistedUris(context: Context) {
    val cr = context.contentResolver
    cr.persistedUriPermissions.forEach { perm ->
        try {
            cr.releasePersistableUriPermission(
                perm.uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}
    }
}