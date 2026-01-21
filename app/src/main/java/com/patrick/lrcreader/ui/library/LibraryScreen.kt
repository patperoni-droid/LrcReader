package com.patrick.lrcreader.ui.library



import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.ui.LibraryEntry
import com.patrick.lrcreader.ui.LibraryFolderCache
import com.patrick.lrcreader.ui.clearPersistedUris
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onPlayFromLibrary: (String) -> Unit
) {
// palette analogique commune
    val titleColor = Color(0xFFFFF8E1)
    val subtitleColor = Color(0xFFB0BEC5)
    val cardBg = Color(0xFF181818)
    val rowBorder = Color(0x33FFFFFF)
    val accent = Color(0xFFFFC107)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val focusManager = LocalFocusManager.current

    var showLrcEditor by remember { mutableStateOf(false) }
    var lrcEditorUri by remember { mutableStateOf<Uri?>(null) }
    var lrcEditorName by remember { mutableStateOf("") }
    var lrcEditorText by remember { mutableStateOf("") }
    val initialFolder = remember { BackupFolderPrefs.getLibraryRootUri(context) }

    var currentFolderUri by remember { mutableStateOf<Uri?>(initialFolder) }
    var folderStack by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var entries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var selectedSongs by remember { mutableStateOf<Set<Uri>>(emptySet()) }

    var actionsExpanded by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var loadingStartedAt by remember { mutableStateOf(0L) }

    var moveProgress by remember { mutableStateOf<Float?>(null) }
    var moveLabel by remember { mutableStateOf<String?>(null) }

    var indexAll by remember { mutableStateOf<List<LibraryIndexCache.CachedEntry>>(emptyList()) }
    // ------------------------------------------------------------
    // ✅ Injection DJ : visible mais désactivé
    // ------------------------------------------------------------
    fun buildEntriesForFolder(folderUri: Uri): List<LibraryEntry> {
        val fromIndex = LibraryIndexCache.childrenOf(indexAll, folderUri).map { e ->
            LibraryEntry(
                uri = Uri.parse(e.uriString),
                name = e.name,
                isDirectory = e.isDirectory
            )
        }.toMutableList()

        val folderDoc =
            DocumentFile.fromTreeUri(context, folderUri)
                ?: DocumentFile.fromSingleUri(context, folderUri)

        val djDoc = folderDoc?.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.equals("DJ", ignoreCase = true) }

        if (djDoc != null) {
            val already = fromIndex.any { it.isDirectory && it.name.equals("DJ", ignoreCase = true) }
            if (!already) {
                fromIndex.add(
                    LibraryEntry(
                        uri = djDoc.uri,
                        name = djDoc.name ?: "DJ",
                        isDirectory = true,
                        disabled = true,
                        disabledReason = "Exclu de la bibliothèque (utilisé en mode DJ)"
                    )
                )
            }
        }

        return fromIndex.sortedWith(
            compareByDescending<LibraryEntry> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }
// dialogs state
    var showAssignDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }

    var pendingMoveUri by remember { mutableStateOf<Uri?>(null) }
    var showMoveBrowser by remember { mutableStateOf(false) }
    var moveBrowserFolder by remember { mutableStateOf<Uri?>(null) }
    var moveBrowserStack by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var renameTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var renameText by remember { mutableStateOf("") }

// search
    var searchQuery by remember { mutableStateOf("") }
    val globalAudioEntries = remember(indexAll) {
        indexAll.filter { !it.isDirectory }.map {
            LibraryEntry(Uri.parse(it.uriString), it.name, false)
        }
    }
    val filteredEntries = remember(searchQuery, entries, globalAudioEntries) {
        if (searchQuery.isBlank()) entries
        else globalAudioEntries.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val bottomBarHeight = 56.dp

// --------------------------------------------------------------------
// ✅ QUICK PLAY (sans ouvrir le lecteur)
// --------------------------------------------------------------------
    val quickPlayer = remember { ExoPlayer.Builder(context).build() }
    var quickNowUri by remember { mutableStateOf<Uri?>(null) }
    var quickIsPlaying by remember { mutableStateOf(false) }

    DisposableEffect(quickPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                quickIsPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    quickIsPlaying = false
                }
            }
        }
        quickPlayer.addListener(listener)

        onDispose {
            quickPlayer.removeListener(listener)
            quickPlayer.release()
        }
    }

    fun quickPlayToggle(uri: Uri) {
        try {
            if (quickNowUri == null || quickNowUri != uri) {
                quickNowUri = uri
                quickPlayer.setMediaItem(MediaItem.fromUri(uri))
                quickPlayer.prepare()
                quickPlayer.playWhenReady = true
                return
            }
            if (quickPlayer.isPlaying) quickPlayer.pause() else quickPlayer.play()
        } catch (e: Exception) {
            Log.e("LibraryQuickPlay", "Erreur quick play", e)
        }
    }
    fun readTextFromUri(uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }.getOrNull()
    }

    fun writeTextToUri(uri: Uri, text: String): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(uri, "wt")
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { it.write(text) }
            true
        }.getOrElse { false }
    }
    fun stopQuickPlay() {
        try {
            if (quickPlayer.isPlaying) quickPlayer.pause()
        } catch (_: Exception) {
        }
        quickIsPlaying = false
    }
// --------------------------------------------------------------------
fun isPlayableMediaUri(uri: Uri): Boolean {
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    if (mime != null) return mime.startsWith("audio/") || mime.startsWith("video/")

    val name = runCatching {
        DocumentFile.fromSingleUri(context, uri)?.name
            ?: DocumentFile.fromTreeUri(context, uri)?.name
    }.getOrNull()?.lowercase()

    return name?.let {
        it.endsWith(".mp3") || it.endsWith(".wav") || it.endsWith(".m4a") || it.endsWith(".aac") ||
                it.endsWith(".flac") || it.endsWith(".ogg") ||
                it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".webm") || it.endsWith(".mov") || it.endsWith(".avi")
    } ?: false
}

    fun fileExtOf(uri: Uri): String {
        val name = runCatching {
            DocumentFile.fromSingleUri(context, uri)?.name
                ?: DocumentFile.fromTreeUri(context, uri)?.name
        }.getOrNull() ?: return ""
        return name.substringAfterLast('.', "").lowercase()
    }
    fun startLoading(label: String, determinate: Boolean) {
        loadingStartedAt = System.currentTimeMillis()
        isLoading = true
        moveLabel = label
        moveProgress = if (determinate) 0f else null
    }

    suspend fun stopLoadingNice() {
        val elapsed = System.currentTimeMillis() - loadingStartedAt
        val minMs = 500L
        if (elapsed < minMs) delay(minMs - elapsed)
        isLoading = false
        moveProgress = null
        moveLabel = null
    }

// ---------- SAF launchers ----------
    val pickRootFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    startLoading("Analyse de la bibliothèque…", determinate = false)
                    try {
                        persistTreePermIfPossible(context, uri)

                        BackupFolderPrefs.saveLibraryRootUri(context, uri)
                        currentFolderUri = uri
                        folderStack = emptyList()

                        libraryRescanAll(
                            context = context,
                            root = uri,
                            folderToShow = uri,
                            onIndexAll = { indexAll = it },
                            onEntries = { entries = it }
                        )
                        // ✅ réinjecte DJ grisé dans la vue courante
                        entries = buildEntriesForFolder(uri)
                    } finally {
                        stopLoadingNice()
                    }
                }
            }
        }
    )

    val moveToFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { destUri ->
            val srcUri = pendingMoveUri
            if (destUri != null && srcUri != null) {
                scope.launch {
                    startLoading("Déplacement…", determinate = true)
                    try {
                        persistTreePermIfPossible(context, destUri)

                        val result = libraryMoveOneFile(
                            context = context,
                            mainHandler = mainHandler,
                            srcUri = srcUri,
                            destUri = destUri,
                            indexAll = indexAll,
                            onProgress = { p, label ->
                                moveProgress = p
                                moveLabel = label
                            }
                        )

                        libraryLogMove(result)

                        libraryApplyMoveResult(
                            context = context,
                            src = srcUri,
                            dest = destUri,
                            result = result,
                            entries = entries,
                            indexAll = indexAll,
                            onEntries = { entries = it },
                            onIndexAll = { indexAll = it },
                            onProgress = { p, label -> moveProgress = p; moveLabel = label },
                            refreshFolderUri = currentFolderUri ?: destUri
                        )
                    } finally {
                        pendingMoveUri = null
                        stopLoadingNice()
                    }
                }
            } else {
                pendingMoveUri = null
            }
        }
    )

// ---------- initial load ----------
    LaunchedEffect(Unit) {
        currentFolderUri = BackupFolderPrefs.getLibraryRootUri(context)
        libraryLoadInitial(
            context = context,
            currentFolderUri = currentFolderUri,
            onIndexAll = { indexAll = it },
            onEntries = { entries = it }

        )
        // ✅ inject DJ désactivé si besoin
        currentFolderUri?.let { folder ->
            entries = buildEntriesForFolder(folder)
        }
    }
    // ✅ écoute les modifs d'index (LRC / midi / import / export) sans rescan
    LaunchedEffect(Unit) {
        com.patrick.lrcreader.core.LibraryIndexCache.updates.collect {
            val fresh = com.patrick.lrcreader.core.LibraryIndexCache.load(context).orEmpty()
            indexAll = fresh

            val folder =
                currentFolderUri ?: com.patrick.lrcreader.core.BackupFolderPrefs.getLibraryRootUri(context)

            if (folder != null) {
                entries = buildEntriesForFolder(folder)
            }
        }
    }
// 🔁 Auto-refresh de la bibliothèque quand un fichier (.lrc / midi / json) est créé

// ---------- UI ----------
    val currentFolderName = currentFolderUri?.let {
        (DocumentFile.fromTreeUri(context, it) ?: DocumentFile.fromSingleUri(context, it))?.name
    } ?: "Aucun dossier sélectionné"

    DarkBlueGradientBackground {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

            LibraryHeader(
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                currentFolderName = currentFolderName,
                canGoBack = folderStack.isNotEmpty(),
                onBack = {
                    scope.launch {
                        startLoading("Chargement…", determinate = false)
                        try {
                            val newStack = folderStack.dropLast(1)
                            val parentUri = newStack.lastOrNull() ?: BackupFolderPrefs.getLibraryRootUri(context)
                            currentFolderUri = parentUri

                            entries = parentUri?.let { uri ->
                                buildEntriesForFolder(uri)
                            } ?: emptyList()

                            folderStack = newStack
                            selectedSongs = emptySet()
                        } finally {
                            delay(150)
                            isLoading = false
                        }
                    }
                },
                actionsExpanded = actionsExpanded,
                onActionsExpanded = { actionsExpanded = it },
                onPickRoot = { pickRootFolderLauncher.launch(null) },
                onRescan = {
                    scope.launch {
                        startLoading("Analyse de la bibliothèque…", determinate = false)
                        try {
                            val root = BackupFolderPrefs.getLibraryRootUri(context) ?: return@launch
                            val folderToShow = currentFolderUri ?: root
                            libraryRescanAll(
                                context = context,
                                root = root,
                                folderToShow = folderToShow,
                                onIndexAll = { indexAll = it },
                                onEntries = { entries = it }
                            )
                            currentFolderUri?.let { folder ->
                                entries = buildEntriesForFolder(folder)
                            }
                        } finally {
                            delay(150)
                            isLoading = false
                        }
                    }
                },
                onForgetRoot = {
                    stopQuickPlay()

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

            Spacer(Modifier.height(10.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {

                if (currentFolderUri == null) {
                    Text(
                        "Aucun dossier pour l’instant.\nChoisis ton dossier Music avec tes MP3 / WAV.",
                        color = subtitleColor,
                        fontSize = 13.sp
                    )
                } else {

                    Column(modifier = Modifier.fillMaxSize()) {

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(0.85f).heightIn(min = 44.dp),
                            placeholder = { Text("Rechercher…") },
                            singleLine = true
                        )

                        Spacer(Modifier.height(8.dp))

                        LibraryList(
                            entries = filteredEntries,
                            cardBg = cardBg,
                            rowBorder = rowBorder,
                            accent = accent,
                            bottomPadding = if (selectedSongs.isNotEmpty()) bottomBarHeight else 0.dp,
                            selectedSongs = selectedSongs,

                            onToggleSelect = { uri ->
                                selectedSongs =
                                    if (selectedSongs.contains(uri)) selectedSongs - uri else selectedSongs + uri
                            },

                            onOpenFolder = { entry ->
                                // 🔒 DJ = visible mais non ouvrable
                                if (entry.disabled) return@LibraryList
                                scope.launch {
                                    isLoading = true
                                    moveProgress = null
                                    moveLabel = null
                                    try {
                                        currentFolderUri?.let { folderStack = folderStack + it }
                                        currentFolderUri = entry.uri

                                        entries = buildEntriesForFolder(entry.uri)
                                      

                                        searchQuery = ""
                                        selectedSongs = emptySet()
                                    } finally {
                                        delay(150)
                                        isLoading = false
                                    }
                                }
                            },

                            // 🔥 OUVERTURE DU LECTEUR AUDIO
                            onOpenPlayer = { uri ->
                                stopQuickPlay()
                                onPlayFromLibrary(uri.toString())
                            },

                            // ▶️ QUICK PLAY
                            onQuickPlay = { uri ->
                                quickPlayToggle(uri)
                            },

                            // 📦 IMPORT JSON
                            onImportBackupJson = { uri ->
                                Log.d("BackupImport", "Import demandé pour $uri")
                                // TODO: on recâble ton vrai import après
                            },
                            // ✏️ OUVRIR ÉDITEUR LRC  ← ← ← ICI LE NOUVEAU CALLBACK
                            onOpenLrcEditor = { lrcUri ->
                                stopQuickPlay()

                                lrcEditorUri = lrcUri
                                lrcEditorName = runCatching {
                                    DocumentFile.fromSingleUri(context, lrcUri)?.name
                                        ?: DocumentFile.fromTreeUri(context, lrcUri)?.name
                                        ?: "lyrics.lrc"
                                }.getOrNull() ?: "lyrics.lrc"

                                lrcEditorText = readTextFromUri(lrcUri) ?: ""
                                showLrcEditor = true
                            },

                            onAssignOne = { uri ->
                                selectedSongs = setOf(uri)
                                showAssignDialog = true
                            },
                            onMoveOne = { uri ->
                                pendingMoveUri = uri

                                val root = BackupFolderPrefs.getLibraryRootUri(context)
                                if (root == null) {
                                    // pas de dossier bibliothèque => pas de navigateur de déplacement
                                    showMoveBrowser = false
                                    return@LibraryList
                                }

                                moveBrowserFolder = root
                                moveBrowserStack = emptyList()
                                showMoveBrowser = true
                            },
                            onRenameOne = { entry ->
                                renameTarget = entry
                                renameText = entry.name
                            },
                            onDeleteOne = { uri ->
                                pendingDeleteUri = uri
                                showDeleteConfirmDialog = true
                            }
                        )
                    }

                    if (selectedSongs.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(androidx.compose.ui.Alignment.BottomCenter)
                                .zIndex(20f) // optionnel mais conseillé
                        ) {
                            LibraryBottomBar(
                                bottomBarHeight = bottomBarHeight,
                                selectedCount = selectedSongs.size,
                                onAssign = { showAssignDialog = true },
                                onClear = { selectedSongs = emptySet() }
                            )
                        }
                    }
                }

                LibraryLoadingOverlay(isLoading = isLoading, moveProgress = moveProgress, moveLabel = moveLabel)
            }

            // ---------- dialogs ----------
            AssignDialog(
                show = showAssignDialog,
                selectedSongs = selectedSongs,
                onDismiss = { showAssignDialog = false },
                onAssignedDone = {
                    showAssignDialog = false
                    selectedSongs = emptySet()
                }
            )

            DeleteConfirmDialog(
                show = showDeleteConfirmDialog,
                pendingDeleteUri = pendingDeleteUri,
                onCancel = {
                    showDeleteConfirmDialog = false
                    pendingDeleteUri = null
                },
                onConfirmDelete = { target ->
                    scope.launch {
                        startLoading("Suppression…", determinate = false)
                        try {
                            val ok = libraryDeleteFile(context, target)
                            if (ok) {
                                selectedSongs = selectedSongs - target
                                val folderUri = currentFolderUri ?:BackupFolderPrefs.getLibraryRootUri(context)
                                if (folderUri != null) {
                                    libraryRefreshCurrentFolderOnly(context, folderUri) { entries = it }
                                }
                            }
                        } finally {
                            showDeleteConfirmDialog = false
                            pendingDeleteUri = null
                            stopLoadingNice()
                        }
                    }
                }
            )

            RenameDialog(
                show = renameTarget != null,
                renameText = renameText,
                onRenameText = { renameText = it },
                onCancel = { renameTarget = null },
                enabled = !isLoading,
                onConfirm = {
                    focusManager.clearFocus(force = true)

                    val target = renameTarget ?: return@RenameDialog
                    val newBase = renameText.trim()
                    if (newBase.isEmpty()) {
                        renameTarget = null
                        return@RenameDialog
                    }

                    val folderUri = indexAll
                        .firstOrNull { it.uriString == target.uri.toString() }
                        ?.parentUriString
                        ?.let { Uri.parse(it) }
                        ?: currentFolderUri
                        ?: run {
                            Log.e("LibraryRename", "No parent folder found for uri=${target.uri}")
                            renameTarget = null
                            return@RenameDialog
                        }

                    renameTarget = null
                    startLoading("Renommage…", determinate = false)

                    scope.launch {
                        try {
                            val oldName = target.name
                            val ext = oldName.substringAfterLast('.', "")
                            val newNameFinal =
                                if (ext.isNotEmpty() && !newBase.contains(".")) "$newBase.$ext" else newBase

                            val newUriAfterRename = libraryRenameFileDeviceSafe(
                                context = context,
                                folderUri = folderUri,
                                oldUri = target.uri,
                                oldName = oldName,
                                newNameFinal = newNameFinal
                            )

                            if (newUriAfterRename == null) return@launch

                            indexAll = indexAll.map { ce ->
                                if (ce.uriString == target.uri.toString()) ce.copy(name = newNameFinal) else ce
                            }
                            LibraryIndexCache.save(context, indexAll)

                            PlaylistRepository.clearCustomTitleEverywhere(target.uri.toString())

                            persistTreePermIfPossible(context, newUriAfterRename)

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
                            } else {
                                entries = entries.map { e ->
                                    if (e.uri == target.uri) e.copy(name = newNameFinal) else e
                                }
                            }
                        } finally {
                            isLoading = false
                            moveProgress = null
                            moveLabel = null
                        }
                    }
                }
            )

            MoveBrowserDialog(
                show = showMoveBrowser && pendingMoveUri != null,
                indexAll = indexAll,
                root = BackupFolderPrefs.getLibraryRootUri(context),
                moveBrowserFolder = moveBrowserFolder,
                moveBrowserStack = moveBrowserStack,
                onGoUp = {
                    val root = BackupFolderPrefs.getLibraryRootUri(context)
                    val newStack = moveBrowserStack.dropLast(1)
                    val parent = newStack.lastOrNull() ?: root
                    moveBrowserStack = newStack
                    moveBrowserFolder = parent
                },
                onEnterFolder = { folderUri ->
                    val root = BackupFolderPrefs.getLibraryRootUri(context)
                    val from = moveBrowserFolder ?: root ?: folderUri
                    moveBrowserStack = moveBrowserStack + from
                    moveBrowserFolder = folderUri
                },
                onMoveHere = {
                    val rootTree = BackupFolderPrefs.getLibraryRootUri(context) ?: return@MoveBrowserDialog
                    val dest = moveBrowserFolder ?: rootTree
                    val src = pendingMoveUri ?: return@MoveBrowserDialog

                    showMoveBrowser = false

                    scope.launch {
                        startLoading("Déplacement…", determinate = true)
                        try {
                            val result = libraryMoveOneFile(
                                context = context,
                                mainHandler = mainHandler,
                                srcUri = src,
                                destUri = dest,
                                indexAll = indexAll,
                                onProgress = { p, label -> moveProgress = p; moveLabel = label }
                            )

                            libraryApplyMoveResult(
                                context = context,
                                src = src,
                                dest = dest,
                                result = result,
                                entries = entries,
                                indexAll = indexAll,
                                onEntries = { entries = it },
                                onIndexAll = { indexAll = it },
                                onProgress = { p, label -> moveProgress = p; moveLabel = label },
                                refreshFolderUri = currentFolderUri ?: dest
                            )
                        } finally {
                            pendingMoveUri = null
                            showMoveBrowser = false
                            stopLoadingNice()
                        }
                    }
                },
                onDismiss = {
                    showMoveBrowser = false
                    pendingMoveUri = null
                },
                onOtherFolder = {
                    showMoveBrowser = false
                    moveToFolderLauncher.launch(null)
                }
            )
            // ----------------------------

            // ---------------- LRC EDITOR DIALOG ----------------
            if (showLrcEditor) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showLrcEditor = false },
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false
                    )
                ) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.98f)
                            .fillMaxHeight(0.92f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            androidx.compose.material3.Text(
                                text = "Édition : $lrcEditorName",
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            androidx.compose.material3.OutlinedTextField(
                                value = lrcEditorText,
                                onValueChange = { lrcEditorText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                singleLine = false
                            )

                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                androidx.compose.material3.TextButton(
                                    onClick = { showLrcEditor = false }
                                ) { androidx.compose.material3.Text("Annuler") }

                                Spacer(Modifier.width(8.dp))

                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        val uri = lrcEditorUri ?: return@TextButton
                                        val ok = writeTextToUri(uri, lrcEditorText)
                                        if (ok) showLrcEditor = false
                                        else android.util.Log.e("LRC", "Échec écriture sur $uri")
                                    }
                                ) { androidx.compose.material3.Text("Enregistrer") }
                            }
                        }
                    }
                }
            }
        }
    }
}