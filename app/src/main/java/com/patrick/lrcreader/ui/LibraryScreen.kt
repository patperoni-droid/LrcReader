package com.patrick.lrcreader.ui


import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.heightIn
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onPlayFromLibrary: (String) -> Unit
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

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }

    var indexAll by remember { mutableStateOf<List<LibraryIndexCache.CachedEntry>>(emptyList()) }
    val globalAudioEntries = remember(indexAll) {
        indexAll
            .filter { !it.isDirectory }
            .map {
                LibraryEntry(
                    uri = Uri.parse(it.uriString),
                    name = it.name,
                    isDirectory = false
                )
            }
    }

    var pendingMoveUri by remember { mutableStateOf<Uri?>(null) }

    // renommage
    var renameTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val bottomBarHeight = 56.dp

    // ✅ Recherche
    var searchQuery by remember { mutableStateOf("") }
    val filteredEntries = remember(searchQuery, entries, globalAudioEntries) {
        if (searchQuery.isBlank()) {
            entries
        } else {
            globalAudioEntries.filter { e ->
                e.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    suspend fun refreshIndexAndShowCurrent() {
        val root = BackupFolderPrefs.get(context) ?: return
        val newFull = withContext(Dispatchers.IO) { buildFullIndex(context, root) }

        LibraryIndexCache.save(context, newFull)
        indexAll = newFull

        val folderToShow = currentFolderUri ?: root
        entries = LibraryIndexCache.childrenOf(newFull, folderToShow).map { e ->
            LibraryEntry(
                uri = Uri.parse(e.uriString),
                name = e.name,
                isDirectory = e.isDirectory
            )
        }
    }

    suspend fun refreshCurrentFolderOnly() {
        val folderUri = currentFolderUri ?: BackupFolderPrefs.get(context) ?: return

        val folderDoc = DocumentFile.fromTreeUri(context, folderUri)
            ?: DocumentFile.fromSingleUri(context, folderUri)
            ?: return

        val all = folderDoc.listFiles()

        val folders = all
            .filter { it.isDirectory }
            .sortedBy { it.name?.lowercase() ?: "" }
            .map { LibraryEntry(it.uri, it.name ?: "Dossier", true) }

        val jsonFiles = all
            .filter { it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true }
            .sortedBy { it.name?.lowercase() ?: "" }
            .map { LibraryEntry(it.uri, it.name ?: "sauvegarde.json", false) }

        val mediaFiles = all
            .filter { it.isFile && isAudioOrVideo(it.name) }
            .sortedBy { it.name?.lowercase() ?: "" }
            .map { f -> LibraryEntry(f.uri, f.name ?: "media", false) }

        entries = folders + jsonFiles + mediaFiles
    }

    // --- Choix du dossier racine de la bibliothèque ---
    val pickRootFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    isLoading = true
                    try {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        } catch (_: Exception) {}

                        BackupFolderPrefs.save(context, uri)

                        currentFolderUri = uri
                        folderStack = emptyList()

                        val full = withContext(Dispatchers.IO) {
                            buildFullIndex(context, uri)
                        }
                        LibraryIndexCache.save(context, full)
                        indexAll = full

                        entries = LibraryIndexCache.childrenOf(full, uri).map { e ->
                            LibraryEntry(
                                uri = Uri.parse(e.uriString),
                                name = e.name,
                                isDirectory = e.isDirectory
                            )
                        }
                    } finally {
                        delay(150)
                        isLoading = false
                    }
                }
            }
        }
    )

    // --- Déplacer vers dossier ---
    val moveToFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { destUri ->
            val srcUri = pendingMoveUri
            if (destUri != null && srcUri != null) {
                scope.launch {
                    isLoading = true
                    try {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                destUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        } catch (_: Exception) {}

                        val ok = withContext(Dispatchers.IO) {
                            moveLibraryFile(context, srcUri, destUri)
                        }
                        if (ok) {
                            selectedSongs = selectedSongs - srcUri
                            refreshIndexAndShowCurrent()
                        }
                    } finally {
                        pendingMoveUri = null
                        delay(150)
                        isLoading = false
                    }
                }
            } else {
                pendingMoveUri = null
            }
        }
    )

    // ✅ Premier chargement
    LaunchedEffect(Unit) {
        val root = BackupFolderPrefs.get(context)
        currentFolderUri = root

        val cachedAll = LibraryIndexCache.load(context)
        if (root != null && !cachedAll.isNullOrEmpty()) {
            indexAll = cachedAll
            entries = LibraryIndexCache.childrenOf(cachedAll, root).map { e ->
                LibraryEntry(
                    uri = Uri.parse(e.uriString),
                    name = e.name,
                    isDirectory = e.isDirectory
                )
            }
        } else {
            entries = emptyList()
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
                            try {
                                val newStack = folderStack.dropLast(1)
                                val parentUri = newStack.lastOrNull() ?: BackupFolderPrefs.get(context)
                                currentFolderUri = parentUri

                                entries = parentUri?.let { uri ->
                                    LibraryIndexCache.childrenOf(indexAll, uri).map { e ->
                                        LibraryEntry(
                                            uri = Uri.parse(e.uriString),
                                            name = e.name,
                                            isDirectory = e.isDirectory
                                        )
                                    }
                                } ?: emptyList()

                                folderStack = newStack
                                selectedSongs = emptySet()
                            } finally {
                                delay(150)
                                isLoading = false
                            }
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Bibliothèque", color = titleColor, fontSize = 20.sp)
                    Text(
                        text = currentFolderUri?.let {
                            (DocumentFile.fromTreeUri(context, it)
                                ?: DocumentFile.fromSingleUri(context, it))?.name
                        } ?: "Aucun dossier sélectionné",
                        color = subtitleColor,
                        fontSize = 11.sp
                    )
                }

                IconButton(onClick = { actionsExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = Color.White)
                }

                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Choisir dossier Music") },
                        onClick = {
                            actionsExpanded = false
                            pickRootFolderLauncher.launch(null)
                        }
                    )
                    // ✅ RESCAN bibliothèque (rebuild index + réparer playlists)
                    DropdownMenuItem(
                        text = { Text("Rescan bibliothèque") },
                        onClick = {
                            actionsExpanded = false
                            scope.launch {
                                isLoading = true
                                try {
                                    val root = BackupFolderPrefs.get(context) ?: return@launch

                                    // rebuild index EN LOCAL (pas via state)
                                    val newFull = withContext(Dispatchers.IO) { buildFullIndex(context, root) }
                                    LibraryIndexCache.save(context, newFull)

                                    // ✅ mettre à jour l’UI
                                    indexAll = newFull
                                    val folderToShow = currentFolderUri ?: root
                                    entries = LibraryIndexCache.childrenOf(newFull, folderToShow).map { e ->
                                        LibraryEntry(
                                            uri = Uri.parse(e.uriString),
                                            name = e.name,
                                            isDirectory = e.isDirectory
                                        )
                                    }

                                    // ✅ réparer AVEC le bon index
                                    com.patrick.lrcreader.core.PlaylistRepair.repairDeadUrisFromIndex(
                                        context = context,
                                        indexAll = newFull
                                    )

                                } finally {
                                    delay(150)
                                    isLoading = false
                                }
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Oublier le dossier") },
                        onClick = {
                            actionsExpanded = false
                            clearPersistedUris(context)
                            BackupFolderPrefs.clear(context)
                            LibraryIndexCache.clear(context)

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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .heightIn(min = 44.dp),
                            placeholder = { Text("Rechercher…") },
                            singleLine = true
                        )

                        Spacer(Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                bottom = if (selectedSongs.isNotEmpty()) bottomBarHeight else 0.dp
                            )
                        ) {
                            items(filteredEntries, key = { it.uri.toString() }) { entry ->
                                if (entry.isDirectory) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .background(cardBg, RoundedCornerShape(10.dp))
                                            .border(1.dp, rowBorder, RoundedCornerShape(10.dp))
                                            .clickable {
                                                scope.launch {
                                                    isLoading = true
                                                    try {
                                                        currentFolderUri?.let { folderStack = folderStack + it }
                                                        currentFolderUri = entry.uri

                                                        entries = LibraryIndexCache.childrenOf(indexAll, entry.uri).map { e ->
                                                            LibraryEntry(
                                                                uri = Uri.parse(e.uriString),
                                                                name = e.name,
                                                                isDirectory = e.isDirectory
                                                            )
                                                        }

                                                        searchQuery = ""
                                                        selectedSongs = emptySet()
                                                    } finally {
                                                        delay(150)
                                                        isLoading = false
                                                    }
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
                                        Text(entry.name, color = Color.White, fontSize = 15.sp)
                                    }
                                } else {
                                    val uri = entry.uri
                                    val isSelected = selectedSongs.contains(uri)
                                    var menuOpen by remember { mutableStateOf(false) }

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
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(
                                                    if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) accent else Color.White.copy(alpha = 0.7f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .clickable {
                                                    selectedSongs =
                                                        if (isSelected) selectedSongs - uri
                                                        else selectedSongs + uri
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) Text("✕", color = accent, fontSize = 13.sp)
                                        }

                                        Spacer(Modifier.width(10.dp))

                                        Text(
                                            entry.name,
                                            color = Color.White,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    if (selectedSongs.isNotEmpty()) {
                                                        selectedSongs =
                                                            if (isSelected) selectedSongs - uri
                                                            else selectedSongs + uri
                                                    } else {
                                                        onPlayFromLibrary(uri.toString())
                                                    }
                                                }
                                        )

                                        Box {
                                            IconButton(onClick = { menuOpen = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                                            }
                                            DropdownMenu(
                                                expanded = menuOpen,
                                                onDismissRequest = { menuOpen = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Attribuer à une playlist", color = Color.White) },
                                                    onClick = {
                                                        menuOpen = false
                                                        selectedSongs = setOf(uri)
                                                        showAssignDialog = true
                                                    }
                                                )

                                                DropdownMenuItem(
                                                    text = { Text("Déplacer vers un dossier", color = Color.White) },
                                                    onClick = {
                                                        menuOpen = false
                                                        pendingMoveUri = uri
                                                        moveToFolderLauncher.launch(null)
                                                    }
                                                )

                                                DropdownMenuItem(
                                                    text = { Text("Renommer", color = Color.White) },
                                                    onClick = {
                                                        menuOpen = false
                                                        renameTarget = entry
                                                        renameText = entry.name
                                                    }
                                                )

                                                DropdownMenuItem(
                                                    text = { Text("Supprimer définitivement", color = Color(0xFFFF6464)) },
                                                    onClick = {
                                                        menuOpen = false
                                                        pendingDeleteUri = uri
                                                        showDeleteConfirmDialog = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

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
                                Text("Chargement…", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                            }
                        }
                    }

                    if (selectedSongs.isNotEmpty()) {
                        BottomAppBar(
                            containerColor = Color(0xFF1E1E1E),
                            contentColor = Color.White,
                            tonalElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .height(bottomBarHeight)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .size(28.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = selectedSongs.size.toString(), color = Color.White, fontSize = 13.sp)
                            }

                            Spacer(Modifier.weight(1f))

                            TextButton(onClick = { showAssignDialog = true }) {
                                Text("Attribuer", color = Color.White)
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { selectedSongs = emptySet() }) {
                                Text("Désélect.", color = Color(0xFFB0B0B0))
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
                    Text("Aucune playlist.\nVa dans l’onglet “Toutes” pour en créer.", color = Color.Gray)
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
                                        selectedSongs.forEach { u ->
                                            PlaylistRepository.assignSongToPlaylist(plName, u.toString())
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

    // ───── DIALOG SUPPRESSION ─────
    if (showDeleteConfirmDialog && pendingDeleteUri != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                pendingDeleteUri = null
            },
            title = { Text("Supprimer le fichier", color = Color.White) },
            text = { Text("Supprimer définitivement ce fichier ?", color = Color.White) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = pendingDeleteUri ?: return@TextButton
                        scope.launch {
                            isLoading = true
                            try {
                                val ok = withContext(Dispatchers.IO) { deleteLibraryFile(context, target) }
                                if (ok) {
                                    selectedSongs = selectedSongs - target
                                    refreshCurrentFolderOnly()
                                }
                            } finally {
                                isLoading = false
                                showDeleteConfirmDialog = false
                                pendingDeleteUri = null
                            }
                        }
                    }
                ) { Text("Supprimer", color = Color(0xFFFF6464)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        pendingDeleteUri = null
                    }
                ) { Text("Annuler", color = Color(0xFFB0BEC5)) }
            },
            containerColor = Color(0xFF222222)
        )
    }

    // ───── DIALOG RENOMMAGE ─────
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renommer", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nouveau nom", color = Color.LightGray) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isLoading,
                    onClick = {
                        focusManager.clearFocus(force = true)

                        val target = renameTarget ?: return@TextButton
                        val newBase = renameText.trim()
                        if (newBase.isEmpty()) {
                            renameTarget = null
                            return@TextButton
                        }

                        val folderUri = indexAll
                            .firstOrNull { it.uriString == target.uri.toString() }
                            ?.parentUriString
                            ?.let { Uri.parse(it) }
                            ?: currentFolderUri
                            ?: run {
                                android.util.Log.e("LibraryRename", "No parent folder found for uri=${target.uri}")
                                return@TextButton
                            }

                        renameTarget = null
                        isLoading = true

                        scope.launch {
                            try {
                                val oldName = target.name
                                val ext = oldName.substringAfterLast('.', "")
                                val newNameFinal =
                                    if (ext.isNotEmpty() && !newBase.contains(".")) "$newBase.$ext" else newBase

                                android.util.Log.d(
                                    "LibraryRename",
                                    "RENAME request: ${target.name} -> $newNameFinal folderUri=$folderUri"
                                )

                                // ✅ DEVICE-SAFE rename : on récupère directement le nouvel URI après rename
                                val newUriAfterRename: Uri? = withContext(Dispatchers.IO) {

                                    val parentDoc =
                                        DocumentFile.fromTreeUri(context, folderUri)
                                            ?: return@withContext null

                                    val srcName = target.name
                                    val fileDoc = parentDoc.findFile(srcName) ?: run {
                                        android.util.Log.e(
                                            "LibraryRename",
                                            "findFile failed. srcName=$srcName folderUri=$folderUri"
                                        )
                                        return@withContext null
                                    }

                                    val renamedOk = try {
                                        fileDoc.renameTo(newNameFinal)
                                    } catch (e: Exception) {
                                        android.util.Log.e(
                                            "LibraryRename",
                                            "renameTo failed: ${e.javaClass.simpleName}: ${e.message}"
                                        )
                                        false
                                    }

                                    if (!renamedOk) return@withContext null

                                    // 🔥 CRUCIAL : sur device, c’est la seule méthode fiable
                                    parentDoc.findFile(newNameFinal)?.uri
                                        ?: findUriByNameInFolder(context, folderUri, newNameFinal)
                                }

                                if (newUriAfterRename == null) {
                                    android.util.Log.e(
                                        "LibraryRename",
                                        "rename FAILED (newUriAfterRename=null) oldUri=${target.uri} newName=$newNameFinal"
                                    )
                                    return@launch
                                }

// 1) UI immédiate (nom)
                                entries = entries.map { e ->
                                    if (e.uri == target.uri) e.copy(name = newNameFinal) else e
                                }

                                indexAll = indexAll.map { ce ->
                                    if (ce.uriString == target.uri.toString()) ce.copy(name = newNameFinal) else ce
                                }
                                LibraryIndexCache.save(context, indexAll)

// 2) Supprime les renommages playlist qui masquent le vrai nom
                                PlaylistRepository.clearCustomTitleEverywhere(target.uri.toString())

// 3) DEVICE FIX : re-persist permission sur le nouveau document
                                try {
                                    context.contentResolver.takePersistableUriPermission(
                                        newUriAfterRename,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    )
                                } catch (_: Exception) {}

// 4) Si l’URI a changé → migration complète
                                if (newUriAfterRename != target.uri) {

                                    PlaylistRepository.clearCustomTitleEverywhere(newUriAfterRename.toString())

                                    entries = entries.map { e ->
                                        if (e.uri == target.uri) e.copy(uri = newUriAfterRename, name = newNameFinal) else e
                                    }

                                    indexAll = indexAll.map { ce ->
                                        if (ce.uriString == target.uri.toString()) {
                                            ce.copy(uriString = newUriAfterRename.toString(), name = newNameFinal)
                                        } else ce
                                    }
                                    LibraryIndexCache.save(context, indexAll)

                                    PlaylistRepository.replaceSongUriEverywhere(
                                        oldUri = target.uri.toString(),
                                        newUri = newUriAfterRename.toString()
                                    )

                                    if (selectedSongs.contains(target.uri)) {
                                        selectedSongs = (selectedSongs - target.uri) + newUriAfterRename
                                    }
                                }
                                // 1) UI immédiate (nom)
                                entries = entries.map { e ->
                                    if (e.uri == target.uri) e.copy(name = newNameFinal) else e
                                }

                                indexAll = indexAll.map { ce ->
                                    if (ce.uriString == target.uri.toString()) ce.copy(name = newNameFinal) else ce
                                }
                                LibraryIndexCache.save(context, indexAll)

                                // 2) IMPORTANT : supprimer tout customTitle qui masquerait le vrai nom
                                PlaylistRepository.clearCustomTitleEverywhere(target.uri.toString())

                                // 3) Retrouver la NOUVELLE URI après rename
                                val newUri = withContext(Dispatchers.IO) {
                                    findUriByNameInFolder(context, folderUri, newNameFinal)
                                }

                                // 4) DEVICE FIX : sécuriser permissions sur la nouvelle URI
                                if (newUri != null) {
                                    try {
                                        context.contentResolver.takePersistableUriPermission(
                                            newUri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                        )
                                    } catch (_: Exception) {
                                        // OK si déjà autorisé ou non persistable
                                    }
                                }

                                // 5) Si l'URI a changé → MIGRATION GLOBALE
                                if (newUri != null && newUri != target.uri) {

                                    // nettoyage customTitle aussi côté nouvelle URI
                                    PlaylistRepository.clearCustomTitleEverywhere(newUri.toString())

                                    entries = entries.map { e ->
                                        if (e.uri == target.uri) e.copy(uri = newUri, name = newNameFinal) else e
                                    }

                                    indexAll = indexAll.map { ce ->
                                        if (ce.uriString == target.uri.toString()) {
                                            ce.copy(uriString = newUri.toString(), name = newNameFinal)
                                        } else ce
                                    }
                                    LibraryIndexCache.save(context, indexAll)

                                    // 🔥 FIX LECTURE : playlists + états
                                    PlaylistRepository.replaceSongUriEverywhere(
                                        oldUri = target.uri.toString(),
                                        newUri = newUri.toString()
                                    )

                                    if (selectedSongs.contains(target.uri)) {
                                        selectedSongs = (selectedSongs - target.uri) + newUri
                                    }
                                }

                            } finally {
                                // 6) FIN PROPRE
                                isLoading = false
                            }
                        }
                    }
                ) {
                    Text("OK", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Annuler", color = Color(0xFFB0BEC5))
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

private fun isAudioOrVideo(name: String?): Boolean {
    val n = name?.lowercase() ?: return false
    return n.endsWith(".mp3") || n.endsWith(".wav") ||
            n.endsWith(".m4a") || n.endsWith(".aac") ||
            n.endsWith(".flac") || n.endsWith(".ogg") ||
            n.endsWith(".mp4") || n.endsWith(".mkv") ||
            n.endsWith(".webm") || n.endsWith(".mov")
}

private fun findUriByNameInFolder(
    context: android.content.Context,
    folderUri: Uri,
    fileName: String
): Uri? {
    val folderDoc = DocumentFile.fromTreeUri(context, folderUri)
        ?: DocumentFile.fromSingleUri(context, folderUri)
        ?: return null

    return folderDoc.listFiles()
        .firstOrNull { it.isFile && it.name == fileName }
        ?.uri
}