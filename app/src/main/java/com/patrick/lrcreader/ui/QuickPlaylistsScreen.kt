package com.patrick.lrcreader.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.PlaylistRepository
import java.net.URLDecoder

@Composable
fun QuickPlaylistsScreen(
    modifier: Modifier = Modifier,
    onPlaySong: (String, String) -> Unit,
    refreshKey: Int,
    currentPlayingUri: String? = null,
    selectedPlaylist: String? = null,
    onSelectedPlaylistChange: (String?) -> Unit = {},
) {
    val context = LocalContext.current

    // liste de playlists dispo
    val playlists = remember(refreshKey) { PlaylistRepository.getPlaylists() }

    // playlist actuellement affichée
    var internalSelected by rememberSaveable {
        mutableStateOf<String?>(selectedPlaylist ?: playlists.firstOrNull())
    }

    // menu déroulant ouvert ?
    var showMenu by remember { mutableStateOf(false) }

    // chansons affichées (mutable pour le drag)
    val songs = remember(internalSelected, refreshKey) {
        mutableStateListOf<String>().apply {
            internalSelected?.let { addAll(PlaylistRepository.getSongsFor(it)) }
        }
    }

    // pour le drag
    val listState = rememberLazyListState()
    val rowHeight = 56.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingUri by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }

    // renommage
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    // état local du fond sonore
    var isFillerRunning by remember { mutableStateOf(FillerSoundManager.isPlaying()) }

    // si le parent change de playlist
    LaunchedEffect(selectedPlaylist) {
        if (selectedPlaylist != null) {
            internalSelected = selectedPlaylist
            songs.clear()
            songs.addAll(PlaylistRepository.getSongsFor(selectedPlaylist))
        } else if (internalSelected == null) {
            val first = playlists.firstOrNull()
            internalSelected = first
            if (first != null) {
                songs.clear()
                songs.addAll(PlaylistRepository.getSongsFor(first))
            }
        }
    }

    // si la liste de playlists change
    LaunchedEffect(playlists) {
        if (internalSelected !in playlists) {
            val first = playlists.firstOrNull()
            internalSelected = first
            onSelectedPlaylistChange(first)
            songs.clear()
            if (first != null) {
                songs.addAll(PlaylistRepository.getSongsFor(first))
            }
        }
    }

    val songColor = Color(0xFFE86FFF)
    val menuBg = Color(0xFF222222)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // ─── HEADER ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // gauche : nom de la playlist
            Box {
                Text(
                    text = internalSelected ?: "Sélectionne une playlist",
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.clickable { showMenu = true }
                )

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(menuBg)
                ) {
                    playlists.forEach { name ->
                        val isCurrent = name == internalSelected
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = name,
                                    color = if (isCurrent) songColor else Color.White,
                                    fontSize = 16.sp
                                )
                            },
                            onClick = {
                                internalSelected = name
                                onSelectedPlaylistChange(name)
                                showMenu = false
                                songs.clear()
                                songs.addAll(PlaylistRepository.getSongsFor(name))
                            }
                        )
                    }
                }
            }

            // droite : 3 icônes
            Row {
                // 1) réinitialiser cette playlist
                if (internalSelected != null) {
                    IconButton(
                        onClick = {
                            val pl = internalSelected ?: return@IconButton
                            PlaylistRepository.resetPlayedFor(pl)
                            songs.clear()
                            songs.addAll(PlaylistRepository.getSongsFor(pl))
                            onSelectedPlaylistChange(pl)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Réinitialiser la liste",
                            tint = Color(0xFFFFB74D)
                        )
                    }
                }

                // 2) toggle fond sonore
                IconButton(
                    onClick = {
                        if (isFillerRunning) {
                            FillerSoundManager.fadeOutAndStop(200)
                            isFillerRunning = false
                        } else {
                            FillerSoundManager.startIfConfigured(context)
                            isFillerRunning = FillerSoundManager.isPlaying()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isFillerRunning) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (isFillerRunning) "Arrêter le fond sonore" else "Lancer le fond sonore",
                        tint = Color(0xFFE86FFF)
                    )
                }

                // 3) son suivant du fond sonore
                IconButton(
                    onClick = {
                        FillerSoundManager.next(context)
                        // on reste en "running" si on passe au suivant
                        isFillerRunning = FillerSoundManager.isPlaying()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Son suivant",
                        tint = Color(0xFFE86FFF)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ─── LISTE DES TITRES ─────────────────────────────────────────────
        if (internalSelected == null) {
            Text("Aucune playlist.\nVa dans “Toutes” pour en créer.", color = Color.Gray)
        } else {

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState
            ) {
                itemsIndexed(songs, key = { _, item -> item }) { _, uriString ->
                    // 1) décoder proprement
                    val decoded = runCatching {
                        URLDecoder.decode(uriString, "UTF-8")
                    }.getOrElse { uriString }

                    // 2) extraire juste le nom et virer l’extension
                    val baseNameClean = decoded
                        .substringAfterLast('/')    // après le dernier /
                        .substringAfterLast(':')    // pour les content://...:123
                        .let { name ->
                            when {
                                name.endsWith(".mp3", ignoreCase = true) -> name.dropLast(4)
                                name.endsWith(".wav", ignoreCase = true) -> name.dropLast(4)
                                else -> name
                            }
                        }
                        .trim()

                    // 3) si la playlist a un titre personnalisé pour ce morceau, on le prend
                    val displayName = internalSelected?.let {
                        PlaylistRepository.getCustomTitle(it, uriString)
                    } ?: baseNameClean

                    val isPlayed = internalSelected?.let {
                        PlaylistRepository.isSongPlayed(it, uriString)
                    } ?: false

                    val isCurrentPlaying = currentPlayingUri == uriString
                    val isDraggingThis = draggingUri == uriString

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .background(if (isDraggingThis) Color(0x22FFFFFF) else Color.Transparent)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // poignée
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = "Déplacer",
                            tint = songColor,
                            modifier = Modifier
                                .size(34.dp)
                                .padding(end = 8.dp)
                                .pointerInput(songs.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingUri = uriString
                                            dragOffsetPx = 0f
                                        },
                                        onDragEnd = {
                                            draggingUri = null
                                            dragOffsetPx = 0f
                                            internalSelected?.let { pl ->
                                                PlaylistRepository.updatePlayListOrder(pl, songs.toList())
                                            }
                                        },
                                        onDragCancel = {
                                            draggingUri = null
                                            dragOffsetPx = 0f
                                        }
                                    ) { _, dragAmount ->
                                        val current = draggingUri ?: return@detectDragGesturesAfterLongPress
                                        val currentIndex = songs.indexOf(current)
                                        if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                                        dragOffsetPx += dragAmount.y

                                        // descendre
                                        if (dragOffsetPx >= rowHeightPx / 2f) {
                                            val next = currentIndex + 1
                                            if (next < songs.size) songs.swap(currentIndex, next)
                                            dragOffsetPx = 0f
                                        }
                                        // monter
                                        if (dragOffsetPx <= -rowHeightPx / 2f) {
                                            val prev = currentIndex - 1
                                            if (prev >= 0) songs.swap(currentIndex, prev)
                                            dragOffsetPx = 0f
                                        }
                                    }
                                }
                        )

                        // titre
                        Text(
                            text = displayName,
                            color = when {
                                isCurrentPlaying -> Color.White
                                isPlayed -> Color.Gray
                                else -> songColor
                            },
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    internalSelected?.let { pl ->
                                        onPlaySong(uriString, pl)
                                        onSelectedPlaylistChange(pl)
                                    }
                                }
                        )

                        // menu 3 points (le reste de ton code d’origine)
                        Box {
                            var menuOpen by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .border(
                                        width = 1.dp,
                                        color = if (isPlayed) Color.Gray else songColor,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { menuOpen = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Options",
                                    tint = if (isPlayed) Color.Gray else songColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                modifier = Modifier.background(Color(0xFF1E1E1E))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Retirer de la liste", color = Color.White) },
                                    onClick = {
                                        internalSelected?.let { pl ->
                                            PlaylistRepository.removeSongFromPlaylist(pl, uriString)
                                        }
                                        songs.remove(uriString)
                                        menuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Renommer", color = Color.White) },
                                    onClick = {
                                        renameTarget = uriString
                                        renameText = displayName
                                        menuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── dialog renommage ─────────────────────────────────────────────
    if (renameTarget != null && internalSelected != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renommer", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val targetUri = renameTarget ?: return@TextButton
                    val pl = internalSelected ?: return@TextButton
                    PlaylistRepository.renameSongInPlaylist(pl, targetUri, renameText.trim())
                    renameTarget = null
                }) {
                    Text("OK", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Annuler", color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

// utilitaire pour le drag
private fun <T> MutableList<T>.swap(i: Int, j: Int) {
    if (i == j) return
    val tmp = this[i]
    this[i] = this[j]
    this[j] = tmp
}