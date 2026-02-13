package com.patrick.lrcreader.ui.library

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.BackupFolderPrefsInternal
import com.patrick.lrcreader.core.BackupFolderPrefsSaf
import com.patrick.lrcreader.core.DjFolderPrefs
import com.patrick.lrcreader.core.ImportAudioManager
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.LibraryEntry
import com.patrick.lrcreader.ui.LibraryFolderCache
import com.patrick.lrcreader.ui.clearPersistedUris
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.patrick.lrcreader.core.StorageModePrefs
import java.io.File

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onPlayFromLibrary: (String) -> Unit
) {
    val context = LocalContext.current
    Log.e("SIG_LIB", "SIG#0 TOP composable 2026-02-08 18:00 Z")
    val focusManager = LocalFocusManager.current

    // Palette analogique commune
    val titleColor = Color(0xFFFFF8E1)
    val subtitleColor = Color(0xFFB0BEC5)
    val cardBg = Color(0xFF181818)
    val rowBorder = Color(0x33FFFFFF)
    val accent = Color(0xFFFFC107)

    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Strings (pré-calculés, utilisables partout, y compris dans les callbacks)
    val sScanning = stringResource(R.string.library_scanning)
    val sMoving = stringResource(R.string.library_moving)
    val sImporting = stringResource(R.string.library_importing_music)
    val sDeleting = stringResource(R.string.library_deleting)
    val sRenaming = stringResource(R.string.library_renaming)
    val sLoading = stringResource(R.string.common_loading)
    val sSearch = stringResource(R.string.common_search_placeholder)
    val sNoFolderHint = stringResource(R.string.library_no_folder_hint)
    val sNoFolderSelected = stringResource(R.string.library_no_folder_selected)
    val sDjExcludedReason = stringResource(R.string.library_dj_excluded_reason)

    // State
    var showLrcEditor by remember { mutableStateOf(false) }
    var lrcEditorUri by remember { mutableStateOf<Uri?>(null) }
    var lrcEditorName by remember { mutableStateOf("") }
    var lrcEditorText by remember { mutableStateOf("") }

    val storageMode = StorageModePrefs.get(context)

    val backend: LibraryBackend = remember(storageMode) {
        when (storageMode) {
            StorageModePrefs.Mode.INTERNAL -> LibraryBackendInternal(context)
            StorageModePrefs.Mode.SAF -> LibraryBackendSaf(context)
            else -> LibraryBackendSaf(context) // sécurité si un jour il y a une 3e valeur / null / migration
        }
    }

    Log.e("SIG_LIB", "BOOT storageMode=$storageMode backend=${backend.javaClass.simpleName}")

    val initialFolder = remember(storageMode) { backend.getRootUri() }
    var currentFolderUri by remember { mutableStateOf<Uri?>(initialFolder) }
    var folderStack by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var entries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var selectedSongs by remember { mutableStateOf<Set<Uri>>(emptySet()) }

    var isLoading by remember { mutableStateOf(false) }
    var loadingStartedAt by remember { mutableStateOf(0L) }
    var moveProgress by remember { mutableStateOf<Float?>(null) }
    var moveLabel by remember { mutableStateOf<String?>(null) }

    var indexAll by remember { mutableStateOf<List<LibraryIndexCache.CachedEntry>>(emptyList()) }
    var importTargetFolderUri by remember { mutableStateOf<Uri?>(null) }

    fun buildEntriesForFolder(folderUri: Uri, useCache: Boolean = true): List<LibraryEntry> {
        if (useCache) {
            LibraryFolderCache.get(folderUri)?.let { return it }
        }
        val fresh = backend.listFolder(
            folderUri = folderUri,
            indexAll = indexAll,
            djExcludedReason = sDjExcludedReason
        )
        LibraryFolderCache.put(folderUri, fresh)
        return fresh
    }

    suspend fun runGlobalScan(root: Uri, folderToShow: Uri) {
        LibraryFolderCache.clear()
        backend.scanAll(
            root = root,
            folderToShow = folderToShow,
            onIndexAll = { indexAll = it },
            onEntries = {
                entries = it
                LibraryFolderCache.put(folderToShow, it)
            }
        )
    }

    // dialogs state
    var showAssignDialog by remember { mutableStateOf(false) }

    // ✅ delete with optional LRC
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDeleteLrcUri by remember { mutableStateOf<Uri?>(null) }

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

    // ✅ QUICK PLAY (sans ouvrir le lecteur)
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

    // --------------------------------------------
    // Helper: retrouve le .lrc associé à un audio
    // (même dossier + même nom de base + .lrc)
    // --------------------------------------------
    fun findAssociatedLrcUri(audioUri: Uri): Uri? {
        val audio = indexAll.firstOrNull { it.uriString == audioUri.toString() } ?: return null
        if (audio.isDirectory) return null

        val parent = audio.parentUriString ?: return null
        val base = audio.name.substringBeforeLast('.', audio.name)
        val wantedName = "$base.lrc"

        val lrc = indexAll.firstOrNull {
            !it.isDirectory &&
                    it.parentUriString == parent &&
                    it.name.equals(wantedName, ignoreCase = true)
        } ?: return null

        return Uri.parse(lrc.uriString)
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
            if (uri == null) return@rememberLauncherForActivityResult
// ✅ Si l'utilisateur passe par le picker SAF, on est forcément en mode SAF.
            StorageModePrefs.set(context, StorageModePrefs.Mode.SAF)
            scope.launch {
                startLoading(sScanning, determinate = false)
                try {
                    persistTreePermIfPossible(context, uri)

                    BackupFolderPrefs.saveSetupTreeUri(context, uri)
                    BackupFolderPrefsSaf.saveSetupTreeUri(context, uri)

                    val baseTree = DocumentFile.fromTreeUri(context, uri) ?: return@launch

                    val splRoot =
                        baseTree.listFiles().firstOrNull {
                            it.isDirectory && it.name.equals("SPL_Music", ignoreCase = true)
                        } ?: baseTree.createDirectory("SPL_Music")

                    if (splRoot == null || !splRoot.isDirectory) return@launch

                    fun ensureDirSmart(
                        context: android.content.Context,
                        parent: DocumentFile,
                        expectedName: String,
                        aliases: List<String> = emptyList()
                    ): DocumentFile? {

                        fun norm(s: String): String =
                            s.trim().lowercase().replace(" ", "").replace(Regex("\\(\\d+\\)$"), "")

                        val wanted = (listOf(expectedName) + aliases).map { norm(it) }

                        val parentUri = parent.uri
                        val parentDocId =
                            runCatching { DocumentsContract.getDocumentId(parentUri) }.getOrNull()
                                ?: return parent.findFile(expectedName) ?: parent.createDirectory(expectedName)

                        val childrenUri =
                            DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentDocId)

                        val cr = context.contentResolver
                        cr.query(
                            childrenUri,
                            arrayOf(
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                DocumentsContract.Document.COLUMN_MIME_TYPE
                            ),
                            null,
                            null,
                            null
                        )?.use { c ->
                            val idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            val nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                            val mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                            while (c.moveToNext()) {
                                val mime = c.getString(mimeCol) ?: ""
                                if (mime != DocumentsContract.Document.MIME_TYPE_DIR) continue

                                val name = c.getString(nameCol) ?: continue
                                val n = norm(name)

                                if (wanted.any { w -> n == w || n.startsWith(w) }) {
                                    val childDocId = c.getString(idCol)
                                    val childUri =
                                        DocumentsContract.buildDocumentUriUsingTree(parentUri, childDocId)
                                    return DocumentFile.fromSingleUri(context, childUri)
                                }
                            }
                        }

                        return parent.createDirectory(expectedName)
                    }

                    ensureDirSmart(
                        context = context,
                        parent = splRoot,
                        expectedName = "BackingTracks",
                        aliases = listOf("BackingTrack")
                    )

                    val djDir = ensureDirSmart(
                        context = context,
                        parent = splRoot,
                        expectedName = "DJ"
                    )
// ✅ IMPORTANT : on garde l'URI "document" renvoyée par DocumentFile.
// Les treeUri "fabriqués" peuvent donner exists=false / listFiles=0.
                    // --- SPL_Music (docUri) ---
                    val splDocUri: Uri = splRoot.uri

// Permission persistée sur le tree choisi (uri du picker)
                    persistTreePermIfPossible(context, uri)
                    BackupFolderPrefs.saveLibraryRootUri(context, splDocUri)
                    BackupFolderPrefsSaf.saveLibraryRootUri(context, splDocUri)

                    currentFolderUri = splDocUri
                    folderStack = emptyList()

                    if (djDir != null) {
                        DjFolderPrefs.save(context, djDir.uri)
                    }

// --- rescan ---
                    runGlobalScan(
                        root = splDocUri,
                        folderToShow = splDocUri
                    )

                } finally {
                    stopLoadingNice()
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
                    startLoading(sMoving, determinate = true)
                    try {
                        persistTreePermIfPossible(context, destUri)

                        val result = backend.move(
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

                        val root = backend.getRootUri()
                        val refreshFolder = currentFolderUri ?: destUri
                        if (root != null) runGlobalScan(root = root, folderToShow = refreshFolder)
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

    val importAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { pickedUris ->
        if (pickedUris.isNullOrEmpty()) return@rememberLauncherForActivityResult

        scope.launch {
            startLoading(sImporting, determinate = false)
            try {
                val rootUri = backend.getRootUri() ?: return@launch
                val folderToShow = backend.importAudio(
                    pickedUris = pickedUris,
                    destFolderUri = importTargetFolderUri,
                    currentFolderUri = currentFolderUri
                ) ?: return@launch

                runGlobalScan(
                    root = rootUri,
                    folderToShow = folderToShow
                )

                currentFolderUri = folderToShow
                entries = buildEntriesForFolder(folderToShow)

            } finally {
                importTargetFolderUri = null
                stopLoadingNice()
            }
        }
    }

    // ---------- initial load ----------
    Log.e("SIG_LIB", "SIG#1 JUST BEFORE LaunchedEffect 2026-02-08 18:00 Z")
    LaunchedEffect(Unit) {
        Log.e("SIG_LIB", "SIG#2 ENTER LaunchedEffect(Unit)")
        try {
            backend.ensureBaseFolders()
            val root = backend.getRootUri()

            if (root == null) {
                currentFolderUri = null
                entries = emptyList()
                return@LaunchedEffect
            }

            indexAll = backend.loadIndex()
            val folderToShow = backend.chooseInitialFolder(root, indexAll)
            currentFolderUri = folderToShow

            if (indexAll.isEmpty()) {
                startLoading(sScanning, determinate = false)
                try {
                    runGlobalScan(
                        root = root,
                        folderToShow = folderToShow
                    )
                } finally {
                    stopLoadingNice()
                }
            } else {
                entries = buildEntriesForFolder(folderToShow)
            }
        } catch (t: Throwable) {
            Log.e("SIG_LIB", "LaunchedEffect CRASH", t)
        }
    }

// ⚠️ IMPORTANT : on supprime la normalisation DocumentsContract "string-based"
// car elle peut fabriquer un treeUri non exploitable selon le provider.



    // ---------- UI ----------
    val currentFolderName = currentFolderUri?.let { u ->
        if (u.scheme == "file") {
            java.io.File(u.path ?: "").name.ifBlank { "SPL_Music" }
        } else {
            val doc = DocumentFile.fromTreeUri(context, u) ?: DocumentFile.fromSingleUri(context, u)
            doc?.name ?: "SPL_Music"
        }
    } ?: sNoFolderSelected
    // ✅ DEBUG SAF : voir ce que l'appli croit être le dossier courant
    LaunchedEffect(currentFolderUri) {
        val u = currentFolderUri ?: return@LaunchedEffect
        Log.d("LIB_SAF", "currentFolderUri=$u")
        Log.d("LIB_SAF", "savedRootUri=${BackupFolderPrefs.getLibraryRootUri(context)}")

        if (u.scheme == "file") {
            val f = File(u.path ?: "")
            Log.d("LIB_FILE", "exists=${f.exists()} isDir=${f.isDirectory} name=${f.name} children=${f.listFiles()?.size}")
            return@LaunchedEffect
        }

        val treeDoc = DocumentFile.fromTreeUri(context, u)
        val singleDoc = DocumentFile.fromSingleUri(context, u)

        Log.d("LIB_SAF", "fromTreeUri exists=${treeDoc?.exists()} isDir=${treeDoc?.isDirectory} name=${treeDoc?.name}")
        Log.d("LIB_SAF", "fromSingleUri exists=${singleDoc?.exists()} isDir=${singleDoc?.isDirectory} name=${singleDoc?.name}")

        val listTree = runCatching { treeDoc?.listFiles()?.size }.getOrNull()
        val listSingle = runCatching { singleDoc?.listFiles()?.size }.getOrNull()
        Log.d("LIB_SAF", "listFiles(tree)=$listTree  listFiles(single)=$listSingle")
    }
    val isSetupDone = when (StorageModePrefs.get(context)) {
        StorageModePrefs.Mode.INTERNAL -> true
        else -> {
            // ✅ SAF : on considère le setup OK dès que la permission du "setup tree" est valide.
            // Le libraryRootUri (SPL_Music) peut être une URI différente et ne pas matcher persistedUriPermissions à l'identique,
            // ce qui créait une boucle.
            BackupFolderPrefs.hasValidSetupTreePermission(context)
        }
    }
    if (!isSetupDone) {
        DarkBlueGradientBackground {
            SetupInstallScreen(
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accent = accent,
                onSetupDone = {
                    val root = backend.getRootUri()
                    currentFolderUri = root
                    if (root != null) entries = buildEntriesForFolder(root)
                },
                onImportNow = {
                    importTargetFolderUri = backend.getRootUri()
                    importAudioLauncher.launch(arrayOf("audio/*"))
                },
                onImportLater = { }
            )
        }
        return
    }

    DarkBlueGradientBackground {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

            LibraryHeader(
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                currentFolderUri = currentFolderUri,
                canGoBack = folderStack.isNotEmpty(),

                onBack = {
                    val newStack = folderStack.dropLast(1)
                    val parentUri = newStack.lastOrNull() ?: backend.getRootUri()
                    currentFolderUri = parentUri
                    entries = parentUri?.let { uri -> buildEntriesForFolder(uri) } ?: emptyList()
                    folderStack = newStack
                    selectedSongs = emptySet()
                },

                onPickRoot = { pickRootFolderLauncher.launch(null) },

                onRescan = {

                    scope.launch {
                        val rootNow = backend.getRootUri() ?: return@launch
                        startLoading(sScanning, determinate = false)
                        try {
                            val folderToShow = currentFolderUri ?: rootNow
                            runGlobalScan(
                                root = rootNow,
                                folderToShow = folderToShow
                            )

                            currentFolderUri?.let { folder ->
                                entries = buildEntriesForFolder(folder)
                            }
                        } finally {
                            stopLoadingNice()
                        }
                    }
                },

                onForget = {
                    stopQuickPlay()

                    clearPersistedUris(context)
                    BackupFolderPrefs.clear(context)
                    BackupFolderPrefsInternal.clear(context)
                    BackupFolderPrefsSaf.clear(context)
                    LibraryIndexCache.clear(context)
                    StorageModePrefs.set(context, StorageModePrefs.Mode.SAF)
                    currentFolderUri = null
                    entries = emptyList()
                    selectedSongs = emptySet()
                    folderStack = emptyList()
                    LibraryFolderCache.clear()
                },

                onImportBackingTracks = {
                    importTargetFolderUri = currentFolderUri
                    importAudioLauncher.launch(arrayOf("audio/*"))
                }
            )

            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {

                if (currentFolderUri == null) {
                    Text(
                        text = sNoFolderHint,
                        color = subtitleColor,
                        fontSize = 13.sp
                    )
                } else {

                    Column(modifier = Modifier.fillMaxSize()) {

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(0.85f).heightIn(min = 44.dp),
                            placeholder = { Text(sSearch) },
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
                                if (entry.disabled) return@LibraryList
                                currentFolderUri?.let { folderStack = folderStack + it }
                                currentFolderUri = entry.uri
                                entries = buildEntriesForFolder(entry.uri)
                                searchQuery = ""
                                selectedSongs = emptySet()
                            },

                            onOpenPlayer = { uri ->
                                stopQuickPlay()
                                onPlayFromLibrary(uri.toString())
                            },

                            onQuickPlay = { uri ->
                                quickPlayToggle(uri)
                            },

                            onImportBackupJson = { uri ->
                                Log.d("BackupImport", "Import demandé pour $uri")
                                // TODO: recâbler ton vrai import
                            },

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

                                val root = backend.getRootUri()
                                if (root == null) {
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
                                pendingDeleteLrcUri = findAssociatedLrcUri(uri)
                                showDeleteConfirmDialog = true
                            }
                        )
                    }

                    if (selectedSongs.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(androidx.compose.ui.Alignment.BottomCenter)
                                .zIndex(20f)
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
            // ✅ Nouveau dialog suppression : audio seul OU audio + .lrc
            if (showDeleteConfirmDialog && pendingDeleteUri != null) {
                val targetAudio = pendingDeleteUri!!
                val targetLrc = pendingDeleteLrcUri

                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        showDeleteConfirmDialog = false
                        pendingDeleteUri = null
                        pendingDeleteLrcUri = null
                    },
                    title = { androidx.compose.material3.Text("Supprimer le backing track") },
                    text = {
                        Column {
                            androidx.compose.material3.Text("Voulez-vous supprimer ce fichier audio ?")
                            if (targetLrc != null) {
                                Spacer(Modifier.height(8.dp))
                                androidx.compose.material3.Text(
                                    "Un fichier de paroles (.lrc) associé a été trouvé.\nSouhaitez-vous aussi le supprimer ?",
                                    color = Color.Gray
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Column {
                            // Bouton 1 : audio + lrc (si dispo)
                            if (targetLrc != null) {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        scope.launch {
                                            startLoading(sDeleting, determinate = false)
                                            try {
                                                val okAudio = backend.delete(targetAudio)
                                                val okLrc = backend.delete(targetLrc)

                                                if (okAudio) selectedSongs = selectedSongs - targetAudio
                                                if (okLrc) selectedSongs = selectedSongs - targetLrc

                                                val root = backend.getRootUri()
                                                val folderUri = currentFolderUri ?: root
                                                if (root != null && folderUri != null) {
                                                    runGlobalScan(root = root, folderToShow = folderUri)
                                                }
                                            } finally {
                                                showDeleteConfirmDialog = false
                                                pendingDeleteUri = null
                                                pendingDeleteLrcUri = null
                                                stopLoadingNice()
                                            }
                                        }
                                    }
                                ) {
                                    androidx.compose.material3.Text("Supprimer audio + .lrc")
                                }
                            }

                            // Bouton 2 : audio seul
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    scope.launch {
                                        startLoading(sDeleting, determinate = false)
                                        try {
                                            val ok = backend.delete(targetAudio)
                                            if (ok) {
                                                selectedSongs = selectedSongs - targetAudio
                                                val root = backend.getRootUri()
                                                val folderUri = currentFolderUri ?: root
                                                if (root != null && folderUri != null) {
                                                    runGlobalScan(root = root, folderToShow = folderUri)
                                                }
                                            }
                                        } finally {
                                            showDeleteConfirmDialog = false
                                            pendingDeleteUri = null
                                            pendingDeleteLrcUri = null
                                            stopLoadingNice()
                                        }
                                    }
                                }
                            ) {
                                androidx.compose.material3.Text("Voulez-vous supprimer ce fichier audio ?")
                            }

                            // ✅ Petit rappel (toujours affiché, même si pas de .lrc détecté)
                            Spacer(Modifier.height(6.dp))
                            androidx.compose.material3.Text(
                                text = "Astuce : si des paroles existent, pense à supprimer aussi le fichier dans le dossier \"Lyrics\".",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showDeleteConfirmDialog = false
                                pendingDeleteUri = null
                                pendingDeleteLrcUri = null
                            }
                        ) {
                            androidx.compose.material3.Text("Annuler")
                        }
                    }
                )
            }

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
                    startLoading(sRenaming, determinate = false)

                    scope.launch {
                        try {
                            val oldName = target.name
                            val ext = oldName.substringAfterLast('.', "")
                            val newNameFinal =
                                if (ext.isNotEmpty() && !newBase.contains(".")) "$newBase.$ext" else newBase

                            val newUriAfterRename = backend.rename(
                                folderUri = folderUri,
                                oldUri = target.uri,
                                oldName = oldName,
                                newNameFinal = newNameFinal
                            ) ?: return@launch

                            PlaylistRepository.clearCustomTitleEverywhere(target.uri.toString())
                            if (newUriAfterRename.scheme != "file") {
                                persistTreePermIfPossible(context, newUriAfterRename)
                            }

                            if (newUriAfterRename != target.uri) {
                                PlaylistRepository.clearCustomTitleEverywhere(newUriAfterRename.toString())

                                PlaylistRepository.replaceSongUriEverywhere(
                                    oldUri = target.uri.toString(),
                                    newUri = newUriAfterRename.toString()
                                )

                                if (selectedSongs.contains(target.uri)) {
                                    selectedSongs = (selectedSongs - target.uri) + newUriAfterRename
                                }
                            }

                            val root = backend.getRootUri()
                            if (root != null) {
                                runGlobalScan(root = root, folderToShow = folderUri)
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
                root = backend.getRootUri(),
                moveBrowserFolder = moveBrowserFolder,
                moveBrowserStack = moveBrowserStack,
                onGoUp = {
                    val root = backend.getRootUri()
                    val newStack = moveBrowserStack.dropLast(1)
                    val parent = newStack.lastOrNull() ?: root
                    moveBrowserStack = newStack
                    moveBrowserFolder = parent
                },
                onEnterFolder = { folderUri ->
                    val root = backend.getRootUri()
                    val from = moveBrowserFolder ?: root ?: folderUri
                    moveBrowserStack = moveBrowserStack + from
                    moveBrowserFolder = folderUri
                },
                onMoveHere = {
                    val rootTree = backend.getRootUri() ?: return@MoveBrowserDialog
                    val dest = moveBrowserFolder ?: rootTree
                    val src = pendingMoveUri ?: return@MoveBrowserDialog

                    showMoveBrowser = false

                    scope.launch {
                        startLoading(sMoving, determinate = true)
                        try {
                            val result = backend.move(
                                mainHandler = mainHandler,
                                srcUri = src,
                                destUri = dest,
                                indexAll = indexAll,
                                onProgress = { p, label -> moveProgress = p; moveLabel = label }
                            )

                            if (result.ok) {
                                runGlobalScan(
                                    root = rootTree,
                                    folderToShow = currentFolderUri ?: dest
                                )
                            }
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

            // ---------------- LRC EDITOR DIALOG ----------------
            if (showLrcEditor) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showLrcEditor = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
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
                                text = stringResource(R.string.library_lrc_editing_title, lrcEditorName),
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
                                ) { androidx.compose.material3.Text(stringResource(R.string.common_cancel)) }

                                Spacer(Modifier.width(8.dp))

                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        val uri = lrcEditorUri ?: return@TextButton
                                        val ok = writeTextToUri(uri, lrcEditorText)
                                        if (ok) showLrcEditor = false
                                        else Log.e("LRC", "Échec écriture sur $uri")
                                    }
                                ) { androidx.compose.material3.Text(stringResource(R.string.common_save)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------
// Helpers
// ------------------------------------------------------------
private fun toTreeUri(docUri: Uri): Uri {
    val authority = docUri.authority ?: return docUri
    val docId = runCatching { DocumentsContract.getDocumentId(docUri) }.getOrNull() ?: return docUri
    return DocumentsContract.buildTreeDocumentUri(authority, docId)
}

private fun normalizeToSplMusicDocUri(
    context: android.content.Context,
    anyTreeOrDocUri: Uri
): Uri {
    // ✅ Mode interne : ne jamais appeler DocumentsContract
    if (anyTreeOrDocUri.scheme == "file") return anyTreeOrDocUri

    val setupTree = BackupFolderPrefs.getSetupTreeUri(context) ?: return anyTreeOrDocUri

    val id = runCatching { DocumentsContract.getTreeDocumentId(anyTreeOrDocUri) }.getOrNull()
        ?: runCatching { DocumentsContract.getDocumentId(anyTreeOrDocUri) }.getOrNull()
        ?: return anyTreeOrDocUri

    val parts = id.split('/')
    val idx = parts.indexOfFirst { it.equals("SPL_Music", ignoreCase = true) }
    if (idx < 0) return anyTreeOrDocUri

    val splId = parts.take(idx + 1).joinToString("/")

    val authority = setupTree.authority ?: return anyTreeOrDocUri
    return DocumentsContract.buildTreeDocumentUri(authority, splId)
}
