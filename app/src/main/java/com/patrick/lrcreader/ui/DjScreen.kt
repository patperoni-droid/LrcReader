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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import kotlin.math.min

@Composable
fun DjScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current
) {
    // 1. dossier DJ
    var djFolderUri by remember { mutableStateOf<Uri?>(DjFolderPrefs.get(context)) }
    var tracks by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    // 2. lecteurs A / B
    val scope = rememberCoroutineScope()
    var mpA by remember { mutableStateOf<MediaPlayer?>(null) }
    var mpB by remember { mutableStateOf<MediaPlayer?>(null) }
    var activeSlot by remember { mutableStateOf(0) } // 0 = rien, 1 = A joue, 2 = B joue

    // ce qui est réellement en train de jouer
    var playingUri by remember { mutableStateOf<String?>(null) }

    // ce qu’on affiche comme “sélectionné” (feedback immédiat)
    var uiSelectedUri by remember { mutableStateOf<String?>(null) }

    // timeline
    var progress by remember { mutableStateOf(0f) }
    var currentDurationMs by remember { mutableStateOf(0) }

    // menu
    var menuOpen by remember { mutableStateOf(false) }

    // choisir dossier
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
                djFolderUri = uri
                tracks = loadDjTracks(context, uri)
            }
        }
    )

    // premier chargement
    LaunchedEffect(djFolderUri) {
        djFolderUri?.let {
            tracks = loadDjTracks(context, it)
        }
    }

    // boucle timeline: tant qu’on joue, on avance
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

    // fonction de lecture DJ avec crossfade
    fun playDjTrack(uriString: String) {
        uiSelectedUri = uriString      // 👉 feedback immédiat

        scope.launch {
            val useA = activeSlot != 1  // si A ne joue pas, on prend A, sinon on prend B
            val newPlayer = if (useA) {
                mpA?.release()
                MediaPlayer().also { mpA = it }
            } else {
                mpB?.release()
                MediaPlayer().also { mpB = it }
            }

            try {
                newPlayer.setDataSource(context, Uri.parse(uriString))
                newPlayer.prepare() // sync mais OK pour ton cas
                val newDuration = newPlayer.duration
                currentDurationMs = newDuration
                newPlayer.setVolume(0f, 0f)
                newPlayer.start()

                val oldPlayer = if (useA) mpB else mpA
                val oldWasPlaying = if (useA) activeSlot == 2 else activeSlot == 1

                activeSlot = if (useA) 1 else 2
                playingUri = uriString

                // crossfade simple sur 800 ms
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

                // fin du fade → on coupe l’ancien
                if (oldWasPlaying) {
                    oldPlayer?.stop()
                    oldPlayer?.release()
                    if (useA) {
                        mpB = null
                    } else {
                        mpA = null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // en cas d’erreur on enlève le playing mais on laisse uiSelectedUri (ça montre le clic)
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

    // STOP DJ
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
        // on ne touche pas à uiSelectedUri : ça reste comme “dernier cliqué”
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(14.dp)
    ) {
        // HEADER DJ
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("DJ", color = Color.White, fontSize = 20.sp)
                Text(
                    text = if (djFolderUri != null)
                        "Dossier DJ : " + (DocumentFile.fromTreeUri(context, djFolderUri!!)?.name ?: "...")
                    else
                        "Aucun dossier DJ choisi",
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
                if (djFolderUri != null) {
                    DropdownMenuItem(
                        text = { Text("Oublier le dossier") },
                        onClick = {
                            menuOpen = false
                            DjFolderPrefs.clear(context)
                            djFolderUri = null
                            tracks = emptyList()
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

        if (djFolderUri == null) {
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
                items(tracks, key = { it.uri.toString() }) { file ->
                    val uriStr = file.uri.toString()
                    val isSelected = uriStr == uiSelectedUri   // feedback immédiat
                    val title = file.name
                        ?.removeSuffix(".mp3")
                        ?.removeSuffix(".wav")
                        ?: "Titre"

                    DjTrackRow(
                        title = title,
                        isPlaying = isSelected,
                        onClick = { playDjTrack(uriStr) }
                    )
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

/* utils */
private fun loadDjTracks(context: Context, folderUri: Uri): List<DocumentFile> {
    val doc = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
    return doc.listFiles()
        .filter {
            it.isFile && (
                    it.name?.endsWith(".mp3", true) == true ||
                            it.name?.endsWith(".wav", true) == true
                    )
        }
        .sortedBy { it.name?.lowercase() ?: "" }
}