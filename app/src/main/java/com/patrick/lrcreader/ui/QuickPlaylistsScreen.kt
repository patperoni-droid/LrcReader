package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.PlaylistRepository
import java.net.URLDecoder

@Composable
fun QuickPlaylistsScreen(
    modifier: Modifier = Modifier,
    onPlaySong: (String, String) -> Unit,   // uri + nom de playlist
    refreshKey: Int
) {
    val playlists = remember(refreshKey) { PlaylistRepository.getPlaylists() }

    var selectedPlaylist by remember(refreshKey) {
        mutableStateOf<String?>(playlists.firstOrNull())
    }
    var showMenu by remember { mutableStateOf(false) }

    val songs = remember(selectedPlaylist, refreshKey) {
        selectedPlaylist?.let { PlaylistRepository.getSongsFor(it) } ?: emptyList()
    }

    val songColor = Color(0xFFE86FFF)
    val menuBg = Color(0xFF222222)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // titre + menu
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
            Text(
                text = "Chansons de “$selectedPlaylist”",
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(songs) { uriString ->
                    val displayName = try {
                        URLDecoder.decode(uriString, "UTF-8").substringAfterLast('/')
                    } catch (e: Exception) {
                        uriString
                    }

                    val isPlayed = selectedPlaylist?.let {
                        PlaylistRepository.isSongPlayed(it, uriString)
                    } ?: false

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPlaylist?.let { plName ->
                                    onPlaySong(uriString, plName)
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 🎵 icône plus grande
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(32.dp)
                                .padding(end = 8.dp)
                        )

                        Text(
                            text = displayName,
                            color = if (isPlayed) Color.Gray else songColor,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "▶",
                            color = if (isPlayed) Color.Gray else songColor,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}