package com.patrick.lrcreader.ui

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.content.Intent
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.DjFolderPrefs
import com.patrick.lrcreader.core.FillerSoundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/* -------------------------------------------------------------------------- */
/*  Modèle de ligne (dossier ou fichier)                                      */
/* -------------------------------------------------------------------------- */
private data class DjEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean
)

/* -------------------------------------------------------------------------- */
/*  Cache mémoire pour navigation rapide                                      */
/* -------------------------------------------------------------------------- */
private object DjFolderCache {
    private val map = mutableMapOf<String, List<DjEntry>>()
    fun get(uri: Uri): List<DjEntry>? = map[uri.toString()]
    fun put(uri: Uri, list: List<DjEntry>) { map[uri.toString()] = list }
    fun clear() = map.clear()
}

@Composable
fun DjScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current
) {
    /* --------------------- état navigation dossiers --------------------- */
    var rootFolderUri by remember { mutableStateOf<Uri?>(DjFolderPrefs.get(context)) }
    var currentFolderUri by remember { mutableStateOf<Uri?>(rootFolderUri) }
    var folderStack by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var entries by remember { mutableStateOf<List<DjEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    /* ------------------------ état lecteur DJ ---------------------------- */
    val scope = rememberCoroutineScope()

    var mpA by remember { mutableStateOf<MediaPlayer?>(null) }
    var mpB by remember { mutableStateOf<MediaPlayer?>(null) }

    // 0 = rien, 1 = A joue, 2 = B joue
    var activeSlot by remember { mutableStateOf(0) }

    // timeline
    var playingUri by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var currentDurationMs by remember { mutableStateOf(0) }

    // retour visuel liste
    var uiSelectedUri by remember { mutableStateOf<String?>(null) }

    // infos decks
    var deckATitle by remember { mutableStateOf("A vide") }
    var deckBTitle by remember { mutableStateOf("B vide") }
    var deckAUri by remember { mutableStateOf<String?>(null) }
    var deckBUri by remember { mutableStateOf<String?>(null) }

    // crossfader (0 = A, 1 = B)
    var crossfadePos by remember { mutableStateOf(0.5f) }

    // menu
    var menuOpen by remember { mutableStateOf(false) }

    /* --------------------- animation platines rondes --------------------- */
    val infinite = rememberInfiniteTransition(label = "dj-discs")
    val angleA by infinite.animateFloat(
        0f,
        360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleA"
    )
    val angleB by infinite.animateFloat(
        0f,
        360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleB"
    )

    /* -------------------------- choisir dossier -------------------------- */
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                DjFolderPrefs.save(context, uri)
                DjFolderCache.clear()

                rootFolderUri = uri
                currentFolderUri = uri
                folderStack = emptyList()

                scope.launch {
                    isLoading = true
                    val fresh = withContext(Dispatchers.IO) { loadDjEntries(context, uri) }
                    entries = fresh
                    DjFolderCache.put(uri, fresh)
                    isLoading = false
                }
            }
        }
    )

    /* -------------------------- 1er chargement --------------------------- */
    LaunchedEffect(rootFolderUri) {
        val root = rootFolderUri
        if (root != null) {
            DjFolderCache.get(root)?.let {
                entries = it
            } ?: run {
                isLoading = true
                val fresh = withContext(Dispatchers.IO) { loadDjEntries(context, root) }
                entries = fresh
                DjFolderCache.put(root, fresh)
                isLoading = false
            }
        } else {
            entries = emptyList()
        }
    }

    /* --------------------- avancer la timeline --------------------------- */
    LaunchedEffect(playingUri, activeSlot) {
        while (playingUri != null && (activeSlot == 1 || activeSlot == 2)) {
            delay(200)
            val dur = currentDurationMs
            if (dur > 0) {
                val pos = try {
                    if (activeSlot == 1) mpA?.currentPosition ?: 0 else mpB?.currentPosition ?: 0
                } catch (_: Exception) { 0 }
                progress = pos.toFloat() / dur.toFloat()
            } else {
                progress = 0f
            }
        }
        if (playingUri == null) {
            progress = 0f
            currentDurationMs = 0
        }
    }

    /* ----------------- applique la position du crossfader ---------------- */
    fun applyCrossfader() {
        val aVol = 1f - crossfadePos
        val bVol = crossfadePos
        mpA?.setVolume(aVol, aVol)
        mpB?.setVolume(bVol, bVol)
    }

    /* ----------------- sélection dans la liste --------------------------- */
    fun selectTrack(uriString: String, displayName: String) {
        uiSelectedUri = uriString

        scope.launch {
            if (activeSlot == 0) {
                // premier titre → on joue A
                FillerSoundManager.fadeOutAndStop(400)
                mpA?.release()
                val p = MediaPlayer()
                mpA = p
                try {
                    withContext(Dispatchers.IO) {
                        p.setDataSource(context, Uri.parse(uriString))
                        p.prepare()
                    }
                    currentDurationMs = p.duration
                    p.setVolume(1f - crossfadePos, 1f - crossfadePos)
                    p.start()

                    deckATitle = displayName
                    deckAUri = uriString
                    activeSlot = 1
                    playingUri = uriString
                } catch (e: Exception) {
                    e.printStackTrace()
                    mpA = null
                }
            } else {
                // il y a déjà quelque chose qui joue
                val loadIntoA = (activeSlot == 2) // si B joue, on prépare A
                if (loadIntoA) {
                    mpA?.release()
                    val p = MediaPlayer()
                    mpA = p
                    try {
                        withContext(Dispatchers.IO) {
                            p.setDataSource(context, Uri.parse(uriString))
                            p.prepare()
                        }
                        p.setVolume(0f, 0f)
                        deckATitle = displayName
                        deckAUri = uriString
                    } catch (e: Exception) {
                        e.printStackTrace()
                        mpA = null
                    }
                } else {
                    mpB?.release()
                    val p = MediaPlayer()
                    mpB = p
                    try {
                        withContext(Dispatchers.IO) {
                            p.setDataSource(context, Uri.parse(uriString))
                            p.prepare()
                        }
                        p.setVolume(0f, 0f)
                        deckBTitle = displayName
                        deckBUri = uriString
                    } catch (e: Exception) {
                        e.printStackTrace()
                        mpB = null
                    }
                }
            }
        }
    }

    /* --------------------- bouton Lancer (2 sens) ------------------------ */
    fun launchCrossfade() {
        scope.launch {
            // cas 1 : A joue, B prêt → A -> B
            if (activeSlot == 1 && mpA != null && mpB != null) {
                val playerA = mpA!!
                val playerB = mpB!!

                if (!playerB.isPlaying) {
                    try { playerB.seekTo(0); playerB.start() } catch (_: Exception) {}
                }

                val fadeSteps = 20
                val targetA = 1f - crossfadePos
                val targetB = crossfadePos

                repeat(fadeSteps) { i ->
                    val t = (i + 1) / fadeSteps.toFloat()
                    val volA = targetA * (1f - t)
                    val volB = targetB * t
                    playerA.setVolume(max(0f, volA), max(0f, volA))
                    playerB.setVolume(max(0f, volB), max(0f, volB))
                    delay(50)
                }

                try { playerA.stop() } catch (_: Exception) {}
                playerA.release()
                mpA = null

                activeSlot = 2
                playingUri = deckBUri
                currentDurationMs = try { playerB.duration } catch (_: Exception) { 0 }
                return@launch
            }

            // cas 2 : B joue, A prêt → B -> A
            if (activeSlot == 2 && mpA != null && mpB != null) {
                val playerA = mpA!!
                val playerB = mpB!!

                if (!playerA.isPlaying) {
                    try { playerA.seekTo(0); playerA.start() } catch (_: Exception) {}
                }

                val fadeSteps = 20
                val targetA = 1f - crossfadePos
                val targetB = crossfadePos

                repeat(fadeSteps) { i ->
                    val t = (i + 1) / fadeSteps.toFloat()
                    val volB = targetB * (1f - t)
                    val volA = targetA * t
                    playerB.setVolume(max(0f, volB), max(0f, volB))
                    playerA.setVolume(max(0f, volA), max(0f, volA))
                    delay(50)
                }

                try { playerB.stop() } catch (_: Exception) {}
                playerB.release()
                mpB = null

                activeSlot = 1
                playingUri = deckAUri
                currentDurationMs = try { playerA.duration } catch (_: Exception) { 0 }
            }
        }
    }

    /* ----------------------------- STOP DJ ----------------------------- */
    fun stopDj() {
        mpA?.stop(); mpA?.release(); mpA = null
        mpB?.stop(); mpB?.release(); mpB = null
        activeSlot = 0
        playingUri = null
        progress = 0f
        currentDurationMs = 0
    }

    /* ============================== UI ============================== */
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
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
                                val fresh = withContext(Dispatchers.IO) {
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
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Text("DJ", color = Color.White, fontSize = 20.sp)
                Text(
                    text = currentFolderUri?.let {
                        DocumentFile.fromTreeUri(context, it)?.name ?: "…"
                    } ?: "Aucun dossier DJ choisi",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White
                )
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
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
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // PLATINES + CROSSFADER + BOUTON
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
                        .size(70.dp)
                        .graphicsLayer { rotationZ = if (activeSlot == 1) angleA else 0f }
                        .background(Color(0xFF1F1F1F), CircleShape)
                        .border(2.dp, Color(0xFF4CAF50), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color.Black, CircleShape)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(deckATitle, color = Color.White, fontSize = 11.sp, maxLines = 1)
            }

            // centre
            Column(
                modifier = Modifier
                    .width(90.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("X-Fade", color = Color.Gray, fontSize = 10.sp)
                Slider(
                    value = crossfadePos,
                    onValueChange = {
                        crossfadePos = it
                        applyCrossfader()
                    },
                    modifier = Modifier.height(60.dp),
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { launchCrossfade() },
                    enabled = (activeSlot == 1 && mpB != null) || (activeSlot == 2 && mpA != null)
                ) {
                    Text("Lancer", fontSize = 10.sp)
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
                        .size(70.dp)
                        .graphicsLayer { rotationZ = if (activeSlot == 2) angleB else 0f }
                        .background(Color(0xFF1F1F1F), CircleShape)
                        .border(2.dp, Color(0xFFE040FB), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color.Black, CircleShape)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(deckBTitle, color = Color.White, fontSize = 11.sp, maxLines = 1)
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
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp),
                color = Color(0xFFE040FB),
                trackColor = Color(0x33E040FB)
            )
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = { stopDj() }) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Arrêter",
                    tint = if (playingUri != null) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // contenu dossier
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (currentFolderUri == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Choisis un dossier pour tes titres DJ.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.uri.toString() }) { entry ->
                        if (entry.isDirectory) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val old = currentFolderUri
                                        if (old != null) {
                                            folderStack = folderStack + old
                                        }
                                        currentFolderUri = entry.uri

                                        val cached = DjFolderCache.get(entry.uri)
                                        if (cached != null) {
                                            entries = cached
                                        } else {
                                            scope.launch {
                                                isLoading = true
                                                val fresh = withContext(Dispatchers.IO) {
                                                    loadDjEntries(context, entry.uri)
                                                }
                                                entries = fresh
                                                DjFolderCache.put(entry.uri, fresh)
                                                isLoading = false
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(entry.name, color = Color.White)
                            }
                        } else {
                            val uriStr = entry.uri.toString()
                            val isSelected = uriStr == uiSelectedUri
                            DjTrackRow(
                                title = entry.name,
                                isPlaying = isSelected,
                                onClick = { selectTrack(uriStr, entry.name) }
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
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }

    // nettoyage
    DisposableEffect(Unit) {
        onDispose {
            mpA?.release()
            mpB?.release()
        }
    }
}

@Composable
private fun DjTrackRow(
    title: String,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (isPlaying) Color(0xFFE040FB) else Color.White,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(22.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (isPlaying) Color(0xFFE040FB) else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Lecture d’un dossier (à faire en IO)                                      */
/* -------------------------------------------------------------------------- */
private fun loadDjEntries(context: Context, folderUri: Uri): List<DjEntry> {
    val doc = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
    val all = doc.listFiles()

    val folders = all
        .filter { it.isDirectory }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map { DjEntry(uri = it.uri, name = it.name ?: "Dossier", isDirectory = true) }

    val audio = all
        .filter {
            it.isFile && (
                    it.name?.endsWith(".mp3", true) == true ||
                            it.name?.endsWith(".wav", true) == true
                    )
        }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map {
            val clean = (it.name ?: "titre")
                .removeSuffix(".mp3")
                .removeSuffix(".MP3")
                .removeSuffix(".wav")
                .removeSuffix(".WAV")
            DjEntry(uri = it.uri, name = clean, isDirectory = false)
        }

    return folders + audio
}