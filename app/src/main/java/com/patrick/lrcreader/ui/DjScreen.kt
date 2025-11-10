package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent     // 👈 manquait
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.DjFolderPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

// petit cache en mémoire comme pour la bibliothèque
private object DjFolderCache {
    private val map = mutableMapOf<String, List<DjEntry>>()

    fun get(uri: Uri): List<DjEntry>? = map[uri.toString()]

    fun put(uri: Uri, list: List<DjEntry>) {
        map[uri.toString()] = list
    }

    fun clear() = map.clear()
}

// ce qu’on affiche dans la liste DJ (dossier ou fichier)
private data class DjEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean
)

@Composable
fun DjScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current
) {
    // dossier DJ enregistré
    var rootDjFolder by remember { mutableStateOf<Uri?>(DjFolderPrefs.get(context)) }

    // dossier courant + pile pour revenir en arrière vite
    var currentFolder by remember { mutableStateOf<Uri?>(rootDjFolder) }
    var folderStack by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // ce qu’on affiche
    var entries by remember { mutableStateOf<List<DjEntry>>(emptyList()) }

    // media players
    val scope = rememberCoroutineScope()
    var mpA by remember { mutableStateOf<MediaPlayer?>(null) }
    var mpB by remember { mutableStateOf<MediaPlayer?>(null) }
    var activeSlot by remember { mutableStateOf(0) } // 0 rien, 1 = A joue, 2 = B joue

    var playingUri by remember { mutableStateOf<String?>(null) }
    var uiSelectedUri by remember { mutableStateOf<String?>(null) }

    // timeline
    var progress by remember { mutableStateOf(0f) }
    var currentDurationMs by remember { mutableStateOf(0) }

    // menu
    var menuOpen by remember { mutableStateOf(false) }

    // loader (si jamais on doit relire un gros dossier)
    var isLoading by remember { mutableStateOf(false) }

    // picker
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
                rootDjFolder = uri
                currentFolder = uri
                folderStack = emptyList()

                val cached = DjFolderCache.get(uri)
                if (cached != null) {
                    entries = cached
                } else {
                    val fresh = loadDjEntries(context, uri)
                    entries = fresh
                    DjFolderCache.put(uri, fresh)
                }
            }
        }
    )

    // premier chargement
    LaunchedEffect(rootDjFolder) {
        val root = rootDjFolder
        if (root != null) {
            val cached = DjFolderCache.get(root)
            entries = if (cached != null) {
                cached
            } else {
                val fresh = loadDjEntries(context, root)
                DjFolderCache.put(root, fresh)
                fresh
            }
        } else {
            entries = emptyList()
        }
    }

    // timeline
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

    // play DJ
    fun playDjTrack(uriString: String) {
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

                activeSlot = if (useA) 1 else 2
                playingUri = uriString

                // petit crossfade
                val fadeSteps = 16
                repeat(fadeSteps) { step ->
                    val t = (step + 1) / fadeSteps.toFloat()
                    val volIn = t
                    val volOut = 1f - t

                    newPlayer.setVolume(volIn, volIn)
                    if (oldWasPlaying) {
                        oldPlayer?.setVolume(max(0f, volOut), max(0f, volOut))
                    }
                    delay(50)
                }

                if (oldWasPlaying) {
                    oldPlayer?.stop()
                    oldPlayer?.release()
                    if (useA) mpB = null else mpA = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                playingUri = null
                currentDurationMs = 0
                progress = 0f
                if (useA) {
                    mpA?.release()
                    mpA = null
                } else {
                    mpB?.release()
                    mpB = null
                }
            }
        }
    }

    fun stopDj() {
        mpA?.stop()
        mpA?.release()
        mpA = null
        mpB?.stop()
        mpB?.release()
        mpB = null
        playingUri = null
        activeSlot = 0
        progress = 0f
        currentDurationMs = 0
    }

    // navigation dans les dossiers
    fun openFolder(uri: Uri) {
        val old = currentFolder
        if (old != null) {
            folderStack = folderStack + old
        }
        currentFolder = uri

        val cached = DjFolderCache.get(uri)
        if (cached != null) {
            entries = cached
        } else {
            isLoading = true
            scope.launch {
                val fresh = loadDjEntries(context, uri)
                DjFolderCache.put(uri, fresh)
                entries = fresh
                isLoading = false
            }
        }
    }

    fun goBackFolder() {
        val newStack = folderStack.dropLast(1)
        val parent = newStack.lastOrNull() ?: rootDjFolder
        currentFolder = parent
        entries = parent?.let { uri ->
            DjFolderCache.get(uri) ?: loadDjEntries(context, uri).also { DjFolderCache.put(uri, it) }
        } ?: emptyList()
        folderStack = newStack
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
            Column(Modifier.weight(1f)) {
                Text("DJ", color = Color.White, fontSize = 20.sp)
                Text(
                    text = when {
                        currentFolder != null -> {
                            DocumentFile.fromTreeUri(context, currentFolder!!)?.name ?: "…"
                        }
                        else -> "Aucun dossier DJ choisi"
                    },
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            // back rien que si on est dans un sous dossier
            if (folderStack.isNotEmpty()) {
                IconButton(onClick = { goBackFolder() }) {
                    Icon(
                        imageVector = Icons.Default.Folder, // tu peux mettre une vraie icône de retour
                        contentDescription = "Retour dossier",
                        tint = Color.White
                    )
                }
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
                if (rootDjFolder != null) {
                    DropdownMenuItem(
                        text = { Text("Oublier le dossier") },
                        onClick = {
                            menuOpen = false
                            DjFolderPrefs.clear(context)
                            rootDjFolder = null
                            currentFolder = null
                            folderStack = emptyList()
                            entries = emptyList()
                            DjFolderCache.clear()
                        }
                    )
                }
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

        if (currentFolder == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Choisis un dossier pour tes titres DJ.",
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(entries, key = { it.uri.toString() }) { entry ->
                    if (entry.isDirectory) {
                        // dossier
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clickable { openFolder(entry.uri) }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = entry.name,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        val uriStr = entry.uri.toString()
                        val isSelected = uriStr == uiSelectedUri
                        DjTrackRow(
                            title = entry.name,
                            isPlaying = isSelected,
                            onClick = { playDjTrack(uriStr) }
                        )
                    }
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

/* ----- utils : lit un dossier DJ et renvoie dossiers + mp3/wav ----- */
private fun loadDjEntries(context: Context, folderUri: Uri): List<DjEntry> {
    val doc = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
    val all = doc.listFiles()

    val folders = all
        .filter { it.isDirectory }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map { DjEntry(uri = it.uri, name = it.name ?: "Dossier", isDirectory = true) }

    val audio = all
        .filter {
            it.isFile && (it.name?.endsWith(".mp3", true) == true ||
                    it.name?.endsWith(".wav", true) == true)
        }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map {
            val clean = (it.name ?: "Titre")
                .removeSuffix(".mp3")
                .removeSuffix(".MP3")
                .removeSuffix(".wav")
                .removeSuffix(".WAV")
            DjEntry(uri = it.uri, name = clean, isDirectory = false)
        }

    return folders + audio
}