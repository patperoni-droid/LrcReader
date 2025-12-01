package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.DjBusController
import com.patrick.lrcreader.core.DjFolderPrefs
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.dj.DjEngine
import com.patrick.lrcreader.core.dj.DjQueuedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DjScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current
) {
    DjEngine.init(context)

    // --------------------- état navigation dossiers ---------------------
    var rootFolderUri by remember { mutableStateOf<Uri?>(DjFolderPrefs.get(context)) }
    var currentFolderUri by remember { mutableStateOf<Uri?>(rootFolderUri) }
    var folderStack by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var entries by remember { mutableStateOf<List<DjEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // ---------------------- état purement UI local ----------------------
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var isQueuePanelOpen by remember { mutableStateOf(false) }

    // 🔍 état recherche
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 🔁 index global (tous les sous-dossiers du dossier DJ)
    var allAudioEntries by remember { mutableStateOf<List<DjEntry>>(emptyList()) }
    var isBuildingGlobalIndex by remember { mutableStateOf(false) }

    // état DJ global (lecteur, decks, queue, timeline…)
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

    // -------------------------- choisir dossier --------------------------
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                DjFolderPrefs.save(context, uri)
                DjFolderCache.clear()

                rootFolderUri = uri
                currentFolderUri = uri
                folderStack = emptyList()
                entries = emptyList()
                allAudioEntries = emptyList()
                searchQuery = ""

                scope.launch {
                    // 1) charger le dossier courant
                    isLoading = true
                    val fresh: List<DjEntry> = withContext(Dispatchers.IO) {
                        loadDjEntries(context, uri)
                    }
                    entries = fresh
                    DjFolderCache.put(uri, fresh)
                    isLoading = false

                    // 2) construire l’index global une seule fois
                    isBuildingGlobalIndex = true
                    val all: List<DjEntry> = withContext(Dispatchers.IO) {
                        scanAllAudioEntries(context, uri)
                    }
                    allAudioEntries = all
                    isBuildingGlobalIndex = false
                }
            }
        }
    )

    // -------------------------- 1er chargement ---------------------------
    LaunchedEffect(rootFolderUri) {
        val root = rootFolderUri
        if (root != null) {
            DjFolderCache.get(root)?.let {
                entries = it
            } ?: run {
                isLoading = true
                val fresh: List<DjEntry> = withContext(Dispatchers.IO) {
                    loadDjEntries(context, root)
                }
                entries = fresh
                DjFolderCache.put(root, fresh)
                isLoading = false
            }

            if (allAudioEntries.isEmpty() && !isBuildingGlobalIndex) {
                isBuildingGlobalIndex = true
                val all: List<DjEntry> = withContext(Dispatchers.IO) {
                    scanAllAudioEntries(context, root)
                }
                allAudioEntries = all
                isBuildingGlobalIndex = false
            }
        } else {
            entries = emptyList()
            allAudioEntries = emptyList()
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

    // Liste affichée tout en bas : dossier courant
    val visibleEntries = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) entries
        else {
            val q = searchQuery.trim().lowercase()
            entries.filter { it.name.lowercase().contains(q) }
        }
    }

    // Résultats de recherche globale (tous dossiers)
    val searchResults = remember(searchQuery, allAudioEntries, entries) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val base = if (allAudioEntries.isNotEmpty()) allAudioEntries else entries
            val q = searchQuery.trim().lowercase()
            base.filter { !it.isDirectory && it.name.lowercase().contains(q) }
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

                        if (parentUri != null) {
                            DjFolderCache.get(parentUri)?.let {
                                entries = it
                            } ?: run {
                                scope.launch {
                                    isLoading = true
                                    val fresh: List<DjEntry> = withContext(Dispatchers.IO) {
                                        loadDjEntries(context, parentUri)
                                    }
                                    entries = fresh
                                    DjFolderCache.put(parentUri, fresh)
                                    isLoading = false
                                }
                            }
                        } else {
                            entries = emptyList()
                        }
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

                    Text(
                        text = currentFolderUri?.let {
                            DocumentFile.fromTreeUri(context, it)?.name ?: "…"
                        } ?: "Aucun dossier DJ choisi",
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
                        leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        text = { Text("Choisir dossier DJ") },
                        onClick = {
                            menuOpen = false
                            pickFolderLauncher.launch(null)
                        }
                    )
                    if (rootFolderUri != null) {
                        DropdownMenuItem(
                            text = { Text("Oublier le dossier") },
                            onClick = {
                                menuOpen = false
                                DjFolderPrefs.clear(context)
                                DjFolderCache.clear()
                                rootFolderUri = null
                                currentFolderUri = null
                                folderStack = emptyList()
                                entries = emptyList()
                                allAudioEntries = emptyList()
                                searchQuery = ""
                            }
                        )
                    }
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

                if (isBuildingGlobalIndex) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = onBg
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Indexation des titres DJ…",
                            color = sub,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ───────── Carte principale DJ (platines + bus) ─────────
            androidx.compose.material3.Card(
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(16.dp),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    // Bandeau BUS DJ
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF3A2C24),
                                        Color(0xFF4B372A),
                                        Color(0xFF3A2C24)
                                    )
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                Color(0x55FFFFFF),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "BUS DJ / MIX AUTO",
                            color = Color(0xFFFFECB3),
                            fontSize = 13.sp,
                            letterSpacing = 2.sp
                        )
                    }

                    // Volume DJ
                    Spacer(Modifier.height(4.dp))
                    val djLevel = djState.masterLevel.coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Volume DJ",
                            color = sub,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(djLevel * 100).toInt()}%",
                            color = onBg,
                            fontSize = 11.sp
                        )
                    }
                    Slider(
                        value = djLevel,
                        onValueChange = { v ->
                            val clamped = v.coerceIn(0f, 1f)
                            DjEngine.setMasterVolume(clamped)
                            DjBusController.setUiLevel(clamped)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    )

                    // Platines + crossfader + GO
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Deck A
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .background(
                                        color = if (djState.activeSlot == 1)
                                            deckAGlow.copy(alpha = 0.4f)
                                        else
                                            Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .graphicsLayer {
                                            rotationZ = if (djState.activeSlot == 1) angleA else 0f
                                            val s = if (djState.activeSlot == 1) pulse else 1f
                                            scaleX = s
                                            scaleY = s
                                        }
                                        .background(Color(0xFF1F1F1F), CircleShape)
                                        .border(2.dp, deckAGlow, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(Color.Black, CircleShape)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                djState.deckATitle,
                                color = onBg,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }

                        // Centre : crossfader + GO
                        Column(
                            modifier = Modifier
                                .width(100.dp)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("X-Fade", color = sub, fontSize = 10.sp)
                            Slider(
                                value = djState.crossfadePos,
                                onValueChange = { DjEngine.setCrossfadePos(it) },
                                modifier = Modifier.height(60.dp)
                            )
                            Spacer(Modifier.height(6.dp))

                            val goEnabled =
                                (djState.activeSlot == 1 && djState.deckBUri != null) ||
                                        (djState.activeSlot == 2 && djState.deckAUri != null)

                            Button(
                                onClick = {
                                    PlaybackCoordinator.onDjStart()
                                    DjEngine.launchCrossfade()
                                },
                                enabled = goEnabled,
                                modifier = Modifier
                                    .height(40.dp)
                                    .width(80.dp)
                                    .graphicsLayer {
                                        if (goEnabled) shadowElevation = 18f
                                    },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentGo,
                                    contentColor = Color.Black,
                                    disabledContainerColor = Color(0xFF555555),
                                    disabledContentColor = Color(0xFF222222)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("GO", fontSize = 12.sp)
                            }
                        }

                        // Deck B
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .background(
                                        color = if (djState.activeSlot == 2)
                                            deckBGlow.copy(alpha = 0.4f)
                                        else
                                            Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .graphicsLayer {
                                            rotationZ = if (djState.activeSlot == 2) angleB else 0f
                                            val s = if (djState.activeSlot == 2) pulse else 1f
                                            scaleX = s
                                            scaleY = s
                                        }
                                        .background(Color(0xFF1F1F1F), CircleShape)
                                        .border(2.dp, deckBGlow, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(Color.Black, CircleShape)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                djState.deckBTitle,
                                color = onBg,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // timeline + stop
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = djState.progress.coerceIn(0f, 1f),
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp),
                            color = deckBGlow,
                            trackColor = Color(0x33E040FB)
                        )
                        Spacer(Modifier.width(10.dp))

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = if (djState.playingUri != null)
                                        Color(0xFFFF5252)
                                    else
                                        Color(0xFF444444),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0x55FFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { DjEngine.stopDj() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Arrêter DJ",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // ---------------------- File d’attente -------------------------
            if (djState.queue.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = card),
                    shape = RoundedCornerShape(14.dp),
                    elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isQueuePanelOpen = !isQueuePanelOpen },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Liste d’attente (${djState.queue.size})",
                                color = Color(0xFF81D4FA),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (isQueuePanelOpen) "▲" else "▼",
                                color = sub,
                                fontSize = 12.sp
                            )
                        }

                        if (isQueuePanelOpen) {
                            Spacer(Modifier.height(4.dp))
                            djState.queue.forEach { qItem: DjQueuedTrack ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                        .clickable {
                                            PlaybackCoordinator.onDjStart()
                                            DjEngine.playFromQueue(qItem)
                                        }
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = qItem.title,
                                        color = Color(0xFF81D4FA),
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { DjEngine.removeFromQueue(qItem) },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Stop,
                                            contentDescription = "Retirer",
                                            tint = Color(0xFFFF8A80),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 🔍 Résultats de recherche GLOBALE sous le bloc DJ + queue
            if (isSearchOpen && searchQuery.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    items(searchResults, key = { it.uri.toString() }) { entry ->
                        val uriStr = entry.uri.toString()
                        val isSelected = uriStr == djState.playingUri
                        DjTrackRow(
                            title = entry.name,
                            isPlaying = isSelected,
                            onPlay = {
                                PlaybackCoordinator.onDjStart()
                                DjEngine.selectTrackFromList(uriStr, entry.name)
                            },
                            onEnqueue = {
                                DjEngine.addToQueue(uriStr, entry.name)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ───────── Liste dossiers + titres (dossier courant) ─────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 6.dp)
            ) {
                if (currentFolderUri == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Choisis un dossier pour tes titres DJ.",
                            color = sub
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(visibleEntries, key = { it.uri.toString() }) { entry ->
                            if (entry.isDirectory) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val old = currentFolderUri
                                            if (old != null) folderStack = folderStack + old
                                            currentFolderUri = entry.uri

                                            val cached = DjFolderCache.get(entry.uri)
                                            if (cached != null) {
                                                entries = cached
                                            } else {
                                                scope.launch {
                                                    isLoading = true
                                                    val fresh: List<DjEntry> =
                                                        withContext(Dispatchers.IO) {
                                                            loadDjEntries(context, entry.uri)
                                                        }
                                                    entries = fresh
                                                    DjFolderCache.put(entry.uri, fresh)
                                                    isLoading = false
                                                }
                                            }
                                            searchQuery = ""
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = onBg,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(entry.name, color = onBg, fontSize = 13.sp)
                                }
                            } else {
                                val uriStr = entry.uri.toString()
                                val isSelected = uriStr == djState.playingUri
                                DjTrackRow(
                                    title = entry.name,
                                    isPlaying = isSelected,
                                    onPlay = {
                                        PlaybackCoordinator.onDjStart()
                                        DjEngine.selectTrackFromList(uriStr, entry.name)
                                    },
                                    onEnqueue = {
                                        DjEngine.addToQueue(uriStr, entry.name)
                                    }
                                )
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
                        CircularProgressIndicator(color = onBg)
                    }
                }
            }
        }
    }
}