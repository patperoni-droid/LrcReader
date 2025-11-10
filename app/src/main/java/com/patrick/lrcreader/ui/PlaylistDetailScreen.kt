package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.PlaylistRepository

@Composable
fun PlaylistDetailScreen(
    modifier: Modifier = Modifier,
    playlistName: String,
    onBack: () -> Unit,
    onPlaySong: (String) -> Unit,
    refreshKey: Long = 0L
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // recharge les chansons à chaque changement de playlist
    val songs = remember(playlistName, refreshKey) {
        PlaylistRepository.getSongsFor(playlistName)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // ─── BARRE DU HAUT (façon Musicolet) ───────────────────────
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

            Spacer(Modifier.width(6.dp))

            // le rectangle cliquable avec flèche
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = playlistName,
                        color = Color.White,
                        fontSize = 18.sp,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Menu playlist",
                        tint = Color.White
                    )
                }
            }
        }

        // menu déroulant de la playlist
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Renommer") },
                onClick = {
                    // TODO : renommer playlist
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Supprimer") },
                onClick = {
                    // TODO : supprimer playlist
                    menuExpanded = false
                }
            )
        }

        // ─── CONTENU ───────────────────────
        if (songs.isEmpty()) {
            Text(
                "Aucun titre dans cette liste.",
                color = Color.Gray
            )
        } else {
            LazyColumn {
                items(songs) { uriString ->
                    // 1. décodage propre
                    val decoded = runCatching {
                        java.net.URLDecoder.decode(uriString, "UTF-8")
                    }.getOrElse { uriString }

                    // 2. on récupère juste le nom du fichier
                    val filePart = decoded
                        .substringAfterLast('/')
                        .substringAfterLast(':')

                    // 3. on nettoie l’extension
                    val displayName = when {
                        filePart.endsWith(".mp3", ignoreCase = true) ->
                            filePart.dropLast(4)
                        filePart.endsWith(".wav", ignoreCase = true) ->
                            filePart.dropLast(4)
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