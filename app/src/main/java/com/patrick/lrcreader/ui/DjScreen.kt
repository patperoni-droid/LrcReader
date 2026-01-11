package com.patrick.lrcreader.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import androidx.compose.material3.LinearProgressIndicator
import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.DjBusController
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.dj.DjEngine

@Composable
fun DjScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current
) {
    DjEngine.init(context)

    // ✅ On réutilise EXACTEMENT la même racine + le même index que la Bibliothèque
    // (donc : pas de rescan, pas de demande d’autorisation)
    var rootFolderUri by remember { mutableStateOf<Uri?>(BackupFolderPrefs.get(context)) }
    var currentFolderUri by remember { mutableStateOf<Uri?>(rootFolderUri) }
    var folderStack by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var indexAll by remember { mutableStateOf<List<LibraryIndexCache.CachedEntry>>(emptyList()) }
    var entries by remember { mutableStateOf<List<DjEntry>>(emptyList()) }

    // (spinner seulement si index absent/invalide)
    var isLoading by remember { mutableStateOf(false) }

    // ---------------------- état purement UI local ----------------------
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var isQueuePanelOpen by remember { mutableStateOf(false) }

    // 🔍 état recherche
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 🔁 index global (tous les titres audio)
    var allAudioEntries by remember { mutableStateOf<List<DjEntry>>(emptyList()) }

    // état DJ global
    val djState by DjEngine.state.collectAsState()

    Spacer(Modifier.height(8.dp))




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

    fun cachedToDjEntry(e: LibraryIndexCache.CachedEntry): DjEntry {
        return DjEntry(
            uri = Uri.parse(e.uriString),
            name = e.name,
            isDirectory = e.isDirectory
        )
    }

    fun refreshFromIndex() {
        val root = rootFolderUri
        val cur = currentFolderUri ?: root

        if (root == null) {
            entries = emptyList()
            allAudioEntries = emptyList()
            return
        }

        if (cur == null) {
            entries = emptyList()
        } else {
            val children = LibraryIndexCache.childrenOf(indexAll, cur)
            entries = children.map { cachedToDjEntry(it) }
        }

        // Index global audio : instantané (filtre en mémoire)
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
            rootFolderUri = BackupFolderPrefs.get(context)
            currentFolderUri = rootFolderUri
            folderStack = emptyList()

            val cachedAll = LibraryIndexCache.load(context)
            indexAll = cachedAll ?: emptyList()

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
                if (folderStack.isNotEmpty()) {
                    IconButton(onClick = {
                        val newStack = folderStack.dropLast(1)
                        val parentUri = newStack.lastOrNull() ?: rootFolderUri
                        currentFolderUri = parentUri
                        folderStack = newStack
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

                    val shownUri = currentFolderUri ?: rootFolderUri
                    Text(
                        text = shownUri?.let {
                            DocumentFile.fromTreeUri(context, it)?.name ?: "…"
                        } ?: "Choisis d’abord Music dans Bibliothèque",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                // 🔍 icône recherche
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
                        text = { Text("Rafraîchir depuis l’index Bibliothèque") },
                        onClick = {
                            menuOpen = false
                            isLoading = true
                            try {
                                rootFolderUri = BackupFolderPrefs.get(context)
                                if (currentFolderUri == null) currentFolderUri = rootFolderUri
                                indexAll = LibraryIndexCache.load(context) ?: emptyList()
                                refreshFromIndex()
                            } finally {
                                isLoading = false
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Info : Music se choisit dans Bibliothèque") },
                        onClick = { menuOpen = false }
                    )
                }
            }

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
                            "Recherche (tous dossiers)…",
                            fontSize = 12.sp,
                            color = Color(0x77FFFFFF)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = Color(0xFFFFC107)
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
                onStop = { DjEngine.stopDj() }
            )
// ───────── Progress + timing (titre en cours) ─────────
            // ───────── Progress + timing (titre en cours) + SEEK tactile ─────────
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
                    // gauche = restant
                    Text(
                        text = "-${formatMs(remainingMs)}",
                        color = sub,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp) // zone tactile plus grande que la barre
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
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val newX = change.position.x
                                        seekProgress = progressFromX(newX, size.width.toFloat())
                                    },
                                    onDragEnd = {
                                        isSeeking = false
                                        commitSeek(seekProgress)
                                    },
                                    onDragCancel = {
                                        isSeeking = false
                                    }
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

                    // droite = écoulé
                    Text(
                        text = formatMs(elapsedMs),
                        color = sub,
                        fontSize = 12.sp
                    )
                }
            }
            // ---------------------- File d’attente -------------------------
            val showQueuePanel = djState.queueAutoPlay || djState.queue.isNotEmpty()
            if (djState.queueAutoPlay) isQueuePanelOpen = true
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
                currentFolderUri = currentFolderUri,
                visibleEntries = visibleEntries,
                onBg = onBg,
                subColor = sub,
                isLoading = isLoading,
                onDirectoryClick = { entry ->
                    val old = currentFolderUri ?: rootFolderUri
                    if (old != null) folderStack = folderStack + old
                    currentFolderUri = entry.uri
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

            // ✅ Petit message si pas de dossier Music choisi
            if (rootFolderUri == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "⚠️ Va dans Bibliothèque → “Choisir dossier Music” (1 fois). Ensuite DJ sera instantané.",
                    color = sub,
                    fontSize = 12.sp
                )
            }

            // ✅ Spinner si index vide (rare) / refresh
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