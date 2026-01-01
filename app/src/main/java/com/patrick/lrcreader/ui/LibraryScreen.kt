package com.patrick.lrcreader.ui

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

    val bottomBarHeight = 56.dp

    // ✅ Recherche
    var searchQuery by remember { mutableStateOf("") }
    val filteredEntries = remember(searchQuery, entries, globalAudioEntries) {
        if (searchQuery.isBlank()) {
            // mode normal : navigation par dossier
            entries
        } else {
            // 🔎 mode recherche globale
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

    // --- Choix du dossier racine de la bibliothèque ---
    val pickRootFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    isLoading = true
                    try {
                        // 1) Permission persistante
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        } catch (_: Exception) {}

                        // 2) On mémorise le dossier racine
                        BackupFolderPrefs.save(context, uri)

                        // 3) On scanne ce dossier (une fois)
                        currentFolderUri = uri
                        folderStack = emptyList()

                        val full = withContext(Dispatchers.IO) {
                            buildFullIndex(context, uri) // scan récursif COMPLET
                        }
                        LibraryIndexCache.save(context, full)
                        indexAll = full

// afficher les enfants de Music immédiatement (sans re-scan)
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

    // --- Choix du dossier de destination pour "Déplacer vers un dossier" ---
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

    // ✅ Premier chargement : on recharge depuis le cache disque si présent
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
            // pas d'index => écran vide + l'utilisateur devra choisir Music
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
                            (DocumentFile.fromTreeUri(context, it) ?: DocumentFile.fromSingleUri(context, it))?.name

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

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        // 🔍 Champ de recherche
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .heightIn(min = 44.dp),   // ✅ clé du problème
                            placeholder = { Text("Rechercher…") },
                            singleLine = true
                        )

                        Spacer(Modifier.height(8.dp))

                        // 📂 Liste filtrée
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

                                                        // ✅ reset recherche quand on change de dossier (sinon tu crois que ça “marche pas”)
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
                                        Text(
                                            entry.name,
                                            color = Color.White,
                                            fontSize = 15.sp
                                        )
                                    }
                                } else {
                                    // ─── Fichier ───
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
                                        // Carré sélection
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
                                            if (isSelected) {
                                                Text("✕", color = accent, fontSize = 13.sp)
                                            }
                                        }

                                        Spacer(Modifier.width(10.dp))

                                        Text(
                                            entry.name,
                                            color = Color.White,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    // ✅ SÉCURITÉ SCÈNE :
                                                    // s’il y a AU MOINS un titre sélectionné → JAMAIS de lecture
                                                    if (selectedSongs.isNotEmpty()) {
                                                        selectedSongs =
                                                            if (isSelected) selectedSongs - uri
                                                            else selectedSongs + uri
                                                    } else {
                                                        onPlayFromLibrary(uri.toString())
                                                    }
                                                }
                                        )

                                        // Menu ⋮
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

                    // ✅ Spinner (overlay)
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
                                    "Chargement…",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    // ✅ Barre sélection multiple (overlay bas)
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
                            val ok = withContext(Dispatchers.IO) { deleteLibraryFile(context, target) }
                            if (ok) {
                                selectedSongs = selectedSongs - target
                                refreshIndexAndShowCurrent()
                            }

                            isLoading = false
                            showDeleteConfirmDialog = false
                            pendingDeleteUri = null
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
                    onClick = {
                        val target = renameTarget ?: return@TextButton
                        val newBase = renameText.trim()
                        val folderUri = currentFolderUri
                        if (newBase.isEmpty() || folderUri == null) {
                            renameTarget = null
                            return@TextButton
                        }

                        scope.launch {
                            isLoading = true
                            val ok = withContext(Dispatchers.IO) {
                                renameLibraryFile(
                                    context = context,
                                    folderUri = folderUri,
                                    fileUri = target.uri,
                                    newBaseName = newBase
                                )
                            }
                            if (ok) refreshIndexAndShowCurrent()


                            isLoading = false
                            renameTarget = null
                        }
                    }
                ) { Text("OK", color = Color.White) }
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
