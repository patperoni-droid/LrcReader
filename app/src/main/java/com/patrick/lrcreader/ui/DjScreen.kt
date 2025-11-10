package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

// ---------------------------------------------------------------------
// Modèle qu’on affiche dans la liste (dossier ou fichier audio)
// ---------------------------------------------------------------------
private data class DjEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean
)

// ---------------------------------------------------------------------
// Petit cache en mémoire (clé = uri.toString())
// ---------------------------------------------------------------------
private object DjFolderCache {
    private val map = mutableMapOf<String, List<DjEntry>>()
    fun get(uri: Uri): List<DjEntry>? = map[uri.toString()]
    fun put(uri: Uri, list: List<DjEntry>) {
        map[uri.toString()] = list
    }
    fun clear() = map.clear()
}

@Composable
fun DjScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current
) {
    // dossier racine DJ mémorisé
    var rootFolderUri by remember { mutableStateOf<Uri?>(DjFolderPrefs.get(context)) }

    // dossier courant
    var currentFolderUri by remember { mutableStateOf<Uri?>(rootFolderUri) }
    var folderStack by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var entries by remember { mutableStateOf<List<DjEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // 2 lecteurs
    val scope = rememberCoroutineScope()
    var mpA by remember { mutableStateOf<MediaPlayer?>(null) }
    var mpB by remember { mutableStateOf<MediaPlayer?>(null) }
    var activeSlot by remember { mutableStateOf(0) } // 0 rien, 1 = A, 2 = B

    // ce qui joue vraiment
    var playingUri by remember { mutableStateOf<String?>(null) }
    // ce qu'on surligne tout de suite
    var uiSelectedUri by remember { mutableStateOf<String?>(null) }

    // timeline
    var progress by remember { mutableStateOf(0f) }
    var currentDurationMs by remember { mutableStateOf(0) }

    // titres pour les deux decks
    var deckATitle by remember { mutableStateOf("A vide") }
    var deckBTitle by remember { mutableStateOf("B vide") }

    // crossfader
    var crossfadePos by remember { mutableStateOf(0.5f) }

    // menu
    var menuOpen by remember { mutableStateOf(false) }

    // animation rotation platines
    val infinite = rememberInfiniteTransition(label = "dj-discs")
    val angleA by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleA"
    )
    val angleB by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleB"
    )

    // choisir dossier DJ
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
                    val fresh = loadDjEntries(context, uri)
                    entries = fresh
                    DjFolderCache.put(uri, fresh)
                    isLoading = false
                }
            }
        }
    )

    // premier chargement
    LaunchedEffect(rootFolderUri) {
        val root = rootFolderUri
        if (root != null) {
            val cached = DjFolderCache.get(root)
            if (cached != null) {
                entries = cached
            } else {
                isLoading = true
                val fresh = loadDjEntries(context, root)
                entries = fresh
                DjFolderCache.put(root, fresh)
                isLoading = false
            }
        } else {
            entries = emptyList()
        }
    }

    // faire avancer la timeline
    LaunchedEffect(playingUri, activeSlot) {
        while (playingUri != null && (activeSlot == 1 || activeSlot == 2)) {
            delay(200)
            val dur = currentDurationMs
            if (dur > 0) {
                val pos = try {
                    if (activeSlot == 1) mpA?.currentPosition ?: 0 else mpB?.currentPosition ?: 0
                } catch (_: Exception) {
                    0
                }
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

    // applique la position du crossfader aux 2 players existants
    fun applyCrossfader() {
        val aVol = 1f - crossfadePos
        val bVol = crossfadePos
        mpA?.setVolume(aVol, aVol)
        mpB?.setVolume(bVol, bVol)
    }

    // lecture DJ
    fun playDjTrack(uriString: String, displayName: String) {
        // quand on lance un titre DJ → on coupe le fond sonore
        FillerSoundManager.fadeOutAndStop(400)

        uiSelectedUri = uriString
        scope.launch {
            val useA = activeSlot != 1
            val newPlayer = if (useA) {
                mpA?.release()
                MediaPlayer().also { mpA = it }
            } else {
                mpB?.release()
                MediaPlayer().also { mpB = it }
            }

            try {
                newPlayer.setDataSource(context, Uri.parse(uriString))
                newPlayer.prepare()
                currentDurationMs = newPlayer.duration
                newPlayer.setVolume(0f, 0f)
                newPlayer.start()

                val oldPlayer = if (useA) mpB else mpA
                val oldWasPlaying = if (useA) activeSlot == 2 else activeSlot == 1

                if (useA) deckATitle = displayName else deckBTitle = displayName

                activeSlot = if (useA) 1 else 2
                playingUri = uriString

                // crossfade 800 ms
                val fadeSteps = 16
                repeat(fadeSteps) { step ->
                    val t = (step + 1) / fadeSteps.toFloat()
                    val targetA = 1f - crossfadePos
                    val targetB = crossfadePos

                    if (useA) {
                        val volA = targetA * t
                        newPlayer.setVolume(volA, volA)
                        if (oldWasPlaying) {
                            val volB = targetB * (1f - t)
                            oldPlayer?.setVolume(max(0f, volB), max(0f, volB))
                        }
                    } else {
                        val volB = targetB * t
                        newPlayer.setVolume(volB, volB)
                        if (oldWasPlaying) {
                            val volA = targetA * (1f - t)
                            oldPlayer?.setVolume(max(0f, volA), max(0f, volA))
                        }
                    }

                    delay(50)
                }

                if (oldWasPlaying) {
                    oldPlayer?.stop()
                    oldPlayer?.release()
                    if (useA) mpB = null else mpA = null
                }

                applyCrossfader()
            } catch (e: Exception) {
                e.printStackTrace()
                playingUri = null
                currentDurationMs = 0
                progress = 0f
                if (useA) {
                    mpA?.release(); mpA = null
                } else {
                    mpB?.release(); mpB = null
                }
            }
        }
    }

    // stop
    fun stopDj() {
        mpA?.stop(); mpA?.release(); mpA = null
        mpB?.stop(); mpB?.release(); mpB = null
        playingUri = null
        activeSlot = 0
        progress = 0f
        currentDurationMs = 0
    }

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
                        val cached = DjFolderCache.get(parentUri)
                        if (cached != null) {
                            entries = cached
                        } else {
                            scope.launch {
                                isLoading = true
                                val fresh = loadDjEntries(context, parentUri)
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

        // ───────────── Zone double platine ronde ─────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
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
                        .graphicsLayer {
                            rotationZ = if (activeSlot == 1) angleA else 0f
                        }
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
                Text(
                    text = deckATitle,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            // Crossfader au milieu
            Column(
                modifier = Modifier
                    .width(70.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("X-Fade", color = Color.Gray, fontSize = 10.sp)
                Slider(
                    value = crossfadePos,
                    onValueChange = {
                        crossfadePos = it
                        applyCrossfader()
                    },
                    modifier = Modifier.height(80.dp),
                )
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
                        .graphicsLayer {
                            rotationZ = if (activeSlot == 2) angleB else 0f
                        }
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
                Text(
                    text = deckBTitle,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // timeline générale + stop
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
                                                val fresh = loadDjEntries(context, entry.uri)
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
                                onClick = { playDjTrack(uriStr, entry.name) }
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

/* util : lit le dossier DJ et renvoie dossiers + fichiers audio */
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