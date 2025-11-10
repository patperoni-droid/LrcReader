package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.ArrowBack
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
import com.patrick.lrcreader.core.FillerSoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

// petit modèle pour la liste
private data class DjEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean
)

// cache comme pour la bibliothèque
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
    val savedRoot = remember { DjFolderPrefs.get(context) }

    // racine DJ choisie
    var djRootUri by remember { mutableStateOf<Uri?>(savedRoot) }

    // dossier courant (peut être la racine ou un sous-dossier)
    var currentFolderUri by remember { mutableStateOf<Uri?>(savedRoot) }

    // pile de navigation
    var folderStack by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // contenu du dossier courant
    var entries by remember { mutableStateOf<List<DjEntry>>(emptyList()) }

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

    // menu
    var menuOpen by remember { mutableStateOf(false) }

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
                } catch (_: Exception) { }
                DjFolderPrefs.save(context, uri)
                djRootUri = uri
                currentFolderUri = uri
                folderStack = emptyList()
                val fresh = loadDjEntries(context, uri)
                entries = fresh
                DjFolderCache.put(uri, fresh)
            }
        }
    )

    // premier chargement
    LaunchedEffect(djRootUri) {
        val root = djRootUri
        if (root != null) {
            val cached = DjFolderCache.get(root)
            if (cached != null) {
                entries = cached
            } else {
                val fresh = loadDjEntries(context, root)
                entries = fresh
                DjFolderCache.put(root, fresh)
            }
        } else {
            entries = emptyList()
            currentFolderUri = null
            folderStack = emptyList()
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

    // lecture DJ
    fun playDjTrack(uriString: String) {
        // dès qu'on lance un titre DJ → stop fond sonore
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

                // on coupe l'ancien une fois le fondu fini
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
            // bouton retour seulement si on est dans un sous-dossier
            if (folderStack.isNotEmpty()) {
                IconButton(
                    onClick = {
                        // retour : on récupère le dernier dossier de la pile
                        val newStack = folderStack.dropLast(1)
                        val parent = newStack.lastOrNull() ?: djRootUri
                        currentFolderUri = parent
                        entries = parent?.let { uri ->
                            DjFolderCache.get(uri) ?: loadDjEntries(context, uri).also {
                                DjFolderCache.put(uri, it)
                            }
                        } ?: emptyList()
                        folderStack = newStack
                    }
                ) {
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
                    text = when {
                        currentFolderUri != null -> {
                            DocumentFile.fromTreeUri(context, currentFolderUri!!)?.name ?: "…"
                        }
                        else -> "Aucun dossier DJ choisi"
                    },
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
                if (djRootUri != null) {
                    DropdownMenuItem(
                        text = { Text("Oublier le dossier") },
                        onClick = {
                            menuOpen = false
                            DjFolderPrefs.clear(context)
                            DjFolderCache.clear()
                            djRootUri = null
                            currentFolderUri = null
                            entries = emptyList()
                            folderStack = emptyList()
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

        if (djRootUri == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Choisis un dossier pour tes titres DJ.", color = Color.Gray)
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
                                        val fresh = loadDjEntries(context, entry.uri)
                                        entries = fresh
                                        DjFolderCache.put(entry.uri, fresh)
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(entry.name, color = Color.White, fontSize = 13.sp)
                        }
                    } else {
                        // fichier audio
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

/* charge dossiers + mp3/wav */
private fun loadDjEntries(context: Context, folderUri: Uri): List<DjEntry> {
    val doc = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
    val all = doc.listFiles()

    val folders = all
        .filter { it.isDirectory }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map {
            DjEntry(
                uri = it.uri,
                name = it.name ?: "Dossier",
                isDirectory = true
            )
        }

    val audio = all
        .filter { file ->
            file.isFile && file.name?.let { n ->
                n.endsWith(".mp3", true) || n.endsWith(".wav", true)
            } == true
        }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map {
            val clean = (it.name ?: "inconnu")
                .removeSuffix(".mp3")
                .removeSuffix(".MP3")
                .removeSuffix(".wav")
                .removeSuffix(".WAV")
            DjEntry(
                uri = it.uri,
                name = clean,
                isDirectory = false
            )
        }

    return folders + audio
}