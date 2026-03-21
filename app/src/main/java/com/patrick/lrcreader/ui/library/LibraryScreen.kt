package com.patrick.lrcreader.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
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
import androidx.core.content.FileProvider
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
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.core.getSmpSongId
import com.patrick.lrcreader.core.config.TitleAliasesStore
import com.patrick.lrcreader.core.search.SearchEngine
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.SmpConverter
import com.patrick.lrcreader.smp.SmpExporter
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.ui.LibraryEntry
import com.patrick.lrcreader.ui.LibraryFolderCache
import com.patrick.lrcreader.ui.clearPersistedUris
import com.patrick.lrcreader.ui.isHiddenLibraryTransportFile
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.patrick.lrcreader.core.StorageModePrefs
import java.io.File

private val PROMPTER_FOLDER_URI: Uri = Uri.parse("spl-prompter://folder")
private val SMP_FOLDER_URI: Uri = Uri.parse("spl-smp://folder")

private fun isPrompterFolderUri(uri: Uri?): Boolean = uri?.scheme == "spl-prompter"
private fun isSmpFolderUri(uri: Uri?): Boolean = uri?.scheme == "spl-smp"

private fun extractPrompterId(uri: Uri): String? {
    val raw = uri.toString()
    if (!raw.startsWith("prompter://")) return null
    return raw.removePrefix("prompter://").ifBlank { null }
}

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    smpRefreshVersion: Int = 0,
    lastImportedSmpSongId: String? = null,
    onImportExternalSmp: () -> Unit,
    onImportGeneratedSmp: suspend (Uri) -> com.patrick.lrcreader.smp.SongUnit?,
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
    val sConvertingSmp = stringResource(R.string.library_converting_smp)
    val sDeleting = stringResource(R.string.library_deleting)
    val sRenaming = stringResource(R.string.library_renaming)
    val sLoading = stringResource(R.string.common_loading)
    val sSearch = stringResource(R.string.common_search_placeholder)
    val sNoFolderHint = stringResource(R.string.library_no_folder_hint)
    val sNoFolderSelected = stringResource(R.string.library_no_folder_selected)
    val sDjExcludedReason = stringResource(R.string.library_dj_excluded_reason)
    val sDeleteBackingTrackTitle = stringResource(R.string.library_delete_backing_track_title)
    val sDeleteFileTitle = stringResource(R.string.library_delete_file_title)
    val sDeleteAudioOnly = stringResource(R.string.library_delete_audio_question)
    val sDeleteAudioPlusLrc = stringResource(R.string.library_delete_audio_plus_lrc)
    val sDeleteConfirmText = stringResource(R.string.library_delete_file_confirm_text)
    val sDeletePermanently = stringResource(R.string.library_list_delete_permanently)
    val sDeleteSmpTitle = stringResource(R.string.library_delete_smp_title)
    val sDeleteSmpConfirmText = stringResource(R.string.library_delete_smp_confirm_text)
    val sDeleteSmpFailed = stringResource(R.string.library_delete_smp_failed)
    val sShareSmpFailed = stringResource(R.string.library_share_smp_failed)
    val sPrompterFolder = stringResource(R.string.main_menu_prompter)
    val sSmpFolder = stringResource(R.string.library_smp_folder)
    val sConvertSmpSingleSuccess = stringResource(R.string.library_convert_smp_success_single)
    val sConvertSmpSingleFailed = stringResource(R.string.library_convert_smp_failed_single)
    val sConvertSmpNoMp3 = stringResource(R.string.library_convert_smp_no_mp3)

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
    val smpConverter = remember(context) { SmpConverter(context) }
    val smpLibraryScanner = remember(context) { SmpLibraryScanner(context) }
    var lastHandledImportedSmpRefresh by remember { mutableIntStateOf(-1) }

    fun buildPrompterEntries(): List<LibraryEntry> {
        return TextSongRepository.listAll(context).map { song ->
            LibraryEntry(
                uri = Uri.parse(song.uri),
                name = song.title.ifBlank { "Prompter" },
                isDirectory = false
            )
        }
    }

    fun buildSmpEntries(): List<LibraryEntry> {
        return smpLibraryScanner.listSongs()
            .map { song ->
                val uriString = buildSmpItem(song.id)
                val displayName = TitleAliasesStore.getTitleForTrack(context, uriString)
                    ?: PlaylistRepository.getAnyCustomTitleForUri(uriString)
                    ?: song.title.ifBlank { song.id }
                LibraryEntry(
                    uri = Uri.parse(uriString),
                    name = displayName,
                    isDirectory = false
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun decorateEntriesForFolder(folderUri: Uri, source: List<LibraryEntry>): List<LibraryEntry> {
        if (isPrompterFolderUri(folderUri)) {
            return buildPrompterEntries()
        }
        if (isSmpFolderUri(folderUri)) {
            return buildSmpEntries()
        }

        val root = backend.getRootUri()
        val isRootFolder = root != null && folderUri.toString() == root.toString()
        if (!isRootFolder) return source

        val extraEntries = mutableListOf<LibraryEntry>()
        val alreadyHasPrompter = source.any { it.isDirectory && isPrompterFolderUri(it.uri) }
        if (!alreadyHasPrompter) {
            extraEntries += LibraryEntry(PROMPTER_FOLDER_URI, sPrompterFolder, isDirectory = true)
        }

        val alreadyHasSmp = source.any { it.isDirectory && isSmpFolderUri(it.uri) }
        if (!alreadyHasSmp && buildSmpEntries().isNotEmpty()) {
            extraEntries += LibraryEntry(SMP_FOLDER_URI, sSmpFolder, isDirectory = true)
        }

        if (extraEntries.isEmpty()) return source

        return (source + extraEntries)
            .sortedWith(
                compareByDescending<LibraryEntry> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
    }

    fun buildEntriesForFolder(folderUri: Uri, useCache: Boolean = true): List<LibraryEntry> {
        if (useCache) {
            LibraryFolderCache.get(folderUri)?.let { return it }
        }
        val fresh = if (isPrompterFolderUri(folderUri) || isSmpFolderUri(folderUri)) {
            emptyList()
        } else {
            backend.listFolder(
                folderUri = folderUri,
                indexAll = indexAll,
                djExcludedReason = sDjExcludedReason
            )
        }
        val decorated = decorateEntriesForFolder(folderUri, fresh)
        LibraryFolderCache.put(folderUri, decorated)
        return decorated
    }

    suspend fun runGlobalScan(root: Uri, folderToShow: Uri) {
        LibraryFolderCache.clear()
        backend.scanAll(
            root = root,
            folderToShow = folderToShow,
            onIndexAll = { indexAll = it },
            onEntries = {
                val decorated = decorateEntriesForFolder(folderToShow, it)
                entries = decorated
                LibraryFolderCache.put(folderToShow, decorated)
            }
        )
    }

    suspend fun refreshLibraryFolder(folderUri: Uri?) {
        val root = backend.getRootUri() ?: return
        val folderToShow = folderUri ?: root
        runGlobalScan(
            root = root,
            folderToShow = folderToShow
        )
        currentFolderUri = folderToShow
        entries = buildEntriesForFolder(folderToShow, useCache = false)
    }

    LaunchedEffect(smpRefreshVersion, currentFolderUri) {
        val currentFolder = currentFolderUri ?: return@LaunchedEffect
        val rootFolder = backend.getRootUri()
        val shouldRefreshCurrentFolder =
            isSmpFolderUri(currentFolder) ||
                (rootFolder != null && currentFolder.toString() == rootFolder.toString())
        if (!shouldRefreshCurrentFolder) return@LaunchedEffect
        LibraryFolderCache.clear()
        entries = buildEntriesForFolder(currentFolder, useCache = false)
    }

    fun removePrompterFromAllPlaylists(uriString: String) {
        PlaylistRepository.getPlaylists().forEach { playlist ->
            PlaylistRepository.removeSongFromPlaylist(playlist, uriString)
        }
    }

    // dialogs state
    var showAssignDialog by remember { mutableStateOf(false) }

    // ✅ delete planifiée (audio + associés potentiels)
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeletePlan by remember { mutableStateOf<LibraryDeletePlan?>(null) }
    var deleteInProgress by remember { mutableStateOf(false) }
    var pendingDeleteSmpUri by remember { mutableStateOf<Uri?>(null) }
    var deleteSmpInProgress by remember { mutableStateOf(false) }

    var pendingMoveUri by remember { mutableStateOf<Uri?>(null) }
    var showMoveBrowser by remember { mutableStateOf(false) }
    var moveBrowserFolder by remember { mutableStateOf<Uri?>(null) }
    var moveBrowserStack by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var renameTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var editPrompterId by remember { mutableStateOf<String?>(null) }
    var editPrompterTitle by remember { mutableStateOf("") }
    var editPrompterContent by remember { mutableStateOf("") }
    var showEditPrompterDialog by remember { mutableStateOf(false) }

    // search
    var searchQuery by remember { mutableStateOf("") }
    val titleAliasVersion = TitleAliasesStore.version.intValue
    data class SearchableLibraryEntry(
        val entry: LibraryEntry,
        val indexedItem: SearchEngine.IndexedItem
    )
    val globalAudioEntries = remember(indexAll, titleAliasVersion) {
        indexAll.filter { !it.isDirectory && !isHiddenLibraryTransportFile(it.name) }.map {
            val alias = TitleAliasesStore.getTitleForTrack(context, it.uriString)
                ?: PlaylistRepository.getAnyCustomTitleForUri(it.uriString)
            SearchableLibraryEntry(
                entry = LibraryEntry(Uri.parse(it.uriString), it.name, false),
                indexedItem = SearchEngine.index(
                    id = it.uriString,
                    displayTitle = alias ?: it.name,
                    fallbackName = it.name
                )
            )
        }
    }
    val filteredEntries = remember(searchQuery, entries, globalAudioEntries, currentFolderUri) {
        val normalizedQuery = SearchEngine.normalize(searchQuery)
        if (normalizedQuery.isBlank()) {
            entries
        } else if (isPrompterFolderUri(currentFolderUri) || isSmpFolderUri(currentFolderUri)) {
            val indexed = entries
                .asSequence()
                .filter { !it.isDirectory }
                .map { entry ->
                    SearchableLibraryEntry(
                        entry = entry,
                        indexedItem = SearchEngine.index(
                            id = entry.uri.toString(),
                            displayTitle = entry.name,
                            fallbackName = entry.name
                        )
                    )
                }
                .toList()
            val filteredIds = SearchEngine.filter(
                items = indexed.map { it.indexedItem },
                query = searchQuery
            ).asSequence().map { it.id }.toSet()
            indexed
                .filter { it.indexedItem.id in filteredIds }
                .map { it.entry }
        } else {
            val filteredIds = SearchEngine.filter(
                items = globalAudioEntries.map { it.indexedItem },
                query = searchQuery
            ).asSequence().map { it.id }.toSet()
            globalAudioEntries
                .filter { it.indexedItem.id in filteredIds }
                .map { it.entry }
        }
    }
    LaunchedEffect(searchQuery, globalAudioEntries.size, filteredEntries.size, currentFolderUri) {
        if (BuildConfig.DEBUG) {
            val normalizedQuery = SearchEngine.normalize(searchQuery)
            val itemsBefore = when {
                normalizedQuery.isBlank() -> entries.size
                isPrompterFolderUri(currentFolderUri) || isSmpFolderUri(currentFolderUri) -> entries.size
                else -> globalAudioEntries.size
            }
            val itemsAfter = filteredEntries.size
            Log.d(
                "SEARCH_PROOF",
                "mode=LIBRARY query='$normalizedQuery' playlist=- itemsBefore=$itemsBefore itemsAfter=$itemsAfter"
            )
        }
    }

    LaunchedEffect(smpRefreshVersion, lastImportedSmpSongId) {
        val importedSongId = lastImportedSmpSongId?.trim().takeUnless { it.isNullOrEmpty() } ?: return@LaunchedEffect
        if (smpRefreshVersion == lastHandledImportedSmpRefresh) return@LaunchedEffect

        val importedUriString = buildSmpItem(importedSongId)
        val smpEntries = buildSmpEntries()
        val isImportedSongVisible = smpEntries.any { it.uri.toString() == importedUriString }
        if (!isImportedSongVisible) {
            return@LaunchedEffect
        }

        lastHandledImportedSmpRefresh = smpRefreshVersion
        currentFolderUri
            ?.takeUnless { isSmpFolderUri(it) }
            ?.let { currentFolder ->
                if (folderStack.lastOrNull()?.toString() != currentFolder.toString()) {
                    folderStack = folderStack + currentFolder
                }
            }

        currentFolderUri = SMP_FOLDER_URI
        LibraryFolderCache.clear()
        entries = buildEntriesForFolder(SMP_FOLDER_URI, useCache = false)
        searchQuery = ""
        selectedSongs = emptySet()
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
                if (isPlaying) {
                    val session = runCatching { quickPlayer.audioSessionId }.getOrDefault(0)
                    Log.d(
                        "METER",
                        "PLAY_START engine=Other.LibraryQuickPlayer sessionId=$session isPlaying=$isPlaying"
                    )
                }
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

    fun summarizeDeleteRoles(items: List<LibraryDeleteItem>): String {
        val hasLyrics = items.any { it.role == LibraryDeleteRole.LYRICS }
        val hasAccords = items.any { it.role == LibraryDeleteRole.ACCORDS }
        return when {
            hasLyrics && hasAccords -> "Lyrics + Accords"
            hasLyrics -> "Lyrics"
            hasAccords -> "Accords"
            else -> ".lrc"
        }
    }

    fun applyDeleteResult(result: LibraryDeleteResult) {
        result.results
            .filter { it.success }
            .forEach { itemResult ->
                selectedSongs = selectedSongs - itemResult.item.uri
            }

        if (!result.hasFailures) return

        val failed = result.results.count { !it.success }
        val total = result.results.size
        Toast.makeText(
            context,
            context.getString(R.string.library_delete_partial_failure, failed, total),
            Toast.LENGTH_LONG
        ).show()
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
            val backendInitialFolder = backend.chooseInitialFolder(root, indexAll)
            val folderToShow = root
            Log.i(
                "LIB_SCAN_DIAG",
                "mount root=$root indexSize=${indexAll.size} willFullScan=${indexAll.isEmpty()} backendInitialFolder=$backendInitialFolder folderToShow=$folderToShow"
            )
            currentFolderUri = folderToShow
            folderStack = emptyList()

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
        if (isPrompterFolderUri(u)) {
            sPrompterFolder
        } else if (isSmpFolderUri(u)) {
            sSmpFolder
        } else if (u.scheme == "file") {
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

        if (isPrompterFolderUri(u)) {
            Log.d("LIB_SAF", "virtualPrompterFolder=true (skip DocumentFile probes)")
            return@LaunchedEffect
        }

        if (isSmpFolderUri(u)) {
            Log.d("LIB_SAF", "virtualSmpFolder=true (skip DocumentFile probes)")
            return@LaunchedEffect
        }

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
                onImportLater = { },
                onDemoInstalled = { result ->
                    LibraryFolderCache.clear()
                    indexAll = backend.loadIndex()
                    val targetFolder = result.audioFolderUri ?: backend.getRootUri()
                    currentFolderUri = targetFolder
                    entries = targetFolder?.let { buildEntriesForFolder(it, useCache = false) }.orEmpty()
                }
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
                    val parentUri = folderStack.lastOrNull() ?: backend.getRootUri()
                    val newStack = folderStack.dropLast(1)
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
                            val folderToShow = currentFolderUri
                                ?.takeUnless { isPrompterFolderUri(it) || isSmpFolderUri(it) }
                                ?: rootNow
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
                    importTargetFolderUri = currentFolderUri?.takeUnless {
                        isPrompterFolderUri(it) || isSmpFolderUri(it)
                    }
                    importAudioLauncher.launch(arrayOf("audio/*"))
                },

                onConvertFolderToSmp = {
                    val folderUri = currentFolderUri?.takeUnless {
                        isPrompterFolderUri(it) || isSmpFolderUri(it)
                    } ?: return@LibraryHeader

                    scope.launch {
                        startLoading(sConvertingSmp, determinate = false)
                        try {
                            val results = withContext(Dispatchers.IO) {
                                smpConverter.convertFolder(folderUri)
                            }

                            if (results.isEmpty()) {
                                Toast.makeText(context, sConvertSmpNoMp3, Toast.LENGTH_SHORT).show()
                            } else {
                                var successCount = 0
                                var failureCount = 0
                                results.forEach { result ->
                                    val importedSong = result.getOrNull()?.let { outputUri ->
                                        onImportGeneratedSmp(outputUri)
                                    }
                                    if (importedSong != null) {
                                        successCount += 1
                                    } else {
                                        failureCount += 1
                                    }
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.library_convert_smp_folder_summary,
                                        successCount,
                                        failureCount
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } finally {
                            stopLoadingNice()
                        }
                    }
                },

                onImportSmp = {
                    onImportExternalSmp()
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

                            onConvertOneToSmp = { mp3Uri ->
                                scope.launch {
                                    startLoading(sConvertingSmp, determinate = false)
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            smpConverter.convertSingle(mp3Uri)
                                        }
                                        result.fold(
                                            onSuccess = { outputUri ->
                                                val importedSong = onImportGeneratedSmp(outputUri)
                                                Toast.makeText(
                                                    context,
                                                    if (importedSong != null) {
                                                        sConvertSmpSingleSuccess
                                                    } else {
                                                        sConvertSmpSingleFailed
                                                    },
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onFailure = {
                                                Toast.makeText(
                                                    context,
                                                    sConvertSmpSingleFailed,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        )
                                    } finally {
                                        stopLoadingNice()
                                    }
                                }
                            },

                            onAssignOne = { uri ->
                                selectedSongs = setOf(uri)
                                showAssignDialog = true
                            },

                            onShareOne = { uri ->
                                val songId = getSmpSongId(uri.toString())
                                if (songId == null) {
                                    Toast.makeText(context, sShareSmpFailed, Toast.LENGTH_SHORT).show()
                                    return@LibraryList
                                }

                                scope.launch {
                                    startLoading(sLoading, determinate = false)
                                    try {
                                        val shareUri = withContext(Dispatchers.IO) {
                                            val song = smpLibraryScanner.findSongById(songId) ?: return@withContext null
                                            val exportedFile = SmpExporter.exportSongUnitToCacheSmp(context, song)
                                                ?: return@withContext null
                                            FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                exportedFile
                                            )
                                        }

                                        if (shareUri == null) {
                                            Toast.makeText(context, sShareSmpFailed, Toast.LENGTH_SHORT).show()
                                        } else {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/octet-stream"
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(
                                                    shareIntent,
                                                    context.getString(R.string.backup_share)
                                                )
                                            )
                                        }
                                    } finally {
                                        stopLoadingNice()
                                    }
                                }
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
                                val prompterId = extractPrompterId(entry.uri)
                                if (prompterId != null) {
                                    val textSong = TextSongRepository.get(context, prompterId)
                                    if (textSong != null) {
                                        editPrompterId = prompterId
                                        editPrompterTitle = textSong.title
                                        editPrompterContent = textSong.content
                                        showEditPrompterDialog = true
                                    }
                                } else {
                                    renameTarget = entry
                                    renameText = TitleAliasesStore.getTitleForTrack(context, entry.uri.toString())
                                        ?: PlaylistRepository.getAnyCustomTitleForUri(entry.uri.toString())
                                        ?: entry.name
                                }
                            },

                            onDeleteOne = { uri ->
                                val prompterId = extractPrompterId(uri)
                                if (prompterId != null) {
                                    if (deletePrompterAndRemoveFromAllPlaylists(context, uri.toString())) {
                                        selectedSongs = selectedSongs - uri
                                        val folder = currentFolderUri
                                        if (folder != null) {
                                            entries = buildEntriesForFolder(folder, useCache = false)
                                        }
                                    }
                                } else {
                                    val smpSongId = getSmpSongId(uri.toString())
                                    if (smpSongId != null) {
                                        pendingDeleteSmpUri = uri
                                    } else {
                                        scope.launch {
                                            val plan = runCatching {
                                                backend.planDelete(
                                                    target = uri,
                                                    indexAll = indexAll
                                                )
                                            }.getOrElse {
                                                LibraryDeletePlan(
                                                    target = LibraryDeleteItem(
                                                        uri = uri,
                                                        role = LibraryDeleteRole.FILE,
                                                        displayName = uri.lastPathSegment ?: "file"
                                                    ),
                                                    associated = emptyList()
                                                )
                                            }
                                            pendingDeletePlan = plan
                                            showDeleteConfirmDialog = true
                                        }
                                    }
                                }
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
            // ✅ Nouveau dialog suppression : audio seul OU audio + fichiers associes
            if (showDeleteConfirmDialog && pendingDeletePlan != null) {
                val deletePlan = pendingDeletePlan!!
                val hasAssociated = deletePlan.isAudioTarget && deletePlan.hasAssociated

                suspend fun executeDeletion(includeAssociated: Boolean) {
                    if (deleteInProgress) return
                    deleteInProgress = true
                    showDeleteConfirmDialog = false
                    startLoading(sDeleting, determinate = false)
                    try {
                        val result = backend.deleteWithPlan(
                            plan = deletePlan,
                            includeAssociated = includeAssociated
                        )
                        applyDeleteResult(result)

                        val root = backend.getRootUri()
                        val folderUri = currentFolderUri ?: root
                        if (root != null && folderUri != null) {
                            runGlobalScan(root = root, folderToShow = folderUri)
                        }
                    } finally {
                        deleteInProgress = false
                        showDeleteConfirmDialog = false
                        pendingDeletePlan = null
                        stopLoadingNice()
                    }
                }

                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        if (deleteInProgress) return@AlertDialog
                        showDeleteConfirmDialog = false
                        pendingDeletePlan = null
                    },
                    title = {
                        androidx.compose.material3.Text(
                            if (deletePlan.isAudioTarget) sDeleteBackingTrackTitle else sDeleteFileTitle
                        )
                    },
                    confirmButton = {
                        Column {
                            if (hasAssociated) {
                                androidx.compose.material3.TextButton(
                                    enabled = !deleteInProgress,
                                    onClick = {
                                        if (deleteInProgress) return@TextButton
                                        scope.launch {
                                            executeDeletion(includeAssociated = true)
                                        }
                                    }
                                ) {
                                    androidx.compose.material3.Text(sDeleteAudioPlusLrc)
                                }
                            }

                            androidx.compose.material3.TextButton(
                                enabled = !deleteInProgress,
                                onClick = {
                                    if (deleteInProgress) return@TextButton
                                    scope.launch {
                                        executeDeletion(includeAssociated = false)
                                    }
                                }
                            ) {
                                androidx.compose.material3.Text(
                                    if (deletePlan.isAudioTarget) sDeleteAudioOnly else sDeletePermanently
                                )
                            }
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            enabled = !deleteInProgress,
                            onClick = {
                                if (deleteInProgress) return@TextButton
                                showDeleteConfirmDialog = false
                                pendingDeletePlan = null
                            }
                        ) {
                            androidx.compose.material3.Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }

            if (pendingDeleteSmpUri != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        if (deleteSmpInProgress) return@AlertDialog
                        pendingDeleteSmpUri = null
                    },
                    title = {
                        androidx.compose.material3.Text(sDeleteSmpTitle)
                    },
                    text = {
                        androidx.compose.material3.Text(sDeleteSmpConfirmText)
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            enabled = !deleteSmpInProgress,
                            onClick = {
                                if (deleteSmpInProgress) return@TextButton
                                val deleteUri = pendingDeleteSmpUri ?: return@TextButton
                                val songId = getSmpSongId(deleteUri.toString()) ?: return@TextButton
                                scope.launch {
                                    deleteSmpInProgress = true
                                    startLoading(sDeleting, determinate = false)
                                    try {
                                        val deleted = withContext(Dispatchers.IO) {
                                            val songDir = smpLibraryScanner.findSongById(songId)
                                                ?.storageFolder
                                                ?.takeIf { it.isNotBlank() }
                                                ?.let(::File)
                                                ?: File(context.filesDir, "tracks/$songId")
                                            !songDir.exists() || songDir.deleteRecursively()
                                        }
                                        if (deleted) {
                                            selectedSongs = selectedSongs - deleteUri
                                            LibraryFolderCache.clear()
                                            val folder = currentFolderUri
                                            if (folder != null) {
                                                entries = buildEntriesForFolder(folder, useCache = false)
                                            }
                                        } else {
                                            Toast.makeText(context, sDeleteSmpFailed, Toast.LENGTH_SHORT).show()
                                        }
                                    } finally {
                                        deleteSmpInProgress = false
                                        pendingDeleteSmpUri = null
                                        stopLoadingNice()
                                    }
                                }
                            }
                        ) {
                            androidx.compose.material3.Text(
                                stringResource(R.string.library_delete_action),
                                color = Color(0xFFFF6464)
                            )
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            enabled = !deleteSmpInProgress,
                            onClick = {
                                if (deleteSmpInProgress) return@TextButton
                                pendingDeleteSmpUri = null
                            }
                        ) {
                            androidx.compose.material3.Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }

            val commitAliasRename: () -> Unit = commit@{
                focusManager.clearFocus(force = true)

                val target = renameTarget ?: return@commit
                val newTitle = renameText.trim()
                if (newTitle.isEmpty()) {
                    renameTarget = null
                    return@commit
                }

                if (BuildConfig.DEBUG) {
                    Log.d("ALIAS_RENAME", "commit source=library uri=${target.uri} newTitle='$newTitle'")
                }

                renameTarget = null
                scope.launch {
                    startLoading(sRenaming, determinate = false)
                    try {
                        val saved = withContext(Dispatchers.IO) {
                            TitleAliasesStore.setTitleForTrack(context, target.uri.toString(), newTitle)
                        }
                        if (saved) {
                            PlaylistRepository.clearCustomTitleEverywhere(target.uri.toString())
                        }

                        if (BuildConfig.DEBUG) {
                            Toast.makeText(
                                context,
                                if (saved) "Alias enregistré" else "Alias NON enregistré (voir logs)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } finally {
                        stopLoadingNice()
                    }
                }
            }

            RenameDialog(
                show = renameTarget != null,
                renameText = renameText,
                onRenameText = { renameText = it },
                onCancel = { renameTarget = null },
                enabled = !isLoading,
                onConfirm = commitAliasRename
            )

            if (showEditPrompterDialog && editPrompterId != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        showEditPrompterDialog = false
                        editPrompterId = null
                    },
                    title = {
                        androidx.compose.material3.Text(stringResource(R.string.quickplaylists_edit_prompter_title))
                    },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = editPrompterTitle,
                                onValueChange = { editPrompterTitle = it },
                                label = { Text(stringResource(R.string.common_title_label)) },
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editPrompterContent,
                                onValueChange = { editPrompterContent = it },
                                label = { Text(stringResource(R.string.quickplaylists_prompter_text_label)) },
                                minLines = 4
                            )
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                val id = editPrompterId ?: return@TextButton
                                val title = editPrompterTitle.trim()
                                val content = editPrompterContent.trim()
                                if (title.isBlank()) return@TextButton
                                TextSongRepository.update(
                                    context = context,
                                    id = id,
                                    title = title,
                                    content = content
                                )
                                val folder = currentFolderUri
                                if (folder != null) {
                                    entries = buildEntriesForFolder(folder, useCache = false)
                                }
                                showEditPrompterDialog = false
                                editPrompterId = null
                            }
                        ) {
                            androidx.compose.material3.Text(stringResource(R.string.common_save))
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showEditPrompterDialog = false
                                editPrompterId = null
                            }
                        ) {
                            androidx.compose.material3.Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }

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
