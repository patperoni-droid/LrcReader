package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
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
    refreshKey: Long = 0L    // tu l’avais déjà
) {
    // on relit la liste quand la playlist ou le refresh change
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
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 30.dp, bottom = 12.dp)
                .fillMaxWidth()
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

        if (songs.isEmpty()) {
            Text(
                "Aucun titre dans cette liste.",
                color = Color.Gray
            )
        } else {
            LazyColumn {
                items(songs) { uriString ->
                    // 1. on essaie de décoder, sinon on garde tel quel
                    val decoded = runCatching {
                        java.net.URLDecoder.decode(uriString, "UTF-8")
                    }.getOrElse { uriString }

                    // 2. on récupère juste le nom de fichier
                    val filePart = decoded
                        .substringAfterLast('/')   // après le dernier /
                        .substringAfterLast(':')   // certains URI Android ont un ":" à la fin

                    // 3. on enlève l’extension seulement si elle est à la fin
                    val displayName = when {
                        filePart.endsWith(".mp3", ignoreCase = true) ->
                            filePart.dropLast(4)   // ".mp3" = 4 caractères
                        filePart.endsWith(".wav", ignoreCase = true) ->
                            filePart.dropLast(4)   // ".wav" = 4 caractères
                        else -> filePart
                    }.trim()

                    val played = PlaylistRepository.isSongPlayed(playlistName, uriString)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(24.dp)
                        )

                        Text(
                            text = displayName,
                            color = if (played) Color(0xFF888888) else Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPlaySong(uriString) }
                        )

                        IconButton(onClick = { onPlaySong(uriString) }) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Lire",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}