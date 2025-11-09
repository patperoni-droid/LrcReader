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
import androidx.compose.material.icons.filled.Palette
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

    // playlists dispo
    val playlists = remember(refreshKey) { PlaylistRepository.getPlaylists() }

    // playlist affichée
    var internalSelected by rememberSaveable {
        mutableStateOf<String?>(selectedPlaylist ?: playlists.firstOrNull())
    }

    // liste mutables de titres (on la garde)
    val songs = remember { mutableStateListOf<String>() }

    // couleur courante (on mettra la vraie depuis les prefs)
    var currentListColor by remember { mutableStateOf(Color(0xFFE86FFF)) }
    var colorMenuOpen by remember { mutableStateOf(false) }

    // menus
    var showMenu by remember { mutableStateOf(false) }

    // drag
    val listState = rememberLazyListState()
    val rowHeight = 56.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingUri by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }

    // renommage
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    // fond sonore
    var isFillerRunning by remember { mutableStateOf(FillerSoundManager.isPlaying()) }

    // quand la playlist change → on recharge les titres + on relit la couleur dans les prefs
    LaunchedEffect(internalSelected, refreshKey) {
        songs.clear()
        val pl = internalSelected
        if (pl != null) {
            songs.addAll(PlaylistRepository.getSongsFor(pl))

            // 👇 on relit la couleur sauvegardée pour cette playlist
            val savedColor = loadPlaylistColor(context, pl)
            currentListColor = savedColor ?: Color(0xFFE86FFF)
        }
    }

    // si le parent force une playlist
    LaunchedEffect(selectedPlaylist) {
        if (selectedPlaylist != null) {
            internalSelected = selectedPlaylist
            songs.clear()
            songs.addAll(PlaylistRepository.getSongsFor(selectedPlaylist))
            val savedColor = loadPlaylistColor(context, selectedPlaylist)
            currentListColor = savedColor ?: Color(0xFFE86FFF)
        }
    }

    // si la liste des playlists change (suppression, renommer…)
    LaunchedEffect(playlists) {
        if (internalSelected !in playlists) {
            val first = playlists.firstOrNull()
            internalSelected = first
            songs.clear()
            if (first != null) {
                songs.addAll(PlaylistRepository.getSongsFor(first))
                val savedColor = loadPlaylistColor(context, first)
                currentListColor = savedColor ?: Color(0xFFE86FFF)
                onSelectedPlaylistChange(first)
            }
        }
    }

    val menuBg = Color(0xFF222222)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // ─── HEADER ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // gauche : titre + menu
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
                                    color = if (isCurrent) currentListColor else Color.White,
                                    fontSize = 16.sp
                                )
                            },
                            onClick = {
                                internalSelected = name
                                onSelectedPlaylistChange(name)
                                showMenu = false
                                // le LaunchedEffect se charge du reste
                            }
                        )
                    }
                }
            }

            // droite : palette + actions
            Row(verticalAlignment = Alignment.CenterVertically) {

                // Palette de couleurs
                Box {
                    IconButton(onClick = { colorMenuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = "Couleur de la liste",
                            tint = currentListColor
                        )
                    }
                    DropdownMenu(
                        expanded = colorMenuOpen,
                        onDismissRequest = { colorMenuOpen = false },
                        modifier = Modifier.background(Color(0xFF1E1E1E))
                    ) {
                        val colors = listOf(
                            Color.White,
                            Color(0xFFE86FFF),
                            Color(0xFF6AC5FE),
                            Color(0xFFFFB74D),
                            Color(0xFF81C784),
                            Color(0xFFFF6F91),
                        )
                        colors.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("") },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(c, RoundedCornerShape(999.dp))
                                            .border(1.dp, Color.DarkGray, RoundedCornerShape(999.dp))
                                    )
                                },
                                onClick = {
                                    currentListColor = c
                                    // 👇 on enregistre pour cette playlist
                                    internalSelected?.let { pl ->
                                        savePlaylistColor(context, pl, c)
                                    }
                                    colorMenuOpen = false
                                }
                            )
                        }
                    }
                }

                // reset played
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

                // fond sonore on/off
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
                        contentDescription = null,
                        tint = currentListColor
                    )
                }

                // son suivant
                IconButton(
                    onClick = {
                        FillerSoundManager.next(context)
                        isFillerRunning = FillerSoundManager.isPlaying()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = null,
                        tint = currentListColor
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ─── LISTE DES TITRES ───────────────────────────────
        if (internalSelected == null) {
            Text("Aucune playlist.\nVa dans “Toutes” pour en créer.", color = Color.Gray)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState
            ) {
                itemsIndexed(songs, key = { _, item -> item }) { _, uriString ->
                    val decoded = runCatching {
                        URLDecoder.decode(uriString, "UTF-8")
                    }.getOrElse { uriString }

                    val baseNameClean = decoded
                        .substringAfterLast('/')
                        .substringAfterLast(':')
                        .let { name ->
                            when {
                                name.endsWith(".mp3", ignoreCase = true) -> name.dropLast(4)
                                name.endsWith(".wav", ignoreCase = true) -> name.dropLast(4)
                                else -> name
                            }
                        }
                        .trim()

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
                            tint = currentListColor,
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

                                        if (dragOffsetPx >= rowHeightPx / 2f) {
                                            val next = currentIndex + 1
                                            if (next < songs.size) songs.swap(currentIndex, next)
                                            dragOffsetPx = 0f
                                        }
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
                            text = displayName.uppercase(),
                            color = when {
                                isCurrentPlaying -> Color.White
                                isPlayed -> Color.Gray
                                else -> currentListColor
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

                        // menu 3 points
                        Box {
                            var menuOpen by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .border(
                                        width = 1.dp,
                                        color = if (isPlayed) Color.Gray else currentListColor,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { menuOpen = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Options",
                                    tint = if (isPlayed) Color.Gray else currentListColor,
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

    // dialog renommage
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

/* ───────────── PERSO COULEURS PAR PLAYLIST ───────────── */

private const val PREFS_PLAYLIST_COLORS = "playlist_colors"

private fun savePlaylistColor(context: Context, playlist: String, color: Color) {
    val prefs = context.getSharedPreferences(PREFS_PLAYLIST_COLORS, Context.MODE_PRIVATE)
    // on stocke la valeur brute du Color (ULong → String)
    prefs.edit().putString(playlist, color.value.toString()).apply()
}

private fun loadPlaylistColor(context: Context, playlist: String): Color? {
    val prefs = context.getSharedPreferences(PREFS_PLAYLIST_COLORS, Context.MODE_PRIVATE)
    val raw = prefs.getString(playlist, null) ?: return null
    return try {
        // on reconstruit le Color
        Color(raw.toULong())
    } catch (e: Exception) {
        null
    }
}