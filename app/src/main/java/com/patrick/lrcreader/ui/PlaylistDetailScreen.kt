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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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

    // chansons de la playlist
    val songs = remember(playlistName, refreshKey) {
        PlaylistRepository.getSongsFor(playlistName)
    }

    // map en mémoire pour marquer "à revoir" par chanson
    val reviewMap = remember(playlistName, refreshKey) {
        mutableStateMapOf<String, Boolean>()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // ─── BARRE DU HAUT ───────────────────────
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

            // bloc avec nom de la playlist + flèche
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

        // menu playlist (renommer / supprimer)
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Renommer") },
                onClick = {
                    // TODO : renommer la playlist
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Supprimer") },
                onClick = {
                    // TODO : supprimer la playlist
                    menuExpanded = false
                }
            )
        }

        HorizontalDivider(color = Color(0xFF1E1E1E))

        // ─── CONTENU ───────────────────────
        if (songs.isEmpty()) {
            Text(
                "Aucun titre dans cette liste.",
                color = Color.Gray,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            LazyColumn {
                items(songs, key = { it }) { uriString ->
                    // décodage
                    val decoded = runCatching {
                        java.net.URLDecoder.decode(uriString, "UTF-8")
                    }.getOrElse { uriString }

                    val filePart = decoded
                        .substringAfterLast('/')
                        .substringAfterLast(':')

                    val displayName = when {
                        filePart.endsWith(".mp3", ignoreCase = true) ->
                            filePart.dropLast(4)
                        filePart.endsWith(".wav", ignoreCase = true) ->
                            filePart.dropLast(4)
                        else -> filePart
                    }.trim()

                    val played = PlaylistRepository.isSongPlayed(playlistName, uriString)
                    val isReview = reviewMap[uriString] == true

                    // couleur texte:
                    //  - jouée  -> gris
                    //  - à revoir -> rouge
                    //  - normal -> blanc
                    val textColor = when {
                        played -> Color(0xFF888888)
                        isReview -> Color(0xFFFF8080)
                        else -> Color.White
                    }

                    var songMenuExpanded by remember(uriString) { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // handle "drag"
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(24.dp)
                        )

                        // titre cliquable -> lecture
                        Text(
                            text = displayName,
                            color = textColor,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPlaySong(uriString) }
                        )

                        // bouton Play
                        IconButton(onClick = { onPlaySong(uriString) }) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Lire",
                                tint = Color.White
                            )
                        }

                        // bouton menu 3 points
                        IconButton(onClick = { songMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Options du titre",
                                tint = Color.White
                            )
                        }

                        // menu par titre
                        DropdownMenu(
                            expanded = songMenuExpanded,
                            onDismissRequest = { songMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Retirer de la liste") },
                                onClick = {
                                    // TODO : retirer ce titre de la playlist si tu as une fonction repo
                                    // PlaylistRepository.removeSongFromPlaylist(playlistName, uriString)
                                    songMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Renommer") },
                                onClick = {
                                    // TODO : ouvrir un dialog de renommage si besoin
                                    songMenuExpanded = false
                                }
                            )

                            val reviewLabel = if (isReview) {
                                "Retirer \"À revoir\""
                            } else {
                                "Marquer \"À revoir\""
                            }

                            DropdownMenuItem(
                                text = { Text(reviewLabel) },
                                onClick = {
                                    reviewMap[uriString] = !isReview
                                    songMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}