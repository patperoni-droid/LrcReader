package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.PlaylistRepository
import java.net.URLDecoder

@Composable
fun QuickPlaylistsScreen(
    modifier: Modifier = Modifier,
    onPlaySong: (String) -> Unit   // ← envoyé par MainActivity
) {
    // on récupère les playlists existantes
    val playlists = remember { PlaylistRepository.getPlaylists() }

    // playlist actuellement choisie dans cet onglet
    var selectedPlaylist by remember { mutableStateOf<String?>(playlists.firstOrNull()) }

    // chansons de cette playlist
    val songs = remember(selectedPlaylist) {
        selectedPlaylist?.let { PlaylistRepository.getSongsFor(it) } ?: emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text("Playlists (live)", color = Color.White, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))

        // liste horizontale / simple de playlists
        if (playlists.isEmpty()) {
            Text(
                "Aucune playlist.\nVa dans l’onglet “Toutes” pour en créer.",
                color = Color.Gray
            )
        } else {
            // petites puces cliquables
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                playlists.forEach { name ->
                    val isSelected = name == selectedPlaylist
                    Surface(
                        color = if (isSelected) Color(0xFF1DB954) else Color.Transparent,
                        border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.clickable { selectedPlaylist = name }
                    ) {
                        Text(
                            text = name,
                            color = if (isSelected) Color.Black else Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // titre de section
            Text(
                text = "Chansons de “${selectedPlaylist ?: "-"}”",
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(8.dp))

            // liste des chansons
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(songs) { uriString ->
                    val displayName = try {
                        URLDecoder.decode(uriString, "UTF-8").substringAfterLast('/')
                    } catch (e: Exception) {
                        uriString
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaySong(uriString) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = displayName,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "▶",
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}