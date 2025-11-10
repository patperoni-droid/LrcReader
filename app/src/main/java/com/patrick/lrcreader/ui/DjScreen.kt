package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.DjFolderPrefs

@Composable
fun DjScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    onPlayTrack: (String) -> Unit,
    onStop: () -> Unit,               // 👈 nouvelle lambda pour stopper
    currentUri: String?,
    progress: Float
) {
    // dossier DJ déjà enregistré ?
    var djFolderUri by remember { mutableStateOf<Uri?>(DjFolderPrefs.get(context)) }
    var tracks by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    // menu (3 points)
    var menuOpen by remember { mutableStateOf(false) }

    // pour choisir le dossier
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

        // ───── TIMELINE + bouton STOP ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
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

            IconButton(
                onClick = onStop,
                enabled = currentUri != null,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = if (currentUri != null) Color(0xFFFF6F91) else Color.Gray
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ───── LISTE DES TITRES DJ ──────────────────────────────
        if (djFolderUri == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Choisis un dossier pour tes titres DJ.", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(tracks, key = { it.uri.toString() }) { file ->
                    val fileUriString = file.uri.toString()
                    val isPlaying = currentUri != null && currentUri == fileUriString

                    DjTrackRow(
                        title = file.name
                            ?.removeSuffix(".mp3")
                            ?.removeSuffix(".wav")
                            ?: "Titre",
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(fileUriString) }
                    )
                }
            }
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

/* utils locale pour lire le contenu du dossier DJ */
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