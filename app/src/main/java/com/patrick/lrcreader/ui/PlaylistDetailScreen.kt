package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.PlaylistRepository
import java.net.URLDecoder

@Composable
fun PlaylistDetailScreen(
    modifier: Modifier = Modifier,
    playlistName: String,
    onBack: () -> Unit,
    onPlaySong: (String) -> Unit,
    refreshKey: Long = 0L               // 👈 ajouté
) {
    // on relit la liste à chaque changement de refreshKey
    val songs = remember(playlistName, refreshKey) {
        PlaylistRepository.getSongsFor(playlistName)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // barre du haut
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = Color.White
                )
            }

            Text(
                text = playlistName,
                color = Color.White,
                fontSize = 22.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        if (songs.isEmpty()) {
            Text(
                "Aucun titre dans cette liste.",
                color = Color.Gray
            )
        } else {
            LazyColumn {
                items(songs) { uriString ->
                    val displayName = try {
                        URLDecoder.decode(uriString, "UTF-8").substringAfterLast('/')
                    } catch (e: Exception) {
                        uriString
                    }

                    // 👇 on regarde si ce titre est marqué joué
                    val played = PlaylistRepository.isSongPlayed(playlistName, uriString)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onPlaySong(uriString) }) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Lire",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = displayName,
                            color = if (played) Color(0xFF888888) else Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPlaySong(uriString) }
                        )
                    }
                }
            }
        }
    }
}