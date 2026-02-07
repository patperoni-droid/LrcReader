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
import com.patrick.lrcreader.core.InternalStoragePaths
import com.patrick.lrcreader.core.StorageModePrefs
import java.io.File

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onPlayFromLibrary: (String) -> Unit
) {
    val context = LocalContext.current
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

    val initialFolder = remember { BackupFolderPrefs.getLibraryRootUri(context) }
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

    // ✅ Injection DJ : visible mais désactivé
    // ✅ Injection DJ : visible mais désactivé
    fun buildInternalIndex(rootDir: File): List<LibraryIndexCache.CachedEntry> {
        val out = mutableListOf<LibraryIndexCache.CachedEntry>()

        fun walk(dir: File, parentUri: String?) {
            val children = dir.listFiles()?.toList().orEmpty()
            children.forEach { f ->
                val uriStr = Uri.fromFile(f).toString()
                out += LibraryIndexCache.CachedEntry(
                    uriString = uriStr,
                    name = f.name,
                    isDirectory = f.isDirectory,
                    parentUriString = parentUri
                )
                if (f.isDirectory) walk(f, uriStr)
            }
        }

        // root lui-même (optionnel mais pratique)
        val rootUriStr = Uri.fromFile(rootDir).toString()
        out += LibraryIndexCache.CachedEntry(
            uriString = rootUriStr,
            name = rootDir.name.ifBlank { "SPL_Music" },
            isDirectory = true,
            parentUriString = null
        )

        walk(rootDir, rootUriStr)

        return out
    }
    fun buildEntriesForFolder(folderUri: Uri): List<LibraryEntry> {

        // ✅ MODE INTERNE : listing via File()
        if (folderUri.scheme == "file") {
            val dir = File(folderUri.path ?: return emptyList())
            val children = dir.listFiles()?.toList().orEmpty()

            val items = children.map { f ->
                LibraryEntry(
                    uri = Uri.fromFile(f),
                    name = f.name,
                    isDirectory = f.isDirectory
                )
            }

            val withDjDisabled = items.map { e ->
                if (e.isDirectory && e.name.equals("DJ", ignoreCase = true)) {
                    e.copy(disabled = true, disabledReason = sDjExcludedReason)
                } else e
            }

            return withDjDisabled.sortedWith(
                compareByDescending<LibraryEntry> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
        }

        // ✅ MODE SAF : index + fallback DocumentFile
        val fromIndex = LibraryIndexCache.childrenOf(indexAll, folderUri).map { e ->
            LibraryEntry(
                uri = Uri.parse(e.uriString),
                name = e.name,
                isDirectory = e.isDirectory
            )
        }.toMutableList()

        if (fromIndex.isEmpty()) {
            val folderDoc =
                DocumentFile.fromTreeUri(context, folderUri)
                    ?: DocumentFile.fromSingleUri(context, folderUri)

            val real = folderDoc?.listFiles().orEmpty().mapNotNull { f ->
                val n = f.name ?: return@mapNotNull null
                LibraryEntry(
                    uri = f.uri,
                    name = n,
                    isDirectory = f.isDirectory
                )
            }
            fromIndex.addAll(real)
        }

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
                        disabledReason = sDjExcludedReason
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

            scope.launch {
                startLoading(sScanning, determinate = false)
                try {
                    persistTreePermIfPossible(context, uri)

                    BackupFolderPrefs.saveSetupTreeUri(context, uri)

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

                    val splTreeUri = toTreeUri(splRoot.uri)
                    BackupFolderPrefs.saveLibraryRootUri(context, splTreeUri)

                    currentFolderUri = splTreeUri
                    folderStack = emptyList()

                    if (djDir != null) {
                        DjFolderPrefs.save(context, toTreeUri(djDir.uri))
                    }

                    libraryRescanAll(
                        context = context,
                        root = splTreeUri,
                        folderToShow = splTreeUri,
                        onIndexAll = { indexAll = it },
                        onEntries = { entries = it }
                    )

                    entries = buildEntriesForFolder(splTreeUri)

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

    val importAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { pickedUris ->
        if (pickedUris.isNullOrEmpty()) return@rememberLauncherForActivityResult

        scope.launch {
            startLoading(sImporting, determinate = false)
            try {
                val rootUri = BackupFolderPrefs.getLibraryRootUri(context) ?: return@launch

                // ✅ MODE INTERNE : on copie dans /files/SPL_Music/BackingTracks
                // ✅ MODE INTERNE : on copie dans /files/SPL_Music/BackingTracks/Audio
                if (rootUri.scheme == "file") {
                    val rootDir = File(rootUri.path ?: return@launch)

                    val backingRoot = File(rootDir, "BackingTracks").apply { mkdirs() }
                    val audioDir = File(backingRoot, "Audio").apply { mkdirs() }  // ✅ le vrai dossier audio

                    pickedUris.forEach { src ->
                        val name = runCatching {
                            DocumentFile.fromSingleUri(context, src)?.name
                        }.getOrNull() ?: ("import_" + System.currentTimeMillis() + ".mp3")

                        val dest = File(audioDir, name)

                        runCatching {
                            context.contentResolver.openInputStream(src)?.use { input ->
                                dest.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }.onFailure { e ->
                            Log.e("IMPORT_INTERNAL", "Erreur copie $src -> $dest", e)
                        }
                    }

                    // refresh UI
                    val audioFolderUri = Uri.fromFile(audioDir)
                    currentFolderUri = audioFolderUri
                    entries = buildEntriesForFolder(audioFolderUri)
                    return@launch
                }

                // ✅ MODE SAF : comportement actuel
                val splRoot = rootUri
                val baseTree = BackupFolderPrefs.getSetupTreeUri(context) ?: splRoot

                val rawDest = importTargetFolderUri ?: currentFolderUri ?: splRoot
                val destDoc =
                    DocumentFile.fromTreeUri(context, rawDest) ?: DocumentFile.fromSingleUri(context, rawDest)
                val destFolder = if (destDoc != null && destDoc.isDirectory) rawDest else splRoot

                persistTreePermIfPossible(context, destFolder)

                ImportAudioManager.importAudioFiles(
                    context = context,
                    appRootTreeUri = baseTree,
                    sourceUris = pickedUris,
                    destFolderName = "BackingTracks",
                    overwriteIfExists = false,
                    destFolderUri = destFolder
                )

                libraryRescanAll(
                    context = context,
                    root = splRoot,
                    folderToShow = destFolder,
                    onIndexAll = { indexAll = it },
                    onEntries = { entries = it }
                )

                currentFolderUri = destFolder
                entries = buildEntriesForFolder(destFolder)

            } finally {
                importTargetFolderUri = null
                stopLoadingNice()
            }
        }
    }

    // ---------- initial load ----------
    // ---------- initial load ----------
    LaunchedEffect(Unit) {
        var root = BackupFolderPrefs.getLibraryRootUri(context)
        Log.e("ROOT_DEBUG", "StorageMode=" + StorageModePrefs.get(context))
        Log.e("ROOT_DEBUG", "LibraryRoot=" + BackupFolderPrefs.getLibraryRootUri(context))
        Log.e("ROOT_DEBUG", "SetupTree=" + BackupFolderPrefs.getSetupTreeUri(context))
        // ✅ Mode B : si INTERNAL, on force root sur /files/SPL_Music
        if (StorageModePrefs.get(context) == StorageModePrefs.Mode.INTERNAL) {
            val internalRoot = InternalStoragePaths.ensureSplRoot(context)
            root = Uri.fromFile(internalRoot)
            BackupFolderPrefs.saveLibraryRootUri(context, root)
        }

        // ✅ Mode interne : pas de SAF, pas d'index SAF
        if (root != null && root.scheme == "file") {
            currentFolderUri = root

            val rootDir = File(root.path ?: return@LaunchedEffect)
            val newIndex = buildInternalIndex(rootDir)
            LibraryIndexCache.save(context, newIndex)
            indexAll = newIndex

            entries = buildEntriesForFolder(root)
            return@LaunchedEffect
        }

        // ✅ Normalisation SAF uniquement
        if (root != null) {
            val fixed = normalizeToSplMusicDocUri(context, root)
            if (fixed != root) {
                BackupFolderPrefs.saveLibraryRootUri(context, fixed)
                root = fixed
            }
        }

        currentFolderUri = root

        indexAll = LibraryIndexCache.load(context) ?: emptyList()

        if (root != null && indexAll.isEmpty()) {
            startLoading(sScanning, determinate = false)
            try {
                libraryRescanAll(
                    context = context,
                    root = root,
                    folderToShow = root,
                    onIndexAll = { indexAll = it },
                    onEntries = { entries = it }
                )
            } finally {
                stopLoadingNice()
            }
        } else {
            entries = root?.let { buildEntriesForFolder(it) } ?: emptyList()
        }
    }

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
        else -> BackupFolderPrefs.getSetupTreeUri(context) != null
    }
    if (!isSetupDone) {
        DarkBlueGradientBackground {
            SetupInstallScreen(
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accent = accent,
                onSetupDone = {
                    val root = BackupFolderPrefs.getLibraryRootUri(context)
                    currentFolderUri = root
                    if (root != null) entries = buildEntriesForFolder(root)
                },
                onImportNow = {
                    importTargetFolderUri = BackupFolderPrefs.getLibraryRootUri(context)
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
                    scope.launch {
                        startLoading(sLoading, determinate = false)
                        try {
                            val newStack = folderStack.dropLast(1)
                            val parentUri = newStack.lastOrNull() ?: BackupFolderPrefs.getLibraryRootUri(context)
                            currentFolderUri = parentUri

                            entries = parentUri?.let { uri -> buildEntriesForFolder(uri) } ?: emptyList()

                            folderStack = newStack
                            selectedSongs = emptySet()
                        } finally {
                            stopLoadingNice()
                        }
                    }
                },

                onPickRoot = { pickRootFolderLauncher.launch(null) },

                onRescan = {

                    scope.launch {
                        val rootNow = BackupFolderPrefs.getLibraryRootUri(context)
                        if (rootNow != null && rootNow.scheme == "file") {
                            startLoading(sScanning, determinate = false)
                            try {
                                val rootDir = File(rootNow.path ?: return@launch)
                                val newIndex = buildInternalIndex(rootDir)

                                LibraryIndexCache.save(context, newIndex)
                                indexAll = newIndex

                                val folder = currentFolderUri ?: rootNow
                                entries = buildEntriesForFolder(folder)

                                Log.d("RESCAN_INTERNAL", "Index rebuilt: ${newIndex.size} entries")
                            } finally {
                                stopLoadingNice()
                            }
                            return@launch
                        }
                        startLoading(sScanning, determinate = false)
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
                            stopLoadingNice()
                        }
                    }
                },

                onForget = {
                    stopQuickPlay()

                    clearPersistedUris(context)
                    BackupFolderPrefs.clear(context)
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

                                val root = BackupFolderPrefs.getLibraryRootUri(context)
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
                                                val okAudio = libraryDeleteFile(context, targetAudio)
                                                val okLrc = libraryDeleteFile(context, targetLrc)

                                                if (okAudio) selectedSongs = selectedSongs - targetAudio
                                                if (okLrc) selectedSongs = selectedSongs - targetLrc

                                                val folderUri =
                                                    currentFolderUri ?: BackupFolderPrefs.getLibraryRootUri(context)
                                                if (folderUri != null) {
                                                    libraryRefreshCurrentFolderOnly(context, folderUri) { entries = it }
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
                                            val ok = libraryDeleteFile(context, targetAudio)
                                            if (ok) {
                                                selectedSongs = selectedSongs - targetAudio
                                                val folderUri =
                                                    currentFolderUri ?: BackupFolderPrefs.getLibraryRootUri(context)
                                                if (folderUri != null) {
                                                    libraryRefreshCurrentFolderOnly(context, folderUri) { entries = it }
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
                                androidx.compose.material3.Text("Supprimer audio uniquement")
                            }
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

                            val newUriAfterRename = libraryRenameFileDeviceSafe(
                                context = context,
                                folderUri = folderUri,
                                oldUri = target.uri,
                                oldName = oldName,
                                newNameFinal = newNameFinal
                            ) ?: return@launch

                            indexAll = indexAll.map { ce ->
                                if (ce.uriString == target.uri.toString()) ce.copy(name = newNameFinal) else ce
                            }
                            LibraryIndexCache.save(context, indexAll)

                            PlaylistRepository.clearCustomTitleEverywhere(target.uri.toString())
                            persistTreePermIfPossible(context, newUriAfterRename)

                            if (newUriAfterRename != target.uri) {
                                PlaylistRepository.clearCustomTitleEverywhere(newUriAfterRename.toString())

                                entries = entries.map { e ->
                                    if (e.uri == target.uri) e.copy(uri = newUriAfterRename, name = newNameFinal)
                                    else e
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
                        startLoading(sMoving, determinate = true)
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