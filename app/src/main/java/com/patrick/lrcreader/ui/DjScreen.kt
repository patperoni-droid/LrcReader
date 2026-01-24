package com.patrick.lrcreader.ui


import com.patrick.lrcreader.core.BackupFolderPrefs
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrick.lrcreader.core.DjFolderPrefs
import com.patrick.lrcreader.core.DjIndexCache
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.dj.DjEngine
import kotlin.math.roundToInt

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

    var menuOpen by remember { mutableStateOf(false) }
    var isQueuePanelOpen by remember { mutableStateOf(false) }

    // 🔍 état recherche
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 🔁 index global (tous les titres audio)
    var allAudioEntries by remember { mutableStateOf<List<DjEntry>>(emptyList()) }

    // ✅ pour lancer des traitements lourds hors UI
    val scope = rememberCoroutineScope()

    val pickDjFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            // 1) persister la permission SAF
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}

            // 2) sauver la racine DJ + mettre à jour le browser
            DjFolderPrefs.save(context, uri)
            browserVm.setRoot(uri)
            browserVm.setCurrent(uri)

            // 3) scanner en arrière-plan (sinon écran noir / crash)
            scope.launch {
                isLoading = true
                try {
                    val newDjIndex = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        buildDjFullIndex(context, uri)
                    }

                    DjIndexCache.save(context, newDjIndex)
                    indexAll = newDjIndex

                    // refresh UI à partir de l’index LOCAL (pas indexAll qui peut être "en retard")
                    val root = browserVm.rootFolderUri
                    val cur = browserVm.currentFolderUri ?: root

                    if (root == null) {
                        entries = emptyList()
                        allAudioEntries = emptyList()
                    } else {
                        entries = if (cur == null) {
                            emptyList()
                        } else {
                            val children = DjIndexCache.childrenOf(newDjIndex, cur)
                            children.map { e ->
                                DjEntry(
                                    uri = Uri.parse(e.uriString),
                                    name = e.name,
                                    isDirectory = e.isDirectory
                                )
                            }
                        }

                        allAudioEntries = newDjIndex
                            .asSequence()
                            .filter { !it.isDirectory }
                            .map { e ->
                                DjEntry(
                                    uri = Uri.parse(e.uriString),
                                    name = e.name,
                                    isDirectory = e.isDirectory
                                )
                            }
                            .toList()
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("DJ", "Pick/Scan DJ folder crash: ${t.javaClass.simpleName}: ${t.message}", t)
                } finally {
                    isLoading = false
                }
            }
        }
    )
    // ✅ Import MP3 vers SPL Music/backingtracks
    val pickAudioFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult

            // ⚠️ Ici, on a besoin du dossier SPL Music (TreeUri) – celui que tu utilises pour créer backingtracks.
            // Je pars sur ton stockage central : BackupFolderPrefs (si chez toi c’est un autre Pref, dis-moi lequel et on le remplace).
            val appRoot = BackupFolderPrefs.get(context)

            if (appRoot == null) {
                // pas de crash : on ne fait rien, mais tu peux afficher un toast/snackbar si tu veux
                return@rememberLauncherForActivityResult
            }

            isLoading = true
            try {
                val res = com.patrick.lrcreader.core.ImportAudioManager.importAudioFiles(
                    context = context,
                    appRootTreeUri = appRoot,
                    sourceUris = uris,
                    destFolderName = "backingtracks",
                    overwriteIfExists = false
                )

                // 👉 Option simple : après import, tu rescannes (si ta bibliothèque pointe sur SPL Music).
                // Si ta bibliothèque scanne un autre root (Music choisi ailleurs), on branchera le rescan au bon endroit.
                // Ici on rafraîchit juste l’index DJ si besoin, sinon laisse.
            } finally {
                isLoading = false
            }
        }
    )
    // état DJ global
    val djState by DjEngine.state.collectAsState()

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

    fun cachedToDjEntry(e: DjIndexCache.Entry): DjEntry {
        return DjEntry(
            uri = Uri.parse(e.uriString),
            name = e.name,
            isDirectory = e.isDirectory
        )
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

    // -------------------------- 1er chargement ---------------------------
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val djRoot = DjFolderPrefs.get(context)

            if (browserVm.rootFolderUri == null) {
                browserVm.setRoot(djRoot)
            }
            if (browserVm.currentFolderUri == null) {
                browserVm.setCurrent(browserVm.rootFolderUri)
            }

            indexAll = DjIndexCache.load(context) ?: emptyList()
            // ✅ si on a un dossier DJ mais pas d'index => on scanne automatiquement 1 fois
            if (indexAll.isEmpty()) {
                val djRoot = DjFolderPrefs.get(context)
                if (djRoot != null) {
                    isLoading = true
                    try {
                        val newDjIndex = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            buildDjFullIndex(context, djRoot)
                        }
                        DjIndexCache.save(context, newDjIndex)
                        indexAll = newDjIndex

                        // important : mettre à jour le browser VM aussi
                        if (browserVm.rootFolderUri == null) browserVm.setRoot(djRoot)
                        if (browserVm.currentFolderUri == null) browserVm.setCurrent(djRoot)
                    } catch (t: Throwable) {
                        android.util.Log.e("DJ", "Auto-scan DJ failed: ${t.message}", t)
                    } finally {
                        isLoading = false
                    }
                }
            }
            refreshFromIndex()
        } finally {
            isLoading = false
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

    // ============================== UI ==============================
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .imePadding()
    ) {
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

                if (browserVm.folderStack.isNotEmpty()) {
                    IconButton(onClick = {
                        browserVm.popToParentOrRoot()
                        refreshFromIndex()
                        searchQuery = ""
                    }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Retour",
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
                        "DJ",
                        color = Color.White,
                        fontSize = 20.sp
                    )

                    Spacer(Modifier.width(10.dp))

                    val shownUri = browserVm.currentFolderUri ?: browserVm.rootFolderUri
                    Text(
                        text = shownUri?.let { DocumentFile.fromTreeUri(context, it)?.name ?: "…" }
                            ?: "Aucun dossier DJ choisi",
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
                        contentDescription = "Rechercher",
                        tint = if (isSearchOpen) accentGo else onBg
                    )
                }

                // ⋮ menu
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Options",
                        tint = onBg
                    )
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {

                    DropdownMenuItem(
                        text = { Text("Choisir dossier DJ…") },
                        onClick = {
                            menuOpen = false
                            pickDjFolderLauncher.launch(null)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Scanner / rafraîchir le dossier DJ") },
                        onClick = {
                            menuOpen = false
                            isLoading = true
                            try {
                                val djRoot = DjFolderPrefs.get(context)

                                browserVm.setRoot(djRoot)
                                if (browserVm.currentFolderUri == null) {
                                    browserVm.setCurrent(browserVm.rootFolderUri)
                                }

                                if (djRoot == null) {
                                    indexAll = emptyList()
                                    refreshFromIndex()
                                } else {
                                    val newDjIndex = buildDjFullIndex(context, djRoot)
                                    DjIndexCache.save(context, newDjIndex)
                                    indexAll = newDjIndex
                                    refreshFromIndex()
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    )

                    Divider()

                    DropdownMenuItem(
                        text = { Text("Importer des musiques (→ backingtracks)") },
                        onClick = {
                            menuOpen = false
                            // sélection multi-fichiers
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 14.sp,
                        color = Color.White
                    ),
                    placeholder = {
                        Text(
                            "Recherche (tous dossiers DJ)…",
                            fontSize = 12.sp,
                            color = Color(0x77FFFFFF)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = accentGo
                        )
                    }
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
                            contentDescription = "Stop DJ",
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

            // ✅ Petit message si pas de dossier DJ choisi
            if (browserVm.rootFolderUri == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "⚠️ Menu ⋮ → “Scanner / rafraîchir le dossier DJ” (1 fois). Ensuite DJ sera instantané.",
                    color = sub,
                    fontSize = 12.sp
                )
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
                    Text("Chargement…", color = sub, fontSize = 12.sp)
                }
            }
        }
    }
}
