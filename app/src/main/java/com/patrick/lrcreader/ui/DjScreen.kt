package com.patrick.lrcreader.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.DjFolderPrefs
import com.patrick.lrcreader.core.DjIndexCache
import com.patrick.lrcreader.core.DjMediaFolderNode
import com.patrick.lrcreader.core.DjBusController
import com.patrick.lrcreader.core.buildDjGlobalAudioIndex
import com.patrick.lrcreader.core.djGlobalFolderDisplayName
import com.patrick.lrcreader.core.djGlobalRootUri
import com.patrick.lrcreader.core.hasDjGlobalAudioAccess
import com.patrick.lrcreader.core.isDjGlobalFolder
import com.patrick.lrcreader.core.isDjGlobalRoot
import com.patrick.lrcreader.core.DjScanState
import com.patrick.lrcreader.core.loadDjMediaFolderTree
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.dj.DjEngine
import com.patrick.lrcreader.core.dj.DjQueuedTrack
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.theme.SplColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val DJ_SIGNATURE_RECHECK_COOLDOWN_MS = 15_000L
private const val DJ_FOLDER_TAG = "DJ_FOLDER"

@Composable
fun DjScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current
) {
    DjEngine.init(context)

    // ✅ ÉTAT PERSISTANT (reste mémorisé quand tu changes d’onglet)
    val browserVm: DjBrowserViewModel = viewModel()

    // ✅ Mode DJ = dossier + index séparés (pas la bibliothèque)
    var indexAll by remember { mutableStateOf<List<DjIndexCache.Entry>>(emptyList()) }
    var entries by remember { mutableStateOf<List<DjEntry>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }
    var isMediaBrowserLoading by remember { mutableStateOf(false) }
    var hasResolvedDjAccess by remember { mutableStateOf(false) }
    var isGlobalAudioMode by remember { mutableStateOf(hasDjGlobalAudioAccess(context)) }

    var menuOpen by remember { mutableStateOf(false) }
    var isQueuePanelOpen by remember { mutableStateOf(false) }
    var isDjVolumeFaderOpen by remember { mutableStateOf(false) }
    var isDjFolderPickerOpen by remember { mutableStateOf(false) }
    var djMediaFolderRoots by remember { mutableStateOf<List<DjMediaFolderNode>>(emptyList()) }
    var djFolderPickerStack by remember { mutableStateOf<List<DjMediaFolderNode>>(emptyList()) }
    var didAutoRequestAudioPermission by rememberSaveable { mutableStateOf(false) }
    var openFolderAfterAudioPermission by remember { mutableStateOf(false) }

    // 🔍 état recherche
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 🔁 index global (tous les titres audio)
    var allAudioEntries by remember { mutableStateOf<List<DjEntry>>(emptyList()) }

    // ✅ pour lancer des traitements lourds hors UI
    val scope = rememberCoroutineScope()
    val sDjLiteLimitTitle = stringResource(R.string.dj_lite_limit_title)
    val sDjLiteLimitMessage = stringResource(R.string.dj_lite_limit_message)
    val sUpgradeToPro = stringResource(R.string.library_upgrade_to_pro)

    val openUpgradeToPro: () -> Unit = remember(context) {
        {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://search?q=MusiMio Pro")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=MusiMio%20Pro&c=apps")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(marketIntent)
            } catch (_: ActivityNotFoundException) {
                context.startActivity(webIntent)
            }
        }
    }

    fun cachedToDjEntry(e: DjIndexCache.Entry): DjEntry {
        return DjEntry(
            uri = Uri.parse(e.uriString),
            name = e.name,
            isDirectory = e.isDirectory
        )
    }

    fun syncBrowserToRoot(root: Uri?) {
        val rootChanged = browserVm.rootFolderUri?.toString() != root?.toString()
        if (rootChanged) {
            browserVm.setRoot(root)
            browserVm.clearStack()
        } else if (browserVm.rootFolderUri == null) {
            browserVm.setRoot(root)
        }

        if (rootChanged || browserVm.currentFolderUri == null) {
            browserVm.setCurrent(root)
        }
    }

    fun djRootDisplayName(root: Uri?): String {
        if (root == null) return context.getString(R.string.dj_no_folder_selected)
        if (isDjGlobalRoot(root)) {
            return context.getString(R.string.dj_global_music_label)
        }
        if (isDjGlobalFolder(root)) {
            return djGlobalFolderDisplayName(root) ?: context.getString(R.string.common_ellipsis)
        }
        if (root.scheme == "file") {
            return File(root.path ?: "").name.ifBlank { context.getString(R.string.common_ellipsis) }
        }
        return (
            DocumentFile.fromTreeUri(context, root)
                ?: DocumentFile.fromSingleUri(context, root)
            )?.name ?: context.getString(R.string.common_ellipsis)
    }

    fun readDjRootLastModified(root: Uri): Long {
        return if (root.scheme == "file") {
            File(root.path ?: return 0L).lastModified()
        } else {
            val doc = DocumentFile.fromTreeUri(context, root)
                ?: DocumentFile.fromSingleUri(context, root)
                ?: return 0L
            runCatching { doc.lastModified() }.getOrDefault(0L)
        }
    }

    suspend fun shouldAutoScanDj(root: Uri, currentIndex: List<DjIndexCache.Entry>): Boolean {
        if (isDjGlobalRoot(root)) {
            val meta = DjIndexCache.loadScanMeta(context)
            if (currentIndex.isEmpty()) return true
            if (meta?.rootUriString != root.toString()) return true
            if (!DjScanState.isScanning.value && System.currentTimeMillis() - meta.verifiedAtMs < DJ_SIGNATURE_RECHECK_COOLDOWN_MS) {
                return false
            }
            return true
        }

        if (!DjFolderPrefs.isScanned(context)) return true
        if (currentIndex.isEmpty()) return true

        val meta = DjIndexCache.loadScanMeta(context) ?: return true
        if (meta.rootUriString != root.toString()) return true

        val currentRootLastModified = withContext(Dispatchers.IO) {
            readDjRootLastModified(root)
        }
        if (
            meta.rootLastModifiedMs > 0L &&
            currentRootLastModified > 0L &&
            meta.rootLastModifiedMs == currentRootLastModified
        ) {
            return false
        }

        val now = System.currentTimeMillis()
        if (!DjScanState.isScanning.value && now - meta.verifiedAtMs < DJ_SIGNATURE_RECHECK_COOLDOWN_MS) {
            return false
        }

        val currentSignature = withContext(Dispatchers.IO) {
            computeDjFolderSignature(context, root)
        } ?: return true

        if (currentSignature.hash == meta.signature) {
            DjIndexCache.updateScanVerification(
                context = context,
                rootUri = root,
                rootLastModifiedMs = currentSignature.rootLastModifiedMs,
                verifiedAtMs = now
            )
            return false
        }

        return true
    }

    fun refreshFromIndex() {
        val root = browserVm.rootFolderUri
        val cur = browserVm.currentFolderUri ?: root

        if (root == null) {
            entries = emptyList()
            allAudioEntries = emptyList()
            return
        }

        // 📁 contenu du dossier courant
        entries = if (cur == null) {
            emptyList()
        } else {
            val children = DjIndexCache.childrenOf(indexAll, cur)
            children.map { cachedToDjEntry(it) }
        }

        // 🎵 index global audio : instantané (filtre en mémoire)
        allAudioEntries = indexAll
            .asSequence()
            .filter { !it.isDirectory }
            .map { cachedToDjEntry(it) }
            .toList()
    }

    // ✅ 1 seule source de vérité pour lancer un scan DJ depuis n'importe où
    fun launchDjScan(djRoot: Uri, showToast: Boolean) {
        if (DjScanState.isScanning.value) {
            if (showToast) {
                Toast.makeText(
                    context,
                    context.getString(R.string.dj_scan_already_running),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        scope.launch {
            isLoading = true
            DjScanState.start()
            try {
                val djRootAccessible = withContext(Dispatchers.IO) {
                    if (isDjGlobalRoot(djRoot)) {
                        hasDjGlobalAudioAccess(context)
                    } else if (djRoot.scheme == "file") {
                        File(djRoot.path ?: "").isDirectory
                    } else {
                        val doc = DocumentFile.fromTreeUri(context, djRoot)
                            ?: DocumentFile.fromSingleUri(context, djRoot)
                        doc != null && doc.isDirectory
                    }
                }
                if (!djRootAccessible) {
                    throw IllegalStateException("DJ root inaccessible: $djRoot")
                }

                val newDjIndex = withContext(Dispatchers.IO) {
                    if (isDjGlobalRoot(djRoot)) {
                        buildDjGlobalAudioIndex(context)
                    } else {
                        buildDjFullIndex(context, djRoot)
                    }
                }
                DjIndexCache.save(context, newDjIndex)
                if (isDjGlobalRoot(djRoot)) {
                    val latestModifiedMs = newDjIndex.maxOfOrNull { it.lastModifiedMs } ?: 0L
                    DjIndexCache.saveScanMeta(
                        context = context,
                        rootUri = djRoot,
                        signature = "media:${newDjIndex.size}:$latestModifiedMs",
                        rootLastModifiedMs = latestModifiedMs,
                        itemCount = newDjIndex.size
                    )
                } else {
                    withContext(Dispatchers.IO) {
                        computeDjFolderSignature(context, djRoot)
                    }?.let { signature ->
                        DjIndexCache.saveScanMeta(
                            context = context,
                            rootUri = djRoot,
                            signature = signature.hash,
                            rootLastModifiedMs = signature.rootLastModifiedMs,
                            itemCount = signature.itemCount
                        )
                    }
                }
                DjFolderPrefs.setScanned(context, true)

                indexAll = newDjIndex
                refreshFromIndex()

                if (showToast) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.dj_scan_done, newDjIndex.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (t: Throwable) {
                android.util.Log.e("DJ", "Scan DJ failed: ${t.message}", t)
                if (showToast) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.dj_scan_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                DjScanState.stop()
                isLoading = false
            }
        }
    }

    suspend fun refreshDjState(forceSignatureCheck: Boolean) {
        val globalAudioEnabled = hasDjGlobalAudioAccess(context)
        isGlobalAudioMode = globalAudioEnabled
        val root = if (globalAudioEnabled) {
            djGlobalRootUri()
        } else {
            DjFolderPrefs.getOrAdoptFromLibraryRoot(context)
        }
        android.util.Log.i(
            DJ_FOLDER_TAG,
            "refreshDjState forceSignatureCheck=$forceSignatureCheck globalAudioEnabled=$globalAudioEnabled resolvedRoot=$root"
        )
        syncBrowserToRoot(root)

        if (root == null) {
            indexAll = emptyList()
            refreshFromIndex()
            return
        }

        val cached = DjIndexCache.load(context).orEmpty()
        indexAll = cached
        refreshFromIndex()
    }

    fun startDjFolderPlayback(tracks: List<DjQueuedTrack>) {
        if (tracks.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.dj_folder_play_empty),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        PlaybackCoordinator.onDjStart()
        DjEngine.clearQueue()
        DjEngine.setQueueAutoPlay(true)
        DjEngine.selectTrackFromList(tracks.first().uri, tracks.first().title)
        tracks.drop(1).forEach { item ->
            DjEngine.addToQueue(item.uri, item.title)
        }

        Toast.makeText(
            context,
            context.getString(R.string.dj_folder_play_started, tracks.size),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun openDjFolderPlayChooser() {
        scope.launch {
            isLoading = true
            try {
                djMediaFolderRoots = withContext(Dispatchers.IO) {
                    loadDjMediaFolderTree(context)
                }
                djFolderPickerStack = emptyList()
                isDjFolderPickerOpen = true
            } finally {
                isLoading = false
            }
        }
    }

    suspend fun loadMediaBrowserTreeIfAllowed() {
        if (!hasDjGlobalAudioAccess(context)) return
        isMediaBrowserLoading = true
        try {
            djMediaFolderRoots = withContext(Dispatchers.IO) {
                loadDjMediaFolderTree(context)
            }
            djFolderPickerStack = emptyList()
        } finally {
            isMediaBrowserLoading = false
        }
    }

    val requestDjAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                isGlobalAudioMode = true
                if (openFolderAfterAudioPermission) {
                    openDjFolderPlayChooser()
                } else {
                    scope.launch {
                        loadMediaBrowserTreeIfAllowed()
                    }
                }
            } else {
                isGlobalAudioMode = false
                Toast.makeText(
                    context,
                    context.getString(R.string.dj_folder_play_permission_required),
                    Toast.LENGTH_SHORT
                ).show()
            }
            openFolderAfterAudioPermission = false
        }
    )

    val pickDjFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            android.util.Log.i(DJ_FOLDER_TAG, "picker:selected uri=$uri")

            // 1) persister la permission SAF
            var persistPermissionOk = false
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                persistPermissionOk = true
            } catch (error: Exception) {
                android.util.Log.w(
                    DJ_FOLDER_TAG,
                    "picker:persist_permission_failed uri=$uri reason=${error.message}",
                    error
                )
            }

            val resolvedDjRoot = DjFolderPrefs.resolveFixedDjRootFromPickedTree(context, uri) ?: uri
            android.util.Log.i(
                DJ_FOLDER_TAG,
                "picker:resolved picked=$uri persistPermissionOk=$persistPermissionOk resolved=$resolvedDjRoot"
            )

            // 2) sauver la racine DJ + mettre à jour le browser
            DjFolderPrefs.save(context, resolvedDjRoot)
            android.util.Log.i(
                DJ_FOLDER_TAG,
                "picker:saved saved=${DjFolderPrefs.get(context)}"
            )
            syncBrowserToRoot(resolvedDjRoot)
            indexAll = emptyList()
            refreshFromIndex()

            // 3) scanner
            launchDjScan(resolvedDjRoot, showToast = false)
        }
    )

    val pickDjAutoFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            scope.launch {
                val tracks = withContext(Dispatchers.IO) {
                    val root = DocumentFile.fromTreeUri(context, uri) ?: return@withContext emptyList()
                    root.listFiles()
                        .asSequence()
                        .filter { it.isFile }
                        .filter { doc ->
                            val mime = doc.type?.lowercase().orEmpty()
                            if (mime.startsWith("audio/")) return@filter true
                            val name = doc.name.orEmpty()
                            val ext = name.substringAfterLast('.', "").lowercase()
                            MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(ext)
                                ?.lowercase()
                                ?.startsWith("audio/") == true
                        }
                        .sortedBy { it.name.orEmpty().lowercase() }
                        .mapNotNull { doc ->
                            val trackUri = doc.uri.toString().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val trackTitle = doc.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                                ?: doc.name
                                ?: trackUri
                            DjQueuedTrack(uri = trackUri, title = trackTitle)
                        }
                        .toList()
                }

                startDjFolderPlayback(tracks)
            }
        }
    )

    // ✅ Import MP3 vers SPL Music/backingtracks
    val pickAudioFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult

            val appRoot = BackupFolderPrefs.get(context) ?: return@rememberLauncherForActivityResult

            isLoading = true
            try {
                com.patrick.lrcreader.core.ImportAudioManager.importAudioFiles(
                    context = context,
                    appRootTreeUri = appRoot,
                    sourceUris = uris,
                    destFolderName = "backingtracks",
                    overwriteIfExists = false
                )
            } finally {
                isLoading = false
            }
        }
    )

    // état DJ global
    val djState by DjEngine.state.collectAsState()
    val djUiLevel by DjBusController.uiLevel.collectAsState()

    // --------------------- animation platines rondes ---------------------
    val infinite = rememberInfiniteTransition(label = "dj-discs")
    val angleA by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleA"
    )
    val angleB by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleB"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val lifecycleOwner = LocalLifecycleOwner.current

    // -------------------------- 1er chargement ---------------------------
    LaunchedEffect(Unit) {
        try {
            refreshDjState(forceSignatureCheck = false)
        } finally {
            hasResolvedDjAccess = true
        }
        if (hasDjGlobalAudioAccess(context)) {
            loadMediaBrowserTreeIfAllowed()
        } else if (!didAutoRequestAudioPermission) {
            didAutoRequestAudioPermission = true
            openFolderAfterAudioPermission = false
            requestDjAudioPermissionLauncher.launch(requiredDjAudioPermission())
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    refreshDjState(forceSignatureCheck = true)
                    if (djMediaFolderRoots.isEmpty()) {
                        loadMediaBrowserTreeIfAllowed()
                    }
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Palette "console analogique"
    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF171717),
            Color(0xFF101010),
            Color(0xFF181410)
        )
    )
    val onBg = Color(0xFFFFF8E1)
    val sub = Color(0xFFB0BEC5)
    val card = Color(0xFF1B1B1B)
    val accentGo = Color(0xFFFFC107)
    val deckAGlow = Color(0xFF4CAF50)
    val deckBGlow = Color(0xFFE040FB)
    val fixedDjPath = stringResource(R.string.dj_fixed_folder_path)
    val globalMusicLabel = stringResource(R.string.dj_global_music_label)
    val scanRefreshLabel = if (isGlobalAudioMode) {
        stringResource(R.string.dj_menu_scan_refresh_global)
    } else {
        stringResource(R.string.dj_menu_scan_refresh)
    }
    val sliderHeight = 450.dp
    val sliderWidth = 60.dp
    val overhangRight = 18.dp
    val blockPaddingEnd = 10.dp
    val buttonOffsetX = 30.dp
    val buttonOffsetY = 0.dp
    val djLevelPercent = (djUiLevel.coerceIn(0f, 1f) * 100f).roundToInt()

    // Liste visible (filtre dans le dossier courant)
    val visibleEntries = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) entries
        else {
            val q = searchQuery.trim().lowercase()
            entries.filter { it.name.lowercase().contains(q) }
        }
    }

    // Résultats de recherche globale (tous dossiers)
    val searchResults = remember(searchQuery, allAudioEntries) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.trim().lowercase()
            allAudioEntries.filter { !it.isDirectory && it.name.lowercase().contains(q) }
        }
    }
    val mediaCurrentFolder = djFolderPickerStack.lastOrNull()
    val isResolvingDjAccess = !hasResolvedDjAccess
    val needsDjAuthorization = hasResolvedDjAccess && browserVm.rootFolderUri == null
    val showDjEmptyState = hasResolvedDjAccess &&
        !isGlobalAudioMode &&
        !needsDjAuthorization &&
        !isLoading &&
        searchQuery.isBlank() &&
        visibleEntries.isEmpty() &&
        allAudioEntries.isEmpty() &&
        DjFolderPrefs.isScanned(context)
    val authorizeMenuLabel = if (needsDjAuthorization) {
        stringResource(R.string.dj_menu_choose_folder)
    } else {
        stringResource(R.string.dj_menu_reauthorize_folder)
    }

    // ============================== UI ==============================
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .imePadding()
    ) {
        if (isDjFolderPickerOpen) {
            val currentPickerFolder = djFolderPickerStack.lastOrNull()
            DjFolderPickerScreen(
                currentFolder = currentPickerFolder,
                rootFolders = djMediaFolderRoots,
                isLoading = isLoading,
                onBack = {
                    if (djFolderPickerStack.isNotEmpty()) {
                        djFolderPickerStack = djFolderPickerStack.dropLast(1)
                    } else {
                        isDjFolderPickerOpen = false
                    }
                },
                onOpenFolder = { folder ->
                    djFolderPickerStack = djFolderPickerStack + folder
                },
                onSelectFolder = { folder ->
                    isDjFolderPickerOpen = false
                    djFolderPickerStack = emptyList()
                    startDjFolderPlayback(folder.recursiveTracks)
                },
                onChooseManualFolder = {
                    isDjFolderPickerOpen = false
                    pickDjAutoFolderLauncher.launch(null)
                }
            )
        } else {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {

            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                val canNavigateBack = if (isGlobalAudioMode) {
                    mediaCurrentFolder != null
                } else {
                    browserVm.folderStack.isNotEmpty()
                }

                if (canNavigateBack) {
                    IconButton(onClick = {
                        if (isGlobalAudioMode && mediaCurrentFolder != null) {
                            djFolderPickerStack = djFolderPickerStack.dropLast(1)
                        } else {
                            browserVm.popToParentOrRoot()
                            refreshFromIndex()
                            searchQuery = ""
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dj_cd_back),
                            tint = onBg
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dj_title),
                        color = Color.White,
                        fontSize = 20.sp
                    )

                    Spacer(Modifier.width(10.dp))

                    FilledTonalButton(
                        onClick = {
                            if (hasDjGlobalAudioAccess(context)) {
                                openDjFolderPlayChooser()
                            } else {
                                openFolderAfterAudioPermission = true
                                requestDjAudioPermissionLauncher.launch(requiredDjAudioPermission())
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            contentColor = onBg
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.dj_folder_play_button),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    val shownUri = browserVm.currentFolderUri ?: browserVm.rootFolderUri
                    val shownLocation = if (isGlobalAudioMode) {
                        mediaCurrentFolder?.folderName ?: globalMusicLabel
                    } else {
                        djRootDisplayName(shownUri)
                    }
                    Text(
                        text = shownLocation,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                // 🔍 recherche
                IconButton(
                    onClick = {
                        isSearchOpen = !isSearchOpen
                        if (!isSearchOpen) searchQuery = ""
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.dj_cd_search),
                        tint = if (isSearchOpen) accentGo else onBg
                    )
                }

                // ⋮ menu
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.dj_cd_options),
                        tint = onBg
                    )
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {

                    if (!isGlobalAudioMode) {
                        DropdownMenuItem(
                            text = { Text(authorizeMenuLabel) },
                            onClick = {
                                menuOpen = false
                                pickDjFolderLauncher.launch(null)
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text(scanRefreshLabel) },
                        onClick = {
                            menuOpen = false

                            val djRoot = if (isGlobalAudioMode) {
                                djGlobalRootUri()
                            } else {
                                DjFolderPrefs.getOrAdoptFromLibraryRoot(context)
                            }
                            syncBrowserToRoot(djRoot)

                            if (djRoot == null) {
                                indexAll = emptyList()
                                refreshFromIndex()
                                return@DropdownMenuItem
                            }

                            launchDjScan(djRoot, showToast = true)
                        }
                    )

                    Divider()

                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.dj_menu_import_music))
                        },
                        onClick = {
                            menuOpen = false
                            pickAudioFilesLauncher.launch(
                                arrayOf(
                                    "audio/*",
                                    "application/ogg",
                                    "application/octet-stream"
                                )
                            )
                        }
                    )
                }
            } // <-- FIN DU Row du header

            // 🔍 barre de recherche
            if (isSearchOpen) {
                Spacer(Modifier.height(6.dp))
                SmpSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = if (isGlobalAudioMode) {
                        stringResource(R.string.dj_search_placeholder_global)
                    } else {
                        stringResource(R.string.dj_search_placeholder)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .height(48.dp),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 14.sp
                    ),
                    placeholderColor = Color(0x77FFFFFF),
                    leadingIconTint = accentGo
                )
            }

            Spacer(Modifier.height(10.dp))

            // ───────── Carte principale DJ (platines + bus) ─────────
            val goEnabled =
                (djState.activeSlot == 1 && djState.deckBUri != null) ||
                        (djState.activeSlot == 2 && djState.deckAUri != null)

            DjMainCard(
                cardColor = card,
                subColor = sub,
                onBg = onBg,
                accentGo = accentGo,
                deckAGlow = deckAGlow,
                deckBGlow = deckBGlow,
                crossfadePos = djState.crossfadePos,
                activeSlot = djState.activeSlot,
                deckATitle = djState.deckATitle,
                deckBTitle = djState.deckBTitle,
                isPlaying = djState.playingUri != null,
                angleA = angleA,
                angleB = angleB,
                pulse = pulse,
                goEnabled = goEnabled,
                onCrossfadeChange = { DjEngine.setCrossfadePos(it) },
                onGo = {
                    PlaybackCoordinator.onDjStart()
                    DjEngine.launchCrossfade()
                },
                onStop = { DjEngine.stopDj() },

                // ✅ progress/seek
                progress = djState.progress,
                currentPositionMs = djState.currentPositionMs,
                currentDurationMs = djState.currentDurationMs,
                onSeekTo = { ms -> DjEngine.seekTo(ms) }
            )

            // ───────── Progress + timing + SEEK tactile ─────────
            if (djState.playingUri != null && djState.currentDurationMs > 0) {

                fun formatMs(ms: Int): String {
                    val totalSec = (ms / 1000).coerceAtLeast(0)
                    val m = totalSec / 60
                    val s = totalSec % 60
                    return "%d:%02d".format(m, s)
                }

                var isSeeking by remember { mutableStateOf(false) }
                var seekProgress by remember { mutableStateOf(0f) } // 0..1

                val elapsedMs = djState.currentPositionMs.coerceIn(0, djState.currentDurationMs)
                val remainingMs = (djState.currentDurationMs - elapsedMs).coerceAtLeast(0)
                val shownProgress = if (isSeeking) seekProgress else djState.progress.coerceIn(0f, 1f)

                fun progressFromX(x: Float, widthPx: Float): Float {
                    if (widthPx <= 1f) return 0f
                    return (x / widthPx).coerceIn(0f, 1f)
                }

                fun commitSeek(p: Float) {
                    val ms = (p.coerceIn(0f, 1f) * djState.currentDurationMs.toFloat()).roundToInt()
                    DjEngine.seekTo(ms)
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "-${formatMs(remainingMs)}", color = sub, fontSize = 12.sp)

                    Spacer(Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp)
                            .pointerInput(djState.playingUri, djState.currentDurationMs) {
                                detectTapGestures { offset: Offset ->
                                    val p = progressFromX(offset.x, size.width.toFloat())
                                    seekProgress = p
                                    isSeeking = false
                                    commitSeek(p)
                                }
                            }
                            .pointerInput(djState.playingUri, djState.currentDurationMs) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        isSeeking = true
                                        seekProgress = progressFromX(offset.x, size.width.toFloat())
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        seekProgress = progressFromX(change.position.x, size.width.toFloat())
                                    },
                                    onDragEnd = {
                                        isSeeking = false
                                        commitSeek(seekProgress)
                                    },
                                    onDragCancel = { isSeeking = false }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        LinearProgressIndicator(
                            progress = shownProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Text(text = formatMs(elapsedMs), color = sub, fontSize = 12.sp)

                    Spacer(Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFFF5252), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(10.dp))
                            .clickable { DjEngine.stopDj() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.dj_cd_stop),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ---------------------- File d’attente -------------------------
            val showQueuePanel = djState.queueAutoPlay || djState.queue.isNotEmpty()
            if (showQueuePanel) {
                Spacer(Modifier.height(8.dp))
                DjQueuePanel(
                    cardColor = card,
                    subColor = sub,
                    queue = djState.queue,
                    isOpen = isQueuePanelOpen,
                    onToggleOpen = { isQueuePanelOpen = !isQueuePanelOpen },
                    queueAutoPlay = djState.queueAutoPlay,
                    onToggleAutoPlay = { enabled -> DjEngine.setQueueAutoPlay(enabled) },
                    onPlayItem = { qItem ->
                        PlaybackCoordinator.onDjStart()
                        DjEngine.playFromQueue(qItem)
                    },
                    onRemoveItem = { qItem ->
                        DjEngine.removeFromQueue(qItem)
                    }
                )
            }

            // 🔍 Résultats de recherche GLOBALE
            DjSearchResultsList(
                isVisible = isSearchOpen && searchQuery.isNotBlank(),
                searchResults = searchResults,
                playingUri = djState.playingUri,
                onPlay = { entry ->
                    val uriStr = entry.uri.toString()
                    PlaybackCoordinator.onDjStart()
                    DjEngine.selectTrackFromList(uriStr, entry.name)
                },
                onEnqueue = { entry ->
                    val uriStr = entry.uri.toString()
                    DjEngine.addToQueue(uriStr, entry.name)
                }
            )

            Spacer(Modifier.height(8.dp))

            // ───────── Liste dossiers + titres (dossier courant) ─────────
            when {
                isResolvingDjAccess -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = card),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = onBg
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.common_loading),
                                color = sub,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                showDjEmptyState -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = card),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dj_empty_title),
                                color = onBg,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (isGlobalAudioMode) {
                                    stringResource(R.string.dj_empty_global_body, globalMusicLabel)
                                } else {
                                    stringResource(R.string.dj_empty_body, fixedDjPath)
                                },
                                color = sub,
                                fontSize = 13.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        browserVm.rootFolderUri?.let { launchDjScan(it, showToast = true) }
                                    }
                                ) {
                                    Text(scanRefreshLabel)
                                }
                                TextButton(onClick = { pickDjFolderLauncher.launch(null) }) {
                                    Text(
                                        if (isGlobalAudioMode) {
                                            stringResource(R.string.dj_menu_use_legacy_folder)
                                        } else {
                                            stringResource(R.string.dj_reauthorize_action)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    if (isGlobalAudioMode) {
                        DjMediaStoreBrowser(
                            currentFolder = mediaCurrentFolder,
                            rootFolders = djMediaFolderRoots,
                            playingUri = djState.playingUri,
                            onBg = onBg,
                            subColor = sub,
                            isLoading = isMediaBrowserLoading,
                            onOpenFolder = { folder ->
                                djFolderPickerStack = djFolderPickerStack + folder
                                searchQuery = ""
                            },
                            onTrackPlay = { track ->
                                PlaybackCoordinator.onDjStart()
                                DjEngine.selectTrackFromList(track.uri, track.title)
                            },
                            onTrackEnqueue = { track ->
                                DjEngine.addToQueue(track.uri, track.title)
                            },
                            onChooseManualFolder = {
                                pickDjAutoFolderLauncher.launch(null)
                            }
                        )
                    } else {
                        DjFolderBrowser(
                            currentFolderUri = browserVm.currentFolderUri,
                            visibleEntries = visibleEntries,
                            onBg = onBg,
                            subColor = sub,
                            isLoading = isLoading,
                            onDirectoryClick = { entry ->
                                val old = browserVm.currentFolderUri ?: browserVm.rootFolderUri
                                if (old != null) browserVm.pushCurrent(old)

                                browserVm.setCurrent(entry.uri)
                                refreshFromIndex()
                                searchQuery = ""
                            },
                            onFilePlay = { entry ->
                                val uriStr = entry.uri.toString()
                                PlaybackCoordinator.onDjStart()
                                DjEngine.selectTrackFromList(uriStr, entry.name)
                            },
                            onFileEnqueue = { entry ->
                                val uriStr = entry.uri.toString()
                                DjEngine.addToQueue(uriStr, entry.name)
                            }
                        )
                    }
                }
            }

            // ✅ Spinner si refresh
            if (isLoading) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = onBg
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.common_loading), color = sub, fontSize = 12.sp)
                }
            }
        }
        }

        GainDrawer(
            isOpen = isDjVolumeFaderOpen,
            onToggleOpen = { isDjVolumeFaderOpen = !isDjVolumeFaderOpen },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .zIndex(9999f),
            faderHeight = sliderHeight,
            faderWidth = sliderWidth,
            drawerWidth = sliderWidth + overhangRight + 9.dp,
            endPadding = blockPaddingEnd,
            bottomPadding = 16.dp,
            buttonSize = 40.dp,
            buttonOffsetX = buttonOffsetX,
            buttonOffsetY = buttonOffsetY
        ) {
            VerticalTransparentSpeedSlider(
                value = djUiLevel.coerceIn(0f, 1f),
                onValueChange = { DjBusController.setUiLevel(it.coerceIn(0f, 1f)) },
                valueRange = 0f..1f,
                height = sliderHeight,
                width = sliderWidth,
                trackThickness = 4.dp,
                trackVerticalPadding = 24.dp,
                trackColor = SplColors.Outline.copy(alpha = 0.35f),
                filledTrackColor = SplColors.Accent.copy(alpha = 0.78f),
                centeredFilledTrack = true,
                thumbColor = Color.White.copy(alpha = 0.94f),
                thumbShadowElevation = 4.dp,
                thumbContent = {
                    Text(
                        text = djLevelPercent.toString(),
                        color = Color(0xFF111111),
                        fontSize = 11.sp
                    )
                },
                bottomLabel = stringResource(R.string.track_mix_level),
                bottomLabelColor = SplColors.SubText.copy(alpha = 0.90f),
                overhangRight = overhangRight
            )
        }

        if (djState.showLiteAutoPlayLimitDialog) {
            AlertDialog(
                onDismissRequest = { DjEngine.consumeLiteAutoLimitDialog() },
                title = {
                    Text(
                        text = sDjLiteLimitTitle,
                        color = onBg
                    )
                },
                text = {
                    Text(
                        text = sDjLiteLimitMessage,
                        color = sub
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            DjEngine.consumeLiteAutoLimitDialog()
                            openUpgradeToPro()
                        }
                    ) {
                        Text(sUpgradeToPro)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { DjEngine.consumeLiteAutoLimitDialog() }
                    ) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            )
        }
    }
}

private fun requiredDjAudioPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
