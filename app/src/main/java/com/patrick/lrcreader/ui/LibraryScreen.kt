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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* -----------------------------------------------------------
   Cache global pour la bibliothèque
   ----------------------------------------------------------- */
private object LibraryFolderCache {
    private val cache = mutableMapOf<String, List<LibraryEntry>>()
    fun get(uri: Uri): List<LibraryEntry>? = cache[uri.toString()]
    fun put(uri: Uri, list: List<LibraryEntry>) { cache[uri.toString()] = list }
    fun clear() = cache.clear()
}

/* entrée affichée */
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
    val scope = rememberCoroutineScope()

    // palette analogique commune
    val titleColor = Color(0xFFFFF8E1)
    val subtitleColor = Color(0xFFB0BEC5)
    val cardBg = Color(0xFF181818)
    val rowBorder = Color(0x33FFFFFF)
    val accent = Color(0xFFFFC107)

    val initialFolder = remember { BackupFolderPrefs.get(context) }

    var currentFolderUri by remember { mutableStateOf<Uri?>(initialFolder) }
    var folderStack by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var entries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var selectedSongs by remember { mutableStateOf<Set<Uri>>(emptySet()) }

    var showAssignDialog by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val bottomBarHeight = 56.dp

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    isLoading = true
                    try {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        } catch (_: Exception) {}
                        BackupFolderPrefs.save(context, uri)

                        currentFolderUri = uri
                        folderStack = emptyList()

                        val fresh = withContext(Dispatchers.IO) {
                            listEntriesInFolder(context, uri)
                        }
                        entries = fresh
                        LibraryFolderCache.put(uri, fresh)
                        selectedSongs = emptySet()
                    } finally {
                        delay(200)
                        isLoading = false
                    }
                }
            }
        }
    )

    // premier chargement
    LaunchedEffect(initialFolder) {
        if (initialFolder != null) {
            isLoading = true
            val cached = LibraryFolderCache.get(initialFolder)
            if (cached != null) {
                entries = cached
                delay(200); isLoading = false
            } else {
                val fresh = withContext(Dispatchers.IO) {
                    listEntriesInFolder(context, initialFolder)
                }
                entries = fresh
                LibraryFolderCache.put(initialFolder, fresh)
                delay(200); isLoading = false
            }
        }
    }

    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // ───── HEADER ─────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (folderStack.isNotEmpty()) {
                    IconButton(onClick = {
                        scope.launch {
                            isLoading = true
                            val newStack = folderStack.dropLast(1)
                            val parentUri = newStack.lastOrNull() ?: initialFolder
                            currentFolderUri = parentUri
                            entries = parentUri?.let { uri ->
                                LibraryFolderCache.get(uri)
                                    ?: withContext(Dispatchers.IO) {
                                        listEntriesInFolder(context, uri)
                                    }.also { LibraryFolderCache.put(uri, it) }
                            } ?: emptyList()
                            folderStack = newStack
                            selectedSongs = emptySet()
                            delay(200); isLoading = false
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "Bibliothèque",
                        color = titleColor,
                        fontSize = 20.sp
                    )
                    Text(
                        text = currentFolderUri?.let {
                            DocumentFile.fromTreeUri(context, it)?.name ?: "Aucun dossier sélectionné"
                        } ?: "Aucun dossier sélectionné",
                        color = subtitleColor,
                        fontSize = 11.sp
                    )
                }

                IconButton(onClick = { actionsExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
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

            Spacer(Modifier.height(10.dp))

            if (currentFolderUri == null) {
                Text(
                    "Aucun dossier pour l’instant.\nChoisis ton dossier Music avec tes MP3 / WAV.",
                    color = subtitleColor
                )
            } else {
                Spacer(Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = if (selectedSongs.isNotEmpty()) bottomBarHeight else 0.dp
                        )
                    ) {
                        items(entries, key = { it.uri.toString() }) { entry ->
                            if (entry.isDirectory) {
                                // ─── Ligne dossier ───
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .background(cardBg, RoundedCornerShape(10.dp))
                                        .border(
                                            1.dp,
                                            rowBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            scope.launch {
                                                isLoading = true
                                                currentFolderUri?.let {
                                                    folderStack = folderStack + it
                                                }
                                                currentFolderUri = entry.uri
                                                val cached = LibraryFolderCache.get(entry.uri)
                                                entries =
                                                    if (cached != null) cached
                                                    else withContext(Dispatchers.IO) {
                                                        listEntriesInFolder(
                                                            context,
                                                            entry.uri
                                                        )
                                                    }.also {
                                                        LibraryFolderCache.put(entry.uri, it)
                                                    }
                                                selectedSongs = emptySet()
                                                delay(200); isLoading = false
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        entry.name,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                }
                            } else {
                                // ─── Ligne fichier audio / json ───
                                val uri = entry.uri
                                val isSelected = selectedSongs.contains(uri)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .background(cardBg, RoundedCornerShape(10.dp))
                                        .border(
                                            1.dp,
                                            if (isSelected) accent else rowBorder,
                                            RoundedCornerShape(10.dp)
                                        )
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
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(
                                                if (isSelected)
                                                    accent.copy(alpha = 0.18f)
                                                else
                                                    Color.Transparent,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) accent else Color.White.copy(alpha = 0.7f),
                                                RoundedCornerShape(4.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Text(
                                                "✕",
                                                color = accent,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        entry.name,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Spinner de chargement
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Chargement des fichiers audio…",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    // ——— BARRE FLOTTANTE (sans AnimatedVisibility) ———
                    if (selectedSongs.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            BottomAppBar(
                                containerColor = Color(0xFF1E1E1E),
                                contentColor = Color.White,
                                tonalElevation = 6.dp,
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .height(bottomBarHeight)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 16.dp)
                                        .size(28.dp)
                                        .border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.85f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = selectedSongs.size.toString(),
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(Modifier.weight(1f))

                                TextButton(onClick = { showAssignDialog = true }) {
                                    Text("Attribuer", color = Color.White)
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { selectedSongs = emptySet() }) {
                                    Text(
                                        "Désélect.",
                                        color = Color(0xFFB0B0B0)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ───── DIALOG ATTRIBUTION ─────
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
                                        selectedSongs = emptySet()
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

    val jsonFiles = all
        .filter { it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map { LibraryEntry(it.uri, it.name ?: "sauvegarde.json", false) }

    val audioFiles = all
        .filter { file ->
            file.isFile && file.name?.let { name ->
                name.endsWith(".mp3", true) || name.endsWith(".wav", true)
            } == true
        }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map {
            val cleanName = (it.name ?: "inconnu")
                .replace(".mp3", "", true)
                .replace(".wav", "", true)
            LibraryEntry(it.uri, cleanName, false)
        }

    return folders + jsonFiles + audioFiles
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