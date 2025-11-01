package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.PlaylistRepository

@Composable
fun AllPlaylistsScreen(
    modifier: Modifier = Modifier,
    onPlaylistClick: (String) -> Unit
) {
    // on lit les playlists actuelles
    var playlists by remember { mutableStateOf(PlaylistRepository.getPlaylists()) }

    // playlist affichée dans la ligne du haut
    var selected by remember {
        mutableStateOf(
            if (playlists.isNotEmpty()) playlists.first() else ""
        )
    }

    // menu déroulant ouvert / fermé
    var expanded by remember { mutableStateOf(false) }

    // dialog création
    var showDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "Vos listes de lecture",
                color = Color(0xFF1DB954),
                fontSize = 22.sp
            )

            Spacer(Modifier.height(16.dp))

            // ---- ligne de sélection ----
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .clickable { expanded = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selected.isNotEmpty())
                            selected
                        else
                            "Sélectionne une liste de lecture",
                        color = Color.White,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = if (expanded) "▲" else "▼",
                        color = Color.White
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = Color(0xFF2A2A2A)
                ) {
                    // toutes les playlists existantes
                    playlists.forEach { name ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = name,
                                    color = Color.White
                                )
                            },
                            onClick = {
                                selected = name
                                expanded = false
                                // 👉 là seulement on ouvre le détail
                                onPlaylistClick(name)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- bouton "nouvelle liste" TOUJOURS visible ----
            OutlinedButton(
                onClick = { showDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Nouvelle liste de lecture")
            }
        }

        // ---- dialog création ----
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Nouvelle liste", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nom de la liste") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val clean = newName.trim()
                        if (clean.isNotEmpty()) {
                            // on ajoute dans le repo
                            PlaylistRepository.addPlaylist(clean)
                            // on relit la liste
                            playlists = PlaylistRepository.getPlaylists()
                            // on met la nouvelle en sélection
                            selected = clean
                            // ❗️on NE BASCULE PAS vers l’écran détail
                            newName = ""
                        }
                        showDialog = false
                    }) {
                        Text("OK", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Annuler", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF222222)
            )
        }
    }
}