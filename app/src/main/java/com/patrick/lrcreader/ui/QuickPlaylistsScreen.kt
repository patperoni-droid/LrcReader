package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.PlaylistRepository
import java.net.URLDecoder

@Composable
fun QuickPlaylistsScreen(
    modifier: Modifier = Modifier,
    onPlaySong: (String, String) -> Unit,
    refreshKey: Int
) {
    val playlists = remember(refreshKey) { PlaylistRepository.getPlaylists() }

    var selectedPlaylist by remember(refreshKey) {
        mutableStateOf<String?>(playlists.firstOrNull())
    }
    var showMenu by remember { mutableStateOf(false) }

    // liste mutable pour bouger / enlever les titres
    val songs = remember(selectedPlaylist, refreshKey) {
        mutableStateListOf<String>().apply {
            selectedPlaylist?.let { addAll(PlaylistRepository.getSongsFor(it)) }
        }
    }

    val songColor = Color(0xFFE86FFF)
    val menuBg = Color(0xFF222222)

    val listState = rememberLazyListState()

    val rowHeight = 56.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }

    var draggingUri by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // header playlist
        Box {
            Text(
                text = selectedPlaylist ?: "Sélectionne une playlist",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier
                    .clickable { showMenu = true }
                    .padding(bottom = 8.dp)
            )

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(menuBg)
            ) {
                playlists.forEach { name ->
                    val isCurrent = name == selectedPlaylist
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = name,
                                color = if (isCurrent) songColor else Color.White,
                                fontSize = 16.sp
                            )
                        },
                        onClick = {
                            selectedPlaylist = name
                            showMenu = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (selectedPlaylist == null) {
            Text("Aucune playlist.\nVa dans “Toutes” pour en créer.", color = Color.Gray)
        } else {
            Text("Chansons de “$selectedPlaylist”", color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState
            ) {
                itemsIndexed(
                    items = songs,
                    key = { _, item -> item }
                ) { _, uriString ->
                    val displayName = try {
                        URLDecoder.decode(uriString, "UTF-8").substringAfterLast('/')
                    } catch (e: Exception) {
                        uriString
                    }

                    val isPlayed = selectedPlaylist?.let {
                        PlaylistRepository.isSongPlayed(it, uriString)
                    } ?: false

                    val isDraggingThis = draggingUri == uriString

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .background(
                                if (isDraggingThis) Color(0x22FFFFFF) else Color.Transparent
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // poignée drag – même couleur que le titre
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
                                            // ➜ on sauvegarde l'ordre dans le repo
                                            selectedPlaylist?.let { pl ->
                                                PlaylistRepository.updatePlayListOrder(
                                                    pl,
                                                    songs.toList()
                                                )
                                            }
                                            draggingUri = null
                                            dragOffsetPx = 0f
                                        },
                                        onDragCancel = {
                                            draggingUri = null
                                            dragOffsetPx = 0f
                                        }
                                    ) { _, dragAmount ->
                                        val currentUri = draggingUri ?: return@detectDragGesturesAfterLongPress
                                        val currentIndex = songs.indexOf(currentUri)
                                        if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                                        dragOffsetPx += dragAmount.y

                                        if (dragOffsetPx >= rowHeightPx / 2f) {
                                            val next = currentIndex + 1
                                            if (next < songs.size) {
                                                songs.swap(currentIndex, next)
                                            }
                                            dragOffsetPx = 0f
                                        }
                                        if (dragOffsetPx <= -rowHeightPx / 2f) {
                                            val prev = currentIndex - 1
                                            if (prev >= 0) {
                                                songs.swap(currentIndex, prev)
                                            }
                                            dragOffsetPx = 0f
                                        }
                                    }
                                }
                        )

                        // titre
                        Text(
                            text = displayName,
                            color = if (isPlayed) Color.Gray else songColor,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedPlaylist?.let { pl ->
                                        onPlaySong(uriString, pl)
                                    }
                                }
                        )

                        // 3 petits points dans un petit cadre, même couleur que le titre
                        var showRowMenu by remember { mutableStateOf(false) }

                        Box {
                            IconButton(
                                onClick = { showRowMenu = true },
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(Color(0x22FFFFFF)) // léger fond
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Options",
                                    tint = if (isPlayed) Color.Gray else songColor
                                )
                            }

                            DropdownMenu(
                                expanded = showRowMenu,
                                onDismissRequest = { showRowMenu = false },
                                modifier = Modifier.background(Color(0xFF141414))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Retirer de la liste", color = Color.White) },
                                    onClick = {
                                        // 1) on enlève du state local
                                        songs.remove(uriString)

                                        // 2) on met à jour le repo pour que ça ne revienne plus
                                        selectedPlaylist?.let { pl ->
                                            PlaylistRepository.updatePlayListOrder(
                                                pl,
                                                songs.toList()
                                            )
                                        }

                                        showRowMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// util
private fun <T> MutableList<T>.swap(i: Int, j: Int) {
    if (i == j) return
    val tmp = this[i]
    this[i] = this[j]
    this[j] = tmp
}